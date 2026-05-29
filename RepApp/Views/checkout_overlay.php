<?php
// Extracted Failsafe: Calculates the TRUE outstanding balance for each customer 
$successInvoiceId = $_GET['success_invoice_id'] ?? null;
$successCustomerId = $_GET['customer_id'] ?? null;
$successCustomer = null;

$db = new Database();
foreach ($customers as $c) {
    if ($successCustomerId && $c->id == $successCustomerId) {
        $successCustomer = $c;
    }

    if (!isset($c->outstanding_balance)) {
        $db->query("
            SELECT 
               (SELECT COALESCE(SUM(total_amount - COALESCE(CASE WHEN global_discount_type = '%' THEN (total_amount * global_discount_val / 100) ELSE global_discount_val END, 0) + COALESCE(tax_amount, 0)), 0) FROM invoices WHERE customer_id = :id1 AND status != 'Voided') 
               - 
               (SELECT COALESCE(SUM(amount), 0) FROM customer_payments WHERE customer_id = :id2) 
               - 
               (SELECT COALESCE(SUM(total_amount), 0) FROM credit_notes WHERE customer_id = :id3) 
               AS outstanding_balance
        ");
        $db->bind(':id1', $c->id);
        $db->bind(':id2', $c->id);
        $db->bind(':id3', $c->id);
        $balRow = $db->single();
        $c->outstanding_balance = $balRow->outstanding_balance ?? 0;
    }
}

if ($successInvoiceId && !$successCustomer) {
    $db->query("SELECT * FROM customers WHERE id = :id");
    $db->bind(':id', $successCustomerId);
    $successCustomer = $db->single();
}
?>
<style>
    /* POS Checkout Terminal Overlay */
    .co-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--app-bg); z-index: 3000; display: none; flex-direction: column; overflow-y: auto; }
    .co-header { background: #cfd8dc; padding: 15px 20px; display: flex; align-items: center; gap: 15px; position: sticky; top:0; z-index: 3010; }
    .co-header h2 { margin: 0; font-size: 18px; color: #333; }
    .co-header span { font-size: 12px; color: #666; }
    
    .co-title-bar { padding: 20px; background: var(--surface); display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); }
    .co-card { background: var(--surface); border-radius: 12px; border: 1px solid var(--border); margin: 15px 20px; padding: 20px; }
    
    .co-item-row { display: flex; justify-content: space-between; align-items: flex-start; padding: 15px 0; border-bottom: 1px dashed var(--border); cursor: pointer; transition: 0.2s; }
    .co-item-row:hover { background: rgba(0,102,204,0.02); }
    .co-item-row:last-child { border-bottom: none; padding-bottom: 0; }
    .co-del-btn { color: #c62828; cursor: pointer; border: none; background: transparent; font-size: 18px; padding: 5px; margin-top: 5px; }

    .pay-input-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 15px;}
    .pay-input-row label { font-weight: bold; font-size: 14px; display: flex; align-items: center; gap: 8px; color: var(--text-dark);}
    .pay-input-box { width: 60%; border-radius: 8px; padding: 12px; text-align: right; font-size: 16px; font-weight: bold; font-family: monospace; outline: none; border: 1px solid var(--border);}

    .pay-cash { background: #e8f5e9; color: #2e7d32; border-color: #c8e6c9; }
    .pay-bank { background: #e3f2fd; color: #1565c0; border-color: #bbdefb; }
    .pay-cheque { background: #fff8e1; color: #f57c00; border-color: #ffecb3; }

    .co-totals-row { display: flex; justify-content: space-between; padding: 8px 0; font-size: 14px; color: var(--text-muted);}
    .co-payable-row { display: flex; justify-content: space-between; padding: 15px 0; font-size: 20px; font-weight: bold; color: #0066cc; border-top: 1px dashed #ccc; border-bottom: 1px dashed #ccc; margin-top: 10px;}
    .co-due-row { display: flex; justify-content: space-between; font-size: 16px; font-weight: bold; color: var(--text-dark); margin-top: 20px; border-top: 2px solid var(--border); padding-top: 15px;}
    
    .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; }
</style>

<!-- POS Checkout Terminal Overlay -->
<div class="co-overlay" id="checkoutOverlay">
    <div class="co-header">
        <button onclick="closeCheckout()" style="background:transparent; border:none; font-size:24px; cursor:pointer; color:#333;">←</button>
        <div>
            <h2>POS Terminal</h2>
            <span>Generate Invoice & Arrears</span>
        </div>
    </div>

    <!-- Customer Info Display & Payment Term -->
    <div style="padding: 0 20px;">
        <div class="co-card" style="margin: 15px 0 0 0; border: 2px solid var(--primary); display: flex; justify-content: space-between; align-items: center; cursor: pointer;" onclick="openCustomerSheet()">
            <div>
                <label style="font-size:11px; font-weight:bold; color:var(--primary); text-transform:uppercase; display:block; margin-bottom:5px;">Billing Customer (Tap to Change)</label>
                <div id="checkoutCustomerName" style="font-size: 16px; font-weight: bold; color: var(--text-dark);">Not Selected</div>
            </div>
            <div style="text-align: right;">
                <label style="font-size:11px; font-weight:bold; color:var(--text-muted); text-transform:uppercase; display:block; margin-bottom:5px;">Outstanding</label>
                <div id="checkoutCustomerOws" style="font-size: 14px; font-weight: bold; color: var(--text-dark);">Rs 0.00</div>
            </div>
        </div>

        <div class="co-card" style="margin: 15px 0 0 0;">
            <label style="font-size:11px; font-weight:bold; color:var(--text-muted); text-transform:uppercase; display:block; margin-bottom:5px;">Invoice Payment Term *</label>
            <select id="coPaymentTerm" class="form-control" onchange="toggleTermChequeDate()" style="width: 100%; padding: 12px; font-size: 15px; font-weight: bold; background: var(--surface); color: var(--text-dark); border: 1px solid var(--border); border-radius: 8px; outline: none;">
                <option value="">Select Term...</option>
                <?php foreach($paymentTerms as $pt):
                    $isChequeTerm = (stripos($pt->name, 'cheque') !== false || stripos($pt->name, 'check') !== false);
                ?>
                    <option value="<?= $pt->id ?>" data-is-cheque="<?= $isChequeTerm ? '1' : '0' ?>"><?= htmlspecialchars($pt->name) ?></option>
                <?php endforeach; ?>
            </select>
        </div>

        <div class="co-card" id="coChequeDateWrap" style="margin: 15px 0 0 0; display: none; border-color: #ffecb3; background: #fff8e1;">
            <label style="font-size:11px; font-weight:bold; color:#f57c00; text-transform:uppercase; display:block; margin-bottom:5px;">Cheque Date (written on cheque) *</label>
            <input type="date" id="coTermChequeDate" class="form-control" style="width: 100%; padding: 12px; font-size: 15px; font-weight: bold; border: 1px solid #ffecb3; border-radius: 8px;">
            <div style="font-size: 11px; color: #888; margin-top: 6px;">Required when payment term is Cheque. Shown on the printed invoice.</div>
        </div>
    </div>

    <div class="co-title-bar" style="margin-top: 15px;">
        <h3 style="margin:0; display:flex; align-items:center; gap:10px; color:var(--text-dark);">🛒 Current Bill Summary</h3>
        <button onclick="closeCheckout()" style="background:transparent; border:none; font-size:20px; cursor:pointer; color:var(--text-muted);">✕</button>
    </div>

    <div class="co-card">
        <div style="font-size:11px; font-weight:bold; color:var(--text-muted); margin-bottom:15px; text-transform:uppercase;">Billed Items (Tap to Edit)</div>
        <div id="coItemsList"></div>
    </div>

    <div style="padding:0 20px;">
        <div class="grid-2">
            <div class="co-card" style="margin:0; padding:15px;">
                <div style="font-size:11px; font-weight:bold; color:var(--text-muted); margin-bottom:10px;">BILL DISCOUNT</div>
                <div style="display:flex; gap:5px;">
                    <select id="coGlobalDiscType" onchange="calcCheckout()" style="padding:8px; border:1px solid var(--border); border-radius:6px; background:var(--surface); color:var(--text-dark);"><option value="%">%</option><option value="Rs">Rs</option></select>
                    <input type="number" id="coGlobalDiscVal" value="0" oninput="calcCheckout()" style="width:100%; padding:8px; border:1px solid var(--border); border-radius:6px; text-align:right; font-weight:bold; color:#c62828; background:var(--surface);">
                </div>
            </div>
            <div class="co-card" style="margin:0; padding:15px;">
                <div style="font-size:11px; font-weight:bold; color:var(--text-muted); margin-bottom:10px;">TAX / VAT</div>
                <div style="display:flex; gap:5px;">
                    <select id="coTaxType" onchange="calcCheckout()" style="padding:8px; border:1px solid var(--border); border-radius:6px; background:var(--surface); color:var(--text-dark);"><option value="%">%</option><option value="Rs">Rs</option></select>
                    <input type="number" id="coTaxVal" value="0" oninput="calcCheckout()" style="width:100%; padding:8px; border:1px solid var(--border); border-radius:6px; text-align:right; font-weight:bold; color:#0066cc; background:var(--surface);">
                </div>
            </div>
        </div>
    </div>

    <div class="co-card">
        <div class="co-totals-row">
            <span>Subtotal</span>
            <span id="coSubtotal" style="font-family: monospace; font-weight:bold; color:var(--text-dark);">Rs 0.00</span>
        </div>
        <div class="co-payable-row">
            <span>CURRENT BILL PAYABLE</span>
            <span>Rs <span id="coPayable" style="font-family: monospace;">0.00</span></span>
        </div>
    </div>

    <!-- Credit Collection (Arrears) -->
    <div class="co-card" id="creditCollectionBox" style="display: none; border: 2px solid #0066cc; background: rgba(0,102,204,0.02);">
        <div style="font-size:14px; font-weight:bold; color:#0066cc; margin-bottom:5px; text-transform:uppercase;">Collect Outstanding Arrears</div>
        <p style="font-size:12px; color:var(--text-muted); margin-top:0; margin-bottom:15px;">Collections here will drop the customer's Accounts Receivable balance instantly.</p>
        
        <div class="pay-input-row">
            <label>💵 Cash</label>
            <input type="number" id="payCash" class="pay-input-box pay-cash" value="0.00" onfocus="if(this.value=='0.00')this.value='';" onblur="if(this.value=='')this.value='0.00';" oninput="calcCheckout()">
        </div>
        <div class="pay-input-row">
            <label>🏦 Bank Transfer</label>
            <input type="number" id="payBank" class="pay-input-box pay-bank" value="0.00" onfocus="if(this.value=='0.00')this.value='';" onblur="if(this.value=='')this.value='0.00';" oninput="calcCheckout()">
        </div>
        <!-- PDC CHEQUES SECTION -->
        <div style="margin-top: 15px; border-top: 1px dashed #ddd; padding-top: 15px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
                <label style="font-weight: bold; font-size: 13px; color: var(--text-dark); margin: 0;">💳 Cheques (PDC)</label>
                <button type="button" class="btn" onclick="addRepChequeRow()" style="padding: 4px 8px; font-size: 11px; margin: 0; background: #0066cc; color: white; border: none; border-radius: 4px; cursor: pointer; font-weight: bold;">➕ Add Cheque</button>
            </div>
            <div id="rep-cheques-container" style="display: flex; flex-direction: column; gap: 10px;"></div>
        </div>
    </div>

    <!-- Final Calculation Output -->
    <div class="co-card" style="margin-top: 0;">
        <div class="co-due-row" style="margin-top: 0; padding-top: 0; border-top: none;">
            <span>NEW PROJECTED OUTSTANDING</span>
            <span id="coProjectedOutstanding" style="font-family: monospace;">0.00</span>
        </div>
        <p style="font-size: 11px; color: var(--text-muted); margin: 5px 0 0 0; text-align: right;">Includes previous arrears + this bill - collections.</p>
    </div>

    <div style="padding: 0 20px 40px 20px;">
        <form id="checkoutForm" action="<?= APP_URL ?>/rep/billing/process_checkout" method="POST">
            <input type="hidden" name="checkout_payload" id="checkoutPayload">
            <button type="button" id="confirmSubmitBtn" class="btn-primary" onclick="submitCheckout()">Confirm Checkout & Invoice</button>
        </form>
    </div>
</div>

<!-- SUCCESS MODAL (Triggered when the backend redirects with a success_invoice_id) -->
<?php if ($successInvoiceId && $successCustomer): ?>
<div class="sheet-overlay" id="successModalOverlay" style="display:flex; z-index: 5000; align-items:center;">
    <div class="modal-content" style="background:var(--surface); width:90%; max-width:400px; padding:30px; border-radius:16px; text-align:center; position:relative;">
        <div style="font-size: 50px; margin-bottom: 15px;">✅</div>
        <h2 style="margin-top:0; color:#2e7d32;">Order Saved Successfully!</h2>
        <p style="color:var(--text-muted); font-size:14px; margin-bottom:25px;">Invoice #<?= htmlspecialchars($_GET['invoice_num'] ?? $successInvoiceId) ?> has been generated and GPS location recorded.</p>
        
        <div style="display:flex; flex-direction:column; gap:12px;">
            <?php if(!empty($successCustomer->email)): ?>
                <a href="<?= APP_URL ?>/sales/email_invoice?invoice_id=<?= $successInvoiceId ?>" class="btn-primary" style="background:#0066cc; text-decoration:none; padding:12px;">📧 Email Receipt</a>
            <?php else: ?>
                <button class="btn-primary" style="background:var(--border); color:var(--text-muted); padding:12px; box-shadow:none; cursor:not-allowed;" disabled>📧 No Email on File</button>
            <?php endif; ?>

            <?php if(!empty($successCustomer->whatsapp) || !empty($successCustomer->phone)): ?>
                <?php
                    $phone = !empty($successCustomer->whatsapp) ? $successCustomer->whatsapp : $successCustomer->phone;
                    $cleanPhone = preg_replace('/[^\d+]/', '', $phone);
                    if(strpos($cleanPhone, '0') === 0) $cleanPhone = '94' . substr($cleanPhone, 1);
                    $invUrl = APP_URL . "/sales/show/" . $successInvoiceId;
                    $waMsg = "Dear {$successCustomer->name},\n\nThank you for your order. Your invoice is ready and can be viewed/downloaded here:\n{$invUrl}\n\nThank you!";
                    $waLink = "https://wa.me/{$cleanPhone}?text=" . urlencode($waMsg);
                ?>
                <a href="<?= $waLink ?>" target="_blank" class="btn-primary" style="background:#25D366; text-decoration:none; padding:12px;">💬 Send via WhatsApp</a>
            <?php else: ?>
                <button class="btn-primary" style="background:var(--border); color:var(--text-muted); padding:12px; box-shadow:none; cursor:not-allowed;" disabled>💬 No Phone/WA on File</button>
            <?php endif; ?>

            <button class="btn-primary" style="background:#f57c00; padding:12px;" onclick="document.getElementById('qrCodeBox').style.display='block'">📲 Show QR Code</button>
        </div>

        <div id="qrCodeBox" style="display:none; margin-top: 20px; padding: 15px; border: 2px dashed #f57c00; border-radius: 8px;">
            <p style="margin: 0 0 10px 0; font-size: 13px; font-weight:bold; color: #f57c00;">Scan to view Invoice</p>
            <img src="https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=<?= urlencode(APP_URL . "/sales/show/" . $successInvoiceId) ?>" alt="QR Code" style="width:150px; height:150px; border-radius:8px;">
        </div>

        <a href="<?= APP_URL ?>/rep/billing" class="btn-primary" style="background:transparent; color:var(--text-muted); border:1px solid var(--border); box-shadow:none; margin-top:20px; padding:12px; text-decoration:none;">Close & Start New Bill</a>
    </div>
</div>
<?php endif; ?>

<!-- Free Issue Bottom Sheet overlay -->
<div class="sheet-overlay" id="freeIssueSheetOverlay" style="z-index: 6000;">
    <div class="bottom-sheet" id="freeIssueSheet" style="max-height: 85vh; display: flex; flex-direction: column; padding: 25px 20px;">
        <div class="sheet-handle" onclick="closeFreeIssueSheet()"></div>
        <h3 style="margin-top:0; color:#ef6c00; margin-bottom: 10px; display: flex; align-items: center; gap: 8px;">🎁 Free Issue Items</h3>
        
        <!-- Search Products for Free Issue -->
        <div style="position: relative; margin-bottom: 15px;">
            <input type="text" id="freeItemSearch" placeholder="Search product to add as free issue..." onkeyup="filterFreeSearch(event)" style="width:100%; padding:12px 15px; border:2px solid #ef6c00; border-radius:8px; font-size:14px; font-weight:bold; outline:none; background:var(--surface); color:var(--text-dark);" autocomplete="off">
            <ul id="freeSearchResults" class="search-results" style="left:0; right:0; max-height: 180px; display:none; position:absolute; z-index:1000; box-shadow: 0 5px 15px rgba(0,0,0,0.1); border-radius: 8px; border: 1px solid var(--border); background: var(--surface); padding-left: 0; margin-top: 5px;"></ul>
        </div>

        <!-- Free Issue Cart Items -->
        <div id="freeCartList" style="flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; min-height: 150px; max-height: 300px; margin-bottom: 15px; padding-bottom: 10px;">
            <div style="text-align: center; color: var(--text-muted); padding: 20px; font-size: 13px;">No free issue items added yet. Search above to add!</div>
        </div>

        <div style="display: flex; gap: 10px; margin-top: auto;">
            <button class="btn-primary" type="button" style="flex: 1; background: transparent; color: var(--text-muted); border: 1px solid var(--border); box-shadow: none;" onclick="closeFreeIssueSheet()">Cancel</button>
            <button class="btn-primary" type="button" style="flex: 1.5; background: #ef6c00; border-color: #ef6c00;" onclick="confirmFreeIssueCart()">Confirm Free Issue</button>
        </div>
    </div>
</div>

<script>
    // Unified product registry for free issue system
    window.globalFreeProducts = <?= json_encode(array_map(function($prod) {
        $hasVars = !empty($prod->variations);
        $totalReserved = $prod->quantity_reserved ?? 0;
        $availableStock = ($prod->quantity_on_hand ?? 0) - $totalReserved;
        
        if ($hasVars) {
            foreach($prod->variations as $v) {
                $vReserved = $v->quantity_reserved ?? 0;
                $v->available_stock = ($v->quantity_on_hand ?? 0) - $vReserved;
            }
        }
        $prod->available_stock = $availableStock;

        return [
            'id' => $prod->id,
            'type' => $prod->type ?? 'Inventory',
            'name' => $prod->name,
            'code' => $prod->item_code ?? '',
            'price' => $prod->price ?? 0,
            'available_stock' => $availableStock,
            'has_variations' => $hasVars,
            'rawProd' => $prod
        ];
    }, $products)); ?>;

    let freeCart = [];

    function openFreeIssueSheet() {
        if (!globalSelectedCustomerId) {
            alert("Please select a billing customer first.");
            openCustomerSheet();
            return;
        }
        freeCart = []; // Reset free issue temp cart on open
        renderFreeCartUI();
        document.getElementById('freeItemSearch').value = '';
        document.getElementById('freeSearchResults').innerHTML = '';
        document.getElementById('freeSearchResults').style.display = 'none';

        document.getElementById('freeIssueSheetOverlay').style.display = 'flex';
        setTimeout(() => { document.getElementById('freeIssueSheet').classList.add('open'); }, 10);
    }

    function closeFreeIssueSheet() {
        document.getElementById('freeIssueSheet').classList.remove('open');
        setTimeout(() => { document.getElementById('freeIssueSheetOverlay').style.display = 'none'; }, 300);
    }

    function filterFreeSearch(e) {
        const val = e.target.value.toLowerCase().trim();
        const resList = document.getElementById('freeSearchResults');
        resList.innerHTML = '';
        if(!val) { resList.style.display = 'none'; return; }

        const filtered = window.globalFreeProducts.filter(i => i.name.toLowerCase().includes(val) || (i.code && i.code.toLowerCase().includes(val))).slice(0, 10);
        if(filtered.length === 0) { resList.style.display = 'none'; return; }

        filtered.forEach(item => {
            const li = document.createElement('li');
            li.style.padding = '10px 12px';
            li.style.cursor = 'pointer';
            li.style.borderBottom = '1px solid var(--border)';
            li.style.display = 'flex';
            li.style.justifyContent = 'space-between';
            li.style.alignItems = 'center';
            li.style.listStyle = 'none';
            
            li.innerHTML = `
                <div>
                    <div style="font-weight:bold; font-size:13px; color:var(--text-dark);">${item.name}</div>
                    <span style="font-size: 10px; color: var(--text-muted);">${item.code ? 'SKU: '+item.code : ''}</span>
                </div>
                <div style="color: #ef6c00; font-weight: bold; font-size: 12px;">Add Free</div>
            `;
            li.onclick = () => {
                addFreeItemToTempCart(item);
                e.target.value = '';
                resList.style.display = 'none';
            };
            resList.appendChild(li);
        });
        resList.style.display = 'block';
    }

    function addFreeItemToTempCart(item) {
        if (item.has_variations && item.rawProd.variations) {
            item.rawProd.variations.forEach(v => {
                let cartItemId = `${item.id}_${v.id}_free`;
                let existing = freeCart.find(f => f.cartItemId === cartItemId);
                if (!existing) {
                    freeCart.push({
                        cartItemId: cartItemId,
                        itemId: item.id,
                        varId: v.id,
                        name: `${item.name} - ${v.variation_name}: ${v.value_name} (FREE)`,
                        price: 0,
                        qty: 1,
                        disc_val: 0,
                        disc_type: 'Rs',
                        type: item.type,
                        rawProd: item.rawProd
                    });
                } else {
                    existing.qty += 1;
                }
            });
        } else {
            let cartItemId = `${item.id}_0_free`;
            let existing = freeCart.find(f => f.cartItemId === cartItemId);
            if (!existing) {
                freeCart.push({
                    cartItemId: cartItemId,
                    itemId: item.id,
                    varId: null,
                    name: `${item.name} (FREE)`,
                    price: 0,
                    qty: 1,
                    disc_val: 0,
                    disc_type: 'Rs',
                    type: item.type,
                    rawProd: item.rawProd
                });
            } else {
                existing.qty += 1;
            }
        }
        renderFreeCartUI();
    }

    function changeFreeQty(cartItemId, delta) {
        let item = freeCart.find(f => f.cartItemId === cartItemId);
        if (item) {
            item.qty += delta;
            if (item.qty <= 0) {
                freeCart = freeCart.filter(f => f.cartItemId !== cartItemId);
            }
            renderFreeCartUI();
        }
    }

    function renderFreeCartUI() {
        const listDiv = document.getElementById('freeCartList');
        listDiv.innerHTML = '';

        if (freeCart.length === 0) {
            listDiv.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 40px 20px; font-size: 13px;">No free issue items added yet. Search above to add!</div>';
            return;
        }

        freeCart.forEach(item => {
            listDiv.innerHTML += `
                <div style="display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; background: rgba(239, 108, 0, 0.05); border: 1px solid rgba(239, 108, 0, 0.15); border-radius: 8px;">
                    <div style="flex: 1; padding-right: 10px;">
                        <div style="font-weight: bold; font-size: 13px; color: var(--text-dark);">${item.name}</div>
                        <span style="font-size: 10px; color: #ef6c00; font-weight: bold;">FREE ISSUE</span>
                    </div>
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <button type="button" onclick="changeFreeQty('${item.cartItemId}', -1)" style="width: 28px; height: 28px; border-radius: 50%; border: 1px solid #ef6c00; background: white; color: #ef6c00; font-weight: bold; cursor: pointer; font-size: 16px; display: flex; align-items: center; justify-content: center; line-height: 1;">-</button>
                        <span style="font-weight: bold; font-size: 14px; min-width: 20px; text-align: center;">${item.qty}</span>
                        <button type="button" onclick="changeFreeQty('${item.cartItemId}', 1)" style="width: 28px; height: 28px; border-radius: 50%; border: 1px solid #ef6c00; background: white; color: #ef6c00; font-weight: bold; cursor: pointer; font-size: 16px; display: flex; align-items: center; justify-content: center; line-height: 1;">+</button>
                    </div>
                </div>
            `;
        });
    }

    function confirmFreeIssueCart() {
        if (freeCart.length === 0) {
            alert("No free issue items added to confirm.");
            return;
        }

        freeCart.forEach(item => {
            let mainCartItemId = item.varId ? `${item.itemId}_${item.varId}_free` : `${item.itemId}_0_free`;
            let existingIndex = cart.findIndex(c => c.cartItemId === mainCartItemId);
            let mainItem = {
                cartItemId: mainCartItemId,
                itemId: item.itemId,
                varId: item.varId,
                name: item.name,
                price: 0,
                qty: item.qty,
                disc_val: 0,
                disc_type: 'Rs',
                type: item.type,
                rawProd: item.rawProd,
                is_free: true
            };

            if (existingIndex >= 0) {
                cart[existingIndex] = mainItem;
            } else {
                cart.push(mainItem);
            }
        });

        if (typeof updateCartUI === 'function') {
            updateCartUI();
        }
        
        closeFreeIssueSheet();
        
        if (document.getElementById('checkoutOverlay').style.display === 'flex') {
            renderCheckoutItems();
            calcCheckout();
        }
    }

    function openCheckout() {
        document.getElementById('checkoutOverlay').style.display = 'flex';
        renderCheckoutItems();
        calcCheckout();
    }

    function closeCheckout() {
        document.getElementById('checkoutOverlay').style.display = 'none';
    }

    function renderCheckoutItems() {
        const list = document.getElementById('coItemsList');
        list.innerHTML = '';
        
        if (cart.length === 0) {
            list.innerHTML = '<div style="text-align:center; color:#888; padding:20px;">Cart is empty</div>';
            return;
        }

        cart.forEach(item => {
            let rowGross = item.qty * item.price;
            let rowDisc = (item.disc_type === '%') ? (rowGross * item.disc_val / 100) : parseFloat(item.disc_val);
            let rowNet = rowGross - rowDisc;
            if (rowNet < 0) rowNet = 0;

            let discBadge = rowDisc > 0 ? ` &nbsp;|&nbsp; <span style="color:#c62828; font-weight:bold;">Disc: -Rs ${rowDisc.toFixed(2)}</span>` : '';
            if (item.is_free) {
                rowNet = 0;
                discBadge = ` &nbsp;|&nbsp; <span style="color:#ef6c00; font-weight:bold;">FREE ISSUE</span>`;
            }

            list.innerHTML += `
                <div class="co-item-row">
                    <div style="flex:1;" ${item.is_free ? '' : `onclick="editCartItem('${item.cartItemId}')"`}>
                        <div style="font-weight:bold; color:var(--text-dark); font-size:14px; margin-bottom:4px;">${item.name}</div>
                        <div style="font-size:12px; color:var(--text-muted);">${item.qty}x @ Rs ${parseFloat(item.price).toFixed(2)}${discBadge}</div>
                    </div>
                    <div style="display:flex; flex-direction:column; align-items:flex-end; gap:5px;">
                        <div style="font-weight:bold; font-size:14px; color:var(--text-dark);">Rs ${rowNet.toFixed(2)}</div>
                        <button type="button" class="co-del-btn" onclick="removeFromCart('${item.cartItemId}')">🗑️</button>
                    </div>
                </div>
            `;
        });
    }

    function removeFromCart(cartItemId) {
        cart = cart.filter(i => i.cartItemId !== cartItemId);
        if(typeof updateCartUI === 'function') updateCartUI(); 
        renderCheckoutItems(); 
        calcCheckout();
        if(cart.length === 0) closeCheckout();
    }

    function calcCheckout() {
        let subtotal = 0;
        cart.forEach(item => {
            let rowGross = item.qty * item.price;
            let rowDisc = (item.disc_type === '%') ? (rowGross * item.disc_val / 100) : parseFloat(item.disc_val);
            let rowNet = rowGross - rowDisc;
            if(rowNet < 0) rowNet = 0;
            if(item.is_free) rowNet = 0;
            subtotal += rowNet;
        });

        const globalDiscVal = parseFloat(document.getElementById('coGlobalDiscVal').value) || 0;
        const globalDiscType = document.getElementById('coGlobalDiscType').value;
        let billDisc = (globalDiscType === '%') ? (subtotal * globalDiscVal / 100) : globalDiscVal;
        
        let currentBill = subtotal - billDisc;
        if(currentBill < 0) currentBill = 0;

        const taxVal = parseFloat(document.getElementById('coTaxVal').value) || 0;
        const taxType = document.getElementById('coTaxType').value;
        let tax = (taxType === '%') ? (currentBill * taxVal / 100) : taxVal;

        const payable = currentBill + tax;

        const outstanding = typeof globalSelectedCustomerOutstanding !== 'undefined' ? globalSelectedCustomerOutstanding : 0;

        const cash = parseFloat(document.getElementById('payCash').value) || 0;
        const bank = parseFloat(document.getElementById('payBank').value) || 0;
        
        let cheque = 0;
        const repChequeInputs = document.querySelectorAll('.rep-cheque-amount');
        for (let i = 0; i < repChequeInputs.length; i++) {
            let val = parseFloat(repChequeInputs[i].value);
            if (!isNaN(val) && val > 0) {
                cheque += val;
            }
        }
        
        const collectionBox = document.getElementById('creditCollectionBox');
        if (outstanding > 0) {
            collectionBox.style.display = 'block';
        } else {
            collectionBox.style.display = 'none';
        }

        const collections = cash + bank + cheque;
        const projectedOutstanding = outstanding + payable - collections;

        document.getElementById('coSubtotal').innerText = `Rs ${subtotal.toFixed(2)}`;
        document.getElementById('coPayable').innerText = payable.toFixed(2);
        
        const projEl = document.getElementById('coProjectedOutstanding');
        projEl.innerText = projectedOutstanding.toFixed(2);
        
        if (projectedOutstanding <= 0) {
            projEl.style.color = '#2e7d32'; 
        } else {
            projEl.style.color = '#c62828'; 
        }
    }

    function isChequePaymentTermSelected() {
        const sel = document.getElementById('coPaymentTerm');
        if (!sel || !sel.value) return false;
        const opt = sel.options[sel.selectedIndex];
        return opt && opt.getAttribute('data-is-cheque') === '1';
    }

    function toggleTermChequeDate() {
        const wrap = document.getElementById('coChequeDateWrap');
        const input = document.getElementById('coTermChequeDate');
        if (!wrap || !input) return;
        const show = isChequePaymentTermSelected();
        wrap.style.display = show ? 'block' : 'none';
        if (show && !input.value) {
            input.value = new Date().toISOString().split('T')[0];
        }
    }

    function addRepChequeRow() {
        const container = document.getElementById('rep-cheques-container');
        const div = document.createElement('div');
        div.className = 'rep-cheque-card';
        div.style.padding = '10px 12px';
        div.style.background = '#fff8e1';
        div.style.border = '1px dashed #ffb300';
        div.style.borderRadius = '6px';
        div.style.position = 'relative';
        div.style.marginTop = '8px';
        
        const today = new Date().toISOString().split('T')[0];
        
        div.innerHTML = 
            '<button type="button" onclick="removeRepChequeRow(this)" style="position: absolute; top: 5px; right: 5px; background: none; border: none; font-size: 14px; color: #d32f2f; cursor: pointer; padding: 2px;">✕</button>' +
            '<label style="font-size: 10px; font-weight: bold; color: #f57c00; display: block; margin-bottom: 2px; text-transform: uppercase;">Cheque Amount</label>' +
            '<input type="number" class="rep-cheque-amount form-control" value="0.00" onfocus="if(this.value==\'0.00\')this.value=\'\';" onblur="if(this.value==\'\')this.value=\'0.00\';" oninput="calcCheckout()" style="margin-bottom: 6px; border-color: #ffecb3; height: 32px; font-size: 13px;">' +
            '<label style="font-size: 10px; font-weight: bold; color: #f57c00; display: block; margin-bottom: 2px; text-transform: uppercase;">Bank Name</label>' +
            '<input type="text" class="rep-cheque-bank form-control" placeholder="Bank Name" style="margin-bottom: 6px; border-color: #ffecb3; height: 32px; font-size: 13px;">' +
            '<label style="font-size: 10px; font-weight: bold; color: #f57c00; display: block; margin-bottom: 2px; text-transform: uppercase;">Cheque Number</label>' +
            '<input type="text" class="rep-cheque-number form-control" placeholder="Cheque Number" style="margin-bottom: 6px; border-color: #ffecb3; height: 32px; font-size: 13px;">' +
            '<label style="font-size: 10px; font-weight: bold; color: #f57c00; display: block; margin-bottom: 2px; text-transform: uppercase;">Banking Date</label>' +
            '<input type="date" class="rep-cheque-date form-control" style="border-color: #ffecb3; height: 32px; font-size: 13px;" value="' + today + '">';
            
        container.appendChild(div);
        calcCheckout();
    }

    function removeRepChequeRow(btn) {
        const card = btn.parentNode;
        card.parentNode.removeChild(card);
        calcCheckout();
    }

    function submitCheckout() {
        if (cart.length === 0) { alert('Cart is empty!'); return; }
        
        const customerId = typeof globalSelectedCustomerId !== 'undefined' ? globalSelectedCustomerId : null;
        if (!customerId) { alert('ERROR: You must select a Billing Customer from the top bar.'); return; }
        
        const termId = document.getElementById('coPaymentTerm').value;
        if (!termId) { alert('ERROR: You must select an Invoice Payment Term.'); return; }

        if (isChequePaymentTermSelected()) {
            const chequeDate = document.getElementById('coTermChequeDate').value.trim();
            if (!chequeDate) {
                alert('Please enter the Cheque Date (date written on the cheque).');
                return;
            }
        }

        // Validate cheque inputs
        const chequeCards = document.querySelectorAll('.rep-cheque-card');
        for (let i = 0; i < chequeCards.length; i++) {
            const card = chequeCards[i];
            const amt = parseFloat(card.querySelector('.rep-cheque-amount').value) || 0;
            const bank = card.querySelector('.rep-cheque-bank').value.trim();
            const num = card.querySelector('.rep-cheque-number').value.trim();
            const date = card.querySelector('.rep-cheque-date').value.trim();
            if (amt > 0) {
                if (!bank || !num || !date) {
                    alert("Please provide all Cheque Details (Bank Name, Cheque Number, and Date) for each entered cheque.");
                    return;
                }
            }
        }

        const submitBtn = document.getElementById('confirmSubmitBtn');
        submitBtn.disabled = true;
        submitBtn.innerText = "📍 Capturing Location & Saving...";
        submitBtn.style.opacity = '0.8';

        if ("geolocation" in navigator) {
            navigator.geolocation.getCurrentPosition(
                function(position) {
                    finalizeSubmit(position.coords.latitude, position.coords.longitude, customerId, termId);
                },
                function(error) {
                    alert("GPS Capture Failed: " + error.message + " - Saving order without location.");
                    finalizeSubmit(null, null, customerId, termId);
                },
                { enableHighAccuracy: true, timeout: 5000, maximumAge: 0 }
            );
        } else {
            finalizeSubmit(null, null, customerId, termId);
        }
    }
    
    function finalizeSubmit(lat, lng, customerId, termId) {
        let chequeTotal = 0;
        const chequesList = [];
        const chequeCards = document.querySelectorAll('.rep-cheque-card');
        for (let i = 0; i < chequeCards.length; i++) {
            const card = chequeCards[i];
            const amt = parseFloat(card.querySelector('.rep-cheque-amount').value) || 0;
            const bank = card.querySelector('.rep-cheque-bank').value.trim();
            const num = card.querySelector('.rep-cheque-number').value.trim();
            const date = card.querySelector('.rep-cheque-date').value.trim();
            if (amt > 0) {
                chequesList.push({
                    amount: amt,
                    bank: bank || 'Unknown',
                    number: num || 'Unknown',
                    date: date || new Date().toISOString().split('T')[0]
                });
                chequeTotal += amt;
            }
        }

        const payload = {
            customer_id: customerId,
            payment_term_id: termId,
            term_cheque_date: isChequePaymentTermSelected() ? document.getElementById('coTermChequeDate').value : null,
            cart: cart,
            discounts: {
                val: parseFloat(document.getElementById('coGlobalDiscVal').value) || 0,
                type: document.getElementById('coGlobalDiscType').value
            },
            tax: {
                val: parseFloat(document.getElementById('coTaxVal').value) || 0,
                type: document.getElementById('coTaxType').value
            },
            arrears_collections: {
                cash: parseFloat(document.getElementById('payCash').value) || 0,
                bank: parseFloat(document.getElementById('payBank').value) || 0,
                cheque: chequeTotal
            },
            cheques: chequesList,
            location: {
                lat: lat,
                lng: lng
            }
        };

        document.getElementById('checkoutPayload').value = JSON.stringify(payload);
        document.getElementById('checkoutForm').submit();
    }
</script>