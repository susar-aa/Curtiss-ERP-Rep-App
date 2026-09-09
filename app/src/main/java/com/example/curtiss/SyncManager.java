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
import android.util.JsonReader;
import android.util.JsonToken;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SyncManager {

    // Enhanced sync status constants
    public static final int SYNC_PENDING = 1; // Never synced
    public static final int SYNC_SYNCING = 2; // Currently being sent
    public static final int SYNC_SYNCED = 3; // Successfully synced
    public static final int SYNC_FAILED = 4; // Failed after max retries
    public static final int SYNC_CONFLICT = 5; // Conflict detected
    public static final int SYNC_MERGED = 6; // Auto-merged with server

    private static final String TAG = "SyncManager";
    private static SyncManager instance;
    private final Context context;
    private final DatabaseHelper dbHelper;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private volatile boolean isSyncing = false;
    private final java.util.concurrent.locks.ReentrantLock syncLock = new java.util.concurrent.locks.ReentrantLock();
    private String lastSyncError = "";
    private String lastServerSystemDate = null;

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
            androidx.work.PeriodicWorkRequest syncRequest = new androidx.work.PeriodicWorkRequest.Builder(
                    SyncWorker.class, 30, java.util.concurrent.TimeUnit.MINUTES)
                    .setConstraints(new androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build())
                    .build();
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "CurtissPeriodicSync",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest);
        } catch (Exception e) {
            Log.e(TAG, "Error enqueuing periodic sync: " + e.getMessage());
        }
    }

    public void enqueueInstantPushSync() {
        try {
            androidx.work.OneTimeWorkRequest pushRequest = new androidx.work.OneTimeWorkRequest.Builder(
                    PushWorker.class)
                    .setConstraints(new androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build())
                    .build();
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                    "CurtissInstantPush",
                    androidx.work.ExistingWorkPolicy.REPLACE, // If there's already one waiting, replace it
                    pushRequest);
        } catch (Exception e) {
            Log.e(TAG, "Error enqueuing instant push sync: " + e.getMessage());
        }
    }

    public static boolean shouldRunDailyFullSync(Context context) {
        android.content.SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
        String lastFullSyncDate = prefs.getString("last_full_sync_date", "");
        String currentDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
        if (!currentDate.equals(lastFullSyncDate)) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            if (hour >= 8) {
                return true;
            }
        }
        return false;
    }

    // Execute server Pull
    boolean executePull(Context context, int userId) {
        return executePull(context, userId, null, false);
    }

    boolean executePull(Context context, int userId, final SyncListener listener) {
        return executePull(context, userId, listener, false);
    }

    boolean executePull(Context context, int userId, final SyncListener listener, boolean isFullSync) {
        lastSyncError = "Unknown error";
        if (!isNetworkAvailable(context)) {
            lastSyncError = "Network unavailable";
            return false;
        }

        android.content.SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
        String baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");
        String lastSyncTimestamp = prefs.getString("last_sync_timestamp", "2000-01-01 00:00:00");
        String urlString = baseUrl + "/rep/RepDashboard/sync_pull?api_sync=1";
        try {
            if (isFullSync) {
                urlString += "&last_sync=" + java.net.URLEncoder.encode("", "UTF-8");
            } else {
                urlString += "&last_sync=" + java.net.URLEncoder.encode(lastSyncTimestamp, "UTF-8");
            }
        } catch (Exception e) {
            Log.e(TAG, "URLEncoder failed for lastSyncTimestamp: " + e.getMessage());
        }

        int maxRetries = 3;
        int attempt = 0;
        while (attempt < maxRetries) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                Log.d(TAG, "Pull Sync attempt " + attempt + " of " + maxRetries + " to URL: " + urlString);
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setUseCaches(false);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(90000);
                Log.d(TAG, "Pull Sync Request Method: " + conn.getRequestMethod());

                // Add Authorization Header with userId
                conn.setRequestProperty("X-User-ID", String.valueOf(userId));
                String apiTokenHeader = SecurePreferences.getSessionPrefs(context).getString("api_token", "");
                if (!apiTokenHeader.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiTokenHeader);
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Pull Sync Response Code: " + responseCode);

                // Log all response headers
                java.util.Map<String, java.util.List<String>> headers = conn.getHeaderFields();
                if (headers != null) {
                    for (java.util.Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
                        Log.d(TAG, "Pull Sync Response Header: " + entry.getKey() + " = " + entry.getValue());
                    }
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    lastServerSystemDate = null;
                    boolean success = parseAndSaveSyncData(context, conn.getInputStream(), userId, listener);
                    if (success) {
                        if (isFullSync) {
                            String currentDate = new java.text.SimpleDateFormat("yyyy-MM-dd",
                                    java.util.Locale.getDefault()).format(new java.util.Date());
                            prefs.edit().putString("last_full_sync_date", currentDate).apply();
                        }
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
                                            Cursor cursorImg = db.rawQuery(
                                                    "SELECT COUNT(*) FROM image_download_queue WHERE status = 'downloading'",
                                                    null);
                                            if (cursorImg.moveToFirst()) {
                                                totalImagesToDownload = cursorImg.getInt(0);
                                            }
                                            cursorImg.close();

                                            if (totalImagesToDownload > 0) {
                                                Log.d(TAG, "Pull sync (Async Image Monitor): " + totalImagesToDownload
                                                        + " images currently downloading.");
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
                                                    Cursor cursorImg2 = db.rawQuery(
                                                            "SELECT COUNT(*) FROM image_download_queue WHERE status = 'downloading'",
                                                            null);
                                                    if (cursorImg2.moveToFirst()) {
                                                        remaining = cursorImg2.getInt(0);
                                                    }
                                                    cursorImg2.close();

                                                    int downloaded = totalImagesToDownload - remaining;
                                                    int percent = (downloaded * 100) / totalImagesToDownload;
                                                    updateProgress(listener, "Downloading images: " + downloaded + " / "
                                                            + totalImagesToDownload + " (" + percent + "%)");
                                                }
                                            }
                                        } catch (Exception imgEx) {
                                            Log.e(TAG, "Error waiting for images to download asynchronously: "
                                                    + imgEx.getMessage());
                                        }
                                    }
                                }).start();
                            }
                        } catch (Exception imgEx) {
                            Log.e(TAG, "Error starting image queue download: " + imgEx.getMessage());
                        }

                        String newTimestamp = lastServerSystemDate != null && !lastServerSystemDate.trim().isEmpty()
                                ? lastServerSystemDate
                                : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                        .format(new java.util.Date());
                        prefs.edit().putString("last_sync_timestamp", newTimestamp).apply();
                        return true;
                    } else {
                        throw new Exception("Incremental streaming database save failed.");
                    }
                } else {
                    java.io.InputStream errStream = conn.getErrorStream();
                    String errText = "";
                    if (errStream != null) {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(errStream, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        reader.close();
                        errText = sb.toString().trim();
                    }
                    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED
                            || responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == 419) {
                        handleServerSessionExpired();
                        throw new Exception("Session Expired (HTTP " + responseCode + ")");
                    }
                    Log.e(TAG, "Server error during pull (HTTP " + responseCode + "): " + errText);
                    throw new Exception("HTTP Response Code " + responseCode + " - Error: "
                            + (errText.length() > 200 ? errText.substring(0, 200) : errText));
                }
            } catch (Exception e) {
                Log.e(TAG, "Pull Sync connection attempt " + attempt + " failed: " + e.getMessage(), e);
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
        return false;
    }

    private boolean parseAndSaveSyncData(Context context, InputStream inputStream, int userId, SyncListener listener) {
        DatabaseHelper dbHelper = DatabaseHelper.getInstance(context);
        int dbRetries = 5;
        int dbAttempt = 0;
        while (dbAttempt < dbRetries) {
            dbAttempt++;
            try {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.beginTransaction();
                try {
                    JsonReader reader = new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                    reader.beginObject();

                    int totalCustomers = 0;
                    int customerCount = 0;

                    List<Integer> activeProductIds = null;
                    List<Integer> activeCustomerIds = null;
                    List<Integer> activeInvoiceIds = null;

                    // Fix B-11: Track sync IDs in-memory instead of using SharedPreferences
                    final long[] syncTempLocalRouteId = { -1L };
                    final java.util.HashMap<Integer, Long> syncTempInvIdMap = new java.util.HashMap<>();
                    final java.util.HashSet<Long> syncTempDeletedInvItems = new java.util.HashSet<>();

                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        if (name.equals("unauthorized")) {
                            if (reader.nextBoolean()) {
                                handleServerSessionExpired();
                                return false;
                            }
                        } else if (name.equals("message")) {
                            String msg = reader.nextString();
                            if (msg.contains("Unauthorized")) {
                                handleServerSessionExpired();
                                return false;
                            }
                        } else if (name.equals("success")) {
                            reader.nextBoolean();
                        } else if (name.equals("total_customers")) {
                            totalCustomers = reader.nextInt();
                        } else if (name.equals("active_product_ids")) {
                            activeProductIds = new ArrayList<>();
                            reader.beginArray();
                            while (reader.hasNext()) {
                                activeProductIds.add(reader.nextInt());
                            }
                            reader.endArray();
                        } else if (name.equals("active_customer_ids")) {
                            activeCustomerIds = new ArrayList<>();
                            reader.beginArray();
                            while (reader.hasNext()) {
                                activeCustomerIds.add(reader.nextInt());
                            }
                            reader.endArray();
                        } else if (name.equals("products")) {
                            reader.beginArray();
                            updateProgress(listener, "Saving products...");
                            List<JSONObject> productsList = new ArrayList<>();
                            while (reader.hasNext()) {
                                productsList.add(parseJsonObject(reader));
                            }
                            reader.endArray();

                            for (int i = 0; i < productsList.size(); i++) {
                                if (i % 50 == 0 || i == productsList.size() - 1) {
                                    int percentage = (int) (((double) (i + 1) / productsList.size()) * 100);
                                    updateProgress(listener, "Saving products (" + percentage + "% - " + (i + 1) + "/"
                                            + productsList.size() + ")");
                                }
                                JSONObject p = productsList.get(i);
                                int id = p.getInt("id");
                                String pName = p.getString("name");
                                String catName = "General";
                                if (!p.isNull("category_name")) {
                                    String rawCat = p.optString("category_name", "General");
                                    if (rawCat != null && !rawCat.trim().isEmpty()
                                            && !rawCat.equalsIgnoreCase("null")) {
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
                                    if (rawImg != null && !rawImg.trim().isEmpty()
                                            && !rawImg.equalsIgnoreCase("null")) {
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

                                dbHelper.saveProduct(id, pName, catName, price, wholesale, costPrice, qty, reserved,
                                        imgUrl, sku, sampleCode, variationsJson, brand, description, status);

                                if (status.equalsIgnoreCase("inactive")) {
                                    db.delete("image_download_queue", "product_id = ?",
                                            new String[] { String.valueOf(id) });
                                } else {
                                    if (!imgUrl.isEmpty()) {
                                        ImageDownloadManager.getInstance(context).queueImageDownload(context, id,
                                                imgUrl);
                                    }
                                    // Queue variation images as well
                                    if (variationsJson != null && !variationsJson.trim().isEmpty()
                                            && !variationsJson.equals("[]")) {
                                        try {
                                            org.json.JSONArray varArray = new org.json.JSONArray(variationsJson);
                                            for (int v = 0; v < varArray.length(); v++) {
                                                org.json.JSONObject varObj = varArray.getJSONObject(v);
                                                String varImgUrl = varObj.optString("image_path", "");
                                                if (varImgUrl != null && !varImgUrl.trim().isEmpty()
                                                        && !varImgUrl.equalsIgnoreCase("null")) {
                                                    ImageDownloadManager.getInstance(context)
                                                            .queueImageDownload(context, id, varImgUrl);
                                                }
                                            }
                                        } catch (Exception e) {
                                            android.util.Log.e("SyncManager",
                                                    "Error parsing variations_json for image queue: " + e.getMessage());
                                        }
                                    }
                                }
                            }

                            try {
                                ImageDownloadManager.getInstance(context).cleanObsoleteImages(context);
                            } catch (Exception e) {
                                android.util.Log.e("SyncManager", "Obsolete image cleanup error: " + e.getMessage());
                            }

                        } else if (name.equals("categories")) {
                            reader.beginArray();
                            updateProgress(listener, "Saving categories...");
                            List<JSONObject> catsList = new ArrayList<>();
                            while (reader.hasNext()) {
                                catsList.add(parseJsonObject(reader));
                            }
                            reader.endArray();

                            for (int i = 0; i < catsList.size(); i++) {
                                if (i % 10 == 0 || i == catsList.size() - 1) {
                                    int percentage = (int) (((double) (i + 1) / catsList.size()) * 100);
                                    updateProgress(listener, "Saving categories (" + percentage + "% - " + (i + 1) + "/"
                                            + catsList.size() + ")");
                                }
                                JSONObject cObj = catsList.get(i);
                                int catId = cObj.getInt("id");
                                String cName = cObj.getString("name");
                                String status = cObj.optString("status", "active");

                                if (status.equalsIgnoreCase("inactive")) {
                                    db.delete("categories", "id = ?", new String[] { String.valueOf(catId) });
                                    db.delete("products", "category_name = ?", new String[] { cName });
                                } else {
                                    ContentValues cv = new ContentValues();
                                    cv.put("id", catId);
                                    cv.put("name", cName);
                                    cv.put("status", status);
                                    db.insertWithOnConflict("categories", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                                }
                            }
                        } else if (name.equals("customers")) {
                            reader.beginArray();
                            updateProgress(listener, "Saving customers...");

                            java.util.Set<Integer> modifiedLocalCustomerServerIds = new java.util.HashSet<>();
                            try {
                                Cursor cursor = db
                                        .rawQuery("SELECT server_id FROM customers WHERE is_profile_synced = 0", null);
                                while (cursor.moveToNext()) {
                                    modifiedLocalCustomerServerIds.add(cursor.getInt(0));
                                }
                                cursor.close();
                            } catch (Exception e) {
                                Log.e(TAG, "Error pre-checking customer profiles: " + e.getMessage());
                            }

                            while (reader.hasNext()) {
                                JSONObject c = parseJsonObject(reader);
                                int serverId = c.getInt("id");
                                customerCount++;
                                if (customerCount % 100 == 0 || customerCount == totalCustomers) {
                                    int percentage = totalCustomers > 0
                                            ? (int) (((double) customerCount / totalCustomers) * 100)
                                            : 0;
                                    updateProgress(listener, "Saving customers (" + percentage + "% - " + customerCount
                                            + "/" + (totalCustomers > 0 ? totalCustomers : "?") + ")");
                                }

                                if (modifiedLocalCustomerServerIds.contains(serverId)) {
                                    double outstanding = c.optDouble("outstanding",
                                            c.optDouble("outstanding_amount", c.optDouble("balance", 0.0)));
                                    ContentValues balanceCv = new ContentValues();
                                    balanceCv.put("outstanding", outstanding);
                                    db.update("customers", balanceCv, "server_id = ?",
                                            new String[] { String.valueOf(serverId) });
                                    continue;
                                }

                                String cName = c.getString("name");
                                String phone = c.optString("phone", "");
                                String wa = c.optString("whatsapp", "");
                                String address = c.optString("address", "");
                                String territory = c.optString("territory", "");
                                double lat = c.optDouble("latitude", 0.0);
                                double lng = c.optDouble("longitude", 0.0);
                                double outstanding = c.optDouble("outstanding",
                                        c.optDouble("outstanding_amount", c.optDouble("balance", 0.0)));
                                int mcaId = c.optInt("mca_id", 0);
                                String mcaName = c.optString("mca_name", "");
                                String email = c.optString("email", "");
                                double creditLimit = c.optDouble("credit_limit", 0.00);
                                String customerType = c.optString("customer_type", "Standard");
                                String notes = c.optString("notes", "");
                                String status = c.optString("status", "active");
                                String updatedAt = c.optString("updated_at", "");

                                ContentValues cv = new ContentValues();
                                cv.put("server_id", serverId);
                                cv.put("name", cName);
                                cv.put("phone", phone);
                                cv.put("whatsapp", wa);
                                cv.put("address", address);
                                cv.put("territory", territory);
                                cv.put("latitude", lat);
                                cv.put("longitude", lng);
                                cv.put("outstanding", outstanding);
                                cv.put("mca_id", mcaId > 0 ? mcaId : null);
                                cv.put("mca_name", mcaName);
                                cv.put("email", email);
                                cv.put("credit_limit", creditLimit);
                                cv.put("customer_type", customerType);
                                cv.put("notes", notes);
                                cv.put("status", status);
                                cv.put("updated_at", updatedAt);
                                cv.put("is_profile_synced", 1);
                                cv.put("is_synced", 1);
                                cv.put("sync_status", 3); // 3 = Synced

                                Cursor cursor = db.rawQuery("SELECT id FROM customers WHERE server_id = ?",
                                        new String[] { String.valueOf(serverId) });
                                if (cursor.moveToFirst()) {
                                    long localId = cursor.getLong(0);
                                    db.update("customers", cv, "id = ?", new String[] { String.valueOf(localId) });
                                } else {
                                    db.insert("customers", null, cv);
                                }
                                cursor.close();
                            }
                            reader.endArray();
                        } else if (name.equals("routes")) {
                            reader.beginArray();
                            updateProgress(listener, "Saving routes...");
                            db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS server_routes (id INTEGER PRIMARY KEY, name TEXT NOT NULL, main_area_id INTEGER DEFAULT 0, main_area_name TEXT, status TEXT DEFAULT 'active')");
                            db.execSQL("DELETE FROM server_routes");

                            List<JSONObject> routesList = new ArrayList<>();
                            while (reader.hasNext()) {
                                routesList.add(parseJsonObject(reader));
                            }
                            reader.endArray();

                            for (int i = 0; i < routesList.size(); i++) {
                                if (i % 10 == 0 || i == routesList.size() - 1) {
                                    int percentage = (int) (((double) (i + 1) / routesList.size()) * 100);
                                    updateProgress(listener, "Saving routes (" + percentage + "% - " + (i + 1) + "/"
                                            + routesList.size() + ")");
                                }
                                JSONObject r = routesList.get(i);
                                int routeId = r.getInt("id");
                                String routeName = r.getString("name");
                                int mainAreaId = r.optInt("main_area_id", 0);
                                String mainAreaName = r.optString("main_area_name", "");
                                String rStatus = r.optString("status", "active");

                                ContentValues rCv = new ContentValues();
                                rCv.put("id", routeId);
                                rCv.put("name", routeName);
                                rCv.put("main_area_id", mainAreaId);
                                rCv.put("main_area_name", mainAreaName);
                                rCv.put("status", rStatus);
                                db.insertWithOnConflict("server_routes", null, rCv, SQLiteDatabase.CONFLICT_REPLACE);
                            }
                        } else if (name.equals("reps")) {
                            reader.beginArray();
                            updateProgress(listener, "Saving representatives...");

                            String currentHash = null;
                            try {
                                Cursor hashCursor = db.rawQuery(
                                        "SELECT password_hash FROM representatives WHERE id = ?",
                                        new String[] { String.valueOf(userId) });
                                if (hashCursor.moveToFirst()) {
                                    currentHash = hashCursor.getString(0);
                                }
                                hashCursor.close();
                            } catch (Exception e) {
                                // Table may not exist yet
                            }

                            db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS representatives (id INTEGER PRIMARY KEY, username TEXT UNIQUE, password_hash TEXT, employee_id INTEGER, first_name TEXT, last_name TEXT)");
                            db.execSQL("DELETE FROM representatives");

                            List<JSONObject> repsList = new ArrayList<>();
                            while (reader.hasNext()) {
                                repsList.add(parseJsonObject(reader));
                            }
                            reader.endArray();

                            for (int i = 0; i < repsList.size(); i++) {
                                if (i % 10 == 0 || i == repsList.size() - 1) {
                                    int percentage = (int) (((double) (i + 1) / repsList.size()) * 100);
                                    updateProgress(listener, "Saving representatives (" + percentage + "% - " + (i + 1)
                                            + "/" + repsList.size() + ")");
                                }
                                JSONObject rep = repsList.get(i);
                                int repId = rep.getInt("id");
                                String username = rep.getString("username");
                                int employeeId = rep.getInt("employee_id");
                                String firstName = rep.optString("first_name", username);
                                String lastName = rep.optString("last_name", "");

                                ContentValues cv = new ContentValues();
                                cv.put("id", repId);
                                cv.put("username", username);
                                if (repId == userId && currentHash != null) {
                                    cv.put("password_hash", currentHash);
                                }
                                cv.put("employee_id", employeeId);
                                cv.put("first_name", firstName);
                                cv.put("last_name", lastName);
                                db.insertWithOnConflict("representatives", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                            }
                        } else if (name.equals("payment_terms")) {
                            reader.beginArray();
                            updateProgress(listener, "Saving payment terms...");
                            db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS payment_terms (id INTEGER PRIMARY KEY, name TEXT NOT NULL, days_due INTEGER DEFAULT 0)");
                            db.execSQL("DELETE FROM payment_terms");

                            List<JSONObject> termsList = new ArrayList<>();
                            while (reader.hasNext()) {
                                termsList.add(parseJsonObject(reader));
                            }
                            reader.endArray();

                            for (int i = 0; i < termsList.size(); i++) {
                                if (i % 5 == 0 || i == termsList.size() - 1) {
                                    int percentage = (int) (((double) (i + 1) / termsList.size()) * 100);
                                    updateProgress(listener, "Saving payment terms (" + percentage + "% - " + (i + 1)
                                            + "/" + termsList.size() + ")");
                                }
                                JSONObject t = termsList.get(i);
                                int termId = t.getInt("id");
                                String termName = t.getString("name");
                                int daysDue = t.getInt("days_due");

                                ContentValues cv = new ContentValues();
                                cv.put("id", termId);
                                cv.put("name", termName);
                                cv.put("days_due", daysDue);
                                db.insertWithOnConflict("payment_terms", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                            }
                        } else if (name.equals("credit_invoices")) {
                            reader.beginArray();
                            updateProgress(listener, "Saving credit invoices...");
                            db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS credit_invoices (id INTEGER PRIMARY KEY, invoice_number TEXT NOT NULL, customer_id INTEGER, invoice_date TEXT, true_grand_total REAL, customer_name TEXT, customer_address TEXT)");
                            db.execSQL("DELETE FROM credit_invoices");

                            List<JSONObject> cInvsList = new ArrayList<>();
                            while (reader.hasNext()) {
                                cInvsList.add(parseJsonObject(reader));
                            }
                            reader.endArray();

                            for (int i = 0; i < cInvsList.size(); i++) {
                                if (i % 50 == 0 || i == cInvsList.size() - 1) {
                                    int percentage = (int) (((double) (i + 1) / cInvsList.size()) * 100);
                                    updateProgress(listener, "Saving credit invoices (" + percentage + "% - " + (i + 1)
                                            + "/" + cInvsList.size() + ")");
                                }
                                JSONObject cObj = cInvsList.get(i);
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
                        } else if (name.equals("active_route")) {
                            if (reader.peek() != android.util.JsonToken.NULL) {
                                JSONObject act = parseJsonObject(reader);
                                int serverRouteId = act.getInt("id");
                                String routeName = act.getString("route_name");
                                double startMeter = act.optDouble("start_meter", 0.0);
                                String startTime = act.optString("start_time", "");
                                double startLat = act.optDouble("start_lat", 0.0);
                                double startLng = act.optDouble("start_lng", 0.0);
                                String status = act.optString("status", "Active");

                                Cursor routeCursor = db.rawQuery(
                                        "SELECT id, is_synced FROM daily_routes WHERE server_id = ?",
                                        new String[] { String.valueOf(serverRouteId) });
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
                                    if (localIsSynced == 1) {
                                        db.update("daily_routes", rCv, "id = ?",
                                                new String[] { String.valueOf(localRouteId) });
                                    }
                                } else {
                                    localRouteId = db.insert("daily_routes", null, rCv);
                                }
                                routeCursor.close();

                                // Fix B-11: store local route ID in-memory instead of SharedPreferences
                                syncTempLocalRouteId[0] = localRouteId;
                            } else {
                                reader.nextNull();
                            }
                        } else if (name.equals("active_route_invoices")) {
                            reader.beginArray();
                            updateProgress(listener, "Saving route invoices...");

                            long localRouteId = syncTempLocalRouteId[0]; // Fix B-11: read from in-memory map
                            List<JSONObject> invsList = new ArrayList<>();
                            while (reader.hasNext()) {
                                invsList.add(parseJsonObject(reader));
                            }
                            reader.endArray();

                            activeInvoiceIds = new ArrayList<>();
                            for (int j = 0; j < invsList.size(); j++) {
                                if (j % 10 == 0 || j == invsList.size() - 1) {
                                    int percentage = (int) (((double) (j + 1) / invsList.size()) * 100);
                                    updateProgress(listener, "Saving route invoices (" + percentage + "% - " + (j + 1)
                                            + "/" + invsList.size() + ")");
                                }
                                JSONObject invObj = invsList.get(j);
                                int serverInvId = invObj.getInt("id");
                                activeInvoiceIds.add(serverInvId);
                                String invNumber = invObj.getString("invoice_number");
                                int serverCustId = invObj.getInt("customer_id");
                                String invDate = invObj.optString("invoice_date", "");
                                String dueDate = invObj.optString("due_date", "");
                                int payTermId = invObj.optInt("payment_term_id", 0);
                                double subtotal = invObj.optDouble("total_amount", 0.0);
                                double discountRate = invObj.optDouble("global_discount_val", 0.0);
                                String discountType = invObj.optString("global_discount_type", "Rs");
                                double discountAmt = "%".equals(discountType) ? (subtotal * discountRate / 100.0)
                                        : discountRate;
                                double tax = invObj.optDouble("tax_amount", 0.0);
                                double grandTotal = subtotal - discountAmt + tax;

                                int localCustId = serverCustId;
                                Cursor custCursor = db.rawQuery("SELECT id FROM customers WHERE server_id = ?",
                                        new String[] { String.valueOf(serverCustId) });
                                if (custCursor.moveToFirst()) {
                                    localCustId = custCursor.getInt(0);
                                }
                                custCursor.close();

                                Cursor invCursor = db.rawQuery("SELECT id, is_synced FROM invoices WHERE server_id = ?",
                                        new String[] { String.valueOf(serverInvId) });
                                ContentValues iCv = new ContentValues();
                                iCv.put("server_id", serverInvId);
                                iCv.put("invoice_number", invNumber);
                                iCv.put("customer_id", localCustId);
                                if (localRouteId != -1) {
                                    iCv.put("route_id", localRouteId);
                                }
                                iCv.put("invoice_date", invDate);
                                iCv.put("due_date", dueDate);
                                iCv.put("payment_term_id", payTermId > 0 ? payTermId : null);
                                iCv.put("subtotal", subtotal);
                                iCv.put("discount", discountAmt);
                                iCv.put("discount_type", discountType);
                                iCv.put("discount_rate", discountRate);
                                iCv.put("tax", tax);
                                iCv.put("grand_total", grandTotal);
                                String payMethod = "Credit";
                                if (payTermId > 0) {
                                    Cursor termCursor = db.rawQuery("SELECT name FROM payment_terms WHERE id = ?",
                                            new String[] { String.valueOf(payTermId) });
                                    if (termCursor.moveToFirst()) {
                                        payMethod = termCursor.getString(0);
                                    }
                                    termCursor.close();
                                }
                                iCv.put("payment_method", payMethod);
                                iCv.put("is_synced", 1);
                                iCv.put("sync_status", 3);

                                long localInvId;
                                if (invCursor.moveToFirst()) {
                                    localInvId = invCursor.getLong(0);
                                    int localInvSynced = invCursor.getInt(1);
                                    if (localInvSynced == 1) {
                                        db.update("invoices", iCv, "id = ?",
                                                new String[] { String.valueOf(localInvId) });
                                    }
                                } else {
                                    localInvId = db.insert("invoices", null, iCv);
                                }
                                invCursor.close();

                                // Fix B-11: store invoice ID mapping in-memory instead of SharedPreferences
                                syncTempInvIdMap.put(serverInvId, localInvId);
                            }
                        } else if (name.equals("active_route_invoice_items")) {
                            reader.beginArray();
                            while (reader.hasNext()) {
                                JSONObject itemObj = parseJsonObject(reader);
                                int serverInvId = itemObj.getInt("invoice_id");
                                // Fix B-11: read from in-memory map
                                Long localInvId = syncTempInvIdMap.get(serverInvId);
                                if (localInvId != null && localInvId != -1) {
                                    // Fix B-11: use in-memory set to track which invoices already had items deleted
                                    if (!syncTempDeletedInvItems.contains(localInvId)) {
                                        db.delete("invoice_items", "invoice_id = ?",
                                                new String[] { String.valueOf(localInvId) });
                                        syncTempDeletedInvItems.add(localInvId);
                                    }

                                    double unitPrice = itemObj.getDouble("unit_price");
                                    int qty = itemObj.getInt("quantity");
                                    double itemSubtotal = qty * unitPrice;
                                    double itemDiscRate = itemObj.optDouble("discount_value", 0.0);
                                    String itemDiscType = itemObj.optString("discount_type", "Rs");
                                    double itemDiscVal = "%".equals(itemDiscType)
                                            ? (itemSubtotal * itemDiscRate / 100.0)
                                            : itemDiscRate;

                                    ContentValues itCv = new ContentValues();
                                    itCv.put("invoice_id", localInvId);
                                    itCv.put("product_id", itemObj.getInt("item_id"));
                                    String desc = itemObj.getString("description");
                                    itCv.put("product_name", desc);
                                    itCv.put("quantity", qty);
                                    itCv.put("unit_price", unitPrice);
                                    itCv.put("discount_val", itemDiscVal);
                                    itCv.put("discount_type", itemDiscType);
                                    itCv.put("discount_rate", itemDiscRate);
                                    itCv.put("total", itemObj.getDouble("total"));

                                    int varOptId = itemObj.optInt("variation_option_id", 0);
                                    itCv.put("variation_option_id", varOptId);

                                    String selectedVar = "";
                                    if (desc.contains(" - ")) {
                                        selectedVar = desc.substring(desc.lastIndexOf(" - ") + 3);
                                    }
                                    itCv.put("selected_variation", selectedVar);

                                    db.insert("invoice_items", null, itCv);
                                }
                            }
                            reader.endArray();
                        } else if (name.equals("discount_rules")) {
                            reader.beginArray();
                            updateProgress(listener, "Saving promotional discount rules...");
                            db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS discount_rules (id INTEGER PRIMARY KEY, name TEXT NOT NULL, rule_type TEXT NOT NULL, reward_type TEXT DEFAULT 'free_issue', target_item_id INTEGER, target_category_id INTEGER, start_date TEXT, end_date TEXT, discount_cap REAL, status TEXT)");
                            try {
                                db.execSQL(
                                        "ALTER TABLE discount_rules ADD COLUMN reward_type TEXT DEFAULT 'free_issue'");
                            } catch (Exception e) {
                            }
                            try {
                                db.execSQL("ALTER TABLE discount_rules ADD COLUMN target_category_id INTEGER");
                            } catch (Exception e) {
                            }
                            try {
                                db.execSQL("ALTER TABLE discount_rules ADD COLUMN start_date TEXT");
                            } catch (Exception e) {
                            }
                            try {
                                db.execSQL("ALTER TABLE discount_rules ADD COLUMN end_date TEXT");
                            } catch (Exception e) {
                            }
                            try {
                                db.execSQL("ALTER TABLE discount_rules ADD COLUMN discount_cap REAL");
                            } catch (Exception e) {
                            }
                            db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS discount_rule_tiers (id INTEGER PRIMARY KEY, rule_id INTEGER, min_threshold REAL, max_threshold REAL, reward_val REAL)");
                            db.execSQL("DELETE FROM discount_rules");
                            db.execSQL("DELETE FROM discount_rule_tiers");

                            while (reader.hasNext()) {
                                JSONObject rObj = parseJsonObject(reader);
                                int ruleId = rObj.getInt("id");
                                String rName = rObj.getString("name");
                                String rType = rObj.getString("rule_type");
                                String rewardType = rObj.optString("reward_type", "free_issue");
                                int targetItemId = rObj.optInt("target_item_id", 0);
                                int targetCatId = rObj.optInt("target_category_id", 0);
                                String startDate = rObj.optString("start_date", "");
                                String endDate = rObj.optString("end_date", "");
                                double discountCap = rObj.optDouble("discount_cap", 0.0);
                                String status = rObj.optString("status", "Active");

                                ContentValues rCv = new ContentValues();
                                rCv.put("id", ruleId);
                                rCv.put("name", rName);
                                rCv.put("rule_type", rType);
                                rCv.put("reward_type", rewardType);
                                if (targetItemId > 0) {
                                    rCv.put("target_item_id", targetItemId);
                                } else {
                                    rCv.putNull("target_item_id");
                                }
                                if (targetCatId > 0) {
                                    rCv.put("target_category_id", targetCatId);
                                } else {
                                    rCv.putNull("target_category_id");
                                }
                                if (!startDate.isEmpty() && !startDate.equalsIgnoreCase("null")) {
                                    rCv.put("start_date", startDate);
                                } else {
                                    rCv.putNull("start_date");
                                }
                                if (!endDate.isEmpty() && !endDate.equalsIgnoreCase("null")) {
                                    rCv.put("end_date", endDate);
                                } else {
                                    rCv.putNull("end_date");
                                }
                                if (discountCap > 0) {
                                    rCv.put("discount_cap", discountCap);
                                } else {
                                    rCv.putNull("discount_cap");
                                }
                                rCv.put("status", status);
                                db.insertWithOnConflict("discount_rules", null, rCv, SQLiteDatabase.CONFLICT_REPLACE);

                                if (rObj.has("tiers") && !rObj.isNull("tiers")) {
                                    JSONArray tiersArr = rObj.getJSONArray("tiers");
                                    for (int k = 0; k < tiersArr.length(); k++) {
                                        JSONObject tObj = tiersArr.getJSONObject(k);
                                        ContentValues tCv = new ContentValues();
                                        tCv.put("id", tObj.getInt("id"));
                                        tCv.put("rule_id", tObj.getInt("rule_id"));
                                        tCv.put("min_threshold", tObj.optDouble("min_threshold", 0.0));
                                        if (tObj.isNull("max_threshold")) {
                                            tCv.putNull("max_threshold");
                                        } else {
                                            tCv.put("max_threshold", tObj.optDouble("max_threshold", 0.0));
                                        }
                                        tCv.put("reward_val", tObj.optDouble("reward_val", 0.0));
                                        db.insertWithOnConflict("discount_rule_tiers", null, tCv,
                                                SQLiteDatabase.CONFLICT_REPLACE);
                                    }
                                }
                            }
                            reader.endArray();
                        } else if (name.equals("system_date")) {
                            if (reader.peek() != android.util.JsonToken.NULL) {
                                lastServerSystemDate = reader.nextString();
                            } else {
                                reader.nextNull();
                            }
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                    reader.close();

                    // Clean up deleted products
                    if (activeProductIds != null) {
                        Cursor cursorProd = db.rawQuery("SELECT id FROM products", null);
                        List<Integer> localProductIds = new ArrayList<>();
                        while (cursorProd.moveToNext()) {
                            localProductIds.add(cursorProd.getInt(0));
                        }
                        cursorProd.close();

                        for (int localId : localProductIds) {
                            if (!activeProductIds.contains(localId)) {
                                db.delete("products", "id = ?", new String[] { String.valueOf(localId) });
                                db.delete("image_download_queue", "product_id = ?",
                                        new String[] { String.valueOf(localId) });
                            }
                        }
                    }

                    // Clean up deleted customers
                    if (activeCustomerIds != null) {
                        Cursor cursorCust = db.rawQuery("SELECT server_id FROM customers WHERE server_id > 0", null);
                        List<Integer> localCustServerIds = new ArrayList<>();
                        while (cursorCust.moveToNext()) {
                            localCustServerIds.add(cursorCust.getInt(0));
                        }
                        cursorCust.close();

                        for (int serverId : localCustServerIds) {
                            if (!activeCustomerIds.contains(serverId)) {
                                db.delete("customers", "server_id = ?", new String[] { String.valueOf(serverId) });
                            }
                        }
                    }

                    // Clean up deleted/voided invoices (only if they were already synced)
                    if (activeInvoiceIds != null) {
                        Cursor cursorInv = db.rawQuery(
                                "SELECT id, server_id FROM invoices WHERE is_synced = 1 AND server_id > 0", null);
                        List<Long> localInvIdsToDelete = new ArrayList<>();
                        while (cursorInv.moveToNext()) {
                            long localId = cursorInv.getLong(0);
                            int serverId = cursorInv.getInt(1);
                            if (!activeInvoiceIds.contains(serverId)) {
                                localInvIdsToDelete.add(localId);
                            }
                        }
                        cursorInv.close();

                        for (long localId : localInvIdsToDelete) {
                            db.delete("invoices", "id = ?", new String[] { String.valueOf(localId) });
                            db.delete("invoice_items", "invoice_id = ?", new String[] { String.valueOf(localId) });
                            db.delete("sync_logs", "bill_id = ?", new String[] { String.valueOf(localId) });
                        }
                    }

                    db.setTransactionSuccessful();
                    Log.d(TAG, "Pull Sync Transaction Successful");
                    return true;
                } finally {
                    db.endTransaction();
                }
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().contains("locked") || e.getMessage().contains("BUSY")
                        || e.getMessage().contains("code 5"))) {
                    Log.w(TAG, "Database is locked during pull insertion. Retrying...");
                    try {
                        Thread.sleep(200 * dbAttempt);
                    } catch (Exception ignored) {
                    }
                } else {
                    Log.e(TAG, "Error in incremental sync: " + e.getMessage(), e);
                    return false;
                }
            }
        }
        return false;
    }

    private static JSONObject parseJsonObject(JsonReader reader) throws Exception {
        JSONObject obj = new JSONObject();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (reader.peek() == android.util.JsonToken.NULL) {
                reader.nextNull();
                obj.put(name, JSONObject.NULL);
            } else if (reader.peek() == android.util.JsonToken.BOOLEAN) {
                obj.put(name, reader.nextBoolean());
            } else if (reader.peek() == android.util.JsonToken.NUMBER) {
                double val = reader.nextDouble();
                if (val == (long) val) {
                    obj.put(name, (long) val);
                } else {
                    obj.put(name, val);
                }
            } else if (reader.peek() == android.util.JsonToken.STRING) {
                obj.put(name, reader.nextString());
            } else if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                obj.put(name, parseJsonObject(reader));
            } else if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                obj.put(name, parseJsonArray(reader));
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return obj;
    }

    private static JSONArray parseJsonArray(JsonReader reader) throws Exception {
        JSONArray arr = new JSONArray();
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() == android.util.JsonToken.NULL) {
                reader.nextNull();
                arr.put(JSONObject.NULL);
            } else if (reader.peek() == android.util.JsonToken.BOOLEAN) {
                arr.put(reader.nextBoolean());
            } else if (reader.peek() == android.util.JsonToken.NUMBER) {
                double val = reader.nextDouble();
                if (val == (long) val) {
                    arr.put((long) val);
                } else {
                    arr.put(val);
                }
            } else if (reader.peek() == android.util.JsonToken.STRING) {
                arr.put(reader.nextString());
            } else if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                arr.put(parseJsonObject(reader));
            } else if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                arr.put(parseJsonArray(reader));
            } else {
                reader.skipValue();
            }
        }
        reader.endArray();
        return arr;
    }

    // Two-phase commit wrapper for push sync
    private void syncFCMToken(Context context, int userId) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE);
        boolean isSynced = prefs.getBoolean("token_synced", true);
        String token = prefs.getString("fcm_token", "");
        if (isSynced || token.isEmpty())
            return;

        android.content.SharedPreferences sessionPrefs = SecurePreferences.getSessionPrefs(context);
        String baseUrl = sessionPrefs.getString("base_url", "https://curtiss.suzxlabs.com");
        String urlString = baseUrl + "/rep/RepDashboard/update_fcm_token";

        try {
            JSONObject payload = new JSONObject();
            payload.put("fcm_token", token);

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("X-User-ID", String.valueOf(userId));
            String apiTokenHeader = SecurePreferences.getSessionPrefs(context).getString("api_token", "");
            if (!apiTokenHeader.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiTokenHeader);
            }
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                prefs.edit().putBoolean("token_synced", true).apply();
                Log.d(TAG, "FCM token synced successfully.");
            } else {
                Log.e(TAG, "Failed to sync FCM token, HTTP " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error syncing FCM token: " + e.getMessage());
        }
    }

    public boolean executePushSafe(Context context, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Pre-check: Check if there is anything to push before marking/initiating
        // transactions
        boolean hasPending = false;
        try {
            Cursor cPending = db.rawQuery(
                    "SELECT 1 FROM invoices WHERE is_synced = 0 OR sync_status IN (1, 4) " +
                            "UNION SELECT 1 FROM customers WHERE is_synced = 0 OR sync_status IN (1, 4) " +
                            "UNION SELECT 1 FROM daily_routes WHERE is_synced = 0 OR sync_status IN (1, 4) " +
                            "UNION SELECT 1 FROM payments WHERE is_synced = 0 OR sync_status IN (1, 4) LIMIT 1",
                    null);
            if (cPending != null) {
                hasPending = cPending.moveToFirst();
                cPending.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking pending push status: " + e.getMessage());
        }
        if (!hasPending) {
            Log.d(TAG, "No pending records to push. Skipping push sync.");
            return true;
        }

        // PHASE 1: Prepare - (Removed) We no longer blindly mark records as "syncing" (2).
        // executePush() safely reads pending (1) and failed (4) records directly.
        // This prevents locally modified records from falsely upgrading to Synced (3).

        // Sync FCM Token before data push
        syncFCMToken(context, userId);

        // PHASE 2: Execute sync
        boolean success = executePush(context, userId);

        // PHASE 3: Finalize - (Removed) executePush() already updates mapped records to 3
        // and unmapped records to 4. We no longer blindly upgrade state 2 records to 3.
        // If the push fails, records safely remain in their original state (1 or 4).

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
            java.util.List<Integer> attemptedCustomerIds = new java.util.ArrayList<>();
            Cursor custCursor = db.rawQuery(
                    "SELECT * FROM customers WHERE (is_synced = 0 OR sync_status IN (1, 2, 4)) AND (server_id = 0 OR is_profile_synced = 0)",
                    null);
            while (custCursor.moveToNext()) {
                JSONObject c = new JSONObject();
                int localCustId = DatabaseHelper.safeGetInt(custCursor, "id", 0);
                attemptedCustomerIds.add(localCustId);
                c.put("local_id", localCustId);
                c.put("server_id", DatabaseHelper.safeGetInt(custCursor, "server_id", 0));
                c.put("name", DatabaseHelper.safeGetString(custCursor, "name", ""));
                c.put("phone", DatabaseHelper.safeGetString(custCursor, "phone", ""));
                c.put("whatsapp", DatabaseHelper.safeGetString(custCursor, "whatsapp", ""));
                c.put("address", DatabaseHelper.safeGetString(custCursor, "address", ""));
                c.put("territory", DatabaseHelper.safeGetString(custCursor, "territory", ""));
                c.put("latitude", DatabaseHelper.safeGetDouble(custCursor, "latitude", 0.0));
                c.put("longitude", DatabaseHelper.safeGetDouble(custCursor, "longitude", 0.0));
                c.put("uuid", DatabaseHelper.safeGetString(custCursor, "uuid", ""));
                c.put("sync_source", DatabaseHelper.safeGetString(custCursor, "sync_source", ""));
                c.put("mca_name", DatabaseHelper.safeGetString(custCursor, "mca_name", ""));
                custArray.put(c);
            }
            custCursor.close();
            payload.put("customers", custArray);

            // 2. Fetch local unsynced Routes
            JSONArray routeArray = new JSONArray();
            java.util.List<Integer> attemptedRouteIds = new java.util.ArrayList<>();
            Cursor rCursor = db.rawQuery("SELECT * FROM daily_routes WHERE is_synced = 0 OR sync_status IN (1, 2, 4)",
                    null);
            while (rCursor.moveToNext()) {
                JSONObject r = new JSONObject();
                int localRouteId = DatabaseHelper.safeGetInt(rCursor, "id", 0);
                attemptedRouteIds.add(localRouteId);
                r.put("local_id", localRouteId);
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
            Cursor invCursor = db.rawQuery("SELECT * FROM invoices WHERE is_synced = 0 OR sync_status IN (1, 2, 4)",
                    null);
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
                Cursor cCust = db.rawQuery("SELECT server_id FROM customers WHERE id = ?",
                        new String[] { String.valueOf(localCustId) });
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
                Cursor cRoute = db.rawQuery("SELECT server_id FROM daily_routes WHERE id = ?",
                        new String[] { String.valueOf(localRouteId) });
                if (cRoute.moveToFirst()) {
                    serverRouteId = cRoute.getInt(0);
                }
                cRoute.close();
                inv.put("server_route_id", serverRouteId);

                // Track associated route_uuid in payload for precise server side route matching
                String routeUuid = "";
                Cursor cRouteUuid = db.rawQuery("SELECT uuid FROM daily_routes WHERE id = ?",
                        new String[] { String.valueOf(localRouteId) });
                if (cRouteUuid.moveToFirst()) {
                    routeUuid = cRouteUuid.getString(0);
                }
                cRouteUuid.close();
                inv.put("route_uuid", routeUuid);

                Log.d(TAG,
                        "SyncManager: Staging invoice " + DatabaseHelper.safeGetString(invCursor, "invoice_number", "")
                                + " (local_id: " + localInvId + ", local_route_id: " + localRouteId
                                + ", server_route_id: " + serverRouteId + ", route_uuid: " + routeUuid
                                + ", server_customer_id: " + serverCustId + ")");

                inv.put("invoice_date", DatabaseHelper.safeGetString(invCursor, "invoice_date", ""));
                inv.put("due_date", DatabaseHelper.safeGetString(invCursor, "due_date", ""));
                inv.put("subtotal", DatabaseHelper.safeGetDouble(invCursor, "subtotal", 0.0));

                double discountAmt = DatabaseHelper.safeGetDouble(invCursor, "discount", 0.0);
                double discountRate = DatabaseHelper.safeGetDouble(invCursor, "discount_rate", 0.0);
                String discountType = DatabaseHelper.safeGetString(invCursor, "discount_type", "Rs");
                if (discountRate == 0.0 && discountAmt > 0.0) {
                    discountRate = discountAmt;
                    discountType = "Rs";
                }
                inv.put("discount", discountRate);
                inv.put("discount_type", discountType);
                inv.put("global_discount_type", discountType);

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
                Cursor itemCursor = db.rawQuery("SELECT * FROM invoice_items WHERE invoice_id = ?",
                        new String[] { String.valueOf(localInvId) });
                while (itemCursor.moveToNext()) {
                    JSONObject item = new JSONObject();
                    item.put("product_id", DatabaseHelper.safeGetInt(itemCursor, "product_id", 0));
                    item.put("product_name", DatabaseHelper.safeGetString(itemCursor, "product_name", ""));
                    item.put("quantity", DatabaseHelper.safeGetInt(itemCursor, "quantity", 0));
                    item.put("unit_price", DatabaseHelper.safeGetDouble(itemCursor, "unit_price", 0.0));
                    item.put("variation_option_id", DatabaseHelper.safeGetInt(itemCursor, "variation_option_id", 0));

                    double itemDiscVal = DatabaseHelper.safeGetDouble(itemCursor, "discount_val", 0.0);
                    double itemDiscRate = DatabaseHelper.safeGetDouble(itemCursor, "discount_rate", 0.0);
                    String itemDiscType = DatabaseHelper.safeGetString(itemCursor, "discount_type", "Rs");
                    if (itemDiscRate == 0.0 && itemDiscVal > 0.0) {
                        itemDiscRate = itemDiscVal;
                        itemDiscType = "Rs";
                    }
                    item.put("discount_val", itemDiscRate);
                    item.put("discount_type", itemDiscType);

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
            java.util.List<Integer> attemptedPaymentIds = new java.util.ArrayList<>();
            try {
                Cursor payCursor = db.rawQuery("SELECT * FROM payments WHERE is_synced = 0 OR sync_status IN (1, 2, 4)",
                        null);
                while (payCursor.moveToNext()) {
                    JSONObject payObj = new JSONObject();
                    int localPayId = DatabaseHelper.safeGetInt(payCursor, "id", 0);
                    attemptedPaymentIds.add(localPayId);
                    payObj.put("local_id", localPayId);
                    payObj.put("uuid", DatabaseHelper.safeGetString(payCursor, "uuid", ""));
                    payObj.put("customer_id", DatabaseHelper.safeGetInt(payCursor, "customer_id", 0));
                    int serverRouteId = DatabaseHelper.safeGetInt(payCursor, "server_route_id", 0);
                    int localRouteId = DatabaseHelper.safeGetInt(payCursor, "local_route_id", 0);
                    if (serverRouteId <= 0 && localRouteId > 0) {
                        Cursor cRouteServerId = db.rawQuery("SELECT server_id FROM daily_routes WHERE id = ?",
                                new String[] { String.valueOf(localRouteId) });
                        if (cRouteServerId.moveToFirst()) {
                            serverRouteId = cRouteServerId.getInt(0);
                        }
                        cRouteServerId.close();
                    }
                    payObj.put("server_route_id", serverRouteId);
                    payObj.put("local_route_id", localRouteId);

                    // Track associated route_uuid in payload for precise server-side route matching
                    String routeUuid = "";
                    if (localRouteId > 0) {
                        Cursor cRouteUuid = db.rawQuery("SELECT uuid FROM daily_routes WHERE id = ?",
                                new String[] { String.valueOf(localRouteId) });
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

            // 5. Fetch local unsynced Unproductive Visits
            JSONArray unprodArray = new JSONArray();
            java.util.List<String> attemptedUnproductiveUuids = new java.util.ArrayList<>();
            try {
                Cursor unprodCursor = db.rawQuery("SELECT * FROM unproductive_visits WHERE sync_status IN (0, 1, 2, 4)",
                        null);
                while (unprodCursor.moveToNext()) {
                    JSONObject unprodObj = new JSONObject();
                    String uuid = DatabaseHelper.safeGetString(unprodCursor, "uuid", "");
                    attemptedUnproductiveUuids.add(uuid);
                    unprodObj.put("uuid", uuid);
                    unprodObj.put("local_id", DatabaseHelper.safeGetInt(unprodCursor, "id", 0));

                    int localCustId = DatabaseHelper.safeGetInt(unprodCursor, "customer_id", 0);
                    int serverCustId = localCustId;
                    Cursor cCust = db.rawQuery("SELECT server_id FROM customers WHERE id = ?",
                            new String[] { String.valueOf(localCustId) });
                    if (cCust.moveToFirst()) {
                        int sid = cCust.getInt(0);
                        if (sid > 0)
                            serverCustId = sid;
                    }
                    cCust.close();
                    unprodObj.put("customer_id", serverCustId);

                    int localRouteId = DatabaseHelper.safeGetInt(unprodCursor, "route_id", 0);
                    int serverRouteId = 0;
                    Cursor cRoute = db.rawQuery("SELECT server_id FROM daily_routes WHERE id = ?",
                            new String[] { String.valueOf(localRouteId) });
                    if (cRoute.moveToFirst()) {
                        serverRouteId = cRoute.getInt(0);
                    }
                    cRoute.close();
                    unprodObj.put("local_route_id", localRouteId);
                    unprodObj.put("server_route_id", serverRouteId);

                    unprodObj.put("reason", DatabaseHelper.safeGetString(unprodCursor, "reason", ""));
                    unprodObj.put("custom_reason", DatabaseHelper.safeGetString(unprodCursor, "custom_reason", ""));
                    unprodObj.put("latitude", DatabaseHelper.safeGetDouble(unprodCursor, "latitude", 0.0));
                    unprodObj.put("longitude", DatabaseHelper.safeGetDouble(unprodCursor, "longitude", 0.0));
                    unprodObj.put("visit_time", DatabaseHelper.safeGetString(unprodCursor, "visit_time", ""));
                    unprodArray.put(unprodObj);
                }
                unprodCursor.close();
            } catch (Exception e) {
                Log.e(TAG, "Error staging unproductive visits: " + e.getMessage());
            }
            payload.put("unproductive_visits", unprodArray);

            // Skip API post if there's nothing to upload
            if (custArray.length() == 0 && routeArray.length() == 0 && invArray.length() == 0 && payArray.length() == 0
                    && unprodArray.length() == 0) {
                return true;
            }

            // POST unified payload to Plesk Sync API
            android.content.SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
            String baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");
            String urlString = baseUrl + "/rep/RepDashboard/sync_push?api_sync=1";
            Log.d(TAG, "Starting Push Sync POST to: " + urlString);
            // S-02 Fix: Only log full payload in debug builds to prevent customer data
            // exposure
            if (com.example.curtiss.BuildConfig.DEBUG) {
                Log.d(TAG, "Push Payload details: " + payload.toString());
            } else {
                Log.d(TAG, "Push Payload: [" + payload.length() + " chars, debug logging disabled in release]");
            }

            responseBody = null;
            int maxRetries = 3;
            int attempt = 0;
            byte[] jsonBytes = payload.toString().getBytes(StandardCharsets.UTF_8);

            while (attempt < maxRetries) {
                attempt++;
                HttpURLConnection conn = null;
                try {
                    Log.d(TAG, "Push Sync attempt " + attempt + " of " + maxRetries + " to URL: " + urlString);
                    URL url = new URL(urlString);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("X-User-ID", String.valueOf(userId));
                    String apiTokenHeader = SecurePreferences.getSessionPrefs(context).getString("api_token", "");
                    if (!apiTokenHeader.isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + apiTokenHeader);
                    }
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    OutputStream os = conn.getOutputStream();
                    os.write(jsonBytes, 0, jsonBytes.length);
                    os.flush();
                    os.close();

                    int responseCode = conn.getResponseCode();
                    Log.d(TAG, "Push Sync Response Code: " + responseCode);

                    // Log all response headers
                    java.util.Map<String, java.util.List<String>> headers = conn.getHeaderFields();
                    if (headers != null) {
                        for (java.util.Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
                            Log.d(TAG, "Push Sync Response Header: " + entry.getKey() + " = " + entry.getValue());
                        }
                    }

                    BufferedReader reader;
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    } else {
                        java.io.InputStream errStream = conn.getErrorStream();
                        if (errStream != null) {
                            Log.d(TAG, "Push Sync Error Stream is available.");
                            reader = new BufferedReader(new InputStreamReader(errStream, StandardCharsets.UTF_8));
                        } else {
                            Log.w(TAG, "Push Sync Error Stream is NULL.");
                            reader = null;
                        }
                    }

                    if (reader != null) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        reader.close();

                        String resText = sb.toString().trim();
                        Log.d(TAG, "Push Sync Raw Response (length=" + resText.length() + "): " + resText);

                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            if (!resText.startsWith("{") && !resText.startsWith("[")) {
                                Log.e(TAG, "Push Sync response is not valid JSON. Response starts with: "
                                        + (resText.length() > 100 ? resText.substring(0, 100) : resText));
                                throw new Exception("Server response is not valid JSON. Starts with: "
                                        + (resText.length() > 60 ? resText.substring(0, 60) : resText));
                            }
                            responseBody = resText;
                            break;
                        } else {
                            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED
                                    || responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == 419) {
                                handleServerSessionExpired();
                                throw new Exception("Session Expired (HTTP " + responseCode + ")");
                            }
                            Log.e(TAG, "Server error during push (HTTP " + responseCode + "): " + resText);
                            throw new Exception("HTTP Response Code " + responseCode + " - Error: "
                                    + (resText.length() > 200 ? resText.substring(0, 200) : resText));
                        }
                    } else {
                        Log.e(TAG, "Push Sync reader is null (no stream).");
                        throw new Exception("HTTP Response Code " + responseCode + " (No response stream available)");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Push Sync connection attempt " + attempt + " failed: " + e.getMessage(), e);
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
                if (response.optBoolean("unauthorized", false)
                        || response.optString("message", "").contains("Unauthorized")) {
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
                                JSONArray cMaps = mappings.optJSONArray("customers");
                                if (cMaps != null) {
                                    android.database.sqlite.SQLiteStatement cStmt = db.compileStatement("UPDATE customers SET server_id = ?, is_synced = 1, sync_status = 3 WHERE id = ?");
                                    for (int i = 0; i < cMaps.length(); i++) {
                                        JSONObject map = cMaps.getJSONObject(i);
                                        cStmt.bindLong(1, map.getInt("server_id"));
                                        cStmt.bindLong(2, map.getInt("local_id"));
                                        cStmt.executeUpdateDelete();
                                        cStmt.clearBindings();
                                    }
                                    cStmt.close();
                                }

                                // 2. Mark routes synced
                                JSONArray rMaps = mappings.optJSONArray("routes");
                                if (rMaps != null) {
                                    android.database.sqlite.SQLiteStatement rStmt = db.compileStatement("UPDATE daily_routes SET server_id = ?, is_synced = 1, sync_status = 3 WHERE id = ?");
                                    for (int i = 0; i < rMaps.length(); i++) {
                                        JSONObject map = rMaps.getJSONObject(i);
                                        rStmt.bindLong(1, map.getInt("server_id"));
                                        rStmt.bindLong(2, map.getInt("local_id"));
                                        rStmt.executeUpdateDelete();
                                        rStmt.clearBindings();
                                    }
                                    rStmt.close();
                                }

                                // 3. Mark invoices synced
                                JSONArray iMaps = mappings.optJSONArray("invoices");
                                java.util.Set<Integer> mappedInvoiceIds = new java.util.HashSet<>();
                                if (iMaps != null) {
                                    android.database.sqlite.SQLiteStatement iStmt = db.compileStatement("UPDATE invoices SET server_id = ?, is_synced = 1, sync_status = 3, failure_reason = ?, invoice_number = ? WHERE id = ?");
                                    android.database.sqlite.SQLiteStatement logStmt = db.compileStatement("UPDATE sync_logs SET upload_completed = ?, erp_response = 'Success (Mapped)' WHERE bill_id = ?");
                                    String completedTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                                    
                                    for (int i = 0; i < iMaps.length(); i++) {
                                        JSONObject map = iMaps.getJSONObject(i);
                                        int localId = map.getInt("local_id");
                                        int serverId = map.getInt("server_id");
                                        mappedInvoiceIds.add(localId);

                                        iStmt.bindLong(1, serverId);
                                        iStmt.bindString(2, ""); // failure_reason
                                        
                                        if (map.has("invoice_number")) {
                                            String mappedNum = map.getString("invoice_number");
                                            iStmt.bindString(3, mappedNum);

                                            // Parse suffix and update SharedPreferences so next invoice starts from here!
                                            if (mappedNum.length() >= 4) {
                                                try {
                                                    String suffix = mappedNum.substring(mappedNum.length() - 4);
                                                    int parsedSeq = Integer.parseInt(suffix);
                                                    android.content.SharedPreferences seqPrefs = context
                                                            .getSharedPreferences("CurtissPrefs", Context.MODE_PRIVATE);
                                                    int currentSeq = seqPrefs.getInt("global_invoice_seq", 0);
                                                    if (parsedSeq > currentSeq) {
                                                        seqPrefs.edit().putInt("global_invoice_seq", parsedSeq).apply();
                                                    }
                                                } catch (Exception e) {
                                                    // Ignore parsing errors
                                                }
                                            }
                                        } else {
                                            // Fallback for invoice_number
                                            Cursor fallbackInvCursor = db.rawQuery("SELECT invoice_number FROM invoices WHERE id = ?", new String[]{String.valueOf(localId)});
                                            if (fallbackInvCursor.moveToFirst()) {
                                                iStmt.bindString(3, DatabaseHelper.safeGetString(fallbackInvCursor, "invoice_number", ""));
                                            } else {
                                                iStmt.bindNull(3);
                                            }
                                            fallbackInvCursor.close();
                                        }
                                        
                                        iStmt.bindLong(4, localId);
                                        iStmt.executeUpdateDelete();
                                        iStmt.clearBindings();

                                        // Update sync_logs table
                                        try {
                                            logStmt.bindString(1, completedTime);
                                            logStmt.bindLong(2, localId);
                                            logStmt.executeUpdateDelete();
                                            logStmt.clearBindings();
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error updating sync_logs upload_completed for invoice " + localId
                                                    + ": " + e.getMessage());
                                        }
                                    }
                                    iStmt.close();
                                    logStmt.close();
                                }

                                // Mark any attempted invoices that were NOT in the server's mapping response as
                                // failed
                                for (int attemptedId : attemptedInvoiceIds) {
                                    if (!mappedInvoiceIds.contains(attemptedId)) {
                                        android.database.sqlite.SQLiteStatement failIStmt = db.compileStatement("UPDATE invoices SET sync_status = 4, failure_reason = 'Server failed to return a mapping - check ERP logs.' WHERE id = ?");
                                        failIStmt.bindLong(1, attemptedId);
                                        failIStmt.executeUpdateDelete();
                                        failIStmt.close();

                                        try {
                                            android.database.sqlite.SQLiteStatement failLogStmt = db.compileStatement("UPDATE sync_logs SET failure_reason = 'Server failed to return a mapping' WHERE bill_id = ?");
                                            failLogStmt.bindLong(1, attemptedId);
                                            failLogStmt.executeUpdateDelete();
                                            failLogStmt.close();
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error updating sync_logs failure reason for invoice "
                                                    + attemptedId + ": " + e.getMessage());
                                        }
                                        Log.w(TAG, "Invoice local ID " + attemptedId
                                                + " was pushed but server returned no mapping for it.");
                                    }
                                }

                                // 4. Mark payment collections synced
                                JSONArray pMaps = mappings.optJSONArray("payments");
                                java.util.Set<Integer> mappedPaymentIds = new java.util.HashSet<>();
                                if (pMaps != null) {
                                    android.database.sqlite.SQLiteStatement pStmt = db.compileStatement("UPDATE payments SET server_id = ?, is_synced = 1, sync_status = 3 WHERE id = ?");
                                    for (int i = 0; i < pMaps.length(); i++) {
                                        JSONObject map = pMaps.getJSONObject(i);
                                        int localId = map.getInt("local_id");
                                        mappedPaymentIds.add(localId);

                                        pStmt.bindLong(1, map.getInt("server_id"));
                                        pStmt.bindLong(2, localId);
                                        pStmt.executeUpdateDelete();
                                        pStmt.clearBindings();
                                    }
                                    pStmt.close();

                                    // Mark any attempted payments that were NOT in the server's mapping response as
                                    // failed
                                    for (int attemptedId : attemptedPaymentIds) {
                                        if (!mappedPaymentIds.contains(attemptedId)) {
                                            android.database.sqlite.SQLiteStatement failPStmt = db.compileStatement("UPDATE payments SET sync_status = 4 WHERE id = ?");
                                            failPStmt.bindLong(1, attemptedId);
                                            failPStmt.executeUpdateDelete();
                                            failPStmt.close();
                                            Log.w(TAG, "Payment local ID " + attemptedId
                                                    + " was pushed but server returned no mapping for it.");
                                        }
                                    }
                                }

                                // 5. Mark unproductive visits synced
                                JSONArray uMaps = mappings.optJSONArray("unproductive_visits");
                                if (uMaps != null) {
                                    android.database.sqlite.SQLiteStatement uStmt = db.compileStatement("UPDATE unproductive_visits SET server_id = ?, sync_status = 3 WHERE uuid = ?");
                                    for (int i = 0; i < uMaps.length(); i++) {
                                        JSONObject map = uMaps.getJSONObject(i);
                                        uStmt.bindLong(1, map.optInt("server_id", 0));
                                        uStmt.bindString(2, map.getString("uuid"));
                                        uStmt.executeUpdateDelete();
                                        uStmt.clearBindings();
                                    }
                                    uStmt.close();
                                }

                                // Perform post-sync checksum/integrity verification before committing
                                boolean customersOk = verifySyncIntegrity(mappings, "customers");
                                boolean routesOk = verifySyncIntegrity(mappings, "routes");
                                boolean invoicesOk = verifySyncIntegrity(mappings, "invoices");
                                boolean paymentsOk = verifySyncIntegrity(mappings, "payments");

                                if (customersOk && routesOk && invoicesOk && paymentsOk) {
                                    db.setTransactionSuccessful();
                                    Log.d(TAG,
                                            "Push Sync Successful: Staged payments & invoices committed successfully!");
                                    dbSuccess = true;
                                } else {
                                    Log.e(TAG,
                                            "Integrity verification failed for one or more sync groups. Transaction aborted!");
                                    dbSuccess = false;
                                }
                                break;
                            } finally {
                                db.endTransaction();
                            }
                        } catch (Exception e) {
                            if (e.getMessage() != null && (e.getMessage().contains("locked")
                                    || e.getMessage().contains("BUSY") || e.getMessage().contains("code 5"))) {
                                Log.w(TAG, "Database is locked (SQLITE_BUSY) during push insertion. Attempt "
                                        + dbAttempt + " of " + dbRetries + ". Retrying...");
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
                                db.update("invoices", cv, "id = ?", new String[] { String.valueOf(attemptedId) });

                                try {
                                    db.execSQL("UPDATE sync_logs SET failure_reason = ? WHERE bill_id = ?",
                                            new Object[] { serverMsg, attemptedId });
                                } catch (Exception e) {
                                    Log.e(TAG,
                                            "Error updating sync_logs failure reason on server rejection for invoice "
                                                    + attemptedId + ": " + e.getMessage());
                                }
                            }

                            for (int attemptedId : attemptedCustomerIds) {
                                ContentValues cv = new ContentValues();
                                cv.put("sync_status", 4);
                                db.update("customers", cv, "id = ?", new String[] { String.valueOf(attemptedId) });
                            }

                            for (int attemptedId : attemptedRouteIds) {
                                ContentValues cv = new ContentValues();
                                cv.put("sync_status", 4);
                                db.update("daily_routes", cv, "id = ?", new String[] { String.valueOf(attemptedId) });
                            }

                            for (int attemptedId : attemptedPaymentIds) {
                                ContentValues cv = new ContentValues();
                                cv.put("sync_status", 4);
                                db.update("payments", cv, "id = ?", new String[] { String.valueOf(attemptedId) });
                            }

                            for (String attemptedUuid : attemptedUnproductiveUuids) {
                                ContentValues cv = new ContentValues();
                                cv.put("sync_status", 4);
                                db.update("unproductive_visits", cv, "uuid = ?", new String[] { attemptedUuid });
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
                Log.e(TAG, "Push error parsing server JSON response: " + e.getMessage(), e);
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
            } else if (type.equals("payments")) {
                tableName = "payments";
                mappingKey = "payments";
            } else {
                return true;
            }

            if (mappings == null || !mappings.has(mappingKey)) {
                return true;
            }

            JSONArray serverIds = mappings.optJSONArray(mappingKey);
            if (serverIds == null) {
                return true;
            }
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            // Check that every mapped record returned by the server exists in our local
            // database and has matching UUID
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
                    Log.w(TAG, "Integrity verification failure: server returned invalid server_id (" + serverId
                            + ") for " + type);
                    return false;
                }

                // Retrieve local record by ID or UUID
                Cursor cursor = null;
                if (localId != -1) {
                    cursor = db.rawQuery("SELECT uuid FROM " + tableName + " WHERE id = ?",
                            new String[] { String.valueOf(localId) });
                } else {
                    cursor = db.rawQuery("SELECT uuid FROM " + tableName + " WHERE uuid = ?",
                            new String[] { serverUuid });
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
                    Log.w(TAG,
                            "Integrity verification failure: Server returned mapping for non-existent local record in "
                                    + tableName + " (localId: " + localId + ", uuid: " + serverUuid + ")");
                    return false;
                }

                // If both serverUuid and localUuid are populated, they must match exactly
                if (!serverUuid.isEmpty() && localUuid != null && !localUuid.isEmpty()) {
                    if (!serverUuid.equals(localUuid)) {
                        Log.w(TAG, "Integrity verification failure: UUID mismatch for " + tableName + " ID " + localId
                                + ". Local: " + localUuid + ", Server: " + serverUuid);
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
            Cursor cust = db.rawQuery(
                    "SELECT id, uuid FROM customers WHERE uuid IS NOT NULL AND uuid != '' AND (is_synced = 0 OR sync_status IN (2, 3))",
                    null);
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
            Cursor routes = db.rawQuery(
                    "SELECT id, uuid FROM daily_routes WHERE uuid IS NOT NULL AND uuid != '' AND (is_synced = 0 OR sync_status IN (2, 3))",
                    null);
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
            Cursor invs = db.rawQuery(
                    "SELECT id, uuid FROM invoices WHERE uuid IS NOT NULL AND uuid != '' AND (is_synced = 0 OR sync_status IN (2, 3))",
                    null);
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
            Cursor pmts = db.rawQuery(
                    "SELECT id, uuid FROM payments WHERE uuid IS NOT NULL AND uuid != '' AND (is_synced = 0 OR sync_status IN (2, 3))",
                    null);
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
            payload.put("user_id", userId);

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
            conn.setRequestProperty("X-User-ID", String.valueOf(userId));
            String apiTokenHeader = SecurePreferences.getSessionPrefs(context).getString("api_token", "");
            if (!apiTokenHeader.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiTokenHeader);
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            OutputStream os = conn.getOutputStream();
            os.write(jsonBytes, 0, jsonBytes.length);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
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
                        String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                                java.util.Locale.getDefault()).format(new java.util.Date());
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
                                    if (serverId > 0)
                                        cv.put("server_id", serverId);
                                    cv.put("last_attempt_time", currentTime);
                                    db.update("customers", cv, "id = ?",
                                            new String[] { String.valueOf(custMap.get(uuid)) });
                                } else if (type.equals("route") && routeMap.containsKey(uuid)) {
                                    cv.put("is_synced", 1);
                                    cv.put("sync_status", 3);
                                    cv.put("failure_reason", "");
                                    if (serverId > 0)
                                        cv.put("server_id", serverId);
                                    cv.put("last_attempt_time", currentTime);
                                    db.update("daily_routes", cv, "id = ?",
                                            new String[] { String.valueOf(routeMap.get(uuid)) });
                                } else if (type.equals("invoice") && invMap.containsKey(uuid)) {
                                    cv.put("is_synced", 1);
                                    cv.put("sync_status", 3);
                                    cv.put("failure_reason", "");
                                    if (serverId > 0)
                                        cv.put("server_id", serverId);
                                    cv.put("last_attempt_time", currentTime);
                                    db.update("invoices", cv, "id = ?",
                                            new String[] { String.valueOf(invMap.get(uuid)) });
                                } else if (type.equals("payment") && pmtMap.containsKey(uuid)) {
                                    cv.put("is_synced", 1);
                                    cv.put("sync_status", 3);
                                    cv.put("failure_reason", "");
                                    cv.put("last_attempt_time", currentTime);
                                    db.update("payments", cv, "id = ?",
                                            new String[] { String.valueOf(pmtMap.get(uuid)) });
                                }
                            } else {
                                cv.put("sync_status", 4); // Failed verification
                                cv.put("is_synced", 0);
                                cv.put("failure_reason", "Unverified on server");
                                cv.put("last_attempt_time", currentTime);
                                if (type.equals("customer") && custMap.containsKey(uuid)) {
                                    db.update("customers", cv, "id = ?",
                                            new String[] { String.valueOf(custMap.get(uuid)) });
                                } else if (type.equals("route") && routeMap.containsKey(uuid)) {
                                    db.update("daily_routes", cv, "id = ?",
                                            new String[] { String.valueOf(routeMap.get(uuid)) });
                                } else if (type.equals("invoice") && invMap.containsKey(uuid)) {
                                    db.update("invoices", cv, "id = ?",
                                            new String[] { String.valueOf(invMap.get(uuid)) });
                                } else if (type.equals("payment") && pmtMap.containsKey(uuid)) {
                                    db.update("payments", cv, "id = ?",
                                            new String[] { String.valueOf(pmtMap.get(uuid)) });
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
    public void startPullSync(final Context context, final int userId, final SyncListener listener,
            final boolean isFullSync) {
        if (listener != null) {
            listener.onSyncStarted();
        }

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                boolean acquired = false;
                try {
                    acquired = syncLock.tryLock();
                    if (!acquired) {
                        updateProgress(listener, "Waiting for background synchronization to finish...");
                        acquired = syncLock.tryLock(30, java.util.concurrent.TimeUnit.SECONDS);
                    }

                    if (!acquired) {
                        completeSync(listener, false, "Sync suspended: Another synchronization is in progress.");
                        return;
                    }

                    isSyncing = true;
                    if (isFullSync) {
                        updateProgress(listener, "Downloading DAILY FULL catalog, routes & customers...");
                    } else {
                        updateProgress(listener, "Downloading fresh catalog, routes & customers...");
                    }
                    boolean pullSuccess = executePull(context, userId, listener, isFullSync);
                    if (pullSuccess) {
                        completeSync(listener, true, "Database pulled successfully!");
                    } else {
                        completeSync(listener, false,
                                "Pull sync failed: "
                                        + (lastSyncError != null && !lastSyncError.isEmpty() ? lastSyncError
                                                : "Server unreachable."));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    completeSync(listener, false, "Synchronization interrupted.");
                } catch (Exception e) {
                    Log.e(TAG, "Pull Sync exception: " + e.getMessage());
                    completeSync(listener, false, "Exception: " + e.getMessage());
                } finally {
                    if (acquired) {
                        releaseSyncLock();
                    }
                }
            }
        });
    }

    // User-initiated Two-Phase Push Sync (Mobile -> ERP) with Verification
    public void startManualPushSync(final Context context, final int userId, final SyncListener listener) {
        if (listener != null) {
            listener.onSyncStarted();
        }

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                boolean acquired = false;
                try {
                    acquired = syncLock.tryLock();
                    if (!acquired) {
                        updateProgress(listener, "Waiting for background synchronization to finish...");
                        acquired = syncLock.tryLock(30, java.util.concurrent.TimeUnit.SECONDS);
                    }

                    if (!acquired) {
                        completeSync(listener, false, "Sync suspended: Another synchronization is in progress.");
                        return;
                    }

                    isSyncing = true;
                    SQLiteDatabase db = dbHelper.getWritableDatabase();

                    int maxAttempts = 3;
                    int attempt = 0;
                    boolean allVerified = false;
                    String statusMessage = "";

                    while (attempt < maxAttempts) {
                        attempt++;

                        // Phase 1: Uploading
                        updateProgress(listener, "Phase 1: Uploading transactions to ERP (Attempt " + attempt + ")...");
                        boolean pushSuccess = executePushSafe(context, userId);

                        // Phase 2: Verification
                        updateProgress(listener,
                                "Phase 2: Verifying uploads with ERP server (Attempt " + attempt + ")...");
                        boolean verifySuccess = executeVerification(context, userId);

                        // Count remaining unsynced items to determine absolute success
                        int unsyncedInvoices = 0;
                        int unsyncedPayments = 0;
                        int unsyncedCustomers = 0;
                        int unsyncedRoutes = 0;

                        Cursor c = null;
                        try {
                            c = db.rawQuery("SELECT COUNT(*) FROM invoices WHERE is_synced = 0 OR sync_status != 3",
                                    null);
                            if (c.moveToFirst())
                                unsyncedInvoices = c.getInt(0);
                            c.close();

                            c = db.rawQuery("SELECT COUNT(*) FROM payments WHERE is_synced = 0 OR sync_status != 3",
                                    null);
                            if (c.moveToFirst())
                                unsyncedPayments = c.getInt(0);
                            c.close();

                            c = db.rawQuery(
                                    "SELECT COUNT(*) FROM customers WHERE (is_synced = 0 OR sync_status != 3) AND (server_id = 0 OR is_profile_synced = 0)",
                                    null);
                            if (c.moveToFirst())
                                unsyncedCustomers = c.getInt(0);
                            c.close();

                            c = db.rawQuery("SELECT COUNT(*) FROM daily_routes WHERE is_synced = 0 OR sync_status != 3",
                                    null);
                            if (c.moveToFirst())
                                unsyncedRoutes = c.getInt(0);
                            c.close();
                        } catch (Exception e) {
                            Log.e(TAG, "Error checking remaining unsynced count: " + e.getMessage());
                        } finally {
                            if (c != null)
                                c.close();
                        }

                        int totalUnsynced = unsyncedInvoices + unsyncedPayments + unsyncedCustomers + unsyncedRoutes;

                        if (totalUnsynced == 0) {
                            allVerified = true;
                            statusMessage = "All data synced and verified successfully!";
                            break;
                        } else {
                            statusMessage = "Discrepancy: " +
                                    (unsyncedInvoices > 0 ? unsyncedInvoices + " bills " : "") +
                                    (unsyncedPayments > 0 ? unsyncedPayments + " payments " : "") +
                                    (unsyncedCustomers > 0 ? unsyncedCustomers + " profiles " : "") +
                                    (unsyncedRoutes > 0 ? unsyncedRoutes + " routes " : "") +
                                    "failed to verify on the server.";
                            Log.w(TAG, "Attempt " + attempt + " failed to reconcile all data: " + statusMessage);
                            updateProgress(listener, statusMessage + " Retrying in 2 seconds...");

                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }

                    if (allVerified) {
                        Log.d(TAG,
                                "Post-sync: successfully synced all items. Preserving local data for offline reference.");
                        completeSync(listener, true, "All data synced and verified successfully!");
                    } else {
                        completeSync(listener, false, "Push verification incomplete. " + statusMessage);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    completeSync(listener, false, "Synchronization interrupted.");
                } catch (Exception e) {
                    Log.e(TAG, "Manual Push Sync exception: " + e.getMessage());
                    completeSync(listener, false, "Exception: " + e.getMessage());
                } finally {
                    if (acquired) {
                        releaseSyncLock();
                    }
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
                    android.widget.Toast
                            .makeText(context, "Session Expired. Please login again.", android.widget.Toast.LENGTH_LONG)
                            .show();
                    android.content.Intent intent = new android.content.Intent(context, LoginActivity.class);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start LoginActivity: " + e.getLocalizedMessage());
                }
            }
        });
    }

    private void backupConflict(SQLiteDatabase db, String tableName, int recordId, String uuid, JSONObject localData,
            JSONObject serverData) {
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

    private boolean isNetworkAvailable(Context context) {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                    return capabilities != null
                            && (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
                }
            } else {
                @SuppressWarnings("deprecation")
                android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        }
        return false;
    }

    public void pullMissedStockEvents() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    android.content.SharedPreferences prefs = context.getSharedPreferences("curtiss_db_prefs",
                            Context.MODE_PRIVATE);
                    int lastEventId = prefs.getInt("last_stock_event_id", 0);

                    android.content.SharedPreferences sessionPrefs = SecurePreferences.getSessionPrefs(context);
                    String baseUrl = sessionPrefs.getString("base_url", "https://curtiss.suzxlabs.com");

                    String urlString = baseUrl + "/StockEvents/pull?api_sync=1&last_event_id=" + lastEventId;

                    java.net.URL url = new java.net.URL(urlString);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);

                    if (conn.getResponseCode() == java.net.HttpURLConnection.HTTP_OK) {
                        java.io.InputStream in = new java.io.BufferedInputStream(conn.getInputStream());
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                        StringBuilder result = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            result.append(line);
                        }

                        JSONObject json = new JSONObject(result.toString());
                        if (json.optBoolean("success", false)) {
                            JSONArray events = json.optJSONArray("events");
                            if (events != null && events.length() > 0) {
                                DatabaseHelper dbHelper = new DatabaseHelper(context);
                                int maxEventId = lastEventId;
                                for (int i = 0; i < events.length(); i++) {
                                    JSONObject ev = events.getJSONObject(i);
                                    int evId = ev.optInt("event_id", 0);
                                    int parentItem = ev.optInt("parent_item", 0);
                                    int varId = ev.optInt("variation_id", 0);
                                    double onHand = ev.optDouble("on_hand", 0.0);
                                    double reserved = ev.optDouble("reserved", 0.0);
                                    int stockVersion = ev.optInt("stock_version", 0);

                                    int targetId = varId > 0 ? varId : parentItem;
                                    dbHelper.updateStockLocally(targetId, onHand, reserved);
                                    if (evId > maxEventId) {
                                        maxEventId = evId;
                                    }
                                }
                                if (maxEventId > lastEventId) {
                                    prefs.edit().putInt("last_stock_event_id", maxEventId).apply();
                                }
                                Log.i(TAG, "Successfully pulled and applied " + events.length() + " missed stock events.");
                            }
                        }
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Error pulling missed stock events: " + e.getMessage());
                }
            }
        }).start();
    }
}
