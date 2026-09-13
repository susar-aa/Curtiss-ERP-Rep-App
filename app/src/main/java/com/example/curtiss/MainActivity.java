package com.example.curtiss;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;
import android.widget.FrameLayout;
import android.content.res.ColorStateList;
import java.net.URL;
import java.net.HttpURLConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Calendar;
import android.app.DatePickerDialog;
import android.widget.DatePicker;


public class MainActivity extends AppCompatActivity {

    private TextView txtSalesTotal, txtBillsCount, txtPendingSyncCount, txtSyncStatus, txtActiveRouteName, txtRouteStartTime;
    private TextView txtCreditPendingMsg;
    private Button btnOpenCreditBills;
    private LinearLayout layoutStartRoute, layoutEndRoute;
    private EditText edtEndOdo;
    private Button btnStartRoute, btnEndRoute, btnCancelRoute;
    private View btnSyncNow;
    private ProgressBar progressSync;
    private BottomNavigationView bottomNavigation;
    private View cardCreditCollections;
    private LinearLayout layoutMetricsGrid;

    private DatabaseHelper dbHelper;
    private long activeRouteLocalId = -1;
    private int representativeUserId = 12; // Dynamic user ID mapped for rep context
    private android.net.ConnectivityManager.NetworkCallback networkCallback;

    private android.app.ProgressDialog progressDialog;

