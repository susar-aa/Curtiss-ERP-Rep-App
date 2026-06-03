package com.example.curtiss;

import android.content.Context;
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
        this.dbHelper = new DatabaseHelper(context.getApplicationContext());
    }

    public static synchronized ImageDownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new ImageDownloadManager(context);
        }
        return instance;
    }

    // Schedule background download for a product image
    public void downloadProductImage(final Context context, final int productId, final String imageUrlString) {
        if (imageUrlString == null || imageUrlString.trim().isEmpty()) {
            return;
        }

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    // Resolve file target path inside app files directory
                    String fileName = "item_img_" + productId + ".jpg";
                    File outputDir = context.getDir("item_images", Context.MODE_PRIVATE);
                    File outputFile = new File(outputDir, fileName);

                    // Form absolute download URL
                    String absoluteUrl = imageUrlString;
                    if (!imageUrlString.startsWith("http")) {
                        // Extract filename from the path to avoid double public/webroot folder references
                        String filename = imageUrlString;
                        int lastSlash = imageUrlString.lastIndexOf('/');
                        if (lastSlash != -1) {
                            filename = imageUrlString.substring(lastSlash + 1);
                        }
                        android.content.SharedPreferences prefs = context.getSharedPreferences("rep_session", Context.MODE_PRIVATE);
                        String baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");
                        absoluteUrl = baseUrl + "/uploads/products/" + filename;
                    } else {
                        // If it starts with http, clean any erroneous "public/uploads/products" reference
                        if (imageUrlString.contains("public/uploads/products/")) {
                            absoluteUrl = imageUrlString.replace("public/uploads/products/", "uploads/products/");
                        }
                    }

                    Log.d(TAG, "Starting download for Product " + productId + " URL: " + absoluteUrl);

                    URL url = new URL(absoluteUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.connect();

                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        InputStream input = new BufferedInputStream(connection.getInputStream());
                        FileOutputStream output = new FileOutputStream(outputFile);

                        byte[] data = new byte[4096];
                        int count;
                        while ((count = input.read(data)) != -1) {
                            output.write(data, 0, count);
                        }

                        output.flush();
                        output.close();
                        input.close();

                        // Save local image path to database
                        String localPath = outputFile.getAbsolutePath();
                        dbHelper.updateProductLocalImagePath(productId, localPath);
                        Log.d(TAG, "Completed download for Product " + productId + " Saved: " + localPath);
                    } else {
                        Log.e(TAG, "Failed response code: " + connection.getResponseCode() + " for Product " + productId);
                    }
                    connection.disconnect();

                } catch (Exception e) {
                    Log.e(TAG, "Exception downloading image for Product " + productId + ": " + e.getMessage());
                }
            }
        });
    }
}
