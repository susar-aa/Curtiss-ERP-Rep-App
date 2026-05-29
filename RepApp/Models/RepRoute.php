<?php
class RepRoute {
    private $db;

    public function __construct() {
        $this->db = new Database(); // Uses your core Database class
    }

    public function getActiveRoute($userId) {
        // NEW: JOIN mca_areas to pull the budget and actual distance metrics for the dashboard
        $this->db->query("SELECT r.*, m.budget_km, m.actual_route_km 
                          FROM rep_daily_routes r 
                          LEFT JOIN mca_areas m ON r.route_name = m.name 
                          WHERE r.user_id = :uid AND r.status = 'Active' 
                          LIMIT 1");
        $this->db->bind(':uid', $userId);
        return $this->db->single();
    }

    public function getMcaRoutes() {
        $this->db->query("SELECT * FROM mca_areas ORDER BY name ASC");
        return $this->db->resultSet();
    }

    public function startRoute($userId, $routeName, $startMeter, $lat, $lng) {
        $this->db->query("INSERT INTO rep_daily_routes (user_id, route_name, start_meter, start_time, start_lat, start_lng, status) 
                          VALUES (:uid, :rname, :meter, NOW(), :lat, :lng, 'Active')");
        
        $this->db->bind(':uid', $userId);
        $this->db->bind(':rname', $routeName);
        $this->db->bind(':meter', $startMeter);
        $this->db->bind(':lat', $lat);
        $this->db->bind(':lng', $lng);
        
        return $this->db->execute();
    }

    // NEW: Fetch aggregate statistics for the dashboard
    public function getRouteStats($routeId) {
        $this->db->query("
            SELECT 
                COUNT(*) as bill_count, 
                COALESCE(SUM(total_amount - COALESCE(CASE WHEN global_discount_type = '%' THEN (total_amount * global_discount_val / 100) ELSE global_discount_val END, 0) + COALESCE(tax_amount, 0)), 0) as total_sales 
            FROM invoices 
            WHERE rep_route_id = :rid AND status != 'Voided'
        ");
        $this->db->bind(':rid', $routeId);
        return $this->db->single();
    }

    // NEW: Fetch all itemized bills generated on this specific route
    public function getRouteInvoices($routeId) {
        $this->db->query("
            SELECT i.*, c.name as customer_name,
            (total_amount - COALESCE(CASE WHEN global_discount_type = '%' THEN (total_amount * global_discount_val / 100) ELSE global_discount_val END, 0) + COALESCE(tax_amount, 0)) as true_grand_total
            FROM invoices i 
            JOIN customers c ON i.customer_id = c.id 
            WHERE i.rep_route_id = :rid 
            ORDER BY i.created_at DESC
        ");
        $this->db->bind(':rid', $routeId);
        return $this->db->resultSet();
    }

    // NEW: End the route and record final mileage
    public function endRoute($routeId, $endMeter, $lat, $lng) {
        $this->db->query("UPDATE rep_daily_routes 
                          SET end_meter = :meter, end_time = NOW(), end_lat = :lat, end_lng = :lng, status = 'Completed' 
                          WHERE id = :id");
        $this->db->bind(':id', $routeId);
        $this->db->bind(':meter', $endMeter);
        $this->db->bind(':lat', $lat);
        $this->db->bind(':lng', $lng);
        return $this->db->execute();
    }

    // NEW: Generate the comprehensive End of Day Summary Report
    public function getRouteSummaryData($routeId) {
        $this->db->query("SELECT * FROM rep_daily_routes WHERE id = :id");
        $this->db->bind(':id', $routeId);
        $route = $this->db->single();

        if (!$route) return false;

        // Sales & Bills
        $this->db->query("SELECT COUNT(*) as bill_count, 
                                 COALESCE(SUM(total_amount - COALESCE(CASE WHEN global_discount_type = '%' THEN (total_amount * global_discount_val / 100) ELSE global_discount_val END, 0) + COALESCE(tax_amount, 0)), 0) as total_sales 
                          FROM invoices WHERE rep_route_id = :rid AND status != 'Voided'");
        $this->db->bind(':rid', $routeId);
        $sales = $this->db->single();

        // Collections (Cash, Bank, Cheque) based on the exact time window of the route
        $endTime = $route->end_time ?: date('Y-m-d H:i:s');
        $this->db->query("SELECT payment_method, COUNT(*) as tx_count, COALESCE(SUM(amount), 0) as total_collected 
                          FROM customer_payments 
                          WHERE created_by = :uid AND created_at >= :start_time AND created_at <= :end_time
                          GROUP BY payment_method");
        $this->db->bind(':uid', $route->user_id);
        $this->db->bind(':start_time', $route->start_time);
        $this->db->bind(':end_time', $endTime);
        $collections = $this->db->resultSet();

        // MCA budget logic to calculate overage
        $this->db->query("SELECT budget_km FROM mca_areas WHERE name = :name LIMIT 1");
        $this->db->bind(':name', $route->route_name);
        $mca = $this->db->single();
        $budget = $mca ? $mca->budget_km : 0;

        return [
            'route' => $route,
            'sales' => $sales,
            'collections' => $collections,
            'budget_km' => $budget
        ];
    }
}