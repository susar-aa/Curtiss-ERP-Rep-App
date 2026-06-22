package com.example.curtiss;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private EditText edtInvoiceSearch;
    private ListView lstInvoices, lstDetailItems;
    private RelativeLayout layoutInvoiceDetailOverlay;
    private TextView txtDetailInvNumber, txtDetailInvCust, txtDetailSubtotal, txtDetailDiscount, txtDetailTax, txtDetailNetTotal;
    private Button btnCloseDetail;

    private DatabaseHelper dbHelper;
    private List<InvoiceModel> invoiceList = new ArrayList<>();
    private List<InvoiceItemModel> detailItemList = new ArrayList<>();
    private BottomNavigationView bottomNavigation;

    private InvoiceAdapter invoiceAdapter;
    private DetailItemAdapter detailAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        dbHelper = DatabaseHelper.getInstance(this);

        // Bind layouts
        edtInvoiceSearch = findViewById(R.id.edtInvoiceSearch);
        lstInvoices = findViewById(R.id.lstInvoices);
        lstDetailItems = findViewById(R.id.lstDetailItems);
        layoutInvoiceDetailOverlay = findViewById(R.id.layoutInvoiceDetailOverlay);

        txtDetailInvNumber = findViewById(R.id.txtDetailInvNumber);
        txtDetailInvCust = findViewById(R.id.txtDetailInvCust);
        txtDetailSubtotal = findViewById(R.id.txtDetailSubtotal);
        txtDetailDiscount = findViewById(R.id.txtDetailDiscount);
        txtDetailTax = findViewById(R.id.txtDetailTax);
        txtDetailNetTotal = findViewById(R.id.txtDetailNetTotal);
        btnCloseDetail = findViewById(R.id.btnCloseDetail);

        bottomNavigation = findViewById(R.id.bottom_navigation);
        if (bottomNavigation != null) {
            bottomNavigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int itemId = item.getItemId();
                    if (itemId == R.id.nav_home) {
                        Intent intent = new Intent(HistoryActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        return true;
                    } else if (itemId == R.id.nav_customers) {
                        Intent intent = new Intent(HistoryActivity.this, CustomerActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        return true;
                    } else if (itemId == R.id.nav_history) {
                        return true;
                    } else if (itemId == R.id.nav_dashboard) {
                        Intent intent = new Intent(HistoryActivity.this, DashboardActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        return true;
                    }
                    return false;
                }
            });
        }

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // List item click opens detail overlay
        lstInvoices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openInvoiceDetails(invoiceList.get(position));
            }
        });

        btnCloseDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutInvoiceDetailOverlay.setVisibility(View.GONE);
            }
        });

        // Search watcher
        edtInvoiceSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadInvoicesFromLocal(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadInvoicesFromLocal("");
    }

    private void loadInvoicesFromLocal(String filter) {
        invoiceList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // Join query to fetch customer name mapping
        String query = "SELECT i.*, c.name AS customer_name FROM invoices i " +
                "LEFT JOIN customers c ON i.customer_id = c.id";
        String[] args = null;

        if (filter != null && !filter.trim().isEmpty()) {
            query = "SELECT i.*, c.name AS customer_name FROM invoices i " +
                    "LEFT JOIN customers c ON i.customer_id = c.id " +
                    "WHERE i.invoice_number LIKE ? OR c.name LIKE ?";
            args = new String[]{"%" + filter + "%", "%" + filter + "%"};
        }

        query += " ORDER BY i.id DESC";

        Cursor cursor = db.rawQuery(query, args);
        while (cursor.moveToNext()) {
            InvoiceModel inv = new InvoiceModel();
            inv.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            inv.serverId = cursor.getInt(cursor.getColumnIndexOrThrow("server_id"));
            inv.invoiceNumber = cursor.getString(cursor.getColumnIndexOrThrow("invoice_number"));
            inv.customerName = cursor.getString(cursor.getColumnIndexOrThrow("customer_name"));
            if (inv.customerName == null) inv.customerName = "Unknown Shop";
            inv.date = cursor.getString(cursor.getColumnIndexOrThrow("invoice_date"));
            inv.subtotal = cursor.getDouble(cursor.getColumnIndexOrThrow("subtotal"));
            inv.discount = cursor.getDouble(cursor.getColumnIndexOrThrow("discount"));
            inv.tax = cursor.getDouble(cursor.getColumnIndexOrThrow("tax"));
            inv.grandTotal = cursor.getDouble(cursor.getColumnIndexOrThrow("grand_total"));
            inv.paymentMethod = cursor.getString(cursor.getColumnIndexOrThrow("payment_method"));
            inv.isSynced = cursor.getInt(cursor.getColumnIndexOrThrow("is_synced"));
            invoiceList.add(inv);
        }
        cursor.close();

        if (invoiceAdapter == null) {
            invoiceAdapter = new InvoiceAdapter();
            lstInvoices.setAdapter(invoiceAdapter);
        } else {
            invoiceAdapter.notifyDataSetChanged();
        }
    }

    private void openInvoiceDetails(InvoiceModel inv) {
        detailItemList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT * FROM invoice_items WHERE invoice_id = " + inv.id, null);
        while (cursor.moveToNext()) {
            InvoiceItemModel item = new InvoiceItemModel();
            item.productName = cursor.getString(cursor.getColumnIndexOrThrow("product_name"));
            item.quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity"));
            item.unitPrice = cursor.getDouble(cursor.getColumnIndexOrThrow("unit_price"));
            item.total = cursor.getDouble(cursor.getColumnIndexOrThrow("total"));
            detailItemList.add(item);
        }
        cursor.close();

        // Populate detail views
        txtDetailInvNumber.setText("INVOICE: " + inv.invoiceNumber);
        txtDetailInvCust.setText("Client Shop: " + inv.customerName);
        txtDetailSubtotal.setText(String.format(Locale.getDefault(), "LKR %.2f", inv.subtotal));
        txtDetailDiscount.setText(String.format(Locale.getDefault(), "LKR %.2f", inv.discount));
        txtDetailTax.setText(String.format(Locale.getDefault(), "LKR %.2f", inv.tax));
        txtDetailNetTotal.setText(String.format(Locale.getDefault(), "LKR %.2f", inv.grandTotal));

        if (detailAdapter == null) {
            detailAdapter = new DetailItemAdapter();
            lstDetailItems.setAdapter(detailAdapter);
        } else {
            detailAdapter.notifyDataSetChanged();
        }

        layoutInvoiceDetailOverlay.setVisibility(View.VISIBLE);
    }

    // Helper Models
    private static class InvoiceModel {
        int id, serverId, isSynced;
        String invoiceNumber, customerName, date, paymentMethod;
        double subtotal, discount, tax, grandTotal;
    }

    private static class InvoiceItemModel {
        String productName;
        int quantity;
        double unitPrice, total;
    }

    // Invoices list adapter
    private class InvoiceAdapter extends BaseAdapter {
        @Override
        public int getCount() { return invoiceList.size(); }
        @Override
        public Object getItem(int position) { return invoiceList.get(position); }
        @Override
        public long getItemId(int position) { return invoiceList.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(HistoryActivity.this).inflate(R.layout.item_invoice, parent, false);
            }

            InvoiceModel inv = invoiceList.get(position);

            TextView lblInvNumber = convertView.findViewById(R.id.lblInvNumber);
            TextView lblInvSyncStatus = convertView.findViewById(R.id.lblInvSyncStatus);
            TextView lblInvCustomer = convertView.findViewById(R.id.lblInvCustomer);
            TextView lblInvDate = convertView.findViewById(R.id.lblInvDate);
            TextView lblInvPaymentMethod = convertView.findViewById(R.id.lblInvPaymentMethod);
            TextView lblInvTotal = convertView.findViewById(R.id.lblInvTotal);

            lblInvNumber.setText(inv.invoiceNumber);
            lblInvCustomer.setText("Shop: " + inv.customerName);
            lblInvDate.setText("Billed: " + inv.date);
            lblInvPaymentMethod.setText("Method: " + inv.paymentMethod);
            lblInvTotal.setText(String.format(Locale.getDefault(), "LKR %.2f", inv.grandTotal));

            if (inv.isSynced == 1) {
                lblInvSyncStatus.setText("✓ Synced");
                lblInvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                lblInvSyncStatus.setText("⚠️ Pending Upload");
                lblInvSyncStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            }

            return convertView;
        }
    }

    // Invoice Details popup items adapter
    private class DetailItemAdapter extends BaseAdapter {
        @Override
        public int getCount() { return detailItemList.size(); }
        @Override
        public Object getItem(int position) { return detailItemList.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(HistoryActivity.this).inflate(android.R.layout.simple_list_item_2, parent, false);
            }

            InvoiceItemModel item = detailItemList.get(position);
            TextView text1 = convertView.findViewById(android.R.id.text1);
            TextView text2 = convertView.findViewById(android.R.id.text2);

            text1.setText(item.productName);
            text1.setTextColor(getResources().getColor(android.R.color.white));

            text2.setText(String.format(Locale.getDefault(), "Qty: %d  x  LKR %.2f   =   LKR %.2f", item.quantity, item.unitPrice, item.total));
            text2.setTextColor(getResources().getColor(android.R.color.holo_blue_light));

            return convertView;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_history);
        }
    }
}
