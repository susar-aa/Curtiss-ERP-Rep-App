<!-- 
  CURTISS ERP PROGRESSIVE OFFLINE ENGINE
  High-Density Browser Interceptor & Background Sync Manager
-->
<div id="offlineFloatingBanner" style="display: none; position: fixed; top: 10px; left: 50%; transform: translateX(-50%); background: rgba(239, 68, 68, 0.9); backdrop-filter: blur(8px); color: white; padding: 10px 20px; border-radius: 20px; font-size: 13px; font-weight: bold; z-index: 99999; box-shadow: 0 4px 15px rgba(0,0,0,0.2); border: 1px solid rgba(255,255,255,0.2); align-items: center; gap: 8px;">
    <span>⚠️</span>
    <span>Offline Mode Active - Processing Locally</span>
</div>

<div id="onlineSyncBanner" style="display: none; position: fixed; top: 10px; left: 50%; transform: translateX(-50%); background: rgba(16, 185, 129, 0.95); backdrop-filter: blur(8px); color: white; padding: 10px 20px; border-radius: 20px; font-size: 13px; font-weight: bold; z-index: 99999; box-shadow: 0 4px 15px rgba(0,0,0,0.2); border: 1px solid rgba(255,255,255,0.2); align-items: center; gap: 8px;">
    <span>🔄</span>
    <span>Syncing offline items with ERP...</span>
</div>

