package com.example.curtiss;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private LinearLayout syncCard;
    private TextView txtSplashStatus;
    private TextView txtSplashDetail;
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

        prefs = getSharedPreferences("rep_session", Context.MODE_PRIVATE);
        dbHelper = new DatabaseHelper(this);
        mainHandler = new Handler(Looper.getMainLooper());

        // Check login session state
        if (prefs.contains("user_id")) {
            // User is logged in: Show sync system and pull latest server registry
            syncCard.setVisibility(View.VISIBLE);
            runBackgroundCleanSync();
        } else {
            // User is not logged in: Hide sync progress card, show static splash for 1.5s then login
            syncCard.setVisibility(View.GONE);
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    navigateToLogin();
                }
            }, 1500);
        }
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

        // Online clean database sync
        SyncManager.getInstance(this).startCleanSync(this, userId, new SyncManager.SyncListener() {
            @Override
            public void onSyncStarted() {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        txtSplashStatus.setText("Initializing clean sync...");
                        txtSplashDetail.setText("Securing Plesk server connection...");
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
                    }
                });
            }

            @Override
            public void onSyncCompleted(final boolean success, final String message) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
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
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
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
}
