package com.example.curtiss;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
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
import android.content.pm.PackageInstaller;
import android.app.PendingIntent;
import java.io.FileInputStream;

public class UpdateActivity extends AppCompatActivity {

    private TextView txtUpdateVersion;
    private Button btnWhatsNew;
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
    private ExecutorService executorService;

    private static final int REQUEST_INSTALL_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);
        executorService = Executors.newSingleThreadExecutor();

        txtUpdateVersion = findViewById(R.id.txtUpdateVersion);
        btnWhatsNew = findViewById(R.id.btnWhatsNew);
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

        btnWhatsNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showReleaseNotesDialog();
            }
        });

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    private void startApkDownload() {
        buttonContainer.setVisibility(View.GONE);
        downloadProgressContainer.setVisibility(View.VISIBLE);
        
        executeDownload(apkUrl);
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

        try {
            PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }

            int sessionId = packageInstaller.createSession(params);
            PackageInstaller.Session session = packageInstaller.openSession(sessionId);

            OutputStream out = session.openWrite("package", 0, -1);
            FileInputStream in = new FileInputStream(apkFile);
            byte[] buffer = new byte[65536];
            int c;
            while ((c = in.read(buffer)) != -1) {
                out.write(buffer, 0, c);
            }
            session.fsync(out);
            in.close();
            out.close();

            Intent intent = new Intent(this, InstallReceiver.class);
            intent.setAction(InstallReceiver.ACTION_INSTALL_COMPLETE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    1,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0)
            );

            session.commit(pendingIntent.getIntentSender());
            session.close();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start installation.", Toast.LENGTH_SHORT).show();
            buttonContainer.setVisibility(View.VISIBLE);
            downloadProgressContainer.setVisibility(View.GONE);
        }
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

    private void executeDownload(final String sUrl) {
        final Handler handler = new Handler(Looper.getMainLooper());
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                InputStream input = null;
                OutputStream output = null;
                HttpURLConnection connection = null;
                boolean result = false;
                try {
                    URL url = new URL(sUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setUseCaches(false);
                    connection.setDefaultUseCaches(false);
                    connection.setRequestProperty("Cache-Control", "no-cache");
                    connection.setRequestProperty("Pragma", "no-cache");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.connect();

                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        int fileLength = connection.getContentLength();
                        input = connection.getInputStream();
                        output = new FileOutputStream(apkFile);

                        byte[] data = new byte[4096];
                        long total = 0;
                        int count;
                        while ((count = input.read(data)) != -1) {
                            if (Thread.currentThread().isInterrupted()) {
                                input.close();
                                result = false;
                                break;
                            }
                            total += count;
                            if (fileLength > 0) {
                                final int progress = (int) (total * 100 / fileLength);
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (progress > 0 && downloadProgressBar.isIndeterminate()) {
                                            downloadProgressBar.setIndeterminate(false);
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                            downloadProgressBar.setProgress(progress, true);
                                        } else {
                                            downloadProgressBar.setProgress(progress);
                                        }
                                        txtDownloadStatus.setText("Downloading: " + progress + "%");
                                    }
                                });
                            }
                            output.write(data, 0, count);
                        }
                        if (!Thread.currentThread().isInterrupted()) {
                            result = true;
                        }
                    }
                } catch (Exception e) {
                    result = false;
                } finally {
                    try {
                        if (output != null) output.close();
                        if (input != null) input.close();
                    } catch (IOException ignored) {}

                    if (connection != null) connection.disconnect();
                }

                final boolean finalResult = result;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (finalResult) {
                            txtDownloadStatus.setText("Download completed! Initiating installation...");
                            installApk();
                        } else {
                            Toast.makeText(UpdateActivity.this, "Failed to download update package. Please try again.", Toast.LENGTH_LONG).show();
                            buttonContainer.setVisibility(View.VISIBLE);
                            downloadProgressContainer.setVisibility(View.GONE);
                        }
                    }
                });
            }
        });
    }
    private void showReleaseNotesDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_whats_new);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView txtModalReleaseNotes = dialog.findViewById(R.id.txtModalReleaseNotes);
        Button btnModalClose = dialog.findViewById(R.id.btnModalClose);

        if (releaseNotes != null && !releaseNotes.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String note : releaseNotes) {
                String trimmed = note.trim();
                if (trimmed.isEmpty()) {
                    continue; // Skip empty lines in visual display
                }
                
                int dashIndex = trimmed.indexOf(" – ");
                if (dashIndex == -1) dashIndex = trimmed.indexOf(" - ");
                
                if (dashIndex != -1) {
                    String title = trimmed.substring(0, dashIndex);
                    String body = trimmed.substring(dashIndex + 3);
                    sb.append("<b>").append(title).append("</b><br>").append(body).append("<br><br>");
                } else if (!trimmed.startsWith("•") && trimmed.length() < 30) {
                    // Make short headers bold
                    sb.append("<b>").append(trimmed).append("</b><br><br>");
                } else {
                    sb.append(trimmed).append("<br><br>");
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                txtModalReleaseNotes.setText(android.text.Html.fromHtml(sb.toString().trim(), android.text.Html.FROM_HTML_MODE_COMPACT));
            } else {
                txtModalReleaseNotes.setText(android.text.Html.fromHtml(sb.toString().trim()));
            }
        } else {
            txtModalReleaseNotes.setText("Bug fixes and performance improvements.");
        }

        btnModalClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}
