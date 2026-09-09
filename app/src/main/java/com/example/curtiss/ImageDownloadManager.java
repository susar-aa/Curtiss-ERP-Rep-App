package com.example.curtiss;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageDownloadManager {

    private static final String TAG = "ImageDownloadManager";
    private static ImageDownloadManager instance;
    private final ExecutorService executorService;
    private final DatabaseHelper dbHelper;

    private ImageDownloadManager(Context context) {
        // Multi-threaded downloader pool of 3 concurrent worker threads
        this.executorService = Executors.newFixedThreadPool(3);
        this.dbHelper = DatabaseHelper.getInstance(context.getApplicationContext());
    }

    public static synchronized ImageDownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new ImageDownloadManager(context);
        }
        return instance;
    }

    public void queueImageDownload(Context context, int productId, String imageUrlString) {
        if (imageUrlString == null || imageUrlString.trim().isEmpty()) {
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Check if this image file already exists locally
        String fileName;
        if (imageUrlString.contains("var_") || imageUrlString.contains("prod_")) {
            int lastSlash = imageUrlString.lastIndexOf('/');
            if (lastSlash != -1) {
                fileName = imageUrlString.substring(lastSlash + 1);
            } else {
                fileName = "item_img_" + productId + "_" + System.currentTimeMillis() + ".jpg";
            }
        } else {
            fileName = "item_img_" + productId + ".jpg";
        }

        File outputDir = context.getDir("item_images", Context.MODE_PRIVATE);
        File outputFile = new File(outputDir, fileName);

        if (outputFile.exists() && outputFile.length() > 0) {
            Log.d(TAG, "Image already cached for URL: " + imageUrlString);
            return;
        }

        ContentValues cv = new ContentValues();
        cv.put("product_id", productId);
        cv.put("image_url", imageUrlString);
        cv.put("status", "pending");
        cv.put("attempts", 0);
        cv.put("last_error", (String)null);
        db.insertWithOnConflict("image_download_queue", null, cv, SQLiteDatabase.CONFLICT_REPLACE);

        // Update pending count in SharedPreferences
        updatePendingImageCount(context);
    }


    // Start background worker for queued downloads
    public synchronized void startQueueDownload(final Context context) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        // Reset stuck 'downloading' states back to 'pending' so they can be processed
        try {
            ContentValues resetCv = new ContentValues();
            resetCv.put("status", "pending");
            db.update("image_download_queue", resetCv, "status = 'downloading'", null);
        } catch (Exception e) {
            Log.e(TAG, "Error resetting stuck downloading states: " + e.getMessage());
        }

        Cursor cursor = db.rawQuery("SELECT product_id, image_url, attempts FROM image_download_queue WHERE status = 'pending' OR (status = 'failed' AND attempts < 3)", null);

        while (cursor.moveToNext()) {
            final int productId = cursor.getInt(0);
            final String imageUrl = cursor.getString(1);
            final int attempts = cursor.getInt(2);

            // Update status to downloading in database
            ContentValues cv = new ContentValues();
            cv.put("status", "downloading");
            db.update("image_download_queue", cv, "image_url = ?", new String[]{imageUrl});

            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    downloadImageTask(context, productId, imageUrl, attempts);
                }
            });
        }
        cursor.close();
        updatePendingImageCount(context);
    }


    private void downloadImageTask(Context context, int productId, String imageUrlString, int currentAttempts) {
        if (imageUrlString != null) {
            int httpIndex = imageUrlString.indexOf("http://");
            if (httpIndex == -1) {
                httpIndex = imageUrlString.indexOf("https://");
            }
            if (httpIndex != -1) {
                imageUrlString = imageUrlString.substring(httpIndex);
            }
        }
        String absoluteUrl = imageUrlString;
        File outputFile = null;
        File tempFile = null;
        try {
            String fileName;
            if (imageUrlString.contains("var_") || imageUrlString.contains("prod_")) {
                int lastSlash = imageUrlString.lastIndexOf('/');
                if (lastSlash != -1) {
                    fileName = imageUrlString.substring(lastSlash + 1);
                } else {
                    fileName = "item_img_" + productId + "_" + System.currentTimeMillis() + ".jpg";
                }
            } else {
                fileName = "item_img_" + productId + ".jpg";
            }
            File outputDir = context.getDir("item_images", Context.MODE_PRIVATE);
            outputFile = new File(outputDir, fileName);
            tempFile = new File(outputDir, fileName + ".tmp");


            // Form candidate URLs to try
            java.util.List<String> candidateUrls = new java.util.ArrayList<>();
            if (imageUrlString.startsWith("http")) {
                candidateUrls.add(imageUrlString);
                if (imageUrlString.contains("public/uploads/products/")) {
                    candidateUrls.add(imageUrlString.replace("public/uploads/products/", "uploads/products/"));
                } else if (imageUrlString.contains("/uploads/products/")) {
                    candidateUrls.add(imageUrlString.replace("/uploads/products/", "/public/uploads/products/"));
                }
            } else {
                android.content.SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
                String baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");
                // Remove trailing slash if present in baseUrl
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }

                String filename = imageUrlString;
                int lastSlash = imageUrlString.lastIndexOf('/');
                if (lastSlash != -1) {
                    filename = imageUrlString.substring(lastSlash + 1);
                }

                // If imageUrlString starts with "public/", strip it for smart relative paths
                String relativeWithoutPublic = imageUrlString;
                if (imageUrlString.startsWith("public/")) {
                    relativeWithoutPublic = imageUrlString.substring(7);
                } else if (imageUrlString.startsWith("/public/")) {
                    relativeWithoutPublic = imageUrlString.substring(8);
                }

                // Try 1: Original relative path
                candidateUrls.add(baseUrl + "/" + imageUrlString);

                // Try 2: Smart relative path (stripping public/ prefix)
                candidateUrls.add(baseUrl + "/" + relativeWithoutPublic);

                // Try 3: Standard production path
                candidateUrls.add(baseUrl + "/uploads/products/" + filename);

                // Try 4: Standard local subfolder path
                candidateUrls.add(baseUrl + "/public/uploads/products/" + filename);

                // Try 5: If baseUrl ends with "/public", strip it and try the original relative path
                if (baseUrl.endsWith("/public")) {
                    String baseWithoutPublic = baseUrl.substring(0, baseUrl.length() - 7);
                    candidateUrls.add(baseWithoutPublic + "/" + imageUrlString);
                }
            }

            Log.d(TAG, "Queue downloading for Product " + productId + " checking candidate URLs: " + candidateUrls + " (Attempt: " + (currentAttempts + 1) + ")");

            boolean downloadSuccess = false;
            String errorMsg = "";
            for (String urlStr : candidateUrls) {
                HttpURLConnection connection = null;
                try {
                    Log.d(TAG, "Trying download URL for Product " + productId + ": " + urlStr);
                    URL url = new URL(urlStr);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream input = new BufferedInputStream(connection.getInputStream());
                        FileOutputStream output = new FileOutputStream(tempFile);

                        byte[] data = new byte[4096];
                        int count;
                        while ((count = input.read(data)) != -1) {
                            output.write(data, 0, count);
                        }

                        output.flush();
                        output.close();
                        input.close();

                        if (tempFile.exists() && tempFile.length() > 0) {
                            // Decode file to verify it's a valid image
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(tempFile.getAbsolutePath(), options);
                            if (options.outWidth > 0 && options.outHeight > 0) {
                                absoluteUrl = urlStr; // Mark this as the successful URL
                                downloadSuccess = true;
                                Log.d(TAG, "Verified valid image (" + options.outMimeType + ", " + options.outWidth + "x" + options.outHeight + ") from URL: " + urlStr);
                                break;
                            } else {
                                Log.w(TAG, "URL " + urlStr + " returned non-image content or HTML. Deleting temp file and trying next candidate...");
                                tempFile.delete();
                                errorMsg = "URL returned non-image content.";
                            }
                        }
                    } else {
                        errorMsg = "HTTP response code " + responseCode + " for " + urlStr;
                    }
                } catch (Exception e) {
                    errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown connection error";
                    Log.w(TAG, "Failed downloading from " + urlStr + ": " + errorMsg);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }

            if (!downloadSuccess) {
                throw new Exception(errorMsg.isEmpty() ? "All candidate download URLs failed." : errorMsg);
            }

            // Verification check: check if file exists and is not empty
            if (tempFile.exists() && tempFile.length() > 0) {
                // Clean up old output file if exists
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                // Rename temp file to output file
                if (tempFile.renameTo(outputFile)) {
                    String localPath = outputFile.getAbsolutePath();

                    // Check if the downloaded URL is the main image or a variation image
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    db.beginTransaction();
                    try {
                        Cursor pCursor = db.rawQuery("SELECT image_url, variations_json FROM products WHERE id = ?", new String[]{String.valueOf(productId)});
                        String mainImgUrl = "";
                        String varsJsonStr = "";
                        if (pCursor.moveToFirst()) {
                            mainImgUrl = pCursor.getString(0);
                            varsJsonStr = pCursor.getString(1);
                        }
                        pCursor.close();

                        if (imageUrlString.equals(mainImgUrl)) {
                            // 1. Update products table main image
                            ContentValues cv = new ContentValues();
                            cv.put("local_image_path", localPath);
                            db.update("products", cv, "id = ?", new String[]{String.valueOf(productId)});
                        } else {
                            // 2. Update variations_json with local path
                            if (varsJsonStr != null && !varsJsonStr.isEmpty()) {
                                org.json.JSONArray varArray = new org.json.JSONArray(varsJsonStr);
                                boolean updated = false;
                                for (int v = 0; v < varArray.length(); v++) {
                                    org.json.JSONObject varObj = varArray.getJSONObject(v);
                                    String varImgUrl = varObj.optString("image_path", "");
                                    if (imageUrlString.equals(varImgUrl)) {
                                        varObj.put("local_image_path", localPath);
                                        varObj.put("image_path", localPath); // Set both to be safe
                                        updated = true;
                                    }
                                }
                                if (updated) {
                                    ContentValues cv = new ContentValues();
                                    cv.put("variations_json", varArray.toString());
                                    db.update("products", cv, "id = ?", new String[]{String.valueOf(productId)});
                                }
                            }
                        }
                        db.setTransactionSuccessful();
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating products/variations within transaction for product " + productId + ": " + e.getMessage());
                    } finally {
                        db.endTransaction();
                    }


                    // 2. Update queue status to completed
                    updateQueueStatusWithRetry(imageUrlString, "completed", currentAttempts + 1, null);

                    // Increment diagnostics success
                    incrementImageCount(context, true);
                    Log.d(TAG, "Success downloading image for Product " + productId + " saved to " + localPath);
                } else {
                    throw new Exception("Temp file renaming to destination failed.");
                }
            } else {
                throw new Exception("Downloaded file is empty or corrupted.");
            }
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : "Unknown download exception";
            Log.e(TAG, "Failed downloading image for product " + productId + ": " + errMsg);

            // Clean up temp file on failure
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }

            // Update queue status
            int newAttempts = currentAttempts + 1;
            updateQueueStatusWithRetry(imageUrlString, newAttempts >= 3 ? "failed" : "pending", newAttempts, errMsg);

            if (newAttempts >= 3) {
                incrementImageCount(context, false);
            }

            // Log error to sync diagnostics file
            logSyncError(context, "Image download failed for Product ID " + productId + " URL: " + absoluteUrl + ". Error: " + errMsg + " (Attempts: " + newAttempts + ")");
        } finally {
            updatePendingImageCount(context);
        }
    }

    private void updateQueueStatusWithRetry(String imageUrl, String status, int attempts, String lastError) {
        int retries = 5;
        for (int i = 1; i <= retries; i++) {
            try {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("status", status);
                cv.put("attempts", attempts);
                if (lastError != null) {
                    cv.put("last_error", lastError);
                } else {
                    cv.putNull("last_error");
                }
                db.update("image_download_queue", cv, "image_url = ?", new String[]{imageUrl});
                return; // Success
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().contains("locked") || e.getMessage().contains("BUSY") || e.getMessage().contains("code 5"))) {
                    Log.w(TAG, "Database is locked during queue status update for URL " + imageUrl + ". Attempt " + i + " of " + retries + ". Retrying...");
                    try {
                        Thread.sleep(100 * i);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    Log.e(TAG, "Non-lock error during queue status update: " + e.getMessage());
                    break;
                }
            }
        }
    }


    private void incrementImageCount(Context context, boolean success) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("CurtissPrefs", Context.MODE_PRIVATE);
        int val = prefs.getInt(success ? "sync_downloaded_images" : "sync_failed_images", 0);
        prefs.edit().putInt(success ? "sync_downloaded_images" : "sync_failed_images", val + 1).apply();
    }

    public void updatePendingImageCount(Context context) {
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM image_download_queue WHERE status = 'pending' OR status = 'downloading'", null);
            int pending = 0;
            if (cursor.moveToFirst()) {
                pending = cursor.getInt(0);
            }
            cursor.close();
            android.content.SharedPreferences prefs = context.getSharedPreferences("CurtissPrefs", Context.MODE_PRIVATE);
            prefs.edit().putInt("sync_pending_images", pending).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error updating pending count: " + e.getMessage());
        }
    }

    public static void logSyncError(Context context, String logMsg) {
        try {
            File logFile = new File(context.getFilesDir(), "sync_diagnostics.txt");
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            java.io.FileWriter writer = new java.io.FileWriter(logFile, true);
            writer.append("[").append(timestamp).append("] ").append(logMsg).append("\n");
            writer.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to write sync diagnostics log: " + e.getMessage());
        }
    }

    // Clean up obsolete image files that are no longer referenced in products table
    public void cleanObsoleteImages(Context context) {
        try {
            File outputDir = context.getDir("item_images", Context.MODE_PRIVATE);
            File[] files = outputDir.listFiles();
            if (files == null) return;

            SQLiteDatabase db = dbHelper.getReadableDatabase();
            java.util.HashSet<String> activePaths = new java.util.HashSet<>();
            Cursor cursor = db.rawQuery("SELECT local_image_path, variations_json FROM products", null);
            while (cursor.moveToNext()) {
                String mainPath = cursor.getString(0);
                if (mainPath != null) {
                    activePaths.add(mainPath);
                }
                String varsJson = cursor.getString(1);
                if (varsJson != null && !varsJson.isEmpty()) {
                    try {
                        org.json.JSONArray varArray = new org.json.JSONArray(varsJson);
                        for (int v = 0; v < varArray.length(); v++) {
                            org.json.JSONObject varObj = varArray.getJSONObject(v);
                            String varLocalPath = varObj.optString("local_image_path", "");
                            if (!varLocalPath.isEmpty()) {
                                activePaths.add(varLocalPath);
                            }
                            String varImgUrlPath = varObj.optString("image_path", "");
                            if (!varImgUrlPath.isEmpty() && varImgUrlPath.contains("/")) {
                                File f = new File(varImgUrlPath);
                                if (f.isAbsolute()) {
                                    activePaths.add(varImgUrlPath);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing variations_json for activePaths: " + e.getMessage());
                    }
                }
            }
            cursor.close();


            int deleteCount = 0;
            for (File file : files) {
                // Don't delete .tmp files that are currently downloading
                if (file.getName().endsWith(".tmp")) {
                    continue;
                }
                String absolutePath = file.getAbsolutePath();
                if (!activePaths.contains(absolutePath)) {
                    if (file.delete()) {
                        deleteCount++;
                        Log.d(TAG, "Deleted obsolete image file: " + absolutePath);
                    }
                }
            }
            if (deleteCount > 0) {
                logSyncError(context, "Obsolete image cleanup: deleted " + deleteCount + " unused local image files.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning obsolete images: " + e.getMessage());
            logSyncError(context, "Obsolete image cleanup failed: " + e.getMessage());
        }
    }
}
