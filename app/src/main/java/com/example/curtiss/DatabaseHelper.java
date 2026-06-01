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
    private static final int DATABASE_VERSION = 2;

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS products");
        db.execSQL("DROP TABLE IF EXISTS customers");
        db.execSQL("DROP TABLE IF EXISTS daily_routes");
        db.execSQL("DROP TABLE IF EXISTS invoices");
        db.execSQL("DROP TABLE IF EXISTS invoice_items");
        db.execSQL("DROP TABLE IF EXISTS server_routes");
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
        Cursor cursor = db.rawQuery("SELECT local_image_path FROM products WHERE id = " + id, null);
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

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // Self-healing database mechanism: dynamically add outstanding column to customers if missing
        try {
            db.execSQL("ALTER TABLE customers ADD COLUMN outstanding REAL DEFAULT 0.0");
        } catch (Exception e) {
            // Already exists, ignore safely
        }
        try {
            db.execSQL("ALTER TABLE server_routes ADD COLUMN main_area_id INTEGER DEFAULT 0");
        } catch (Exception e) {
            // Already exists, ignore safely
        }
        try {
            db.execSQL("ALTER TABLE invoices ADD COLUMN payment_term_id INTEGER");
        } catch (Exception e) {
            // Already exists, ignore safely
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
                ContentValues cv = new ContentValues();
                cv.put("id", 12);
                cv.put("username", "rep");
                // Password '123' hashed with BCrypt
                cv.put("password_hash", "$2a$10$tM78Fm9nCqS8/DkP7M3U2e4k9F10q7F6B6X.2U19xO6Xz1Y2S4Vmu");
                cv.put("employee_id", 1);
                cv.put("first_name", "Susara");
                cv.put("last_name", "Senarathne");
                db.insert("representatives", null, cv);
                android.util.Log.d("DatabaseHelper", "Successfully seeded default offline representative account: rep / 123");
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
    }
}
