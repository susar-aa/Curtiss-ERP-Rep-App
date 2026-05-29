<?php
class CustomersController extends RepController {
    private $customerModel;
    private $routeModel;

    public function __construct() {
        if (!isset($_SESSION['user_id'])) { header('Location: ' . APP_URL . '/auth/login'); exit; }
        $this->customerModel = $this->model('RepCustomer');
        $this->routeModel = $this->model('RepRoute');
    }

    public function index() {
        // Find if the rep is currently on an active route
        $activeRoute = $this->routeModel->getActiveRoute($_SESSION['user_id']);
        
        // Show all customers in the database (whole territory) as requested
        $customers = $this->customerModel->getAllCustomers();
        $subtitle = "Showing All Territory Customers";

        $data = [
            'title' => 'Territory Shops',
            'content_view' => 'customers',
            'active_route' => $activeRoute,
            'subtitle' => $subtitle,
            'customers' => $customers,
            'error' => $_GET['error'] ?? '',
            'success' => $_GET['success'] ?? ''
        ];
        $this->view('layout', $data);
    }

    public function save() {
        if ($_SERVER['REQUEST_METHOD'] == 'POST') {
            $action = $_POST['action'] ?? '';
            
            if ($action === 'delete') {
                if ($this->customerModel->deleteCustomer($_POST['customer_id'])) {
                    header('Location: ' . APP_URL . '/rep/customers?success=' . urlencode('Customer deleted successfully.'));
                } else {
                    header('Location: ' . APP_URL . '/rep/customers?error=' . urlencode('Cannot delete: Customer is linked to existing invoices.'));
                }
                exit;
            }

            // Combine the 3 address lines beautifully
            $addr1 = trim($_POST['addr1'] ?? '');
            $addr2 = trim($_POST['addr2'] ?? '');
            $addr3 = trim($_POST['addr3'] ?? '');
            $combinedAddress = implode(' | ', array_filter([$addr1, $addr2, $addr3]));

            $custData = [
                'id' => $_POST['customer_id'] ?? null,
                'name' => trim($_POST['name'] ?? ''),
                'phone' => trim($_POST['phone'] ?? ''),
                'whatsapp' => trim($_POST['whatsapp'] ?? ''),
                'address' => $combinedAddress,
                'territory' => $_POST['territory'] ?? null, // Pulled from hidden field
                'lat' => !empty($_POST['latitude']) ? $_POST['latitude'] : null,
                'lng' => !empty($_POST['longitude']) ? $_POST['longitude'] : null,
                'update_location' => isset($_POST['at_shop']) && $_POST['at_shop'] == '1'
            ];

            if (empty($custData['name'])) {
                header('Location: ' . APP_URL . '/rep/customers?error=' . urlencode('Shop Name is required.'));
                exit;
            }

            if ($action === 'add') {
                $this->customerModel->addCustomer($custData);
                header('Location: ' . APP_URL . '/rep/customers?success=' . urlencode('New Shop added to territory!'));
            } elseif ($action === 'edit') {
                $this->customerModel->updateCustomer($custData);
                header('Location: ' . APP_URL . '/rep/customers?success=' . urlencode('Shop profile updated.'));
            }
            exit;
        }
    }
}