package com.example.curtiss;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StartRouteActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
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
        setContentView(R.layout.activity_start_route);

        dbHelper = DatabaseHelper.getInstance(this);

        final EditText edtRouteSearch = findViewById(R.id.edtRouteSearch);
        final ListView lstRoutes = findViewById(R.id.lstRoutes);
        final EditText edtStartOdoDialog = findViewById(R.id.edtStartOdoDialog);
        TextView btnCancelDialog = findViewById(R.id.btnCancelDialog);
        Button btnStartTripDialog = findViewById(R.id.btnStartTripDialog);
        
        final View layoutOdoPopupOverlay = findViewById(R.id.layoutOdoPopupOverlay);
        Button btnSelectRoutePopup = findViewById(R.id.btnSelectRoutePopup);

        // State variables
        final String[] selectedRouteHolder = { "" };
        final String[] validatedOdoHolder = { "" };

        // Load route items
        final List<String> allRoutes = dbHelper.getTerritories();
        final List<String> filteredRoutes = new ArrayList<>(allRoutes);

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_route, filteredRoutes) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = getLayoutInflater().inflate(R.layout.item_route, parent, false);
                }
                com.google.android.material.card.MaterialCardView cardView = (com.google.android.material.card.MaterialCardView) view;
                TextView txtRouteName = view.findViewById(R.id.txtRouteName);
                
                String currentItem = getItem(position);
                txtRouteName.setText(currentItem);
                
                if (currentItem != null && currentItem.equals(selectedRouteHolder[0])) {
                    // Highlight selected item
                    cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#E0F2FE")); // Light Blue background
                    cardView.setStrokeWidth(2);
                    cardView.setStrokeColor(android.graphics.Color.parseColor("#3B82F6")); // Blue border
                    txtRouteName.setTextColor(android.graphics.Color.parseColor("#1D4ED8")); // Dark Blue Text
                } else {
                    // Normal state
                    cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#F2F2F7"));
                    cardView.setStrokeWidth(0);
                    txtRouteName.setTextColor(android.graphics.Color.parseColor("#000000"));
                }
                return view;
            }
        };
        lstRoutes.setAdapter(adapter);

        // Auto-format 6th digit as decimal and restrict length
        edtStartOdoDialog.setFilters(new android.text.InputFilter[] {
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

        edtStartOdoDialog.addTextChangedListener(new TextWatcher() {
            boolean isFormatting = false;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
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

        // Popup logic
        layoutOdoPopupOverlay.setVisibility(View.VISIBLE);
        btnSelectRoutePopup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String odoStr = edtStartOdoDialog.getText().toString().trim();
                if (odoStr.isEmpty()) {
                    Toast.makeText(StartRouteActivity.this, "Please enter the starting odometer mileage.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (odoStr.length() > 7) {
                    Toast.makeText(StartRouteActivity.this, "Odometer mileage must be at most 6 digits.", Toast.LENGTH_SHORT).show();
                    return;
                }
                validatedOdoHolder[0] = odoStr;
                layoutOdoPopupOverlay.setVisibility(View.GONE);
            }
        });

        // Search logic
        edtRouteSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filteredRoutes.clear();
                String filter = s.toString().toLowerCase().trim();
                
                // If a route is selected, keep it at the top even while searching if it matches or if we just want it there
                if (!selectedRouteHolder[0].isEmpty() && selectedRouteHolder[0].toLowerCase().contains(filter)) {
                    filteredRoutes.add(selectedRouteHolder[0]);
                }
                
                for (String r : allRoutes) {
                    if (r.toLowerCase().contains(filter) && !r.equals(selectedRouteHolder[0])) {
                        filteredRoutes.add(r);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Item selection: move to top and highlight
        lstRoutes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String clickedRoute = filteredRoutes.get(position);
                selectedRouteHolder[0] = clickedRoute;
                
                // Reorder to put selected at top
                filteredRoutes.remove(clickedRoute);
                filteredRoutes.add(0, clickedRoute);
                
                // Also update allRoutes so if search is cleared, it stays on top? 
                // Or just let search logic handle it. We'll update allRoutes so it persists.
                allRoutes.remove(clickedRoute);
                allRoutes.add(0, clickedRoute);
                
                adapter.notifyDataSetChanged();
                lstRoutes.setSelection(0);
                lstRoutes.smoothScrollToPosition(0);
            }
        });

        btnCancelDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnStartTripDialog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String selectedRoute = selectedRouteHolder[0];
                final String odoStr = validatedOdoHolder[0];

                if (selectedRoute.isEmpty()) {
                    Toast.makeText(StartRouteActivity.this, "Please select a territory route from the list.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (odoStr.isEmpty()) {
                    // Should not happen as popup validates it, but just in case
                    layoutOdoPopupOverlay.setVisibility(View.VISIBLE);
                    Toast.makeText(StartRouteActivity.this, "Please enter odometer mileage.", Toast.LENGTH_SHORT).show();
                    return;
                }

                double startOdo = Double.parseDouble(odoStr);
                String startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                if (!LocationHelper.checkAndShowLocationSettings(StartRouteActivity.this)) {
                    return;
                }

                if (ContextCompat.checkSelfPermission(StartRouteActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(StartRouteActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
                    return;
                }

                showProgressDialog("Acquiring GPS location...");
                final double finalStartOdo = startOdo;
                final String finalStartTime = startTime;
                LocationHelper.captureCurrentLocation(StartRouteActivity.this, new LocationHelper.LocationResultListener() {
                    private boolean hasExecuted = false;

                    @Override
                    public void onLocationResult(double latitude, double longitude, boolean isFallback) {
                        synchronized (this) {
                            if (hasExecuted) return;
                            hasExecuted = true;
                        }
                        dismissProgressDialog();
                        if (isFallback) {
                            Toast.makeText(StartRouteActivity.this, "⚠️ GPS unavailable. Route start location set to default.", Toast.LENGTH_LONG).show();
                        }
                        long localId = dbHelper.startRouteOffline(selectedRoute, finalStartOdo, finalStartTime, latitude, longitude);
                        if (localId > 0) {
                            Toast.makeText(StartRouteActivity.this, "Daily Route Started Offline!\n" + selectedRoute + " (Odo: " + odoStr + " KM)", Toast.LENGTH_LONG).show();
                            LocationTrackingService.startService(StartRouteActivity.this);
                            finish();
                        } else {
                            Toast.makeText(StartRouteActivity.this, "Error starting route offline.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }
}
