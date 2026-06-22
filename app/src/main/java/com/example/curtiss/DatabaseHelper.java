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
    private static final int DATABASE_VERSION = 8;

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.enableWriteAheadLogging();
    }

    public void addCustomersExtraColumns(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE customers ADD COLUMN email TEXT");
        } catch (Exception e) {}
        try {
            db.execSQL("ALTER TABLE customers ADD COLUMN credit_limit REAL DEFAULT 0.0");
        } catch (Exception e) {}
        try {
            db.execSQL("ALTER TABLE customers ADD COLUMN customer_type TEXT");
        } catch (Exception e) {}
        try {
            db.execSQL("ALTER TABLE customers ADD COLUMN notes TEXT");
        } catch (Exception e) {}
    }


    public void addProductsSearchColumns(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE products ADD COLUMN sku TEXT");
        } catch (Exception e) {}
        try {
            db.execSQL("ALTER TABLE products ADD COLUMN sample_code TEXT");
        } catch (Exception e) {}
        try {
            db.execSQL("ALTER TABLE products ADD COLUMN variations_json TEXT");
        } catch (Exception e) {}
        try {
            db.execSQL("ALTER TABLE products ADD COLUMN brand TEXT");
        } catch (Exception e) {}
        try {
            db.execSQL("ALTER TABLE products ADD COLUMN description TEXT");
        } catch (Exception e) {}
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
                "cost_price REAL DEFAULT 0.0," +
                "quantity_on_hand INTEGER DEFAULT 0," +
                "quantity_reserved INTEGER DEFAULT 0," +
                "image_url TEXT," +
                "local_image_path TEXT," +
                "sku TEXT," +
                "sample_code TEXT," +
                "variations_json TEXT," +
                "brand TEXT," +
                "description TEXT," +
                "status TEXT DEFAULT 'active'" +
                ")");

        // 8. Categories Table
        db.execSQL("CREATE TABLE IF NOT EXISTS categories (" +
                "id INTEGER PRIMARY KEY," +
                "name TEXT NOT NULL UNIQUE," +
                "status TEXT DEFAULT 'active'" +
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
                "email TEXT," +
                "credit_limit REAL DEFAULT 0.0," +
                "customer_type TEXT," +
                "notes TEXT," +
                "status TEXT DEFAULT 'active'," +
                "is_synced INTEGER DEFAULT 0," +
                "uuid TEXT UNIQUE," +
                "sync_status INTEGER DEFAULT 1," +
                "sync_attempts INTEGER DEFAULT 0," +
                "last_attempt_time TEXT," +
                "failure_reason TEXT" +
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
                "is_synced INTEGER DEFAULT 0," +
                "uuid TEXT UNIQUE," +
                "sync_status INTEGER DEFAULT 1," +
                "sync_attempts INTEGER DEFAULT 0," +
                "last_attempt_time TEXT," +
                "failure_reason TEXT" +
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
                "is_synced INTEGER DEFAULT 0," +
                "uuid TEXT UNIQUE," +
                "sync_status INTEGER DEFAULT 1," +
                "sync_attempts INTEGER DEFAULT 0," +
                "last_attempt_time TEXT," +
                "server_timestamp TEXT," +
                "failure_reason TEXT" +
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
                "name TEXT NOT NULL," +
                "status TEXT DEFAULT 'active'" +
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
                "local_route_id INTEGER DEFAULT 0," +
                "payment_method TEXT NOT NULL," +
                "amount REAL NOT NULL," +
                "bank_name TEXT," +
                "cheque_number TEXT," +
                "cheque_date TEXT," +
                "latitude REAL," +
                "longitude REAL," +
                "is_synced INTEGER DEFAULT 0," +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP," +
                "uuid TEXT UNIQUE," +
                "sync_status INTEGER DEFAULT 1," +
                "sync_attempts INTEGER DEFAULT 0," +
                "last_attempt_time TEXT," +
                "failure_reason TEXT" +
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

        // 13. Image Download Queue Table
        db.execSQL("CREATE TABLE IF NOT EXISTS image_download_queue (" +
                "product_id INTEGER PRIMARY KEY," +
                "image_url TEXT NOT NULL," +
                "status TEXT DEFAULT 'pending'," +
                "attempts INTEGER DEFAULT 0," +
                "last_error TEXT" +
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
        db.execSQL("DROP TABLE IF EXISTS image_download_queue");
        onCreate(db);
    }

    // Helper: Bulk Insert or Update Products downloaded from server
    public void saveProduct(int id, String name, String category, double price, double wholesale, double costPrice, int qty, int reserved, String imgUrl, String sku, String sampleCode, String variationsJson, String brand, String description, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("name", name);
        cv.put("category_name", category);
        cv.put("price", price);
        cv.put("wholesale_price", wholesale);
        cv.put("cost_price", costPrice);
        cv.put("quantity_on_hand", qty);
        cv.put("quantity_reserved", reserved);
        cv.put("image_url", imgUrl);
        cv.put("sku", sku);
        cv.put("sample_code", sampleCode);
        cv.put("variations_json", variationsJson);
        cv.put("brand", brand);
        cv.put("description", description);
        cv.put("status", status);

        // Check if item exists to keep local image caching paths
        String existingLocalImagePath = null;
        String existingImageUrl = null;
        Cursor cursor = db.rawQuery("SELECT image_url, local_image_path FROM products WHERE id = ?", new String[]{String.valueOf(id)});
        boolean exists = cursor.moveToFirst();
        if (exists) {
            existingImageUrl = cursor.getString(0);
            existingLocalImagePath = cursor.getString(1);
        }
        cursor.close();

        if (exists) {
            // Smart Image Update Handling: check if the image URL from server changed
            if (existingImageUrl != null && !existingImageUrl.equals(imgUrl)) {
                if (existingLocalImagePath != null) {
                    try {
                        java.io.File file = new java.io.File(existingLocalImagePath);
                        if (file.exists()) {
                            file.delete();
                            android.util.Log.d("DatabaseHelper", "Deleted obsolete image file: " + existingLocalImagePath);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("DatabaseHelper", "Failed to delete obsolete image: " + e.getMessage());
                    }
                }
                cv.put("local_image_path", (String)null);
            }
            db.update("products", cv, "id = ?", new String[]{String.valueOf(id)});
        } else {
            db.insert("products", null, cv);
        }
    }

    public void saveProduct(int id, String name, String category, double price, double wholesale, double costPrice, int qty, int reserved, String imgUrl, String sku, String sampleCode, String variationsJson, String brand, String description) {
        saveProduct(id, name, category, price, wholesale, costPrice, qty, reserved, imgUrl, sku, sampleCode, variationsJson, brand, description, "active");
    }

    public void saveProduct(int id, String name, String category, double price, double wholesale, int qty, int reserved, String imgUrl, String sku, String sampleCode, String variationsJson, String brand, String description) {
        saveProduct(id, name, category, price, wholesale, 0.0, qty, reserved, imgUrl, sku, sampleCode, variationsJson, brand, description, "active");
    }

    public void saveProduct(int id, String name, String category, double price, double wholesale, int qty, int reserved, String imgUrl, String sku, String sampleCode, String variationsJson) {
        saveProduct(id, name, category, price, wholesale, 0.0, qty, reserved, imgUrl, sku, sampleCode, variationsJson, "", "");
    }

    public void saveProduct(int id, String name, String category, double price, double wholesale, int qty, int reserved, String imgUrl) {
        saveProduct(id, name, category, price, wholesale, 0.0, qty, reserved, imgUrl, "", "", "", "", "");
    }

    // Helper: Update downloaded local image path in cache
    public void updateProductLocalImagePath(int productId, String path) {
        int retries = 5;
        for (int i = 1; i <= retries; i++) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("local_image_path", path);
                db.update("products", cv, "id = ?", new String[]{String.valueOf(productId)});
                return;
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().contains("locked") || e.getMessage().contains("BUSY") || e.getMessage().contains("code 5"))) {
                    android.util.Log.w("DatabaseHelper", "Database is locked during updateProductLocalImagePath. Attempt " + i + " of " + retries + ". Retrying...");
                    try {
                        Thread.sleep(100 * i);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    android.util.Log.e("DatabaseHelper", "Error updating product local image path: " + e.getMessage());
                    break;
                }
            }
        }
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
        cv.put("uuid", java.util.UUID.randomUUID().toString());
        cv.put("sync_status", 1);
        return db.insert("customers", null, cv);
    }

    // Helper: Update customer details offline
    public int updateCustomerOffline(int id, String name, String phone, String whatsapp, String address, double lat, double lng) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("phone", phone);
        cv.put("whatsapp", whatsapp);
        cv.put("address", address);
        cv.put("latitude", lat);
        cv.put("longitude", lng);
        cv.put("is_synced", 0);
        cv.put("sync_status", 1);

        Cursor c = db.rawQuery("SELECT uuid FROM customers WHERE id = ?", new String[]{String.valueOf(id)});
        String uuid = null;
        if (c.moveToFirst()) {
            uuid = c.getString(0);
        }
        c.close();
        if (uuid == null || uuid.isEmpty()) {
            cv.put("uuid", java.util.UUID.randomUUID().toString());
        }

        return db.update("customers", cv, "id = ?", new String[]{String.valueOf(id)});
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
        cv.put("uuid", java.util.UUID.randomUUID().toString());
        cv.put("sync_status", 1);
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
        cv.put("sync_status", 1);

        Cursor c = db.rawQuery("SELECT uuid FROM daily_routes WHERE id = ?", new String[]{String.valueOf(localRouteId)});
        String uuid = null;
        if (c.moveToFirst()) {
            uuid = c.getString(0);
        }
        c.close();
        if (uuid == null || uuid.isEmpty()) {
            cv.put("uuid", java.util.UUID.randomUUID().toString());
        }

        db.update("daily_routes", cv, "id = ?", new String[]{String.valueOf(localRouteId)});
    }

    // Get currently active route in the mobile system
    public Cursor getActiveRoute() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM daily_routes WHERE status = 'Active' ORDER BY id DESC LIMIT 1", null);
    }

    public boolean hasActiveRoute() {
        Cursor cursor = getActiveRoute();
        boolean active = false;
        if (cursor != null) {
            active = cursor.getCount() > 0;
            cursor.close();
        }
        return active;
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

        Cursor cursor = db.rawQuery("SELECT name FROM server_routes WHERE status = 'active' ORDER BY name ASC", null);
        while (cursor.moveToNext()) {
            list.add(cursor.getString(0));
        }
        cursor.close();

        if (list.isEmpty()) {
            // Fallback to distinct customer territories if server_routes is empty
            cursor = db.rawQuery("SELECT DISTINCT territory FROM customers WHERE territory IS NOT NULL AND territory != '' AND status = 'active' ORDER BY territory ASC", null);
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
        StringBuilder queryBuilder = new StringBuilder();
        List<String> selectionArgs = new ArrayList<>();
        
        queryBuilder.append("SELECT * FROM customers WHERE status = 'active'");
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
        addCustomersExtraColumns(db);
        addProductsSearchColumns(db);
        
        // Self-healing status columns
        try {
            if (!hasColumn(db, "products", "status")) {
                db.execSQL("ALTER TABLE products ADD COLUMN status TEXT DEFAULT 'active'");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "categories", "status")) {
                db.execSQL("ALTER TABLE categories ADD COLUMN status TEXT DEFAULT 'active'");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "customers", "status")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN status TEXT DEFAULT 'active'");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "server_routes", "status")) {
                db.execSQL("ALTER TABLE server_routes ADD COLUMN status TEXT DEFAULT 'active'");
            }
        } catch (Exception e) {}
        
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
            if (!hasColumn(db, "invoices", "uuid")) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN uuid TEXT UNIQUE");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column uuid to invoices: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "invoices", "sync_status")) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN sync_status INTEGER DEFAULT 1");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column sync_status to invoices: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "invoices", "sync_attempts")) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN sync_attempts INTEGER DEFAULT 0");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column sync_attempts to invoices: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "invoices", "last_attempt_time")) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN last_attempt_time TEXT");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column last_attempt_time to invoices: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "invoices", "server_timestamp")) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN server_timestamp TEXT");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column server_timestamp to invoices: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "invoices", "failure_reason")) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN failure_reason TEXT");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column failure_reason to invoices: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "payments", "latitude")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN latitude REAL DEFAULT 0.0");
            }
            if (!hasColumn(db, "payments", "longitude")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN longitude REAL DEFAULT 0.0");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding location to payments: " + e.getMessage());
        }

        // Self-healing database alignment for customers table sync fields
        try {
            if (!hasColumn(db, "customers", "uuid")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN uuid TEXT UNIQUE");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "customers", "sync_status")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN sync_status INTEGER DEFAULT 1");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "customers", "sync_attempts")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN sync_attempts INTEGER DEFAULT 0");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "customers", "last_attempt_time")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN last_attempt_time TEXT");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "customers", "failure_reason")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN failure_reason TEXT");
            }
        } catch (Exception e) {}

        // Self-healing database alignment for daily_routes table sync fields
        try {
            if (!hasColumn(db, "daily_routes", "uuid")) {
                db.execSQL("ALTER TABLE daily_routes ADD COLUMN uuid TEXT UNIQUE");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "daily_routes", "sync_status")) {
                db.execSQL("ALTER TABLE daily_routes ADD COLUMN sync_status INTEGER DEFAULT 1");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "daily_routes", "sync_attempts")) {
                db.execSQL("ALTER TABLE daily_routes ADD COLUMN sync_attempts INTEGER DEFAULT 0");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "daily_routes", "last_attempt_time")) {
                db.execSQL("ALTER TABLE daily_routes ADD COLUMN last_attempt_time TEXT");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "daily_routes", "failure_reason")) {
                db.execSQL("ALTER TABLE daily_routes ADD COLUMN failure_reason TEXT");
            }
        } catch (Exception e) {}

        // Self-healing database alignment for payments table sync fields
        try {
            if (!hasColumn(db, "payments", "uuid")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN uuid TEXT UNIQUE");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "payments", "sync_status")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN sync_status INTEGER DEFAULT 1");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "payments", "sync_attempts")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN sync_attempts INTEGER DEFAULT 0");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "payments", "last_attempt_time")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN last_attempt_time TEXT");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "payments", "failure_reason")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN failure_reason TEXT");
            }
        } catch (Exception e) {}
        try {
            if (!hasColumn(db, "payments", "local_route_id")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN local_route_id INTEGER DEFAULT 0");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column local_route_id to payments: " + e.getMessage());
        }

        // Self-healing database alignment: align sync_status for successfully synced invoices, customers, routes, and payments
        try {
            db.execSQL("UPDATE invoices SET sync_status = 3 WHERE is_synced = 1 AND (sync_status IS NULL OR sync_status = 1)");
            db.execSQL("UPDATE customers SET sync_status = 3 WHERE is_synced = 1 AND (sync_status IS NULL OR sync_status = 1)");
            db.execSQL("UPDATE daily_routes SET sync_status = 3 WHERE is_synced = 1 AND (sync_status IS NULL OR sync_status = 1)");
            db.execSQL("UPDATE payments SET sync_status = 3 WHERE is_synced = 1 AND (sync_status IS NULL OR sync_status = 1)");
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error self-healing sync_status alignment: " + e.getMessage());
        }

        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS sync_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "bill_id INTEGER," +
                    "uuid TEXT," +
                    "created_time TEXT," +
                    "upload_started TEXT," +
                    "upload_completed TEXT," +
                    "erp_response TEXT," +
                    "failure_reason TEXT," +
                    "retry_count INTEGER DEFAULT 0" +
                    ")");
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error creating sync_logs table: " + e.getMessage());
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
                    "created_at TEXT DEFAULT CURRENT_TIMESTAMP," +
                    "uuid TEXT UNIQUE," +
                    "sync_status INTEGER DEFAULT 1," +
                    "sync_attempts INTEGER DEFAULT 0," +
                    "last_attempt_time TEXT," +
                    "failure_reason TEXT" +
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

        int localRouteId = 0;
        int resolvedServerRouteId = serverRouteId;
        Cursor cRoute = db.rawQuery("SELECT id, server_id FROM daily_routes WHERE status = 'Active' LIMIT 1", null);
        if (cRoute.moveToFirst()) {
            localRouteId = cRoute.getInt(0);
            if (resolvedServerRouteId <= 0) {
                resolvedServerRouteId = cRoute.getInt(1);
            }
        }
        cRoute.close();

        ContentValues cv = new ContentValues();
        cv.put("customer_id", customerId);
        cv.put("server_route_id", resolvedServerRouteId);
        cv.put("local_route_id", localRouteId);
        cv.put("payment_method", method);
        cv.put("amount", amount);
        cv.put("bank_name", bank);
        cv.put("cheque_number", chqNum);
        cv.put("cheque_date", chqDate);
        cv.put("latitude", latitude);
        cv.put("longitude", longitude);
        cv.put("is_synced", 0);
        cv.put("uuid", java.util.UUID.randomUUID().toString());
        cv.put("sync_status", 1);

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
        StringBuilder queryBuilder = new StringBuilder();
        
        queryBuilder.append("SELECT customer_id, customer_name, customer_address, SUM(true_grand_total) AS total_outstanding " +
                            "FROM credit_invoices " +
                            "WHERE customer_id NOT IN (SELECT customer_id FROM payments WHERE is_synced = 0)");

        queryBuilder.append(" GROUP BY customer_id, customer_name, customer_address ORDER BY customer_name ASC");
        
        String sql = queryBuilder.toString();
        android.util.Log.d("DatabaseHelper", "Constructed credit outstanding SQL (unfiltered): " + sql);

        Cursor result = db.rawQuery(sql, new String[0]);
        
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
        Cursor c3 = db.rawQuery("SELECT COUNT(*) FROM invoices WHERE is_synced = 0 OR sync_status IN (1, 4)", null);
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

    public String getPendingUploadsSummary() {
        SQLiteDatabase db = this.getReadableDatabase();
        int customers = 0;
        int routes = 0;
        int invoices = 0;
        int payments = 0;

        Cursor c1 = null;
        try {
            c1 = db.rawQuery("SELECT COUNT(*) FROM customers WHERE is_synced = 0", null);
            if (c1.moveToFirst()) customers = c1.getInt(0);
        } catch (Exception e) {} finally { if (c1 != null) c1.close(); }

        Cursor c2 = null;
        try {
            c2 = db.rawQuery("SELECT COUNT(*) FROM daily_routes WHERE is_synced = 0", null);
            if (c2.moveToFirst()) routes = c2.getInt(0);
        } catch (Exception e) {} finally { if (c2 != null) c2.close(); }

        Cursor c3 = null;
        try {
            c3 = db.rawQuery("SELECT COUNT(*) FROM invoices WHERE is_synced = 0", null);
            if (c3.moveToFirst()) invoices = c3.getInt(0);
        } catch (Exception e) {} finally { if (c3 != null) c3.close(); }

        Cursor c4 = null;
        try {
            c4 = db.rawQuery("SELECT COUNT(*) FROM payments WHERE is_synced = 0", null);
            if (c4.moveToFirst()) payments = c4.getInt(0);
        } catch (Exception e) {} finally { if (c4 != null) c4.close(); }

        if (customers == 0 && routes == 0 && invoices == 0 && payments == 0) {
            return "All local data synced with server";
        }
        
        StringBuilder sb = new StringBuilder("Pending: ");
        boolean first = true;
        if (invoices > 0) { sb.append("Invoices (").append(invoices).append(")"); first = false; }
        if (payments > 0) { if (!first) sb.append(", "); sb.append("Payments (").append(payments).append(")"); first = false; }
        if (customers > 0) { if (!first) sb.append(", "); sb.append("Customers (").append(customers).append(")"); first = false; }
        if (routes > 0) { if (!first) sb.append(", "); sb.append("Routes (").append(routes).append(")"); }
        return sb.toString();
    }
}
