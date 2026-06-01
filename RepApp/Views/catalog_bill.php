<?php
// Secure extraction of new controller variables
$customers = $data['customers'] ?? [];
$paymentTerms = $data['payment_terms'] ?? [];

$successInvoiceId = $_GET['success_invoice_id'] ?? null;
$successCustomerId = $_GET['customer_id'] ?? null;
$successCustomer = null;

// Failsafe: Dynamically calculate the TRUE outstanding balance for each customer 
// to ensure the POS Terminal "Arrears Collection" logic is perfectly accurate.
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

// Ensure the success modal doesn't crash if the customer isn't in the active route array
if ($successInvoiceId && !$successCustomer) {
    $db->query("SELECT * FROM customers WHERE id = :id");
    $db->bind(':id', $successCustomerId);
    $successCustomer = $db->single();
}
?>
<style>
    /* CSS hacks to make the catalog go edge-to-edge on mobile */
    .app-content { padding: 0 !important; background: var(--app-bg); display: flex; flex-direction: column; }
    
    .cat-header { background: var(--surface); padding: 15px; border-bottom: 1px solid var(--border); position: sticky; top: 0; z-index: 100;}
    .cat-scroll { display: flex; gap: 10px; overflow-x: auto; padding-bottom: 5px; scrollbar-width: none; -ms-overflow-style: none;}
    .cat-scroll::-webkit-scrollbar { display: none; }
    .cat-pill { padding: 8px 16px; background: var(--app-bg); border: 1px solid var(--border); border-radius: 20px; white-space: nowrap; font-size: 13px; font-weight: 600; color: var(--text-muted); cursor: pointer; transition: 0.2s;}
    .cat-pill.active { background: var(--primary); color: #fff; border-color: var(--primary); }

    .product-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; padding: 15px; flex: 1; overflow-y: auto; align-content: start; padding-bottom: 100px;}
    .product-card { background: var(--surface); border-radius: 12px; border: 1px solid var(--border); overflow: hidden; display: flex; flex-direction: column; box-shadow: 0 2px 8px rgba(0,0,0,0.02); cursor: pointer; transition: 0.2s;}
    .product-card:active { transform: scale(0.96); border-color: var(--primary); }
    .img-wrapper { width: 100%; aspect-ratio: 1/1; background: #eee; position: relative;}
    .img-wrapper img { width: 100%; height: 100%; object-fit: cover; }
    
    .stock-badge { position: absolute; top: 8px; right: 8px; background: rgba(0,0,0,0.6); color: #fff; padding: 4px 8px; border-radius: 12px; font-size: 10px; font-weight: bold; backdrop-filter: blur(4px);}
    .stock-out { background: rgba(198,40,40,0.9); }
    
    .prod-info { padding: 12px; flex: 1; display: flex; flex-direction: column; justify-content: flex-start;}
    .prod-name { font-size: 13px; font-weight: 600; color: var(--text-dark); margin: 0 0 5px 0; line-height: 1.3;}
    .prod-price { font-size: 15px; font-weight: bold; color: var(--primary); margin: 0;}
    
    /* The Floating Cart Footer */
    .cart-bar { position: absolute; bottom: 65px; left: 15px; right: 15px; background: #111; color: #fff; border-radius: 12px; padding: 15px 20px; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 10px 25px rgba(0,0,0,0.3); z-index: 100;}
    .cart-bar-info { display: flex; flex-direction: column; }
    .cart-total { font-size: 18px; font-weight: bold; color: #4caf50;}
    .cart-items { font-size: 12px; color: #aaa; }
    .checkout-btn { background: #4caf50; color: #fff; border: none; padding: 10px 20px; border-radius: 8px; font-weight: bold; font-size: 14px; cursor: pointer;}

    /* Native App Bottom Sheet UI for Configuration */
    .sheet-overlay { display: none; position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); z-index: 4000; align-items: flex-end; justify-content: center; }
    .bottom-sheet { background: var(--app-bg); width: 100%; max-width: 480px; border-radius: 20px 20px 0 0; padding: 25px 20px; box-sizing: border-box; transform: translateY(100%); transition: transform 0.3s ease-out; max-height: 90vh; overflow-y: auto; position: relative;}
    .bottom-sheet.open { transform: translateY(0); }
    .sheet-handle { width: 40px; height: 5px; background: #ccc; border-radius: 5px; margin: 0 auto 20px auto; }
    
    .pm-row { background: var(--surface); padding: 15px; border-radius: 8px; border: 1px solid var(--border); margin-bottom: 10px; display: flex; flex-direction: column; gap: 10px;}
    .pm-row-header { display: flex; justify-content: space-between; align-items: center; font-weight: bold; font-size: 14px; color: var(--text-dark);}
    .pm-inputs { display: flex; gap: 10px; }
    .pm-input-group { flex: 1; display: flex; flex-direction: column; gap: 5px; }
    .pm-input-group label { font-size: 11px; color: var(--text-muted); text-transform: uppercase; font-weight: bold;}
    .pm-input-group input, .pm-input-group select { width: 100%; padding: 10px; border: 1px solid var(--border); border-radius: 6px; font-size: 15px; font-weight: bold; box-sizing: border-box; background: var(--app-bg); color: var(--text-dark); outline: none;}
    .pm-input-group input:focus, .pm-input-group select:focus { border-color: var(--primary); }
    
    .discount-box { display: flex; align-items: center; gap: 10px; background: rgba(0,102,204,0.05); padding: 15px; border-radius: 8px; border: 1px solid rgba(0,102,204,0.2); margin-bottom: 15px;}
    .discount-box select { padding: 10px; border: 1px solid var(--border); border-radius: 6px; background: var(--surface); color: var(--text-dark); font-size: 14px; font-weight: bold; outline: none;}

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
    .pay-input-box { width: 60%; border-radius: 8px; padding: 12px; text-align: right; font-size: 16px; font-weight: bold; font-family: monospace; outline: none;}

    .pay-cash { background: #e8f5e9; color: #2e7d32; border: 1px solid #c8e6c9; }
    .pay-bank { background: #e3f2fd; color: #1565c0; border: 1px solid #bbdefb; }
    .pay-cheque { background: #fff8e1; color: #f57c00; border: 1px solid #ffecb3; }

    .co-totals-row { display: flex; justify-content: space-between; padding: 8px 0; font-size: 14px; color: var(--text-muted);}
    .co-payable-row { display: flex; justify-content: space-between; padding: 15px 0; font-size: 20px; font-weight: bold; color: #0066cc; border-top: 1px dashed #ccc; border-bottom: 1px dashed #ccc; margin-top: 10px;}
    .co-due-row { display: flex; justify-content: space-between; font-size: 16px; font-weight: bold; color: var(--text-dark); margin-top: 20px; border-top: 2px solid var(--border); padding-top: 15px;}
    
    .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; }
</style>

<div class="cat-header">
    <!-- Sticky Active Customer Bar -->
    <div id="activeCustomerBar" style="padding-bottom: 15px; margin-bottom: 15px; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; cursor: pointer;" onclick="openCustomerSheet()">
        <div>
            <div style="font-size: 11px; color: var(--text-muted); text-transform: uppercase; font-weight: bold; margin-bottom: 3px;">Billing Customer (Tap to Change)</div>
            <div id="activeCustomerName" style="font-weight: bold; color: var(--primary); font-size: 16px;">Tap to select customer...</div>
        </div>
        <div style="text-align: right; display: none;" id="activeCustomerOwsBox">
            <div style="font-size: 11px; color: var(--text-muted); text-transform: uppercase; font-weight: bold; margin-bottom: 3px;">Outstanding</div>
            <div id="activeCustomerOws" style="font-weight: bold; font-size: 15px;">Rs: 0.00</div>
        </div>
    </div>

    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
        <span style="font-size: 11px; color: var(--text-muted); text-transform: uppercase; font-weight: bold;">Categories</span>
        <button type="button" onclick="openFreeIssueSheet()" style="background: #ef6c00; color: white; border: none; padding: 6px 12px; border-radius: 20px; font-weight: bold; cursor: pointer; font-size: 12px; display: flex; align-items: center; gap: 4px; box-shadow: 0 2px 5px rgba(239, 108, 0, 0.2);">
            🎁 Add Free Issue
        </button>
    </div>

    <!-- Swipeable Category Nav -->
    <div class="cat-scroll">
        <div class="cat-pill active" onclick="filterCat('all', this)">All Items</div>
        <?php foreach($data['categories'] as $cat): ?>
            <div class="cat-pill" onclick="filterCat(<?= $cat->id ?>, this)"><?= htmlspecialchars($cat->name) ?></div>
        <?php endforeach; ?>
    </div>
</div>

<!-- Dynamic Image Grid -->
<div class="product-grid" id="prodGrid">
    <?php foreach($data['products'] as $prod): ?>
        <?php 
            $hasVars = !empty($prod->variations);
            
            // Calculate available stock: On Hand minus Reserved
            $reservedAmt = $prod->quantity_reserved ?? 0;
            $availableStock = ($prod->quantity_on_hand ?? 0) - $reservedAmt;
            
            $isOut = $prod->type === 'Inventory' && !$hasVars && $availableStock <= 0;
            
            // Attach live available stock values over the object before encoding to JS
            $prod->available_stock = $availableStock; 
            if($hasVars) {
                foreach($prod->variations as $v) {
                    $vReserved = $v->quantity_reserved ?? 0;
                    $v->available_stock = ($v->quantity_on_hand ?? 0) - $vReserved;
                }
            }
            
            // JSON Encode the product safely so it can be passed directly into JS
            $jsonProd = htmlspecialchars(json_encode($prod), ENT_QUOTES, 'UTF-8');
        ?>
        <div class="product-card cat-<?= $prod->category_id ?: 'none' ?>" data-id="<?= $prod->id ?>" data-product="<?= $jsonProd ?>" onclick="openProductModal(this)">
            <div class="img-wrapper">
                <?php 
                $img_src = '';
                if ($prod->image_path) {
                    $filename = basename($prod->image_path);
                    $img_src = APP_URL . '/uploads/products/' . $filename;
                }
                ?>
                <?php if($img_src): ?>
                    <img src="<?= $img_src ?>" alt="Product" onerror="this.src='https://placehold.co/300?text=No+Image'">
                <?php else: ?>
                    <div style="width:100%; height:100%; display:flex; align-items:center; justify-content:center; color:#aaa; font-size:12px;">No Image</div>
                <?php endif; ?>
                
                <?php if($prod->type === 'Service'): ?>
                    <div class="stock-badge" style="background: rgba(106,27,154,0.8);">Service</div>
                <?php elseif($hasVars): ?>
                    <div class="stock-badge" style="background: rgba(0,102,204,0.8);">Variations</div>
                <?php elseif($isOut): ?>
                    <div class="stock-badge stock-out">Out of Stock</div>
                <?php else: ?>
                    <div class="stock-badge"><?= $availableStock ?> available</div>
                <?php endif; ?>
            </div>
            
            <div class="prod-info">
                <h4 class="prod-name"><?= htmlspecialchars($prod->name) ?></h4>
                <div class="prod-price">Rs: <?= number_format($prod->price, 2) ?></div>
            </div>
        </div>
    <?php endforeach; ?>
</div>

<!-- Floating Action Bar (Cart) -->
<div class="cart-bar" id="cartBar" style="display: none;">
    <div class="cart-bar-info">
        <span class="cart-total" id="cartTotal">Rs: 0.00</span>
        <span class="cart-items" id="cartCount">0 items in cart</span>
    </div>
    <button class="checkout-btn" onclick="openCheckout()">Checkout &rarr;</button>
</div>

<!-- Customer Selection Modal (Bottom Sheet) -->
<div class="sheet-overlay" id="customerSheetOverlay" style="z-index: 5000;">
    <div class="bottom-sheet" id="customerSheet">
        <div class="sheet-handle" onclick="closeCustomerSheet()"></div>
        <h3 style="margin-top:0; color:var(--text-dark); margin-bottom: 15px;">Select Billing Customer</h3>
        
        <div class="form-group">
            <select id="globalCustomerSelect" class="form-control" style="font-size: 16px; padding: 12px; font-weight: bold; outline: none;" onchange="handleGlobalCustomerChange()">
                <option value="">Select Customer...</option>
                <?php foreach($customers as $c): ?>
                    <option value="<?= $c->id ?>" 
                            data-name="<?= htmlspecialchars($c->name) ?>" 
                            data-phone="<?= htmlspecialchars($c->phone ?: 'No Phone') ?>" 
                            data-outstanding="<?= $c->outstanding_balance ?? 0 ?>">
                        <?= htmlspecialchars($c->name) ?>
                    </option>
                <?php endforeach; ?>
            </select>
        </div>

        <div id="customerDetailsBox" style="display:none; margin-top: 15px; padding: 15px; background: rgba(0,0,0,0.02); border: 1px solid var(--border); border-radius: 8px;">
            <h4 id="cdName" style="margin: 0 0 5px 0; font-size: 18px; color: var(--text-dark);"></h4>
            <p id="cdPhone" style="margin: 0 0 15px 0; font-size: 13px; color: var(--text-muted);"></p>
            
            <div id="cdOutstandingBox" style="font-size: 15px; font-weight: bold; padding: 12px; border-radius: 6px; border: 1px dashed transparent;">
                Previous Outstanding: <span id="cdOutstandingAmount">0.00</span>
            </div>
        </div>

        <div style="margin-top: 25px;">
            <button class="btn-primary" onclick="confirmGlobalCustomer()">Confirm & Continue Billing</button>
        </div>
    </div>
</div>

<!-- Product Configuration Modal (Bottom Sheet) -->
<div class="sheet-overlay" id="prodSheetOverlay">
    <div class="bottom-sheet" id="prodSheet">
        <div class="sheet-handle" onclick="closeProductModal()"></div>
        <h3 id="pmTitle" style="margin-top:0; color:var(--text-dark); margin-bottom: 5px;">Product Name</h3>
        <p id="pmStockInfo" style="font-size: 13px; font-weight: bold; color: var(--primary); margin-bottom: 20px;">Available Stock: 10</p>
        
        <div class="discount-box">
            <div class="pm-input-group" style="flex: 1;">
                <label style="color:#0066cc;">Item-Wise Discount</label>
                <div style="display:flex; gap:5px;">
                    <input type="number" id="pmDiscVal" value="0" min="0" step="0.01" style="border-color: rgba(0,102,204,0.3);">
                    <select id="pmDiscType" style="border-color: rgba(0,102,204,0.3);">
                        <option value="Rs">Rs</option>
                        <option value="%">%</option>
                    </select>
                </div>
            </div>
        </div>

        <div id="pmVariantsContainer">
            <!-- Dynamic Variant Rows Injected Here -->
        </div>

        <div style="display: flex; gap: 10px; margin-top: 20px;">
            <button class="btn-primary" style="flex: 1; background: transparent; color: var(--text-muted); border: 1px solid var(--border); box-shadow: none;" onclick="closeProductModal()">Cancel</button>
            <button class="btn-primary" style="flex: 2;" onclick="confirmAddToCart()">Confirm Item</button>
        </div>
    </div>
</div>

<?php include 'checkout_overlay.php'; ?>

<script>
    let cart = [];
    let currentProd = null;
    let globalSelectedCustomerId = null;
    let globalSelectedCustomerOutstanding = 0;
    let pendingProductModalStr = null;

    function filterCat(id, el) {
        document.querySelectorAll('.cat-pill').forEach(p => p.classList.remove('active'));
        el.classList.add('active');
        
        document.querySelectorAll('.product-card').forEach(card => {
            if(id === 'all' || card.classList.contains('cat-' + id)) {
                card.style.display = 'flex';
            } else {
                card.style.display = 'none';
            }
        });
    }

    // --- Global Customer Selection Engine ---

    function openCustomerSheet() {
        document.getElementById('customerSheetOverlay').style.display = 'flex';
        setTimeout(() => { document.getElementById('customerSheet').classList.add('open'); }, 10);
        handleGlobalCustomerChange();
    }

    function closeCustomerSheet() {
        document.getElementById('customerSheet').classList.remove('open');
        setTimeout(() => { document.getElementById('customerSheetOverlay').style.display = 'none'; }, 300);
        pendingProductModalStr = null;
    }

    function handleGlobalCustomerChange() {
        const select = document.getElementById('globalCustomerSelect');
        const detailsBox = document.getElementById('customerDetailsBox');
        const cdName = document.getElementById('cdName');
        const cdPhone = document.getElementById('cdPhone');
        const outBox = document.getElementById('cdOutstandingBox');
        const outAmount = document.getElementById('cdOutstandingAmount');

        if (select.value === '') {
            detailsBox.style.display = 'none';
            return;
        }

        const opt = select.options[select.selectedIndex];
        const outstanding = parseFloat(opt.getAttribute('data-outstanding')) || 0;
        
        detailsBox.style.display = 'block';
        cdName.innerText = opt.getAttribute('data-name');
        cdPhone.innerText = opt.getAttribute('data-phone');

        if (outstanding > 0) {
            outBox.style.background = '#ffebee';
            outBox.style.borderColor = '#ef9a9a';
            outBox.style.color = '#c62828';
            outAmount.innerText = 'Rs: ' + outstanding.toLocaleString('en-IN', {minimumFractionDigits: 2});
        } else if (outstanding < 0) {
            outBox.style.background = '#e8f5e9';
            outBox.style.borderColor = '#a5d6a7';
            outBox.style.color = '#2e7d32';
            outAmount.innerText = 'Rs: ' + Math.abs(outstanding).toLocaleString('en-IN', {minimumFractionDigits: 2}) + ' (Credit)';
        } else {
            outBox.style.background = '#e8f5e9';
            outBox.style.borderColor = '#a5d6a7';
            outBox.style.color = '#2e7d32';
            outAmount.innerText = 'Rs: 0.00 (All Cleared)';
        }
    }

    function confirmGlobalCustomer() {
        const select = document.getElementById('globalCustomerSelect');
        if (select.value === '') {
            alert('Please select a customer first to start billing.');
            return;
        }

        const opt = select.options[select.selectedIndex];
        globalSelectedCustomerId = select.value;
        globalSelectedCustomerOutstanding = parseFloat(opt.getAttribute('data-outstanding')) || 0;

        // Update Top Active Bar
        document.getElementById('activeCustomerName').innerText = opt.getAttribute('data-name');
        const topOwsBox = document.getElementById('activeCustomerOwsBox');
        const topOws = document.getElementById('activeCustomerOws');
        
        topOwsBox.style.display = 'block';
        if (globalSelectedCustomerOutstanding > 0) {
            topOws.style.color = '#c62828';
            topOws.innerText = 'Rs: ' + globalSelectedCustomerOutstanding.toLocaleString('en-IN', {minimumFractionDigits: 2});
        } else if (globalSelectedCustomerOutstanding < 0) {
            topOws.style.color = '#2e7d32';
            topOws.innerText = 'Cr: Rs: ' + Math.abs(globalSelectedCustomerOutstanding).toLocaleString('en-IN', {minimumFractionDigits: 2});
        } else {
            topOws.style.color = '#2e7d32';
            topOws.innerText = 'Rs: 0.00';
        }

        // Update Checkout UI
        document.getElementById('checkoutCustomerName').innerText = opt.getAttribute('data-name');
        document.getElementById('checkoutCustomerOws').innerText = topOws.innerText;
        document.getElementById('checkoutCustomerOws').style.color = topOws.style.color;

        closeCustomerSheet();
        
        // Recalculate checkout if it was open
        if (document.getElementById('checkoutOverlay').style.display === 'flex') {
            calcCheckout(); 
        }

        // If a product was tapped before selecting the customer, instantly open it now
        if (pendingProductModalStr) {
            setTimeout(() => {
                openProductModal(pendingProductModalStr, true);
                pendingProductModalStr = null;
            }, 350);
        }
    }

    // --- PRODUCT MODAL LOGIC ---

    function openProductModal(trigger, bypassCustomerCheck = false) {
        let prodStr = '';
        if (typeof trigger === 'object' && trigger !== null && trigger.getAttribute) {
            prodStr = trigger.getAttribute('data-product');
        } else {
            prodStr = trigger;
        }

        // INTERCEPT: Force customer selection before allowing items to be added
        if (!globalSelectedCustomerId && !bypassCustomerCheck) {
            pendingProductModalStr = prodStr;
            openCustomerSheet();
            return;
        }

        // Fix: Gracefully parse only if it is passed as a string configuration
        const prod = (typeof prodStr === 'string') ? JSON.parse(prodStr) : prodStr;
        console.log('[DEBUG] openProductModal opened. Product ID:', prod.id, 'Name:', prod.name, 'Image Path:', prod.image_path);
        currentProd = prod;
        
        document.getElementById('pmTitle').innerText = prod.name;
        document.getElementById('pmDiscVal').value = 0;
        document.getElementById('pmDiscType').value = 'Rs';
        
        const isService = prod.type === 'Service';
        document.getElementById('pmStockInfo').innerText = isService ? 'Service Item (No Stock Limit)' : `Available Stock (Net): ${prod.available_stock}`;
        
        const container = document.getElementById('pmVariantsContainer');
        container.innerHTML = '';
        
        let hasCartItems = false;

        if (prod.variations && prod.variations.length > 0) {
            prod.variations.forEach(v => {
                let cartItemId = `${prod.id}_${v.id}`;
                let cartItem = cart.find(c => c.cartItemId === cartItemId);
                if(cartItem) hasCartItems = true;

                let stockLabel = isService ? 'N/A' : v.available_stock;
                let stockColor = (!isService && v.available_stock <= 0) ? '#c62828' : 'var(--text-muted)';
                let price = cartItem ? parseFloat(cartItem.price).toFixed(2) : (v.price ? parseFloat(v.price).toFixed(2) : parseFloat(prod.price).toFixed(2));
                let qty = cartItem ? cartItem.qty : 0;
                let stockLimit = isService ? '' : `max="${v.available_stock}"`;
                
                container.innerHTML += `
                    <div class="pm-row">
                        <div class="pm-row-header">
                            <span>${v.variation_name}: ${v.value_name}</span>
                            <span style="font-size:12px; font-weight:bold; color:${stockColor};">Stock: ${stockLabel}</span>
                        </div>
                        <div class="pm-inputs">
                            <div class="pm-input-group">
                                <label>Edit Price (Rs:)</label>
                                <input type="number" id="pmPrice_${v.id}" value="${price}" step="0.01" min="0">
                            </div>
                            <div class="pm-input-group">
                                <label>QTY to Bill</label>
                                <input type="number" id="pmQty_${v.id}" value="${qty}" step="1" min="0" ${stockLimit} oninput="validateModalQty(this, ${isService})">
                            </div>
                        </div>
                    </div>
                `;
            });
        } else {
            let cartItemId = `${prod.id}_0`;
            let cartItem = cart.find(c => c.cartItemId === cartItemId);
            if(cartItem) hasCartItems = true;

            let stockLabel = isService ? 'N/A' : prod.available_stock;
            let stockColor = (!isService && prod.available_stock <= 0) ? '#c62828' : 'var(--text-muted)';
            let price = cartItem ? parseFloat(cartItem.price).toFixed(2) : parseFloat(prod.price).toFixed(2);
            let qty = cartItem ? cartItem.qty : 0; 
            let stockLimit = isService ? '' : `max="${prod.available_stock}"`;
            
            container.innerHTML += `
                <div class="pm-row">
                    <div class="pm-row-header">
                        <span>Standard Item</span>
                        <span style="font-size:12px; font-weight:bold; color:${stockColor};">Stock: ${stockLabel}</span>
                    </div>
                    <div class="pm-inputs">
                        <div class="pm-input-group">
                            <label>Edit Price (Rs:)</label>
                            <input type="number" id="pmPrice_base" value="${price}" step="0.01" min="0">
                        </div>
                        <div class="pm-input-group">
                            <label>QTY to Bill</label>
                            <input type="number" id="pmQty_base" value="${qty}" step="1" min="0" ${stockLimit} oninput="validateModalQty(this, ${isService})">
                        </div>
                    </div>
                </div>
            `;
        }
        
        if (hasCartItems) {
            let firstFound = cart.find(c => c.itemId == prod.id);
            if(firstFound) {
                document.getElementById('pmDiscVal').value = firstFound.disc_val;
                document.getElementById('pmDiscType').value = firstFound.disc_type;
            }
        }

        document.getElementById('prodSheetOverlay').style.display = 'flex';
        setTimeout(() => { document.getElementById('prodSheet').classList.add('open'); }, 10);
    }

    function closeProductModal() {
        document.getElementById('prodSheet').classList.remove('open');
        setTimeout(() => { document.getElementById('prodSheetOverlay').style.display = 'none'; }, 300);
    }

    function validateModalQty(input, isService) {
        if (!isService && input.hasAttribute('max')) {
            let max = parseFloat(input.getAttribute('max'));
            let val = parseFloat(input.value);
            if (val > max) {
                alert("Insufficient stock! Only " + max + " available.");
                input.value = max;
            }
        }
    }

    function confirmAddToCart() {
        if (!currentProd) return;
        
        const discVal = parseFloat(document.getElementById('pmDiscVal').value) || 0;
        const discType = document.getElementById('pmDiscType').value;
        
        if (currentProd.variations && currentProd.variations.length > 0) {
            currentProd.variations.forEach(v => {
                let qtyInput = document.getElementById(`pmQty_${v.id}`);
                let priceInput = document.getElementById(`pmPrice_${v.id}`);
                let qty = parseFloat(qtyInput.value) || 0;
                let price = parseFloat(priceInput.value) || 0;
                
                let varName = `${v.variation_name}: ${v.value_name}`;
                processAdd(currentProd.id, currentProd.name, price, v.id, varName, qty, discVal, discType, currentProd.type);
            });
        } else {
            let qtyInput = document.getElementById(`pmQty_base`);
            let priceInput = document.getElementById(`pmPrice_base`);
            let qty = parseFloat(qtyInput.value) || 0;
            let price = parseFloat(priceInput.value) || 0;
            
            processAdd(currentProd.id, currentProd.name, price, null, null, qty, discVal, discType, currentProd.type);
        }
        
        updateCartUI();
        closeProductModal();
        
        if (document.getElementById('checkoutOverlay').style.display === 'flex') {
            renderCheckoutItems();
            calcCheckout();
            if(cart.length === 0) closeCheckout();
        }
    }

    function processAdd(itemId, name, price, varId, varName, qty, discVal, discType, type) {
        let fullName = varName ? `${name} - ${varName}` : name;
        let cartItemId = varId ? `${itemId}_${varId}` : `${itemId}_0`;
        let existingIndex = cart.findIndex(i => i.cartItemId === cartItemId);
        
        if (qty > 0) {
            let newItem = {
                cartItemId: cartItemId,
                itemId: itemId,
                varId: varId,
                name: fullName,
                price: price,
                qty: qty,
                disc_val: discVal,
                disc_type: discType,
                type: type,
                rawProd: currentProd 
            };
            
            if (existingIndex >= 0) {
                cart[existingIndex] = newItem;
            } else {
                cart.push(newItem);
            }
        } else {
            if (existingIndex >= 0) {
                cart.splice(existingIndex, 1);
            }
        }
    }

    function updateCartUI() {
        const cartBar = document.getElementById('cartBar');
        if(cart.length === 0) {
            cartBar.style.display = 'none';
            return;
        }
        
        cartBar.style.display = 'flex';
        let totalQty = 0;
        let totalAmt = 0;
        
        cart.forEach(i => {
            totalQty += i.qty;
            let rowGross = i.qty * i.price;
            let rowDisc = (i.disc_type === '%') ? (rowGross * i.disc_val / 100) : parseFloat(i.disc_val);
            let rowNet = rowGross - rowDisc;
            if(rowNet < 0) rowNet = 0;
            if(i.is_free) rowNet = 0;
            totalAmt += rowNet;
        });
        
        document.getElementById('cartCount').innerText = `${totalQty} units in cart`;
        document.getElementById('cartTotal').innerText = `Rs: ${totalAmt.toLocaleString('en-IN', {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;
    }
</script>