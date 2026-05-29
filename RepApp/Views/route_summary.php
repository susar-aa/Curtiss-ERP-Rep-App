<?php
$s = $data['summary'];
$route = $s['route'];
$sales = $s['sales'];

$distanceDriven = $route->end_meter - $route->start_meter;
$budget = floatval($s['budget_km']);
$overage = $distanceDriven - $budget;

$cash = 0; $bank = 0; $cheque = 0; $chequeCount = 0;
foreach($s['collections'] as $c) {
    if ($c->payment_method === 'Cash') { $cash += $c->total_collected; }
    if ($c->payment_method === 'Bank Transfer') { $bank += $c->total_collected; }
    if ($c->payment_method === 'Cheque') { 
        $cheque += $c->total_collected; 
        $chequeCount = $c->tx_count; 
    }
}
$totalCollected = $cash + $bank + $cheque;
?>
<style>
    /* Mobile App View Styles */
    .summary-card { background: var(--surface); padding: 25px; border-radius: 12px; box-shadow: 0 10px 30px rgba(0,0,0,0.05); margin-bottom: 20px;}
    .summary-header { text-align: center; border-bottom: 2px solid var(--border); padding-bottom: 15px; margin-bottom: 20px;}
    .summary-header h2 { margin: 0 0 5px 0; color: var(--primary); font-size: 22px;}
    .summary-header p { margin: 0; color: var(--text-muted); font-size: 13px;}
    
    .section-title { font-size: 12px; font-weight: bold; color: var(--text-muted); text-transform: uppercase; margin-bottom: 10px; letter-spacing: 0.5px;}
    
    .data-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px dashed var(--border); font-size: 14px;}
    .data-row:last-child { border-bottom: none; }
    .data-label { color: var(--text-dark); }
    .data-val { font-weight: bold; font-family: monospace; font-size: 16px; color: var(--text-dark);}
    
    .highlight-box { background: rgba(0,102,204,0.05); padding: 15px; border-radius: 8px; border: 1px solid rgba(0,102,204,0.2); margin-top: 10px;}
    .highlight-val { font-size: 24px; font-weight: bold; color: #0066cc; font-family: monospace; text-align: right;}
    
    .alert-box { background: #ffebee; color: #c62828; padding: 10px; border-radius: 6px; font-size: 13px; font-weight: bold; text-align: center; margin-top: 10px;}

    .share-btn { width: 100%; background: #333; color: #fff; border: none; padding: 16px; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 12px rgba(0,0,0,0.2); text-align: center; display: flex; align-items: center; justify-content: center; gap: 10px;}

    /* PDF Print & Share Optimization */
    @media print {
        @page { margin: 10mm; size: A4 portrait;}
        body { background: #fff !important; margin: 0; padding: 0;}
        .mobile-container { width: 100%; max-width: none; box-shadow: none; height: auto; display: block;}
        .app-header, .bottom-nav, .share-btn, .btn-primary { display: none !important; }
        .summary-card { box-shadow: none; border: none; padding: 0;}
        .data-row { border-bottom: 1px solid #ccc;}
    }
</style>

<button class="share-btn" onclick="window.print()" style="margin-bottom: 20px;">
    <span>📄</span> Share / Export as PDF
</button>

<div class="summary-card">
    <div class="summary-header">
        <h2>Daily Route Summary</h2>
        <p><strong>Route:</strong> <?= htmlspecialchars($route->route_name) ?></p>
        <p><?= date('M d, Y', strtotime($route->start_time)) ?></p>
    </div>

    <!-- Distance Tracking -->
    <div style="margin-bottom: 25px;">
        <div class="section-title">Mileage & Timing</div>
        <div class="data-row">
            <span class="data-label">Start Time</span>
            <span class="data-val"><?= date('h:i A', strtotime($route->start_time)) ?></span>
        </div>
        <div class="data-row">
            <span class="data-label">End Time</span>
            <span class="data-val"><?= date('h:i A', strtotime($route->end_time)) ?></span>
        </div>
        <div class="data-row">
            <span class="data-label">Meter Start</span>
            <span class="data-val"><?= $route->start_meter ?> KM</span>
        </div>
        <div class="data-row">
            <span class="data-label">Meter End</span>
            <span class="data-val"><?= $route->end_meter ?> KM</span>
        </div>
        <div class="data-row" style="background: rgba(0,0,0,0.02); padding: 10px; border-radius: 4px; margin-top: 5px;">
            <span class="data-label">Total Distance Driven</span>
            <span class="data-val"><?= number_format($distanceDriven, 1) ?> KM</span>
        </div>
        
        <?php if($budget > 0 && $overage > 0): ?>
            <div class="alert-box">
                ⚠ Over Budget by <?= number_format($overage, 1) ?> KM (Limit: <?= number_format($budget, 1) ?> KM)
            </div>
        <?php endif; ?>
    </div>

    <!-- Sales Tracking -->
    <div style="margin-bottom: 25px;">
        <div class="section-title">Sales Performance</div>
        <div class="data-row">
            <span class="data-label">Total Bills Generated</span>
            <span class="data-val"><?= $sales->bill_count ?></span>
        </div>
        <div class="highlight-box" style="border-color: #2e7d32; background: rgba(46,125,50,0.05);">
            <div style="font-size: 12px; color: #2e7d32; font-weight: bold; text-transform: uppercase;">Total Sales Value</div>
            <div class="highlight-val" style="color: #2e7d32;">Rs: <?= number_format($sales->total_sales, 2) ?></div>
        </div>
    </div>

    <!-- Collections Tracking -->
    <div style="margin-bottom: 25px;">
        <div class="section-title">Collections & Receipts</div>
        <div class="data-row">
            <span class="data-label">Cash Collected</span>
            <span class="data-val" style="color: #2e7d32;">Rs: <?= number_format($cash, 2) ?></span>
        </div>
        <div class="data-row">
            <span class="data-label">Bank Transfers</span>
            <span class="data-val">Rs: <?= number_format($bank, 2) ?></span>
        </div>
        <div class="data-row">
            <span class="data-label">Cheques Received (<?= $chequeCount ?>)</span>
            <span class="data-val" style="color: #f57c00;">Rs: <?= number_format($cheque, 2) ?></span>
        </div>
        <div class="highlight-box">
            <div style="font-size: 12px; color: #0066cc; font-weight: bold; text-transform: uppercase;">Total Value Collected</div>
            <div class="highlight-val">Rs: <?= number_format($totalCollected, 2) ?></div>
        </div>
    </div>

    <div style="text-align: center; color: var(--text-muted); font-size: 11px; margin-top: 30px; border-top: 1px solid var(--border); padding-top: 15px;">
        Generated by CURTISS ERP Mobile Engine<br>
        Report ID: R-<?= str_pad($route->id, 5, '0', STR_PAD_LEFT) ?>
    </div>
</div>

<a href="<?= APP_URL ?>/rep" class="btn-primary" style="background: transparent; color: var(--primary); border: 2px solid var(--primary); box-shadow: none;">Return to Dashboard</a>