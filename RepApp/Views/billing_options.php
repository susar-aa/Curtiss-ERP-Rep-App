<?php
?>
<style>
    .options-container { display: flex; flex-direction: column; gap: 20px; margin-top: 20px; }
    .bill-option-card {
        background: var(--surface); border: 2px solid var(--border); border-radius: 16px; padding: 30px 20px; text-align: center; text-decoration: none; color: var(--text-dark); transition: 0.2s; box-shadow: 0 4px 15px rgba(0,0,0,0.02);
    }
    .bill-option-card:active { transform: scale(0.96); background: rgba(0,102,204,0.05); border-color: var(--primary); }
    .bill-icon { font-size: 50px; margin-bottom: 15px; }
    .bill-title { font-size: 20px; font-weight: bold; margin-bottom: 8px; color: var(--primary); }
    .bill-desc { font-size: 14px; color: var(--text-muted); line-height: 1.4; }
</style>

<div class="card" style="text-align: center; background: transparent; box-shadow: none; border: none; padding: 10px 0;">
    <h2 style="margin-top:0; color: var(--text-dark);">Select Billing Workflow</h2>
    <p style="color: var(--text-muted); font-size: 14px; margin-bottom: 10px;">How would you like to build this customer's invoice?</p>

    <div class="options-container">
        <!-- Visual Catalog Option -->
        <a href="<?= APP_URL ?>/rep/billing/catalog" class="bill-option-card" style="border-color: #0066cc; background: rgba(0,102,204,0.02);">
            <div class="bill-icon">📱</div>
            <div class="bill-title">Visual Catalog</div>
            <div class="bill-desc">Shop by tapping product images. Excellent for presenting directly to the customer on-site.</div>
        </a>

        <!-- Standard SKU Option -->
        <a href="<?= APP_URL ?>/rep/billing/standard" class="bill-option-card">
            <div class="bill-icon">⌨️</div>
            <div class="bill-title">Standard Input</div>
            <div class="bill-desc">Fast text-based SKU search. Best for high-volume orders where item codes are known.</div>
        </a>
    </div>
</div>