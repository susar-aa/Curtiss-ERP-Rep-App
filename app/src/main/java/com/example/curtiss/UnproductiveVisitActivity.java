package com.example.curtiss;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UnproductiveVisitActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private AutoCompleteTextView autoCustomer;
    private ImageButton btnClearCustomer;
    private Spinner spinnerReason;
    private EditText edtCustomReason;
    private LinearLayout layoutCustomReason;
    private TextView txtRouteStatusTitle, txtActiveRouteName, txtRepName, txtVisitTime, txtGPSStatus;
    private Button btnSaveVisit;
    private View cardRouteInfo;

    private double capturedLat = 0.0;
    private double capturedLng = 0.0;
    private long activeRouteId = -1;
    private String activeRouteName = "";
    private int repUserId = 12;

    private final List<CustomerItem> customerList = new ArrayList<>();
    private final List<String> customerDisplayList = new ArrayList<>();
    private CustomerItem selectedCustomer = null;

    public static class CustomerItem {
        public long id;
        public String name;
        public String territory;
        public String displayString;

        public CustomerItem(long id, String name, String territory) {
            this.id = id;
            this.name = name;
            this.territory = territory;
            this.displayString = name + (territory != null && !territory.isEmpty() ? " (" + territory + ")" : "");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unproductive_visit);

        dbHelper = DatabaseHelper.getInstance(this);

        SharedPreferences prefs = SecurePreferences.getSessionPrefs(this);
        repUserId = prefs.getInt("user_id", 12);
        String fName = prefs.getString("first_name", "Susara");
        String lName = prefs.getString("last_name", "Senarathne");
        String fullRepName = fName + " " + lName;

        // Bind UI Elements
        autoCustomer = findViewById(R.id.autoCustomer);
        btnClearCustomer = findViewById(R.id.btnClearCustomer);
        spinnerReason = findViewById(R.id.spinnerReason);
        edtCustomReason = findViewById(R.id.edtCustomReason);
        layoutCustomReason = findViewById(R.id.layoutCustomReason);
        txtRouteStatusTitle = findViewById(R.id.txtRouteStatusTitle);
        txtActiveRouteName = findViewById(R.id.txtActiveRouteName);
        txtRepName = findViewById(R.id.txtRepName);
        txtVisitTime = findViewById(R.id.txtVisitTime);
        txtGPSStatus = findViewById(R.id.txtGPSStatus);
        btnSaveVisit = findViewById(R.id.btnSaveVisit);
        cardRouteInfo = findViewById(R.id.cardRouteInfo);

        txtRepName.setText(fullRepName);
        String currentTimeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        txtVisitTime.setText(currentTimeStr);

        // Back button
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Searchable Customer Input Handlers
        setupSearchableCustomerInput();

        // Resolve Active Route
        checkActiveRoute();

        // Load Customers for Active Route
        loadActiveRouteCustomers();

        // Predefined Reasons Setup
        setupReasonSpinner();

        // Start GPS Capture in background
        captureGPSLocation();

        // Save Button Handler
        btnSaveVisit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveUnproductiveVisit();
            }
        });
    }

    private void setupSearchableCustomerInput() {
        autoCustomer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (autoCustomer.getAdapter() != null && autoCustomer.getAdapter().getCount() > 0) {
                        autoCustomer.showDropDown();
                    }
                }
                return false;
            }
        });

        autoCustomer.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && autoCustomer.getAdapter() != null && autoCustomer.getAdapter().getCount() > 0) {
                    autoCustomer.showDropDown();
                }
            }
        });

        autoCustomer.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selectedStr = (String) parent.getItemAtPosition(position);
                selectedCustomer = findCustomerByDisplayString(selectedStr);
                if (selectedCustomer != null) {
                    btnClearCustomer.setVisibility(View.VISIBLE);
                }
            }
        });

        autoCustomer.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    selectedCustomer = null;
                    btnClearCustomer.setVisibility(View.GONE);
                } else {
                    btnClearCustomer.setVisibility(View.VISIBLE);
                    if (selectedCustomer != null && !selectedCustomer.displayString.equalsIgnoreCase(query)) {
                        selectedCustomer = null;
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearCustomer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoCustomer.setText("");
                selectedCustomer = null;
                btnClearCustomer.setVisibility(View.GONE);
                autoCustomer.requestFocus();
                if (autoCustomer.getAdapter() != null && autoCustomer.getAdapter().getCount() > 0) {
                    autoCustomer.showDropDown();
                }
            }
        });
    }

    private CustomerItem findCustomerByDisplayString(String displayStr) {
        if (displayStr == null) return null;
        for (CustomerItem c : customerList) {
            if (c.displayString.equalsIgnoreCase(displayStr) || c.name.equalsIgnoreCase(displayStr)) {
                return c;
            }
        }
        return null;
    }

    private void checkActiveRoute() {
        Cursor cursor = dbHelper.getActiveRoute();
        if (cursor != null && cursor.moveToFirst()) {
            activeRouteId = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            activeRouteName = cursor.getString(cursor.getColumnIndexOrThrow("route_name"));
            txtRouteStatusTitle.setText("Active Territory Route");
            txtActiveRouteName.setText(activeRouteName);
            cursor.close();
        } else {
            if (cursor != null) cursor.close();
            activeRouteId = -1;
            txtRouteStatusTitle.setText("⚠️ NO ACTIVE ROUTE");
            txtActiveRouteName.setText("You must START a daily territory route before logging visits.");
            btnSaveVisit.setEnabled(false);
            btnSaveVisit.setAlpha(0.5f);
            autoCustomer.setEnabled(false);
        }
    }

    private void loadActiveRouteCustomers() {
        customerList.clear();
        customerDisplayList.clear();

        Cursor cursor = dbHelper.getCustomersByActiveRouteMainTerritory("");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                String territory = cursor.getString(cursor.getColumnIndexOrThrow("territory"));
                CustomerItem item = new CustomerItem(id, name, territory);
                customerList.add(item);
                customerDisplayList.add(item.displayString);
            }
            cursor.close();
        }

        if (customerDisplayList.isEmpty()) {
            autoCustomer.setHint("No active customers found on current route");
            autoCustomer.setEnabled(false);
        } else {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, customerDisplayList);
            autoCustomer.setAdapter(adapter);
        }
    }

    private void setupReasonSpinner() {
        final String[] reasons = new String[]{
                "-- Select Reason --",
                "Shop Closed",
                "Owner Not Available",
                "No Stock Required",
                "Already Purchased from Another Supplier",
                "Customer Refused",
                "Holiday",
                "Temporary Closed",
                "Other"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, reasons);
        spinnerReason.setAdapter(adapter);

        spinnerReason.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = reasons[position];
                if ("Other".equals(selected)) {
                    layoutCustomReason.setVisibility(View.VISIBLE);
                } else {
                    layoutCustomReason.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void captureGPSLocation() {
        txtGPSStatus.setText("Acquiring GPS...");
        LocationHelper.captureCurrentLocation(this, new LocationHelper.LocationResultListener() {
            @Override
            public void onLocationResult(double latitude, double longitude, boolean isFallback) {
                capturedLat = latitude;
                capturedLng = longitude;
                if (isFallback) {
                    txtGPSStatus.setText(String.format(Locale.US, "%.4f, %.4f (Default)", latitude, longitude));
                } else {
                    txtGPSStatus.setText(String.format(Locale.US, "%.4f, %.4f (Tagged)", latitude, longitude));
                }
            }
        });
    }

    private void saveUnproductiveVisit() {
        if (!LocationHelper.checkAndShowLocationSettings(this)) {
            return;
        }

        if (activeRouteId == -1) {
            new AlertDialog.Builder(this)
                    .setTitle("Route Required")
                    .setMessage("You must START a daily territory route before recording unproductive customer visits.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        // Validate customer selection
        String inputQuery = autoCustomer.getText().toString().trim();
        if (selectedCustomer == null && !inputQuery.isEmpty()) {
            selectedCustomer = findCustomerByDisplayString(inputQuery);
        }

        if (selectedCustomer == null) {
            Toast.makeText(this, "Please search and select a valid customer shop.", Toast.LENGTH_SHORT).show();
            autoCustomer.requestFocus();
            if (autoCustomer.getAdapter() != null && autoCustomer.getAdapter().getCount() > 0) {
                autoCustomer.showDropDown();
            }
            return;
        }

        int reasonPos = spinnerReason.getSelectedItemPosition();
        if (reasonPos <= 0) {
            Toast.makeText(this, "Please select a reason for unproductive visit.", Toast.LENGTH_SHORT).show();
            return;
        }

        String reason = spinnerReason.getSelectedItem().toString();
        String customReason = edtCustomReason.getText().toString().trim();

        if ("Other".equals(reason) && TextUtils.isEmpty(customReason)) {
            Toast.makeText(this, "Please enter custom reason details for 'Other'.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validation 1: Customer already has a bill on this route
        if (dbHelper.hasBilledCustomerOnRoute(activeRouteId, selectedCustomer.id)) {
            new AlertDialog.Builder(this)
                    .setTitle("Billed Customer Notice")
                    .setMessage("An invoice has already been placed for " + selectedCustomer.name + " on current route (" + activeRouteName + "). Unproductive visits cannot be recorded for billed customers.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        // Validation 2: Customer already has an unproductive visit recorded on this route
        if (dbHelper.hasUnproductiveVisitOnRoute(activeRouteId, selectedCustomer.id)) {
            new AlertDialog.Builder(this)
                    .setTitle("Visit Already Recorded")
                    .setMessage("An unproductive visit has already been recorded for " + selectedCustomer.name + " on current route (" + activeRouteName + ") today.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        // Execute save
        btnSaveVisit.setEnabled(false);
        long resultId = dbHelper.insertUnproductiveVisit(activeRouteId, selectedCustomer.id, reason, customReason, capturedLat, capturedLng);

        if (resultId > 0) {
            Toast.makeText(this, "Unproductive Visit Recorded Offline!", Toast.LENGTH_LONG).show();
            
            // Trigger background push sync if network available
            SyncManager.getInstance(this).startManualPushSync(this, repUserId, null);
            
            finish();
        } else {
            btnSaveVisit.setEnabled(true);
            Toast.makeText(this, "Failed to save unproductive visit locally.", Toast.LENGTH_SHORT).show();
        }
    }
}
