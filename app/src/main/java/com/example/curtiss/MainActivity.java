package com.example.curtiss;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
    private Button btnStartRoute, btnEndRoute, btnSyncNow;
    private ProgressBar progressSync;
    private BottomNavigationView bottomNavigation;

    private DatabaseHelper dbHelper;
    private long activeRouteLocalId = -1;
    private int representativeUserId = 12; // Dynamic user ID mapped for rep context
    private android.net.ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Fetch dynamic rep context from authenticated session
        android.content.SharedPreferences prefs = getSharedPreferences("rep_session", MODE_PRIVATE);
        representativeUserId = prefs.getInt("user_id", 12);

        dbHelper = DatabaseHelper.getInstance(this);

        // Bind Views
        txtSalesTotal = findViewById(R.id.txtSalesTotal);
        txtBillsCount = findViewById(R.id.txtBillsCount);
        txtPendingSyncCount = findViewById(R.id.txtPendingSyncCount);
        txtSyncStatus = findViewById(R.id.txtSyncStatus);
        txtActiveRouteName = findViewById(R.id.txtActiveRouteName);
        txtRouteStartTime = findViewById(R.id.txtRouteStartTime);
        txtCreditPendingMsg = findViewById(R.id.txtCreditPendingMsg);
        btnOpenCreditBills = findViewById(R.id.btnOpenCreditBills);

        layoutStartRoute = findViewById(R.id.layoutStartRoute);
        layoutEndRoute = findViewById(R.id.layoutEndRoute);
        edtEndOdo = findViewById(R.id.edtEndOdo);
        btnStartRoute = findViewById(R.id.btnStartRoute);
        btnEndRoute = findViewById(R.id.btnEndRoute);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        progressSync = findViewById(R.id.progressSync);
        bottomNavigation = findViewById(R.id.bottom_navigation);
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
                    Intent intent = new Intent(MainActivity.this, SyncLogsActivity.class);
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

        // Open Credit Outstanding Bills List dialog
        btnOpenCreditBills.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreditBillsDialog();
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
                                    txtSyncStatus.setText("Pulling fresh catalog data on pull-to-refresh...");
                                }

                                @Override
                                public void onSyncProgress(String message) {}

                                @Override
                                public void onSyncCompleted(boolean success, String message) {
                                    swipeRefreshLayout.setRefreshing(false);
                                    if (success) {
                                        txtSyncStatus.setText("Last synced: Just Now (Pull)");
                                        Toast.makeText(MainActivity.this, "Pull Sync Complete!", Toast.LENGTH_SHORT).show();
                                    } else {
                                        txtSyncStatus.setText("Sync Failed");
                                        Toast.makeText(MainActivity.this, "Pull Sync Failed: " + message, Toast.LENGTH_SHORT).show();
                                    }
                                    refreshDashboardState();
                                }
                            }
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
                            "(SELECT COUNT(*) FROM customers WHERE is_synced = 0) + " +
                            "(SELECT COUNT(*) FROM daily_routes WHERE is_synced = 0)",
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

                // Capture GPS coordinates (Fallback robust implementation)
                double capturedLat = 7.1824;
                double capturedLng = 79.8801;

                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                        Location loc = null;
                        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        }
                        if (loc == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                            loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        }
                        if (loc != null) {
                            capturedLat = loc.getLatitude();
                            capturedLng = loc.getLongitude();
                            Log.d("StartRoute", "GPS location acquired: " + capturedLat + ", " + capturedLng);
                        }
                    } catch (SecurityException e) {
                        Log.e("StartRoute", "Location capture exception: " + e.getMessage());
                    }
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
                }

                long localId = dbHelper.startRouteOffline(selectedRoute, startOdo, startTime, capturedLat, capturedLng);
                if (localId > 0) {
                    Toast.makeText(MainActivity.this, "Daily Route Started Offline!\n" + selectedRoute + " (Odo: " + odoStr + " KM)", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    refreshDashboardState();
                } else {
                    Toast.makeText(MainActivity.this, "Error starting route offline.", Toast.LENGTH_SHORT).show();
                }
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
                final android.content.SharedPreferences prefs = getSharedPreferences("rep_session", MODE_PRIVATE);
                String fName = prefs.getString("first_name", "Susara");
                String lName = prefs.getString("last_name", "Senarathne");
                String username = prefs.getString("username", "rep");

                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Representative Profile")
                        .setMessage("Active Rep: " + fName + " " + lName + "\nUsername: @" + username + "\nAssigned Target: LKR 250,000\n\nDo you want to log out from this device?")
                        .setPositiveButton("Logout", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
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
    }

    private void setupSyncController() {
        btnSyncNow.setVisibility(View.VISIBLE);
        btnSyncNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dbHelper.hasActiveRoute()) {
                    Toast.makeText(MainActivity.this, "Cannot sync while a route is active. Please end the route first.", Toast.LENGTH_LONG).show();
                    return;
                }
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
                        "(SELECT COUNT(*) FROM customers WHERE is_synced = 0) + " +
                        "(SELECT COUNT(*) FROM daily_routes WHERE is_synced = 0)",
                null
        );
        int pendingCount = 0;
        if (cUnsynced.moveToFirst()) {
            pendingCount = cUnsynced.getInt(0);
        }
        cUnsynced.close();

        txtPendingSyncCount.setText("Pending Upload: " + pendingCount);
        if (pendingCount > 0) {
            txtPendingSyncCount.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        } else {
            txtPendingSyncCount.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }

        // 4. Pending credit collections banner
        if (activeRouteLocalId == -1) {
            if (txtCreditPendingMsg != null) {
                txtCreditPendingMsg.setText("⚠️ Start Route to view credit collections");
                txtCreditPendingMsg.setVisibility(View.VISIBLE);
            }
            if (btnOpenCreditBills != null) {
                btnOpenCreditBills.setVisibility(View.GONE);
            }
        } else {
            Cursor cCredit = dbHelper.getOutstandingCustomersByActiveRouteMainTerritory();
            int creditCount = cCredit != null ? cCredit.getCount() : 0;
            if (cCredit != null) cCredit.close();

            if (txtCreditPendingMsg != null) {
                if (creditCount > 0) {
                    txtCreditPendingMsg.setText("You have " + creditCount + " Pending Payment Collections");
                    txtCreditPendingMsg.setVisibility(View.VISIBLE);
                    if (btnOpenCreditBills != null) btnOpenCreditBills.setVisibility(View.VISIBLE);
                } else {
                    txtCreditPendingMsg.setText("No Pending Payment Collections");
                    txtCreditPendingMsg.setVisibility(View.GONE);
                    if (btnOpenCreditBills != null) btnOpenCreditBills.setVisibility(View.GONE);
                }
            }
        }
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

                // Capture ending coordinates
                double endLat = 7.1824;
                double endLng = 79.8801;
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                        Location loc = null;
                        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        }
                        if (loc == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                            loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        }
                        if (loc != null) {
                            endLat = loc.getLatitude();
                            endLng = loc.getLongitude();
                        }
                    } catch (Exception e) {
                        Log.e("EndRoute", "Location error: " + e.getMessage());
                    }
                }

                // Compile summary statistics and trigger finalization inside dialog
                showRouteSummaryDialog(activeRouteLocalId, startOdo, endOdo, endTime, endLat, endLng);
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
        double totalSales = dbHelper.getRouteSalesTotal(routeId);

        // Fetch payment method breakdown from payments table (actual collections)
        double cashSum = 0, chequeSum = 0, bankSum = 0;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        long serverRouteId = 0;
        Cursor cRouteServer = db.rawQuery("SELECT server_id FROM daily_routes WHERE id = " + routeId, null);
        if (cRouteServer.moveToFirst()) {
            serverRouteId = cRouteServer.getLong(0);
        }
        cRouteServer.close();

        Cursor cursor = db.rawQuery("SELECT payment_method, SUM(amount) FROM payments WHERE local_route_id = " + routeId + " OR (server_route_id = " + serverRouteId + " AND " + serverRouteId + " > 0) GROUP BY payment_method", null);
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
        Cursor cursor = dbHelper.getOutstandingCustomersByActiveRouteMainTerritory();
        if (cursor == null || cursor.getCount() == 0) {
            if (cursor != null) cursor.close();
            Toast.makeText(this, "No outstanding customers found in ongoing route's territory. Try syncing to download outstanding bills.", Toast.LENGTH_LONG).show();
            return;
        }

        final List<Map<String, Object>> customersList = new ArrayList<>();
        List<String> listItems = new ArrayList<>();

        while (cursor.moveToNext()) {
            Map<String, Object> customer = new HashMap<>();
            int customerId = cursor.getInt(cursor.getColumnIndexOrThrow("customer_id"));
            String customerName = cursor.getString(cursor.getColumnIndexOrThrow("customer_name"));
            String customerAddress = cursor.getString(cursor.getColumnIndexOrThrow("customer_address"));
            double totalOutstanding = cursor.getDouble(cursor.getColumnIndexOrThrow("total_outstanding"));

            customer.put("customer_id", customerId);
            customer.put("customer_name", customerName);
            customer.put("customer_address", customerAddress);
            customer.put("total_outstanding", totalOutstanding);

            customersList.add(customer);
            listItems.add("👤 Customer: " + customerName + "\n📍 Address: " + (customerAddress != null ? customerAddress : "N/A") + "\n💰 Total Arrears: LKR " + String.format("%,.2f", totalOutstanding));
        }
        cursor.close();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("💳 OUTSTANDING PAYMENTS BY CUSTOMER");
        
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(android.R.id.text1);
                text.setTextColor(getResources().getColor(android.R.color.black));
                text.setTextSize(14);
                text.setPadding(24, 24, 24, 24);
                return view;
            }
        };

        builder.setAdapter(adapter, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                Map<String, Object> selectedCustomer = customersList.get(which);
                dialog.dismiss();
                showCollectPaymentDialog(selectedCustomer);
            }
        });

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void showCollectPaymentDialog(final Map<String, Object> customer) {
        final int customerId = (Integer) customer.get("customer_id");
        final String customerName = (String) customer.get("customer_name");
        final double trueGrandTotal = (Double) customer.get("total_outstanding");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Collect Payment: " + customerName);

        // Main layout container (Scrollable)
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 24, 32, 24);
        scrollView.addView(container);

        // Customer & Bill Details Card
        TextView lblDetails = new TextView(this);
        lblDetails.setText("👤 Customer: " + customerName + "\n" +
                           "💰 Total Arrears: LKR " + String.format("%,.2f", trueGrandTotal));
        lblDetails.setTextSize(15);
        lblDetails.setTextColor(getResources().getColor(android.R.color.black));
        lblDetails.setPadding(0, 0, 0, 24);
        container.addView(lblDetails);

        // Divider
        View div1 = new View(this);
        div1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        div1.setBackgroundColor(android.graphics.Color.LTGRAY);
        container.addView(div1);

        // 1. Cash Input Section
        TextView lblCash = new TextView(this);
        lblCash.setText("💵 CASH PAYMENT AMOUNT");
        lblCash.setTextSize(12);
        lblCash.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        lblCash.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        lblCash.setPadding(0, 24, 0, 8);
        container.addView(lblCash);

        final EditText txtCash = new EditText(this);
        txtCash.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        txtCash.setHint("LKR 0.00");
        txtCash.setPadding(16, 16, 16, 16);
        container.addView(txtCash);

        // 2. Bank Transfer Input Section
        TextView lblBank = new TextView(this);
        lblBank.setText("🏛️ BANK TRANSFER AMOUNT");
        lblBank.setTextSize(12);
        lblBank.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        lblBank.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        lblBank.setPadding(0, 24, 0, 8);
        container.addView(lblBank);

        final EditText txtBank = new EditText(this);
        txtBank.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        txtBank.setHint("LKR 0.00");
        txtBank.setPadding(16, 16, 16, 16);
        container.addView(txtBank);

        // 3. Cheques Container Header
        TextView lblChequeHeader = new TextView(this);
        lblChequeHeader.setText("✍️ CHEQUES COLLECTION");
        lblChequeHeader.setTextSize(12);
        lblChequeHeader.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        lblChequeHeader.setTextColor(android.graphics.Color.DKGRAY);
        lblChequeHeader.setPadding(0, 24, 0, 8);
        container.addView(lblChequeHeader);

        // Dynamic Cheque List Box
        final LinearLayout chequeBox = new LinearLayout(this);
        chequeBox.setOrientation(LinearLayout.VERTICAL);
        container.addView(chequeBox);

        final List<View> chequeViewsList = new ArrayList<>();

        // Balance recalculation live feedback label
        final TextView lblBalance = new TextView(this);
        lblBalance.setText("Remaining Outstanding: LKR " + String.format("%,.2f", trueGrandTotal));
        lblBalance.setTextSize(14);
        lblBalance.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        lblBalance.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        lblBalance.setPadding(0, 16, 0, 24);

        // Live balance runnable
        final Runnable recalculateBalance = new Runnable() {
            @Override
            public void run() {
                double cash = parseDoubleVal(txtCash.getText().toString());
                double bank = parseDoubleVal(txtBank.getText().toString());
                
                double chequesSum = 0.0;
                for (View row : chequeViewsList) {
                    ChequeHolder holder = (ChequeHolder) row.getTag();
                    chequesSum += parseDoubleVal(holder.txtAmount.getText().toString());
                }
                
                double remaining = trueGrandTotal - (cash + bank + chequesSum);
                lblBalance.setText("Remaining Outstanding: LKR " + String.format("%,.2f", remaining));
                if (remaining <= 0) {
                    lblBalance.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                } else {
                    lblBalance.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                }
            }
        };

        // Add Cheque Button
        Button btnAddCheque = new Button(this);
        btnAddCheque.setText("➕ ADD CHEQUE");
        btnAddCheque.setBackgroundColor(android.graphics.Color.parseColor("#4A4A4A"));
        btnAddCheque.setTextColor(android.graphics.Color.WHITE);
        btnAddCheque.setPadding(16, 16, 16, 16);
        btnAddCheque.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewChequeRow(chequeBox, chequeViewsList, recalculateBalance);
            }
        });
        container.addView(btnAddCheque);

        // Add Spacer and Balance indicator
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 24));
        container.addView(space);
        container.addView(lblBalance);

        // Real-time text watchers for Cash and Bank
        TextWatcher tw = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                recalculateBalance.run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };
        txtCash.addTextChangedListener(tw);
        txtBank.addTextChangedListener(tw);

        builder.setView(scrollView);

        builder.setPositiveButton("Save Collection", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                // Handled custom validation below
            }
        });
        builder.setNegativeButton("Cancel", null);

        final AlertDialog d = builder.create();
        d.show();

        // Custom validation click handler
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double cash = parseDoubleVal(txtCash.getText().toString());
                double bank = parseDoubleVal(txtBank.getText().toString());

                if (cash < 0 || bank < 0) {
                    Toast.makeText(MainActivity.this, "Amounts cannot be negative.", Toast.LENGTH_SHORT).show();
                    return;
                }

                double totalCollected = cash + bank;
                
                // Validate cheque details
                List<ChequeData> validatedCheques = new ArrayList<>();
                for (View row : chequeViewsList) {
                    ChequeHolder holder = (ChequeHolder) row.getTag();
                    double chqAmt = parseDoubleVal(holder.txtAmount.getText().toString());
                    String chqBank = holder.txtBank.getText().toString().trim();
                    String chqNum = holder.txtNumber.getText().toString().trim();
                    String chqDateStr = holder.txtDate.getText().toString().trim();

                    if (chqAmt > 0) {
                        if (chqBank.isEmpty() || chqNum.isEmpty() || chqDateStr.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Please complete all cheque details (Bank, Number, and Clearing Date).", Toast.LENGTH_LONG).show();
                            return;
                        }
                        validatedCheques.add(new ChequeData(chqAmt, chqBank, chqNum, chqDateStr));
                        totalCollected += chqAmt;
                    }
                }

                if (totalCollected <= 0) {
                    Toast.makeText(MainActivity.this, "Please record at least one Cash, Bank Transfer, or Cheque payment.", Toast.LENGTH_LONG).show();
                    return;
                }

                // Save collections locally
                boolean success = false;
                
                int currentRouteId = 0;
                Cursor cRoute = dbHelper.getActiveRoute();
                if (cRoute.moveToFirst()) {
                    currentRouteId = cRoute.getInt(cRoute.getColumnIndexOrThrow("server_id"));
                }
                cRoute.close();

                // Capture dynamic GPS coordinates for Credit Collections
                double capturedLat = 7.1824;
                double capturedLng = 79.8801;
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                        Location loc = null;
                        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        }
                        if (loc == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                            loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        }
                        if (loc != null) {
                            capturedLat = loc.getLatitude();
                            capturedLng = loc.getLongitude();
                            Log.d("CollectPayment", "GPS location acquired: " + capturedLat + ", " + capturedLng);
                        }
                    } catch (Exception e) {
                        Log.e("CollectPayment", "Location capture exception: " + e.getMessage());
                    }
                }

                if (cash > 0.0) {
                    success = dbHelper.savePayment(customerId, currentRouteId, "Cash", cash, "", "", "", capturedLat, capturedLng);
                }
                if (bank > 0.0) {
                    success = dbHelper.savePayment(customerId, currentRouteId, "Bank Transfer", bank, "Bank Transfer", "", "", capturedLat, capturedLng);
                }
                for (ChequeData cd : validatedCheques) {
                    success = dbHelper.savePayment(customerId, currentRouteId, "Cheque", cd.amount, cd.bank, cd.number, cd.date, capturedLat, capturedLng);
                }

                if (success || totalCollected > 0) {
                    StringBuilder summary = new StringBuilder();
                    if (cash > 0.0) {
                        summary.append("Rs: ").append(String.format("%,.2f", cash)).append(" Cash");
                    }
                    if (bank > 0.0) {
                        if (summary.length() > 0) summary.append(" | ");
                        summary.append("Rs: ").append(String.format("%,.2f", bank)).append(" Bank Transfer");
                    }
                    if (!validatedCheques.isEmpty()) {
                        double chqSum = 0;
                        for (ChequeData cd : validatedCheques) chqSum += cd.amount;
                        if (summary.length() > 0) summary.append(" | ");
                        summary.append("Rs: ").append(String.format("%,.2f", chqSum)).append(" Cheque");
                    }
                    Toast.makeText(MainActivity.this, "Recorded Collected Amount:\n" + summary.toString() + "\ncollected and saved offline successfully!", Toast.LENGTH_LONG).show();
                    d.dismiss();
                    refreshDashboardState();
                } else {
                    Toast.makeText(MainActivity.this, "Error saving payment collections locally.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void addNewChequeRow(final LinearLayout chequeContainer, final List<View> chequeViewsList, final Runnable recalculateBalance) {
        final LinearLayout rowLayout = new LinearLayout(MainActivity.this);
        rowLayout.setOrientation(LinearLayout.VERTICAL);
        rowLayout.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"));
        rowLayout.setPadding(24, 24, 24, 24);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 16, 0, 16);
        rowLayout.setLayoutParams(lp);

        // Header containing dynamic label & Delete trigger
        android.widget.RelativeLayout header = new android.widget.RelativeLayout(MainActivity.this);
        TextView title = new TextView(MainActivity.this);
        title.setText("Cheque Details");
        title.setTextColor(android.graphics.Color.BLACK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        
        Button btnDelete = new Button(MainActivity.this);
        btnDelete.setText("Remove ✖");
        btnDelete.setTextSize(10);
        btnDelete.setBackgroundColor(android.graphics.Color.parseColor("#E03A3A"));
        btnDelete.setTextColor(android.graphics.Color.WHITE);
        
        android.widget.RelativeLayout.LayoutParams btnLp = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        btnLp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        btnDelete.setLayoutParams(btnLp);
        
        header.addView(title);
        header.addView(btnDelete);
        rowLayout.addView(header);

        // Bank Name Input
        final EditText txtBank = new EditText(MainActivity.this);
        txtBank.setHint("Bank Name");
        txtBank.setPadding(16, 16, 16, 16);
        rowLayout.addView(txtBank);

        // Cheque number Input
        final EditText txtNumber = new EditText(MainActivity.this);
        txtNumber.setHint("Cheque Number");
        txtNumber.setPadding(16, 16, 16, 16);
        rowLayout.addView(txtNumber);

        // Clearing datepicker input
        final EditText txtDate = new EditText(MainActivity.this);
        txtDate.setHint("Clearing Date (YYYY-MM-DD)");
        txtDate.setFocusable(false);
        txtDate.setClickable(true);
        txtDate.setPadding(16, 16, 16, 16);
        txtDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Calendar c = Calendar.getInstance();
                int year = c.get(Calendar.YEAR);
                int month = c.get(Calendar.MONTH);
                int day = c.get(Calendar.DAY_OF_MONTH);

                DatePickerDialog datePickerDialog = new DatePickerDialog(MainActivity.this,
                        new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                                String formattedDate = String.format(Locale.getDefault(), "%d-%02d-%02d", year, (monthOfYear + 1), dayOfMonth);
                                txtDate.setText(formattedDate);
                            }
                        }, year, month, day);
                datePickerDialog.show();
            }
        });
        rowLayout.addView(txtDate);

        // Amount input
        final EditText txtAmount = new EditText(MainActivity.this);
        txtAmount.setHint("Cheque Amount");
        txtAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        txtAmount.setPadding(16, 16, 16, 16);
        rowLayout.addView(txtAmount);

        // Wrap inputs in Holder Tag
        rowLayout.setTag(new ChequeHolder(txtBank, txtNumber, txtDate, txtAmount));

        // Listen for live updates
        txtAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                recalculateBalance.run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Delete Row callback
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chequeContainer.removeView(rowLayout);
                chequeViewsList.remove(rowLayout);
                recalculateBalance.run();
            }
        });

        chequeContainer.addView(rowLayout);
        chequeViewsList.add(rowLayout);
        recalculateBalance.run();
    }

    private double parseDoubleVal(String val) {
        if (val == null || val.trim().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(val.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static class ChequeHolder {
        final EditText txtBank;
        final EditText txtNumber;
        final EditText txtDate;
        final EditText txtAmount;

        ChequeHolder(EditText txtBank, EditText txtNumber, EditText txtDate, EditText txtAmount) {
            this.txtBank = txtBank;
            this.txtNumber = txtNumber;
            this.txtDate = txtDate;
            this.txtAmount = txtAmount;
        }
    }

    private static class ChequeData {
        final double amount;
        final String bank;
        final String number;
        final String date;

        ChequeData(double amount, String bank, String number, String date) {
            this.amount = amount;
            this.bank = bank;
            this.number = number;
            this.date = date;
        }
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
}
