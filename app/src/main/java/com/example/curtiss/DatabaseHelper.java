package com.example.curtiss;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "curtiss_offline.db";
    private static final int DATABASE_VERSION = 6;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 1. Products Table
        db.execSQL("CREATE TABLE products (" +
                "id INTEGER PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "category_name TEXT," +
                "price REAL DEFAULT 0.0," +
                "wholesale_price REAL DEFAULT 0.0," +
                "quantity_on_hand INTEGER DEFAULT 0," +
                "quantity_reserved INTEGER DEFAULT 0," +
                "image_url TEXT," +
                "local_image_path TEXT" +
                ")");

        // 8. Categories Table
        db.execSQL("CREATE TABLE IF NOT EXISTS categories (" +
                "id INTEGER PRIMARY KEY," +
                "name TEXT NOT NULL UNIQUE" +
                ")");

        // 2. Customers Table
        db.execSQL("CREATE TABLE customers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "server_id INTEGER DEFAULT 0," +
                "name TEXT NOT NULL," +
                "phone TEXT," +
                "whatsapp TEXT," +
                "address TEXT," +
                "territory TEXT," +
                "latitude REAL," +
                "longitude REAL," +
                "outstanding REAL DEFAULT 0.0," +
                "mca_id INTEGER DEFAULT 0," +
                "mca_name TEXT," +
                "is_synced INTEGER DEFAULT 0" +
                ")");

        // 3. Daily Routes Table
        db.execSQL("CREATE TABLE daily_routes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "server_id INTEGER DEFAULT 0," +
                "route_name TEXT NOT NULL," +
                "start_meter REAL DEFAULT 0," +
                "start_time TEXT," +
                "start_lat REAL," +
                "start_lng REAL," +
                "end_meter REAL DEFAULT 0," +
                "end_time TEXT," +
                "end_lat REAL," +
                "end_lng REAL," +
                "status TEXT DEFAULT 'Active'," +
                "is_synced INTEGER DEFAULT 0" +
                ")");

        // 4. Invoices Table
        db.execSQL("CREATE TABLE invoices (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "server_id INTEGER DEFAULT 0," +
                "invoice_number TEXT NOT NULL," +
                "customer_id INTEGER," +
                "route_id INTEGER," +
                "invoice_date TEXT," +
                "due_date TEXT," +
                "payment_term_id INTEGER," +
                "subtotal REAL DEFAULT 0.0," +
                "discount REAL DEFAULT 0.0," +
                "tax REAL DEFAULT 0.0," +
                "grand_total REAL DEFAULT 0.0," +
                "payment_method TEXT," +
                "latitude REAL," +
                "longitude REAL," +
                "is_synced INTEGER DEFAULT 0" +
                ")");

        // 5. Invoice Items Table
        db.execSQL("CREATE TABLE invoice_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "invoice_id INTEGER," +
                "product_id INTEGER," +
                "product_name TEXT," +
                "quantity INTEGER," +
                "unit_price REAL," +
                "discount_val REAL," +
                "total REAL" +
                ")");

        // 6. Server Routes (Territories) Table
        db.execSQL("CREATE TABLE server_routes (" +
                "id INTEGER PRIMARY KEY," +
                "name TEXT NOT NULL" +
                ")");

        // 7. Payment Terms Table
        db.execSQL("CREATE TABLE IF NOT EXISTS payment_terms (" +
                "id INTEGER PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "days_due INTEGER DEFAULT 0" +
                ")");

        // 9. Payments (POS Collection Cache) Table
        db.execSQL("CREATE TABLE IF NOT EXISTS payments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "customer_id INTEGER," +
                "server_route_id INTEGER," +
                "payment_method TEXT NOT NULL," +
                "amount REAL NOT NULL," +
                "bank_name TEXT," +
                "cheque_number TEXT," +
                "cheque_date TEXT," +
                "latitude REAL," +
                "longitude REAL," +
                "is_synced INTEGER DEFAULT 0," +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                ")");

        // 10. Credit Outstanding Invoices Table
        db.execSQL("CREATE TABLE IF NOT EXISTS credit_invoices (" +
                "id INTEGER PRIMARY KEY," +
                "invoice_number TEXT NOT NULL," +
                "customer_id INTEGER," +
                "invoice_date TEXT," +
                "true_grand_total REAL," +
                "customer_name TEXT," +
                "customer_address TEXT" +
                ")");

        // 11. Discount Rules Table
        db.execSQL("CREATE TABLE IF NOT EXISTS discount_rules (" +
                "id INTEGER PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "rule_type TEXT NOT NULL," +
                "target_item_id INTEGER," +
                "status TEXT" +
                ")");

        // 12. Discount Rule Tiers Table
        db.execSQL("CREATE TABLE IF NOT EXISTS discount_rule_tiers (" +
                "id INTEGER PRIMARY KEY," +
                "rule_id INTEGER," +
                "min_threshold REAL," +
                "max_threshold REAL," +
                "reward_val REAL" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS products");
        db.execSQL("DROP TABLE IF EXISTS customers");
        db.execSQL("DROP TABLE IF EXISTS daily_routes");
        db.execSQL("DROP TABLE IF EXISTS invoices");
        db.execSQL("DROP TABLE IF EXISTS invoice_items");
        db.execSQL("DROP TABLE IF EXISTS server_routes");
        db.execSQL("DROP TABLE IF EXISTS payments");
        db.execSQL("DROP TABLE IF EXISTS credit_invoices");
        db.execSQL("DROP TABLE IF EXISTS discount_rules");
        db.execSQL("DROP TABLE IF EXISTS discount_rule_tiers");
        onCreate(db);
    }

    // Helper: Bulk Insert or Update Products downloaded from server
    public void saveProduct(int id, String name, String category, double price, double wholesale, int qty, int reserved, String imgUrl) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("name", name);
        cv.put("category_name", category);
        cv.put("price", price);
        cv.put("wholesale_price", wholesale);
        cv.put("quantity_on_hand", qty);
        cv.put("quantity_reserved", reserved);
        cv.put("image_url", imgUrl);

        // Check if item exists to keep local image caching paths
        Cursor cursor = db.rawQuery("SELECT local_image_path FROM products WHERE id = ?", new String[]{String.valueOf(id)});
        if (cursor.moveToFirst()) {
            db.update("products", cv, "id = ?", new String[]{String.valueOf(id)});
        } else {
            db.insert("products", null, cv);
        }
        cursor.close();
    }

    // Helper: Update downloaded local image path in cache
    public void updateProductLocalImagePath(int productId, String path) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("local_image_path", path);
        db.update("products", cv, "id = ?", new String[]{String.valueOf(productId)});
    }

    // Helper: Add custom added customer offline
    public long insertCustomerOffline(String name, String phone, String whatsapp, String address, String territory, double lat, double lng) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("phone", phone);
        cv.put("whatsapp", whatsapp);
        cv.put("address", address);
        cv.put("territory", territory);
        cv.put("latitude", lat);
        cv.put("longitude", lng);
        cv.put("is_synced", 0);
        return db.insert("customers", null, cv);
    }

    // Helper: Insert/Start a Daily Route offline
    public long startRouteOffline(String routeName, double startMeter, String startTime, double lat, double lng) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("route_name", routeName);
        cv.put("start_meter", startMeter);
        cv.put("start_time", startTime);
        cv.put("start_lat", lat);
        cv.put("start_lng", lng);
        cv.put("status", "Active");
        cv.put("is_synced", 0);
        return db.insert("daily_routes", null, cv);
    }

    // Helper: End a Daily Route offline
    public void endRouteOffline(long localRouteId, double endMeter, String endTime, double lat, double lng) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("end_meter", endMeter);
        cv.put("end_time", endTime);
        cv.put("end_lat", lat);
        cv.put("end_lng", lng);
        cv.put("status", "Completed");
        cv.put("is_synced", 0);
        db.update("daily_routes", cv, "id = ?", new String[]{String.valueOf(localRouteId)});
    }

    // Get currently active route in the mobile system
    public Cursor getActiveRoute() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM daily_routes WHERE status = 'Active' ORDER BY id DESC LIMIT 1", null);
    }

    // Get local invoices totals for real-time dashboard calculations
    public double getRouteSalesTotal(long localRouteId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(grand_total) FROM invoices WHERE route_id = " + localRouteId, null);
        double total = 0.0;
        if (cursor.moveToFirst()) {
            total = cursor.getDouble(0);
        }
        cursor.close();
        return total;
    }

    public int getRouteInvoicesCount(long localRouteId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM invoices WHERE route_id = " + localRouteId, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    // Dynamic Territory Picker resolver
    public List<String> getTerritories() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = this.getWritableDatabase();
        // Self-healing guard: dynamically create table if missing to prevent SQLiteException crashes
        db.execSQL("CREATE TABLE IF NOT EXISTS server_routes (id INTEGER PRIMARY KEY, name TEXT NOT NULL, main_area_id INTEGER DEFAULT 0)");

        Cursor cursor = db.rawQuery("SELECT name FROM server_routes ORDER BY name ASC", null);
        while (cursor.moveToNext()) {
            list.add(cursor.getString(0));
        }
        cursor.close();

        if (list.isEmpty()) {
            // Fallback to distinct customer territories if server_routes is empty
            cursor = db.rawQuery("SELECT DISTINCT territory FROM customers WHERE territory IS NOT NULL AND territory != '' ORDER BY territory ASC", null);
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0));
            }
            cursor.close();
        }
        return list;
    }

    // Dynamic Helper: Fetch shops/customers in active route's parent main territory
    public Cursor getCustomersByActiveRouteMainTerritory() {
        return getCustomersByActiveRouteMainTerritory(null);
    }

    public Cursor getCustomersByActiveRouteMainTerritory(String filter) {
        SQLiteDatabase db = this.getReadableDatabase();
        
        // 1. Get active route name
        Cursor cActive = db.rawQuery("SELECT route_name FROM daily_routes WHERE status = 'Active' ORDER BY id DESC LIMIT 1", null);
        String activeRouteName = null;
        if (cActive.moveToFirst()) {
            activeRouteName = cActive.getString(0);
        }
        cActive.close();
        
        StringBuilder queryBuilder = new StringBuilder();
        List<String> selectionArgs = new ArrayList<>();
        
        if (activeRouteName == null) {
            // No active route, return all customers matching filter
            queryBuilder.append("SELECT * FROM customers");
            if (filter != null && !filter.trim().isEmpty()) {
                queryBuilder.append(" WHERE name LIKE ? OR territory LIKE ?");
                selectionArgs.add("%" + filter + "%");
                selectionArgs.add("%" + filter + "%");
            }
            queryBuilder.append(" ORDER BY name ASC");
            return db.rawQuery(queryBuilder.toString(), selectionArgs.toArray(new String[0]));
        }
        
        // 2. Get main_area_id for the active route
        Cursor cArea = db.rawQuery("SELECT main_area_id FROM server_routes WHERE name = ?", new String[]{activeRouteName});
        int mainAreaId = -1;
        if (cArea.moveToFirst()) {
            mainAreaId = cArea.getInt(0);
        }
        cArea.close();
        
        if (mainAreaId <= 0) {
            // Fallback: Filter by active route name
            queryBuilder.append("SELECT * FROM customers WHERE LOWER(territory) = ?");
            selectionArgs.add(activeRouteName.toLowerCase());
            if (filter != null && !filter.trim().isEmpty()) {
                queryBuilder.append(" AND (name LIKE ? OR territory LIKE ?)");
                selectionArgs.add("%" + filter + "%");
                selectionArgs.add("%" + filter + "%");
            }
            queryBuilder.append(" ORDER BY name ASC");
            return db.rawQuery(queryBuilder.toString(), selectionArgs.toArray(new String[0]));
        }
        
        // 3. Get all route names under this main_area_id
        Cursor cRoutes = db.rawQuery("SELECT name FROM server_routes WHERE main_area_id = ?", new String[]{String.valueOf(mainAreaId)});
        List<String> routeNames = new ArrayList<>();
        while (cRoutes.moveToNext()) {
            routeNames.add(cRoutes.getString(0));
        }
        cRoutes.close();
        
        if (routeNames.isEmpty()) {
            queryBuilder.append("SELECT * FROM customers WHERE LOWER(territory) = ?");
            selectionArgs.add(activeRouteName.toLowerCase());
            if (filter != null && !filter.trim().isEmpty()) {
                queryBuilder.append(" AND (name LIKE ? OR territory LIKE ?)");
                selectionArgs.add("%" + filter + "%");
                selectionArgs.add("%" + filter + "%");
            }
            queryBuilder.append(" ORDER BY name ASC");
            return db.rawQuery(queryBuilder.toString(), selectionArgs.toArray(new String[0]));
        }
        
        // 4. Construct IN query for customer territory
        queryBuilder.append("SELECT * FROM customers WHERE LOWER(territory) IN (");
        for (int i = 0; i < routeNames.size(); i++) {
            queryBuilder.append("?");
            if (i < routeNames.size() - 1) {
                queryBuilder.append(",");
            }
            selectionArgs.add(routeNames.get(i).toLowerCase());
        }
        queryBuilder.append(")");
        
        if (filter != null && !filter.trim().isEmpty()) {
            queryBuilder.append(" AND (name LIKE ? OR territory LIKE ?)");
            selectionArgs.add("%" + filter + "%");
            selectionArgs.add("%" + filter + "%");
        }
        queryBuilder.append(" ORDER BY name ASC");
        
        return db.rawQuery(queryBuilder.toString(), selectionArgs.toArray(new String[0]));
    }

    private boolean hasColumn(SQLiteDatabase db, String tableName, String columnName) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    if (columnName.equalsIgnoreCase(name)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error checking column " + columnName + " in " + tableName + ": " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // Self-healing database mechanism: dynamically add column if missing without throwing warnings/errors
        try {
            if (!hasColumn(db, "customers", "outstanding")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN outstanding REAL DEFAULT 0.0");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column outstanding to customers: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "server_routes", "main_area_id")) {
                db.execSQL("ALTER TABLE server_routes ADD COLUMN main_area_id INTEGER DEFAULT 0");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column main_area_id to server_routes: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "invoices", "payment_term_id")) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN payment_term_id INTEGER");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column payment_term_id to invoices: " + e.getMessage());
        }
        try {
            db.execSQL("UPDATE products SET category_name = 'General' WHERE category_name IS NULL OR category_name = 'null' OR category_name = ''");
        } catch (Exception e) {
            // Ignore safely
        }
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS categories (" +
                    "id INTEGER PRIMARY KEY," +
                    "name TEXT NOT NULL UNIQUE" +
                    ")");
        } catch (Exception e) {
            // Ignore safely
        }
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS representatives (" +
                    "id INTEGER PRIMARY KEY," +
                    "username TEXT UNIQUE," +
                    "password_hash TEXT," +
                    "employee_id INTEGER," +
                    "first_name TEXT," +
                    "last_name TEXT" +
                    ")");
            
            // Seed a fallback representative account for easy offline developer testing
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM representatives", null);
            int count = 0;
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();

            if (count == 0) {
                // Seeding of offline test credentials removed for security
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Seeding fallback representative error: " + e.getMessage());
        }

        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS payment_terms (" +
                    "id INTEGER PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "days_due INTEGER DEFAULT 0" +
                    ")");
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Creating payment_terms error: " + e.getMessage());
        }

        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS payments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "customer_id INTEGER," +
                    "server_route_id INTEGER," +
                    "payment_method TEXT NOT NULL," +
                    "amount REAL NOT NULL," +
                    "bank_name TEXT," +
                    "cheque_number TEXT," +
                    "cheque_date TEXT," +
                    "is_synced INTEGER DEFAULT 0," +
                    "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")");
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Creating payments table error: " + e.getMessage());
        }

        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS credit_invoices (" +
                    "id INTEGER PRIMARY KEY," +
                    "invoice_number TEXT NOT NULL," +
                    "customer_id INTEGER," +
                    "invoice_date TEXT," +
                    "true_grand_total REAL," +
                    "customer_name TEXT," +
                    "customer_address TEXT" +
                    ")");
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Creating credit_invoices table error: " + e.getMessage());
        }
    }

    // --- PAYMENTS & CREDIT INVOICES HELPERS ---
    public boolean savePayment(int customerId, int serverRouteId, String method, double amount, String bank, String chqNum, String chqDate, double latitude, double longitude) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("customer_id", customerId);
        cv.put("server_route_id", serverRouteId);
        cv.put("payment_method", method);
        cv.put("amount", amount);
        cv.put("bank_name", bank);
        cv.put("cheque_number", chqNum);
        cv.put("cheque_date", chqDate);
        cv.put("latitude", latitude);
        cv.put("longitude", longitude);
        cv.put("is_synced", 0);

        long id = db.insert("payments", null, cv);
        return id != -1;
    }

    public Cursor getUnsyncedPayments() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM payments WHERE is_synced = 0", null);
    }

    public void markPaymentsSynced() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE payments SET is_synced = 1 WHERE is_synced = 0");
    }

    public Cursor getCreditInvoices() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM credit_invoices WHERE customer_id NOT IN (SELECT customer_id FROM payments WHERE is_synced = 0) ORDER BY invoice_date ASC", null);
    }

    public Cursor getOutstandingCustomersByActiveRouteMainTerritory() {
        SQLiteDatabase db = this.getReadableDatabase();
        
        // 1. Get active route name (Ongoing MCA Area name)
        Cursor cActive = db.rawQuery("SELECT route_name FROM daily_routes WHERE status = 'Active' ORDER BY id DESC LIMIT 1", null);
        String activeRouteName = null;
        if (cActive.moveToFirst()) {
            activeRouteName = cActive.getString(0);
        }
        cActive.close();
        
        android.util.Log.d("DatabaseHelper", "Active Route Name gathered from daily_routes: " + activeRouteName);

        if (activeRouteName == null) {
            android.util.Log.w("DatabaseHelper", "No active route started! Returning empty cursor.");
            return db.rawQuery("SELECT 0 AS customer_id, '' AS customer_name, '' AS customer_address, 0.0 AS total_outstanding WHERE 0", null);
        }

        // 2. Get main_area_id for the active route
        Cursor cArea = db.rawQuery("SELECT main_area_id FROM server_routes WHERE name = ?", new String[]{activeRouteName});
        int mainAreaId = -1;
        if (cArea.moveToFirst()) {
            mainAreaId = cArea.getInt(0);
        }
        cArea.close();
        
        android.util.Log.d("DatabaseHelper", "Resolved Main Area ID for '" + activeRouteName + "': " + mainAreaId);

        List<String> territoryNames = new ArrayList<>();
        territoryNames.add(activeRouteName.toLowerCase()); // Always include the current active route

        if (mainAreaId > 0) {
            // 3. Get all route names under this main_area_id to include the whole territory/main area
            Cursor cRoutes = db.rawQuery("SELECT name FROM server_routes WHERE main_area_id = ?", new String[]{String.valueOf(mainAreaId)});
            while (cRoutes.moveToNext()) {
                String rName = cRoutes.getString(0);
                if (rName != null && !rName.trim().isEmpty()) {
                    territoryNames.add(rName.toLowerCase());
                }
            }
            cRoutes.close();
        }
        
        android.util.Log.d("DatabaseHelper", "List of territories to query outstanding customers: " + territoryNames.toString());

        StringBuilder queryBuilder = new StringBuilder();
        List<String> selectionArgs = new ArrayList<>();
        
        queryBuilder.append("SELECT customer_id, customer_name, customer_address, SUM(true_grand_total) AS total_outstanding " +
                            "FROM credit_invoices " +
                            "WHERE customer_id NOT IN (SELECT customer_id FROM payments WHERE is_synced = 0)");

        // 4. Construct IN query for customer territory or mca_name
        queryBuilder.append(" AND customer_id IN (SELECT server_id FROM customers WHERE LOWER(territory) IN (");
        for (int i = 0; i < territoryNames.size(); i++) {
            queryBuilder.append("?");
            if (i < territoryNames.size() - 1) {
                queryBuilder.append(",");
            }
            selectionArgs.add(territoryNames.get(i));
        }
        queryBuilder.append(") OR LOWER(mca_name) IN (");
        for (int i = 0; i < territoryNames.size(); i++) {
            queryBuilder.append("?");
            if (i < territoryNames.size() - 1) {
                queryBuilder.append(",");
            }
            selectionArgs.add(territoryNames.get(i));
        }
        queryBuilder.append("))");

        queryBuilder.append(" GROUP BY customer_id, customer_name, customer_address ORDER BY customer_name ASC");
        
        String sql = queryBuilder.toString();
        android.util.Log.d("DatabaseHelper", "Constructed credit outstanding SQL: " + sql);
        android.util.Log.d("DatabaseHelper", "Selection arguments: " + selectionArgs.toString());

        Cursor result = db.rawQuery(sql, selectionArgs.toArray(new String[0]));
        android.util.Log.d("DatabaseHelper", "Number of outstanding customers matched in DB: " + (result != null ? result.getCount() : 0));
        
        // Print all available rows for detailed logcat debugging
        if (result != null) {
            int count = 0;
            while (result.moveToNext()) {
                count++;
                int id = result.getInt(result.getColumnIndexOrThrow("customer_id"));
                String name = result.getString(result.getColumnIndexOrThrow("customer_name"));
                double bal = result.getDouble(result.getColumnIndexOrThrow("total_outstanding"));
                android.util.Log.d("DatabaseHelper", "Row " + count + " -> Customer ID: " + id + ", Name: " + name + ", Balance: LKR " + bal);
            }
            result.moveToPosition(-1); // Reset cursor position for caller
        }

        return result;
    }

    // Check if there are any pending uploads in the offline database
    public boolean hasPendingUploads() {
        SQLiteDatabase db = this.getReadableDatabase();
        
        // 1. Check unsynced customers
        Cursor c1 = db.rawQuery("SELECT COUNT(*) FROM customers WHERE is_synced = 0", null);
        if (c1.moveToFirst() && c1.getInt(0) > 0) { c1.close(); return true; }
        c1.close();

        // 2. Check unsynced daily routes
        Cursor c2 = db.rawQuery("SELECT COUNT(*) FROM daily_routes WHERE is_synced = 0", null);
        if (c2.moveToFirst() && c2.getInt(0) > 0) { c2.close(); return true; }
        c2.close();

        // 3. Check unsynced invoices
        Cursor c3 = db.rawQuery("SELECT COUNT(*) FROM invoices WHERE is_synced = 0", null);
        if (c3.moveToFirst() && c3.getInt(0) > 0) { c3.close(); return true; }
        c3.close();

        // 4. Check unsynced payments
        try {
            Cursor c4 = db.rawQuery("SELECT COUNT(*) FROM payments WHERE is_synced = 0", null);
            if (c4.moveToFirst() && c4.getInt(0) > 0) { c4.close(); return true; }
            c4.close();
        } catch (Exception e) {
            // payments table might not exist
        }

        return false;
    }

    // Clear local cache to allow clean redownload from online server
    public void clearLocalData(boolean force) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            // Delete configuration, catalog and clean static data
            db.execSQL("DELETE FROM products");
            db.execSQL("DELETE FROM categories");
            db.execSQL("DELETE FROM server_routes");
            db.execSQL("DELETE FROM payment_terms");
            db.execSQL("DELETE FROM credit_invoices");
            db.execSQL("DELETE FROM discount_rules");
            db.execSQL("DELETE FROM discount_rule_tiers");
            
            try {
                db.execSQL("DELETE FROM representatives");
            } catch (Exception e) {}

            if (force) {
                db.execSQL("DELETE FROM customers");
                db.execSQL("DELETE FROM daily_routes");
                db.execSQL("DELETE FROM invoices");
                db.execSQL("DELETE FROM invoice_items");
                try {
                    db.execSQL("DELETE FROM payments");
                } catch (Exception e) {}
            } else {
                // Delete ONLY synced data, preserving offline pending items and the active ongoing route progress
                db.execSQL("DELETE FROM customers WHERE is_synced = 1");
                
                // Preserve the active route and only delete completed routes
                db.execSQL("DELETE FROM daily_routes WHERE is_synced = 1 AND status = 'Completed'");
                
                // Preserve invoices created on the active route
                db.execSQL("DELETE FROM invoices WHERE is_synced = 1 AND route_id NOT IN (SELECT id FROM daily_routes WHERE status = 'Active')");
                db.execSQL("DELETE FROM invoice_items WHERE invoice_id NOT IN (SELECT id FROM invoices)");
                try {
                    db.execSQL("DELETE FROM payments WHERE is_synced = 1 AND server_route_id NOT IN (SELECT server_id FROM daily_routes WHERE status = 'Active')");
                } catch (Exception e) {}
            }

            db.setTransactionSuccessful();
            android.util.Log.d("DatabaseHelper", "Successfully cleared local database cache (Force: " + force + ")");
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error clearing local database cache: " + e.getMessage());
        } finally {
            db.endTransaction();
        }
    }
}
