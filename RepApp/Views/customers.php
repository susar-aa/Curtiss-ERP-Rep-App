<?php
$activeRouteName = $data['active_route'] ? $data['active_route']->route_name : '';
?>
<style>
    /* Mobile-Optimized List Cards */
    .customer-card { background: var(--surface); padding: 15px; border-radius: 12px; margin-bottom: 15px; box-shadow: 0 2px 10px rgba(0,0,0,0.02); border: 1px solid var(--border); transition: 0.2s;}
    .customer-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px; }
    .customer-info h4 { margin: 0 0 4px 0; font-size: 16px; color: var(--text-dark); }
    .customer-info p { margin: 0; font-size: 12px; color: var(--text-muted); line-height: 1.4; }
    
    .badge-gps { display: inline-block; padding: 3px 8px; background: #e8f5e9; color: #2e7d32; border-radius: 6px; font-size: 10px; font-weight: bold; margin-top: 8px;}
    .badge-no-gps { display: inline-block; padding: 3px 8px; background: #ffebee; color: #c62828; border-radius: 6px; font-size: 10px; font-weight: bold; margin-top: 8px;}
    
    .action-icons { display: flex; gap: 15px; }
    .icon-btn { background: transparent; border: none; font-size: 16px; color: var(--text-muted); padding: 5px; cursor: pointer; }
    
    /* Native App Bottom Sheet UI */
    .sheet-overlay { display: none; position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); z-index: 2000; align-items: flex-end; justify-content: center; }
    .bottom-sheet { background: var(--app-bg); width: 100%; max-width: 480px; border-radius: 20px 20px 0 0; padding: 25px 20px; box-sizing: border-box; transform: translateY(100%); transition: transform 0.3s ease-out; max-height: 90vh; overflow-y: auto; position: relative;}
    .bottom-sheet.open { transform: translateY(0); }
    
    .sheet-handle { width: 40px; height: 5px; background: #ccc; border-radius: 5px; margin: 0 auto 20px auto; }
    
    /* GPS Toggle Box */
    .gps-toggle-box { background: rgba(0, 102, 204, 0.05); border: 1px solid rgba(0, 102, 204, 0.2); padding: 15px; border-radius: 12px; margin-bottom: 20px; }
    .toggle-row { display: flex; align-items: center; justify-content: space-between; }
    .toggle-label { font-size: 14px; font-weight: bold; color: var(--primary); }
    
    /* Custom Toggle Switch CSS */
    .switch { position: relative; display: inline-block; width: 50px; height: 26px; }
    .switch input { opacity: 0; width: 0; height: 0; }
    .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #ccc; transition: .4s; border-radius: 34px; }
    .slider:before { position: absolute; content: ""; height: 18px; width: 18px; left: 4px; bottom: 4px; background-color: white; transition: .4s; border-radius: 50%; }
    input:checked + .slider { background-color: var(--primary); }
    input:checked + .slider:before { transform: translateX(24px); }

    /* Search Bar */
    .search-box { width: 100%; padding: 12px 15px; border: 1px solid var(--border); border-radius: 8px; font-size: 15px; margin-bottom: 20px; background: var(--surface); color: var(--text-dark); box-sizing: border-box; box-shadow: 0 2px 5px rgba(0,0,0,0.02);}
    .search-box:focus { border-color: var(--primary); outline: none; }
</style>

<?php if(!empty($data['error'])): ?>
    <div style="background:#ffebee; color:#c62828; padding:12px; border-radius:8px; margin-bottom:15px; text-align:center; font-size:13px; font-weight:bold;">
        <?= htmlspecialchars($data['error']) ?>
    </div>
<?php endif; ?>
<?php if(!empty($data['success'])): ?>
    <div style="background:#e8f5e9; color:#2e7d32; padding:12px; border-radius:8px; margin-bottom:15px; text-align:center; font-size:13px; font-weight:bold;">
        ✓ <?= htmlspecialchars($data['success']) ?>
    </div>
<?php endif; ?>

<div style="margin-bottom: 20px; text-align: center; color: var(--text-muted); font-size: 12px; font-weight: bold; text-transform: uppercase;">
    <?= htmlspecialchars($data['subtitle']) ?>
</div>

<input type="text" id="liveSearch" class="search-box" placeholder="🔍 Search shop name, phone, or area..." onkeyup="filterCustomers()">

<button class="btn-primary" style="margin-bottom: 20px;" onclick="openCustomerSheet('add')">+ Add New Shop / Customer</button>

<!-- Customer List -->
<div id="customerListContainer" style="padding-bottom: 30px;">
    <?php if(empty($data['customers'])): ?>
        <p style="text-align:center; color:var(--text-muted); margin-top:40px;">No customers found in this view.</p>
    <?php else: foreach($data['customers'] as $cust): ?>
        <div class="customer-card">
            <div class="customer-header">
                <div class="customer-info">
                    <h4 class="c-name"><?= htmlspecialchars($cust->name) ?></h4>
                    <p class="c-phone">📞 <?= htmlspecialchars($cust->phone) ?: 'No Phone' ?> <?= !empty($cust->whatsapp) ? '| 💬 ' . htmlspecialchars($cust->whatsapp) : '' ?></p>
                    <p class="c-address" style="margin-top:2px;"><?= htmlspecialchars($cust->address) ?: 'No Address' ?></p>
                </div>
                <div class="action-icons">
                    <!-- Passing all data to JS, escaping quotes safely -->
                    <?php 
                        $jsName = htmlspecialchars(addslashes($cust->name));
                        $jsPhone = htmlspecialchars(addslashes($cust->phone ?? ''));
                        $jsWa = htmlspecialchars(addslashes($cust->whatsapp ?? ''));
                        $jsAddr = htmlspecialchars(addslashes($cust->address ?? ''));
                    ?>
                    <button class="icon-btn" onclick="openCustomerSheet('edit', <?= $cust->id ?>, '<?= $jsName ?>', '<?= $jsPhone ?>', '<?= $jsWa ?>', '<?= $jsAddr ?>')">✏️</button>
                    
                    <form action="<?= APP_URL ?>/rep/customers/save" method="POST" style="margin:0;">
                        <input type="hidden" name="action" value="delete">
                        <input type="hidden" name="customer_id" value="<?= $cust->id ?>">
                        <button type="submit" class="icon-btn" onclick="return confirm('Delete this shop?')" style="color: #c62828;">🗑️</button>
                    </form>
                </div>
            </div>
            
            <?php if($cust->latitude && $cust->longitude): ?>
                <div class="badge-gps">📍 Geo-Tagged (<?= round($cust->latitude, 4) ?>, <?= round($cust->longitude, 4) ?>)</div>
            <?php else: ?>
                <div class="badge-no-gps">⚠️ No GPS Data</div>
            <?php endif; ?>
        </div>
    <?php endforeach; endif; ?>
    
    <div id="noResultsMsg" style="display:none; text-align:center; color:var(--text-muted); margin-top:40px;">
        No shops match your search.
    </div>
</div>

<!-- Add/Edit Bottom Sheet -->
<div class="sheet-overlay" id="customerSheetOverlay">
    <div class="bottom-sheet" id="customerSheet">
        <div class="sheet-handle" onclick="closeCustomerSheet()"></div>
        
        <h3 id="sheetTitle" style="margin-top:0; color:var(--text-dark); margin-bottom: 20px;">Add Customer</h3>
        
        <form action="<?= APP_URL ?>/rep/customers/save" method="POST" id="customerForm">
            <input type="hidden" name="action" id="formAction" value="add">
            <input type="hidden" name="customer_id" id="formCustId" value="">
            <input type="hidden" name="territory" value="<?= htmlspecialchars($activeRouteName) ?>">
            
            <label class="form-label">Shop / Customer Name *</label>
            <input type="text" name="name" id="f_name" class="form-input" style="margin-bottom: 15px;" required>
            
            <div style="display: flex; gap: 15px;">
                <div style="flex: 1;">
                    <label class="form-label">Phone Number</label>
                    <input type="tel" name="phone" id="f_phone" class="form-input" style="margin-bottom: 15px;">
                </div>
                <div style="flex: 1;">
                    <label class="form-label">WhatsApp Number</label>
                    <input type="tel" name="whatsapp" id="f_whatsapp" class="form-input" style="margin-bottom: 15px;">
                </div>
            </div>
            
            <div style="background: rgba(0,0,0,0.02); padding: 15px; border-radius: 8px; margin-bottom: 20px; border: 1px solid var(--border);">
                <label class="form-label" style="color: var(--primary);">Shop Address</label>
                <input type="text" name="addr1" id="f_addr1" class="form-input" style="margin-bottom: 10px; padding: 10px;" placeholder="Line 1: No 79, Dambakanda Estate">
                <input type="text" name="addr2" id="f_addr2" class="form-input" style="margin-bottom: 10px; padding: 10px;" placeholder="Line 2: Boayagane">
                <input type="text" name="addr3" id="f_addr3" class="form-input" style="margin-bottom: 0px; padding: 10px;" placeholder="Line 3: Kurunegala">
            </div>
            
            <!-- Geo-Tagging Logic Engine -->
            <div class="gps-toggle-box">
                <div class="toggle-row">
                    <span class="toggle-label" id="gpsStatusText">📍 I am currently at this shop location</span>
                    <label class="switch">
                        <input type="checkbox" name="at_shop" id="gpsToggle" value="1" onchange="handleGpsToggle()">
                        <span class="slider"></span>
                    </label>
                </div>
                <div id="gpsLoading" style="display:none; font-size:11px; color:#888; margin-top:10px; font-weight:bold;">
                    Connecting to satellites... Please wait. 🛰️
                </div>
            </div>

            <!-- Hidden GPS Data -->
            <input type="hidden" name="latitude" id="f_lat">
            <input type="hidden" name="longitude" id="f_lng">

            <button type="submit" class="btn-primary" id="saveBtn">Save Customer Details</button>
            <button type="button" class="btn-primary" style="background:transparent; color:var(--text-muted); box-shadow:none; margin-top:10px;" onclick="closeCustomerSheet()">Cancel</button>
        </form>
    </div>
</div>

<script>
    const overlay = document.getElementById('customerSheetOverlay');
    const sheet = document.getElementById('customerSheet');

    // Instant Live Search Logic
    function filterCustomers() {
        const query = document.getElementById('liveSearch').value.toLowerCase();
        const cards = document.querySelectorAll('.customer-card');
        let visibleCount = 0;

        cards.forEach(card => {
            const name = card.querySelector('.c-name').innerText.toLowerCase();
            const phone = card.querySelector('.c-phone').innerText.toLowerCase();
            const address = card.querySelector('.c-address').innerText.toLowerCase();
            
            if (name.includes(query) || phone.includes(query) || address.includes(query)) {
                card.style.display = 'block';
                visibleCount++;
            } else {
                card.style.display = 'none';
            }
        });

        document.getElementById('noResultsMsg').style.display = (visibleCount === 0 && cards.length > 0) ? 'block' : 'none';
    }

    function openCustomerSheet(mode, id = '', name = '', phone = '', whatsapp = '', address = '') {
        overlay.style.display = 'flex';
        setTimeout(() => { sheet.classList.add('open'); }, 10);
        
        // Reset the GPS toggle
        document.getElementById('gpsToggle').checked = false;
        document.getElementById('gpsStatusText').innerText = "📍 I am currently at this shop location";
        document.getElementById('gpsStatusText').style.color = "var(--primary)";
        document.getElementById('f_lat').value = '';
        document.getElementById('f_lng').value = '';
        document.getElementById('saveBtn').disabled = false;
        document.getElementById('gpsLoading').style.display = 'none';

        if (mode === 'add') {
            document.getElementById('sheetTitle').innerText = 'Add New Customer';
            document.getElementById('formAction').value = 'add';
            document.getElementById('f_name').value = '';
            document.getElementById('f_phone').value = '';
            document.getElementById('f_whatsapp').value = '';
            document.getElementById('f_addr1').value = '';
            document.getElementById('f_addr2').value = '';
            document.getElementById('f_addr3').value = '';
        } else {
            document.getElementById('sheetTitle').innerText = 'Edit Customer';
            document.getElementById('formAction').value = 'edit';
            document.getElementById('formCustId').value = id;
            document.getElementById('f_name').value = name;
            document.getElementById('f_phone').value = phone;
            document.getElementById('f_whatsapp').value = whatsapp;
            
            // Re-split the 3-line address format back into the inputs
            let addrParts = address.split(' | ');
            document.getElementById('f_addr1').value = addrParts[0] ? addrParts[0].trim() : '';
            document.getElementById('f_addr2').value = addrParts[1] ? addrParts[1].trim() : '';
            document.getElementById('f_addr3').value = addrParts[2] ? addrParts[2].trim() : '';
        }
    }

    function closeCustomerSheet() {
        sheet.classList.remove('open');
        setTimeout(() => { overlay.style.display = 'none'; }, 300);
    }

    function handleGpsToggle() {
        const toggle = document.getElementById('gpsToggle');
        const statusText = document.getElementById('gpsStatusText');
        const loading = document.getElementById('gpsLoading');
        const saveBtn = document.getElementById('saveBtn');

        if (toggle.checked) {
            statusText.innerText = "Capturing Location...";
            loading.style.display = 'block';
            saveBtn.disabled = true; 
            saveBtn.style.opacity = '0.5';

            if ("geolocation" in navigator) {
                navigator.geolocation.getCurrentPosition(
                    function(position) {
                        document.getElementById('f_lat').value = position.coords.latitude;
                        document.getElementById('f_lng').value = position.coords.longitude;
                        statusText.innerText = "✅ Location Captured Successfully!";
                        statusText.style.color = "#2e7d32";
                        loading.style.display = 'none';
                        saveBtn.disabled = false;
                        saveBtn.style.opacity = '1';
                    },
                    function(error) {
                        alert("GPS Capture Failed: " + error.message);
                        toggle.checked = false;
                        statusText.innerText = "📍 I am currently at this shop location";
                        loading.style.display = 'none';
                        saveBtn.disabled = false;
                        saveBtn.style.opacity = '1';
                    },
                    { enableHighAccuracy: true, timeout: 5000, maximumAge: 0 }
                );
            } else {
                alert("GPS is not supported on this device.");
                toggle.checked = false;
                loading.style.display = 'none';
                saveBtn.disabled = false;
                saveBtn.style.opacity = '1';
            }
        } else {
            statusText.innerText = "📍 I am currently at this shop location";
            statusText.style.color = "var(--primary)";
            document.getElementById('f_lat').value = '';
            document.getElementById('f_lng').value = '';
            loading.style.display = 'none';
        }
    }
</script>