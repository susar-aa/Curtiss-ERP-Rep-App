package com.example.curtiss;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class StatsActivity extends AppCompatActivity {

    private TextView txtLastSyncTime, txtProductsSynced, txtImagesCache, txtQueueStatus, txtDiagnosticsLog;
    private TextView txtInstalledVersionInfo, txtServerVersionInfo, txtUpdateSystemStatus;
    private Button btnClearLogs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        // Bind Views
        txtLastSyncTime = findViewById(R.id.txtLastSyncTime);
        txtProductsSynced = findViewById(R.id.txtProductsSynced);
        txtImagesCache = findViewById(R.id.txtImagesCache);
        txtQueueStatus = findViewById(R.id.txtQueueStatus);
        txtDiagnosticsLog = findViewById(R.id.txtDiagnosticsLog);
        btnClearLogs = findViewById(R.id.btnClearLogs);

        txtInstalledVersionInfo = findViewById(R.id.txtInstalledVersionInfo);
        txtServerVersionInfo = findViewById(R.id.txtServerVersionInfo);
        txtUpdateSystemStatus = findViewById(R.id.txtUpdateSystemStatus);

        // Load metrics and log contents
        loadDiagnostics();

        // Clear log action
        btnClearLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    File logFile = new File(getFilesDir(), "sync_diagnostics.txt");
                    if (logFile.exists()) {
                        logFile.delete();
                    }
                    txtDiagnosticsLog.setText("[No logs recorded yet]");
                    Toast.makeText(StatsActivity.this, "Diagnostics logs cleared.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(StatsActivity.this, "Failed to clear logs: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Back Button Setup
        findViewById(R.id.btnBackStats).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDiagnostics();
    }

    private void loadDiagnostics() {
        android.content.SharedPreferences prefs = getSharedPreferences("CurtissPrefs", MODE_PRIVATE);
        String lastSync = prefs.getString("sync_last_success_time", "Never");
        int totalProds = prefs.getInt("sync_total_products", 0);
        int updatedProds = prefs.getInt("sync_updated_products", 0);
        int downloadedImgs = prefs.getInt("sync_downloaded_images", 0);
        int failedImgs = prefs.getInt("sync_failed_images", 0);
        int pendingImgs = prefs.getInt("sync_pending_images", 0);

        // Query the database to get real-time pending queue status
        try {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
            android.database.sqlite.SQLiteDatabase db = dbHelper.getReadableDatabase();
            android.database.Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM image_download_queue WHERE status = 'pending' OR status = 'downloading'", null);
            if (cursor.moveToFirst()) {
                pendingImgs = cursor.getInt(0);
            }
            cursor.close();
        } catch (Exception e) {
            // fallback to shared preferences value
        }

        String lastDuration = prefs.getString("sync_last_duration", "");
        String lastStatus = prefs.getString("sync_last_status", "");

        String syncTimeDisplay = lastSync;
        if (!lastDuration.isEmpty()) {
            syncTimeDisplay += " (Duration: " + lastDuration + ")";
        }
        if (!lastStatus.isEmpty()) {
            syncTimeDisplay += " - " + lastStatus;
        }
        txtLastSyncTime.setText(syncTimeDisplay);

        txtProductsSynced.setText(totalProds + " total (" + updatedProds + " updated)");
        txtImagesCache.setText(downloadedImgs + " success (" + failedImgs + " failed)");
        txtQueueStatus.setText(pendingImgs + " pending downloads");

        if (pendingImgs > 0) {
            txtQueueStatus.setTextColor(android.graphics.Color.parseColor("#38BDF8")); // Sky blue
        } else {
            txtQueueStatus.setTextColor(android.graphics.Color.parseColor("#34D399")); // Emerald green
        }

        TextView txtOfflineSyncStatus = findViewById(R.id.txtOfflineSyncStatus);
        if (txtOfflineSyncStatus != null) {
            try {
                DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
                String pendingSummary = dbHelper.getPendingUploadsSummary();
                txtOfflineSyncStatus.setText(pendingSummary);
                if (pendingSummary.equals("All local data synced with server")) {
                    txtOfflineSyncStatus.setTextColor(android.graphics.Color.parseColor("#34D399")); // Emerald green
                } else {
                    txtOfflineSyncStatus.setTextColor(android.graphics.Color.parseColor("#F87171")); // Coral red
                }
            } catch (Exception e) {
                txtOfflineSyncStatus.setText("Unknown");
            }
        }

        // Read log file
        try {
            File logFile = new File(getFilesDir(), "sync_diagnostics.txt");
            if (logFile.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(logFile));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                txtDiagnosticsLog.setText(sb.toString().trim());
            } else {
                txtDiagnosticsLog.setText("[No logs recorded yet]");
            }
        } catch (Exception e) {
            txtDiagnosticsLog.setText("Error reading logs: " + e.getMessage());
        }

        // Load App Update System Diagnostics
        loadUpdateDiagnostics();
    }

    private void loadUpdateDiagnostics() {
        String versionName = "Unknown";
        long versionCode = 0;
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                versionCode = pInfo.getLongVersionCode();
            } else {
                versionCode = pInfo.versionCode;
            }
        } catch (Exception e) {
            // ignore
        }

        txtInstalledVersionInfo.setText("v" + versionName + " (Code: " + versionCode + ")");

        // Request update status from ERP server
        android.content.SharedPreferences sessionPrefs = getSharedPreferences("rep_session", MODE_PRIVATE);
        String baseUrl = sessionPrefs.getString("base_url", "https://curtiss.suzxlabs.com");
        String apiUrl = baseUrl + "/rep/release/api_latest_version";

        txtServerVersionInfo.setText("Fetching...");
        txtUpdateSystemStatus.setText("Checking for updates...");
        txtUpdateSystemStatus.setTextColor(android.graphics.Color.parseColor("#F59E0B")); // Amber

        final long localCode = versionCode;
        new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... urls) {
                HttpURLConnection conn = null;
                InputStream is = null;
                try {
                    URL url = new URL(urls[0]);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setUseCaches(false);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.connect();
                    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        is = conn.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        return sb.toString();
                    }
                } catch (Exception e) {
                    android.util.Log.e("StatsActivity", "Error checking update diagnostics", e);
                } finally {
                    try {
                        if (is != null) is.close();
                    } catch (Exception ignored) {}
                    if (conn != null) conn.disconnect();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null) {
                    try {
                        JSONObject json = new JSONObject(result);
                        String serverVersion = json.getString("latestVersion");
                        long serverCode = json.getLong("latestVersionCode");
                        boolean forceUpdate = json.getBoolean("forceUpdate");

                        String serverPackage = json.optString("packageName", "");
                        String currentPackage = getPackageName();

                        txtServerVersionInfo.setText("v" + serverVersion + " (Code: " + serverCode + ")");

                        if (serverCode > localCode && (serverPackage.isEmpty() || currentPackage.equalsIgnoreCase(serverPackage))) {
                            txtUpdateSystemStatus.setText("Update Available" + (forceUpdate ? " [REQUIRED]" : ""));
                            txtUpdateSystemStatus.setTextColor(android.graphics.Color.parseColor("#EF4444")); // Red
                        } else {
                            txtUpdateSystemStatus.setText("Up to Date");
                            txtUpdateSystemStatus.setTextColor(android.graphics.Color.parseColor("#34D399")); // Emerald Green
                        }
                    } catch (Exception e) {
                        txtServerVersionInfo.setText("Error");
                        txtUpdateSystemStatus.setText("Failed to parse response");
                        txtUpdateSystemStatus.setTextColor(android.graphics.Color.parseColor("#EF4444"));
                    }
                } else {
                    txtServerVersionInfo.setText("Unreachable");
                    txtUpdateSystemStatus.setText("Server offline or host unresolved");
                    txtUpdateSystemStatus.setTextColor(android.graphics.Color.parseColor("#EF4444"));
                }
            }
        }.execute(apiUrl);
    }
}
