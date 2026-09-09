package com.example.curtiss;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CreditCheckoutActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private int customerId;
    private String customerName;
    private double trueGrandTotal;
    private int currentRouteId = -1;

    private EditText edtCashAmount, edtBankAmount;
    private LinearLayout layoutChequesContainer;
    private TextView txtTotalToCollect;
    private Button btnAddCheque, btnConfirmCollection;

    private ProgressDialog progressDialog;

    private static class ChequeData {
        double amount;
        String bank;
        String number;
        String date;
        ChequeData(double amount, String bank, String number, String date) {
            this.amount = amount;
            this.bank = bank;
            this.number = number;
            this.date = date;
        }
    }

    private List<View> chequeViewsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credit_checkout);

        dbHelper = new DatabaseHelper(this);

        Toolbar toolbar = findViewById(R.id.toolbarCreditCheckout);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Collect Payment");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        customerId = getIntent().getIntExtra("customer_id", -1);
        customerName = getIntent().getStringExtra("customer_name");
        trueGrandTotal = getIntent().getDoubleExtra("total_outstanding", 0.0);

        if (customerId == -1) {
            Toast.makeText(this, "Error: Invalid Customer", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Cursor cRoute = dbHelper.getActiveRoute();
        if (cRoute.moveToFirst()) {
            currentRouteId = cRoute.getInt(cRoute.getColumnIndexOrThrow("id"));
        }
        cRoute.close();
        if (currentRouteId == -1) {
            Toast.makeText(this, "Please START a daily route to collect payments.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        TextView txtCheckoutCustomerDetails = findViewById(R.id.txtCheckoutCustomerDetails);
        txtCheckoutCustomerDetails.setText("👤 Customer: " + customerName + "\n💰 Total Arrears: LKR " + String.format(Locale.getDefault(), "%,.2f", trueGrandTotal));

        edtCashAmount = findViewById(R.id.edtCashAmount);
        edtBankAmount = findViewById(R.id.edtBankAmount);
        layoutChequesContainer = findViewById(R.id.layoutChequesContainer);
        txtTotalToCollect = findViewById(R.id.txtTotalToCollect);
        btnAddCheque = findViewById(R.id.btnAddCheque);
        btnConfirmCollection = findViewById(R.id.btnConfirmCollection);

        btnAddCheque.setOnClickListener(v -> addNewChequeRow());

        btnConfirmCollection.setOnClickListener(v -> handleConfirmCollection());
    }

    private void addNewChequeRow() {
        View chequeView = LayoutInflater.from(this).inflate(R.layout.item_cheque_input, layoutChequesContainer, false);
        
        Button btnRemoveCheque = chequeView.findViewById(R.id.btnRemoveCheque);
        EditText edtChequeDate = chequeView.findViewById(R.id.edtChequeDate);

        edtChequeDate.setOnClickListener(v -> {
            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);
            DatePickerDialog datePickerDialog = new DatePickerDialog(CreditCheckoutActivity.this,
                    (view, year1, monthOfYear, dayOfMonth) -> {
                        String formattedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year1, monthOfYear + 1, dayOfMonth);
                        edtChequeDate.setText(formattedDate);
                    }, year, month, day);
            datePickerDialog.show();
        });

        btnRemoveCheque.setOnClickListener(v -> {
            layoutChequesContainer.removeView(chequeView);
            chequeViewsList.remove(chequeView);
        });

        layoutChequesContainer.addView(chequeView);
        chequeViewsList.add(chequeView);
    }

    private void handleConfirmCollection() {
        double cash = 0, bank = 0;
        try {
            String c = edtCashAmount.getText().toString().trim();
            if (!c.isEmpty()) cash = Double.parseDouble(c);
        } catch (NumberFormatException ignored) {}
        
        try {
            String b = edtBankAmount.getText().toString().trim();
            if (!b.isEmpty()) bank = Double.parseDouble(b);
        } catch (NumberFormatException ignored) {}

        List<ChequeData> validatedCheques = new ArrayList<>();
        double chequeTotal = 0;

        for (View cv : chequeViewsList) {
            EditText bText = cv.findViewById(R.id.edtChequeBank);
            EditText nText = cv.findViewById(R.id.edtChequeNumber);
            EditText dText = cv.findViewById(R.id.edtChequeDate);
            EditText aText = cv.findViewById(R.id.edtChequeAmount);

            String chqBank = bText.getText().toString().trim();
            String chqNum = nText.getText().toString().trim();
            String chqDateStr = dText.getText().toString().trim();
            String chqAmtStr = aText.getText().toString().trim();

            if (!chqBank.isEmpty() || !chqNum.isEmpty() || !chqDateStr.isEmpty() || !chqAmtStr.isEmpty()) {
                if (chqBank.isEmpty() || chqNum.isEmpty() || chqDateStr.isEmpty() || chqAmtStr.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields for every added cheque.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    double chqAmt = Double.parseDouble(chqAmtStr);
                    if (chqAmt <= 0) {
                        Toast.makeText(this, "Cheque amount must be greater than zero.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    validatedCheques.add(new ChequeData(chqAmt, chqBank, chqNum, chqDateStr));
                    chequeTotal += chqAmt;
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid cheque amount entered.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        double totalCollected = cash + bank + chequeTotal;

        if (totalCollected <= 0) {
            Toast.makeText(this, "Please enter at least one valid payment amount.", Toast.LENGTH_SHORT).show();
            return;
        }

        txtTotalToCollect.setText(String.format("Total Collected:\nLKR %,.2f", totalCollected));

        showProgressDialog("Acquiring GPS location...");
        final double finalCash = cash;
        final double finalBank = bank;
        final double finalChequeTotal = chequeTotal;

        LocationHelper.captureCurrentLocation(this, new LocationHelper.LocationResultListener() {
            private boolean hasExecuted = false;

            @Override
            public void onLocationResult(double latitude, double longitude, boolean isFallback) {
                synchronized (this) {
                    if (hasExecuted) return;
                    hasExecuted = true;
                }
                
                runOnUiThread(() -> {
                    dismissProgressDialog();
                    if (isFallback) {
                        Toast.makeText(CreditCheckoutActivity.this, "⚠️ GPS unavailable. Payment location set to default.", Toast.LENGTH_LONG).show();
                    }
                    boolean success = false;
                    if (finalCash > 0.0) {
                        success = dbHelper.savePayment(customerId, currentRouteId, "Cash", finalCash, "", "", "", latitude, longitude);
                    }
                    if (finalBank > 0.0) {
                        success = dbHelper.savePayment(customerId, currentRouteId, "Bank Transfer", finalBank, "Bank Transfer", "", "", latitude, longitude);
                    }
                    for (ChequeData cd : validatedCheques) {
                        success = dbHelper.savePayment(customerId, currentRouteId, "Cheque", cd.amount, cd.bank, cd.number, cd.date, latitude, longitude);
                    }

                    if (success || totalCollected > 0) {
                        StringBuilder summary = new StringBuilder();
                        if (finalCash > 0.0) summary.append("Rs: ").append(String.format("%,.2f", finalCash)).append(" Cash");
                        if (finalBank > 0.0) {
                            if (summary.length() > 0) summary.append(" | ");
                            summary.append("Rs: ").append(String.format("%,.2f", finalBank)).append(" Bank Transfer");
                        }
                        if (!validatedCheques.isEmpty()) {
                            if (summary.length() > 0) summary.append(" | ");
                            summary.append("Rs: ").append(String.format("%,.2f", finalChequeTotal)).append(" Cheque");
                        }
                        Toast.makeText(CreditCheckoutActivity.this, "Recorded Collected Amount:\n" + summary.toString() + "\ncollected and saved offline successfully!", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(CreditCheckoutActivity.this, "Error saving payment collections locally.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showProgressDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        if (!isFinishing()) {
            progressDialog.show();
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
