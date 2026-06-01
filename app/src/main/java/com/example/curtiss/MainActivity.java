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

public class MainActivity extends AppCompatActivity {

    private TextView txtSalesTotal, txtBillsCount, txtPendingSyncCount, txtSyncStatus, txtActiveRouteName, txtRouteStartTime;
    private LinearLayout layoutStartRoute, layoutEndRoute;
    private EditText edtEndOdo;
    private Button btnStartRoute, btnEndRoute, btnSyncNow;
    private ProgressBar progressSync;

    private DatabaseHelper dbHelper;
    private long activeRouteLocalId = -1;
    private int representativeUserId = 12; // Dynamic user ID mapped for rep context

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Fetch dynamic rep context from authenticated session
        android.content.SharedPreferences prefs = getSharedPreferences("rep_session", MODE_PRIVATE);
        representativeUserId = prefs.getInt("user_id", 12);

        dbHelper = new DatabaseHelper(this);

        // Bind Views
        txtSalesTotal = findViewById(R.id.txtSalesTotal);
        txtBillsCount = findViewById(R.id.txtBillsCount);
        txtPendingSyncCount = findViewById(R.id.txtPendingSyncCount);
        txtSyncStatus = findViewById(R.id.txtSyncStatus);
        txtActiveRouteName = findViewById(R.id.txtActiveRouteName);
        txtRouteStartTime = findViewById(R.id.txtRouteStartTime);

        layoutStartRoute = findViewById(R.id.layoutStartRoute);
        layoutEndRoute = findViewById(R.id.layoutEndRoute);
        edtEndOdo = findViewById(R.id.edtEndOdo);
        btnStartRoute = findViewById(R.id.btnStartRoute);
        btnEndRoute = findViewById(R.id.btnEndRoute);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        progressSync = findViewById(R.id.progressSync);

        setupNavigationGrid();
        setupSyncController();

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
    }

    private long lastAutoSyncTime = 0;

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboardState();

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoSyncTime > 30000) { // 30-second request cooling
            lastAutoSyncTime = currentTime;
            triggerAutoSync();
        }
    }

    private void triggerAutoSync() {
        SyncManager.getInstance(MainActivity.this).startSync(
                MainActivity.this,
                representativeUserId,
                new SyncManager.SyncListener() {
                    @Override
                    public void onSyncStarted() {
                        // Silent background update - do not block user interface
                        txtSyncStatus.setText("Syncing offline data in background...");
                    }

                    @Override
                    public void onSyncProgress(String message) {
                        // Silent progress
                    }

                    @Override
                    public void onSyncCompleted(boolean success, String message) {
                        if (success) {
                            txtSyncStatus.setText("Last synced: Just Now (Auto)");
                            Toast.makeText(MainActivity.this, "Sync Complete: Products & Customers Updated!", Toast.LENGTH_SHORT).show();
                        } else {
                            txtSyncStatus.setText("Offline Mode (Sync failed)");
                        }
                        refreshDashboardState();
                    }
                }
        );
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

        findViewById(R.id.navProfile).setOnClickListener(new View.OnClickListener() {
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
    }

    private void setupSyncController() {
        btnSyncNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SyncManager.getInstance(MainActivity.this).startSync(
                        MainActivity.this,
                        representativeUserId,
                        new SyncManager.SyncListener() {
                            @Override
                            public void onSyncStarted() {
                                btnSyncNow.setEnabled(false);
                                progressSync.setVisibility(View.VISIBLE);
                                txtSyncStatus.setText("Syncing: Initializing bidirectional tunnel...");
                            }

                            @Override
                            public void onSyncProgress(String message) {
                                txtSyncStatus.setText("Syncing: " + message);
                            }

                            @Override
                            public void onSyncCompleted(boolean success, String message) {
                                btnSyncNow.setEnabled(true);
                                progressSync.setVisibility(View.GONE);
                                if (success) {
                                    txtSyncStatus.setText("Last synced: Just Now");
                                    Toast.makeText(MainActivity.this, "Sync Complete: Products & Customers Updated!", Toast.LENGTH_LONG).show();
                                } else {
                                    txtSyncStatus.setText("Sync Failed: Check server connections.");
                                    Toast.makeText(MainActivity.this, "Sync Error: " + message, Toast.LENGTH_LONG).show();
                                }
                                refreshDashboardState();
                            }
                        }
                );
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
                        "(SELECT COUNT(*) FROM invoices WHERE is_synced = 0) + " +
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

                // Compile summary statistics BEFORE completing the route in database
                showRouteSummaryDialog(activeRouteLocalId, startOdo, endOdo, endTime);

                // Save ending in database
                dbHelper.endRouteOffline(activeRouteLocalId, endOdo, endTime, endLat, endLng);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showRouteSummaryDialog(long routeId, double startOdo, double endOdo, String endTime) {
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

        // Fetch payment method breakdown
        double cashSum = 0, chequeSum = 0, bankSum = 0;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT payment_method, SUM(grand_total) FROM invoices WHERE route_id = " + routeId + " GROUP BY payment_method", null);
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
        summary.append("Trip successfully saved offline and queued for ERP synchronization!");

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
        builder.setTitle("📋 ROUTE AUDIT SUMMARY");
        builder.setMessage(summary.toString());
        builder.setPositiveButton("Done", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                refreshDashboardState();
            }
        });
        builder.setCancelable(false);
        builder.show();
    }
}