    private void showProgressDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new android.app.ProgressDialog(this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(android.graphics.Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setContentView(R.layout.activity_main);

        // Fetch dynamic rep context from authenticated session
        android.content.SharedPreferences prefs = SecurePreferences.getSessionPrefs(this);
        representativeUserId = prefs.getInt("user_id", 12);

        dbHelper = DatabaseHelper.getInstance(this);

        // Fetch FCM Token
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(new com.google.android.gms.tasks.OnCompleteListener<String>() {
                @Override
                public void onComplete(@NonNull com.google.android.gms.tasks.Task<String> task) {
                    if (!task.isSuccessful()) {
                        android.util.Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    android.util.Log.d("FCM", "FCM Token: " + token);
                    
                    // Save token to SharedPreferences
                    getSharedPreferences("fcm_prefs", MODE_PRIVATE)
                            .edit()
                            .putString("fcm_token", token)
                            .putBoolean("token_synced", false)
                            .apply();
                }
            });

        // Bind Views
        txtSalesTotal = findViewById(R.id.txtSalesTotal);
        txtBillsCount = findViewById(R.id.txtBillsCount);
        txtPendingSyncCount = findViewById(R.id.txtPendingSyncCount);
        // txtSyncStatus = findViewById(R.id.txtSyncStatus);
        txtActiveRouteName = findViewById(R.id.txtActiveRouteName);
        txtRouteStartTime = findViewById(R.id.txtRouteStartTime);
        txtCreditPendingMsg = findViewById(R.id.txtCreditPendingMsg);
        btnOpenCreditBills = findViewById(R.id.btnOpenCreditBills);

        layoutStartRoute = findViewById(R.id.layoutStartRoute);
        layoutEndRoute = findViewById(R.id.layoutEndRoute);
        btnStartRoute = findViewById(R.id.btnStartRoute);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        // progressSync = findViewById(R.id.progressSync);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        cardCreditCollections = findViewById(R.id.cardCreditCollections);
        layoutMetricsGrid = findViewById(R.id.layoutMetricsGrid);
        if (bottomNavigation != null) {
            bottomNavigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int itemId = item.getItemId();
                    if (itemId == R.id.nav_home) {
                        return true;
                    } else if (itemId == R.id.nav_customers) {
                        Intent intent = new Intent(MainActivity.this, CustomerActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        return true;
                    } else if (itemId == R.id.nav_history) {
                        Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        return true;
                    } else if (itemId == R.id.nav_dashboard) {
                        Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        return true;
                    }
                    return false;
                }
            });
        }

        setupNavigationGrid();
        setupSyncController();
        if (txtPendingSyncCount != null) {
            txtPendingSyncCount.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, SyncProgressActivity.class);
                    startActivity(intent);
                }
            });
        }

        // Enqueue WorkManager periodic background sync
        SyncManager.getInstance(this).enqueuePeriodicSync();

        // Register Network Callback to automatically trigger sync when network is restored
        registerNetworkCallback();

        // Odometer Start Route Trigger
        btnStartRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, StartRouteActivity.class));
            }
        });

        // Odometer Active Route Summary Card Trigger
        if (layoutEndRoute != null) {
            layoutEndRoute.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, ActiveRouteDetailsActivity.class));
                }
            });
        }

        // Open Credit Outstanding Bills List dialog
        btnOpenCreditBills.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activeRouteLocalId == -1) {
                    Toast.makeText(MainActivity.this, "Please START a daily route to view credit collections.", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(MainActivity.this, CreditBillsActivity.class));
            }
        });


    }

    private long lastAutoSyncTime = 0;

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }
        refreshDashboardState();

        // Check if there are any pending unsynced records to push
        int pendingCount = 0;
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cUnsynced = db.rawQuery(
                    "SELECT " +
                            "(SELECT COUNT(*) FROM invoices WHERE is_synced = 0 OR sync_status IN (1, 4)) + " +
                            "(SELECT COUNT(*) FROM customers WHERE (is_synced = 0 OR sync_status IN (1, 4)) AND (server_id = 0 OR is_profile_synced = 0)) + " +
                            "(SELECT COUNT(*) FROM daily_routes WHERE is_synced = 0 OR sync_status IN (1, 4)) + " +
                            "(SELECT COUNT(*) FROM payments WHERE is_synced = 0 OR sync_status IN (1, 4))",
                    null
            );
            if (cUnsynced.moveToFirst()) {
                pendingCount = cUnsynced.getInt(0);
            }
            cUnsynced.close();
        } catch (Exception e) {
            // Ignore safely
        }
    }

    private void setupNavigationGrid() {
        findViewById(R.id.txtAppNameHeader).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, StatsActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.navCustomers).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CustomerActivity.class));
            }
        });

        findViewById(R.id.navBilling).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activeRouteLocalId == -1) {
                    Toast.makeText(MainActivity.this, "You must START a daily territory route before billing.", Toast.LENGTH_LONG).show();
                    return;
                }
                startActivity(new Intent(MainActivity.this, BillingActivity.class));
            }
        });

        findViewById(R.id.navHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });

        findViewById(R.id.btnProfileHeader).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final android.content.SharedPreferences prefs = SecurePreferences.getSessionPrefs(MainActivity.this);
                String fName = prefs.getString("first_name", "Susara");
                String lName = prefs.getString("last_name", "Senarathne");
                String username = prefs.getString("username", "rep");

                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Representative Profile")
                        .setMessage("Active Rep: " + fName + " " + lName + "\nUsername: @" + username + "\n\nDo you want to log out from this device?")
                        .setPositiveButton("Logout", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                // Call server logout API in a background thread
                                final String baseUrl = prefs.getString("base_url", "https://falcon.trycurtiss.com");
                                final int userId = prefs.getInt("user_id", 0);
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        HttpURLConnection conn = null;
                                        try {
                                            URL url = new URL(baseUrl + "/rep/RepDashboard/api_logout");
                                            conn = (HttpURLConnection) url.openConnection();
                                            conn.setRequestMethod("POST");
                                            conn.setRequestProperty("Content-Type", "application/json");
                                            conn.setConnectTimeout(5000);
                                            conn.setReadTimeout(5000);
                                            conn.setDoOutput(true);
                                            
                                            org.json.JSONObject payload = new org.json.JSONObject();
                                            payload.put("user_id", userId);
                                            
                                            java.io.OutputStream os = conn.getOutputStream();
                                            os.write(payload.toString().getBytes("UTF-8"));
                                            os.flush();
                                            os.close();
                                            
                                            int responseCode = conn.getResponseCode();
                                            Log.d("MainActivity", "Server logout response code: " + responseCode);
                                        } catch (Exception e) {
                                            Log.e("MainActivity", "Server logout failed: " + e.getMessage());
                                        } finally {
                                            if (conn != null) {
                                                conn.disconnect();
                                            }
                                        }
                                    }
                                }).start();

                                // Stop location tracking service
                                LocationTrackingService.stopService(MainActivity.this);

                                // Clear secure session cache
                                prefs.edit().clear().apply();
                                Toast.makeText(MainActivity.this, "Session closed successfully.", Toast.LENGTH_SHORT).show();
                                
                                // Redirect back to LoginActivity
                                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            }
                        })
                        .setNegativeButton("Close", null)
                        .show();
            }
        });

        findViewById(R.id.navCatalog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, CatalogActivity.class));
            }
        });

        findViewById(R.id.navRouteHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isNetworkAvailable()) {
                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("Connection Required")
                            .setMessage("Internet connection required to view Route History.")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                startActivity(new Intent(MainActivity.this, RouteHistoryActivity.class));
            }
        });

        View navUnproductiveSales = findViewById(R.id.navUnproductiveSales);
        if (navUnproductiveSales != null) {
            navUnproductiveSales.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, UnproductiveVisitActivity.class));
                }
            });
        }

        View navInsights = findViewById(R.id.navInsights);
        if (navInsights != null) {
            navInsights.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isNetworkAvailable()) {
                        new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("Connection Required")
                                .setMessage("Internet connection required to view Rep Performance Insights.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    startActivity(new Intent(MainActivity.this, InsightsActivity.class));
                }
            });
        }
    }

    private void setupSyncController() {
        btnSyncNow.setVisibility(View.VISIBLE);
        btnSyncNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SyncProgressActivity.class);
                startActivity(intent);
            }
        });
    }

    private void refreshDashboardState() {

        // 1. Resolve Active Route
        Cursor cursor = dbHelper.getActiveRoute();
        if (cursor.moveToFirst()) {
            activeRouteLocalId = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            String routeName = cursor.getString(cursor.getColumnIndexOrThrow("route_name"));
            String startTime = cursor.getString(cursor.getColumnIndexOrThrow("start_time"));

            txtActiveRouteName.setText(routeName);
            txtRouteStartTime.setText("Started At: " + startTime);

            layoutStartRoute.setVisibility(View.GONE);
            layoutEndRoute.setVisibility(View.VISIBLE);
        } else {
            activeRouteLocalId = -1;
            layoutStartRoute.setVisibility(View.VISIBLE);
            layoutEndRoute.setVisibility(View.GONE);
        }
        cursor.close();

        // Conditional visibility for Metrics grid based on active route
        if (activeRouteLocalId == -1) {
            if (layoutMetricsGrid != null) {
                layoutMetricsGrid.setVisibility(View.GONE);
            }
        } else {
            if (layoutMetricsGrid != null) {
                layoutMetricsGrid.setVisibility(View.VISIBLE);
            }
        }

        // 2. Aggregate Sales Totals
        if (activeRouteLocalId != -1) {
            double totalSales = dbHelper.getRouteSalesTotal(activeRouteLocalId);
            int invoicesCount = dbHelper.getRouteInvoicesCount(activeRouteLocalId);

            txtSalesTotal.setText(String.format(Locale.getDefault(), "LKR %.2f", totalSales));
            txtBillsCount.setText(invoicesCount + " Invoices");
        } else {
            txtSalesTotal.setText("LKR 0.00");
            txtBillsCount.setText("No Active Route");
        }

        // 3. Aggregate Unsynced Items Counter
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cUnsynced = db.rawQuery(
                "SELECT " +
                        "(SELECT COUNT(*) FROM invoices WHERE is_synced = 0 OR sync_status IN (1, 4)) + " +
                        "(SELECT COUNT(*) FROM customers WHERE (is_synced = 0 OR sync_status IN (1, 4)) AND (server_id = 0 OR is_profile_synced = 0)) + " +
                        "(SELECT COUNT(*) FROM daily_routes WHERE is_synced = 0 OR sync_status IN (1, 4)) + " +
                        "(SELECT COUNT(*) FROM payments WHERE is_synced = 0 OR sync_status IN (1, 4))",
                null
        );
        int pendingCount = 0;
        if (cUnsynced.moveToFirst()) {
            pendingCount = cUnsynced.getInt(0);
        }
        cUnsynced.close();

        if (txtPendingSyncCount != null) {
            if (pendingCount > 0) {
                txtPendingSyncCount.setText("Sync (" + pendingCount + ")");
                txtPendingSyncCount.setTextColor(ContextCompat.getColor(this, R.color.ios_orange));
                if (btnSyncNow != null) {
                    btnSyncNow.setBackgroundResource(R.drawable.bg_pill_orange);
                }
            } else {
                txtPendingSyncCount.setText("Sync");
                txtPendingSyncCount.setTextColor(ContextCompat.getColor(this, R.color.ios_blue));
                if (btnSyncNow != null) {
                    btnSyncNow.setBackgroundResource(R.drawable.bg_pill_blue);
                }
            }
        }

        // 4. Pending credit collections banner
        if (activeRouteLocalId == -1) {
            if (cardCreditCollections != null) {
                cardCreditCollections.setVisibility(View.GONE);
            }
        } else {
            Cursor cCredit = dbHelper.getOutstandingCustomersByActiveRouteMainTerritory();
            int creditCount = cCredit != null ? cCredit.getCount() : 0;
            if (cCredit != null) cCredit.close();

            if (cardCreditCollections != null) {
                if (creditCount > 0) {
                    cardCreditCollections.setVisibility(View.VISIBLE);
                    if (txtCreditPendingMsg != null) {
                        txtCreditPendingMsg.setText("You have " + creditCount + " Pending Payment Collections");
                        txtCreditPendingMsg.setVisibility(View.VISIBLE);
                    }
                    if (btnOpenCreditBills != null) {
                        btnOpenCreditBills.setVisibility(View.VISIBLE);
                    }
                } else {
                    cardCreditCollections.setVisibility(View.GONE);
                }
            }
        }
    }

    private void showCreditBillsDialog() {
        if (activeRouteLocalId == -1) {
            Toast.makeText(this, "Please START a daily route to view credit collections.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(MainActivity.this, CreditBillsActivity.class);
        startActivity(intent);
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterNetworkCallback();
    }

    private void registerNetworkCallback() {
        try {
            android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
                        @Override
                        public void onAvailable(@androidx.annotation.NonNull android.net.Network network) {
                            super.onAvailable(network);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    android.util.Log.d("MainActivity", "Network available. Sync deferred to user manual initiation.");
                                }
                            });
                        }
                    };
                    connectivityManager.registerDefaultNetworkCallback(networkCallback);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to register network callback: " + e.getMessage());
        }
    }

    private void unregisterNetworkCallback() {
        try {
            if (networkCallback != null) {
                android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivityManager != null) {
                    connectivityManager.unregisterNetworkCallback(networkCallback);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to unregister network callback: " + e.getMessage());
        }
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
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
                android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        }
        return false;
    }
}
