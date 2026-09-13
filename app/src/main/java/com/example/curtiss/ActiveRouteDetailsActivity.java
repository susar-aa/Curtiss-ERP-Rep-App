package com.example.curtiss;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ActiveRouteDetailsActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private long activeRouteLocalId = -1;
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
        setContentView(R.layout.activity_active_route_details);

        dbHelper = DatabaseHelper.getInstance(this);

        TextView txtActiveRouteName = findViewById(R.id.txtActiveRouteName);
        TextView txtRouteStartTime = findViewById(R.id.txtRouteStartTime);
        final EditText edtEndOdo = findViewById(R.id.edtEndOdo);
        Button btnEndRoute = findViewById(R.id.btnEndRoute);
        Button btnCancelRoute = findViewById(R.id.btnCancelRoute);

        // Limit input to max 5 digits before decimal and 1 digit after
        edtEndOdo.setFilters(new android.text.InputFilter[] {
            new android.text.InputFilter() {
                @Override
                public CharSequence filter(CharSequence source, int start, int end, android.text.Spanned dest, int dstart, int dend) {
                    String currentText = dest.toString();
                    String resultingText = currentText.substring(0, dstart) + source.subSequence(start, end).toString() + currentText.substring(dend);
                    if (resultingText.isEmpty()) return null;
                    if (resultingText.equals(".")) return null;
                    
                    String regex = "^\\d{0,5}(\\.\\d{0,1})?$|^\\d{6}$";
                    if (!resultingText.matches(regex)) {
                        return "";
                    }
                    return null;
                }
            }
        });

        edtEndOdo.addTextChangedListener(new android.text.TextWatcher() {
            boolean isFormatting = false;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (isFormatting) return;
                String str = s.toString();
                String cleanString = str.replace(".", "");
                
                if (cleanString.length() == 6 && !str.contains(".")) {
                    isFormatting = true;
                    String formatted = cleanString.substring(0, 5) + "." + cleanString.substring(5);
                    s.replace(0, s.length(), formatted);
                    isFormatting = false;
                }
            }
        });

        // Fetch Active Route Details
        Cursor cursor = dbHelper.getActiveRoute();
        if (cursor.moveToFirst()) {
            activeRouteLocalId = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            String routeName = cursor.getString(cursor.getColumnIndexOrThrow("route_name"));
            String startTime = cursor.getString(cursor.getColumnIndexOrThrow("start_time"));

            txtActiveRouteName.setText("Active Route: " + routeName);
            txtRouteStartTime.setText("Started At: " + startTime);
        } else {
            Toast.makeText(this, "No active route found.", Toast.LENGTH_SHORT).show();
            finish();
        }
        cursor.close();

        btnEndRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String endOdoStr = edtEndOdo.getText().toString().trim();
                if (endOdoStr.isEmpty()) {
                    Toast.makeText(ActiveRouteDetailsActivity.this, "Please enter ending odometer mileage.", Toast.LENGTH_SHORT).show();
                    return;
                }

                double endOdo = Double.parseDouble(endOdoStr);
                
                // Fetch starting odometer to validate
                double startOdoTemp = 0.0;
                Cursor cRoute = dbHelper.getActiveRoute();
                if (cRoute.moveToFirst()) {
                    startOdoTemp = cRoute.getDouble(cRoute.getColumnIndexOrThrow("start_meter"));
                }
                cRoute.close();
                final double startOdo = startOdoTemp;

                if (endOdo < startOdo) {
                    Toast.makeText(ActiveRouteDetailsActivity.this, "Error: Ending mileage cannot be less than starting mileage (" + startOdo + " KM).", Toast.LENGTH_LONG).show();
                    return;
                }

                String endTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                if (!LocationHelper.checkAndShowLocationSettings(ActiveRouteDetailsActivity.this)) {
                    return;
                }
                
                if (ContextCompat.checkSelfPermission(ActiveRouteDetailsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ActiveRouteDetailsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
                    return;
                }

                showProgressDialog("Acquiring GPS location...");
                final double finalEndOdo = endOdo;
                final String finalEndTime = endTime;
                LocationHelper.captureCurrentLocation(ActiveRouteDetailsActivity.this, new LocationHelper.LocationResultListener() {
                    private boolean hasExecuted = false;

                    @Override
                    public void onLocationResult(double latitude, double longitude, boolean isFallback) {
                        synchronized (this) {
                            if (hasExecuted) return;
                            hasExecuted = true;
                        }
                        dismissProgressDialog();
                        if (isFallback) {
                            Toast.makeText(ActiveRouteDetailsActivity.this, "⚠️ GPS unavailable. Route end location set to default.", Toast.LENGTH_LONG).show();
                        }
                        showRouteSummaryDialog(activeRouteLocalId, startOdo, finalEndOdo, finalEndTime, latitude, longitude);
                    }
                });
            }
        });

        btnCancelRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelActiveRoute();
            }
        });
    }

    private void cancelActiveRoute() {
        if (activeRouteLocalId == -1) {
            Toast.makeText(this, "No active route to cancel.", Toast.LENGTH_SHORT).show();
            return;
        }

        int invoicesCount = dbHelper.getRouteInvoicesCount(activeRouteLocalId);
        if (invoicesCount > 0) {
            new AlertDialog.Builder(this)
                .setTitle("Cannot Cancel Route")
                .setMessage("You cannot cancel this route because " + invoicesCount + " invoice(s) have already been created on it. Please end today's route instead.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Cancel Started Route?")
            .setMessage("Are you sure you want to cancel the started route? This will delete the active route record from your device.")
            .setPositiveButton("Yes, Cancel", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    int rowsDeleted = db.delete("daily_routes", "id = ? AND status = ?", new String[]{String.valueOf(activeRouteLocalId), "Active"});
                    if (rowsDeleted > 0) {
                        Toast.makeText(ActiveRouteDetailsActivity.this, "Route Cancelled successfully!", Toast.LENGTH_SHORT).show();
                        finish(); // Return to MainActivity
                    } else {
                        Toast.makeText(ActiveRouteDetailsActivity.this, "Error cancelling route.", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("No", null)
            .show();
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

        StringBuilder summary = new StringBuilder();
        summary.append("🗺️ Territory Route: ").append(routeName).append("\n\n");
        summary.append("🚙 Start Odometer: ").append(String.format(Locale.getDefault(), "%.1f KM", startOdo)).append("\n");
        summary.append("🛑 End Odometer: ").append(String.format(Locale.getDefault(), "%.1f KM", endOdo)).append("\n");
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

        AlertDialog.Builder builder = new AlertDialog.Builder(ActiveRouteDetailsActivity.this);
        builder.setTitle("📋 ROUTE AUDIT SUMMARY");
        builder.setMessage(summary.toString());
        builder.setPositiveButton("Complete", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                dbHelper.endRouteOffline(routeId, endOdo, endTime, endLat, endLng);
                LocationTrackingService.stopService(ActiveRouteDetailsActivity.this);
                Toast.makeText(ActiveRouteDetailsActivity.this, "Route saved offline. Opening manual sync screen...", Toast.LENGTH_LONG).show();
                
                Intent intent = new Intent(ActiveRouteDetailsActivity.this, SyncProgressActivity.class);
                startActivity(intent);
                
                finish(); // Finish current activity so returning from sync goes to MainActivity
            }
        });
        builder.setCancelable(false);
        builder.show();
    }
}
