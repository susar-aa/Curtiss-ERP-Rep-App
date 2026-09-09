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
    private static final int DATABASE_VERSION = 14;

    private static DatabaseHelper instance;
    private static boolean schemaHealed = false;
    private final Context mContext;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    // Safe Cursor extraction methods to prevent crashes on schema mismatches
    public static String safeGetString(Cursor cursor, String columnName, String defaultValue) {
        int index = cursor.getColumnIndex(columnName);
        if (index == -1) {
            android.util.Log.w("DatabaseHelper", "Column '" + columnName + "' not found in cursor");
            return defaultValue;
        }
        return cursor.getString(index);
    }

    public static int safeGetInt(Cursor cursor, String columnName, int defaultValue) {
        int index = cursor.getColumnIndex(columnName);
        if (index == -1) {
            android.util.Log.w("DatabaseHelper", "Column '" + columnName + "' not found in cursor");
            return defaultValue;
        }
        return cursor.getInt(index);
    }

    public static double safeGetDouble(Cursor cursor, String columnName, double defaultValue) {
        int index = cursor.getColumnIndex(columnName);
        if (index == -1) {
            android.util.Log.w("DatabaseHelper", "Column '" + columnName + "' not found in cursor");
            return defaultValue;
        }
        return cursor.getDouble(index);
    }

    public static long safeGetLong(Cursor cursor, String columnName, long defaultValue) {
        int index = cursor.getColumnIndex(columnName);
        if (index == -1) {
            android.util.Log.w("DatabaseHelper", "Column '" + columnName + "' not found in cursor");
            return defaultValue;
        }
        return cursor.getLong(index);
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.mContext = context;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.enableWriteAheadLogging();
    }

    public void addCustomersExtraColumns(SQLiteDatabase db) {
        try {
            if (!hasColumn(db, "customers", "email")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN email TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "credit_limit")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN credit_limit REAL DEFAULT 0.0");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "customer_type")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN customer_type TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "notes")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN notes TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "updated_at")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN updated_at TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "sync_source")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN sync_source TEXT");
            }
        } catch (Exception e) {
        }
    }

    public void addProductsSearchColumns(SQLiteDatabase db) {
        try {
            if (!hasColumn(db, "products", "sku")) {
                db.execSQL("ALTER TABLE products ADD COLUMN sku TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "products", "sample_code")) {
                db.execSQL("ALTER TABLE products ADD COLUMN sample_code TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "products", "variations_json")) {
                db.execSQL("ALTER TABLE products ADD COLUMN variations_json TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "products", "brand")) {
                db.execSQL("ALTER TABLE products ADD COLUMN brand TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "products", "description")) {
                db.execSQL("ALTER TABLE products ADD COLUMN description TEXT");
            }
        } catch (Exception e) {
        }
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
                "is_profile_synced INTEGER DEFAULT 1," +
                "uuid TEXT UNIQUE," +
                "sync_status INTEGER DEFAULT 1," +
                "sync_attempts INTEGER DEFAULT 0," +
                "last_attempt_time TEXT," +
                "failure_reason TEXT," +
                "updated_at TEXT DEFAULT CURRENT_TIMESTAMP," +
                "sync_source TEXT" +
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
                "discount_type TEXT DEFAULT 'Rs'," +
                "discount_rate REAL DEFAULT 0.0," +
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
                "discount_type TEXT DEFAULT 'Rs'," +
                "discount_rate REAL DEFAULT 0.0," +
                "total REAL," +
                "selected_variation TEXT," +
                "variation_option_id INTEGER DEFAULT 0" +
                ")");

        // 6. Server Routes (Territories) Table
        db.execSQL("CREATE TABLE server_routes (" +
                "id INTEGER PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "main_area_id INTEGER DEFAULT 0," +
                "main_area_name TEXT," +
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
                "server_id INTEGER DEFAULT 0," +
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
                "reward_type TEXT DEFAULT 'free_issue'," +
                "target_item_id INTEGER," +
                "target_category_id INTEGER," +
                "start_date TEXT," +
                "end_date TEXT," +
                "discount_cap REAL," +
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
                "product_id INTEGER," +
                "image_url TEXT PRIMARY KEY," +
                "status TEXT DEFAULT 'pending'," +
                "attempts INTEGER DEFAULT 0," +
                "last_error TEXT" +
                ")");

        // 14. Sync Logs Table
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

        // 15. Conflict Backups Table
        db.execSQL("CREATE TABLE IF NOT EXISTS conflict_backups (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "table_name TEXT NOT NULL," +
                "record_id INTEGER," +
                "uuid TEXT," +
                "local_data TEXT," +
                "server_data TEXT," +
                "resolved INTEGER DEFAULT 0," +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                ")");

        // 16. Unproductive Visits Table
        db.execSQL("CREATE TABLE IF NOT EXISTS unproductive_visits (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "server_id INTEGER DEFAULT 0," +
                "uuid TEXT UNIQUE," +
                "route_id INTEGER NOT NULL," +
                "customer_id INTEGER NOT NULL," +
                "reason TEXT NOT NULL," +
                "custom_reason TEXT," +
                "latitude REAL," +
                "longitude REAL," +
                "visit_time TEXT NOT NULL," +
                "sync_status INTEGER DEFAULT 0," +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        android.util.Log.i("DatabaseHelper", "Upgrading database from version " + oldVersion + " to " + newVersion);
        for (int version = oldVersion + 1; version <= newVersion; version++) {
            upgradeToVersion(db, version);
        }
    }

    private void upgradeToVersion(SQLiteDatabase db, int version) {
        android.util.Log.i("DatabaseHelper", "Applying database migration step to version " + version);
        // Explicit stepwise migration steps can be added here.
        // Currently, all schema adjustments are dynamically healed in onOpen() to
        // ensure
        // all tables are aligned regardless of migration path.
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        android.util.Log.i("DatabaseHelper", "Downgrading database from version " + oldVersion + " to " + newVersion);
        // Non-destructive downgrade path
    }

    // Helper: Bulk Insert or Update Products downloaded from server
    public void saveProduct(int id, String name, String category, double price, double wholesale, double costPrice,
            int qty, int reserved, String imgUrl, String sku, String sampleCode, String variationsJson, String brand,
            String description, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("name", name);
        cv.put("category_name", category);
        cv.put("price", wholesale);
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
        Cursor cursor = db.rawQuery("SELECT image_url, local_image_path FROM products WHERE id = ?",
                new String[] { String.valueOf(id) });
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
                            android.util.Log.d("DatabaseHelper",
                                    "Deleted obsolete image file: " + existingLocalImagePath);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("DatabaseHelper", "Failed to delete obsolete image: " + e.getMessage());
                    }
                }
                cv.put("local_image_path", (String) null);
            }
            db.update("products", cv, "id = ?", new String[] { String.valueOf(id) });
        } else {
            db.insert("products", null, cv);
        }
    }

    public void saveProduct(int id, String name, String category, double price, double wholesale, double costPrice,
            int qty, int reserved, String imgUrl, String sku, String sampleCode, String variationsJson, String brand,
            String description) {
        saveProduct(id, name, category, price, wholesale, costPrice, qty, reserved, imgUrl, sku, sampleCode,
                variationsJson, brand, description, "active");
    }

    public void saveProduct(int id, String name, String category, double price, double wholesale, int qty, int reserved,
            String imgUrl, String sku, String sampleCode, String variationsJson, String brand, String description) {
        saveProduct(id, name, category, price, wholesale, 0.0, qty, reserved, imgUrl, sku, sampleCode, variationsJson,
                brand, description, "active");
    }

    public void saveProduct(int id, String name, String category, double price, double wholesale, int qty, int reserved,
            String imgUrl, String sku, String sampleCode, String variationsJson) {
        saveProduct(id, name, category, price, wholesale, 0.0, qty, reserved, imgUrl, sku, sampleCode, variationsJson,
                "", "");
    }

    public void saveProduct(int id, String name, String category, double price, double wholesale, int qty, int reserved,
            String imgUrl) {
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
                db.update("products", cv, "id = ?", new String[] { String.valueOf(productId) });
                return;
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().contains("locked") || e.getMessage().contains("BUSY")
                        || e.getMessage().contains("code 5"))) {
                    android.util.Log.w("DatabaseHelper",
                            "Database is locked during updateProductLocalImagePath. Attempt " + i + " of " + retries
                                    + ". Retrying...");
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

    public void updateProductVariationsJson(int productId, String varsJson) {
        int retries = 5;
        for (int i = 1; i <= retries; i++) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("variations_json", varsJson);
                db.update("products", cv, "id = ?", new String[] { String.valueOf(productId) });
                return;
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().contains("locked") || e.getMessage().contains("BUSY")
                        || e.getMessage().contains("code 5"))) {
                    android.util.Log.w("DatabaseHelper",
                            "Database is locked during updateProductVariationsJson. Attempt " + i + " of " + retries
                                    + ". Retrying...");
                    try {
                        Thread.sleep(100 * i);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    android.util.Log.e("DatabaseHelper", "Error updating product variations json: " + e.getMessage());
                    break;
                }
            }
        }
    }

    // Helper: Add custom added customer offline
    public long insertCustomerOffline(String name, String phone, String whatsapp, String address, String mcaName,
            double lat, double lng) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("phone", phone);
        cv.put("whatsapp", whatsapp);
        cv.put("address", address);

        String territoryName = getMainAreaNameByRoute(mcaName);
        if (territoryName == null || territoryName.trim().isEmpty()) {
            territoryName = mcaName;
        }
        cv.put("mca_name", mcaName);
        cv.put("territory", territoryName);

        cv.put("latitude", lat);
        cv.put("longitude", lng);
        cv.put("is_synced", 0);
        cv.put("is_profile_synced", 0);
        cv.put("uuid", java.util.UUID.randomUUID().toString());
        cv.put("sync_status", 1);
        cv.put("updated_at", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date()));
        return db.insert("customers", null, cv);
    }

    // Helper: Update customer details offline
    public int updateCustomerOffline(int id, String name, String phone, String whatsapp, String address, double lat,
            double lng) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("phone", phone);
        cv.put("whatsapp", whatsapp);
        cv.put("address", address);
        cv.put("latitude", lat);
        cv.put("longitude", lng);
        cv.put("is_synced", 0);
        cv.put("is_profile_synced", 0);
        cv.put("sync_status", 1);
        cv.put("updated_at", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date()));

        Cursor c = db.rawQuery("SELECT uuid FROM customers WHERE id = ?", new String[] { String.valueOf(id) });
        String uuid = null;
        if (c.moveToFirst()) {
            uuid = c.getString(0);
        }
        c.close();
        if (uuid == null || uuid.isEmpty()) {
            cv.put("uuid", java.util.UUID.randomUUID().toString());
        }

        return db.update("customers", cv, "id = ?", new String[] { String.valueOf(id) });
    }

    // Helper: Update stock locally from FCM push
    public boolean updateStockLocally(int productId, double newStockQty, double reservedQty) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("quantity_on_hand", newStockQty);
        cv.put("quantity_reserved", reservedQty);

        int rows = db.update("products", cv, "id = ?", new String[] { String.valueOf(productId) });
        return rows > 0;
    }

    // Helper: Insert/Start a Daily Route offline
    public long startRouteOffline(String routeName, double startMeter, String startTime, double lat, double lng) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Clear previous day's synced data before starting the new route
        db.beginTransaction();
        try {
            // Delete completed and synced daily routes (only if there are no unsynced
            // invoices or payments referencing them)
            db.execSQL("DELETE FROM daily_routes WHERE is_synced = 1 AND status = 'Completed' " +
                    "AND id NOT IN (SELECT DISTINCT route_id FROM invoices WHERE is_synced = 0) " +
                    "AND id NOT IN (SELECT DISTINCT local_route_id FROM payments WHERE is_synced = 0)");

            // Delete synced invoices
            db.execSQL("DELETE FROM invoices WHERE is_synced = 1");

            // Delete orphaned invoice items
            db.execSQL("DELETE FROM invoice_items WHERE invoice_id NOT IN (SELECT id FROM invoices)");

            // Delete synced payments
            try {
                db.execSQL("DELETE FROM payments WHERE is_synced = 1");
            } catch (Exception e) {
            }

            db.setTransactionSuccessful();
            android.util.Log.d("DatabaseHelper", "Cleared previous day's synced bills, routes, and payments.");
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error clearing previous day's data: " + e.getMessage());
        } finally {
            db.endTransaction();
        }

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

        Cursor c = db.rawQuery("SELECT uuid FROM daily_routes WHERE id = ?",
                new String[] { String.valueOf(localRouteId) });
        String uuid = null;
        if (c.moveToFirst()) {
            uuid = c.getString(0);
        }
        c.close();
        if (uuid == null || uuid.isEmpty()) {
            cv.put("uuid", java.util.UUID.randomUUID().toString());
        }

        db.update("daily_routes", cv, "id = ?", new String[] { String.valueOf(localRouteId) });
    }

    // Get currently active route in the mobile system
    public Cursor getActiveRoute() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM daily_routes WHERE status = ? ORDER BY id DESC LIMIT 1",
                new String[] { "Active" });
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
        Cursor cursor = db.rawQuery("SELECT COALESCE(SUM(grand_total), 0.0) FROM invoices WHERE route_id = ?",
                new String[] { String.valueOf(localRouteId) });
        double total = 0.0;
        if (cursor.moveToFirst()) {
            total = cursor.getDouble(0);
        }
        cursor.close();
        return total;
    }

    public int getRouteInvoicesCount(long localRouteId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COALESCE(COUNT(*), 0) FROM invoices WHERE route_id = ?",
                new String[] { String.valueOf(localRouteId) });
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
        // Self-healing guard: dynamically create table if missing to prevent
        // SQLiteException crashes
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS server_routes (id INTEGER PRIMARY KEY, name TEXT NOT NULL, main_area_id INTEGER DEFAULT 0, main_area_name TEXT, status TEXT DEFAULT 'active')");

        Cursor cursor = db.rawQuery("SELECT name FROM server_routes WHERE status = 'active' ORDER BY name ASC", null);
        while (cursor.moveToNext()) {
            list.add(cursor.getString(0));
        }
        cursor.close();

        if (list.isEmpty()) {
            // Fallback to distinct customer territories if server_routes is empty
            cursor = db.rawQuery(
                    "SELECT DISTINCT territory FROM customers WHERE territory IS NOT NULL AND territory != '' AND status = 'active' ORDER BY territory ASC",
                    null);
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0));
            }
            cursor.close();
        }
        return list;
    }

    public String getMainAreaNameByRoute(String routeName) {
        if (routeName == null || routeName.trim().isEmpty())
            return "";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        String mainArea = "";
        try {
            cursor = db.rawQuery("SELECT main_area_name FROM server_routes WHERE name = ? LIMIT 1",
                    new String[] { routeName });
            if (cursor != null && cursor.moveToFirst()) {
                mainArea = cursor.getString(0);
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error in getMainAreaNameByRoute: " + e.getMessage());
            try {
                SQLiteDatabase writeDb = this.getWritableDatabase();
                if (!hasColumn(writeDb, "server_routes", "main_area_name")) {
                    writeDb.execSQL("ALTER TABLE server_routes ADD COLUMN main_area_name TEXT");
                }
            } catch (Exception ex) {
                android.util.Log.e("DatabaseHelper",
                        "Failed to heal main_area_name column inside catch: " + ex.getMessage());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (mainArea == null || mainArea.trim().isEmpty()) {
            mainArea = routeName;
        }
        return mainArea;
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
            queryBuilder.append(" AND (name LIKE ? OR territory LIKE ? OR phone LIKE ? OR address LIKE ?)");
            selectionArgs.add("%" + filter + "%");
            selectionArgs.add("%" + filter + "%");
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
            android.util.Log.e("DatabaseHelper",
                    "Error checking column " + columnName + " in " + tableName + ": " + e.getMessage());
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

        // Self-healing check for correct sync_logs columns
        try {
            if (!hasColumn(db, "sync_logs", "bill_id")) {
                db.execSQL("DROP TABLE IF EXISTS sync_logs");
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
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error self-healing sync_logs table: " + e.getMessage());
        }

        // Self-healing check for correct image_download_queue primary key
        try {
            boolean needsRecreate = false;
            Cursor ti = db.rawQuery("PRAGMA table_info(image_download_queue)", null);
            if (ti != null) {
                while (ti.moveToNext()) {
                    String name = ti.getString(ti.getColumnIndexOrThrow("name"));
                    int pk = ti.getInt(ti.getColumnIndexOrThrow("pk"));
                    if ("product_id".equals(name) && pk == 1) {
                        needsRecreate = true;
                        break;
                    }
                }
                ti.close();
            }
            if (needsRecreate) {
                db.execSQL("DROP TABLE IF EXISTS image_download_queue");
                db.execSQL("CREATE TABLE IF NOT EXISTS image_download_queue (" +
                        "product_id INTEGER," +
                        "image_url TEXT PRIMARY KEY," +
                        "status TEXT DEFAULT 'pending'," +
                        "attempts INTEGER DEFAULT 0," +
                        "last_error TEXT" +
                        ")");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error self-healing image_download_queue table: " + e.getMessage());
        }

        boolean isHealed = false;

        android.content.SharedPreferences prefs = null;
        if (mContext != null) {
            prefs = mContext.getSharedPreferences("curtiss_db_prefs", Context.MODE_PRIVATE);
            isHealed = (prefs.getInt("healed_version", 0) == DATABASE_VERSION);
        }

        if (isHealed || schemaHealed) {
            return;
        }
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS conflict_backups (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "table_name TEXT NOT NULL," +
                    "record_id INTEGER," +
                    "uuid TEXT," +
                    "local_data TEXT," +
                    "server_data TEXT," +
                    "resolved INTEGER DEFAULT 0," +
                    "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")");
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error creating conflict_backups table: " + e.getMessage());
        }
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS unproductive_visits (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "server_id INTEGER DEFAULT 0," +
                    "uuid TEXT UNIQUE," +
                    "route_id INTEGER NOT NULL," +
                    "customer_id INTEGER NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "custom_reason TEXT," +
                    "latitude REAL," +
                    "longitude REAL," +
                    "visit_time TEXT NOT NULL," +
                    "sync_status INTEGER DEFAULT 0," +
                    "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ")");
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error creating unproductive_visits table: " + e.getMessage());
        }
        addCustomersExtraColumns(db);
        addProductsSearchColumns(db);

        // Self-healing status columns
        try {
            if (!hasColumn(db, "products", "status")) {
                db.execSQL("ALTER TABLE products ADD COLUMN status TEXT DEFAULT 'active'");
            }
        } catch (Exception e) {
        }
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS categories (" +
                    "id INTEGER PRIMARY KEY," +
                    "name TEXT NOT NULL UNIQUE," +
                    "status TEXT DEFAULT 'active'" +
                    ")");
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "invoices", "status")) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN status TEXT DEFAULT 'Completed'");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "categories", "status")) {
                db.execSQL("ALTER TABLE categories ADD COLUMN status TEXT DEFAULT 'active'");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "status")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN status TEXT DEFAULT 'active'");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "server_routes", "status")) {
                db.execSQL("ALTER TABLE server_routes ADD COLUMN status TEXT DEFAULT 'active'");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "discount_rules", "reward_type")) {
                db.execSQL("ALTER TABLE discount_rules ADD COLUMN reward_type TEXT DEFAULT 'free_issue'");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "discount_rules", "target_category_id")) {
                db.execSQL("ALTER TABLE discount_rules ADD COLUMN target_category_id INTEGER");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "discount_rules", "start_date")) {
                db.execSQL("ALTER TABLE discount_rules ADD COLUMN start_date TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "discount_rules", "end_date")) {
                db.execSQL("ALTER TABLE discount_rules ADD COLUMN end_date TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "discount_rules", "discount_cap")) {
                db.execSQL("ALTER TABLE discount_rules ADD COLUMN discount_cap REAL");
            }
        } catch (Exception e) {
        }

        // Self-healing database mechanism: dynamically add column if missing without
        // throwing warnings/errors
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
            android.util.Log.e("DatabaseHelper",
                    "Error adding column main_area_id to server_routes: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "server_routes", "main_area_name")) {
                db.execSQL("ALTER TABLE server_routes ADD COLUMN main_area_name TEXT");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper",
                    "Error adding column main_area_name to server_routes: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "invoices", "payment_term_id")) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN payment_term_id INTEGER");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column payment_term_id to invoices: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "invoices", "discount_type")) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN discount_type TEXT DEFAULT 'Rs'");
            }
            if (!hasColumn(db, "invoices", "discount_rate")) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN discount_rate REAL DEFAULT 0.0");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding discount columns to invoices: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "invoice_items", "discount_type")) {
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN discount_type TEXT DEFAULT 'Rs'");
            }
            if (!hasColumn(db, "invoice_items", "discount_rate")) {
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN discount_rate REAL DEFAULT 0.0");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding discount columns to invoice_items: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "invoice_items", "selected_variation")) {
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN selected_variation TEXT");
            }
            if (!hasColumn(db, "invoice_items", "variation_option_id")) {
                db.execSQL("ALTER TABLE invoice_items ADD COLUMN variation_option_id INTEGER DEFAULT 0");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding variation columns to invoice_items: " + e.getMessage());
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
            android.util.Log.e("DatabaseHelper",
                    "Error adding column last_attempt_time to invoices: " + e.getMessage());
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
            if (!hasColumn(db, "customers", "is_profile_synced")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN is_profile_synced INTEGER DEFAULT 1");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "uuid")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN uuid TEXT UNIQUE");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "sync_status")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN sync_status INTEGER DEFAULT 1");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "sync_attempts")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN sync_attempts INTEGER DEFAULT 0");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "last_attempt_time")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN last_attempt_time TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "failure_reason")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN failure_reason TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "updated_at")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN updated_at TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "customers", "sync_source")) {
                db.execSQL("ALTER TABLE customers ADD COLUMN sync_source TEXT");
            }
        } catch (Exception e) {
        }

        // Self-healing database alignment for daily_routes table sync fields
        try {
            if (!hasColumn(db, "daily_routes", "uuid")) {
                db.execSQL("ALTER TABLE daily_routes ADD COLUMN uuid TEXT UNIQUE");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "daily_routes", "sync_status")) {
                db.execSQL("ALTER TABLE daily_routes ADD COLUMN sync_status INTEGER DEFAULT 1");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "daily_routes", "sync_attempts")) {
                db.execSQL("ALTER TABLE daily_routes ADD COLUMN sync_attempts INTEGER DEFAULT 0");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "daily_routes", "last_attempt_time")) {
                db.execSQL("ALTER TABLE daily_routes ADD COLUMN last_attempt_time TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "daily_routes", "failure_reason")) {
                db.execSQL("ALTER TABLE daily_routes ADD COLUMN failure_reason TEXT");
            }
        } catch (Exception e) {
        }

        // Self-healing database alignment for payments table sync fields
        try {
            if (!hasColumn(db, "payments", "uuid")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN uuid TEXT UNIQUE");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "payments", "sync_status")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN sync_status INTEGER DEFAULT 1");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "payments", "sync_attempts")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN sync_attempts INTEGER DEFAULT 0");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "payments", "last_attempt_time")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN last_attempt_time TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "payments", "failure_reason")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN failure_reason TEXT");
            }
        } catch (Exception e) {
        }
        try {
            if (!hasColumn(db, "payments", "local_route_id")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN local_route_id INTEGER DEFAULT 0");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column local_route_id to payments: " + e.getMessage());
        }
        try {
            if (!hasColumn(db, "payments", "server_id")) {
                db.execSQL("ALTER TABLE payments ADD COLUMN server_id INTEGER DEFAULT 0");
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error adding column server_id to payments: " + e.getMessage());
        }

        // Self-healing database alignment: align sync_status for successfully synced
        // invoices, customers, routes, and payments
        try {
            db.execSQL(
                    "UPDATE invoices SET sync_status = 3 WHERE is_synced = 1 AND (sync_status IS NULL OR sync_status = 1)");
            db.execSQL(
                    "UPDATE customers SET sync_status = 3 WHERE is_synced = 1 AND (sync_status IS NULL OR sync_status = 1)");
            db.execSQL(
                    "UPDATE daily_routes SET sync_status = 3 WHERE is_synced = 1 AND (sync_status IS NULL OR sync_status = 1)");
            db.execSQL(
                    "UPDATE payments SET sync_status = 3 WHERE is_synced = 1 AND (sync_status IS NULL OR sync_status = 1)");
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error self-healing sync_status alignment: " + e.getMessage());
        }

        // Self-healing: generate UUIDs for any existing
        // invoices/customers/routes/payments that lack one
        try {
            // 1. Invoices
            Cursor cInv = db.rawQuery("SELECT id FROM invoices WHERE uuid IS NULL OR uuid = ''", null);
            if (cInv != null) {
                while (cInv.moveToNext()) {
                    long id = cInv.getLong(0);
                    db.execSQL("UPDATE invoices SET uuid = ? WHERE id = ?",
                            new Object[] { java.util.UUID.randomUUID().toString(), id });
                }
                cInv.close();
            }
            // 2. Customers
            Cursor cCust = db.rawQuery("SELECT id FROM customers WHERE uuid IS NULL OR uuid = ''", null);
            if (cCust != null) {
                while (cCust.moveToNext()) {
                    long id = cCust.getLong(0);
                    db.execSQL("UPDATE customers SET uuid = ? WHERE id = ?",
                            new Object[] { java.util.UUID.randomUUID().toString(), id });
                }
                cCust.close();
            }
            // 3. Daily Routes
            Cursor cRoute = db.rawQuery("SELECT id FROM daily_routes WHERE uuid IS NULL OR uuid = ''", null);
            if (cRoute != null) {
                while (cRoute.moveToNext()) {
                    long id = cRoute.getLong(0);
                    db.execSQL("UPDATE daily_routes SET uuid = ? WHERE id = ?",
                            new Object[] { java.util.UUID.randomUUID().toString(), id });
                }
                cRoute.close();
            }
            // 4. Payments
            Cursor cPay = db.rawQuery("SELECT id FROM payments WHERE uuid IS NULL OR uuid = ''", null);
            if (cPay != null) {
                while (cPay.moveToNext()) {
                    long id = cPay.getLong(0);
                    db.execSQL("UPDATE payments SET uuid = ? WHERE id = ?",
                            new Object[] { java.util.UUID.randomUUID().toString(), id });
                }
                cPay.close();
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error self-healing missing UUIDs: " + e.getMessage());
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
            db.execSQL(
                    "UPDATE products SET category_name = 'General' WHERE category_name IS NULL OR category_name = 'null' OR category_name = ''");
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
                    "server_id INTEGER DEFAULT 0," +
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

        if (prefs != null && prefs.getInt("healed_version", 0) < 12) {
            try {
                db.execSQL(
                        "UPDATE invoices SET is_synced = 0, sync_status = 1 WHERE invoice_number IN ('202607170007', '202607170008', '202607170009', '202607170010')");
                db.execSQL(
                        "UPDATE sync_logs SET upload_completed = '', erp_response = '', failure_reason = '' WHERE bill_id IN (SELECT id FROM invoices WHERE invoice_number IN ('202607170007', '202607170008', '202607170009', '202607170010'))");
                android.util.Log.d("DatabaseHelper",
                        "Successfully reset sync status of collided invoices 202607170007-0010 for auto-recovery.");
            } catch (Exception e) {
                android.util.Log.e("DatabaseHelper", "Error resetting collided invoices: " + e.getMessage());
            }
        }

        schemaHealed = true;
        if (prefs != null) {
            prefs.edit().putInt("healed_version", DATABASE_VERSION).apply();
        }
    }

    // --- PAYMENTS & CREDIT INVOICES HELPERS ---
    public boolean savePayment(int customerId, int serverRouteId, String method, double amount, String bank,
            String chqNum, String chqDate, double latitude, double longitude) {
        SQLiteDatabase db = this.getWritableDatabase();

        int localRouteId = 0;
        int resolvedServerRouteId = serverRouteId;
        Cursor cRoute = db.rawQuery("SELECT id, server_id FROM daily_routes WHERE status = ? LIMIT 1",
                new String[] { "Active" });
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
        return db.rawQuery(
                "SELECT * FROM credit_invoices WHERE customer_id NOT IN (SELECT customer_id FROM payments WHERE is_synced = 0) ORDER BY invoice_date ASC",
                null);
    }

    public Cursor getOutstandingCustomersByActiveRouteMainTerritory() {
        SQLiteDatabase db = this.getReadableDatabase();
        StringBuilder queryBuilder = new StringBuilder();

        queryBuilder.append(
                "SELECT customer_id, customer_name, customer_address, COALESCE(SUM(true_grand_total), 0.0) AS total_outstanding "
                        +
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
                android.util.Log.d("DatabaseHelper",
                        "Row " + count + " -> Customer ID: " + id + ", Name: " + name + ", Balance: LKR " + bal);
            }
            result.moveToPosition(-1); // Reset cursor position for caller
        }

        return result;
    }

    // Check if there are any pending uploads in the offline database
    public boolean hasPendingUploads() {
        SQLiteDatabase db = this.getReadableDatabase();

        // 1. Check unsynced customers
        Cursor c1 = db.rawQuery(
                "SELECT COUNT(*) FROM customers WHERE (is_synced = 0 OR sync_status IN (1, 4)) AND (server_id = 0 OR is_profile_synced = 0)",
                null);
        if (c1.moveToFirst() && c1.getInt(0) > 0) {
            c1.close();
            return true;
        }
        c1.close();

        // 2. Check unsynced daily routes
        Cursor c2 = db.rawQuery("SELECT COUNT(*) FROM daily_routes WHERE is_synced = 0 OR sync_status IN (1, 4)", null);
        if (c2.moveToFirst() && c2.getInt(0) > 0) {
            c2.close();
            return true;
        }
        c2.close();

        // 3. Check unsynced invoices
        Cursor c3 = db.rawQuery("SELECT COUNT(*) FROM invoices WHERE is_synced = 0 OR sync_status IN (1, 4)", null);
        if (c3.moveToFirst() && c3.getInt(0) > 0) {
            c3.close();
            return true;
        }
        c3.close();

        // 4. Check unsynced payments
        try {
            Cursor c4 = db.rawQuery("SELECT COUNT(*) FROM payments WHERE is_synced = 0 OR sync_status IN (1, 4)", null);
            if (c4.moveToFirst() && c4.getInt(0) > 0) {
                c4.close();
                return true;
            }
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
            } catch (Exception e) {
            }

            if (force) {
                db.execSQL("DELETE FROM customers");
                db.execSQL("DELETE FROM daily_routes");
                db.execSQL("DELETE FROM invoices");
                db.execSQL("DELETE FROM invoice_items");
                try {
                    db.execSQL("DELETE FROM payments");
                } catch (Exception e) {
                }
            } else {
                // Delete ONLY synced data, preserving offline pending items and the active
                // ongoing route progress
                db.execSQL("DELETE FROM customers WHERE is_synced = 1");

                // Daily routes, invoices, and payments are preserved so reps can view their
                // ended day's bills.
                // These are cleared on starting a new day/route instead.
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
            c1 = db.rawQuery(
                    "SELECT COUNT(*) FROM customers WHERE (is_synced = 0 OR sync_status IN (1, 4)) AND (server_id = 0 OR is_profile_synced = 0)",
                    null);
            if (c1.moveToFirst())
                customers = c1.getInt(0);
        } catch (Exception e) {
        } finally {
            if (c1 != null)
                c1.close();
        }

        Cursor c2 = null;
        try {
            c2 = db.rawQuery("SELECT COUNT(*) FROM daily_routes WHERE is_synced = 0 OR sync_status IN (1, 4)", null);
            if (c2.moveToFirst())
                routes = c2.getInt(0);
        } catch (Exception e) {
        } finally {
            if (c2 != null)
                c2.close();
        }

        Cursor c3 = null;
        try {
            c3 = db.rawQuery("SELECT COUNT(*) FROM invoices WHERE is_synced = 0 OR sync_status IN (1, 4)", null);
            if (c3.moveToFirst())
                invoices = c3.getInt(0);
        } catch (Exception e) {
        } finally {
            if (c3 != null)
                c3.close();
        }

        Cursor c4 = null;
        try {
            c4 = db.rawQuery("SELECT COUNT(*) FROM payments WHERE is_synced = 0 OR sync_status IN (1, 4)", null);
            if (c4.moveToFirst())
                payments = c4.getInt(0);
        } catch (Exception e) {
        } finally {
            if (c4 != null)
                c4.close();
        }

        int unproductive = 0;
        Cursor c5 = null;
        try {
            c5 = db.rawQuery("SELECT COUNT(*) FROM unproductive_visits WHERE sync_status = 0", null);
            if (c5.moveToFirst())
                unproductive = c5.getInt(0);
        } catch (Exception e) {
        } finally {
            if (c5 != null)
                c5.close();
        }

        if (customers == 0 && routes == 0 && invoices == 0 && payments == 0 && unproductive == 0) {
            return "All local data synced with server";
        }

        StringBuilder sb = new StringBuilder("Pending: ");
        boolean first = true;
        if (invoices > 0) {
            sb.append("Invoices (").append(invoices).append(")");
            first = false;
        }
        if (payments > 0) {
            if (!first)
                sb.append(", ");
            sb.append("Payments (").append(payments).append(")");
            first = false;
        }
        if (unproductive > 0) {
            if (!first)
                sb.append(", ");
            sb.append("Unproductive Visits (").append(unproductive).append(")");
            first = false;
        }
        if (customers > 0) {
            if (!first)
                sb.append(", ");
            sb.append("Customers (").append(customers).append(")");
            first = false;
        }
        if (routes > 0) {
            if (!first)
                sb.append(", ");
            sb.append("Routes (").append(routes).append(")");
        }
        return sb.toString();
    }

    public int getVariationReservedQty(int productId, String selectedVariation) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        int reserved = 0;
        try {
            boolean hasStatus = hasColumn(db, "invoices", "status");
            String query = "SELECT SUM(ii.quantity) FROM invoice_items ii JOIN invoices i ON ii.invoice_id = i.id WHERE ii.product_id = ? AND ii.selected_variation = ? AND i.is_synced = 0";
            if (hasStatus) {
                query += " AND (i.status IS NULL OR i.status != 'Voided')";
            }
            cursor = db.rawQuery(query, new String[] { String.valueOf(productId), selectedVariation });
            if (cursor != null && cursor.moveToFirst()) {
                reserved = cursor.getInt(0);
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error getting variation reserved qty: " + e.getMessage());
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return reserved;
    }

    // Helper: Insert an Unproductive Visit offline
    public long insertUnproductiveVisit(long routeId, long customerId, String reason, String customReason, double lat,
            double lng) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("uuid", java.util.UUID.randomUUID().toString());
        cv.put("route_id", routeId);
        cv.put("customer_id", customerId);
        cv.put("reason", reason);
        cv.put("custom_reason", customReason != null ? customReason.trim() : "");
        cv.put("latitude", lat);
        cv.put("longitude", lng);
        String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        cv.put("visit_time", currentTime);
        cv.put("sync_status", 0); // 0 = pending sync
        return db.insert("unproductive_visits", null, cv);
    }

    // Validation 1: Check if customer already has a bill on current route
    public boolean hasBilledCustomerOnRoute(long routeId, long customerId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = null;
        boolean hasBill = false;
        try {
            c = db.rawQuery(
                    "SELECT COUNT(*) FROM invoices WHERE customer_id = ? AND (route_id = ? OR route_id IS NULL OR route_id = 0)",
                    new String[] { String.valueOf(customerId), String.valueOf(routeId) });
            if (c != null && c.moveToFirst()) {
                hasBill = c.getInt(0) > 0;
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error checking billed customer on route: " + e.getMessage());
        } finally {
            if (c != null)
                c.close();
        }
        return hasBill;
    }

    // Validation 2: Check if customer already has an unproductive visit on current
    // route
    public boolean hasUnproductiveVisitOnRoute(long routeId, long customerId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = null;
        boolean exists = false;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM unproductive_visits WHERE customer_id = ? AND route_id = ?",
                    new String[] { String.valueOf(customerId), String.valueOf(routeId) });
            if (c != null && c.moveToFirst()) {
                exists = c.getInt(0) > 0;
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error checking unproductive visit on route: " + e.getMessage());
        } finally {
            if (c != null)
                c.close();
        }
        return exists;
    }

    // Get unproductive visits count for route statistics
    public int getRouteUnproductiveVisitsCount(long routeId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = null;
        int count = 0;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM unproductive_visits WHERE route_id = ?",
                    new String[] { String.valueOf(routeId) });
            if (c != null && c.moveToFirst()) {
                count = c.getInt(0);
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error getting route unproductive visits count: " + e.getMessage());
        } finally {
            if (c != null)
                c.close();
        }
        return count;
    }

    // Fetch pending unproductive visits for Sync Push
    public Cursor getPendingUnproductiveVisits() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM unproductive_visits WHERE sync_status = 0", null);
    }

    // Mark unproductive visits synced
    public void markUnproductiveVisitsSynced(List<String> uuids) {
        if (uuids == null || uuids.isEmpty())
            return;
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            for (String uuid : uuids) {
                ContentValues cv = new ContentValues();
                cv.put("sync_status", 1);
                db.update("unproductive_visits", cv, "uuid = ?", new String[] { uuid });
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            android.util.Log.e("DatabaseHelper", "Error marking unproductive visits synced: " + e.getMessage());
        } finally {
            db.endTransaction();
        }
    }
}
