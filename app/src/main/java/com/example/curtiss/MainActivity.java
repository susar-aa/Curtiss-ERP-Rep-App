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
        edtEndOdo = findViewById(R.id.edtEndOdo);
        btnStartRoute = findViewById(R.id.btnStartRoute);
        btnEndRoute = findViewById(R.id.btnEndRoute);
        btnCancelRoute = findViewById(R.id.btnCancelRoute);
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
                showStartRouteDialog();
            }
        });

        // Odometer End Route Trigger
        btnEndRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEndRouteDialog();
            }
        });

        // Cancel Route Trigger
        if (btnCancelRoute != null) {
            btnCancelRoute.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cancelActiveRoute();
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

        // Pull to Refresh Implementation
        final androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(new androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    SyncManager.getInstance(MainActivity.this).startPullSync(
                            MainActivity.this,
                            representativeUserId,
                            new SyncManager.SyncListener() {
                                @Override
                                public void onSyncStarted() {
                                    if (txtSyncStatus != null) { txtSyncStatus.setText("Pulling fresh catalog data on pull-to-refresh..."); }
                                }

                                @Override
                                public void onSyncProgress(String message) {}

                                @Override
                                public void onSyncCompleted(boolean success, String message) {
                                    swipeRefreshLayout.setRefreshing(false);
                                    if (success) {
                                        if (txtSyncStatus != null) { txtSyncStatus.setText("Last synced: Just Now (Pull)"); }
                                        Toast.makeText(MainActivity.this, "Pull Sync Complete!", Toast.LENGTH_SHORT).show();
                                    } else {
                                        if (txtSyncStatus != null) { txtSyncStatus.setText("Sync Failed"); }
                                        Toast.makeText(MainActivity.this, "Pull Sync Failed: " + message, Toast.LENGTH_SHORT).show();
                                    }
                                    refreshDashboardState();
                                }
                            },
                            false
                    );
                }
            });
        }
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

    private void showStartRouteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_start_route, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.show();

        // Bind dialog views
        final EditText edtRouteSearch = dialogView.findViewById(R.id.edtRouteSearch);
        final ListView lstRoutes = dialogView.findViewById(R.id.lstRoutes);
        final TextView txtSelectedRoute = dialogView.findViewById(R.id.txtSelectedRoute);
        final EditText edtStartOdoDialog = dialogView.findViewById(R.id.edtStartOdoDialog);
        Button btnCancelDialog = dialogView.findViewById(R.id.btnCancelDialog);
        Button btnStartTripDialog = dialogView.findViewById(R.id.btnStartTripDialog);

        // Load route items
        final List<String> allRoutes = dbHelper.getTerritories();
        final List<String> filteredRoutes = new ArrayList<>(allRoutes);

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this, R.layout.item_route, filteredRoutes) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(R.id.txtRouteName);
                if (text == null && view instanceof TextView) {
                    text = (TextView) view;
                }
                if (text != null) {
                    text.setText(getItem(position));
                }
                return view;
            }
        };
        lstRoutes.setAdapter(adapter);

        // Search logic
        edtRouteSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filteredRoutes.clear();
                String filter = s.toString().toLowerCase().trim();
                for (String r : allRoutes) {
                    if (r.toLowerCase().contains(filter)) {
                        filteredRoutes.add(r);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Item selection
        final String[] selectedRouteHolder = { "" };
        lstRoutes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedRouteHolder[0] = filteredRoutes.get(position);
                txtSelectedRoute.setText("Selected: " + selectedRouteHolder[0]);
            }
        });

        btnCancelDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnStartTripDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String selectedRoute = selectedRouteHolder[0];
                final String odoStr = edtStartOdoDialog.getText().toString().trim();

                if (selectedRoute.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please select a territory route from the list.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (odoStr.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please enter the starting odometer mileage.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (odoStr.length() > 6) {
                    Toast.makeText(MainActivity.this, "Odometer mileage must be at most 6 digits.", Toast.LENGTH_SHORT).show();
                    return;
                }

                double startOdo = Double.parseDouble(odoStr);
                String startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                if (!LocationHelper.checkAndShowLocationSettings(MainActivity.this)) {
                    return;
                }

                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
                    return;
                }

                showProgressDialog("Acquiring GPS location...");
                final double finalStartOdo = startOdo;
                final String finalStartTime = startTime;
                LocationHelper.captureCurrentLocation(MainActivity.this, new LocationHelper.LocationResultListener() {
                    private boolean hasExecuted = false;

                    @Override
                    public void onLocationResult(double latitude, double longitude, boolean isFallback) {
                        synchronized (this) {
                            if (hasExecuted) return;
                            hasExecuted = true;
                        }
                        dismissProgressDialog();
                        if (isFallback) {
                            Toast.makeText(MainActivity.this, "⚠️ GPS unavailable. Route start location set to default.", Toast.LENGTH_LONG).show();
                        }
                        long localId = dbHelper.startRouteOffline(selectedRoute, finalStartOdo, finalStartTime, latitude, longitude);
                        if (localId > 0) {
                            Toast.makeText(MainActivity.this, "Daily Route Started Offline!\n" + selectedRoute + " (Odo: " + odoStr + " KM)", Toast.LENGTH_LONG).show();
                            LocationTrackingService.startService(MainActivity.this);
                            dialog.dismiss();
                            refreshDashboardState();
                        } else {
                            Toast.makeText(MainActivity.this, "Error starting route offline.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
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
                                final String baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");
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

            txtActiveRouteName.setText("Active: " + routeName);
            txtRouteStartTime.setText("Started: " + startTime);

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

    private void cancelActiveRoute() {
        if (activeRouteLocalId == -1) {
            Toast.makeText(this, "No active route to cancel.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if any invoice has been created on this route
        int invoicesCount = dbHelper.getRouteInvoicesCount(activeRouteLocalId);
        if (invoicesCount > 0) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Cannot Cancel Route")
                .setMessage("You cannot cancel this route because " + invoicesCount + " invoice(s) have already been created on it. Please end today's route instead.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        // Show confirmation dialog before deleting/cancelling the route
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cancel Started Route?")
            .setMessage("Are you sure you want to cancel the started route? This will delete the active route record from your device.")
            .setPositiveButton("Yes, Cancel", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    // Delete the active route record from daily_routes
                    int rowsDeleted = db.delete("daily_routes", "id = ? AND status = ?", new String[]{String.valueOf(activeRouteLocalId), "Active"});
                    if (rowsDeleted > 0) {
                        Toast.makeText(MainActivity.this, "Route Cancelled successfully!", Toast.LENGTH_SHORT).show();
                        refreshDashboardState();
                    } else {
                        Toast.makeText(MainActivity.this, "Error cancelling route.", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("No", null)
            .show();
    }

    private void showEndRouteDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
        builder.setTitle("END DAILY ROUTE");
        builder.setMessage("Enter the ending odometer mileage to complete and finalize today's route:");

        final EditText input = new EditText(MainActivity.this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Ending Odometer (KM)");
        input.setGravity(android.view.Gravity.CENTER);
        
        // Limit to 6 digits
        input.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(6) });
        builder.setView(input);

        // Fetch starting odometer to validate
        double startOdoTemp = 0.0;
        Cursor cRoute = dbHelper.getActiveRoute();
        if (cRoute.moveToFirst()) {
            startOdoTemp = cRoute.getDouble(cRoute.getColumnIndexOrThrow("start_meter"));
        }
        cRoute.close();
        final double startOdo = startOdoTemp;

        builder.setPositiveButton("Finalize Trip", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                String endOdoStr = input.getText().toString().trim();
                if (endOdoStr.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please enter ending odometer mileage.", Toast.LENGTH_SHORT).show();
                    return;
                }

                double endOdo = Double.parseDouble(endOdoStr);
                if (endOdo < startOdo) {
                    Toast.makeText(MainActivity.this, "Error: Ending mileage cannot be less than starting mileage (" + startOdo + " KM).", Toast.LENGTH_LONG).show();
                    return;
                }

                String endTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                if (!LocationHelper.checkAndShowLocationSettings(MainActivity.this)) {
                    return;
                }

                showProgressDialog("Acquiring GPS location...");
                final double finalEndOdo = endOdo;
                final String finalEndTime = endTime;
                LocationHelper.captureCurrentLocation(MainActivity.this, new LocationHelper.LocationResultListener() {
                    private boolean hasExecuted = false;

                    @Override
                    public void onLocationResult(double latitude, double longitude, boolean isFallback) {
                        synchronized (this) {
                            if (hasExecuted) return;
                            hasExecuted = true;
                        }
                        dismissProgressDialog();
                        if (isFallback) {
                            Toast.makeText(MainActivity.this, "⚠️ GPS unavailable. Route end location set to default.", Toast.LENGTH_LONG).show();
                        }
                        showRouteSummaryDialog(activeRouteLocalId, startOdo, finalEndOdo, finalEndTime, latitude, longitude);
                    }
                });
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showRouteSummaryDialog(final long routeId, final double startOdo, final double endOdo, final String endTime, final double endLat, final double endLng) {
        String routeName = "Unknown";
        String startTime = "N/A";
        Cursor cRoute = dbHelper.getActiveRoute();
        if (cRoute.moveToFirst()) {
            routeName = cRoute.getString(cRoute.getColumnIndexOrThrow("route_name"));
            startTime = cRoute.getString(cRoute.getColumnIndexOrThrow("start_time"));
        }
        cRoute.close();

        double totalDistance = endOdo - startOdo;
        int invoicesCount = dbHelper.getRouteInvoicesCount(routeId);
        int unproductiveCount = dbHelper.getRouteUnproductiveVisitsCount(routeId);
        double totalSales = dbHelper.getRouteSalesTotal(routeId);

        // Fetch payment method breakdown from payments table (actual collections)
        double cashSum = 0, chequeSum = 0, bankSum = 0;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        long serverRouteId = 0;
        Cursor cRouteServer = db.rawQuery("SELECT server_id FROM daily_routes WHERE id = ?", new String[]{String.valueOf(routeId)});
        if (cRouteServer.moveToFirst()) {
            serverRouteId = cRouteServer.getLong(0);
        }
        cRouteServer.close();

        Cursor cursor = db.rawQuery("SELECT payment_method, COALESCE(SUM(amount), 0.0) FROM payments WHERE local_route_id = ? OR (server_route_id = ? AND ? > 0) GROUP BY payment_method", 
                new String[]{String.valueOf(routeId), String.valueOf(serverRouteId), String.valueOf(serverRouteId)});
        while (cursor.moveToNext()) {
            String method = cursor.getString(0);
            double total = cursor.getDouble(1);
            if ("Cash".equalsIgnoreCase(method)) cashSum = total;
            else if ("Cheque".equalsIgnoreCase(method)) chequeSum = total;
            else if ("Bank Transfer".equalsIgnoreCase(method)) bankSum = total;
        }
        cursor.close();

        // Build detailed formatted message
        StringBuilder summary = new StringBuilder();
        summary.append("🗺️ Territory Route: ").append(routeName).append("\n\n");
        summary.append("🏁 Start Odometer: ").append(String.format(Locale.getDefault(), "%.1f KM", startOdo)).append("\n");
        summary.append("🏁 End Odometer: ").append(String.format(Locale.getDefault(), "%.1f KM", endOdo)).append("\n");
        summary.append("🚗 Total Distance: ").append(String.format(Locale.getDefault(), "%.1f KM", totalDistance)).append("\n\n");
        summary.append("⏱️ Start Time: ").append(startTime).append("\n");
        summary.append("⏱️ End Time: ").append(endTime).append("\n\n");
        summary.append("📄 Invoices Generated: ").append(invoicesCount).append(" Bills").append("\n");
        summary.append("🚫 Unproductive Visits: ").append(unproductiveCount).append(" Visits").append("\n");
        summary.append("💰 Gross Sales Total: ").append(String.format(Locale.getDefault(), "LKR %.2f", totalSales)).append("\n\n");
        summary.append("💳 Payment Mode Summary:\n");
        summary.append("   • Cash Collected: ").append(String.format(Locale.getDefault(), "LKR %.2f", cashSum)).append("\n");
        summary.append("   • Cheques Received: ").append(String.format(Locale.getDefault(), "LKR %.2f", chequeSum)).append("\n");
        summary.append("   • Bank Transfers: ").append(String.format(Locale.getDefault(), "LKR %.2f", bankSum)).append("\n\n");
        summary.append("Click 'Complete' to instantly finalize, upload to ERP, and close.");

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
        builder.setTitle("📋 ROUTE AUDIT SUMMARY");
        builder.setMessage(summary.toString());
        builder.setPositiveButton("Complete", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                // Save ended route details to local database first
                dbHelper.endRouteOffline(routeId, endOdo, endTime, endLat, endLng);
                LocationTrackingService.stopService(MainActivity.this);
                Toast.makeText(MainActivity.this, "Route saved offline. Opening manual sync screen...", Toast.LENGTH_LONG).show();
                if (txtSyncStatus != null) {
                    txtSyncStatus.setText("Offline Mode (Pending Upload)");
                }
                
                // Open the manual sync progress screen (SyncProgressActivity)
                Intent intent = new Intent(MainActivity.this, SyncProgressActivity.class);
                startActivity(intent);
                
                refreshDashboardState();
            }
        });
        builder.setCancelable(false);
        builder.show();
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
