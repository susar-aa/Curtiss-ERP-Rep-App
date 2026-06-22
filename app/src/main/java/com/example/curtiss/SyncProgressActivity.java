package com.example.curtiss;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SyncProgressActivity extends AppCompatActivity {

    private ImageView imgPhase1Status;
    private ImageView imgPhase2Status;
    private TextView txtPhase1Title;
    private TextView txtPhase2Title;
    private ProgressBar syncProgressBar;
    private TextView txtSyncProgressStatus;
    private TextView txtSyncProgressDetail;
    private Button btnSyncCancel;
    private Button btnSyncRetry;
    private Button btnSyncFinish;

    private SyncManager syncManager;
    private int userId;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_progress);

        imgPhase1Status = findViewById(R.id.imgPhase1Status);
        imgPhase2Status = findViewById(R.id.imgPhase2Status);
        txtPhase1Title = findViewById(R.id.txtPhase1Title);
        txtPhase2Title = findViewById(R.id.txtPhase2Title);
        syncProgressBar = findViewById(R.id.syncProgressBar);
        txtSyncProgressStatus = findViewById(R.id.txtSyncProgressStatus);
        txtSyncProgressDetail = findViewById(R.id.txtSyncProgressDetail);
        btnSyncCancel = findViewById(R.id.btnSyncCancel);
        btnSyncRetry = findViewById(R.id.btnSyncRetry);
        btnSyncFinish = findViewById(R.id.btnSyncFinish);

        syncManager = SyncManager.getInstance(this);
        mainHandler = new Handler(Looper.getMainLooper());

        SharedPreferences prefs = getSharedPreferences("rep_session", Context.MODE_PRIVATE);
        userId = prefs.getInt("user_id", -1);

        btnSyncCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnSyncRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSyncRetry.setVisibility(View.GONE);
                btnSyncCancel.setVisibility(View.VISIBLE);
                runManualSync();
            }
        });

        btnSyncFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (userId == -1) {
            Toast.makeText(this, "Session invalid. Please log in again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        DatabaseHelper dbHelper = DatabaseHelper.getInstance(this);
        if (dbHelper.hasActiveRoute()) {
            syncProgressBar.setVisibility(View.GONE);
            txtSyncProgressStatus.setText("Sync Blocked");
            txtSyncProgressDetail.setText("Please end your active route before synchronizing transactions.");
            btnSyncCancel.setVisibility(View.GONE);
            btnSyncRetry.setVisibility(View.GONE);
            btnSyncFinish.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Cannot sync while a route is active. Please end the route first.", Toast.LENGTH_LONG).show();
            return;
        }

        runManualSync();
    }

    private void runManualSync() {
        // Keep screen awake while syncing
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Reset UI status
        imgPhase1Status.setImageResource(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.presence_away).getResId());
        imgPhase1Status.setImageTintList(ColorStateList.valueOf(Color.parseColor("#F59E0B"))); // Orange
        txtPhase1Title.setTextColor(Color.parseColor("#FFFFFF"));

        imgPhase2Status.setImageResource(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.presence_invisible).getResId());
        imgPhase2Status.setImageTintList(ColorStateList.valueOf(Color.parseColor("#94A3B8"))); // Slate
        txtPhase2Title.setTextColor(Color.parseColor("#94A3B8"));

        syncProgressBar.setIndeterminate(true);
        txtSyncProgressStatus.setText("Initiating connection...");
        txtSyncProgressDetail.setText("Preparing offline transactions payload...");

        syncManager.startManualPushSync(this, userId, new SyncManager.SyncListener() {
            @Override
            public void onSyncStarted() {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        txtSyncProgressStatus.setText("Starting Manual Push Sync...");
                    }
                });
            }

            @Override
            public void onSyncProgress(final String message) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        txtSyncProgressStatus.setText(message);
                        if (message.contains("Phase 1") || message.contains("Uploading")) {
                            imgPhase1Status.setImageResource(android.R.drawable.presence_away);
                            imgPhase1Status.setImageTintList(ColorStateList.valueOf(Color.parseColor("#F59E0B"))); // Orange
                            txtSyncProgressDetail.setText("Packaging bills, routes, and payment collections...");
                        } else if (message.contains("Phase 2") || message.contains("Verifying")) {
                            // Phase 1 finished successfully
                            imgPhase1Status.setImageResource(android.R.drawable.presence_online);
                            imgPhase1Status.setImageTintList(ColorStateList.valueOf(Color.parseColor("#10B981"))); // Green
                            txtPhase1Title.setTextColor(Color.parseColor("#94A3B8"));

                            imgPhase2Status.setImageResource(android.R.drawable.presence_away);
                            imgPhase2Status.setImageTintList(ColorStateList.valueOf(Color.parseColor("#F59E0B"))); // Orange
                            txtPhase2Title.setTextColor(Color.parseColor("#FFFFFF"));
                            txtSyncProgressDetail.setText("Checking UUIDs against ERP database...");
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

                        syncProgressBar.setIndeterminate(false);
                        syncProgressBar.setProgress(100);
                        btnSyncCancel.setVisibility(View.GONE);

                        if (success) {
                            imgPhase1Status.setImageResource(android.R.drawable.presence_online);
                            imgPhase1Status.setImageTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));

                            imgPhase2Status.setImageResource(android.R.drawable.presence_online);
                            imgPhase2Status.setImageTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));
                            txtPhase2Title.setTextColor(Color.parseColor("#94A3B8"));

                            txtSyncProgressStatus.setText("Sync & Verification Complete!");
                            txtSyncProgressDetail.setText(message);
                            btnSyncFinish.setVisibility(View.VISIBLE);
                            Toast.makeText(SyncProgressActivity.this, "Sync Complete!", Toast.LENGTH_SHORT).show();
                        } else {
                            // Determine which phase failed based on text status
                            String currentStatus = txtSyncProgressStatus.getText().toString();
                            if (currentStatus.contains("Phase 2") || currentStatus.contains("Verifying")) {
                                imgPhase1Status.setImageResource(android.R.drawable.presence_online);
                                imgPhase1Status.setImageTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));

                                imgPhase2Status.setImageResource(android.R.drawable.presence_busy);
                                imgPhase2Status.setImageTintList(ColorStateList.valueOf(Color.parseColor("#EF4444"))); // Red
                            } else {
                                imgPhase1Status.setImageResource(android.R.drawable.presence_busy);
                                imgPhase1Status.setImageTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
                            }

                            txtSyncProgressStatus.setText("Sync Suspended / Failed");
                            txtSyncProgressDetail.setText(message);
                            btnSyncRetry.setVisibility(View.VISIBLE);
                            btnSyncFinish.setVisibility(View.VISIBLE);
                            Toast.makeText(SyncProgressActivity.this, "Sync Failed: " + message, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }
}
