<?php
$isActive = isset($data['active_route']) && $data['active_route'];
?>
<style>
    .dash-header { background: var(--primary); color: #fff; padding: 20px; border-radius: 12px; margin-bottom: 20px; box-shadow: 0 4px 15px rgba(0,102,204,0.2); }
    .dash-header h2 { margin: 0 0 5px 0; font-size: 20px; }
    .dash-header p { margin: 0; font-size: 13px; opacity: 0.9; }
    
    .grid-options { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; }
    .option-card { background: var(--surface); padding: 20px 15px; border-radius: 12px; border: 1px solid var(--border); text-align: center; text-decoration: none; color: var(--text-dark); display: flex; flex-direction: column; align-items: center; justify-content: center; transition: 0.2s; box-shadow: 0 2px 5px rgba(0,0,0,0.02);}
    .option-card:active { transform: scale(0.96); background: rgba(0,0,0,0.02); }
    .option-icon { font-size: 32px; margin-bottom: 10px; }
    .option-title { font-size: 14px; font-weight: 600; }
    
    /* New styling for the route metrics box */
    .route-metrics { background: rgba(255,255,255,0.15); padding: 12px; border-radius: 8px; margin-top: 15px; display: flex; justify-content: space-between; align-items: center; border: 1px solid rgba(255,255,255,0.2);}
    .metric-label { font-size: 11px; text-transform: uppercase; opacity: 0.9; letter-spacing: 0.5px; }
    .metric-value { font-size: 18px; font-weight: bold; margin-top: 2px;}
</style>

<?php if(!empty($data['success'])): ?>
    <div style="padding: 12px; background:#e8f5e9; color:#2e7d32; border-radius:8px; margin-bottom:20px; font-size: 14px; font-weight:bold; text-align:center;">
        ✓ <?= htmlspecialchars($data['success']) ?>
    </div>
<?php endif; ?>

<?php if(!$isActive): ?>
    <div class="dash-header" style="background: #333;">
        <h2>Good Morning!</h2>
        <p>You haven't started a route today.</p>
    </div>
    
    <div class="card" style="text-align:center; padding: 40px 20px;">
        <div style="font-size: 50px; margin-bottom: 20px;">🏍️</div>
        <h3 style="margin-top:0;">Start Your Day</h3>
        <p style="color:var(--text-muted); font-size:14px; margin-bottom:30px;">Enter your starting odometer and select your territory to unlock billing features.</p>
        <a href="<?= APP_URL ?>/rep/start_route" class="btn-primary" style="text-decoration:none;">Start Route Now</a>
    </div>

    <h3 style="font-size: 16px; margin-bottom: 15px; color: var(--text-dark);">Available Actions</h3>
    <div class="grid-options">
        <a href="<?= APP_URL ?>/rep/customers" class="option-card">
            <div class="option-icon">🏪</div>
            <div class="option-title">Customers</div>
        </a>
        <a href="<?= APP_URL ?>/rep/history" class="option-card">
            <div class="option-icon">📋</div>
            <div class="option-title">My Sales / Stats</div>
        </a>
    </div>

<?php else: ?>
    
    <div class="dash-header">
        <h2><?= htmlspecialchars($data['active_route']->route_name) ?></h2>
        <p>Started at <?= date('h:i A', strtotime($data['active_route']->start_time)) ?> | ODO: <?= $data['active_route']->start_meter ?> KM</p>
        
        <!-- NEW: Real-time Route Distances Display -->
        <div class="route-metrics">
            <div>
                <div class="metric-label">Target Path</div>
                <div class="metric-value"><?= number_format($data['active_route']->actual_route_km ?? 0, 1) ?> <span style="font-size: 12px; font-weight:normal;">KM</span></div>
            </div>
            <div style="text-align: right;">
                <div class="metric-label">Assigned Budget</div>
                <div class="metric-value"><?= number_format($data['active_route']->budget_km ?? 0, 1) ?> <span style="font-size: 12px; font-weight:normal;">KM</span></div>
            </div>
        </div>
    </div>

    <!-- NEW: Live Sales Statistics -->
    <div class="grid-options" style="margin-bottom: 20px;">
        <div class="option-card" style="background: rgba(46,125,50,0.05); border-color: rgba(46,125,50,0.2); padding: 15px;">
            <div class="option-title" style="color: #2e7d32; font-size: 12px; text-transform: uppercase;">Total Sales</div>
            <div style="font-size: 22px; font-weight: bold; color: #2e7d32; margin-top: 5px;">Rs: <?= number_format($data['route_stats']->total_sales ?? 0, 2) ?></div>
        </div>
        <div class="option-card" style="background: rgba(0,102,204,0.05); border-color: rgba(0,102,204,0.2); padding: 15px;">
            <div class="option-title" style="color: #0066cc; font-size: 12px; text-transform: uppercase;">Bills Made</div>
            <div style="font-size: 22px; font-weight: bold; color: #0066cc; margin-top: 5px;"><?= $data['route_stats']->bill_count ?? 0 ?></div>
        </div>
    </div>

    <h3 style="font-size: 16px; margin-bottom: 15px; color: var(--text-dark);">Territory Actions</h3>
    
    <div class="grid-options">
        <a href="<?= APP_URL ?>/rep/customers" class="option-card">
            <div class="option-icon">🏪</div>
            <div class="option-title">Customers</div>
        </a>
        <a href="<?= APP_URL ?>/rep/billing" class="option-card">
            <div class="option-icon">🧾</div>
            <div class="option-title">New Invoice</div>
        </a>
        <a href="<?= APP_URL ?>/rep/history" class="option-card">
            <div class="option-icon">📋</div>
            <div class="option-title">My Sales</div>
        </a>
        <a href="<?= APP_URL ?>/rep/end_route" class="option-card" style="border-color: #ff3b30;">
            <div class="option-icon">🏁</div>
            <div class="option-title" style="color: #ff3b30;">End Route</div>
        </a>
    </div>
<?php endif; ?>