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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private EditText edtInvoiceSearch;
    private ListView lstInvoices, lstDetailItems;
    private RelativeLayout layoutInvoiceDetailOverlay;
    private TextView txtDetailInvNumber, txtDetailInvCust, txtDetailSubtotal, txtDetailDiscount, txtDetailTax, txtDetailNetTotal, txtDetailInvTerm;
    private Button btnCloseDetail, btnEditInvoice, btnDownloadPdf;
    private InvoiceModel selectedInvoice;

    private DatabaseHelper dbHelper;
    private List<InvoiceModel> invoiceList = new ArrayList<>();
    private List<InvoiceItemModel> detailItemList = new ArrayList<>();
    private BottomNavigationView bottomNavigation;

    private InvoiceAdapter invoiceAdapter;
    private DetailItemAdapter detailAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(android.graphics.Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setContentView(R.layout.activity_history);

        dbHelper = DatabaseHelper.getInstance(this);

        // Bind layouts
        edtInvoiceSearch = findViewById(R.id.edtInvoiceSearch);
        lstInvoices = findViewById(R.id.lstInvoices);
        lstDetailItems = findViewById(R.id.lstDetailItems);
        layoutInvoiceDetailOverlay = findViewById(R.id.layoutInvoiceDetailOverlay);

        txtDetailInvNumber = findViewById(R.id.txtDetailInvNumber);
        txtDetailInvCust = findViewById(R.id.txtDetailInvCust);
        txtDetailInvTerm = findViewById(R.id.txtDetailInvTerm);
        txtDetailSubtotal = findViewById(R.id.txtDetailSubtotal);
        txtDetailDiscount = findViewById(R.id.txtDetailDiscount);
        txtDetailTax = findViewById(R.id.txtDetailTax);
        txtDetailNetTotal = findViewById(R.id.txtDetailNetTotal);
        btnCloseDetail = findViewById(R.id.btnCloseDetail);
        btnEditInvoice = findViewById(R.id.btnEditInvoice);
        btnDownloadPdf = findViewById(R.id.btnDownloadPdf);

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

        btnEditInvoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedInvoice != null) {
                    Intent intent = new Intent(HistoryActivity.this, BillingActivity.class);
                    intent.putExtra("edit_invoice_id", (long) selectedInvoice.id);
                    startActivity(intent);
                    layoutInvoiceDetailOverlay.setVisibility(View.GONE);
                }
            }
        });

        if (btnDownloadPdf != null) {
            btnDownloadPdf.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectedInvoice != null) {
                        generateAndOpenInvoicePdf(selectedInvoice, detailItemList);
                    }
                }
            });
        }

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

        // Join query to fetch customer name mapping and payment term name
        String query = "SELECT i.*, c.name AS customer_name, pt.name AS payment_term_name FROM invoices i " +
                "LEFT JOIN customers c ON i.customer_id = c.id " +
                "LEFT JOIN payment_terms pt ON i.payment_term_id = pt.id";
        String[] args = null;

        if (filter != null && !filter.trim().isEmpty()) {
            query = "SELECT i.*, c.name AS customer_name, pt.name AS payment_term_name FROM invoices i " +
                    "LEFT JOIN customers c ON i.customer_id = c.id " +
                    "LEFT JOIN payment_terms pt ON i.payment_term_id = pt.id " +
                    "WHERE i.invoice_number LIKE ? OR c.name LIKE ?";
            args = new String[]{"%" + filter + "%", "%" + filter + "%"};
        }

        query += " ORDER BY i.id DESC";

        Cursor cursor = db.rawQuery(query, args);
        while (cursor.moveToNext()) {
            InvoiceModel inv = new InvoiceModel();
            inv.id = DatabaseHelper.safeGetInt(cursor, "id", 0);
            inv.serverId = DatabaseHelper.safeGetInt(cursor, "server_id", 0);
            inv.invoiceNumber = DatabaseHelper.safeGetString(cursor, "invoice_number", "");
            inv.customerName = DatabaseHelper.safeGetString(cursor, "customer_name", "Unknown Shop");
            inv.date = DatabaseHelper.safeGetString(cursor, "invoice_date", "");
            inv.subtotal = DatabaseHelper.safeGetDouble(cursor, "subtotal", 0.0);
            inv.discount = DatabaseHelper.safeGetDouble(cursor, "discount", 0.0);
            inv.discountType = DatabaseHelper.safeGetString(cursor, "discount_type", "Rs");
            inv.discountRate = DatabaseHelper.safeGetDouble(cursor, "discount_rate", 0.0);
            inv.tax = DatabaseHelper.safeGetDouble(cursor, "tax", 0.0);
            inv.grandTotal = DatabaseHelper.safeGetDouble(cursor, "grand_total", 0.0);
            String localTermName = DatabaseHelper.safeGetString(cursor, "payment_term_name", "");
            if (localTermName == null || localTermName.isEmpty()) {
                localTermName = DatabaseHelper.safeGetString(cursor, "payment_method", "Term");
            }
            inv.paymentMethod = localTermName;
            inv.isSynced = DatabaseHelper.safeGetInt(cursor, "is_synced", 0);
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
        this.selectedInvoice = inv;
        detailItemList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT * FROM invoice_items WHERE invoice_id = ?", new String[]{String.valueOf(inv.id)});
        while (cursor.moveToNext()) {
            InvoiceItemModel item = new InvoiceItemModel();
            item.productName = DatabaseHelper.safeGetString(cursor, "product_name", "");
            item.quantity = DatabaseHelper.safeGetInt(cursor, "quantity", 0);
            item.unitPrice = DatabaseHelper.safeGetDouble(cursor, "unit_price", 0.0);
            item.discountVal = DatabaseHelper.safeGetDouble(cursor, "discount_val", 0.0);
            item.discountType = DatabaseHelper.safeGetString(cursor, "discount_type", "Rs");
            item.discountRate = DatabaseHelper.safeGetDouble(cursor, "discount_rate", 0.0);
            item.total = DatabaseHelper.safeGetDouble(cursor, "total", 0.0);
            detailItemList.add(item);
        }
        cursor.close();

        // Populate detail views
        txtDetailInvNumber.setText("INVOICE: " + inv.invoiceNumber);
        txtDetailInvCust.setText("Client Shop: " + inv.customerName);
        txtDetailInvTerm.setText("Payment Term: " + inv.paymentMethod);
        txtDetailSubtotal.setText(String.format(Locale.getDefault(), "LKR %.2f", inv.subtotal));
        if ("%".equals(inv.discountType)) {
            txtDetailDiscount.setText(String.format(Locale.getDefault(), "LKR %.2f (%.1f%%)", inv.discount, inv.discountRate));
        } else {
            txtDetailDiscount.setText(String.format(Locale.getDefault(), "LKR %.2f", inv.discount));
        }
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

    private void generateAndOpenInvoicePdf(InvoiceModel inv, List<InvoiceItemModel> items) {
        if (inv == null) return;

        PdfDocument pdfDocument = new PdfDocument();
        int pageWidth = 595;
        int pageHeight = 842;

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        Paint paint = new Paint();
        Paint titlePaint = new Paint();
        Paint headerPaint = new Paint();
        Paint boldPaint = new Paint();

        // Header Title
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(20);
        titlePaint.setColor(Color.BLACK);
        canvas.drawText("CURTISS ERP", 36, 50, titlePaint);

        paint.setTextSize(12);
        paint.setColor(Color.parseColor("#475569"));
        canvas.drawText("Sales Invoice & Receipt", 36, 68, paint);

        boldPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        boldPaint.setTextSize(14);
        boldPaint.setColor(Color.BLACK);
        canvas.drawText(inv.invoiceNumber != null ? inv.invoiceNumber : "INVOICE", 400, 50, boldPaint);

        // Divider
        paint.setColor(Color.parseColor("#E2E8F0"));
        paint.setStrokeWidth(1);
        canvas.drawLine(36, 85, 559, 85, paint);

        // Invoice Meta Box
        paint.setColor(Color.parseColor("#F8FAFC"));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(36, 95, 559, 165, paint);

        paint.setColor(Color.BLACK);
        paint.setTextSize(11);
        canvas.drawText("Customer / Shop:", 50, 115, boldPaint);
        canvas.drawText(inv.customerName != null ? inv.customerName : "N/A", 160, 115, paint);

        canvas.drawText("Billing Date:", 50, 135, boldPaint);
        canvas.drawText(inv.date != null ? inv.date : "N/A", 160, 135, paint);

        canvas.drawText("Payment Method:", 50, 155, boldPaint);
        canvas.drawText(inv.paymentMethod != null ? inv.paymentMethod : "Term", 160, 155, paint);

        // Items Table Header
        int y = 190;
        paint.setColor(Color.parseColor("#0F172A"));
        canvas.drawRect(36, y, 559, y + 24, paint);

        headerPaint.setColor(Color.WHITE);
        headerPaint.setTextSize(11);
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("ITEM DESCRIPTION", 46, y + 16, headerPaint);
        canvas.drawText("QTY", 320, y + 16, headerPaint);
        canvas.drawText("PRICE (LKR)", 380, y + 16, headerPaint);
        canvas.drawText("TOTAL (LKR)", 470, y + 16, headerPaint);

        y += 36;
        paint.setColor(Color.BLACK);
        paint.setTextSize(10);

        // Line Items Rows
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                InvoiceItemModel item = items.get(i);

                if (i % 2 == 1) {
                    Paint bgPaint = new Paint();
                    bgPaint.setColor(Color.parseColor("#F8FAFC"));
                    canvas.drawRect(36, y - 12, 559, y + 14, bgPaint);
                }

                String prodName = item.productName != null ? item.productName : "Product";
                if (prodName.length() > 35) {
                    prodName = prodName.substring(0, 32) + "...";
                }
                canvas.drawText(prodName, 46, y, paint);
                canvas.drawText(String.valueOf(item.quantity), 320, y, paint);
                canvas.drawText(String.format(Locale.getDefault(), "%.2f", item.unitPrice), 380, y, paint);
                canvas.drawText(String.format(Locale.getDefault(), "%.2f", item.total), 470, y, paint);

                y += 24;
                if (y > 700) {
                    break;
                }
            }
        }

        // Line before totals
        paint.setColor(Color.parseColor("#CBD5E1"));
        canvas.drawLine(36, y + 5, 559, y + 5, paint);
        y += 25;

        // Financial Summary Box
        boldPaint.setTextSize(11);
        canvas.drawText("Subtotal:", 350, y, boldPaint);
        canvas.drawText(String.format(Locale.getDefault(), "LKR %.2f", inv.subtotal), 470, y, paint);
        y += 20;

        canvas.drawText("Discounts:", 350, y, boldPaint);
        canvas.drawText(String.format(Locale.getDefault(), "LKR %.2f", inv.discount), 470, y, paint);
        y += 20;

        canvas.drawText("VAT / Tax:", 350, y, boldPaint);
        canvas.drawText(String.format(Locale.getDefault(), "LKR %.2f", inv.tax), 470, y, paint);
        y += 25;

        // Net Total Box
        Paint netBg = new Paint();
        netBg.setColor(Color.parseColor("#F1F5F9"));
        canvas.drawRect(330, y - 14, 559, y + 14, netBg);

        Paint netText = new Paint();
        netText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        netText.setTextSize(12);
        netText.setColor(Color.parseColor("#16A34A"));
        canvas.drawText("NET TOTAL:", 350, y, netText);
        canvas.drawText(String.format(Locale.getDefault(), "LKR %.2f", inv.grandTotal), 470, y, netText);

        // Footer
        y = 800;
        paint.setColor(Color.parseColor("#94A3B8"));
        paint.setTextSize(9);
        canvas.drawText("Thank you for your business! - Curtiss ERP System", 36, y, paint);

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        canvas.drawText("Generated: " + timestamp, 420, y, paint);

        pdfDocument.finishPage(page);

        // Save File
        String safeFileName = "Invoice_" + (inv.invoiceNumber != null ? inv.invoiceNumber.replaceAll("[^a-zA-Z0-9_-]", "_") : "DOC") + ".pdf";
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }
        File pdfFile = new File(downloadsDir, safeFileName);

        try {
            FileOutputStream fos = new FileOutputStream(pdfFile);
            pdfDocument.writeTo(fos);
            fos.close();
        } catch (Exception e) {
            File fallbackDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (fallbackDir != null) {
                pdfFile = new File(fallbackDir, safeFileName);
                try {
                    FileOutputStream fos = new FileOutputStream(pdfFile);
                    pdfDocument.writeTo(fos);
                    fos.close();
                } catch (IOException ex) {
                    Toast.makeText(this, "Failed to save PDF: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    pdfDocument.close();
                    return;
                }
            }
        }

        pdfDocument.close();
        Toast.makeText(this, "PDF saved to Downloads: " + pdfFile.getName(), Toast.LENGTH_LONG).show();
    }

    // Helper Models
    private static class InvoiceModel {
        int id, serverId, isSynced;
        String invoiceNumber, customerName, date, paymentMethod;
        double subtotal, discount, tax, grandTotal;
        String discountType;
        double discountRate;
    }

    private static class InvoiceItemModel {
        String productName;
        int quantity;
        double unitPrice, total;
        double discountVal;
        String discountType;
        double discountRate;
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
            // Fix B-07: android.R.layout.simple_list_item_2 uses a transparent/white background,
            // making white text invisible on most themes. Use a programmatic dark-themed view instead.
            android.widget.LinearLayout row;
            android.widget.TextView text1;
            android.widget.TextView text2;

            if (convertView == null) {
                row = new android.widget.LinearLayout(HistoryActivity.this);
                row.setOrientation(android.widget.LinearLayout.VERTICAL);
                row.setPadding(24, 14, 24, 14);
                row.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));

                text1 = new android.widget.TextView(HistoryActivity.this);
                text1.setId(android.R.id.text1);
                text1.setTextColor(android.graphics.Color.parseColor("#000000"));
                text1.setTextSize(14);
                text1.setTypeface(null, android.graphics.Typeface.BOLD);

                text2 = new android.widget.TextView(HistoryActivity.this);
                text2.setId(android.R.id.text2);
                text2.setTextColor(android.graphics.Color.parseColor("#475569"));
                text2.setTextSize(12);

                row.addView(text1);
                row.addView(text2);
                convertView = row;
            } else {
                row = (android.widget.LinearLayout) convertView;
                text1 = convertView.findViewById(android.R.id.text1);
                text2 = convertView.findViewById(android.R.id.text2);
            }

            InvoiceItemModel item = detailItemList.get(position);
            text1.setText(item.productName);

            String discText = "";
            if (item.discountVal > 0) {
                if ("%".equals(item.discountType)) {
                    discText = String.format(Locale.getDefault(), "  [Disc: %.1f%% / LKR %.2f]", item.discountRate, item.discountVal);
                } else {
                    discText = String.format(Locale.getDefault(), "  [Disc: LKR %.2f]", item.discountVal);
                }
            }
            text2.setText(String.format(Locale.getDefault(), "Qty: %d  ×  LKR %.2f%s   =   LKR %.2f", item.quantity, item.unitPrice, discText, item.total));

            // Alternate row tint for readability
            if (position % 2 == 0) {
                row.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));
            } else {
                row.setBackgroundColor(android.graphics.Color.parseColor("#F8FAFC"));
            }

            return convertView;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_history);
        }
        if (edtInvoiceSearch != null) {
            loadInvoicesFromLocal(edtInvoiceSearch.getText().toString());
        } else {
            loadInvoicesFromLocal("");
        }
    }
}
