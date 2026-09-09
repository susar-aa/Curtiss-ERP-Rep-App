package com.example.curtiss;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class SplashActivity extends AppCompatActivity {

    private LinearLayout syncCard;
    private TextView txtSplashStatus;
    private TextView txtSplashDetail;
    private android.widget.ProgressBar splashProgress;
    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        syncCard = findViewById(R.id.syncCard);
        txtSplashStatus = findViewById(R.id.txtSplashStatus);
        txtSplashDetail = findViewById(R.id.txtSplashDetail);
        splashProgress = findViewById(R.id.splashProgress);

        prefs = SecurePreferences.getSessionPrefs(this);
        dbHelper = DatabaseHelper.getInstance(this);
        mainHandler = new Handler(Looper.getMainLooper());

        // Check for app updates first if online
        checkForUpdates();
    }

    private void runBackgroundCleanSync() {
        final int userId = prefs.getInt("user_id", 0);
        
        if (!isNetworkAvailable()) {
            txtSplashStatus.setText("Offline mode enabled");
            txtSplashDetail.setText("Network unavailable. Loading local cache...");
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(SplashActivity.this, "Offline Mode: Loaded cached local database.", Toast.LENGTH_LONG).show();
                    navigateToMain();
                }
            }, 1200);
            return;
        }

        // Online pull database sync
        boolean isFullSync = SyncManager.shouldRunDailyFullSync(this);
        SyncManager.getInstance(this).startPullSync(this, userId, new SyncManager.SyncListener() {
            @Override
            public void onSyncStarted() {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // Keep screen awake while syncing
                        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        
                        txtSplashStatus.setText("Initializing clean sync...");
                        txtSplashDetail.setText("Securing Plesk server connection...");
                        if (splashProgress != null) {
                            splashProgress.setIndeterminate(true);
                        }
                    }
                });
            }

            @Override
            public void onSyncProgress(final String message) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        txtSplashStatus.setText("Synchronizing Database");
                        txtSplashDetail.setText(message);
                        if (splashProgress != null) {
                            if (message != null && message.contains("%")) {
                                int startIdx = message.lastIndexOf('(');
                                int endIdx = message.lastIndexOf('%');
                                if (startIdx != -1 && endIdx != -1 && startIdx < endIdx) {
                                    try {
                                        String pct = message.substring(startIdx + 1, endIdx).trim();
                                        int val = Integer.parseInt(pct);
                                        splashProgress.setIndeterminate(false);
                                        splashProgress.setProgress(val);
                                    } catch (NumberFormatException e) {
                                        splashProgress.setIndeterminate(true);
                                    }
                                } else {
                                    splashProgress.setIndeterminate(true);
                                }
                            } else {
                                splashProgress.setIndeterminate(true);
                            }
                        }
                    }
                });
            }

            @Override
            public void onSyncCompleted(final boolean success, final String message) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // Clear keep screen awake flag
                        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                        if (splashProgress != null) {
                            splashProgress.setIndeterminate(false);
                            splashProgress.setProgress(100);
                        }
                        if (success) {
                            txtSplashStatus.setText("Sync Complete!");
                            txtSplashDetail.setText("Local database fully updated.");
                            mainHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    navigateToMain();
                                }
                            }, 800);
                        } else {
                            // Sync failed but allow offline bypass so field representative is not blocked
                            txtSplashStatus.setText("Sync suspended");
                            txtSplashDetail.setText(message);
                            Toast.makeText(SplashActivity.this, "Sync suspended: " + message + ". Accessing offline storage.", Toast.LENGTH_LONG).show();
                            mainHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    navigateToMain();
                                }
                            }, 2000);
                        }
                    }
                });
            }
        }, isFullSync);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                    return capabilities != null && (
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
                }
            } else {
                @SuppressWarnings("deprecation")
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        }
        return false;
    }

    private void navigateToLogin() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void navigateToMain() {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    // --- Version Update Check Logic ---

    private void checkForUpdates() {
        if (!isNetworkAvailable()) {
            android.util.Log.i("CurtissUpdate", "No network connection. Skipping update check.");
            proceedToNextActivity();
            return;
        }

        syncCard.setVisibility(View.VISIBLE);
        txtSplashStatus.setText("Checking for updates...");
        txtSplashDetail.setText("Contacting server...");

        String baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");
        String apiUrl = baseUrl + "/rep/release/api_latest_version";

        android.util.Log.i("CurtissUpdate", "Checking for updates via API: " + apiUrl);
        new CheckUpdateTask(baseUrl).execute(apiUrl);
    }

    private void proceedToNextActivity() {
        if (prefs.contains("user_id")) {
            runBackgroundCleanSync();
        } else {
            navigateToLogin();
        }
    }

    private String getAppVersionName() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            android.util.Log.i("CurtissUpdate", "Current installed app version name: " + versionName);
            return versionName;
        } catch (Exception e) {
            android.util.Log.e("CurtissUpdate", "Failed to retrieve app version name, defaulting to 1.0.0", e);
            return "1.0.0";
        }
    }

    private long getAppVersionCode() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                return getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            } else {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            }
        } catch (Exception e) {
            android.util.Log.e("CurtissUpdate", "Failed to retrieve app version code, defaulting to 1", e);
            return 1;
        }
    }

    private long getNormalizedVersionCode(String versionName) {
        if (versionName == null) return 0;
        String clean = versionName.split("-")[0];
        String[] parts = clean.split("\\.");
        long major = parts.length > 0 ? parseLongSafe(parts[0]) : 0;
        long minor = parts.length > 1 ? parseLongSafe(parts[1]) : 0;
        long patch = parts.length > 2 ? parseLongSafe(parts[2]) : 0;
        return major * 10000 + minor * 100 + patch;
    }

    private long parseLongSafe(String s) {
        try {
            return Long.parseLong(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private String getFileMD5(java.io.File file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            java.io.InputStream is = new java.io.FileInputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            is.close();
            byte[] md5sum = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : md5sum) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            android.util.Log.e("CurtissUpdate", "Failed to compute MD5 of file: " + file.getAbsolutePath(), e);
            return "";
        }
    }

    private boolean isUpdateAvailable(String currentVersion, String latestVersion) {
        android.util.Log.i("CurtissUpdate", "Comparing currentVersion: " + currentVersion + " with latestVersion: " + latestVersion);
        if (currentVersion == null || latestVersion == null) return false;
        
        // Remove text suffixes like "-beta", "-debug", etc.
        String currentClean = currentVersion.split("-")[0];
        String latestClean = latestVersion.split("-")[0];

        String[] currentParts = currentClean.split("\\.");
        String[] latestParts = latestClean.split("\\.");

        int length = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;

            if (latestPart > currentPart) {
                android.util.Log.i("CurtissUpdate", "Update available! " + latestVersion + " > " + currentVersion);
                return true;
            }
            if (currentPart > latestPart) {
                android.util.Log.i("CurtissUpdate", "Installed version is newer than latest release: " + currentVersion + " > " + latestVersion);
                return false;
            }
        }
        android.util.Log.i("CurtissUpdate", "Installed version is up to date: " + currentVersion + " == " + latestVersion);
        return false;
    }

    private class CheckUpdateTask extends AsyncTask<String, Void, String> {
        private final String baseUrl;

        public CheckUpdateTask(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        protected String doInBackground(String... urls) {
            HttpURLConnection conn = null;
            InputStream is = null;
            try {
                URL url = new URL(urls[0]);
                conn = (HttpURLConnection) url.openConnection();
                conn.setUseCaches(false);
                conn.setDefaultUseCaches(false);
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setRequestProperty("Pragma", "no-cache");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.connect();

                int responseCode = conn.getResponseCode();
                android.util.Log.i("CurtissUpdate", "Server API responded with HTTP code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
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
                android.util.Log.e("CurtissUpdate", "Error during background update check request", e);
            } finally {
                try {
                    if (is != null) is.close();
                } catch (IOException ignored) {}
                if (conn != null) conn.disconnect();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                android.util.Log.i("CurtissUpdate", "Raw API Response JSON: " + result);
                try {
                    JSONObject json = new JSONObject(result);
                    String latestVersion = json.getString("latestVersion");
                    String apkUrl = json.getString("apkUrl");
                    boolean forceUpdate = json.getBoolean("forceUpdate");
                    JSONArray notesArray = json.getJSONArray("releaseNotes");
                    String apkMd5 = json.optString("apkMd5", "");
                    long serverCode = json.optLong("latestVersionCode", 0);
                    long serverCodeNormalized = json.optLong("latestVersionCodeNormalized", 0);

                    ArrayList<String> notes = new ArrayList<>();
                    for (int i = 0; i < notesArray.length(); i++) {
                        notes.add(notesArray.getString(i));
                    }

                    // Dynamically rewrite host domain if using custom local server URL
                    if (apkUrl.startsWith("http")) {
                        try {
                            Uri parsedApk = Uri.parse(apkUrl);
                            Uri parsedBase = Uri.parse(baseUrl);
                            apkUrl = parsedBase.buildUpon()
                                .path(parsedBase.getPath() + parsedApk.getPath())
                                .build()
                                .toString();
                            apkUrl = apkUrl.replaceAll("(?<!https?:)/{2,}", "/");
                        } catch (Exception ignored) {}
                    }
                    android.util.Log.i("CurtissUpdate", "Resolved APK URL: " + apkUrl);

                    String currentVersion = getAppVersionName();
                    long currentCode = getAppVersionCode();
                    String currentPackage = getPackageName();
                    String serverPackage = json.optString("packageName", "");

                    android.util.Log.i("CurtissUpdate", "--- Update Diagnostics ---");
                    android.util.Log.i("CurtissUpdate", "Local Package: " + currentPackage + " | Server Package: " + serverPackage);
                    android.util.Log.i("CurtissUpdate", "Local Version Name: " + currentVersion + " | Server Version Name: " + latestVersion);
                    android.util.Log.i("CurtissUpdate", "Local Version Code (Build): " + currentCode + " | Server Version Code (Build): " + serverCode);

                    boolean updateNeeded = false;
                    if (serverCode > currentCode) {
                        if (serverPackage.isEmpty() || currentPackage.equalsIgnoreCase(serverPackage)) {
                            android.util.Log.i("CurtissUpdate", "Update needed: Server build version code " + serverCode + " > installed " + currentCode);
                            updateNeeded = true;
                        } else {
                            android.util.Log.i("CurtissUpdate", "Update bypassed: Package name mismatch (Local: " + currentPackage + " | Server: " + serverPackage + ")");
                        }
                    } else {
                        android.util.Log.i("CurtissUpdate", "No update needed: Server build version code " + serverCode + " <= installed " + currentCode);
                    }

                    if (updateNeeded) {
                        android.util.Log.i("CurtissUpdate", "New release detected. Launching UpdateActivity...");
                        Intent intent = new Intent(SplashActivity.this, UpdateActivity.class);
                        intent.putExtra("apk_url", apkUrl);
                        intent.putExtra("force_update", forceUpdate);
                        intent.putExtra("latest_version", latestVersion);
                        intent.putStringArrayListExtra("release_notes", notes);
                        startActivity(intent);
                        finish();
                        return;
                    }
                } catch (Exception e) {
                    android.util.Log.e("CurtissUpdate", "Failed to parse update check response JSON", e);
                }
            } else {
                android.util.Log.w("CurtissUpdate", "API response was null (request failed or timed out).");
            }
            proceedToNextActivity();
        }
    }
}
