<?php
$hasRoute = isset($data['active_route']) && $data['active_route'];
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title><?= $data['title'] ?> - Rep App</title>
    <style>
        :root {
            --app-bg: #f4f5f7;
            --primary: #0066cc;
            --surface: #ffffff;
            --text-dark: #111111;
            --text-muted: #666666;
            --border: #e0e0e0;
        }

        @media (prefers-color-scheme: dark) {
            :root {
                --app-bg: #121212;
                --surface: #1e1e2d;
                --text-dark: #ffffff;
                --text-muted: #aaaaaa;
                --border: #333333;
            }
        }

        body {
            margin: 0;
            padding: 0;
            background-color: #000;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            display: flex;
            justify-content: center;
        }

        .mobile-container {
            width: 100%;
            max-width: 480px;
            height: 100vh;
            background-color: var(--app-bg);
            position: relative;
            display: flex;
            flex-direction: column;
            overflow: hidden;
            box-shadow: 0 0 20px rgba(0,0,0,0.5);
        }

        .app-header {
            background-color: var(--surface);
            padding: 15px 20px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            border-bottom: 1px solid var(--border);
            z-index: 10;
        }

        .app-header h1 {
            margin: 0;
            font-size: 18px;
            color: var(--text-dark);
        }

        .app-content {
            flex: 1;
            overflow-y: auto;
            padding: 20px;
            -webkit-overflow-scrolling: touch;
        }

        /* Bottom Navigation Bar */
        .bottom-nav {
            background-color: var(--surface);
            border-top: 1px solid var(--border);
            display: flex;
            justify-content: space-around;
            padding: 10px 0;
            padding-bottom: env(safe-area-inset-bottom, 10px);
            z-index: 10;
        }

        .nav-item {
            text-decoration: none;
            color: var(--text-muted);
            font-size: 11px;
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 4px;
            font-weight: 500;
            transition: 0.2s;
        }

        .nav-item.active { color: var(--primary); }
        .nav-icon { font-size: 20px; }

        .card { background: var(--surface); border-radius: 12px; padding: 20px; box-shadow: 0 2px 10px rgba(0,0,0,0.02); margin-bottom: 20px; }
        .form-label { display: block; font-size: 13px; font-weight: 600; color: var(--text-muted); margin-bottom: 8px; text-transform: uppercase; }
        .form-input { width: 100%; padding: 15px; border: 1px solid var(--border); border-radius: 8px; background: var(--app-bg); color: var(--text-dark); font-size: 16px; box-sizing: border-box; margin-bottom: 20px; outline: none; -webkit-appearance: none; }
        .form-input:focus { border-color: var(--primary); }
        .btn-primary { width: 100%; background: var(--primary); color: #fff; border: none; padding: 16px; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 12px rgba(0, 102, 204, 0.3); text-align: center; display: inline-block; box-sizing: border-box; }
        .btn-primary:active { opacity: 0.8; }
    </style>
</head>
<body>
    <div class="mobile-container">
        
        <header class="app-header">
            <h1><?= htmlspecialchars($data['title']) ?></h1>
            <a href="<?= APP_URL ?>/dashboard" style="color:var(--primary); text-decoration:none; font-size:14px; font-weight:bold;">ERP Exit</a>
        </header>

        <main class="app-content">
            <?php require_once '../rep_app/Views/' . $data['content_view'] . '.php'; ?>
        </main>

        <nav class="bottom-nav">
            <a href="<?= APP_URL ?>/rep" class="nav-item <?= $data['content_view'] === 'dashboard' || $data['content_view'] === 'start_route' ? 'active' : '' ?>">
                <span class="nav-icon">🏠</span>
                <span>Home</span>
            </a>
            
            <!-- NEW: Customers unlocked anytime -->
            <a href="<?= APP_URL ?>/rep/customers" class="nav-item <?= $data['content_view'] === 'customers' ? 'active' : '' ?>">
                <span class="nav-icon">🏪</span>
                <span>Shops</span>
            </a>
            
            <!-- Billing still locked until route starts -->
            <a href="<?= $hasRoute ? APP_URL.'/rep/billing' : '#' ?>" class="nav-item <?= $data['content_view'] === 'billing' ? 'active' : '' ?>" onclick="<?= !$hasRoute ? "alert('Start a route first to generate a bill!'); return false;" : "" ?>">
                <span class="nav-icon">🧾</span>
                <span>Bill</span>
            </a>
            
            <!-- NEW: Stats unlocked anytime -->
            <a href="<?= APP_URL ?>/rep/history" class="nav-item <?= $data['content_view'] === 'history' ? 'active' : '' ?>">
                <span class="nav-icon">📈</span>
                <span>Stats</span>
            </a>
        </nav>

    </div>
</body>
</html>