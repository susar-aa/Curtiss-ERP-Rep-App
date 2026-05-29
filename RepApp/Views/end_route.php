<?php
?>
<style>
    .odo-input {
        font-family: monospace;
        font-size: 32px !important;
        text-align: center;
        letter-spacing: 2px;
        font-weight: bold;
        color: #c62828 !important;
        background: #ffebee !important;
        border: 2px solid #ef9a9a !important;
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

    .info-box { background: rgba(0,0,0,0.02); border: 1px solid var(--border); padding: 15px; border-radius: 8px; margin-bottom: 20px; text-align: center;}
</style>

<?php if(!empty($data['error'])): ?>
    <div style="background:#ffebee; color:#c62828; padding:12px; border-radius:8px; margin-bottom:20px; text-align:center; font-weight:bold; font-size:14px;">
        <?= htmlspecialchars($data['error']) ?>
    </div>
<?php endif; ?>

<div class="card" style="position: relative;">
    <h2 style="margin-top:0; color:var(--text-dark); text-align:center; font-size:20px;">End Daily Route</h2>
    <p style="color:var(--text-muted); text-align:center; font-size:14px; margin-bottom:20px;">Finalize your day and generate your summary report.</p>

    <div class="info-box">
        <div style="font-size: 11px; color: var(--text-muted); text-transform: uppercase; font-weight: bold;">Started Route</div>
        <div style="font-size: 16px; font-weight: bold; color: var(--primary);"><?= htmlspecialchars($data['active_route']->route_name) ?></div>
        <div style="font-size: 13px; color: var(--text-muted); margin-top: 5px;">Starting ODO: <strong><?= $data['active_route']->start_meter ?> KM</strong></div>
    </div>

    <form id="endTripForm">
        <label class="form-label" style="text-align:center; margin-top:10px;">Ending Odometer (KM)</label>
        <input type="text" id="odoDisplay" class="form-input odo-input" placeholder="000000" inputmode="numeric" required>
        
        <input type="hidden" name="end_meter" id="actualMeterValue">
        <input type="hidden" name="end_lat" id="gpsLat">
        <input type="hidden" name="end_lng" id="gpsLng">
        
        <!-- Store start meter for JS validation -->
        <input type="hidden" id="startMeterValue" value="<?= $data['active_route']->start_meter ?>">

        <button type="button" class="btn-primary" id="endBtn" style="background: #c62828; box-shadow: 0 4px 12px rgba(198,40,40,0.3);" onclick="initiateEndRoute()">End Route & Generate Report</button>

        <div class="loading-overlay" id="loadingOverlay">
            Fetching GPS & Compiling Report... 📍
        </div>
    </form>
</div>

<script>
    const odoDisplay = document.getElementById('odoDisplay');
    const actualMeterValue = document.getElementById('actualMeterValue');

    odoDisplay.addEventListener('input', function(e) {
        try {
            let val = this.value.replace(/[^0-9]/g, '');
            if (val.length > 6) { val = val.substring(0, 6); }
            this.value = val;
            actualMeterValue.value = val;
        } catch (err) { console.log(err); }
    });

    function initiateEndRoute() {
        const form = document.getElementById('endTripForm');
        const startMeter = parseFloat(document.getElementById('startMeterValue').value);
        const endMeter = parseFloat(actualMeterValue.value);
        
        if (!form.reportValidity()) return;
        
        if (!endMeter || endMeter <= 0) {
            alert("Please enter a valid ending meter reading.");
            return;
        }

        if (endMeter < startMeter) {
            alert("Ending meter (" + endMeter + ") cannot be less than starting meter (" + startMeter + ")!");
            return;
        }

        document.getElementById('loadingOverlay').style.display = 'flex';
        document.getElementById('endBtn').disabled = true;

        const executeSubmit = () => {
            const targetUrl = "<?= APP_URL ?>/rep/RepDashboard/process_end_route";
            const formData = new FormData(form);
            
            fetch(targetUrl, { method: 'POST', body: formData })
            .then(response => {
                if (response.redirected) {
                    window.location.href = response.url;
                } else {
                    alert("Error: Server did not respond correctly.");
                    document.getElementById('loadingOverlay').style.display = 'none';
                    document.getElementById('endBtn').disabled = false;
                }
            })
            .catch(err => {
                alert("Network Error: " + err.message);
                document.getElementById('loadingOverlay').style.display = 'none';
                document.getElementById('endBtn').disabled = false;
            });
        };

        if ("geolocation" in navigator) {
            navigator.geolocation.getCurrentPosition(
                function(position) {
                    document.getElementById('gpsLat').value = position.coords.latitude;
                    document.getElementById('gpsLng').value = position.coords.longitude;
                    executeSubmit();
                },
                function(error) {
                    console.log("GPS Error: " + error.message);
                    executeSubmit();
                },
                { enableHighAccuracy: true, timeout: 5000, maximumAge: 0 }
            );
        } else {
            executeSubmit();
        }
    }
</script>