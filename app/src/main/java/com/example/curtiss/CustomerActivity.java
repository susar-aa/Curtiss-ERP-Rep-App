package com.example.curtiss;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.MenuItem;
import java.util.ArrayList;
import java.util.List;

public class CustomerActivity extends AppCompatActivity {

    private EditText edtSearch, edtShopName, edtShopPhone, edtShopWhatsApp, edtShopAddressLine1, edtShopAddressLine2, edtShopAddressLine3;
    private ListView lstCustomers;
    private RelativeLayout layoutAddCustomerOverlay;
    private View cardFloatingSearch; // The floating search capsule
    private TextView txtGPSCoordinates, txtOverlayTitle;
    private Button btnCaptureGPS, btnCancelAdd, btnSaveCustomer;
    private CustomerModel editingCustomer = null;

    private DatabaseHelper dbHelper;
    private List<CustomerModel> customerList = new ArrayList<>();
    private CustomerAdapter adapter;

    private View layoutCustomerLoading;
    private double capturedLatitude = 0.0;
    private double capturedLongitude = 0.0;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(android.graphics.Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setContentView(R.layout.activity_customer);

        dbHelper = DatabaseHelper.getInstance(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Bind layouts
        layoutCustomerLoading = findViewById(R.id.layoutCustomerLoading);
        edtSearch = findViewById(R.id.edtSearch);
        lstCustomers = findViewById(R.id.lstCustomers);
        layoutAddCustomerOverlay = findViewById(R.id.layoutAddCustomerOverlay);
        cardFloatingSearch = findViewById(R.id.cardFloatingSearch);
        txtOverlayTitle = findViewById(R.id.txtOverlayTitle);

        edtShopName = findViewById(R.id.edtShopName);
        edtShopPhone = findViewById(R.id.edtShopPhone);
        edtShopWhatsApp = findViewById(R.id.edtShopWhatsApp);
        edtShopAddressLine1 = findViewById(R.id.edtShopAddressLine1);
        edtShopAddressLine2 = findViewById(R.id.edtShopAddressLine2);
        edtShopAddressLine3 = findViewById(R.id.edtShopAddressLine3);
        txtGPSCoordinates = findViewById(R.id.txtGPSCoordinates);

        btnCaptureGPS = findViewById(R.id.btnCaptureGPS);
        btnCancelAdd = findViewById(R.id.btnCancelAdd);
        btnSaveCustomer = findViewById(R.id.btnSaveCustomer);

        // Back button action
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // "+ New" FAB to reveal overlay form and HIDE search bar
        findViewById(R.id.fabAddCustomer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetForm();
                layoutAddCustomerOverlay.setVisibility(View.VISIBLE);
                if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.GONE);
            }
        });

        // Cancel button to hide overlay form and SHOW search bar
        btnCancelAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutAddCustomerOverlay.setVisibility(View.GONE);
                if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.VISIBLE);
            }
        });

        // Search action
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadCustomersFromLocal(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // GPS Capture
        btnCaptureGPS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureCoordinates();
            }
        });

        // Save Customer Trigger
        btnSaveCustomer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCustomer();
            }
        });

        loadCustomersFromLocal("");
    }

    // Handle Hardware Back Button so it smoothly closes the overlay without closing the screen
    @Override
    public void onBackPressed() {
        if (layoutAddCustomerOverlay != null && layoutAddCustomerOverlay.getVisibility() == View.VISIBLE) {
            layoutAddCustomerOverlay.setVisibility(View.GONE);
            if (cardFloatingSearch != null) {
                cardFloatingSearch.setVisibility(View.VISIBLE);
            }
        } else {
            super.onBackPressed();
        }
    }

    private void resetForm() {
        editingCustomer = null;
        if (txtOverlayTitle != null) {
            txtOverlayTitle.setText("Create New Customer");
        }
        edtShopName.setText("");
        edtShopPhone.setText("");
        edtShopWhatsApp.setText("");
        edtShopAddressLine1.setText("");
        edtShopAddressLine2.setText("");
        edtShopAddressLine3.setText("");
        txtGPSCoordinates.setText("GPS: Location Pending...");
        capturedLatitude = 0.0;
        capturedLongitude = 0.0;
    }

    private void populateFormForEdit(CustomerModel c) {
        editingCustomer = c;
        if (txtOverlayTitle != null) {
            txtOverlayTitle.setText("EDIT SHOP");
        }
        edtShopName.setText(c.name);
        edtShopPhone.setText(c.phone);
        edtShopWhatsApp.setText(c.whatsapp);

        // Split address by ", "
        String[] parts = c.address.split(", ");
        if (parts.length >= 3) {
            edtShopAddressLine1.setText(parts[0]);
            edtShopAddressLine2.setText(parts[1]);
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(parts[i]);
            }
            edtShopAddressLine3.setText(sb.toString());
        } else if (parts.length == 2) {
            edtShopAddressLine1.setText(parts[0]);
            edtShopAddressLine2.setText(parts[1]);
            edtShopAddressLine3.setText("");
        } else if (parts.length == 1) {
            edtShopAddressLine1.setText(parts[0]);
            edtShopAddressLine2.setText("");
            edtShopAddressLine3.setText("");
        } else {
            edtShopAddressLine1.setText("");
            edtShopAddressLine2.setText("");
            edtShopAddressLine3.setText("");
        }

        capturedLatitude = c.latitude;
        capturedLongitude = c.longitude;
        if (c.latitude != 0.0) {
            txtGPSCoordinates.setText(String.format("GPS: %.5f, %.5f (Tagged)", capturedLatitude, capturedLongitude));
        } else {
            txtGPSCoordinates.setText("GPS: Location Pending...");
        }

        layoutAddCustomerOverlay.setVisibility(View.VISIBLE);
        if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.GONE);
    }

    private void loadCustomersFromLocal(final String filter) {
        if (layoutCustomerLoading != null && (customerList == null || customerList.isEmpty())) {
            layoutCustomerLoading.setVisibility(View.VISIBLE);
        }

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                final List<CustomerModel> loaded = new ArrayList<>();
                try {
                    Cursor cursor = dbHelper.getCustomersByActiveRouteMainTerritory(filter);
                    while (cursor.moveToNext()) {
                        CustomerModel c = new CustomerModel();
                        c.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                        c.serverId = cursor.getInt(cursor.getColumnIndexOrThrow("server_id"));
                        c.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                        c.phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"));
                        c.whatsapp = cursor.getString(cursor.getColumnIndexOrThrow("whatsapp"));
                        c.address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                        c.territory = cursor.getString(cursor.getColumnIndexOrThrow("territory"));
                        c.latitude = cursor.getDouble(cursor.getColumnIndexOrThrow("latitude"));
                        c.longitude = cursor.getDouble(cursor.getColumnIndexOrThrow("longitude"));
                        c.isSynced = cursor.getInt(cursor.getColumnIndexOrThrow("is_synced"));
                        loaded.add(c);
                    }
                    cursor.close();
                } catch (Exception e) {
                    android.util.Log.e("CustomerActivity", "Error querying customers: " + e.getMessage());
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (CustomerActivity.this.isFinishing() || CustomerActivity.this.isDestroyed()) {
                            return;
                        }
                        if (layoutCustomerLoading != null) {
                            layoutCustomerLoading.setVisibility(View.GONE);
                        }
                        customerList.clear();
                        customerList.addAll(loaded);

                        if (adapter == null) {
                            adapter = new CustomerAdapter();
                            lstCustomers.setAdapter(adapter);
                        } else {
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        });
    }

    private void captureCoordinates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            return;
        }

        // Check if GPS is enabled
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = false;
        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {}

        if (!gpsEnabled) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Location Services Disabled")
                .setMessage("GPS/Location services are turned off on your device. Please turn on location services in your system settings to capture the accurate coordinates of this shop.")
                .setPositiveButton("Settings", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }

        txtGPSCoordinates.setText("GPS: Acquiring live coordinates...");
        LocationHelper.captureCurrentLocationStrict(this, new LocationHelper.LocationResultListener() {
            @Override
            public void onLocationResult(double latitude, double longitude, boolean isFallback) {
                if (isFallback || (latitude == 0.0 && longitude == 0.0)) {
                    capturedLatitude = 0.0;
                    capturedLongitude = 0.0;
                    txtGPSCoordinates.setText("GPS: Location Unavailable");
                    Toast.makeText(CustomerActivity.this, "⚠️ GPS coordinates could not be captured. Please ensure you are outdoors with a clear sky view.", Toast.LENGTH_LONG).show();
                } else {
                    capturedLatitude = latitude;
                    capturedLongitude = longitude;
                    txtGPSCoordinates.setText(String.format("GPS: %.5f, %.5f (Tagged)", capturedLatitude, capturedLongitude));
                    Toast.makeText(CustomerActivity.this, "Live Shop Location Captured!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void saveCustomer() {
        String name = edtShopName.getText().toString().trim();
        String phone = edtShopPhone.getText().toString().trim();
        String whatsapp = edtShopWhatsApp.getText().toString().trim();

        String adr1 = edtShopAddressLine1.getText().toString().trim();
        String adr2 = edtShopAddressLine2.getText().toString().trim();
        String adr3 = edtShopAddressLine3.getText().toString().trim();

        StringBuilder addressBuilder = new StringBuilder();
        if (!adr1.isEmpty()) addressBuilder.append(adr1);
        if (!adr2.isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(adr2);
        }
        if (!adr3.isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(adr3);
        }
        String address = addressBuilder.toString();

        if (name.isEmpty()) {
            Toast.makeText(this, "Customer Shop Name is mandatory.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingCustomer != null) {
            int rowsUpdated = dbHelper.updateCustomerOffline(
                    editingCustomer.id, name, phone, whatsapp, address, capturedLatitude, capturedLongitude
            );
            if (rowsUpdated > 0) {
                Toast.makeText(this, "Customer Updated Offline!", Toast.LENGTH_SHORT).show();
                layoutAddCustomerOverlay.setVisibility(View.GONE);
                if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.VISIBLE);
                editingCustomer = null;
                loadCustomersFromLocal("");
            } else {
                Toast.makeText(this, "Error updating customer locally.", Toast.LENGTH_SHORT).show();
            }
        } else {
            boolean hasActiveRoute = false;
            String routeTag = "";
            Cursor cRoute = dbHelper.getActiveRoute();
            if (cRoute.moveToFirst()) {
                routeTag = cRoute.getString(cRoute.getColumnIndexOrThrow("route_name"));
                hasActiveRoute = true;
            }
            cRoute.close();

            if (hasActiveRoute) {
                saveCustomerWithRoute(name, phone, whatsapp, address, routeTag);
            } else {
                List<String> routesList = dbHelper.getTerritories();
                if (routesList.isEmpty()) {
                    routesList.add("No routes available");
                }
                
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(CustomerActivity.this);
                builder.setTitle("Select Route");
                
                final android.widget.Spinner spnRoute = new android.widget.Spinner(CustomerActivity.this);
                android.widget.ArrayAdapter<String> routeAdapter = new android.widget.ArrayAdapter<String>(CustomerActivity.this, android.R.layout.simple_spinner_item, routesList);
                routeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spnRoute.setAdapter(routeAdapter);
                
                LinearLayout layout = new LinearLayout(CustomerActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                int padding = (int) (16 * getResources().getDisplayMetrics().density);
                layout.setPadding(padding, padding, padding, padding);
                layout.addView(spnRoute);
                builder.setView(layout);
                
                final String finalName = name;
                final String finalPhone = phone;
                final String finalWhatsapp = whatsapp;
                final String finalAddress = address;
                
                builder.setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String selectedRoute = "Negombo Territory";
                        if (spnRoute.getSelectedItem() != null) {
                            selectedRoute = spnRoute.getSelectedItem().toString().trim();
                        }
                        if (selectedRoute.equalsIgnoreCase("No routes available")) {
                            selectedRoute = "Negombo Territory";
                        }
                        saveCustomerWithRoute(finalName, finalPhone, finalWhatsapp, finalAddress, selectedRoute);
                    }
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            }
        }
    }

    private void saveCustomerWithRoute(String name, String phone, String whatsapp, String address, String routeTag) {
        long id = dbHelper.insertCustomerOffline(name, phone, whatsapp, address, routeTag, capturedLatitude, capturedLongitude);
        if (id > 0) {
            Toast.makeText(this, "Customer Saved Offline!", Toast.LENGTH_SHORT).show();
            layoutAddCustomerOverlay.setVisibility(View.GONE);
            if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.VISIBLE);
            loadCustomersFromLocal("");
        } else {
            Toast.makeText(this, "Error saving customer locally.", Toast.LENGTH_SHORT).show();
        }
    }



    // Helper Model
    private static class CustomerModel {
        int id, serverId, isSynced;
        String name, phone, whatsapp, address, territory;
        double latitude, longitude;
    }

    // Custom High-End Adapter
    private class CustomerAdapter extends BaseAdapter {
        @Override
        public int getCount() { return customerList.size(); }
        @Override
        public Object getItem(int position) { return customerList.get(position); }
        @Override
        public long getItemId(int position) { return customerList.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(CustomerActivity.this).inflate(R.layout.item_customer, parent, false);
            }

            final CustomerModel c = customerList.get(position);

            TextView lblCustName = convertView.findViewById(R.id.lblCustName);
            TextView lblTerritory = convertView.findViewById(R.id.lblTerritory);
            TextView lblAddress = convertView.findViewById(R.id.lblAddress);
            TextView lblGPS = convertView.findViewById(R.id.lblGPS);
            Button btnCall = convertView.findViewById(R.id.btnCall);
            Button btnWhatsApp = convertView.findViewById(R.id.btnWhatsApp);

            lblCustName.setText(c.name);
            lblTerritory.setText("Route: " + c.territory);
            lblAddress.setText(c.address.isEmpty() ? "No address specified." : c.address);

            if (c.latitude != 0.0) {
                lblGPS.setText(String.format("GPS Tagged: %.5f, %.5f", c.latitude, c.longitude));
                lblGPS.setTextColor(getResources().getColor(android.R.color.black));
            } else {
                lblGPS.setText("GPS Status: Not Tagged");
                lblGPS.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }

            // HIDE OR SHOW CALL BUTTON
            if (c.phone == null || c.phone.trim().isEmpty()) {
                btnCall.setVisibility(View.GONE);
            } else {
                btnCall.setVisibility(View.VISIBLE);
            }

            // HIDE OR SHOW WHATSAPP BUTTON (Fallback to phone if whatsapp is empty)
            String whatsappNum = (c.whatsapp != null && !c.whatsapp.trim().isEmpty()) ? c.whatsapp : c.phone;
            if (whatsappNum == null || whatsappNum.trim().isEmpty()) {
                btnWhatsApp.setVisibility(View.GONE);
            } else {
                btnWhatsApp.setVisibility(View.VISIBLE);
            }


            btnCall.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (c.phone == null || c.phone.trim().isEmpty()) {
                        Toast.makeText(CustomerActivity.this, "No phone number registered.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone));
                    startActivity(intent);
                }
            });

            btnWhatsApp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String number = c.whatsapp != null && !c.whatsapp.trim().isEmpty() ? c.whatsapp : c.phone;
                    if (number == null || number.trim().isEmpty()) {
                        Toast.makeText(CustomerActivity.this, "No WhatsApp number registered.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Clean prefix code
                    String formatted = number.replace("+", "").replace(" ", "");
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=" + formatted));
                    startActivity(intent);
                }
            });

            return convertView;
        }
    }

}