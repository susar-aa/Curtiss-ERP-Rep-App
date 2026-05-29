<?php
?>
<style>
    .odo-input {
        font-family: monospace;
        font-size: 32px !important;
        text-align: center;
        letter-spacing: 2px;
        font-weight: bold;
        color: var(--primary) !important;
        background: rgba(0, 102, 204, 0.05) !important;
        border: 2px solid var(--primary) !important;
    }
    
    .loading-overlay {
        display: none;
        position: absolute;
        top: 0; left: 0; right: 0; bottom: 0;
        background: rgba(0,0,0,0.7);
        border-radius: 12px;
        align-items: center;
        justify-content: center;
        color: white;
        font-weight: bold;
        z-index: 50;
    }

    .terminal-log {
        background: #111;
        color: #0f0;
        padding: 15px;
        font-family: monospace;
        font-size: 11px;
        margin-top: 20px;
        border-radius: 6px;
        max-height: 200px;
        overflow-y: auto;
        border: 1px solid #333;
    }
</style>

<?php if(!empty($data['error'])): ?>
    <div style="background:#ffebee; color:#c62828; padding:12px; border-radius:8px; margin-bottom:20px; text-align:center; font-weight:bold; font-size:14px;">
        <?= htmlspecialchars($data['error']) ?>
    </div>
<?php endif; ?>

<div class="card" style="position: relative;">
    <h2 style="margin-top:0; color:var(--text-dark); text-align:center; font-size:20px;">Start Daily Route</h2>
    <p style="color:var(--text-muted); text-align:center; font-size:14px; margin-bottom:30px;">Select your assigned territory and enter your vehicle's starting odometer reading.</p>

    <form id="startTripForm">
        <label class="form-label">MCA Territory / Route</label>
        <select name="route_name" class="form-input" required>
            <option value="">Select Route...</option>
            <?php foreach($data['routes'] as $route): ?>
                <option value="<?= htmlspecialchars($route->name) ?>"><?= htmlspecialchars($route->name) ?></option>
            <?php endforeach; ?>
        </select>

        <label class="form-label" style="text-align:center; margin-top:10px;">Starting Odometer (KM)</label>
        <input type="text" id="odoDisplay" class="form-input odo-input" placeholder="000000" inputmode="numeric" required>
        
        <input type="hidden" name="start_meter" id="actualMeterValue">
        <input type="hidden" name="start_lat" id="gpsLat">
        <input type="hidden" name="start_lng" id="gpsLng">

        <button type="button" class="btn-primary" id="startBtn" onclick="initiateRoute()">Start Tracking &rarr;</button>

        <div class="loading-overlay" id="loadingOverlay">
            Fetching GPS Location... 📍
        </div>
    </form>
    
    <!-- On-Screen Visual Debugger -->
    <div id="debugLog" class="terminal-log">
        System Ready. Waiting for action...
    </div>
    <div id="phpErrorBox"></div>
</div>

<script>
    const odoDisplay = document.getElementById('odoDisplay');
    const actualMeterValue = document.getElementById('actualMeterValue');

    function logDebug(msg) {
        const logDiv = document.getElementById('debugLog');
        if(logDiv) {
            logDiv.innerHTML += `<br>[${new Date().toLocaleTimeString()}] ${msg}`;
            logDiv.scrollTop = logDiv.scrollHeight;
        }
        console.log(msg);
    }

    odoDisplay.addEventListener('input', function(e) {
        try {
            let val = this.value.replace(/[^0-9]/g, '');
            
            // Limit to exactly 6 digits
            if (val.length > 6) { val = val.substring(0, 6); }
            
            this.value = val;
            actualMeterValue.value = val;
        } catch (err) {
            logDebug("Formatter Error: " + err.message);
        }
    });

    function initiateRoute() {
        logDebug("1. Start Route button tapped.");
        const form = document.getElementById('startTripForm');
        
        if (!form.reportValidity()) {
            logDebug("WARNING: Form validation failed. Missing route or meter.");
            return;
        }
        
        if (!actualMeterValue.value || parseFloat(actualMeterValue.value) <= 0) {
            logDebug("WARNING: Invalid meter reading.");
            alert("Please enter a valid starting meter reading.");
            return;
        }

        logDebug("2. Form valid. ODO: " + actualMeterValue.value);
        document.getElementById('loadingOverlay').style.display = 'flex';
        document.getElementById('startBtn').disabled = true;

        const executeSubmit = () => {
            logDebug("4. Preparing to send data to server...");
            
            // Hardcoded direct routing to completely bypass routing bugs
            const targetUrl = "<?= APP_URL ?>/rep/RepDashboard/start_trip";
            logDebug("POSTing to: " + targetUrl);

            const formData = new FormData(form);
            
            fetch(targetUrl, {
                method: 'POST',
                body: formData
            })
            .then(response => {
                logDebug("5. Server responded. HTTP Status: " + response.status);
                if (response.redirected) {
                    logDebug("6. Server sent redirect to: " + response.url);
                    logDebug("SUCCESS! Forwarding in 1.5 seconds...");
                    setTimeout(() => { window.location.href = response.url; }, 1500);
                } else {
                    return response.text();
                }
            })
            .then(html => {
                if(html) {
                    logDebug("CRITICAL: Server returned HTML/Error instead of redirecting.");
                    
                    // Print the raw HTML error response below the terminal
                    const errDiv = document.getElementById('phpErrorBox');
                    errDiv.style.background = "#fff";
                    errDiv.style.color = "#c62828";
                    errDiv.style.padding = "10px";
                    errDiv.style.marginTop = "10px";
                    errDiv.style.border = "1px solid #c62828";
                    errDiv.style.borderRadius = "4px";
                    errDiv.innerHTML = "<strong>Raw Server Response:</strong><br>" + html;
                    
                    document.getElementById('loadingOverlay').style.display = 'none';
                    document.getElementById('startBtn').disabled = false;
                }
            })
            .catch(err => {
                logDebug("NETWORK ERROR: " + err.message);
                document.getElementById('loadingOverlay').style.display = 'none';
                document.getElementById('startBtn').disabled = false;
            });
        };

        if ("geolocation" in navigator) {
            logDebug("3. Requesting GPS position...");
            navigator.geolocation.getCurrentPosition(
                function(position) {
                    logDebug("GPS Success! Lat: " + position.coords.latitude);
                    document.getElementById('gpsLat').value = position.coords.latitude;
                    document.getElementById('gpsLng').value = position.coords.longitude;
                    executeSubmit();
                },
                function(error) {
                    logDebug("GPS Error: " + error.message);
                    executeSubmit();
                },
                { enableHighAccuracy: true, timeout: 5000, maximumAge: 0 }
            );
        } else {
            logDebug("3. GPS not supported by browser.");
            executeSubmit();
        }
    }
</script>