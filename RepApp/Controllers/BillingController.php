<?php
class BillingController extends RepController {
    private $routeModel;
    private $catalogModel;
    private $customerModel;

    public function __construct() {
        if (!isset($_SESSION['user_id'])) { header('Location: ' . APP_URL . '/auth/login'); exit; }
        $this->routeModel = $this->model('RepRoute');
        $this->catalogModel = $this->model('RepCatalog');
        $this->customerModel = $this->model('RepCustomer');
    }

    public function index() {
        $activeRoute = $this->routeModel->getActiveRoute($_SESSION['user_id']);
        if (!$activeRoute) { header('Location: ' . APP_URL . '/rep'); exit; }

        $data = [
            'title' => 'Select Billing Mode',
            'content_view' => 'billing_options',
            'active_route' => $activeRoute
        ];
        $this->view('layout', $data);
    }

    public function catalog() {
        $activeRoute = $this->routeModel->getActiveRoute($_SESSION['user_id']);
        if (!$activeRoute) { header('Location: ' . APP_URL . '/rep'); exit; }

        require_once '../app/Models/PaymentTerm.php';
        $termModel = new PaymentTerm();

        $data = [
            'title' => 'Visual Catalog',
            'content_view' => 'catalog_bill',
            'active_route' => $activeRoute,
            'categories' => $this->catalogModel->getCategories(),
            'products' => $this->catalogModel->getVisualCatalog(),
            'customers' => $this->customerModel->getAllCustomers(),
            'payment_terms' => $termModel->getAllTerms()
        ];
        $this->view('layout', $data);
    }

    // NEW: Activated the Standard Billing Interface
    public function standard() {
        $activeRoute = $this->routeModel->getActiveRoute($_SESSION['user_id']);
        if (!$activeRoute) { header('Location: ' . APP_URL . '/rep'); exit; }

        require_once '../app/Models/PaymentTerm.php';
        $termModel = new PaymentTerm();

        $data = [
            'title' => 'Standard Billing',
            'content_view' => 'standard_bill',
            'active_route' => $activeRoute,
            'products' => $this->catalogModel->getVisualCatalog(), // Reusing catalog payload for fast JSON parsing
            'customers' => $this->customerModel->getAllCustomers(),
            'payment_terms' => $termModel->getAllTerms()
        ];
        $this->view('layout', $data);
    }

