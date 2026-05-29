<?php
$hasRoute = isset($data['active_route']) && $data['active_route'];
?>
<style>
    .stat-hero { background: rgba(46,125,50,0.05); border: 1px solid rgba(46,125,50,0.2); padding: 25px; border-radius: 12px; text-align: center; margin-bottom: 25px;}
    .stat-label { font-size: 12px; color: #2e7d32; text-transform: uppercase; font-weight: bold; letter-spacing: 0.5px;}
    .stat-amount { font-size: 32px; font-weight: bold; color: #2e7d32; margin: 5px 0;}
    .stat-sub { font-size: 14px; color: var(--text-muted); font-weight: 500;}

    .invoice-card { background: var(--surface); padding: 15px; border-radius: 12px; margin-bottom: 12px; border: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; box-shadow: 0 2px 8px rgba(0,0,0,0.02);}
    .inv-cust { font-weight: bold; font-size: 15px; color: var(--text-dark); margin-bottom: 4px; }
    .inv-num { font-size: 13px; color: var(--primary); font-weight: bold; }
    .inv-time { font-size: 11px; color: var(--text-muted); margin-top: 4px; }
    .inv-amt { font-weight: bold; font-size: 16px; color: var(--text-dark); text-align: right;}
    
    .status-badge { padding: 3px 8px; border-radius: 6px; font-size: 10px; font-weight: bold; display: inline-block; margin-top: 6px; text-transform: uppercase;}
    .status-Paid { background: #e8f5e9; color: #2e7d32; }
    .status-Unpaid { background: #fff3e0; color: #ef6c00; }
    .status-Draft { background: #f5f5f5; color: #666; }
</style>

<?php if(!$hasRoute): ?>
    <div class="card" style="text-align:center; padding: 40px 20px;">
        <div style="font-size: 50px; margin-bottom: 20px;">🏁</div>
        <h3 style="margin-top:0;">No Active Route</h3>
        <p style="color:var(--text-muted); font-size:14px; margin-bottom:30px;">You need to start a route to track your daily sales and bills.</p>
        <a href="<?= APP_URL ?>/rep/start_route" class="btn-primary" style="text-decoration:none;">Start Route Now</a>
    </div>
<?php else: ?>

    <div class="stat-hero">
        <div class="stat-label">Route Total Sales</div>
        <div class="stat-amount">Rs: <?= number_format($data['stats']->total_sales ?? 0, 2) ?></div>
        <div class="stat-sub">Across <?= $data['stats']->bill_count ?? 0 ?> Generated Bills</div>
    </div>

    <h3 style="font-size: 16px; margin-bottom: 15px; color: var(--text-dark);">Bills on Current Route</h3>
    
    <div id="invoiceList" style="padding-bottom: 20px;">
        <?php if(empty($data['invoices'])): ?>
            <div style="text-align:center; padding: 40px 20px; color: var(--text-muted); border: 2px dashed var(--border); border-radius: 12px;">
                <div style="font-size: 30px; margin-bottom: 10px;">🧾</div>
                No bills generated yet.<br>Head over to the Billing tab to create one.
            </div>
        <?php else: foreach($data['invoices'] as $inv): ?>
            <div class="invoice-card">
                <div>
                    <div class="inv-cust"><?= htmlspecialchars($inv->customer_name) ?></div>
                    <div class="inv-num"><?= htmlspecialchars($inv->invoice_number) ?></div>
                    <div class="inv-time">🕒 <?= date('h:i A', strtotime($inv->created_at)) ?></div>
                </div>
                <div style="text-align: right;">
                    <div class="inv-amt">Rs: <?= number_format($inv->true_grand_total, 2) ?></div>
                    <div class="status-badge status-<?= $inv->status ?>"><?= $inv->status ?></div>
                </div>
            </div>
        <?php endforeach; endif; ?>
    </div>

<?php endif; ?>