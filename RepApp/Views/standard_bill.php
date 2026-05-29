<?php
// Secure extraction of controller variables
$customers = $data['customers'] ?? [];
$paymentTerms = $data['payment_terms'] ?? [];
$products = $data['products'] ?? [];

$successInvoiceId = $_GET['success_invoice_id'] ?? null;
$successCustomerId = $_GET['customer_id'] ?? null;
$successCustomer = null;

// Failsafe: Dynamically calculate the TRUE outstanding balance for each customer 
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
?>
<style>
    .app-content { padding: 0 !important; background: var(--app-bg); display: flex; flex-direction: column; }
    .sticky-header { background: var(--surface); position: sticky; top: 0; z-index: 100; border-bottom: 1px solid var(--border); box-shadow: 0 2px 10px rgba(0,0,0,0.02);}
    .search-container { padding: 15px; position: relative; }
    .search-input { width: 100%; padding: 12px 15px 12px 40px; border: 2px solid var(--primary); border-radius: 8px; font-size: 15px; font-weight: bold; box-sizing: border-box; background: var(--surface); color: var(--text-dark); outline: none; }
    .search-icon { position: absolute; left: 25px; top: 50%; transform: translateY(-50%); font-size: 16px; color: var(--primary); }
    .search-results { position: absolute; top: 100%; left: 15px; right: 15px; background: var(--surface); border: 1px solid var(--border); border-top: none; max-height: 250px; overflow-y: auto; z-index: 1000; display: none; box-shadow: 0 10px 20px rgba(0,0,0,0.1); border-radius: 0 0 8px 8px; }
    .search-results li { padding: 12px 15px; cursor: pointer; list-style: none; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center;}
    .search-results li:hover { background: rgba(0,102,204,0.05); }
    .main-cart-area { flex: 1; overflow-y: auto; padding: 15px; padding-bottom: 100px; }
    .empty-cart-msg { text-align: center; color: var(--text-muted); font-size: 14px; padding: 40px 20px; border: 2px dashed var(--border); border-radius: 12px; margin-top: 20px;}
    .line-item { background: var(--surface); border-radius: 8px; border: 1px solid var(--border); padding: 15px; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 2px 5px rgba(0,0,0,0.02); }
    .line-item-info { flex: 1; }
    .line-item-name { font-weight: bold; font-size: 14px; color: var(--text-dark); margin-bottom: 5px; }
    .line-item-meta { font-size: 12px; color: var(--text-muted); display: flex; gap: 10px; }
    .line-item-price { font-weight: bold; font-size: 15px; color: var(--text-dark); text-align: right; }
    .edit-btn { background: rgba(0,102,204,0.1); color: var(--primary); border: none; padding: 6px 12px; border-radius: 6px; font-weight: bold; font-size: 12px; cursor: pointer; margin-left: 10px; }
    .cart-bar { position: absolute; bottom: 65px; left: 15px; right: 15px; background: #111; color: #fff; border-radius: 12px; padding: 15px 20px; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 10px 25px rgba(0,0,0,0.3); z-index: 100;}
    .cart-bar-info { display: flex; flex-direction: column; }
    .cart-total { font-size: 18px; font-weight: bold; color: #4caf50;}
    .cart-items { font-size: 12px; color: #aaa; }
    .checkout-btn { background: #4caf50; color: #fff; border: none; padding: 10px 20px; border-radius: 8px; font-weight: bold; font-size: 14px; cursor: pointer;}
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
</style>

<div class="sticky-header">
    <div id="activeCustomerBar" style="padding: 15px; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; cursor: pointer; background: var(--surface);" onclick="openCustomerSheet()">
        <div>
            <div style="font-size: 11px; color: var(--text-muted); text-transform: uppercase; font-weight: bold; margin-bottom: 3px;">Billing Customer (Tap to Change)</div>
            <div id="activeCustomerName" style="font-weight: bold; color: var(--primary); font-size: 16px;">Tap to select customer...</div>
        </div>
        <div style="text-align: right; display: none;" id="activeCustomerOwsBox">
            <div style="font-size: 11px; color: var(--text-muted); text-transform: uppercase; font-weight: bold; margin-bottom: 3px;">Outstanding</div>
            <div id="activeCustomerOws" style="font-weight: bold; font-size: 15px;">Rs: 0.00</div>
        </div>
    </div>
    <div class="search-container" style="display: flex; gap: 10px; align-items: center;">
        <div style="position: relative; flex: 1;">
            <span class="search-icon">🔍</span>
            <input type="text" id="itemSearch" class="search-input" placeholder="Search item code or name..." onkeyup="filterSearch(event)" autocomplete="off">
            <ul id="searchResults" class="search-results"></ul>
        </div>
        <button type="button" class="btn" onclick="openFreeIssueSheet()" style="background: #ef6c00; color: white; border: none; padding: 12px 15px; border-radius: 8px; font-weight: bold; cursor: pointer; font-size: 14px; white-space: nowrap; box-sizing: border-box; height: 48px; display: flex; align-items: center; gap: 5px;">
            🎁 Free Issue
        </button>
    </div>
</div>

<div class="main-cart-area" id="mainCartArea">
    <div class="empty-cart-msg" id="emptyCartMsg">
        <div style="font-size: 30px; margin-bottom: 10px;">🛒</div>
        Your bill is currently empty.<br>Search and select an item above to add it.
    </div>
    <div id="activeCartList"></div>
</div>

<div class="cart-bar" id="cartBar" style="display: none;">
    <div class="cart-bar-info">
        <span class="cart-total" id="cartTotal">Rs: 0.00</span>
        <span class="cart-items" id="cartCount">0 items in cart</span>
    </div>
    <button class="checkout-btn" onclick="openCheckout()">Checkout &rarr;</button>
</div>

<div class="sheet-overlay" id="customerSheetOverlay" style="z-index: 5000;">
    <div class="bottom-sheet" id="customerSheet">
        <div class="sheet-handle" onclick="closeCustomerSheet()"></div>
        <h3 style="margin-top:0; color:var(--text-dark); margin-bottom: 15px;">Select Billing Customer</h3>
        <div class="form-group">
            <select id="globalCustomerSelect" class="form-control" style="font-size: 16px; padding: 12px; font-weight: bold; outline: none;" onchange="handleGlobalCustomerChange()">
                <option value="">Select Customer...</option>
                <?php foreach($customers as $c): ?>
                    <option value="<?= $c->id ?>" data-name="<?= htmlspecialchars($c->name) ?>" data-phone="<?= htmlspecialchars($c->phone ?: 'No Phone') ?>" data-outstanding="<?= $c->outstanding_balance ?? 0 ?>">
                        <?= htmlspecialchars($c->name) ?>
                    </option>
                <?php endforeach; ?>
            </select>
        </div>
        <div id="customerDetailsBox" style="display:none; margin-top: 15px; padding: 15px; background: rgba(0,0,0,0.02); border: 1px solid var(--border); border-radius: 8px;">
            <h4 id="cdName" style="margin: 0 0 5px 0; font-size: 18px; color: var(--text-dark);"></h4>
            <p id="cdPhone" style="margin: 0 0 15px 0; font-size: 13px; color: var(--text-muted);"></p>
            <div id="cdOutstandingBox" style="font-size: 15px; font-weight: bold; padding: 12px; border-radius: 6px; border: 1px dashed transparent;">Previous Outstanding: <span id="cdOutstandingAmount">0.00</span></div>
        </div>
        <div style="margin-top: 25px;">
            <button class="btn-primary" onclick="confirmGlobalCustomer()">Confirm & Continue Billing</button>
        </div>
    </div>
</div>

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
        <div id="pmVariantsContainer"></div>
        <div style="display: flex; gap: 10px; margin-top: 20px;">
            <button class="btn-primary" style="flex: 1; background: transparent; color: var(--text-muted); border: 1px solid var(--border); box-shadow: none;" onclick="closeProductModal()">Cancel</button>
            <button class="btn-primary" style="flex: 2;" onclick="confirmAddToCart()">Confirm Item</button>
        </div>
    </div>
</div>

<?php include 'checkout_overlay.php'; ?>

<script>
    // Build Base-Only Catalog using Secure Native JSON output from PHP
    const catalogDb = <?= json_encode(array_map(function($prod) {
        $hasVars = !empty($prod->variations);
        $totalReserved = $prod->quantity_reserved ?? 0;
        $availableStock = ($prod->quantity_on_hand ?? 0) - $totalReserved;
        
        // Setup variation available stocks
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
            'rawProd' => $prod // Embeds the entire product object perfectly without string escapes
        ];
    }, $products)); ?>;

    let cart = [];
    let currentProd = null;
    let globalSelectedCustomerId = null;
    let globalSelectedCustomerOutstanding = 0;
    let pendingProductModalObj = null;

    function filterSearch(e) {
        const val = e.target.value.toLowerCase().trim();
        const resList = document.getElementById('searchResults');
        resList.innerHTML = '';
        if(!val) { resList.style.display = 'none'; return; }

        const filtered = catalogDb.filter(i => i.name.toLowerCase().includes(val) || (i.code && i.code.toLowerCase().includes(val))).slice(0, 15);
        if(filtered.length === 0) { resList.style.display = 'none'; return; }

        filtered.forEach(item => {
            const li = document.createElement('li');
            
            let stockBadge = '';
            if (item.type === 'Service') {
                stockBadge = `<span style="color:#0066cc; font-size: 11px; font-weight:bold;">Service</span>`;
            } else if (item.has_variations) {
                stockBadge = `<span style="color:#0066cc; font-size: 11px; font-weight:bold;">Variations Available</span>`;
            } else if (item.available_stock > 0) {
                stockBadge = `<span style="color:#2e7d32; font-size: 11px; font-weight:bold;">Available: ${item.available_stock}</span>`;
            } else {
                stockBadge = `<span style="color:#c62828; font-size: 11px; font-weight:bold;">Out of Stock (Reserved)</span>`;
            }

            li.innerHTML = `
                <div>
                    <div style="font-weight:bold; color:var(--text-dark); font-size:14px; margin-bottom:3px;">${item.name}</div>
                    <span style="font-size: 11px; color: var(--text-muted);">${item.code ? 'SKU: '+item.code + ' | ' : ''}${stockBadge}</span>
                </div>
                <div style="color: var(--primary); font-weight: bold; font-size: 14px;">Rs: ${parseFloat(item.price).toFixed(2)}</div>
            `;
            li.onclick = () => { 
                openProductModal(item.rawProd); // Safely pass the actual javascript object
                e.target.value = ''; 
                resList.style.display = 'none'; 
                document.activeElement.blur();
            };
            resList.appendChild(li);
        });
        resList.style.display = 'block';
    }

    document.addEventListener('click', function(e) {
        if(e.target.id !== 'itemSearch') { document.getElementById('searchResults').style.display = 'none'; }
    });

    // --- Global Customer Selection Engine ---
    function openCustomerSheet() {
        document.getElementById('customerSheetOverlay').style.display = 'flex';
        setTimeout(() => { document.getElementById('customerSheet').classList.add('open'); }, 10);
        handleGlobalCustomerChange();
    }

    function closeCustomerSheet() {
        document.getElementById('customerSheet').classList.remove('open');
        setTimeout(() => { document.getElementById('customerSheetOverlay').style.display = 'none'; }, 300);
        pendingProductModalObj = null;
    }

    function handleGlobalCustomerChange() {
        const select = document.getElementById('globalCustomerSelect');
        const detailsBox = document.getElementById('customerDetailsBox');
        const cdName = document.getElementById('cdName');
        const cdPhone = document.getElementById('cdPhone');
        const outBox = document.getElementById('cdOutstandingBox');
        const outAmount = document.getElementById('cdOutstandingAmount');

        if (select.value === '') { detailsBox.style.display = 'none'; return; }

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
        if (select.value === '') { alert('Please select a customer first to start billing.'); return; }

        const opt = select.options[select.selectedIndex];
        globalSelectedCustomerId = select.value;
        globalSelectedCustomerOutstanding = parseFloat(opt.getAttribute('data-outstanding')) || 0;

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

        // Keep POS Terminal in sync
        document.getElementById('checkoutCustomerName').innerText = opt.getAttribute('data-name');
        document.getElementById('checkoutCustomerOws').innerText = topOws.innerText;
        document.getElementById('checkoutCustomerOws').style.color = topOws.style.color;

        closeCustomerSheet();
        if (document.getElementById('checkoutOverlay').style.display === 'flex') { calcCheckout(); }

        if (pendingProductModalObj) {
            setTimeout(() => { openProductModal(pendingProductModalObj, true); pendingProductModalObj = null; }, 350);
        }
    }

    // --- PRODUCT MODAL LOGIC (Shared with Catalog) ---
    function openProductModal(prod, bypassCustomerCheck = false) {
        if (!globalSelectedCustomerId && !bypassCustomerCheck) {
            pendingProductModalObj = prod;
            openCustomerSheet();
            return;
        }

        // Safe assign directly (both native JSON objects and parsed strings)
        const prodObj = (typeof prod === 'string') ? JSON.parse(prod) : prod;
        currentProd = prodObj;
        
        document.getElementById('pmTitle').innerText = prodObj.name;
        
        let firstFound = cart.find(c => c.itemId == prodObj.id);
        if (firstFound) {
            document.getElementById('pmDiscVal').value = firstFound.disc_val;
            document.getElementById('pmDiscType').value = firstFound.disc_type;
        } else {
            document.getElementById('pmDiscVal').value = 0;
            document.getElementById('pmDiscType').value = 'Rs';
        }
        
        const isService = prodObj.type === 'Service';
        document.getElementById('pmStockInfo').innerText = isService ? 'Service Item' : `Available Stock: ${prodObj.available_stock}`;
        
        const container = document.getElementById('pmVariantsContainer');
        container.innerHTML = '';

        if (prodObj.variations && prodObj.variations.length > 0) {
            prodObj.variations.forEach(v => {
                let cartItemId = `${prodObj.id}_${v.id}`;
                let cartItem = cart.find(c => c.cartItemId === cartItemId);
                let price = cartItem ? cartItem.price : (v.price || prodObj.price);
                let stockLimit = isService ? '' : `max="${v.available_stock}"`;
                container.innerHTML += `
                    <div class="pm-row">
                        <div class="pm-row-header">
                            <span>${v.variation_name}: ${v.value_name}</span>
                            <span style="font-size:12px; font-weight:bold; color:${(!isService && v.available_stock <= 0) ? '#c62828' : 'var(--text-muted)'};">Stock: ${isService ? 'N/A' : v.available_stock}</span>
                        </div>
                        <div class="pm-inputs">
                            <div class="pm-input-group"><label>Price</label><input type="number" id="pmPrice_${v.id}" value="${parseFloat(price).toFixed(2)}" step="0.01"></div>
                            <div class="pm-input-group"><label>QTY</label><input type="number" id="pmQty_${v.id}" value="${cartItem ? cartItem.qty : 0}" min="0" ${stockLimit} oninput="validateModalQty(this, ${isService})"></div>
                        </div>
                    </div>
                `;
            });
        } else {
            let cartItem = cart.find(c => c.cartItemId === `${prodObj.id}_0`);
            let stockLimit = isService ? '' : `max="${prodObj.available_stock}"`;
            container.innerHTML += `
                <div class="pm-row">
                    <div class="pm-row-header">
                        <span>Standard Item</span>
                        <span style="font-size:12px; font-weight:bold; color:${(!isService && prodObj.available_stock <= 0) ? '#c62828' : 'var(--text-muted)'};">Stock: ${isService ? 'N/A' : prodObj.available_stock}</span>
                    </div>
                    <div class="pm-inputs">
                        <div class="pm-input-group"><label>Price</label><input type="number" id="pmPrice_base" value="${parseFloat(cartItem ? cartItem.price : prodObj.price).toFixed(2)}" step="0.01"></div>
                        <div class="pm-input-group"><label>QTY</label><input type="number" id="pmQty_base" value="${cartItem ? cartItem.qty : 0}" min="0" ${stockLimit} oninput="validateModalQty(this, ${isService})"></div>
                    </div>
                </div>
            `;
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
            if (val > max) { alert("Insufficient stock! Only " + max + " available."); input.value = max; }
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
            let newItem = { cartItemId, itemId, varId, name: fullName, price, qty, disc_val: discVal, disc_type: discType, type, rawProd: currentProd };
            if (existingIndex >= 0) { cart[existingIndex] = newItem; } 
            else { cart.push(newItem); }
        } else {
            if (existingIndex >= 0) { cart.splice(existingIndex, 1); }
        }
    }

    // --- Main UI Updater ---
    function updateCartUI() {
        const cartBar = document.getElementById('cartBar');
        const emptyMsg = document.getElementById('emptyCartMsg');
        const cartList = document.getElementById('activeCartList');
        
        cartList.innerHTML = '';

        if(cart.length === 0) {
            cartBar.style.display = 'none';
            emptyMsg.style.display = 'block';
            return;
        }
        
        emptyMsg.style.display = 'none';
        cartBar.style.display = 'flex';
        
        let totalQty = 0;
        let totalAmt = 0;
        
        cart.forEach(item => {
            totalQty += item.qty;
            let rowGross = item.qty * item.price;
            let rowDisc = (item.disc_type === '%') ? (rowGross * item.disc_val / 100) : parseFloat(item.disc_val);
            let rowNet = rowGross - rowDisc;
            if(rowNet < 0) rowNet = 0;
            
            let discBadge = rowDisc > 0 ? `<span style="color:#c62828;">(-Rs ${rowDisc.toFixed(2)})</span>` : '';
            if (item.is_free) {
                rowNet = 0;
                discBadge = `<span style="color:#ef6c00; font-weight:bold;">(FREE ISSUE)</span>`;
            }
            totalAmt += rowNet;

            // Render Standard Invoice Line Item
            cartList.innerHTML += `
                <div class="line-item">
                    <div class="line-item-info">
                        <div class="line-item-name">${item.name}</div>
                        <div class="line-item-meta">
                            <span>${item.qty} QTY</span>
                            <span>@ Rs ${parseFloat(item.price).toFixed(2)}</span>
                            ${discBadge}
                        </div>
                    </div>
                    <div style="display:flex; flex-direction:column; align-items:flex-end;">
                        <div class="line-item-price">Rs ${rowNet.toFixed(2)}</div>
                        ${item.is_free ? `<button class="edit-btn" style="background:rgba(198,40,40,0.1); color:#c62828;" onclick="removeFromCart('${item.cartItemId}')">Delete</button>` : `<button class="edit-btn" onclick="editCartItem('${item.cartItemId}')">Edit QTY</button>`}
                    </div>
                </div>
            `;
        });
        
        document.getElementById('cartCount').innerText = `${totalQty} units total`;
        document.getElementById('cartTotal').innerText = `Rs: ${totalAmt.toLocaleString('en-IN', {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;
    }

    // Required by checkout_overlay.php
    function editCartItem(cartItemId) {
        let item = cart.find(i => i.cartItemId === cartItemId);
        if (item && item.rawProd) { openProductModal(item.rawProd, true); }
    }
</script>