package com.example.curtiss;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class UpdateActivity extends AppCompatActivity {

    private TextView txtUpdateVersion;
    private TextView txtReleaseNotes;
    private LinearLayout downloadProgressContainer;
    private ProgressBar downloadProgressBar;
    private TextView txtDownloadStatus;
    private LinearLayout buttonContainer;
    private Button btnUpdateNow;
    private Button btnUpdateLater;

    private String apkUrl;
    private boolean forceUpdate;
    private String latestVersion;
    private ArrayList<String> releaseNotes;
    private File apkFile;

    private static final int REQUEST_INSTALL_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);

        txtUpdateVersion = findViewById(R.id.txtUpdateVersion);
        txtReleaseNotes = findViewById(R.id.txtReleaseNotes);
        downloadProgressContainer = findViewById(R.id.downloadProgressContainer);
        downloadProgressBar = findViewById(R.id.downloadProgressBar);
        txtDownloadStatus = findViewById(R.id.txtDownloadStatus);
        buttonContainer = findViewById(R.id.buttonContainer);
        btnUpdateNow = findViewById(R.id.btnUpdateNow);
        btnUpdateLater = findViewById(R.id.btnUpdateLater);

        // Retrieve intent extras
        Intent intent = getIntent();
        apkUrl = intent.getStringExtra("apk_url");
        forceUpdate = intent.getBooleanExtra("force_update", false);
        latestVersion = intent.getStringExtra("latest_version");
        releaseNotes = intent.getStringArrayListExtra("release_notes");

        txtUpdateVersion.setText("Version " + latestVersion + " is ready to download");

        // Format release notes
        if (releaseNotes != null && !releaseNotes.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String note : releaseNotes) {
                sb.append("• ").append(note).append("\n");
            }
            txtReleaseNotes.setText(sb.toString().trim());
        } else {
            txtReleaseNotes.setText("• Bug fixes and performance improvements.");
        }

        // Handle force update configuration
        if (forceUpdate) {
            btnUpdateLater.setVisibility(View.GONE);
        } else {
            btnUpdateLater.setVisibility(View.VISIBLE);
            btnUpdateLater.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Bypass update and enter app
                    navigateToApp();
                }
            });
        }

        btnUpdateNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startApkDownload();
            }
        });

        // Initialize output file destination
        apkFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "app-update.apk");
    }

    @Override
    public void onBackPressed() {
        if (forceUpdate) {
            // Block back button if update is forced
            Toast.makeText(this, "A critical update is required to continue.", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }

    private void startApkDownload() {
        buttonContainer.setVisibility(View.GONE);
        downloadProgressContainer.setVisibility(View.VISIBLE);
        
        new DownloadTask(this).execute(apkUrl);
    }

    private void navigateToApp() {
        // Return to Splash to continue normal flow (which handles login redirect/main redirect)
        Intent intent = new Intent(UpdateActivity.this, SplashActivity.class);
        startActivity(intent);
        finish();
    }

    private void installApk() {
        if (!apkFile.exists()) {
            Toast.makeText(this, "Downloaded update package could not be found.", Toast.LENGTH_LONG).show();
            buttonContainer.setVisibility(View.VISIBLE);
            downloadProgressContainer.setVisibility(View.GONE);
            return;
        }

        // On Android 8.0 (API 26) or higher, check for unknown app sources permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_INSTALL_PERMISSION);
                return;
            }
        }

        // Trigger Android package installer package-archive Intent
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        Uri apkUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                apkFile
        );

        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        startActivity(installIntent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (getPackageManager().canRequestPackageInstalls()) {
                    installApk();
                } else {
                    Toast.makeText(this, "Permission to install unknown apps is required to apply updates.", Toast.LENGTH_LONG).show();
                    buttonContainer.setVisibility(View.VISIBLE);
                    downloadProgressContainer.setVisibility(View.GONE);
                }
            }
        }
    }

    private static class DownloadTask extends AsyncTask<String, Integer, Boolean> {
        private final UpdateActivity activity;

        public DownloadTask(UpdateActivity activity) {
            this.activity = activity;
        }

        @Override
        protected Boolean doInBackground(String... sUrl) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.setUseCaches(false);
                connection.setDefaultUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return false;
                }

                int fileLength = connection.getContentLength();
                input = connection.getInputStream();
                output = new FileOutputStream(activity.apkFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    if (isCancelled()) {
                        input.close();
                        return false;
                    }
                    total += count;
                    if (fileLength > 0) {
                        publishProgress((int) (total * 100 / fileLength));
                    }
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                return false;
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (IOException ignored) {}

                if (connection != null) connection.disconnect();
            }
            return true;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            activity.downloadProgressBar.setProgress(progress[0]);
            activity.txtDownloadStatus.setText("Downloading: " + progress[0] + "%");
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                activity.txtDownloadStatus.setText("Download completed! Initiating installation...");
                activity.installApk();
            } else {
                Toast.makeText(activity, "Failed to download update package. Please try again.", Toast.LENGTH_LONG).show();
                activity.buttonContainer.setVisibility(View.VISIBLE);
                activity.downloadProgressContainer.setVisibility(View.GONE);
            }
        }
    }
}
