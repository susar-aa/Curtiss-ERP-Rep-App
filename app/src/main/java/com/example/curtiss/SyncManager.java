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

    private static final String TAG = "SyncManager";
    private static SyncManager instance;
    private final DatabaseHelper dbHelper;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public interface SyncListener {
        void onSyncStarted();
        void onSyncProgress(String message);
        void onSyncCompleted(boolean success, String message);
    }

    private SyncManager(Context context) {
        this.dbHelper = new DatabaseHelper(context.getApplicationContext());
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized SyncManager getInstance(Context context) {
        if (instance == null) {
            instance = new SyncManager(context);
        }
        return instance;
    }

    // Bidirectional Background Sync (Pull then Push)
    public void startSync(final Context context, final int userId, final SyncListener listener) {
        if (listener != null) {
            listener.onSyncStarted();
        }

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    // --- STEP 1: PULL DATA FROM SERVER ---
                    updateProgress(listener, "Downloading items & customer registry...");
                    boolean pullSuccess = executePull(context);
                    
                    if (!pullSuccess) {
                        completeSync(listener, false, "Pull sync failed. Server unreachable.");
                        return;
                    }

                    // --- STEP 2: PUSH OFFLINE DATA TO SERVER ---
                    updateProgress(listener, "Uploading pending offline invoices & profiles...");
                    boolean pushSuccess = executePush(userId);

                    if (pushSuccess) {
                        completeSync(listener, true, "ERP Synced Successfully!");
                    } else {
                        completeSync(listener, false, "Push sync failed. Check server logs.");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Sync exception: " + e.getMessage());
                    completeSync(listener, false, "Exception: " + e.getMessage());
                }
            }
        });
    }

    // Execute server Pull
    private boolean executePull(Context context) {
        try {
            URL url = new URL("https://curtiss.suzxlabs.com/rep/RepDashboard/sync_pull?api_sync=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject response = new JSONObject(sb.toString());
                if (response.optBoolean("success", false)) {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    db.beginTransaction();
                    try {
                        // 1. Sync Products
                        JSONArray products = response.getJSONArray("products");
                        for (int i = 0; i < products.length(); i++) {
                            JSONObject p = products.getJSONObject(i);
                            int id = p.getInt("id");
                            String name = p.getString("name");
                            String catName = p.optString("category_name", "General");
                            double price = p.optDouble("selling_price", 0.0);
                            double wholesale = p.optDouble("wholesale_price", price);
                            int qty = p.optInt("quantity_on_hand", 0);
                            int reserved = p.optInt("quantity_reserved", 0);
                            String imgUrl = p.optString("image_path", "");

                            dbHelper.saveProduct(id, name, catName, price, wholesale, qty, reserved, imgUrl);
                            
                            // Trigger background image download cache
                            if (!imgUrl.isEmpty()) {
                                ImageDownloadManager.getInstance(context).downloadProductImage(context, id, imgUrl);
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

                            // Check if this serverId is already in SQLite
                            Cursor cursor = db.rawQuery("SELECT id FROM customers WHERE server_id = " + serverId, null);
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
                            cv.put("is_synced", 1);

                            if (cursor.moveToFirst()) {
                                db.update("customers", cv, "server_id = ?", new String[]{String.valueOf(serverId)});
                            } else {
                                db.insert("customers", null, cv);
                            }
                            cursor.close();
                        }

                        // 3. Sync Server Master Routes (Territories)
                        JSONArray routes = response.optJSONArray("routes");
                        if (routes != null) {
                            db.execSQL("CREATE TABLE IF NOT EXISTS server_routes (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
                            db.execSQL("DELETE FROM server_routes");
                            for (int i = 0; i < routes.length(); i++) {
                                JSONObject r = routes.getJSONObject(i);
                                int routeId = r.getInt("id");
                                String routeName = r.getString("name");

                                ContentValues rCv = new ContentValues();
                                rCv.put("id", routeId);
                                rCv.put("name", routeName);
                                db.insert("server_routes", null, rCv);
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

                        db.setTransactionSuccessful();
                        Log.d(TAG, "Pull Sync Successful");
                        return true;
                    } finally {
                        db.endTransaction();
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Pull error: " + e.getMessage());
        }
        return false;
    }

    // Compile local changes and push to server
    private boolean executePush(int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            JSONObject payload = new JSONObject();
            payload.put("user_id", userId);

            // 1. Fetch local unsynced Customers
            JSONArray custArray = new JSONArray();
            Cursor custCursor = db.rawQuery("SELECT * FROM customers WHERE is_synced = 0", null);
            while (custCursor.moveToNext()) {
                JSONObject c = new JSONObject();
                c.put("local_id", custCursor.getInt(custCursor.getColumnIndexOrThrow("id")));
                c.put("name", custCursor.getString(custCursor.getColumnIndexOrThrow("name")));
                c.put("phone", custCursor.getString(custCursor.getColumnIndexOrThrow("phone")));
                c.put("whatsapp", custCursor.getString(custCursor.getColumnIndexOrThrow("whatsapp")));
                c.put("address", custCursor.getString(custCursor.getColumnIndexOrThrow("address")));
                c.put("territory", custCursor.getString(custCursor.getColumnIndexOrThrow("territory")));
                c.put("latitude", custCursor.getDouble(custCursor.getColumnIndexOrThrow("latitude")));
                c.put("longitude", custCursor.getDouble(custCursor.getColumnIndexOrThrow("longitude")));
                custArray.put(c);
            }
            custCursor.close();
            payload.put("customers", custArray);

            // 2. Fetch local unsynced Routes
            JSONArray routeArray = new JSONArray();
            Cursor rCursor = db.rawQuery("SELECT * FROM daily_routes WHERE is_synced = 0", null);
            while (rCursor.moveToNext()) {
                JSONObject r = new JSONObject();
                r.put("local_id", rCursor.getInt(rCursor.getColumnIndexOrThrow("id")));
                r.put("route_name", rCursor.getString(rCursor.getColumnIndexOrThrow("route_name")));
                r.put("start_meter", rCursor.getDouble(rCursor.getColumnIndexOrThrow("start_meter")));
                r.put("start_time", rCursor.getString(rCursor.getColumnIndexOrThrow("start_time")));
                r.put("start_lat", rCursor.getDouble(rCursor.getColumnIndexOrThrow("start_lat")));
                r.put("start_lng", rCursor.getDouble(rCursor.getColumnIndexOrThrow("start_lng")));
                r.put("end_meter", rCursor.getDouble(rCursor.getColumnIndexOrThrow("end_meter")));
                r.put("end_time", rCursor.getString(rCursor.getColumnIndexOrThrow("end_time")));
                r.put("end_lat", rCursor.getDouble(rCursor.getColumnIndexOrThrow("end_lat")));
                r.put("end_lng", rCursor.getDouble(rCursor.getColumnIndexOrThrow("end_lng")));
                r.put("status", rCursor.getString(rCursor.getColumnIndexOrThrow("status")));
                routeArray.put(r);
            }
            rCursor.close();
            payload.put("routes", routeArray);

            // 3. Fetch local unsynced Invoices
            JSONArray invArray = new JSONArray();
            Cursor invCursor = db.rawQuery("SELECT * FROM invoices WHERE is_synced = 0", null);
            while (invCursor.moveToNext()) {
                JSONObject inv = new JSONObject();
                int localInvId = invCursor.getInt(invCursor.getColumnIndexOrThrow("id"));
                inv.put("local_id", localInvId);
                inv.put("invoice_number", invCursor.getString(invCursor.getColumnIndexOrThrow("invoice_number")));
                inv.put("customer_id", invCursor.getInt(invCursor.getColumnIndexOrThrow("customer_id")));
                inv.put("invoice_date", invCursor.getString(invCursor.getColumnIndexOrThrow("invoice_date")));
                inv.put("due_date", invCursor.getString(invCursor.getColumnIndexOrThrow("due_date")));
                inv.put("subtotal", invCursor.getDouble(invCursor.getColumnIndexOrThrow("subtotal")));
                inv.put("discount", invCursor.getDouble(invCursor.getColumnIndexOrThrow("discount")));
                inv.put("tax", invCursor.getDouble(invCursor.getColumnIndexOrThrow("tax")));
                inv.put("grand_total", invCursor.getDouble(invCursor.getColumnIndexOrThrow("grand_total")));
                inv.put("payment_method", invCursor.getString(invCursor.getColumnIndexOrThrow("payment_method")));
                inv.put("latitude", invCursor.getDouble(invCursor.getColumnIndexOrThrow("latitude")));
                inv.put("longitude", invCursor.getDouble(invCursor.getColumnIndexOrThrow("longitude")));

                // Load invoice items
                JSONArray itemsArray = new JSONArray();
                Cursor itemCursor = db.rawQuery("SELECT * FROM invoice_items WHERE invoice_id = " + localInvId, null);
                while (itemCursor.moveToNext()) {
                    JSONObject item = new JSONObject();
                    item.put("product_id", itemCursor.getInt(itemCursor.getColumnIndexOrThrow("product_id")));
                    item.put("product_name", itemCursor.getString(itemCursor.getColumnIndexOrThrow("product_name")));
                    item.put("quantity", itemCursor.getInt(itemCursor.getColumnIndexOrThrow("quantity")));
                    item.put("unit_price", itemCursor.getDouble(itemCursor.getColumnIndexOrThrow("unit_price")));
                    item.put("discount_val", itemCursor.getDouble(itemCursor.getColumnIndexOrThrow("discount_val")));
                    item.put("total", itemCursor.getDouble(itemCursor.getColumnIndexOrThrow("total")));
                    itemsArray.put(item);
                }
                itemCursor.close();
                inv.put("items", itemsArray);
                invArray.put(inv);
            }
            invCursor.close();
            payload.put("invoices", invArray);

            // Skip API post if there's nothing to upload
            if (custArray.length() == 0 && routeArray.length() == 0 && invArray.length() == 0) {
                return true;
            }

            // POST unified payload to Plesk Sync API
            URL url = new URL("https://curtiss.suzxlabs.com/rep/RepDashboard/sync_push?api_sync=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            byte[] jsonBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(jsonBytes, 0, jsonBytes.length);
            os.flush();
            os.close();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject response = new JSONObject(sb.toString());
                if (response.optBoolean("success", false)) {
                    JSONObject mappings = response.getJSONObject("mappings");

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
                        for (int i = 0; i < iMaps.length(); i++) {
                            JSONObject map = iMaps.getJSONObject(i);
                            int localId = map.getInt("local_id");
                            int serverId = map.getInt("server_id");

                            ContentValues cv = new ContentValues();
                            cv.put("server_id", serverId);
                            cv.put("is_synced", 1);
                            db.update("invoices", cv, "id = ?", new String[]{String.valueOf(localId)});
                        }

                        db.setTransactionSuccessful();
                        Log.d(TAG, "Push Sync Successful");
                        return true;
                    } finally {
                        db.endTransaction();
                    }
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Push error: " + e.getMessage());
        }
        return false;
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
