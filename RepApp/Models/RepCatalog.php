<?php
class RepCatalog {
    private $db;

    public function __construct() {
        $this->db = new Database(); 
    }

    public function getCategories() {
        $this->db->query("SELECT * FROM item_categories ORDER BY name ASC");
        return $this->db->resultSet();
    }

    public function getVisualCatalog() {
        // Fetch base items and their primary display image
        $this->db->query("
            SELECT i.*, c.name as category_name,
                   (SELECT image_path FROM item_images WHERE item_id = i.id AND variation_value_id IS NULL ORDER BY is_primary DESC, id ASC LIMIT 1) as image_path
            FROM items i
            LEFT JOIN item_categories c ON i.category_id = c.id
            ORDER BY c.name ASC, i.name ASC
        ");
        $items = $this->db->resultSet();
        
        // Embed variations inside each item for the modal selection screen
        foreach($items as $item) {
            $this->db->query("
                SELECT ivo.*, v.name as variation_name, vv.value_name,
                       (SELECT image_path FROM item_images WHERE item_id = :id AND variation_value_id = vv.id ORDER BY is_primary DESC, id ASC LIMIT 1) as var_image
                FROM item_variation_options ivo
                JOIN variations v ON ivo.variation_id = v.id
                JOIN variation_values vv ON ivo.variation_value_id = vv.id
                WHERE ivo.item_id = :id
            ");
            $this->db->bind(':id', $item->id);
            $item->variations = $this->db->resultSet() ?: [];
            
            // Map main item price to Wholesale price
            $billingPrice = 0.00;
            if (isset($item->wholesale_price) && floatval($item->wholesale_price) > 0) {
                $billingPrice = floatval($item->wholesale_price);
            } elseif (isset($item->selling_price) && floatval($item->selling_price) > 0) {
                $billingPrice = floatval($item->selling_price);
            } elseif (isset($item->price) && floatval($item->price) > 0) {
                $billingPrice = floatval($item->price);
            } elseif (isset($item->regular_price) && floatval($item->regular_price) > 0) {
                $billingPrice = floatval($item->regular_price);
            }
            $item->price = $billingPrice;
            $item->selling_price = $billingPrice;
            $item->wholesale_price = $billingPrice;

            // If variations are empty from database, check variations_json
            if (empty($item->variations) && !empty($item->variations_json)) {
                $decoded = json_decode($item->variations_json);
                if (is_array($decoded)) {
                    $item->variations = [];
                    foreach ($decoded as $v) {
                        $vObj = new stdClass();
                        $vObj->id = $v->id ?? 0;
                        $vObj->variation_name = 'Option';
                        $vObj->value_name = $v->attribute ?? '';
                        $vObj->sku = $v->sku ?? '';
                        $vObj->quantity_on_hand = $v->qty ?? $item->quantity_on_hand ?? 0;
                        $vObj->quantity_reserved = $v->quantity_reserved ?? 0;
                        
                        // Enforce wholesale price on variation
                        $vPrice = 0.00;
                        if (isset($v->wholesale_price) && floatval($v->wholesale_price) > 0) {
                            $vPrice = floatval($v->wholesale_price);
                        } elseif (isset($v->price) && floatval($v->price) > 0) {
                            $vPrice = floatval($v->price);
                        } else {
                            $vPrice = $billingPrice;
                        }
                        $vObj->price = $vPrice;
                        $item->variations[] = $vObj;
                    }
                }
            } else if (!empty($item->variations)) {
                // Enforce wholesale price on DB variations
                foreach ($item->variations as $var) {
                    $varPrice = 0.00;
                    if (isset($var->wholesale_price) && floatval($var->wholesale_price) > 0) {
                        $varPrice = floatval($var->wholesale_price);
                    } elseif (isset($var->price) && floatval($var->price) > 0) {
                        $varPrice = floatval($var->price);
                    } else {
                        $varPrice = $billingPrice;
                    }
                    $var->price = $varPrice;
                }
            }
        }
        return $items;
    }
}