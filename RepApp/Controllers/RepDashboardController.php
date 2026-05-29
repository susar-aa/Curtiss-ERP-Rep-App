<?php
class RepDashboardController extends RepController {
    private $routeModel;

    public function __construct() {
        if (!isset($_SESSION['user_id'])) { header('Location: ' . APP_URL . '/auth/login'); exit; }
        $this->routeModel = $this->model('RepRoute');
    }

    public function index() {
        error_log("--- Rep App: index() Loaded ---");
        
        // ROUTER FIX: Catch POST requests that land on index due to the URL structure
        if ($_SERVER['REQUEST_METHOD'] == 'POST') {
            error_log("--- Rep App: POST request intercepted in index() ---");
            error_log("POST DATA: " . print_r($_POST, true));
            
            if (isset($_POST['start_meter'])) {
                return $this->start_trip();
            }
        }

        $activeRoute = $this->routeModel->getActiveRoute($_SESSION['user_id']);
        
        // NEW: Fetch live route stats
        $routeStats = null;
        if ($activeRoute) {
            $routeStats = $this->routeModel->getRouteStats($activeRoute->id);
        }

        $data = [
            'title' => 'Territory Hub',
            'content_view' => 'dashboard',
            'active_route' => $activeRoute,
            'route_stats' => $routeStats,
            'success' => $_GET['success'] ?? ''
        ];
        $this->view('layout', $data);
    }

    public function start_route() {
        error_log("--- Rep App: start_route() UI Loaded ---");
        
        $activeRoute = $this->routeModel->getActiveRoute($_SESSION['user_id']);
        if ($activeRoute) { header('Location: ' . APP_URL . '/rep'); exit; } 

        $data = [
            'title' => 'Start Your Day',
            'content_view' => 'start_route',
            'routes' => $this->routeModel->getMcaRoutes(),
            'error' => $_GET['error'] ?? ''
        ];
        $this->view('layout', $data);
    }

    public function start_trip() {
        if ($_SERVER['REQUEST_METHOD'] == 'POST') {
            $routeName = trim($_POST['route_name']);
            $startMeter = floatval($_POST['start_meter']);
            $lat = $_POST['start_lat'] ?? null;
            $lng = $_POST['start_lng'] ?? null;

            if (empty($routeName) || $startMeter <= 0) {
                header('Location: ' . APP_URL . '/rep/start_route?error=Invalid Route or Odometer Reading.');
                exit;
            }

            if ($this->routeModel->startRoute($_SESSION['user_id'], $routeName, $startMeter, $lat, $lng)) {
                header('Location: ' . APP_URL . '/rep/dashboard?success=Route Started Successfully!');
                exit;
            }
        }
        header('Location: ' . APP_URL . '/rep');
    }

    // NEW: Display the End Route input form
    public function end_route() {
        $activeRoute = $this->routeModel->getActiveRoute($_SESSION['user_id']);
        if (!$activeRoute) { header('Location: ' . APP_URL . '/rep'); exit; }
        
        $data = [
            'title' => 'End Daily Route',
            'content_view' => 'end_route',
            'active_route' => $activeRoute,
            'error' => $_GET['error'] ?? ''
        ];
        $this->view('layout', $data);
    }

    // NEW: Process the End Route submission
    public function process_end_route() {
        if ($_SERVER['REQUEST_METHOD'] == 'POST') {
            $endMeter = floatval($_POST['end_meter']);
            $lat = $_POST['end_lat'] ?? null;
            $lng = $_POST['end_lng'] ?? null;
            
            $activeRoute = $this->routeModel->getActiveRoute($_SESSION['user_id']);
            if ($activeRoute) {
                if ($endMeter < $activeRoute->start_meter) {
                    header('Location: ' . APP_URL . '/rep/end_route?error=Ending Odometer cannot be less than Starting Odometer.');
                    exit;
                }
                $this->routeModel->endRoute($activeRoute->id, $endMeter, $lat, $lng);
                header('Location: ' . APP_URL . '/rep/route_summary/' . $activeRoute->id);
                exit;
            }
        }
        header('Location: ' . APP_URL . '/rep');
    }

    // NEW: Display the final Route Summary Report
    public function route_summary($routeId = null) {
        if (!$routeId) { header('Location: ' . APP_URL . '/rep/history'); exit; }
        
        $summary = $this->routeModel->getRouteSummaryData($routeId);
        
        // Ensure user can only view their own routes
        if (!$summary || $summary['route']->user_id != $_SESSION['user_id']) { 
            die("Unauthorized or Invalid Route"); 
        }
        
        $data = [
            'title' => 'Route Summary',
            'content_view' => 'route_summary',
            'summary' => $summary
        ];
        
        $this->view('layout', $data);
    }
}