<script>
    (function() {
        console.log("🚀 Curtiss PWA Offline Engine Loaded.");

        const BANNER_OFFLINE = document.getElementById('offlineFloatingBanner');
        const BANNER_ONLINE = document.getElementById('onlineSyncBanner');

        // --- 1. Network Status Observers ---
        function checkNetworkState() {
            if (!navigator.onLine) {
                BANNER_OFFLINE.style.display = 'flex';
                BANNER_ONLINE.style.display = 'none';
                applyOfflineOverrides();
            } else {
                BANNER_OFFLINE.style.display = 'none';
                performBackgroundSync();
            }
        }

        window.addEventListener('online', checkNetworkState);
        window.addEventListener('offline', checkNetworkState);
        
        // Run check on initialization
        setTimeout(checkNetworkState, 500);

        // --- 2. Live DOM Registry Harvester ---
        function harvestOnlineRegistry() {
            if (!navigator.onLine) return;

            // A. Harvest Customer outstanding Registry
            const selectCust = document.getElementById('globalCustomerSelect');
            if (selectCust) {
                const customers = [];
                for (let opt of selectCust.options) {
                    if (opt.value) {
                        customers.push({
                            id: opt.value,
                            name: opt.getAttribute('data-name') || opt.innerText,
                            phone: opt.getAttribute('data-phone') || '',
                            whatsapp: opt.getAttribute('data-phone') || '',
                            outstanding: parseFloat(opt.getAttribute('data-outstanding')) || 0
                        });
                    }
                }
                localStorage.setItem('curtiss_customers_registry', JSON.stringify(customers));
                console.log("✓ Customer registry cached in LocalStorage:", customers.length);
            }

            // B. Harvest Product variations Registry
            if (window.globalFreeProducts && window.globalFreeProducts.length > 0) {
                localStorage.setItem('curtiss_products_registry', JSON.stringify(window.globalFreeProducts));
                console.log("✓ Product inventory cached in LocalStorage:", window.globalFreeProducts.length);
            }
        }

        // Run harvester after a delay to ensure view script evaluation complete
        setTimeout(harvestOnlineRegistry, 1500);

        // --- 3. Offline UI DOM Reconstruction Overrides ---
        function applyOfflineOverrides() {
            console.log("Applying dynamic offline UI overlays...");

            // Unlock bottom Billing tab if route is active offline
            const offlineRoute = localStorage.getItem('curtiss_offline_active_route');
            const billTab = document.querySelector('a[href*="/billing"], a[onclick*="Start a route first"]');
            if (billTab && offlineRoute) {
                billTab.setAttribute('href', '<?= APP_URL ?>/rep/billing');
                billTab.removeAttribute('onclick');
                billTab.classList.remove('disabled');
                billTab.style.opacity = '1';
                billTab.style.pointerEvents = 'auto';
            }

            // A. Rebuild POS Customers Dropdown if on billing page
            const selectCust = document.getElementById('globalCustomerSelect');
            if (selectCust && !navigator.onLine) {
                const cachedCust = localStorage.getItem('curtiss_customers_registry');
                if (cachedCust) {
                    const customers = JSON.parse(cachedCust);
                    selectCust.innerHTML = '<option value="">Select Billing Customer...</option>';
                    customers.forEach(c => {
                        selectCust.innerHTML += `<option value="${c.id}" data-name="${c.name}" data-phone="${c.phone}" data-outstanding="${c.outstanding}">${c.name}</option>`;
                    });
                    console.log("✓ Re-populated select dropdown offline.");
                }
            }

            // B. Rebuild POS Product Catalog if on billing page
            const grid = document.getElementById('prodGrid');
            if (grid && !navigator.onLine) {
                const cachedProd = localStorage.getItem('curtiss_products_registry');
                if (cachedProd) {
                    const products = JSON.parse(cachedProd);
                    grid.innerHTML = '';
                    products.forEach(item => {
                        let isOut = item.available_stock <= 0 && item.type === 'Inventory' && !item.has_variations;
                        let outLabel = isOut ? `<div class="stock-badge stock-out">Out of Stock</div>` : `<div class="stock-badge">Stock: ${item.available_stock}</div>`;
                        let price = parseFloat(item.price).toFixed(2);
                        
                        grid.innerHTML += `
                            <div class="product-card" onclick='openProductModal(${JSON.stringify(item.rawProd)})'>
                                <div class="img-wrapper">
                                    <div style="width:100%; height:100%; display:flex; align-items:center; justify-content:center; color:#aaa; font-size:12px; background:#1e1e2d;">📦</div>
                                    ${outLabel}
                                </div>
                                <div class="prod-info">
                                    <h4 class="prod-name">${item.name}</h4>
                                    <p class="prod-price">Rs ${price}</p>
                                </div>
                            </div>
                        `;
                    });
                    console.log("✓ Dynamic offline visual grid reconstructed.");
                }
            }

            // C. Override Home Route state if offline
            const startTripForm = document.getElementById('startTripForm');
            if (startTripForm && offlineRoute && !navigator.onLine) {
                const routeObj = JSON.parse(offlineRoute);
                const parentCard = startTripForm.parentNode;
                parentCard.innerHTML = `
                    <h2 style="margin-top:0; color:#ef5350; text-align:center; font-size:18px;">⚠️ Active Offline Route</h2>
                    <div style="background:rgba(239, 83, 80, 0.05); border:1px solid rgba(239, 83, 80, 0.2); border-radius:8px; padding:15px; text-align:center; margin-bottom:15px;">
                        <span style="font-weight:bold; color:var(--text-dark); display:block; font-size:16px;">${routeObj.route_name}</span>
                        <span style="font-size:12px; color:var(--text-muted); display:block; margin-top:5px;">Started At: ${routeObj.start_time}</span>
                        <span style="font-size:12px; color:var(--text-muted); display:block;">Start Odometer: ${routeObj.start_meter} KM</span>
                    </div>
                    <a href="<?= APP_URL ?>/rep/end_route" class="btn-primary" style="background:#c62828; text-decoration:none; padding:12px; display:block;">End Offline Route</a>
                `;
            }
        }

        // --- 4. Hijack Forms & Functions Runtime hooks ---
        
        // A. Hijack initiateRoute (Start Daily Route)
        if (typeof window.initiateRoute === 'function') {
            const originalStart = window.initiateRoute;
            window.initiateRoute = function() {
                if (!navigator.onLine) {
                    console.log("Intercepting Start Route offline...");
                    const form = document.getElementById('startTripForm');
                    const routeName = form.querySelector('select[name="route_name"]').value;
                    const startMeter = document.getElementById('actualMeterValue').value;

                    if (!routeName || !startMeter) {
                        alert("Select route and enter odometer details.");
                        return;
                    }

                    const routeObj = {
                        route_name: routeName,
                        start_meter: parseFloat(startMeter) || 0,
                        start_time: new Date().toISOString().replace('T', ' ').split('.')[0],
                        start_lat: 7.1824,
                        start_lng: 79.8801
                    };

                    localStorage.setItem('curtiss_offline_active_route', JSON.stringify(routeObj));
                    
                    // Queue for background upload
                    queueOfflineAction('routes', {
                        action_type: 'start_route',
                        route_name: routeName,
                        start_meter: parseFloat(startMeter) || 0,
                        start_time: routeObj.start_time,
                        start_lat: routeObj.start_lat,
                        start_lng: routeObj.start_lng
                    });

                    alert("Route Journey Started Offline!");
                    window.location.href = "<?= APP_URL ?>/rep";
                } else {
                    originalStart();
                }
            };
        }

        // B. Hijack initiateEndRoute (End Daily Route)
        if (typeof window.initiateEndRoute === 'function') {
            const originalEnd = window.initiateEndRoute;
            window.initiateEndRoute = function() {
                if (!navigator.onLine) {
                    console.log("Intercepting End Route offline...");
                    const endMeter = parseFloat(document.getElementById('actualMeterValue').value);
                    if (!endMeter) {
                        alert("Enter valid ending meter.");
                        return;
                    }

                    const offlineRoute = localStorage.getItem('curtiss_offline_active_route');
                    if (offlineRoute) {
                        const routeObj = JSON.parse(offlineRoute);
                        queueOfflineAction('routes', {
                            action_type: 'end_route',
                            end_meter: endMeter,
                            end_time: new Date().toISOString().replace('T', ' ').split('.')[0],
                            end_lat: 7.1824,
                            end_lng: 79.8801
                        });
                    }

                    localStorage.removeItem('curtiss_offline_active_route');
                    alert("Route Ended Offline! Data queued for sync.");
                    window.location.href = "<?= APP_URL ?>/rep";
                } else {
                    originalEnd();
                }
            };
        }

        // C. Hijack finalizeSubmit (POS Billing checkout)
        if (typeof window.finalizeSubmit === 'function') {
            const originalFinalize = window.finalizeSubmit;
            window.finalizeSubmit = function(lat, lng, customerId, termId) {
                if (!navigator.onLine) {
                    console.log("Intercepting POS Checkout offline...");
                    
                    // Compile invoices payload
                    let chequeTotal = 0;
                    const chequesList = [];
                    const chequeCards = document.querySelectorAll('.rep-cheque-card');
                    chequeCards.forEach(card => {
                        const amt = parseFloat(card.querySelector('.rep-cheque-amount').value) || 0;
                        if (amt > 0) {
                            chequesList.push({
                                amount: amt,
                                bank: card.querySelector('.rep-cheque-bank').value.trim() || 'Unknown',
                                number: card.querySelector('.rep-cheque-number').value.trim() || 'Unknown',
                                date: card.querySelector('.rep-cheque-date').value.trim() || new Date().toISOString().split('T')[0]
                            });
                            chequeTotal += amt;
                        }
                    });

                    const invoiceNum = "INV-OFF-" + Math.floor(Date.now() / 1000);
                    const subtotal = cart.reduce((sum, item) => sum + (item.qty * item.price), 0);
                    const discVal = parseFloat(document.getElementById('coGlobalDiscVal').value) || 0;
                    const discType = document.getElementById('coGlobalDiscType').value;
                    let billDisc = (discType === '%') ? (subtotal * discVal / 100) : discVal;
                    let netTotal = subtotal - billDisc;
                    if (netTotal < 0) netTotal = 0;

                    const payload = {
                        invoice_number: invoiceNum,
                        customer_id: customerId,
                        payment_term_id: termId,
                        term_cheque_date: document.getElementById('coTermChequeDate') ? document.getElementById('coTermChequeDate').value : null,
                        cart: cart,
                        discounts: { val: discVal, type: discType },
                        tax: { val: 18, type: '%' }, // inclusive breakdown
                        arrears_collections: {
                            cash: parseFloat(document.getElementById('payCash').value) || 0,
                            bank: parseFloat(document.getElementById('payBank').value) || 0,
                            cheque: chequeTotal
                        },
                        cheques: chequesList,
                        location: { lat: lat || 7.1824, lng: lng || 79.8801 }
                    };

                    queueOfflineAction('invoices', payload);

                    // Clear active PWA cart
                    cart = [];
                    if (typeof updateCartUI === 'function') updateCartUI();

                    // Dynamically build and reveal success modal overlay
                    showOfflineSuccessModal(invoiceNum);
                } else {
                    originalFinalize(lat, lng, customerId, termId);
                }
            };
        }

        // D. Intercept Customer Form Addition
        const custForm = document.getElementById('customerForm');
        if (custForm) {
            custForm.addEventListener('submit', function(e) {
                if (!navigator.onLine) {
                    e.preventDefault();
                    console.log("Intercepting Customer Registry offline...");

                    const name = document.getElementById('f_name').value;
                    const phone = document.getElementById('f_phone').value;
                    const whatsapp = document.getElementById('f_whatsapp').value;
                    const addr1 = document.getElementById('f_addr1').value;
                    const addr2 = document.getElementById('f_addr2').value;
                    const addr3 = document.getElementById('f_addr3').value;
                    const lat = document.getElementById('f_lat').value || 7.1824;
                    const lng = document.getElementById('f_lng').value || 79.8801;

                    const payload = {
                        name: name,
                        phone: phone,
                        whatsapp: whatsapp,
                        address: `${addr1} | ${addr2} | ${addr3}`,
                        latitude: parseFloat(lat),
                        longitude: parseFloat(lng)
                    };

                    queueOfflineAction('customers', payload);
                    alert("Shop tagged and saved offline!");

                    // Append dynamically to in-memory select list if POS active
                    const selectCust = document.getElementById('globalCustomerSelect');
                    if (selectCust) {
                        const tempId = "temp_" + Date.now();
                        selectCust.innerHTML += `<option value="${tempId}" data-name="${name}" data-phone="${phone}" data-outstanding="0">${name} (Offline Tagged)</option>`;
                    }

                    if (typeof closeCustomerSheet === 'function') {
                        closeCustomerSheet();
                    }
                }
            });
        }

        // --- 5. Helper Queuers & Modal Builders ---
        function queueOfflineAction(type, data) {
            const key = `curtiss_queue_${type}`;
            const queue = JSON.parse(localStorage.getItem(key) || '[]');
            queue.push(data);
            localStorage.setItem(key, JSON.stringify(queue));
            console.log(`Action queued in local storage: [${type}]`, queue.length);
        }

        function showOfflineSuccessModal(invoiceNum) {
            const overlay = document.createElement('div');
            overlay.className = 'sheet-overlay';
            overlay.style.display = 'flex';
            overlay.style.alignItems = 'center';
            overlay.style.zIndex = '99999';
            overlay.style.position = 'fixed';
            overlay.style.top = '0'; overlay.style.left = '0'; overlay.style.right = '0'; overlay.style.bottom = '0';
            overlay.style.background = 'rgba(0,0,0,0.6)';
            overlay.style.justifyContent = 'center';

            overlay.innerHTML = `
                <div style="background:var(--surface); width:90%; max-width:400px; padding:30px; border-radius:16px; text-align:center; box-sizing:border-box;">
                    <div style="font-size: 50px; margin-bottom: 15px;">💾</div>
                    <h2 style="margin-top:0; color:#ef6c00;">Saved Offline Successfully!</h2>
                    <p style="color:var(--text-muted); font-size:14px; margin-bottom:25px;">Invoice # ${invoiceNum} is stored in browser cache. It will sync automatically when online.</p>
                    <button class="btn-primary" id="closeOfflineSuccessBtn" style="background:#ef6c00; padding:12px; width:100%; border:none; border-radius:8px; font-weight:bold; color:white; cursor:pointer;">Close & New Bill</button>
                </div>
            `;

            document.body.appendChild(overlay);
            document.getElementById('closeOfflineSuccessBtn').onclick = () => {
                document.body.removeChild(overlay);
                if (typeof closeCheckout === 'function') closeCheckout();
                window.location.reload(); // Refresh catalog to reset stock reserves
            };
        }

        // --- 6. Bidirectional Async Sync Engine ---
        let isSyncing = false;
        function performBackgroundSync() {
            if (isSyncing) return;

            const qInvoices = JSON.parse(localStorage.getItem('curtiss_queue_invoices') || '[]');
            const qCustomers = JSON.parse(localStorage.getItem('curtiss_queue_customers') || '[]');
            const qRoutes = JSON.parse(localStorage.getItem('curtiss_queue_routes') || '[]');

            // Avoid network hit if nothing queued
            if (qInvoices.length === 0 && qCustomers.length === 0 && qRoutes.length === 0) {
                return;
            }

            isSyncing = true;
            BANNER_ONLINE.style.display = 'flex';

            const payload = {
                user_id: 12, // default mapping context
                invoices: qInvoices,
                customers: qCustomers,
                routes: qRoutes
            };

            const targetUrl = "<?= APP_URL ?>/rep/RepDashboard/sync_push?api_sync=1";
            console.log("Online status detected. Syncing local drafts with ERP server...", payload);

            fetch(targetUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    console.log("ERP Synchronized successfully!", data);
                    // Clear queues
                    localStorage.setItem('curtiss_queue_invoices', '[]');
                    localStorage.setItem('curtiss_queue_customers', '[]');
                    localStorage.setItem('curtiss_queue_routes', '[]');
                    
                    BANNER_ONLINE.innerHTML = `<span>✓</span> <span>ERP Synced Successfully!</span>`;
                    BANNER_ONLINE.style.background = "rgba(16, 185, 129, 0.95)";
                    
                    setTimeout(() => {
                        BANNER_ONLINE.style.display = 'none';
                        window.location.reload(); // Reload to refresh fresh ERP catalog
                    }, 2000);
                } else {
                    console.error("ERP sync push rejected:", data);
                    BANNER_ONLINE.style.display = 'none';
                }
                isSyncing = false;
            })
            .catch(err => {
                console.error("Fetch synchronization failed:", err.message);
                BANNER_ONLINE.style.display = 'none';
                isSyncing = false;
            });
        }
    })();
</script>
