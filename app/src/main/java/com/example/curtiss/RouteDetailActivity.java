package com.example.curtiss;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class RouteDetailActivity extends AppCompatActivity {

    private Button btnBack;
    private TextView txtRouteDetailTitle, txtOfflineBanner, txtRouteName, txtRouteDuration;
    private TextView txtStartOdo, txtEndOdo;
    private TextView txtSalesTotal, txtBillsCount;
    private TextView txtCashCollected, txtChequeCollected, txtBankCollected, txtTotalCollected;
    private ListView lstRouteBills;
    private RelativeLayout layoutLoadingOverlay;

    // Invoice Details Overlay Views
    private RelativeLayout layoutInvoiceDetailOverlay;
    private TextView txtDetailInvNumber, txtDetailInvDate, txtDetailInvCust, txtDetailInvContact, txtDetailInvTerm;
    private ListView lstDetailItems, lstDetailPayments;
    private TextView txtDetailSubtotal, txtDetailDiscount, txtDetailTax, txtDetailGrandTotal, txtDetailPaid, txtDetailBalance;
    private Button btnCloseDetail;

    private SharedPreferences prefs;
    private int userId;
    private int routeId;
    private String baseUrl;

    private ArrayList<JSONObject> billsList = new ArrayList<>();
    private BillsAdapter billsAdapter;

    private ArrayList<JSONObject> invoiceItemsList = new ArrayList<>();
    private InvoiceItemsAdapter itemsAdapter;

    private ArrayList<JSONObject> invoicePaymentsList = new ArrayList<>();
    private InvoicePaymentsAdapter paymentsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_detail);

        prefs = SecurePreferences.getSessionPrefs(this);
        userId = prefs.getInt("user_id", 0);
        baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");
        routeId = getIntent().getIntExtra("route_id", 0);

        if (routeId <= 0) {
            Toast.makeText(this, "Invalid route selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupListeners();

        // Load route details
        checkConnectionAndLoad();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        txtRouteDetailTitle = findViewById(R.id.txtRouteDetailTitle);
        txtOfflineBanner = findViewById(R.id.txtOfflineBanner);
        txtRouteName = findViewById(R.id.txtRouteName);
        txtRouteDuration = findViewById(R.id.txtRouteDuration);
        txtStartOdo = findViewById(R.id.txtStartOdo);
        txtEndOdo = findViewById(R.id.txtEndOdo);
        txtSalesTotal = findViewById(R.id.txtSalesTotal);
        txtBillsCount = findViewById(R.id.txtBillsCount);
        txtCashCollected = findViewById(R.id.txtCashCollected);
        txtChequeCollected = findViewById(R.id.txtChequeCollected);
        txtBankCollected = findViewById(R.id.txtBankCollected);
        txtTotalCollected = findViewById(R.id.txtTotalCollected);
        lstRouteBills = findViewById(R.id.lstRouteBills);
        layoutLoadingOverlay = findViewById(R.id.layoutLoadingOverlay);

        // Overlay Views
        layoutInvoiceDetailOverlay = findViewById(R.id.layoutInvoiceDetailOverlay);
        txtDetailInvNumber = findViewById(R.id.txtDetailInvNumber);
        txtDetailInvDate = findViewById(R.id.txtDetailInvDate);
        txtDetailInvCust = findViewById(R.id.txtDetailInvCust);
        txtDetailInvContact = findViewById(R.id.txtDetailInvContact);
        txtDetailInvTerm = findViewById(R.id.txtDetailInvTerm);
        lstDetailItems = findViewById(R.id.lstDetailItems);
        lstDetailPayments = findViewById(R.id.lstDetailPayments);
        txtDetailSubtotal = findViewById(R.id.txtDetailSubtotal);
        txtDetailDiscount = findViewById(R.id.txtDetailDiscount);
        txtDetailTax = findViewById(R.id.txtDetailTax);
        txtDetailGrandTotal = findViewById(R.id.txtDetailGrandTotal);
        txtDetailPaid = findViewById(R.id.txtDetailPaid);
        txtDetailBalance = findViewById(R.id.txtDetailBalance);
        btnCloseDetail = findViewById(R.id.btnCloseDetail);

        billsAdapter = new BillsAdapter();
        lstRouteBills.setAdapter(billsAdapter);

        itemsAdapter = new InvoiceItemsAdapter();
        lstDetailItems.setAdapter(itemsAdapter);

        paymentsAdapter = new InvoicePaymentsAdapter();
        lstDetailPayments.setAdapter(paymentsAdapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnCloseDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutInvoiceDetailOverlay.setVisibility(View.GONE);
            }
        });

        lstRouteBills.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isNetworkAvailable()) {
                    showOfflineWarning();
                    return;
                }
                JSONObject bill = billsList.get(position);
                int invoiceId = bill.optInt("id", 0);
                if (invoiceId > 0) {
                    new FetchInvoiceDetailsTask(invoiceId).execute();
                }
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
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
                @SuppressWarnings("deprecation")
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        }
        return false;
    }

    private void showOfflineWarning() {
        txtOfflineBanner.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Internet connection required to view details.", Toast.LENGTH_LONG).show();
    }

    private void checkConnectionAndLoad() {
        if (!isNetworkAvailable()) {
            showOfflineWarning();
            return;
        }

        txtOfflineBanner.setVisibility(View.GONE);
        new FetchRouteDetailsTask().execute();
    }

    // Task to fetch route details from server
    private class FetchRouteDetailsTask extends AsyncTask<Void, Void, String> {
        @Override
        protected void onPreExecute() {
            layoutLoadingOverlay.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(Void... voids) {
            HttpURLConnection conn = null;
            try {
                String urlStr = baseUrl + "/rep/RepDashboard/api_route_details?user_id=" + userId + "&route_id=" + routeId;
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("X-User-ID", String.valueOf(userId));
                String token = prefs.getString("api_token", "");
                if (!token.isEmpty()) { conn.setRequestProperty("Authorization", "Bearer " + token); }
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        sb.append(line);
                    }
                    in.close();
                    return sb.toString();
                } else {
                    return "{\"success\":false,\"message\":\"Server responded with code " + responseCode + "\"}";
                }
            } catch (Exception e) {
                return "{\"success\":false,\"message\":\"Connection error: " + e.getMessage() + "\"}";
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            layoutLoadingOverlay.setVisibility(View.GONE);
            if (result == null) {
                Toast.makeText(RouteDetailActivity.this, "Empty response from server", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                JSONObject response = new JSONObject(result);
                if (response.optBoolean("success", false)) {
                    JSONObject route = response.optJSONObject("route");
                    if (route != null) {
                        // Populate Route Meta Data
                        String name = route.optString("route_name", "Unknown Route");
                        int id = route.optInt("id", 0);
                        txtRouteDetailTitle.setText("#RT-" + id + " Details");
                        txtRouteName.setText("#RT-" + id + ": " + name);

                        String start = route.optString("start_time", "");
                        String end = route.optString("end_time", "");
                        if (!end.isEmpty() && !end.equals("null")) {
                            txtRouteDuration.setText("Started: " + start + " | Ended: " + end);
                        } else {
                            txtRouteDuration.setText("Started: " + start + " | Status: " + route.optString("status"));
                        }

                        // Odometer values
                        double startOdo = route.optDouble("start_meter", 0.0);
                        double endOdo = route.optDouble("end_meter", 0.0);
                        txtStartOdo.setText(String.format(Locale.getDefault(), "%,.1f KM", startOdo));
                        if (endOdo > 0) {
                            txtEndOdo.setText(String.format(Locale.getDefault(), "%,.1f KM", endOdo));
                        } else {
                            txtEndOdo.setText("In Progress");
                        }

                        // Financial totals
                        double totalSales = route.optDouble("total_sales", 0.0);
                        int totalBills = route.optInt("total_bills", 0);
                        txtSalesTotal.setText(String.format(Locale.getDefault(), "LKR %,.2f", totalSales));
                        txtBillsCount.setText(totalBills + " Invoices");

                        // Collections breakdown
                        double cash = route.optDouble("cash_collections", 0.0);
                        double cheque = route.optDouble("cheque_collections", 0.0);
                        double bank = route.optDouble("bank_collections", 0.0);
                        double totalCol = route.optDouble("total_collections", 0.0);

                        txtCashCollected.setText(String.format(Locale.getDefault(), "LKR %,.2f", cash));
                        txtChequeCollected.setText(String.format(Locale.getDefault(), "LKR %,.2f", cheque));
                        txtBankCollected.setText(String.format(Locale.getDefault(), "LKR %,.2f", bank));
                        txtTotalCollected.setText(String.format(Locale.getDefault(), "LKR %,.2f", totalCol));
                    }

                    // Bills list
                    billsList.clear();
                    JSONArray bills = response.optJSONArray("bills");
                    if (bills != null) {
                        for (int i = 0; i < bills.length(); i++) {
                            billsList.add(bills.getJSONObject(i));
                        }
                    }
                    billsAdapter.notifyDataSetChanged();
                } else {
                    String msg = response.optString("message", "Error loading details");
                    Toast.makeText(RouteDetailActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                android.util.Log.e("RouteDetailActivity", "JSON Parse error", e);
                Toast.makeText(RouteDetailActivity.this, "Failed to parse route data", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Task to fetch invoice details and overlay
    private class FetchInvoiceDetailsTask extends AsyncTask<Void, Void, String> {
        private int invoiceId;

        public FetchInvoiceDetailsTask(int invoiceId) {
            this.invoiceId = invoiceId;
        }

        @Override
        protected void onPreExecute() {
            layoutLoadingOverlay.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(Void... voids) {
            HttpURLConnection conn = null;
            try {
                String urlStr = baseUrl + "/rep/RepDashboard/api_invoice_details?user_id=" + userId + "&invoice_id=" + invoiceId;
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("X-User-ID", String.valueOf(userId));
                String token = prefs.getString("api_token", "");
                if (!token.isEmpty()) { conn.setRequestProperty("Authorization", "Bearer " + token); }
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        sb.append(line);
                    }
                    in.close();
                    return sb.toString();
                } else {
                    return "{\"success\":false,\"message\":\"Server responded with code " + responseCode + "\"}";
                }
            } catch (Exception e) {
                return "{\"success\":false,\"message\":\"Connection error: " + e.getMessage() + "\"}";
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            layoutLoadingOverlay.setVisibility(View.GONE);
            if (result == null) {
                Toast.makeText(RouteDetailActivity.this, "Empty response from server", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                JSONObject response = new JSONObject(result);
                if (response.optBoolean("success", false)) {
                    JSONObject inv = response.optJSONObject("invoice");
                    if (inv != null) {
                        txtDetailInvNumber.setText("INVOICE: " + inv.optString("invoice_number", "INV-XXXX"));
                        txtDetailInvDate.setText(inv.optString("invoice_date", ""));
                        txtDetailInvCust.setText(inv.optString("customer_name") + " (" + inv.optString("customer_code") + ")");
                        txtDetailInvContact.setText("Address: " + inv.optString("customer_address", "-") + " | Phone: " + inv.optString("customer_phone", "-"));
                        
                        String termName = inv.optString("payment_term_name", "");
                        if (termName.isEmpty()) {
                            termName = inv.optString("payment_method", "Credit");
                        }
                        txtDetailInvTerm.setText("Payment Term: " + termName);

                        // Summaries
                        txtDetailSubtotal.setText(String.format(Locale.getDefault(), "LKR %,.2f", inv.optDouble("subtotal", 0.0)));
                        txtDetailDiscount.setText(String.format(Locale.getDefault(), "LKR %,.2f", inv.optDouble("discount", 0.0)));
                        txtDetailTax.setText(String.format(Locale.getDefault(), "LKR %,.2f", inv.optDouble("tax", 0.0)));
                        txtDetailGrandTotal.setText(String.format(Locale.getDefault(), "LKR %,.2f", inv.optDouble("grand_total", 0.0)));
                        txtDetailPaid.setText(String.format(Locale.getDefault(), "LKR %,.2f", inv.optDouble("paid_amount", 0.0)));
                        txtDetailBalance.setText(String.format(Locale.getDefault(), "LKR %,.2f", inv.optDouble("balance", 0.0)));
                    }

                    // Populate Items
                    invoiceItemsList.clear();
                    JSONArray items = response.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            invoiceItemsList.add(items.getJSONObject(i));
                        }
                    }
                    itemsAdapter.notifyDataSetChanged();

                    // Populate Payments
                    invoicePaymentsList.clear();
                    JSONArray payments = response.optJSONArray("payments");
                    if (payments != null) {
                        for (int i = 0; i < payments.length(); i++) {
                            invoicePaymentsList.add(payments.getJSONObject(i));
                        }
                    }
                    paymentsAdapter.notifyDataSetChanged();

                    // Show Overlay Modal
                    layoutInvoiceDetailOverlay.setVisibility(View.VISIBLE);
                } else {
                    String msg = response.optString("message", "Error loading details");
                    Toast.makeText(RouteDetailActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                android.util.Log.e("RouteDetailActivity", "JSON Parsing invoice details failed", e);
                Toast.makeText(RouteDetailActivity.this, "Failed to load invoice details", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Adapters
    private class BillsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return billsList.size();
        }

        @Override
        public Object getItem(int position) {
            return billsList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(RouteDetailActivity.this).inflate(R.layout.item_route_bill_row, parent, false);
            }

            JSONObject bill = billsList.get(position);

            TextView txtRowInvNumber = convertView.findViewById(R.id.txtRowInvNumber);
            TextView txtRowInvCust = convertView.findViewById(R.id.txtRowInvCust);
            TextView txtRowInvStatus = convertView.findViewById(R.id.txtRowInvStatus);
            TextView txtRowInvTotal = convertView.findViewById(R.id.txtRowInvTotal);
            TextView txtRowInvPaid = convertView.findViewById(R.id.txtRowInvPaid);
            TextView txtRowInvBalance = convertView.findViewById(R.id.txtRowInvBalance);
            TextView txtRowInvMethod = convertView.findViewById(R.id.txtRowInvMethod);

            txtRowInvNumber.setText(bill.optString("invoice_number", "INV-XXXX"));
            txtRowInvCust.setText(bill.optString("customer_name", "") + " (" + bill.optString("customer_code", "") + ")");

            String status = bill.optString("status", "Unpaid");
            txtRowInvStatus.setText(status.toUpperCase());
            if (status.equalsIgnoreCase("Paid")) {
                txtRowInvStatus.setBackgroundColor(0xFF10B981); // Green
            } else {
                txtRowInvStatus.setBackgroundColor(0xFFEF4444); // Red
            }

            double total = bill.optDouble("total_amount", 0.0);
            double paid = bill.optDouble("paid_amount", 0.0);
            double bal = bill.optDouble("balance", 0.0);

            txtRowInvTotal.setText(String.format(Locale.getDefault(), "LKR %,.2f", total));
            txtRowInvPaid.setText(String.format(Locale.getDefault(), "LKR %,.2f", paid));
            txtRowInvBalance.setText(String.format(Locale.getDefault(), "LKR %,.2f", bal));
            txtRowInvMethod.setText("Payment Mode: " + bill.optString("payment_method", "Credit"));

            return convertView;
        }
    }

    private class InvoiceItemsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return invoiceItemsList.size();
        }

        @Override
        public Object getItem(int position) {
            return invoiceItemsList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(RouteDetailActivity.this).inflate(R.layout.item_invoice_detail_product_row, parent, false);
            }

            JSONObject item = invoiceItemsList.get(position);

            TextView txtProdName = convertView.findViewById(R.id.txtProdName);
            TextView txtProdQty = convertView.findViewById(R.id.txtProdQty);
            TextView txtProdPrice = convertView.findViewById(R.id.txtProdPrice);
            TextView txtProdDiscount = convertView.findViewById(R.id.txtProdDiscount);
            TextView txtLineTotal = convertView.findViewById(R.id.txtLineTotal);

            String name = item.optString("item_name", "Unknown Item");
            String code = item.optString("item_code", "");
            txtProdName.setText(name + (!code.isEmpty() ? " (" + code + ")" : ""));

            double qty = item.optDouble("quantity", 0.0);
            txtProdQty.setText("Qty: " + String.format(Locale.getDefault(), "%,.0f", qty));

            double price = item.optDouble("unit_price", 0.0);
            txtProdPrice.setText("Price: LKR " + String.format(Locale.getDefault(), "%,.2f", price));

            double discVal = item.optDouble("discount_value", 0.0);
            String discType = item.optString("discount_type", "");
            if (discType.equals("%")) {
                txtProdDiscount.setText("Disc: " + String.format(Locale.getDefault(), "%,.1f%%", discVal));
            } else {
                txtProdDiscount.setText("Disc: LKR " + String.format(Locale.getDefault(), "%,.2f", discVal));
            }

            double total = item.optDouble("total", 0.0);
            txtLineTotal.setText(String.format(Locale.getDefault(), "LKR %,.2f", total));

            return convertView;
        }
    }

    private class InvoicePaymentsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return invoicePaymentsList.size();
        }

        @Override
        public Object getItem(int position) {
            return invoicePaymentsList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(RouteDetailActivity.this).inflate(R.layout.item_invoice_detail_payment_row, parent, false);
            }

            JSONObject payment = invoicePaymentsList.get(position);

            TextView txtPayMethod = convertView.findViewById(R.id.txtPayMethod);
            TextView txtPayDate = convertView.findViewById(R.id.txtPayDate);
            TextView txtPayAmount = convertView.findViewById(R.id.txtPayAmount);
            TextView txtPayStatus = convertView.findViewById(R.id.txtPayStatus);
            TextView txtPayRefNotes = convertView.findViewById(R.id.txtPayRefNotes);

            txtPayMethod.setText(payment.optString("method", "Payment"));
            txtPayDate.setText(payment.optString("date", ""));

            double amount = payment.optDouble("amount", 0.0);
            txtPayAmount.setText(String.format(Locale.getDefault(), "LKR %,.2f", amount));

            String status = payment.optString("status", "Finalized");
            txtPayStatus.setText(status);
            if (status.equalsIgnoreCase("Finalized")) {
                txtPayStatus.setTextColor(0xFF10B981); // Green
            } else {
                txtPayStatus.setTextColor(0xFFF59E0B); // Orange
            }

            String ref = payment.optString("reference", "-");
            String notes = payment.optString("notes", "");
            txtPayRefNotes.setText("Ref: " + ref + (!notes.isEmpty() ? " | Notes: " + notes : ""));

            return convertView;
        }
    }
}