    public function process_checkout() {
        if ($_SERVER['REQUEST_METHOD'] != 'POST') {
            header('Location: ' . APP_URL . '/rep/billing/catalog');
            exit;
        }

        $payload = json_decode($_POST['checkout_payload'], true);
        if (!$payload || empty($payload['cart'])) {
            die("Invalid Checkout Payload received from mobile POS.");
        }

        $db = new Database();

        try {
            $db->query("SHOW COLUMNS FROM invoices LIKE 'cheque_date'");
            if (!$db->single()) {
                $db->query("ALTER TABLE invoices ADD COLUMN cheque_date DATE NULL AFTER due_date");
                $db->execute();
            }
        } catch (Exception $e) {
            // Ignore schema migration errors
        }
        
        try {
            $db->beginTransaction();

            $userId = $_SESSION['user_id'];
            $customerId = $payload['customer_id'];
            $termId = $payload['payment_term_id'];
            $lat = $payload['location']['lat'] ?? null;
            $lng = $payload['location']['lng'] ?? null;
            
            // 1. Fetch Active Route ID
            $db->query("SELECT id FROM rep_daily_routes WHERE user_id = :uid AND status = 'Active' ORDER BY id DESC LIMIT 1");
            $db->bind(':uid', $userId);
            $activeRoute = $db->single();
            $routeId = $activeRoute ? $activeRoute->id : null;

            // 2. Exact Ledger Account Mapping based on user specification
            $db->query("SELECT id, account_code FROM chart_of_accounts WHERE account_code IN ('1000', '1010', '1600', '4000', '1200')");
            $accounts = $db->resultSet();
            $accMap = [];
            foreach($accounts as $a) { $accMap[$a->account_code] = $a->id; }

            $cashAcc = $accMap['1000'] ?? null;
            $chequeAcc = $accMap['1010'] ?? null;
            $bankAcc = $accMap['1600'] ?? null;
            $salesAcc = $accMap['4000'] ?? null;
            $arAcc = $accMap['1200'] ?? null; // Default AR

            if (!$arAcc || !$salesAcc) {
                throw new Exception("Missing AR (1200) or Sales (4000) account in Chart of Accounts.");
            }

            // 3. Calculate Invoice Totals
            $subTotal = 0;
            foreach ($payload['cart'] as $item) {
                $rowGross = $item['qty'] * $item['price'];
                $rowDisc = ($item['disc_type'] === '%') ? ($rowGross * $item['disc_val'] / 100) : $item['disc_val'];
                $rowNet = $rowGross - $rowDisc;
                $subTotal += ($rowNet > 0 ? $rowNet : 0);
            }

            $globalDiscVal = $payload['discounts']['val'];
            $globalDiscType = $payload['discounts']['type'];
            $billDisc = ($globalDiscType === '%') ? ($subTotal * $globalDiscVal / 100) : $globalDiscVal;
            $netSubTotal = $subTotal - $billDisc;
            if ($netSubTotal < 0) $netSubTotal = 0;

            $taxVal = $payload['tax']['val'];
            $taxType = $payload['tax']['type'];
            $taxAmount = ($taxType === '%') ? ($netSubTotal * $taxVal / 100) : $taxVal;
            $grandTotal = $netSubTotal + $taxAmount;

            // 4. Post Sale Double-Entry (Debit AR, Credit Sales)
            $invoiceNumber = 'INV-' . time();
            $db->query("INSERT INTO journal_entries (entry_date, reference, description, created_by, status) VALUES (CURDATE(), :ref, :desc, :uid, 'Posted')");
            $db->bind(':ref', $invoiceNumber);
            $db->bind(':desc', "Mobile POS Sale: " . $invoiceNumber);
            $db->bind(':uid', $userId);
            $db->execute();
            $saleJournalId = $db->lastInsertId();

            $db->query("INSERT INTO transactions (journal_entry_id, account_id, debit, credit) VALUES (:jid, :aid, :deb, 0)");
            $db->bind(':jid', $saleJournalId);
            $db->bind(':aid', $arAcc);
            $db->bind(':deb', $grandTotal);
            $db->execute();
            $db->query("UPDATE chart_of_accounts SET balance = balance + :amt WHERE id = :aid");
            $db->bind(':amt', $grandTotal);
            $db->bind(':aid', $arAcc);
            $db->execute();

            $db->query("INSERT INTO transactions (journal_entry_id, account_id, debit, credit) VALUES (:jid, :aid, 0, :cred)");
            $db->bind(':jid', $saleJournalId);
            $db->bind(':aid', $salesAcc);
            $db->bind(':cred', $grandTotal); // Combining Tax into sales for immediate simplicity
            $db->execute();
            $db->query("UPDATE chart_of_accounts SET balance = balance + :amt WHERE id = :aid");
            $db->bind(':amt', $grandTotal);
            $db->bind(':aid', $salesAcc);
            $db->execute();

            // 5. Create Database Invoice Record
            $db->query("SELECT name, days_due FROM payment_terms WHERE id = :tid");
            $db->bind(':tid', $termId);
            $term = $db->single();
            $daysDue = $term ? $term->days_due : 0;
            $dueDate = date('Y-m-d', strtotime("+$daysDue days"));

            $chequeDate = null;
            if ($term && (stripos($term->name, 'cheque') !== false || stripos($term->name, 'check') !== false)) {
                $chequeDate = !empty($payload['term_cheque_date']) ? $payload['term_cheque_date'] : null;
                if (!$chequeDate) {
                    throw new Exception('Cheque Date is required when payment term is Cheque.');
                }
            }

            $db->query("INSERT INTO invoices (invoice_number, customer_id, invoice_date, due_date, cheque_date, total_amount, global_discount_val, global_discount_type, tax_amount, journal_entry_id, created_by, rep_route_id, latitude, longitude, stock_status) 
                        VALUES (:inv, :cid, CURDATE(), :due, :cdate, :total, :gdval, :gdtype, :tax, :jid, :uid, :route, :lat, :lng, 'reserved')");
            $db->bind(':inv', $invoiceNumber);
            $db->bind(':cid', $customerId);
            $db->bind(':due', $dueDate);
            $db->bind(':cdate', $chequeDate);
            $db->bind(':total', $subTotal);
            $db->bind(':gdval', $globalDiscVal);
            $db->bind(':gdtype', $globalDiscType);
            $db->bind(':tax', $taxAmount);
            $db->bind(':jid', $saleJournalId);
            $db->bind(':uid', $userId);
            $db->bind(':route', $routeId);
            $db->bind(':lat', $lat);
            $db->bind(':lng', $lng);
            $db->execute();
            $invoiceId = $db->lastInsertId();

            // 6. Create Invoice Items and Reserve Inventory
            foreach ($payload['cart'] as $item) {
                $rowGross = $item['qty'] * $item['price'];
                $rowDiscAmount = ($item['disc_type'] === '%') ? ($rowGross * $item['disc_val'] / 100) : $item['disc_val'];
                $rowNet = $rowGross - $rowDiscAmount;

                $db->query("INSERT INTO invoice_items (invoice_id, item_id, variation_option_id, description, quantity, loaded_quantity, unit_price, discount_value, discount_type, total) 
                            VALUES (:iid, :item_id, :var_id, :desc, :qty, :qty, :price, :dval, :dtype, :tot)");
                $db->bind(':iid', $invoiceId);
                $db->bind(':item_id', !empty($item['itemId']) ? $item['itemId'] : null);
                $db->bind(':var_id', !empty($item['varId']) ? $item['varId'] : null);
                $db->bind(':desc', $item['name']);
                $db->bind(':qty', $item['qty']);
                $db->bind(':price', $item['price']);
                $db->bind(':dval', $item['disc_val']);
                $db->bind(':dtype', $item['disc_type']);
                $db->bind(':tot', $rowNet);
                $db->execute();

                // Reserve Inventory instead of deducting physical stock balance
                if (!empty($item['itemId'])) {
                    $db->query("UPDATE items SET quantity_reserved = quantity_reserved + :qty WHERE id = :id");
                    $db->bind(':qty', $item['qty']);
                    $db->bind(':id', $item['itemId']);
                    $db->execute();
                }
                if (!empty($item['varId'])) {
                    $db->query("UPDATE item_variation_options SET quantity_reserved = quantity_reserved + :qty WHERE id = :id");
                    $db->bind(':qty', $item['qty']);
                    $db->bind(':id', $item['varId']);
                    $db->execute();
                }
            }

            // 7. Process Arrears & Collections
            $collections = $payload['arrears_collections'];
            
            $processCollection = function($amount, $assetAccId, $methodStr, $chequeDetails = null) use ($db, $userId, $customerId, $arAcc, $invoiceNumber, $routeId) {
                if ($amount <= 0 || !$assetAccId) return;

                if ($methodStr === 'Cheque' && $chequeDetails) {
                    $db->query("INSERT INTO cheques (customer_id, bank_name, cheque_number, amount, banking_date, status, rep_route_id, created_by) 
                                VALUES (:cid, :bn, :cn, :amt, :bdate, 'Pending', :route_id, :uid)");
                    $db->bind(':cid', $customerId);
                    $db->bind(':bn', $chequeDetails['bank'] ?? 'Unknown');
                    $db->bind(':cn', $chequeDetails['number'] ?? 'Unknown');
                    $db->bind(':amt', $amount);
                    $db->bind(':bdate', $chequeDetails['date'] ?: date('Y-m-d'));
                    $db->bind(':route_id', $routeId);
                    $db->bind(':uid', $userId);
                    $db->execute();
                }

                // Log Customer Payment History (so it shows in profile) - KEEP journal_entry_id as NULL to wait for finalization
                $db->query("INSERT INTO customer_payments (customer_id, amount, payment_date, payment_method, reference, journal_entry_id, rep_route_id, created_by) 
                            VALUES (:cid, :amt, CURDATE(), :method, :ref, NULL, :route_id, :uid)");
                $db->bind(':cid', $customerId);
                $db->bind(':amt', $amount);
                $db->bind(':method', $methodStr);
                $db->bind(':ref', $chequeDetails['number'] ?? '');
                $db->bind(':route_id', $routeId);
                $db->bind(':uid', $userId);
                $db->execute();
            };

            $processCollection($collections['cash'], $cashAcc, 'Cash');
            $processCollection($collections['bank'], $bankAcc, 'Bank Transfer');
            
            $totalChequeAmount = 0.0;
            if (isset($payload['cheques']) && is_array($payload['cheques'])) {
                foreach ($payload['cheques'] as $chequeObj) {
                    $amt = floatval($chequeObj['amount'] ?? 0);
                    if ($amt > 0) {
                        $totalChequeAmount += $amt;
                        $processCollection($amt, $chequeAcc, 'Cheque', [
                            'bank' => $chequeObj['bank'] ?? 'Unknown',
                            'number' => $chequeObj['number'] ?? 'Unknown',
                            'date' => $chequeObj['date'] ?? date('Y-m-d')
                        ]);
                    }
                }
            } else {
                // Fallback to legacy single cheque details if present
                $cqAmt = floatval($collections['cheque'] ?? 0);
                if ($cqAmt > 0) {
                    $totalChequeAmount += $cqAmt;
                    $cqBank = $payload['cheque_details']['bank'] ?? null;
                    $cqNum = $payload['cheque_details']['number'] ?? null;
                    $cqDate = $payload['cheque_details']['date'] ?? null;
                    $processCollection($cqAmt, $chequeAcc, 'Cheque', ['bank' => $cqBank, 'number' => $cqNum, 'date' => $cqDate]);
                }
            }

            // Auto-Allocate Payments to Oldest Unpaid Invoices
            $totalCollected = $collections['cash'] + $collections['bank'] + $totalChequeAmount;
            if ($totalCollected > 0) {
                $db->query("SELECT id, total_amount, tax_amount, global_discount_val, global_discount_type FROM invoices WHERE customer_id = :cid AND status IN ('Unpaid', 'Draft') ORDER BY invoice_date ASC");
                $db->bind(':cid', $customerId);
                $unpaid = $db->resultSet();
                
                $remaining = $totalCollected;
                foreach($unpaid as $inv) {
                    $trueGrandTotal = $inv->total_amount;
                    if ($inv->global_discount_val > 0) {
                        $trueGrandTotal -= ($inv->global_discount_type == '%') ? ($inv->total_amount * ($inv->global_discount_val / 100)) : $inv->global_discount_val;
                    }
                    $trueGrandTotal += $inv->tax_amount;

                    if ($remaining >= $trueGrandTotal - 0.01) { 
                        $db->query("UPDATE invoices SET status = 'Paid' WHERE id = :id");
                        $db->bind(':id', $inv->id);
                        $db->execute();
                        $remaining -= $trueGrandTotal;
                    }
                }
            }

            $db->commit();
            
            // Redirect successfully back to the catalog, triggering the success modal!
            header('Location: ' . APP_URL . "/rep/billing/catalog?success_invoice_id={$invoiceId}&customer_id={$customerId}&invoice_num={$invoiceNumber}");
            exit;

        } catch (Exception $e) {
            if ($db) $db->rollBack();
            die("Checkout Transaction Failed: " . $e->getMessage());
        }
    }
}