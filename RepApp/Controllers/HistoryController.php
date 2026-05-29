<?php
class HistoryController extends RepController {
    private $routeModel;

    public function __construct() {
        if (!isset($_SESSION['user_id'])) { header('Location: ' . APP_URL . '/auth/login'); exit; }
        $this->routeModel = $this->model('RepRoute');
    }

    public function index() {
        $activeRoute = $this->routeModel->getActiveRoute($_SESSION['user_id']);
        
        $invoices = [];
        $stats = null;
        
        if ($activeRoute) {
            $invoices = $this->routeModel->getRouteInvoices($activeRoute->id);
            $stats = $this->routeModel->getRouteStats($activeRoute->id);
        }

        $data = [
            'title' => 'My Route Sales',
            'content_view' => 'history',
            'active_route' => $activeRoute,
            'invoices' => $invoices,
            'stats' => $stats
        ];
        
        $this->view('layout', $data);
    }
}