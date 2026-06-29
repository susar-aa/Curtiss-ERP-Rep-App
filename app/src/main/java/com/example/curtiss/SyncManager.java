package com.example.curtiss;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncManager {

    // Enhanced sync status constants
    public static final int SYNC_PENDING = 1;     // Never synced
    public static final int SYNC_SYNCING = 2;     // Currently being sent
    public static final int SYNC_SYNCED = 3;      // Successfully synced
    public static final int SYNC_FAILED = 4;      // Failed after max retries
    public static final int SYNC_CONFLICT = 5;    // Conflict detected
    public static final int SYNC_MERGED = 6;      // Auto-merged with server

    private static final String TAG = "SyncManager";
    private static SyncManager instance;
    private final Context context;
    private final DatabaseHelper dbHelper;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private volatile boolean isSyncing = false;
    private final java.util.concurrent.locks.ReentrantLock syncLock = new java.util.concurrent.locks.ReentrantLock();
    private String lastSyncError = "";

    public interface SyncListener {
        void onSyncStarted();
        void onSyncProgress(String message);
        void onSyncCompleted(boolean success, String message);
    }

    private SyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = DatabaseHelper.getInstance(this.context);
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized SyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SyncManager(context);
        }
        return instance;
    }

    public boolean isSyncing() {
        return isSyncing;
    }

    public boolean tryAcquireSyncLock() {
        boolean acquired = syncLock.tryLock();
        if (acquired) {
            isSyncing = true;
        }
        return acquired;
    }

    public void releaseSyncLock() {
        if (syncLock.isHeldByCurrentThread()) {
            syncLock.unlock();
        }
        isSyncing = false;
    }

    public void enqueuePeriodicSync() {
        try {
            androidx.work.PeriodicWorkRequest syncRequest =
                new androidx.work.PeriodicWorkRequest.Builder(SyncWorker.class, 30, java.util.concurrent.TimeUnit.MINUTES)
                    .setConstraints(new androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build())
                    .build();
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "CurtissPeriodicSync",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            );
        } catch (Exception e) {
            Log.e(TAG, "Error enqueuing periodic sync: " + e.getMessage());
        }
    }



    // Execute server Pull
    boolean executePull(Context context, int userId) {
        return executePull(context, userId, null);
    }

    boolean executePull(Context context, int userId, final SyncListener listener) {
        lastSyncError = "Unknown error";
        String responseBody = null;
        android.content.SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
        String baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");
        String lastSyncTimestamp = prefs.getString("last_sync_timestamp", "2000-01-01 00:00:00");
        String urlString = baseUrl + "/rep/RepDashboard/sync_pull?api_sync=1&user_id=" + userId;
        try {
            urlString += "&last_sync=" + java.net.URLEncoder.encode(lastSyncTimestamp, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "URLEncoder failed for lastSyncTimestamp: " + e.getMessage());
        }

        int maxRetries = 3;
        int attempt = 0;
        while (attempt < maxRetries) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.connect();

                int responseCode = conn.getResponseCode();
                BufferedReader reader;
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                } else {
                    java.io.InputStream errStream = conn.getErrorStream();
                    reader = errStream != null ? new BufferedReader(new InputStreamReader(errStream, StandardCharsets.UTF_8)) : null;
                }

                if (reader != null) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();

                    String resText = sb.toString().trim();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        if (!resText.startsWith("{") && !resText.startsWith("[")) {
                            Log.e(TAG, "Pull Sync response is not valid JSON. Response starts with: " + (resText.length() > 100 ? resText.substring(0, 100) : resText));
                            throw new Exception("Server response is not valid JSON. Starts with: " + (resText.length() > 60 ? resText.substring(0, 60) : resText));
                        }
                        responseBody = resText;
                        break;
                    } else {
                        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == 419) {
                            handleServerSessionExpired();
                            throw new Exception("Session Expired (HTTP " + responseCode + ")");
                        }
                        Log.e(TAG, "Server error during pull (HTTP " + responseCode + "): " + resText);
                        throw new Exception("HTTP Response Code " + responseCode + " - Error: " + (resText.length() > 200 ? resText.substring(0, 200) : resText));
                    }
                } else {
                    throw new Exception("HTTP Response Code " + responseCode + " (No response stream available)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Pull Sync connection attempt " + attempt + " failed: " + e.getMessage());
                lastSyncError = e.getMessage();
                if (attempt >= maxRetries) {
                    return false;
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        if (responseBody == null) {
            return false;
        }

        try {
            JSONObject response = new JSONObject(responseBody);
            if (response.optBoolean("unauthorized", false) || response.optString("message", "").contains("Unauthorized")) {
                handleServerSessionExpired();
                return false;
            }
            if (response.optBoolean("success", false)) {
                int dbRetries = 5;
                int dbAttempt = 0;
                boolean dbSuccess = false;
                while (dbAttempt < dbRetries) {
                    dbAttempt++;
                    try {
                        SQLiteDatabase db = dbHelper.getWritableDatabase();
                        db.beginTransaction();
                        try {
                        // 1. Sync Products
                        JSONArray products = response.getJSONArray("products");
                        for (int i = 0; i < products.length(); i++) {
                            JSONObject p = products.getJSONObject(i);
                            int id = p.getInt("id");
                            String name = p.getString("name");
                            String catName = "General";
                            if (!p.isNull("category_name")) {
                                String rawCat = p.optString("category_name", "General");
                                if (rawCat != null && !rawCat.trim().isEmpty() && !rawCat.equalsIgnoreCase("null")) {
                                    catName = rawCat;
                                }
                            }
                            double price = p.optDouble("selling_price", 0.0);
                            double wholesale = p.optDouble("wholesale_price", price);
                            int qty = p.optInt("qty", p.optInt("quantity_on_hand", 0));
                            int reserved = p.optInt("quantity_reserved", 0);
                            
                            String imgUrl = "";
                            if (!p.isNull("image_path")) {
                                String rawImg = p.optString("image_path", "");
                                if (rawImg != null && !rawImg.trim().isEmpty() && !rawImg.equalsIgnoreCase("null")) {
                                    imgUrl = rawImg;
                                }
                            }

                            double costPrice = p.optDouble("cost_price", 0.0);
                            String sku = p.optString("sku", "");
                            String sampleCode = p.optString("sample_code", "");
                            String variationsJson = p.optString("variations_json", "");
                            String brand = p.optString("brand", "");
                            String description = p.optString("description", "");
                            String status = p.optString("status", "active");

                            if (status.equalsIgnoreCase("inactive")) {
                                db.delete("products", "id = ?", new String[]{String.valueOf(id)});
                                db.delete("image_download_queue", "product_id = ?", new String[]{String.valueOf(id)});
                            } else {
                                dbHelper.saveProduct(id, name, catName, price, wholesale, costPrice, qty, reserved, imgUrl, sku, sampleCode, variationsJson, brand, description, status);
                                
                                // Trigger background image download cache
                                if (!imgUrl.isEmpty()) {
                                    ImageDownloadManager.getInstance(context).queueImageDownload(context, id, imgUrl);
                                }
                            }
                        }

                        // Clean obsolete local image files immediately after record purges
                        try {
                            ImageDownloadManager.getInstance(context).cleanObsoleteImages(context);
                        } catch (Exception e) {
                            android.util.Log.e("SyncManager", "Obsolete image cleanup error: " + e.getMessage());
                        }

                        // 1.5 Sync Categories directly from server
                        if (response.has("categories")) {
                            try {
                                JSONArray cats = response.getJSONArray("categories");
                                for (int i = 0; i < cats.length(); i++) {
                                    JSONObject cObj = cats.getJSONObject(i);
                                    int catId = cObj.getInt("id");
                                    String cName = cObj.getString("name");
                                    String status = cObj.optString("status", "active");

                                    if (status.equalsIgnoreCase("inactive")) {
                                        db.delete("categories", "id = ?", new String[]{String.valueOf(catId)});
                                    } else if (cName != null && !cName.equalsIgnoreCase("null") && !cName.trim().isEmpty()) {
                                        ContentValues cv = new ContentValues();
                                        cv.put("id", catId);
                                        cv.put("name", cName);
                                        cv.put("status", status);
                                        db.insertWithOnConflict("categories", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                                    }
                                }
                            } catch (Exception e) {
                                android.util.Log.e("SyncManager", "Error syncing categories: " + e.getMessage());
                            }
                        }

                        // 2. Sync Customers Profiles (only override if local matches server profile)
                        JSONArray customers = response.getJSONArray("customers");
                        for (int i = 0; i < customers.length(); i++) {
                            JSONObject c = customers.getJSONObject(i);
                            int serverId = c.getInt("id");
                            String name = c.getString("name");
                            String phone = c.optString("phone", "");
                            String wa = c.optString("whatsapp", "");
                            String address = c.optString("address", "");
                            String territory = c.optString("territory", "");
                            double lat = c.optDouble("latitude", 0.0);
                            double lng = c.optDouble("longitude", 0.0);
                            double outstanding = c.optDouble("outstanding", c.optDouble("outstanding_amount", c.optDouble("balance", 0.0)));
                            int mcaId = c.optInt("mca_id", 0);
                            String mcaName = c.optString("mca_name", "");
                            String status = c.optString("status", "active");

                            if (status.equalsIgnoreCase("inactive")) {
                                db.delete("customers", "server_id = ?", new String[]{String.valueOf(serverId)});
                            } else {
                                // Check if this serverId is already in SQLite
                                Cursor cursor = db.rawQuery("SELECT id, is_synced, name, phone, whatsapp, address, latitude, longitude, updated_at, uuid FROM customers WHERE server_id = ?", new String[]{String.valueOf(serverId)});
                                ContentValues cv = new ContentValues();
                                cv.put("server_id", serverId);
                                cv.put("name", name);
                                cv.put("phone", phone);
                                cv.put("whatsapp", wa);
                                cv.put("address", address);
                                cv.put("territory", territory);
                                cv.put("latitude", lat);
                                cv.put("longitude", lng);
                                cv.put("outstanding", outstanding);
                                cv.put("mca_id", mcaId);
                                cv.put("mca_name", mcaName);
                                cv.put("status", status);
                                cv.put("is_synced", 1);

                                if (cursor.moveToFirst()) {
                                    int localId = cursor.getInt(0);
                                    int localIsSynced = cursor.getInt(1);
                                    String localName = cursor.getString(2);
                                    String localPhone = cursor.getString(3);
                                    String localWhatsapp = cursor.getString(4);
                                    String localAddress = cursor.getString(5);
                                    double localLat = cursor.getDouble(6);
                                    double localLng = cursor.getDouble(7);
                                    String localUpdatedAt = cursor.getString(8);
                                    String localUuid = cursor.getString(9);

                                    // Check if there's a conflict (i.e. local changes exist that differ from server)
                                    if (localIsSynced == 0) {
                                        boolean hasDifference = !name.equals(localName)
                                                || !phone.equals(localPhone)
                                                || !wa.equals(localWhatsapp)
                                                || !address.equals(localAddress)
                                                || Math.abs(lat - localLat) > 0.000001
                                                || Math.abs(lng - localLng) > 0.000001;

                                        if (hasDifference) {
                                            // Conflict detected!
                                             Log.w(TAG, "Conflict detected for customer ID " + serverId + " (" + name + ")");
                                             
                                             // Build local data JSON
                                             JSONObject localJson = new JSONObject();
                                             try {
                                                 localJson.put("name", localName);
                                                 localJson.put("phone", localPhone);
                                                 localJson.put("whatsapp", localWhatsapp);
                                                 localJson.put("address", localAddress);
                                                 localJson.put("latitude", localLat);
                                                 localJson.put("longitude", localLng);
                                                 localJson.put("updated_at", localUpdatedAt);
                                             } catch (Exception jsonEx) {}

                                             // Build server data JSON
                                             JSONObject serverJson = new JSONObject();
                                             String serverUpdatedAt = c.optString("updated_at", "");
                                             try {
                                                 serverJson.put("name", name);
                                                 serverJson.put("phone", phone);
                                                 serverJson.put("whatsapp", wa);
                                                 serverJson.put("address", address);
                                                 serverJson.put("latitude", lat);
                                                 serverJson.put("longitude", lng);
                                                 serverJson.put("updated_at", serverUpdatedAt);
                                             } catch (Exception jsonEx) {}

                                             // Compare timestamps
                                             boolean serverWins = false;
                                             if (!serverUpdatedAt.isEmpty() && localUpdatedAt != null && !localUpdatedAt.isEmpty()) {
                                                 try {
                                                     java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                                                     java.util.Date localDate = sdf.parse(localUpdatedAt);
                                                     java.util.Date serverDate = sdf.parse(serverUpdatedAt);
                                                     if (serverDate != null && localDate != null && serverDate.after(localDate)) {
                                                         serverWins = true;
                                                     }
                                                 } catch (Exception parseEx) {
                                                     Log.e(TAG, "Error parsing conflict timestamps: " + parseEx.getMessage());
                                                 }
                                             }

                                             if (serverWins) {
                                                 Log.d(TAG, "Conflict resolved: Server version wins (newer timestamp). Saving local backup.");
                                                 backupConflict(db, "customers", localId, localUuid, localJson, serverJson);
                                                 // Overwrite with server version
                                                 db.update("customers", cv, "server_id = ?", new String[]{String.valueOf(serverId)});
                                             } else {
                                                 Log.d(TAG, "Conflict resolved: Local version wins. Saving server backup.");
                                                 backupConflict(db, "customers", localId, localUuid, localJson, serverJson);
                                                 // Keep local changes but update server-only fields
                                                 ContentValues localKeepCv = new ContentValues();
                                                 localKeepCv.put("outstanding", outstanding);
                                                 localKeepCv.put("mca_id", mcaId);
                                                 localKeepCv.put("mca_name", mcaName);
                                                 localKeepCv.put("status", status);
                                                 db.update("customers", localKeepCv, "server_id = ?", new String[]{String.valueOf(serverId)});
                                             }
                                        } else {
                                            // No difference, just mark as synced
                                            db.update("customers", cv, "server_id = ?", new String[]{String.valueOf(serverId)});
                                        }
                                    } else {
                                        // Local record is already synced, safe to update
                                        db.update("customers", cv, "server_id = ?", new String[]{String.valueOf(serverId)});
                                    }
                                } else {
                                    // Record doesn't exist, insert new
                                    db.insert("customers", null, cv);
                                }
                                cursor.close();
                            }
                        }

                        // 3. Sync Server Master Routes (Territories)
                        JSONArray routes = response.optJSONArray("routes");
                        if (routes != null) {
                            db.execSQL("CREATE TABLE IF NOT EXISTS server_routes (id INTEGER PRIMARY KEY, name TEXT NOT NULL, main_area_id INTEGER DEFAULT 0, status TEXT DEFAULT 'active')");
                            db.execSQL("DELETE FROM server_routes");
                            for (int i = 0; i < routes.length(); i++) {
                                JSONObject r = routes.getJSONObject(i);
                                int routeId = r.getInt("id");
                                String routeName = r.getString("name");
                                int mainAreaId = r.optInt("main_area_id", 0);
                                String status = r.optString("status", "active");

                                if (!status.equalsIgnoreCase("inactive")) {
                                    ContentValues rCv = new ContentValues();
                                    rCv.put("id", routeId);
                                    rCv.put("name", routeName);
                                    rCv.put("main_area_id", mainAreaId);
                                    rCv.put("status", status);
                                    db.insert("server_routes", null, rCv);
                                }
                            }
                        }

                        // 4. Sync Server Representatives (linked to employee accounts)
                        JSONArray reps = response.optJSONArray("reps");
                        if (reps != null) {
                            db.execSQL("CREATE TABLE IF NOT EXISTS representatives (id INTEGER PRIMARY KEY, username TEXT UNIQUE, password_hash TEXT, employee_id INTEGER, first_name TEXT, last_name TEXT)");
                            db.execSQL("DELETE FROM representatives");
                            for (int i = 0; i < reps.length(); i++) {
                                JSONObject rep = reps.getJSONObject(i);
                                int repId = rep.getInt("id");
                                String username = rep.getString("username");
                                String hash = rep.getString("password_hash");
                                int employeeId = rep.getInt("employee_id");
                                String fName = rep.optString("first_name", "");
                                String lName = rep.optString("last_name", "");

                                ContentValues repCv = new ContentValues();
                                repCv.put("id", repId);
                                repCv.put("username", username);
                                repCv.put("password_hash", hash);
                                repCv.put("employee_id", employeeId);
                                repCv.put("first_name", fName);
                                repCv.put("last_name", lName);
                                db.insert("representatives", null, repCv);
                            }
                        }

                        // 5. Sync Payment Terms
                        JSONArray terms = response.optJSONArray("payment_terms");
                        if (terms != null) {
                            db.execSQL("CREATE TABLE IF NOT EXISTS payment_terms (id INTEGER PRIMARY KEY, name TEXT NOT NULL, days_due INTEGER DEFAULT 0)");
                            db.execSQL("DELETE FROM payment_terms");
                            for (int i = 0; i < terms.length(); i++) {
                                JSONObject t = terms.getJSONObject(i);
                                int termId = t.getInt("id");
                                String termName = t.getString("name");
                                int daysDue = t.optInt("days_due", 0);

                                ContentValues tCv = new ContentValues();
                                tCv.put("id", termId);
                                tCv.put("name", termName);
                                tCv.put("days_due", daysDue);
                                db.insert("payment_terms", null, tCv);
                            }
                        }

                        // 6. Sync Outstanding Credit Invoices
                        if (response.has("credit_invoices") && !response.isNull("credit_invoices")) {
                            db.execSQL("CREATE TABLE IF NOT EXISTS credit_invoices (id INTEGER PRIMARY KEY, invoice_number TEXT NOT NULL, customer_id INTEGER, invoice_date TEXT, true_grand_total REAL, customer_name TEXT, customer_address TEXT)");
                            db.execSQL("DELETE FROM credit_invoices");
                            JSONArray cInvs = response.getJSONArray("credit_invoices");
                            for (int i = 0; i < cInvs.length(); i++) {
                                JSONObject cObj = cInvs.getJSONObject(i);
                                ContentValues cv = new ContentValues();
                                cv.put("id", cObj.getInt("id"));
                                cv.put("invoice_number", cObj.getString("invoice_number"));
                                cv.put("customer_id", cObj.getInt("customer_id"));
                                cv.put("invoice_date", cObj.optString("invoice_date", ""));
                                cv.put("true_grand_total", cObj.optDouble("true_grand_total", 0.0));
                                cv.put("customer_name", cObj.optString("customer_name", ""));
                                cv.put("customer_address", cObj.optString("customer_address", ""));
                                db.insertWithOnConflict("credit_invoices", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                            }
                        }

                        // 7. Sync Ongoing Active Route (if representative has one started on server)
                        if (response.has("active_route") && !response.isNull("active_route") && response.optJSONObject("active_route") != null) {
                            JSONObject act = response.getJSONObject("active_route");
                            int serverRouteId = act.getInt("id");
                            String routeName = act.getString("route_name");
                            double startMeter = act.optDouble("start_meter", 0.0);
                            String startTime = act.optString("start_time", "");
                            double startLat = act.optDouble("start_lat", 0.0);
                            double startLng = act.optDouble("start_lng", 0.0);
                            String status = act.optString("status", "Active");

                            // Check if this route is already in the SQLite database
                            Cursor routeCursor = db.rawQuery("SELECT id, is_synced FROM daily_routes WHERE server_id = ?", new String[]{String.valueOf(serverRouteId)});
                            ContentValues rCv = new ContentValues();
                            rCv.put("server_id", serverRouteId);
                            rCv.put("route_name", routeName);
                            rCv.put("start_meter", startMeter);
                            rCv.put("start_time", startTime);
                            rCv.put("start_lat", startLat);
                            rCv.put("start_lng", startLng);
                            rCv.put("status", status);
                            rCv.put("is_synced", 1);

                            long localRouteId;
                            if (routeCursor.moveToFirst()) {
                                localRouteId = routeCursor.getLong(0);
                                int localIsSynced = routeCursor.getInt(1);
                                // ONLY update status and fields if local route changes are already pushed/synced to server
                                if (localIsSynced == 1) {
                                    db.update("daily_routes", rCv, "id = ?", new String[]{String.valueOf(localRouteId)});
                                }
                            } else {
                                localRouteId = db.insert("daily_routes", null, rCv);
                            }
                            routeCursor.close();

                            // 8. Sync Invoices associated with this active route
                            if (response.has("active_route_invoices") && !response.isNull("active_route_invoices")) {
                                JSONArray invs = response.getJSONArray("active_route_invoices");
                                for (int j = 0; j < invs.length(); j++) {
                                    JSONObject invObj = invs.getJSONObject(j);
                                    int serverInvId = invObj.getInt("id");
                                    String invNumber = invObj.getString("invoice_number");
                                    int serverCustId = invObj.getInt("customer_id");
                                    String invDate = invObj.optString("invoice_date", "");
                                    String dueDate = invObj.optString("due_date", "");
                                    int payTermId = invObj.optInt("payment_term_id", 0);
                                    double subtotal = invObj.optDouble("total_amount", 0.0);
                                    double discount = invObj.optDouble("global_discount_val", 0.0);
                                    double tax = invObj.optDouble("tax_amount", 0.0);
                                    double grandTotal = subtotal - discount + tax;

                                    // Resolve local customer ID
                                    int localCustId = serverCustId;
                                    Cursor custCursor = db.rawQuery("SELECT id FROM customers WHERE server_id = ?", new String[]{String.valueOf(serverCustId)});
                                    if (custCursor.moveToFirst()) {
                                        localCustId = custCursor.getInt(0);
                                    }
                                    custCursor.close();

                                    // Check if invoice exists locally
                                    Cursor invCursor = db.rawQuery("SELECT id, is_synced FROM invoices WHERE server_id = ?", new String[]{String.valueOf(serverInvId)});
                                    ContentValues iCv = new ContentValues();
                                    iCv.put("server_id", serverInvId);
                                    iCv.put("invoice_number", invNumber);
                                    iCv.put("customer_id", localCustId);
                                    iCv.put("route_id", localRouteId);
                                    iCv.put("invoice_date", invDate);
                                    iCv.put("due_date", dueDate);
                                    iCv.put("payment_term_id", payTermId > 0 ? payTermId : null);
                                    iCv.put("subtotal", subtotal);
                                    iCv.put("discount", discount);
                                    iCv.put("tax", tax);
                                    iCv.put("grand_total", grandTotal);
                                    iCv.put("payment_method", "Credit");
                                    iCv.put("is_synced", 1);
                                    iCv.put("sync_status", 3); // 3 = Synced


                                    long localInvId;
                                    if (invCursor.moveToFirst()) {
                                        localInvId = invCursor.getLong(0);
                                        int localInvSynced = invCursor.getInt(1);
                                        // ONLY update invoice locally if changes are already pushed/synced
                                        if (localInvSynced == 1) {
                                            db.update("invoices", iCv, "id = ?", new String[]{String.valueOf(localInvId)});
                                        }
                                    } else {
                                        localInvId = db.insert("invoices", null, iCv);
                                    }
                                    invCursor.close();

                                    // 9. Sync Invoice Items for this invoice
                                    if (response.has("active_route_invoice_items") && !response.isNull("active_route_invoice_items")) {
                                        JSONArray items = response.getJSONArray("active_route_invoice_items");
                                        db.delete("invoice_items", "invoice_id = ?", new String[]{String.valueOf(localInvId)});
                                        for (int k = 0; k < items.length(); k++) {
                                            JSONObject itemObj = items.getJSONObject(k);
                                            if (itemObj.getInt("invoice_id") == serverInvId) {
                                                ContentValues itCv = new ContentValues();
                                                itCv.put("invoice_id", localInvId);
                                                itCv.put("product_id", itemObj.getInt("item_id"));
                                                itCv.put("product_name", itemObj.getString("description"));
                                                itCv.put("quantity", itemObj.getInt("quantity"));
                                                itCv.put("unit_price", itemObj.getDouble("unit_price"));
                                                itCv.put("discount_val", itemObj.optDouble("discount_value", 0.0));
                                                itCv.put("total", itemObj.getDouble("total"));
                                                db.insert("invoice_items", null, itCv);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        db.setTransactionSuccessful();
                        Log.d(TAG, "Pull Sync Successful");
                        dbSuccess = true;
                        break;
                    } finally {
                        db.endTransaction();
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && (e.getMessage().contains("locked") || e.getMessage().contains("BUSY") || e.getMessage().contains("code 5"))) {
                        Log.w(TAG, "Database is locked (SQLITE_BUSY) during pull insertion. Attempt " + dbAttempt + " of " + dbRetries + ". Retrying...");
                        if (dbAttempt >= dbRetries) {
                            throw e;
                        }
                        try {
                            Thread.sleep(200 * dbAttempt); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(ie);
                        }
                    } else {
                        throw e;
                    }
                }
            }
            if (dbSuccess) {
                try {
                    // Trigger queued image downloads
                    ImageDownloadManager.getInstance(context).startQueueDownload(context);

                    // Perform monitoring asynchronously to prevent blocking the sync completion
                    if (listener != null) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    int totalImagesToDownload = 0;
                                    SQLiteDatabase db = dbHelper.getReadableDatabase();
                                    Cursor cursorImg = db.rawQuery("SELECT COUNT(*) FROM image_download_queue WHERE status = 'downloading'", null);
                                    if (cursorImg.moveToFirst()) {
                                        totalImagesToDownload = cursorImg.getInt(0);
                                    }
                                    cursorImg.close();

                                    if (totalImagesToDownload > 0) {
                                        Log.d(TAG, "Pull sync (Async Image Monitor): " + totalImagesToDownload + " images currently downloading.");
                                        int remaining = totalImagesToDownload;
                                        int loopCount = 0;
                                        int maxLoops = 300; // 300 * 300ms = 90 seconds safety timeout
                                        while (remaining > 0 && loopCount < maxLoops) {
                                            loopCount++;
                                            try {
                                                Thread.sleep(300);
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                                break;
                                            }

                                            remaining = 0;
                                            Cursor cursorImg2 = db.rawQuery("SELECT COUNT(*) FROM image_download_queue WHERE status = 'downloading'", null);
                                            if (cursorImg2.moveToFirst()) {
                                                remaining = cursorImg2.getInt(0);
                                            }
                                            cursorImg2.close();

                                            int downloaded = totalImagesToDownload - remaining;
                                            int percent = (downloaded * 100) / totalImagesToDownload;
                                            updateProgress(listener, "Downloading images: " + downloaded + " / " + totalImagesToDownload + " (" + percent + "%)");
                                        }
                                    }
                                } catch (Exception imgEx) {
                                    Log.e(TAG, "Error waiting for images to download asynchronously: " + imgEx.getMessage());
                                }
                            }
                        }).start();
                    }
                } catch (Exception imgEx) {
                    Log.e(TAG, "Error starting image queue download: " + imgEx.getMessage());
                }
            }
            if (dbSuccess) {
                String newTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                prefs.edit().putString("last_sync_timestamp", newTimestamp).apply();
            }
            return dbSuccess;
        }
        } catch (Exception e) {
            Log.e(TAG, "Pull error during database insertion: " + e.getMessage());
            Log.e(TAG, "Pull response body was: " + (responseBody != null ? responseBody : "NULL"));
            lastSyncError = "DB insertion error: " + e.getMessage();
        }
        return false;
    }

    // Two-phase commit wrapper for push sync
    public boolean executePushSafe(Context context, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        // PHASE 1: Prepare - mark records as "syncing"
        db.beginTransaction();
        try {
            // Mark all pending (1) and failed (4) records with sync_status = 2 (Syncing)
            db.execSQL("UPDATE invoices SET sync_status = 2 WHERE sync_status = 1 OR sync_status = 4");
            db.execSQL("UPDATE customers SET sync_status = 2 WHERE sync_status = 1 OR sync_status = 4");
            db.execSQL("UPDATE daily_routes SET sync_status = 2 WHERE sync_status = 1 OR sync_status = 4");
            db.execSQL("UPDATE payments SET sync_status = 2 WHERE sync_status = 1 OR sync_status = 4");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        
        // PHASE 2: Execute sync
        boolean success = executePush(context, userId);
        
        // PHASE 3: Finalize
        db.beginTransaction();
        try {
            if (success) {
                // Mark syncing records as completed
                db.execSQL("UPDATE invoices SET sync_status = 3, is_synced = 1 WHERE sync_status = 2");
                db.execSQL("UPDATE customers SET sync_status = 3, is_synced = 1 WHERE sync_status = 2");
                db.execSQL("UPDATE daily_routes SET sync_status = 3, is_synced = 1 WHERE sync_status = 2");
                db.execSQL("UPDATE payments SET sync_status = 3, is_synced = 1 WHERE sync_status = 2");
            } else {
                // Rollback: restore syncing records to pending (1)
                db.execSQL("UPDATE invoices SET sync_status = 1 WHERE sync_status = 2");
                db.execSQL("UPDATE customers SET sync_status = 1 WHERE sync_status = 2");
                db.execSQL("UPDATE daily_routes SET sync_status = 1 WHERE sync_status = 2");
                db.execSQL("UPDATE payments SET sync_status = 1 WHERE sync_status = 2");
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        
        return success;
    }

    // Compile local changes and push to server
    boolean executePush(Context context, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String responseBody = null;
        try {
            JSONObject payload = new JSONObject();
            payload.put("user_id", userId);

            // 1. Fetch local unsynced Customers
            JSONArray custArray = new JSONArray();
            Cursor custCursor = db.rawQuery("SELECT * FROM customers WHERE is_synced = 0 OR sync_status IN (1, 2, 4)", null);
            while (custCursor.moveToNext()) {
                JSONObject c = new JSONObject();
                c.put("local_id", DatabaseHelper.safeGetInt(custCursor, "id", 0));
                c.put("name", DatabaseHelper.safeGetString(custCursor, "name", ""));
                c.put("phone", DatabaseHelper.safeGetString(custCursor, "phone", ""));
                c.put("whatsapp", DatabaseHelper.safeGetString(custCursor, "whatsapp", ""));
                c.put("address", DatabaseHelper.safeGetString(custCursor, "address", ""));
                c.put("territory", DatabaseHelper.safeGetString(custCursor, "territory", ""));
                c.put("latitude", DatabaseHelper.safeGetDouble(custCursor, "latitude", 0.0));
                c.put("longitude", DatabaseHelper.safeGetDouble(custCursor, "longitude", 0.0));
                c.put("uuid", DatabaseHelper.safeGetString(custCursor, "uuid", ""));
                custArray.put(c);
            }
            custCursor.close();
            payload.put("customers", custArray);

            // 2. Fetch local unsynced Routes
            JSONArray routeArray = new JSONArray();
            Cursor rCursor = db.rawQuery("SELECT * FROM daily_routes WHERE is_synced = 0 OR sync_status IN (1, 2, 4)", null);
            while (rCursor.moveToNext()) {
                JSONObject r = new JSONObject();
                r.put("local_id", DatabaseHelper.safeGetInt(rCursor, "id", 0));
                r.put("route_name", DatabaseHelper.safeGetString(rCursor, "route_name", ""));
                r.put("start_meter", DatabaseHelper.safeGetDouble(rCursor, "start_meter", 0.0));
                r.put("start_time", DatabaseHelper.safeGetString(rCursor, "start_time", ""));
                r.put("start_lat", DatabaseHelper.safeGetDouble(rCursor, "start_lat", 0.0));
                r.put("start_lng", DatabaseHelper.safeGetDouble(rCursor, "start_lng", 0.0));
                r.put("end_meter", DatabaseHelper.safeGetDouble(rCursor, "end_meter", 0.0));
                r.put("end_time", DatabaseHelper.safeGetString(rCursor, "end_time", ""));
                r.put("end_lat", DatabaseHelper.safeGetDouble(rCursor, "end_lat", 0.0));
                r.put("end_lng", DatabaseHelper.safeGetDouble(rCursor, "end_lng", 0.0));
                r.put("status", DatabaseHelper.safeGetString(rCursor, "status", ""));
                r.put("uuid", DatabaseHelper.safeGetString(rCursor, "uuid", ""));
                routeArray.put(r);
            }
            rCursor.close();
            payload.put("routes", routeArray);

            // 3. Fetch local unsynced Invoices
            JSONArray invArray = new JSONArray();
            Cursor invCursor = db.rawQuery("SELECT * FROM invoices WHERE is_synced = 0 OR sync_status IN (1, 2, 4)", null);
            Log.d(TAG, "SyncManager: Found " + invCursor.getCount() + " unsynced local invoices.");
            java.util.List<Integer> attemptedInvoiceIds = new java.util.ArrayList<>();
            while (invCursor.moveToNext()) {
                JSONObject inv = new JSONObject();
                int localInvId = DatabaseHelper.safeGetInt(invCursor, "id", 0);
                attemptedInvoiceIds.add(localInvId);
                inv.put("local_id", localInvId);
                inv.put("invoice_number", DatabaseHelper.safeGetString(invCursor, "invoice_number", ""));
                inv.put("uuid", DatabaseHelper.safeGetString(invCursor, "uuid", ""));
                int localCustId = DatabaseHelper.safeGetInt(invCursor, "customer_id", 0);
                int serverCustId = localCustId;
                Cursor cCust = db.rawQuery("SELECT server_id FROM customers WHERE id = ?", new String[]{String.valueOf(localCustId)});
                if (cCust.moveToFirst()) {
                    int sid = cCust.getInt(0);
                    if (sid > 0) {
                        serverCustId = sid;
                    }
                }
                cCust.close();
                inv.put("customer_id", serverCustId);
                
                // Track associated route_id in payload for precise server side route matching
                int localRouteId = DatabaseHelper.safeGetInt(invCursor, "route_id", 0);
                inv.put("local_route_id", localRouteId);
                int serverRouteId = 0;
                Cursor cRoute = db.rawQuery("SELECT server_id FROM daily_routes WHERE id = ?", new String[]{String.valueOf(localRouteId)});
                if (cRoute.moveToFirst()) {
                    serverRouteId = cRoute.getInt(0);
                }
                cRoute.close();
                inv.put("server_route_id", serverRouteId);
                
                // Track associated route_uuid in payload for precise server side route matching
                String routeUuid = "";
                Cursor cRouteUuid = db.rawQuery("SELECT uuid FROM daily_routes WHERE id = ?", new String[]{String.valueOf(localRouteId)});
                if (cRouteUuid.moveToFirst()) {
                    routeUuid = cRouteUuid.getString(0);
                }
                cRouteUuid.close();
                inv.put("route_uuid", routeUuid);

                Log.d(TAG, "SyncManager: Staging invoice " + DatabaseHelper.safeGetString(invCursor, "invoice_number", "") + " (local_id: " + localInvId + ", local_route_id: " + localRouteId + ", server_route_id: " + serverRouteId + ", route_uuid: " + routeUuid + ", server_customer_id: " + serverCustId + ")");
                
                inv.put("invoice_date", DatabaseHelper.safeGetString(invCursor, "invoice_date", ""));
                inv.put("due_date", DatabaseHelper.safeGetString(invCursor, "due_date", ""));
                inv.put("subtotal", DatabaseHelper.safeGetDouble(invCursor, "subtotal", 0.0));
                inv.put("discount", DatabaseHelper.safeGetDouble(invCursor, "discount", 0.0));
                inv.put("tax", DatabaseHelper.safeGetDouble(invCursor, "tax", 0.0));
                inv.put("grand_total", DatabaseHelper.safeGetDouble(invCursor, "grand_total", 0.0));
                inv.put("payment_method", DatabaseHelper.safeGetString(invCursor, "payment_method", ""));
                inv.put("latitude", DatabaseHelper.safeGetDouble(invCursor, "latitude", 0.0));
                inv.put("longitude", DatabaseHelper.safeGetDouble(invCursor, "longitude", 0.0));
                
                int ptIdx = invCursor.getColumnIndex("payment_term_id");
                if (ptIdx == -1 || invCursor.isNull(ptIdx)) {
                    inv.put("payment_term_id", JSONObject.NULL);
                } else {
                    inv.put("payment_term_id", invCursor.getInt(ptIdx));
                }

                // Load invoice items
                JSONArray itemsArray = new JSONArray();
                Cursor itemCursor = db.rawQuery("SELECT * FROM invoice_items WHERE invoice_id = ?", new String[]{String.valueOf(localInvId)});
                while (itemCursor.moveToNext()) {
                    JSONObject item = new JSONObject();
                    item.put("product_id", DatabaseHelper.safeGetInt(itemCursor, "product_id", 0));
                    item.put("product_name", DatabaseHelper.safeGetString(itemCursor, "product_name", ""));
                    item.put("quantity", DatabaseHelper.safeGetInt(itemCursor, "quantity", 0));
                    item.put("unit_price", DatabaseHelper.safeGetDouble(itemCursor, "unit_price", 0.0));
                    item.put("discount_val", DatabaseHelper.safeGetDouble(itemCursor, "discount_val", 0.0));
                    item.put("total", DatabaseHelper.safeGetDouble(itemCursor, "total", 0.0));
                    itemsArray.put(item);
                }
                itemCursor.close();
                inv.put("items", itemsArray);
                invArray.put(inv);
            }
            invCursor.close();
            payload.put("invoices", invArray);

            // 4. Fetch local unsynced Payments (Outstanding collections)
            JSONArray payArray = new JSONArray();
            try {
                Cursor payCursor = db.rawQuery("SELECT * FROM payments WHERE is_synced = 0 OR sync_status IN (1, 2, 4)", null);
                while (payCursor.moveToNext()) {
                    JSONObject payObj = new JSONObject();
                    payObj.put("local_id", DatabaseHelper.safeGetInt(payCursor, "id", 0));
                    payObj.put("uuid", DatabaseHelper.safeGetString(payCursor, "uuid", ""));
                    payObj.put("customer_id", DatabaseHelper.safeGetInt(payCursor, "customer_id", 0));
                    payObj.put("server_route_id", DatabaseHelper.safeGetInt(payCursor, "server_route_id", 0));
                    int localRouteId = DatabaseHelper.safeGetInt(payCursor, "local_route_id", 0);
                    payObj.put("local_route_id", localRouteId);

                    // Track associated route_uuid in payload for precise server-side route matching
                    String routeUuid = "";
                    if (localRouteId > 0) {
                        Cursor cRouteUuid = db.rawQuery("SELECT uuid FROM daily_routes WHERE id = ?", new String[]{String.valueOf(localRouteId)});
                        if (cRouteUuid.moveToFirst()) {
                            routeUuid = cRouteUuid.getString(0);
                        }
                        cRouteUuid.close();
                    }
                    payObj.put("route_uuid", routeUuid);

                    payObj.put("payment_method", DatabaseHelper.safeGetString(payCursor, "payment_method", ""));
                    payObj.put("amount", DatabaseHelper.safeGetDouble(payCursor, "amount", 0.0));
                    payObj.put("bank_name", DatabaseHelper.safeGetString(payCursor, "bank_name", ""));
                    payObj.put("cheque_number", DatabaseHelper.safeGetString(payCursor, "cheque_number", ""));
                    payObj.put("cheque_date", DatabaseHelper.safeGetString(payCursor, "cheque_date", ""));
                    payObj.put("latitude", DatabaseHelper.safeGetDouble(payCursor, "latitude", 0.0));
                    payObj.put("longitude", DatabaseHelper.safeGetDouble(payCursor, "longitude", 0.0));
                    payArray.put(payObj);
                }
                payCursor.close();
            } catch (Exception e) {
                // Table might not exist, ignore
            }
            payload.put("payments", payArray);

            // Skip API post if there's nothing to upload
            if (custArray.length() == 0 && routeArray.length() == 0 && invArray.length() == 0 && payArray.length() == 0) {
                return true;
            }

            // POST unified payload to Plesk Sync API
            android.content.SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
            String baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");
            String urlString = baseUrl + "/rep/RepDashboard/sync_push?api_sync=1";
            Log.d(TAG, "Starting Push Sync POST to: " + urlString);
            Log.d(TAG, "Push Payload details: " + payload.toString());

            responseBody = null;
            int maxRetries = 3;
            int attempt = 0;
            byte[] jsonBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            while (attempt < maxRetries) {
                attempt++;
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(urlString);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    OutputStream os = conn.getOutputStream();
                    os.write(jsonBytes, 0, jsonBytes.length);
                    os.flush();
                    os.close();

                    int responseCode = conn.getResponseCode();
                    Log.d(TAG, "Push server responded with code: " + responseCode);

                    BufferedReader reader;
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    } else {
                        java.io.InputStream errStream = conn.getErrorStream();
                        reader = errStream != null ? new BufferedReader(new InputStreamReader(errStream, StandardCharsets.UTF_8)) : null;
                    }

                    if (reader != null) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        reader.close();

                        String resText = sb.toString().trim();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            if (!resText.startsWith("{") && !resText.startsWith("[")) {
                                Log.e(TAG, "Push Sync response is not valid JSON. Response starts with: " + (resText.length() > 100 ? resText.substring(0, 100) : resText));
                                throw new Exception("Server response is not valid JSON. Starts with: " + (resText.length() > 60 ? resText.substring(0, 60) : resText));
                            }
                            responseBody = resText;
                            Log.d(TAG, "Push Server Response Body: " + responseBody);
                            break;
                        } else {
                            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == 419) {
                                handleServerSessionExpired();
                                throw new Exception("Session Expired (HTTP " + responseCode + ")");
                            }
                            Log.e(TAG, "Server error during push (HTTP " + responseCode + "): " + resText);
                            throw new Exception("HTTP Response Code " + responseCode + " - Error: " + (resText.length() > 200 ? resText.substring(0, 200) : resText));
                        }
                    } else {
                        throw new Exception("HTTP Response Code " + responseCode + " (No response stream available)");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Push Sync connection attempt " + attempt + " failed: " + e.getMessage());
                    if (attempt >= maxRetries) {
                        return false;
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }

            if (responseBody == null) {
                return false;
            }

            try {
                JSONObject response = new JSONObject(responseBody);
                if (response.optBoolean("unauthorized", false) || response.optString("message", "").contains("Unauthorized")) {
                    handleServerSessionExpired();
                    return false;
                }
                if (response.optBoolean("success", false)) {
                    JSONObject mappings = response.getJSONObject("mappings");

                    int dbRetries = 5;
                    int dbAttempt = 0;
                    boolean dbSuccess = false;
                    while (dbAttempt < dbRetries) {
                        dbAttempt++;
                        try {
                            db.beginTransaction();
                            try {
                        // 1. Mark customers synced and update server_id
                        JSONArray cMaps = mappings.getJSONArray("customers");
                        for (int i = 0; i < cMaps.length(); i++) {
                            JSONObject map = cMaps.getJSONObject(i);
                            int localId = map.getInt("local_id");
                            int serverId = map.getInt("server_id");

                            ContentValues cv = new ContentValues();
                            cv.put("server_id", serverId);
                            cv.put("is_synced", 1);
                            db.update("customers", cv, "id = ?", new String[]{String.valueOf(localId)});
                        }

                        // 2. Mark routes synced
                        JSONArray rMaps = mappings.getJSONArray("routes");
                        for (int i = 0; i < rMaps.length(); i++) {
                            JSONObject map = rMaps.getJSONObject(i);
                            int localId = map.getInt("local_id");
                            int serverId = map.getInt("server_id");

                            ContentValues cv = new ContentValues();
                            cv.put("server_id", serverId);
                            cv.put("is_synced", 1);
                            db.update("daily_routes", cv, "id = ?", new String[]{String.valueOf(localId)});
                        }

                        // 3. Mark invoices synced
                        JSONArray iMaps = mappings.getJSONArray("invoices");
                        java.util.Set<Integer> mappedInvoiceIds = new java.util.HashSet<>();
                        for (int i = 0; i < iMaps.length(); i++) {
                            JSONObject map = iMaps.getJSONObject(i);
                            int localId = map.getInt("local_id");
                            int serverId = map.getInt("server_id");
                            mappedInvoiceIds.add(localId);

                            ContentValues cv = new ContentValues();
                            cv.put("server_id", serverId);
                            cv.put("is_synced", 1);
                            cv.put("sync_status", 3); // 3 = Synced
                            cv.put("failure_reason", "");
                            if (map.has("invoice_number")) {
                                String mappedNum = map.getString("invoice_number");
                                cv.put("invoice_number", mappedNum);

                                // Parse suffix and update SharedPreferences so next invoice starts from here!
                                if (mappedNum.length() >= 4) {
                                    try {
                                        String suffix = mappedNum.substring(mappedNum.length() - 4);
                                        int parsedSeq = Integer.parseInt(suffix);
                                        android.content.SharedPreferences seqPrefs = context.getSharedPreferences("CurtissPrefs", Context.MODE_PRIVATE);
                                        int currentSeq = seqPrefs.getInt("global_invoice_seq", 0);
                                        if (parsedSeq > currentSeq) {
                                            seqPrefs.edit().putInt("global_invoice_seq", parsedSeq).apply();
                                        }
                                    } catch (Exception e) {
                                        // Ignore parsing errors
                                    }
                                }
                            }
                            db.update("invoices", cv, "id = ?", new String[]{String.valueOf(localId)});

                            // Update sync_logs table
                            try {
                                String completedTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                                db.execSQL("UPDATE sync_logs SET upload_completed = ?, erp_response = 'Success (Mapped)' WHERE bill_id = ?", new Object[]{completedTime, localId});
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating sync_logs upload_completed for invoice " + localId + ": " + e.getMessage());
                            }
                        }

                        // Mark any attempted invoices that were NOT in the server's mapping response as failed
                        for (int attemptedId : attemptedInvoiceIds) {
                            if (!mappedInvoiceIds.contains(attemptedId)) {
                                ContentValues cv = new ContentValues();
                                cv.put("sync_status", 4); // 4 = Failed
                                cv.put("failure_reason", "Server failed to return a mapping - check ERP logs.");
                                db.update("invoices", cv, "id = ?", new String[]{String.valueOf(attemptedId)});

                                try {
                                    db.execSQL("UPDATE sync_logs SET failure_reason = 'Server failed to return a mapping' WHERE bill_id = ?", new Object[]{attemptedId});
                                } catch (Exception e) {
                                    Log.e(TAG, "Error updating sync_logs failure reason for invoice " + attemptedId + ": " + e.getMessage());
                                }
                                Log.w(TAG, "Invoice local ID " + attemptedId + " was pushed but server returned no mapping for it.");
                            }
                        }

                        // 4. Mark payment collections synced
                        try {
                            db.execSQL("UPDATE payments SET is_synced = 1 WHERE is_synced = 0");
                        } catch (Exception e) {
                            Log.e(TAG, "Failed updating local payments is_synced: " + e.getMessage());
                        }

                        // Perform post-sync checksum/integrity verification before committing
                        boolean customersOk = verifySyncIntegrity(mappings, "customers");
                        boolean routesOk = verifySyncIntegrity(mappings, "routes");
                        boolean invoicesOk = verifySyncIntegrity(mappings, "invoices");

                        if (customersOk && routesOk && invoicesOk) {
                            db.setTransactionSuccessful();
                            Log.d(TAG, "Push Sync Successful: Staged payments & invoices committed successfully!");
                            dbSuccess = true;
                        } else {
                            Log.e(TAG, "Integrity verification failed for one or more sync groups. Transaction aborted!");
                            dbSuccess = false;
                        }
                        break;
                    } finally {
                        db.endTransaction();
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && (e.getMessage().contains("locked") || e.getMessage().contains("BUSY") || e.getMessage().contains("code 5"))) {
                        Log.w(TAG, "Database is locked (SQLITE_BUSY) during push insertion. Attempt " + dbAttempt + " of " + dbRetries + ". Retrying...");
                        if (dbAttempt >= dbRetries) {
                            throw e;
                        }
                        try {
                            Thread.sleep(200 * dbAttempt); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(ie);
                        }
                    } else {
                        throw e;
                    }
                }
            }
            return dbSuccess;
                } else {
                    String serverMsg = response.optString("message", "Rejected by server business logic");
                    Log.e(TAG, "Push Sync rejected by server business logic: " + serverMsg);
                    try {
                        db.beginTransaction();
                        try {
                            for (int attemptedId : attemptedInvoiceIds) {
                                ContentValues cv = new ContentValues();
                                cv.put("sync_status", 4); // 4 = Failed
                                cv.put("failure_reason", serverMsg);
                                db.update("invoices", cv, "id = ?", new String[]{String.valueOf(attemptedId)});

                                try {
                                    db.execSQL("UPDATE sync_logs SET failure_reason = ? WHERE bill_id = ?", new Object[]{serverMsg, attemptedId});
                                } catch (Exception e) {
                                    Log.e(TAG, "Error updating sync_logs failure reason on server rejection for invoice " + attemptedId + ": " + e.getMessage());
                                }
                            }
                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "Failed to mark invoices as failed on server rejection: " + ex.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Push error parsing server JSON response: " + e.getMessage());
                Log.e(TAG, "Push response body was: " + (responseBody != null ? responseBody : "NULL"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Push error crash/exception: " + e.getMessage(), e);
            Log.e(TAG, "Push response body was: " + (responseBody != null ? responseBody : "NULL"));
        }
        return false;
    }

    // Checksum/Integrity verification after sync
    private boolean verifySyncIntegrity(JSONObject mappings, String type) {
        try {
            String tableName;
            String mappingKey;
            if (type.equals("customers")) {
                tableName = "customers";
                mappingKey = "customers";
            } else if (type.equals("routes")) {
                tableName = "daily_routes";
                mappingKey = "routes";
            } else if (type.equals("invoices")) {
                tableName = "invoices";
                mappingKey = "invoices";
            } else {
                return true;
            }

            if (mappings == null || !mappings.has(mappingKey)) {
                return true;
            }

            JSONArray serverIds = mappings.getJSONArray(mappingKey);
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            // Check that every mapped record returned by the server exists in our local database and has matching UUID
            for (int i = 0; i < serverIds.length(); i++) {
                JSONObject mapping = serverIds.getJSONObject(i);
                int localId = mapping.optInt("local_id", -1);
                String serverUuid = mapping.optString("uuid", "");
                int serverId = mapping.optInt("server_id", -1);

                if (localId == -1 && serverUuid.isEmpty()) {
                    Log.w(TAG, "Integrity verification failure: empty mapping entry returned by server.");
                    return false;
                }

                // Verify that server ID is valid
                if (serverId <= 0) {
                    Log.w(TAG, "Integrity verification failure: server returned invalid server_id (" + serverId + ") for " + type);
                    return false;
                }

                // Retrieve local record by ID or UUID
                Cursor cursor = null;
                if (localId != -1) {
                    cursor = db.rawQuery("SELECT uuid FROM " + tableName + " WHERE id = ?", new String[]{String.valueOf(localId)});
                } else {
                    cursor = db.rawQuery("SELECT uuid FROM " + tableName + " WHERE uuid = ?", new String[]{serverUuid});
                }

                boolean recordExists = false;
                String localUuid = "";
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        recordExists = true;
                        localUuid = cursor.getString(0);
                    }
                    cursor.close();
                }

                if (!recordExists) {
                    Log.w(TAG, "Integrity verification failure: Server returned mapping for non-existent local record in " + tableName + " (localId: " + localId + ", uuid: " + serverUuid + ")");
                    return false;
                }

                // If both serverUuid and localUuid are populated, they must match exactly
                if (!serverUuid.isEmpty() && localUuid != null && !localUuid.isEmpty()) {
                    if (!serverUuid.equals(localUuid)) {
                        Log.w(TAG, "Integrity verification failure: UUID mismatch for " + tableName + " ID " + localId + ". Local: " + localUuid + ", Server: " + serverUuid);
                        return false;
                    }
                }
            }
            Log.d(TAG, "Integrity verification successful for: " + type);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Integrity verification exception for " + type + ": " + e.getMessage());
            return false;
        }
    }

    // Phase 2 Verification Loop
    boolean executeVerification(Context context, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            JSONObject payload = new JSONObject();
            JSONArray uuidArray = new JSONArray();

            // Collect customers
            Cursor cust = db.rawQuery("SELECT id, uuid FROM customers WHERE uuid IS NOT NULL AND uuid != '' AND (is_synced = 0 OR sync_status != 3)", null);
            java.util.Map<String, Integer> custMap = new java.util.HashMap<>();
            while (cust.moveToNext()) {
                String uuid = cust.getString(cust.getColumnIndexOrThrow("uuid"));
                int localId = cust.getInt(cust.getColumnIndexOrThrow("id"));
                custMap.put(uuid, localId);
                
                JSONObject obj = new JSONObject();
                obj.put("uuid", uuid);
                obj.put("type", "customer");
                uuidArray.put(obj);
            }
            cust.close();

            // Collect routes
            Cursor routes = db.rawQuery("SELECT id, uuid FROM daily_routes WHERE uuid IS NOT NULL AND uuid != '' AND (is_synced = 0 OR sync_status != 3)", null);
            java.util.Map<String, Integer> routeMap = new java.util.HashMap<>();
            while (routes.moveToNext()) {
                String uuid = routes.getString(routes.getColumnIndexOrThrow("uuid"));
                int localId = routes.getInt(routes.getColumnIndexOrThrow("id"));
                routeMap.put(uuid, localId);

                JSONObject obj = new JSONObject();
                obj.put("uuid", uuid);
                obj.put("type", "route");
                uuidArray.put(obj);
            }
            routes.close();

            // Collect invoices
            Cursor invs = db.rawQuery("SELECT id, uuid FROM invoices WHERE uuid IS NOT NULL AND uuid != '' AND (is_synced = 0 OR sync_status != 3)", null);
            java.util.Map<String, Integer> invMap = new java.util.HashMap<>();
            while (invs.moveToNext()) {
                String uuid = invs.getString(invs.getColumnIndexOrThrow("uuid"));
                int localId = invs.getInt(invs.getColumnIndexOrThrow("id"));
                invMap.put(uuid, localId);

                JSONObject obj = new JSONObject();
                obj.put("uuid", uuid);
                obj.put("type", "invoice");
                uuidArray.put(obj);
            }
            invs.close();

            // Collect payments
            Cursor pmts = db.rawQuery("SELECT id, uuid FROM payments WHERE uuid IS NOT NULL AND uuid != '' AND (is_synced = 0 OR sync_status != 3)", null);
            java.util.Map<String, Integer> pmtMap = new java.util.HashMap<>();
            while (pmts.moveToNext()) {
                String uuid = pmts.getString(pmts.getColumnIndexOrThrow("uuid"));
                int localId = pmts.getInt(pmts.getColumnIndexOrThrow("id"));
                pmtMap.put(uuid, localId);

                JSONObject obj = new JSONObject();
                obj.put("uuid", uuid);
                obj.put("type", "payment");
                uuidArray.put(obj);
            }
            pmts.close();

            if (uuidArray.length() == 0) {
                return true;
            }

            payload.put("uuids", uuidArray);

            android.content.SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
            String baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");
            String urlString = baseUrl + "/rep/RepDashboard/sync_verify?api_sync=1";
            
            byte[] jsonBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            HttpURLConnection conn = null;
            String responseBody = null;
            
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            OutputStream os = conn.getOutputStream();
            os.write(jsonBytes, 0, jsonBytes.length);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                responseBody = sb.toString().trim();
            }
            
            if (responseBody != null) {
                JSONObject resObj = new JSONObject(responseBody);
                if (resObj.optBoolean("success", false)) {
                    JSONArray results = resObj.getJSONArray("results");
                    db.beginTransaction();
                    try {
                        String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject resItem = results.getJSONObject(i);
                            String uuid = resItem.getString("uuid");
                            String type = resItem.getString("type");
                            boolean verified = resItem.getBoolean("verified");
                            int serverId = resItem.optInt("server_id", 0);

                            ContentValues cv = new ContentValues();
                            if (verified) {
                                if (type.equals("customer") && custMap.containsKey(uuid)) {
                                    cv.put("is_synced", 1);
                                    cv.put("sync_status", 3);
                                    cv.put("failure_reason", "");
                                    if (serverId > 0) cv.put("server_id", serverId);
                                    cv.put("last_attempt_time", currentTime);
                                    db.update("customers", cv, "id = ?", new String[]{String.valueOf(custMap.get(uuid))});
                                } else if (type.equals("route") && routeMap.containsKey(uuid)) {
                                    cv.put("is_synced", 1);
                                    cv.put("sync_status", 3);
                                    cv.put("failure_reason", "");
                                    if (serverId > 0) cv.put("server_id", serverId);
                                    cv.put("last_attempt_time", currentTime);
                                    db.update("daily_routes", cv, "id = ?", new String[]{String.valueOf(routeMap.get(uuid))});
                                } else if (type.equals("invoice") && invMap.containsKey(uuid)) {
                                    cv.put("is_synced", 1);
                                    cv.put("sync_status", 3);
                                    cv.put("failure_reason", "");
                                    if (serverId > 0) cv.put("server_id", serverId);
                                    cv.put("last_attempt_time", currentTime);
                                    db.update("invoices", cv, "id = ?", new String[]{String.valueOf(invMap.get(uuid))});
                                } else if (type.equals("payment") && pmtMap.containsKey(uuid)) {
                                    cv.put("is_synced", 1);
                                    cv.put("sync_status", 3);
                                    cv.put("failure_reason", "");
                                    cv.put("last_attempt_time", currentTime);
                                    db.update("payments", cv, "id = ?", new String[]{String.valueOf(pmtMap.get(uuid))});
                                }
                            } else {
                                cv.put("sync_status", 4); // Failed verification
                                cv.put("failure_reason", "Unverified on server");
                                cv.put("last_attempt_time", currentTime);
                                if (type.equals("customer") && custMap.containsKey(uuid)) {
                                    db.update("customers", cv, "id = ?", new String[]{String.valueOf(custMap.get(uuid))});
                                } else if (type.equals("route") && routeMap.containsKey(uuid)) {
                                    db.update("daily_routes", cv, "id = ?", new String[]{String.valueOf(routeMap.get(uuid))});
                                } else if (type.equals("invoice") && invMap.containsKey(uuid)) {
                                    db.update("invoices", cv, "id = ?", new String[]{String.valueOf(invMap.get(uuid))});
                                } else if (type.equals("payment") && pmtMap.containsKey(uuid)) {
                                    db.update("payments", cv, "id = ?", new String[]{String.valueOf(pmtMap.get(uuid))});
                                }
                            }
                        }
                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Verification loop error: " + e.getMessage());
        }
        return false;
    }

    // Startup Pull-only Synchronization (ERP -> Mobile)
    public void startPullSync(final Context context, final int userId, final SyncListener listener) {
        if (!tryAcquireSyncLock()) {
            if (listener != null) {
                listener.onSyncStarted();
                listener.onSyncCompleted(false, "Sync already in progress.");
            }
            return;
        }

        if (listener != null) {
            listener.onSyncStarted();
        }

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    updateProgress(listener, "Downloading fresh catalog, routes & customers...");
                    boolean pullSuccess = executePull(context, userId, listener);
                    if (pullSuccess) {
                        completeSync(listener, true, "Database pulled successfully!");
                    } else {
                        completeSync(listener, false, "Pull sync failed: " + (lastSyncError != null && !lastSyncError.isEmpty() ? lastSyncError : "Server unreachable."));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Pull Sync exception: " + e.getMessage());
                    completeSync(listener, false, "Exception: " + e.getMessage());
                } finally {
                    releaseSyncLock();
                }
            }
        });
    }

    // User-initiated Two-Phase Push Sync (Mobile -> ERP) with Verification
    public void startManualPushSync(final Context context, final int userId, final SyncListener listener) {
        if (!tryAcquireSyncLock()) {
            if (listener != null) {
                listener.onSyncStarted();
                listener.onSyncCompleted(false, "Sync already in progress.");
            }
            return;
        }

        if (listener != null) {
            listener.onSyncStarted();
        }

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();

                    // Phase 1: Uploading
                    updateProgress(listener, "Phase 1: Uploading transactions to ERP...");
                    boolean pushSuccess = executePushSafe(context, userId);

                    // Phase 2: Verification
                    updateProgress(listener, "Phase 2: Verifying uploads with ERP server...");
                    boolean verifySuccess = executeVerification(context, userId);

                    // Count remaining unsynced items to determine absolute success
                    int unsyncedCount = 0;
                    Cursor c = null;
                    try {
                        c = db.rawQuery("SELECT COUNT(*) FROM invoices WHERE is_synced = 0 UNION ALL SELECT COUNT(*) FROM payments WHERE is_synced = 0 UNION ALL SELECT COUNT(*) FROM customers WHERE is_synced = 0 UNION ALL SELECT COUNT(*) FROM daily_routes WHERE is_synced = 0", null);
                        while (c.moveToNext()) {
                            unsyncedCount += c.getInt(0);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking remaining unsynced count: " + e.getMessage());
                    } finally {
                        if (c != null) c.close();
                    }

                    if (unsyncedCount == 0) {
                        try {
                            dbHelper.clearLocalData(false);
                            Log.d(TAG, "Post-sync: successfully purged local database cache of synced items.");
                        } catch (Exception e) {
                            Log.e(TAG, "Error purging local database cache: " + e.getMessage());
                        }
                        completeSync(listener, true, "All data synced and verified successfully!");
                    } else {
                        completeSync(listener, false, "Push verification incomplete. " + unsyncedCount + " items failed to sync.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Manual Push Sync exception: " + e.getMessage());
                    completeSync(listener, false, "Exception: " + e.getMessage());
                } finally {
                    releaseSyncLock();
                }
            }
        });
    }

    private void handleServerSessionExpired() {
        Log.e(TAG, "Server session expired or user is unauthorized! Clearing local session and redirecting to Login.");
        
        android.content.SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
        prefs.edit()
             .remove("user_id")
             .remove("username")
             .remove("employee_id")
             .remove("first_name")
             .remove("last_name")
             .apply();
             
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    android.widget.Toast.makeText(context, "Session Expired. Please login again.", android.widget.Toast.LENGTH_LONG).show();
                    android.content.Intent intent = new android.content.Intent(context, LoginActivity.class);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start LoginActivity: " + e.getLocalizedMessage());
                }
            }
        });
    }

    private void backupConflict(SQLiteDatabase db, String tableName, int recordId, String uuid, JSONObject localData, JSONObject serverData) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("table_name", tableName);
            cv.put("record_id", recordId);
            cv.put("uuid", uuid);
            cv.put("local_data", localData.toString());
            cv.put("server_data", serverData.toString());
            cv.put("resolved", 0);
            db.insert("conflict_backups", null, cv);
            Log.d(TAG, "Conflict backed up successfully for table " + tableName + ", ID: " + recordId);
        } catch (Exception e) {
            Log.e(TAG, "Error backing up conflict: " + e.getMessage());
        }
    }

    private void updateProgress(final SyncListener listener, final String message) {
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onSyncProgress(message);
                }
            });
        }
    }

    private void completeSync(final SyncListener listener, final boolean success, final String message) {
        if (listener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onSyncCompleted(success, message);
                }
            });
        }
    }
}
