package com.example.curtiss;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BillingActivity extends AppCompatActivity {

    private Spinner spinnerPaymentMethod, spinnerCategory, spinnerPaymentTerm;
    private EditText edtProductSearch, edtDiscount, edtDirectDiscountPct;
    private ListView lstProducts, lstCartSummary;
    private TextView txtCartItemsCount, txtCartSalesSum, txtSubtotal, txtTax, txtNetTotal;

    private TextView txtSelectedCustomerName;
    private Button btnChangeCustomer;
    private CustomerModel selectedCustomer = null;
    private List<String> categoryList = new ArrayList<>();
    private List<PaymentTermModel> paymentTermList = new ArrayList<>();

    private RelativeLayout layoutCartOverlay;
    private Button btnViewCart, btnCancelCart, btnConfirmCheckout;

    private Button btnModeStandard, btnModeVisual;
    private android.widget.GridView gridProducts;
    private ProductGridAdapter productGridAdapter;
    private boolean isVisualMode = false;

    private DatabaseHelper dbHelper;
    private List<CustomerModel> customerList = new ArrayList<>();
    private List<ProductModel> productList = new ArrayList<>();
    private List<CartItemModel> cartList = new ArrayList<>();

    private ProductAdapter productAdapter;
    private CartAdapter cartAdapter;
    private long currentRouteLocalId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_billing);

        dbHelper = new DatabaseHelper(this);

        // Bind layouts
        txtSelectedCustomerName = findViewById(R.id.txtSelectedCustomerName);
        btnChangeCustomer = findViewById(R.id.btnChangeCustomer);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerPaymentMethod = findViewById(R.id.spinnerPaymentMethod);
        spinnerPaymentTerm = findViewById(R.id.spinnerPaymentTerm);

        edtProductSearch = findViewById(R.id.edtProductSearch);
        edtDiscount = findViewById(R.id.edtDiscount);
        edtDirectDiscountPct = findViewById(R.id.edtDirectDiscountPct);
        lstProducts = findViewById(R.id.lstProducts);
        lstCartSummary = findViewById(R.id.lstCartSummary);

        txtCartItemsCount = findViewById(R.id.txtCartItemsCount);
        txtCartSalesSum = findViewById(R.id.txtCartSalesSum);
        txtSubtotal = findViewById(R.id.txtSubtotal);
        txtTax = findViewById(R.id.txtTax);
        txtNetTotal = findViewById(R.id.txtNetTotal);

        layoutCartOverlay = findViewById(R.id.layoutCartOverlay);
        btnViewCart = findViewById(R.id.btnViewCart);
        btnCancelCart = findViewById(R.id.btnCancelCart);
        btnConfirmCheckout = findViewById(R.id.btnConfirmCheckout);

        btnModeStandard = findViewById(R.id.btnModeStandard);
        btnModeVisual = findViewById(R.id.btnModeVisual);
        gridProducts = findViewById(R.id.gridProducts);

        // Mode Selectors Toggle Actions
        btnModeStandard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isVisualMode = false;
                lstProducts.setVisibility(View.VISIBLE);
                gridProducts.setVisibility(View.GONE);
                btnModeStandard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#3B82F6")));
                btnModeStandard.setTextColor(android.graphics.Color.WHITE);
                btnModeVisual.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
                btnModeVisual.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
            }
        });

        btnModeVisual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isVisualMode = true;
                lstProducts.setVisibility(View.GONE);
                gridProducts.setVisibility(View.VISIBLE);
                btnModeVisual.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#3B82F6")));
                btnModeVisual.setTextColor(android.graphics.Color.WHITE);
                btnModeStandard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
                btnModeStandard.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnChangeCustomer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCustomerSelectionDialog();
            }
        });

        // Resolve Active Route
        Cursor cRoute = dbHelper.getActiveRoute();
        if (cRoute.moveToFirst()) {
            currentRouteLocalId = cRoute.getLong(cRoute.getColumnIndexOrThrow("id"));
        }
        cRoute.close();

        // Setup Spinner payment options
        setupSpinners();

        // Search text change listener
        edtProductSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadCatalogItems(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Setup dynamic categories first, then let it trigger catalog loading
        setupCategorySpinner();

        // 🚨 Immediately trigger Customer Selection Dialog at startup
        showCustomerSelectionDialog();



        // View Cart overlay
        btnViewCart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cartList.isEmpty()) {
                    Toast.makeText(BillingActivity.this, "Your shopping cart is empty.", Toast.LENGTH_SHORT).show();
                    return;
                }
                layoutCartOverlay.setVisibility(View.VISIBLE);
                setupCartSummary();
            }
        });

        btnCancelCart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutCartOverlay.setVisibility(View.GONE);
            }
        });

        // Double-mode direct discount interactive text watchers
        final TextWatcher directDiscountWatcher = new TextWatcher() {
            private boolean isUpdating = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdating) return;
                isUpdating = true;
                try {
                    // Get current subtotal before global discount
                    double subtotal = 0.0;
                    for (CartItemModel item : cartList) {
                        subtotal += item.total;
                    }

                    if (edtDirectDiscountPct.hasFocus()) {
                        String pctStr = edtDirectDiscountPct.getText().toString().trim();
                        if (!pctStr.isEmpty()) {
                            double pct = Double.parseDouble(pctStr);
                            double discountVal = subtotal * (pct / 100.0);
                            edtDiscount.setText(String.format(Locale.getDefault(), "%.2f", discountVal));
                        } else {
                            edtDiscount.setText("");
                        }
                    } else if (edtDiscount.hasFocus()) {
                        String amtStr = edtDiscount.getText().toString().trim();
                        if (!amtStr.isEmpty() && subtotal > 0) {
                            double amt = Double.parseDouble(amtStr);
                            double pct = (amt / subtotal) * 100.0;
                            edtDirectDiscountPct.setText(String.format(Locale.getDefault(), "%.1f", pct));
                        } else {
                            edtDirectDiscountPct.setText("");
                        }
                    }
                } catch (Exception ignored) {}
                isUpdating = false;
                recalculateCart();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        edtDirectDiscountPct.addTextChangedListener(directDiscountWatcher);
        edtDiscount.addTextChangedListener(directDiscountWatcher);

        // Checkout Button Trigger
        btnConfirmCheckout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processCheckoutOffline();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupCategorySpinner();
    }

    private void loadCustomers() {
        customerList.clear();
        Cursor cursor = dbHelper.getCustomersByActiveRouteMainTerritory();
        while (cursor.moveToNext()) {
            CustomerModel c = new CustomerModel();
            c.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            c.serverId = cursor.getInt(cursor.getColumnIndexOrThrow("server_id"));
            c.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
            c.phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"));
            c.outstanding = cursor.getDouble(cursor.getColumnIndexOrThrow("outstanding"));
            customerList.add(c);
        }
        cursor.close();
    }

    private void setupSpinners() {
        // Load Payment Methods inside Checkout Overlay Spinner (Premium white text styling)
        List<String> payments = new ArrayList<>();
        payments.add("Cash");
        payments.add("Cheque");
        payments.add("Bank Transfer");

        ArrayAdapter<String> payAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, payments) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(android.graphics.Color.WHITE);
                    ((TextView) v).setTextSize(14);
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(android.graphics.Color.WHITE);
                    v.setBackgroundColor(android.graphics.Color.parseColor("#1E293B"));
                    v.setPadding(16, 16, 16, 16);
                }
                return v;
            }
        };
        payAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaymentMethod.setAdapter(payAdapter);

        // Load Payment Terms from Database dynamically
        paymentTermList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try {
            Cursor cursor = db.rawQuery("SELECT id, name, days_due FROM payment_terms ORDER BY days_due ASC", null);
            while (cursor.moveToNext()) {
                PaymentTermModel term = new PaymentTermModel();
                term.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                term.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                term.daysDue = cursor.getInt(cursor.getColumnIndexOrThrow("days_due"));
                paymentTermList.add(term);
            }
            cursor.close();
        } catch (Exception e) {
            android.util.Log.e("BillingActivity", "Error loading payment terms: " + e.getMessage());
        }

        List<String> termNames = new ArrayList<>();
        for (PaymentTermModel pt : paymentTermList) {
            termNames.add(pt.name);
        }
        if (termNames.isEmpty()) {
            termNames.add("Due on Receipt");
        }

        ArrayAdapter<String> termAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, termNames) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(android.graphics.Color.WHITE);
                    ((TextView) v).setTextSize(14);
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(android.graphics.Color.WHITE);
                    v.setBackgroundColor(android.graphics.Color.parseColor("#1E293B"));
                    v.setPadding(16, 16, 16, 16);
                }
                return v;
            }
        };
        termAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaymentTerm.setAdapter(termAdapter);
    }

    private void loadCatalogItems(String filter) {
        productList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String selectedCategory = "All Categories";
        if (spinnerCategory != null && spinnerCategory.getSelectedItem() != null) {
            selectedCategory = spinnerCategory.getSelectedItem().toString();
        }

        String query = "SELECT * FROM products";
        List<String> argsList = new ArrayList<>();

        if (filter != null && !filter.trim().isEmpty()) {
            if (!"All Categories".equalsIgnoreCase(selectedCategory)) {
                query = "SELECT * FROM products WHERE (name LIKE ? OR category_name LIKE ?) AND category_name = ?";
                argsList.add("%" + filter + "%");
                argsList.add("%" + filter + "%");
                argsList.add(selectedCategory);
            } else {
                query = "SELECT * FROM products WHERE name LIKE ? OR category_name LIKE ?";
                argsList.add("%" + filter + "%");
                argsList.add("%" + filter + "%");
            }
        } else {
            if (!"All Categories".equalsIgnoreCase(selectedCategory)) {
                query = "SELECT * FROM products WHERE category_name = ?";
                argsList.add(selectedCategory);
            }
        }

        String[] args = argsList.isEmpty() ? null : argsList.toArray(new String[0]);

        Cursor cursor = db.rawQuery(query, args);
        while (cursor.moveToNext()) {
            ProductModel p = new ProductModel();
            p.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            p.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
            p.category = cursor.getString(cursor.getColumnIndexOrThrow("category_name"));
            p.price = cursor.getDouble(cursor.getColumnIndexOrThrow("price"));
            p.wholesalePrice = cursor.getDouble(cursor.getColumnIndexOrThrow("wholesale_price"));
            p.qtyOnHand = cursor.getInt(cursor.getColumnIndexOrThrow("quantity_on_hand"));
            p.qtyReserved = cursor.getInt(cursor.getColumnIndexOrThrow("quantity_reserved"));
            p.localImagePath = cursor.getString(cursor.getColumnIndexOrThrow("local_image_path"));
            productList.add(p);
        }
        cursor.close();

        if (productAdapter == null) {
            productAdapter = new ProductAdapter();
            lstProducts.setAdapter(productAdapter);
        } else {
            productAdapter.notifyDataSetChanged();
        }

        if (productGridAdapter == null) {
            productGridAdapter = new ProductGridAdapter();
            gridProducts.setAdapter(productGridAdapter);
        } else {
            productGridAdapter.notifyDataSetChanged();
        }
    }

    private void recalculateCart() {
        double subtotal = 0.0;
        int totalItemsCount = 0;

        for (CartItemModel item : cartList) {
            double price = item.customPrice > 0 ? item.customPrice : item.price;
            item.activePrice = price;
            
            double itemSubtotal = price * item.quantity;
            double discVal = 0.0;
            if (item.discountPercent > 0) {
                discVal = itemSubtotal * (item.discountPercent / 100.0);
            } else if (item.discountAmount > 0) {
                discVal = item.discountAmount;
            }
            item.discountVal = discVal;
            item.total = itemSubtotal - discVal;
            if (item.total < 0) item.total = 0;
            
            subtotal += item.total;
            totalItemsCount += item.quantity;
        }

        String discountStr = edtDiscount.getText().toString().trim();
        double discount = 0.0;
        if (!discountStr.isEmpty()) {
            discount = Double.parseDouble(discountStr);
        }

        double netTotal = subtotal - discount;
        if (netTotal < 0) netTotal = 0;

        // VAT is disabled per user request
        double taxBreakdown = 0.0;

        txtCartItemsCount.setText(totalItemsCount + " Items in Cart");
        txtCartSalesSum.setText(String.format(Locale.getDefault(), "LKR %.2f", netTotal));

        txtSubtotal.setText(String.format(Locale.getDefault(), "LKR %.2f", subtotal));
        txtTax.setText(String.format(Locale.getDefault(), "LKR %.2f", taxBreakdown));
        txtNetTotal.setText(String.format(Locale.getDefault(), "LKR %.2f", netTotal));
    }

    private void setupCartSummary() {
        if (cartAdapter == null) {
            cartAdapter = new CartAdapter();
            lstCartSummary.setAdapter(cartAdapter);
        } else {
            cartAdapter.notifyDataSetChanged();
        }
        recalculateCart();
    }

    private void processCheckoutOffline() {
        if (selectedCustomer == null) {
            Toast.makeText(this, "Please select a valid customer shop.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cartList.isEmpty()) {
            Toast.makeText(this, "Your shopping cart is empty.", Toast.LENGTH_SHORT).show();
            return;
        }
        processDiscountPromptsAndCheckout();
    }

    private void processDiscountPromptsAndCheckout() {
        final List<DiscountCheckResult> itemDiscounts = evaluateItemDiscounts();
        final DiscountCheckResult billDiscount = evaluateBillDiscount();
        showItemDiscountPrompts(itemDiscounts, 0, billDiscount);
    }

    private void showItemDiscountPrompts(final List<DiscountCheckResult> itemDiscounts, final int index, final DiscountCheckResult billDiscount) {
        if (index < itemDiscounts.size()) {
            final DiscountCheckResult rule = itemDiscounts.get(index);
            final int freeQty = (int) rule.rewardVal;

            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            builder.setTitle("Free Issue Offer!");
            builder.setMessage("You qualify for " + freeQty + " free unit(s) of \"" + rule.targetItemName + "\" under promotional rule \"" + rule.name + "\".\n\nWould you like to accept this offer?");
            builder.setPositiveButton("Accept", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    // Add free item to cart
                    CartItemModel freeItem = new CartItemModel();
                    freeItem.productId = rule.targetItemId;
                    freeItem.name = rule.targetItemName + " (Free Issue)";
                    freeItem.price = 0.0;
                    freeItem.wholesalePrice = 0.0;
                    freeItem.activePrice = 0.0;
                    freeItem.quantity = freeQty;
                    freeItem.customPrice = 0.0;
                    freeItem.discountPercent = 0.0;
                    freeItem.discountAmount = 0.0;
                    freeItem.discountVal = 0.0;
                    freeItem.total = 0.0;
                    cartList.add(freeItem);

                    recalculateCart();
                    
                    // Show next prompt
                    showItemDiscountPrompts(itemDiscounts, index + 1, billDiscount);
                }
            });
            builder.setNegativeButton("Reject", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    dialog.dismiss();
                    // Show next prompt
                    showItemDiscountPrompts(itemDiscounts, index + 1, billDiscount);
                }
            });
            builder.create().show();
        } else {
            showBillDiscountPrompt(billDiscount);
        }
    }

    private void showBillDiscountPrompt(final DiscountCheckResult billDiscount) {
        if (billDiscount != null) {
            String currentDiscountText = edtDiscount.getText().toString().trim();
            double currentDiscount = currentDiscountText.isEmpty() ? 0.0 : Double.parseDouble(currentDiscountText);

            if (currentDiscount == 0.0) {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
                builder.setTitle("Discount Offer!");
                builder.setMessage("Your subtotal qualifies for a " + String.format(Locale.getDefault(), "%.1f", billDiscount.rewardVal) + "% global discount under \"" + billDiscount.name + "\".\n\nWould you like to apply this discount?");
                builder.setPositiveButton("Accept", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        double subtotal = 0.0;
                        for (CartItemModel item : cartList) {
                            subtotal += item.total;
                        }
                        double discountAmt = subtotal * (billDiscount.rewardVal / 100.0);
                        edtDiscount.setText(String.format(Locale.getDefault(), "%.2f", discountAmt));
                        edtDirectDiscountPct.setText(String.format(Locale.getDefault(), "%.1f", billDiscount.rewardVal));

                        recalculateCart();
                        executeDatabaseCheckout();
                    }
                });
                builder.setNegativeButton("Reject", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        dialog.dismiss();
                        executeDatabaseCheckout();
                    }
                });
                builder.create().show();
                return;
            }
        }
        executeDatabaseCheckout();
    }

    private void executeDatabaseCheckout() {
        CustomerModel customer = selectedCustomer;
        String payment = "Term";

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            String todayDateCompact = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

            android.content.SharedPreferences seqPrefs = getSharedPreferences("CurtissPrefs", android.content.Context.MODE_PRIVATE);
            int seq = seqPrefs.getInt("global_invoice_seq", 0);

            if (seq == 0) {
                Cursor maxCursor = db.rawQuery(
                    "SELECT invoice_number FROM invoices ORDER BY id DESC LIMIT 1", null
                );
                if (maxCursor != null) {
                    if (maxCursor.moveToFirst()) {
                        String lastInvoiceNum = maxCursor.getString(0);
                        if (lastInvoiceNum.length() >= 4) {
                            try {
                                String suffix = lastInvoiceNum.substring(lastInvoiceNum.length() - 4);
                                seq = Integer.parseInt(suffix);
                            } catch (Exception e) {
                                seq = 0;
                            }
                        }
                    }
                    maxCursor.close();
                }
            }

            seq++;
            seqPrefs.edit().putInt("global_invoice_seq", seq).apply();

            String invoiceNum = String.format(Locale.getDefault(), "%s%04d", todayDateCompact, seq);
            String dateString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            double subtotal = 0.0;
            for (CartItemModel item : cartList) {
                subtotal += item.total;
            }

            String discountStr = edtDiscount.getText().toString().trim();
            double discount = 0.0;
            if (!discountStr.isEmpty()) {
                discount = Double.parseDouble(discountStr);
            }

            double netTotal = subtotal - discount;
            if (netTotal < 0) netTotal = 0;
            double tax = 0.0;

            Integer paymentTermId = null;
            int daysOffset = 0;
            int termPos = spinnerPaymentTerm.getSelectedItemPosition();
            if (!paymentTermList.isEmpty() && termPos >= 0 && termPos < paymentTermList.size()) {
                PaymentTermModel selectedTerm = paymentTermList.get(termPos);
                paymentTermId = selectedTerm.id;
                daysOffset = selectedTerm.daysDue;
            }

            java.util.Calendar cal = java.util.Calendar.getInstance();
            if (daysOffset > 0) {
                cal.add(java.util.Calendar.DAY_OF_YEAR, daysOffset);
            }
            String dueDateString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(cal.getTime());

            double capturedLat = 7.1824;
            double capturedLng = 79.8801;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
                    }
                } catch (Exception e) {
                    android.util.Log.e("BillingActivity", "Location capture exception: " + e.getMessage());
                }
            }

            ContentValues cvHeader = new ContentValues();
            cvHeader.put("invoice_number", invoiceNum);
            cvHeader.put("customer_id", customer.id);
            cvHeader.put("route_id", currentRouteLocalId);
            cvHeader.put("invoice_date", dateString);
            cvHeader.put("due_date", dueDateString);
            if (paymentTermId != null) {
                cvHeader.put("payment_term_id", paymentTermId);
            } else {
                cvHeader.putNull("payment_term_id");
            }
            cvHeader.put("subtotal", subtotal);
            cvHeader.put("discount", discount);
            cvHeader.put("tax", tax);
            cvHeader.put("grand_total", netTotal);
            cvHeader.put("payment_method", payment);
            cvHeader.put("latitude", capturedLat);
            cvHeader.put("longitude", capturedLng);
            cvHeader.put("is_synced", 0);

            long localInvId = db.insert("invoices", null, cvHeader);

            for (CartItemModel item : cartList) {
                ContentValues cvItem = new ContentValues();
                cvItem.put("invoice_id", localInvId);
                cvItem.put("product_id", item.productId);
                cvItem.put("product_name", item.name);
                cvItem.put("quantity", item.quantity);
                cvItem.put("unit_price", item.activePrice);
                cvItem.put("discount_val", item.discountVal);
                cvItem.put("total", item.total);

                db.insert("invoice_items", null, cvItem);
                db.execSQL("UPDATE products SET quantity_reserved = quantity_reserved + " + item.quantity + " WHERE id = " + item.productId);
            }

            db.setTransactionSuccessful();
            showShareBillDialog(invoiceNum, customer.name, customer.phone);

        } catch (Exception e) {
            Toast.makeText(this, "Checkout failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            db.endTransaction();
        }
    }

    private List<DiscountCheckResult> evaluateItemDiscounts() {
        List<DiscountCheckResult> qualified = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                "SELECT r.id, r.name, r.rule_type, r.target_item_id, p.name as target_item_name, " +
                "t.min_threshold, t.reward_val " +
                "FROM discount_rules r " +
                "JOIN discount_rule_tiers t ON r.id = t.rule_id " +
                "LEFT JOIN products p ON r.target_item_id = p.id " +
                "WHERE r.status = 'Active' AND r.rule_type = 'item_wise' " +
                "ORDER BY r.id ASC, t.min_threshold DESC", null
            );

            List<Integer> processedRules = new ArrayList<>();
            while (cursor.moveToNext()) {
                int ruleId = cursor.getInt(0);
                if (processedRules.contains(ruleId)) {
                    continue;
                }

                String name = cursor.getString(1);
                String type = cursor.getString(2);
                int targetId = cursor.getInt(3);
                String itemName = cursor.getString(4);
                double minThresh = cursor.getDouble(5);
                double rewardVal = cursor.getDouble(6);

                int cartQty = 0;
                boolean alreadyHasFree = false;
                for (CartItemModel item : cartList) {
                    if (item.productId == targetId) {
                        if (item.customPrice == 0.0 && item.total == 0.0) {
                            alreadyHasFree = true;
                        } else {
                            cartQty += item.quantity;
                        }
                    }
                }

                if (cartQty >= minThresh && !alreadyHasFree) {
                    processedRules.add(ruleId);
                    DiscountCheckResult res = new DiscountCheckResult();
                    res.ruleId = ruleId;
                    res.name = name;
                    res.ruleType = type;
                    res.targetItemId = targetId;
                    res.targetItemName = itemName != null ? itemName : "Product";
                    res.minThreshold = minThresh;
                    res.rewardVal = rewardVal;
                    qualified.add(res);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("BillingActivity", "Error evaluating item discounts: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return qualified;
    }

    private DiscountCheckResult evaluateBillDiscount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            double subtotal = 0.0;
            for (CartItemModel item : cartList) {
                subtotal += item.total;
            }

            cursor = db.rawQuery(
                "SELECT r.id, r.name, r.rule_type, t.min_threshold, t.reward_val " +
                "FROM discount_rules r " +
                "JOIN discount_rule_tiers t ON r.id = t.rule_id " +
                "WHERE r.status = 'Active' AND r.rule_type = 'bill_wise' " +
                "ORDER BY t.min_threshold DESC", null
            );

            while (cursor.moveToNext()) {
                double minThresh = cursor.getDouble(3);
                double rewardVal = cursor.getDouble(4);

                if (subtotal >= minThresh) {
                    DiscountCheckResult res = new DiscountCheckResult();
                    res.ruleId = cursor.getInt(0);
                    res.name = cursor.getString(1);
                    res.ruleType = cursor.getString(2);
                    res.minThreshold = minThresh;
                    res.rewardVal = rewardVal;
                    return res;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("BillingActivity", "Error evaluating bill discounts: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private void showShareBillDialog(final String invoiceNum, final String customerName, final String customerPhone) {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar);
        dialog.setCancelable(false);

        // Main Container
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(android.graphics.Color.parseColor("#0F172A")); // Slate 900
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);
        container.setGravity(android.view.Gravity.CENTER);

        // Success Icon
        TextView txtCheck = new TextView(this);
        txtCheck.setText("✓");
        txtCheck.setTextColor(android.graphics.Color.parseColor("#22C55E")); // Green 500
        txtCheck.setTextSize(48);
        txtCheck.setGravity(android.view.Gravity.CENTER);
        container.addView(txtCheck);

        // Success Title
        TextView txtTitle = new TextView(this);
        txtTitle.setText("Checkout Successful!");
        txtTitle.setTextColor(android.graphics.Color.WHITE);
        txtTitle.setTextSize(20);
        txtTitle.setGravity(android.view.Gravity.CENTER);
        txtTitle.setPadding(0, 8, 0, 4);
        txtTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(txtTitle);

        // Invoice Number Label
        TextView txtInvoice = new TextView(this);
        txtInvoice.setText(invoiceNum);
        txtInvoice.setTextColor(android.graphics.Color.parseColor("#CBD5E1")); // Slate 400
        txtInvoice.setTextSize(16);
        txtInvoice.setGravity(android.view.Gravity.CENTER);
        txtInvoice.setPadding(0, 0, 0, 16);
        container.addView(txtInvoice);

        // Reserved Stock Quantities Breakdown Display
        LinearLayout reservationSummaryLayout = new LinearLayout(this);
        reservationSummaryLayout.setOrientation(LinearLayout.VERTICAL);
        reservationSummaryLayout.setBackgroundColor(android.graphics.Color.parseColor("#1E293B")); // Dark slate card background
        reservationSummaryLayout.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams resParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        resParams.setMargins(0, 0, 0, 24);
        reservationSummaryLayout.setLayoutParams(resParams);

        TextView txtResHeader = new TextView(this);
        txtResHeader.setText("⚡ OFFLINE RESERVED QUANTITIES");
        txtResHeader.setTextColor(android.graphics.Color.parseColor("#38BDF8")); // Bright blue accent
        txtResHeader.setTextSize(11);
        txtResHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        txtResHeader.setPadding(0, 0, 0, 8);
        reservationSummaryLayout.addView(txtResHeader);

        for (CartItemModel cartItem : cartList) {
            TextView txtItemDetail = new TextView(this);
            txtItemDetail.setText("• " + cartItem.name + "\n  Reserved: " + cartItem.quantity + " qty");
            txtItemDetail.setTextColor(android.graphics.Color.WHITE);
            txtItemDetail.setTextSize(13);
            txtItemDetail.setPadding(0, 4, 0, 4);
            reservationSummaryLayout.addView(txtItemDetail);
        }
        container.addView(reservationSummaryLayout);

        // QR Code Card View / Frame
        LinearLayout qrFrame = new LinearLayout(this);
        qrFrame.setOrientation(LinearLayout.VERTICAL);
        qrFrame.setBackgroundColor(android.graphics.Color.WHITE);
        qrFrame.setPadding(16, 16, 16, 16);
        qrFrame.setGravity(android.view.Gravity.CENTER);
        
        final ImageView imgQr = new ImageView(this);
        int qrSize = (int) (200 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        imgQr.setLayoutParams(qrParams);
        imgQr.setImageResource(android.R.drawable.stat_sys_download); // Downloading icon
        qrFrame.addView(imgQr);
        container.addView(qrFrame);

        // QR Code Label
        TextView txtQrDesc = new TextView(this);
        txtQrDesc.setText("Scan QR to View Bill Online");
        txtQrDesc.setTextColor(android.graphics.Color.parseColor("#CBD5E1")); // Slate 400
        txtQrDesc.setTextSize(12);
        txtQrDesc.setGravity(android.view.Gravity.CENTER);
        txtQrDesc.setPadding(0, 8, 0, 24);
        container.addView(txtQrDesc);

        // Buttons Container
        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.VERTICAL);
        btnLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 1. WhatsApp Button
        Button btnWhatsApp = new Button(this);
        btnWhatsApp.setText("Share via WhatsApp");
        btnWhatsApp.setTextColor(android.graphics.Color.WHITE);
        btnWhatsApp.setBackgroundColor(android.graphics.Color.parseColor("#22C55E")); // Green 500
        btnWhatsApp.setAllCaps(false);
        LinearLayout.LayoutParams btnWaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnWaParams.setMargins(0, 0, 0, 12);
        btnWhatsApp.setLayoutParams(btnWaParams);
        btnWhatsApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String cleanPhone = customerPhone;
                if (cleanPhone != null) {
                    cleanPhone = cleanPhone.replaceAll("[^0-9]", "");
                    if (cleanPhone.startsWith("0")) {
                        cleanPhone = "94" + cleanPhone.substring(1);
                    }
                } else {
                    cleanPhone = "";
                }
                String msg = "Dear " + customerName + ", thank you for your business. Here is the link to view your invoice " + invoiceNum + " online: https://curtiss.suzxlabs.com/sales/show/" + invoiceNum;
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + cleanPhone + "&text=" + Uri.encode(msg)));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(BillingActivity.this, "WhatsApp is not installed on this device.", Toast.LENGTH_SHORT).show();
                }
            }
        });
        btnLayout.addView(btnWhatsApp);

        // 2. Done Button
        Button btnClose = new Button(this);
        btnClose.setText("Back to Dashboard");
        btnClose.setTextColor(android.graphics.Color.WHITE);
        btnClose.setBackgroundColor(android.graphics.Color.parseColor("#94A3B8")); // Slate 600
        btnClose.setAllCaps(false);
        btnClose.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                finish();
            }
        });
        btnLayout.addView(btnClose);

        container.addView(btnLayout);
        dialog.setContentView(container);

        // Set layout params for Dialog window
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();

        // Load QR Code in background thread
        String qrApiUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + Uri.encode("https://curtiss.suzxlabs.com/sales/show/" + invoiceNum);
        loadQrCode(qrApiUrl, imgQr);
    }

    private void loadQrCode(final String url, final ImageView imageView) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.net.URL qrUrl = new java.net.URL(url);
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) qrUrl.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    java.io.InputStream input = connection.getInputStream();
                    final android.graphics.Bitmap myBitmap = android.graphics.BitmapFactory.decodeStream(input);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            imageView.setImageBitmap(myBitmap);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // Helper Models
    private static class DiscountCheckResult {
        int ruleId;
        String name;
        String ruleType;
        int targetItemId;
        String targetItemName;
        double minThreshold;
        double rewardVal;
    }

    private static class PaymentTermModel {
        int id;
        String name;
        int daysDue;
    }

    private static class CustomerModel {
        int id, serverId;
        String name;
        String phone;
        double outstanding;
    }

    private static class ProductModel {
        int id, qtyOnHand, qtyReserved;
        String name, category, localImagePath;
        double price, wholesalePrice;
    }

    private static class CartItemModel {
        int productId, quantity;
        String name;
        double price, wholesalePrice, activePrice, total;
        double customPrice, discountPercent, discountAmount, discountVal;
    }

    // Product visual catalog list adapter
    private class ProductAdapter extends BaseAdapter {
        @Override
        public int getCount() { return productList.size(); }
        @Override
        public Object getItem(int position) { return productList.get(position); }
        @Override
        public long getItemId(int position) { return productList.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(BillingActivity.this).inflate(R.layout.item_product, parent, false);
            }

            final ProductModel p = productList.get(position);

            TextView lblProductName = convertView.findViewById(R.id.lblProductName);
            TextView lblCategory = convertView.findViewById(R.id.lblCategory);
            TextView lblPrice = convertView.findViewById(R.id.lblPrice);
            TextView lblStock = convertView.findViewById(R.id.lblStock);

            lblProductName.setText(p.name);
            lblCategory.setText(p.category);

            final double currentPrice = p.price;
            lblPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", currentPrice));

            int available = p.qtyOnHand - p.qtyReserved;
            lblStock.setText("Available Stock: " + available);

            if (available <= 0) {
                lblStock.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                lblStock.setText("Out of Stock");
            } else {
                lblStock.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showProductConfigDialog(p);
                }
            });

            return convertView;
        }
    }

    // Shopping Cart Summary popup adapter
    private class CartAdapter extends BaseAdapter {
        @Override
        public int getCount() { return cartList.size(); }
        @Override
        public Object getItem(int position) { return cartList.get(position); }
        @Override
        public long getItemId(int position) { return cartList.get(position).productId; }

        @Override
        public View getView(int position, View parentConvertView, ViewGroup parent) {
            View view = parentConvertView;
            if (view == null) {
                // Instantiation row inside Dialog lists
                view = LayoutInflater.from(BillingActivity.this).inflate(android.R.layout.simple_list_item_2, parent, false);
            }

            CartItemModel item = cartList.get(position);
            TextView text1 = view.findViewById(android.R.id.text1);
            TextView text2 = view.findViewById(android.R.id.text2);

            text1.setText(item.name);
            text1.setTextColor(getResources().getColor(android.R.color.white));

            if (item.discountVal > 0) {
                text2.setText(String.format(Locale.getDefault(), "Qty: %d  x  LKR %.2f (Less LKR %.2f Disc)  =  LKR %.2f", item.quantity, item.activePrice, item.discountVal, item.total));
            } else {
                text2.setText(String.format(Locale.getDefault(), "Qty: %d  x  LKR %.2f   =   LKR %.2f", item.quantity, item.activePrice, item.total));
            }
            text2.setTextColor(getResources().getColor(android.R.color.holo_blue_light));

            return view;
        }
    }

    private void addToCart(ProductModel p, int qty) {
        int left = p.qtyOnHand - p.qtyReserved;
        if (left <= 0) {
            Toast.makeText(BillingActivity.this, "Out of Stock.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (qty <= 0) {
            Toast.makeText(BillingActivity.this, "Enter valid quantity.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (qty > left) {
            Toast.makeText(BillingActivity.this, "Insufficient stock available offline.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean found = false;
        for (CartItemModel item : cartList) {
            if (item.productId == p.id) {
                if (item.quantity + qty > left) {
                    Toast.makeText(BillingActivity.this, "Cannot exceed available stock.", Toast.LENGTH_SHORT).show();
                    return;
                }
                item.quantity += qty;
                found = true;
                break;
            }
        }

        if (!found) {
            CartItemModel item = new CartItemModel();
            item.productId = p.id;
            item.name = p.name;
            item.price = p.price;
            item.wholesalePrice = p.wholesalePrice;
            item.quantity = qty;
            cartList.add(item);
        }

        recalculateCart();
        Toast.makeText(BillingActivity.this, p.name + " added to cart.", Toast.LENGTH_SHORT).show();
    }

    private void showProductConfigDialog(final ProductModel p) {
        final androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        // Root container
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);
        layout.setBackgroundColor(android.graphics.Color.parseColor("#1E293B"));

        // Title
        TextView txtTitle = new TextView(this);
        txtTitle.setText(p.name);
        txtTitle.setTextColor(android.graphics.Color.WHITE);
        txtTitle.setTextSize(18);
        txtTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(txtTitle);

        // Subtitle
        TextView txtSub = new TextView(this);
        txtSub.setText("Original Price: LKR " + String.format(Locale.getDefault(), "%,.2f", p.price));
        txtSub.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
        txtSub.setTextSize(12);
        txtSub.setPadding(0, 8, 0, 24);
        layout.addView(txtSub);

        // 1. Price override input
        TextView lblOverride = new TextView(this);
        lblOverride.setText("UNIT PRICE (LKR):");
        lblOverride.setTextColor(android.graphics.Color.parseColor("#3B82F6"));
        lblOverride.setTextSize(11);
        lblOverride.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(lblOverride);

        final EditText edtOverridePrice = new EditText(this);
        edtOverridePrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edtOverridePrice.setTextColor(android.graphics.Color.WHITE);
        edtOverridePrice.setHintTextColor(android.graphics.Color.parseColor("#94A3B8"));
        edtOverridePrice.setText(String.valueOf(p.price));
        edtOverridePrice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
        layout.addView(edtOverridePrice);

        // 2. Quantity input
        TextView lblQty = new TextView(this);
        lblQty.setText("QUANTITY:");
        lblQty.setTextColor(android.graphics.Color.parseColor("#3B82F6"));
        lblQty.setTextSize(11);
        lblQty.setTypeface(null, android.graphics.Typeface.BOLD);
        lblQty.setPadding(0, 16, 0, 0);
        layout.addView(lblQty);

        final EditText edtQtyInput = new EditText(this);
        edtQtyInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        edtQtyInput.setTextColor(android.graphics.Color.WHITE);
        edtQtyInput.setHintTextColor(android.graphics.Color.parseColor("#94A3B8"));
        edtQtyInput.setText("1");
        edtQtyInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
        layout.addView(edtQtyInput);

        // 3. Discount inputs
        LinearLayout discountRow = new LinearLayout(this);
        discountRow.setOrientation(LinearLayout.HORIZONTAL);
        discountRow.setPadding(0, 16, 0, 0);

        // Percentage discount
        LinearLayout colPct = new LinearLayout(this);
        colPct.setOrientation(LinearLayout.VERTICAL);
        colPct.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView lblPct = new TextView(this);
        lblPct.setText("DISCOUNT (%):");
        lblPct.setTextColor(android.graphics.Color.parseColor("#10B981"));
        lblPct.setTextSize(11);
        lblPct.setTypeface(null, android.graphics.Typeface.BOLD);
        colPct.addView(lblPct);

        final EditText edtDiscountPct = new EditText(this);
        edtDiscountPct.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edtDiscountPct.setTextColor(android.graphics.Color.WHITE);
        edtDiscountPct.setHintTextColor(android.graphics.Color.parseColor("#94A3B8"));
        edtDiscountPct.setHint("0.0%");
        edtDiscountPct.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
        colPct.addView(edtDiscountPct);

        discountRow.addView(colPct);

        // Spacer
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(24, 1));
        discountRow.addView(spacer);

        // Amount discount
        LinearLayout colAmt = new LinearLayout(this);
        colAmt.setOrientation(LinearLayout.VERTICAL);
        colAmt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView lblAmt = new TextView(this);
        lblAmt.setText("DISCOUNT (LKR):");
        lblAmt.setTextColor(android.graphics.Color.parseColor("#10B981"));
        lblAmt.setTextSize(11);
        lblAmt.setTypeface(null, android.graphics.Typeface.BOLD);
        colAmt.addView(lblAmt);

        final EditText edtDiscountAmt = new EditText(this);
        edtDiscountAmt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edtDiscountAmt.setTextColor(android.graphics.Color.WHITE);
        edtDiscountAmt.setHintTextColor(android.graphics.Color.parseColor("#94A3B8"));
        edtDiscountAmt.setHint("Rs 0.00");
        edtDiscountAmt.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
        colAmt.addView(edtDiscountAmt);

        discountRow.addView(colAmt);
        layout.addView(discountRow);

        // Live Total Preview Label
        final TextView txtLiveTotal = new TextView(this);
        txtLiveTotal.setText("TOTAL: LKR " + String.format(Locale.getDefault(), "%,.2f", p.price));
        txtLiveTotal.setTextColor(android.graphics.Color.parseColor("#10B981"));
        txtLiveTotal.setTextSize(16);
        txtLiveTotal.setTypeface(null, android.graphics.Typeface.BOLD);
        txtLiveTotal.setPadding(0, 24, 0, 16);
        txtLiveTotal.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        layout.addView(txtLiveTotal);

        builder.setView(layout);
        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        // live preview update helper
        final Runnable updatePreview = new Runnable() {
            private boolean isUpdating = false;
            @Override
            public void run() {
                if (isUpdating) return;
                isUpdating = true;
                try {
                    String overridePriceStr = edtOverridePrice.getText().toString().trim();
                    double uPrice = overridePriceStr.isEmpty() ? p.price : Double.parseDouble(overridePriceStr);

                    String qtyStr = edtQtyInput.getText().toString().trim();
                    int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);

                    double sub = uPrice * qty;
                    double discountVal = 0.0;

                    String pctStr = edtDiscountPct.getText().toString().trim();
                    String amtStr = edtDiscountAmt.getText().toString().trim();

                    if (edtDiscountPct.hasFocus() && !pctStr.isEmpty()) {
                        double pct = Double.parseDouble(pctStr);
                        discountVal = sub * (pct / 100.0);
                        edtDiscountAmt.setText(String.format(Locale.getDefault(), "%.2f", discountVal));
                    } else if (edtDiscountAmt.hasFocus() && !amtStr.isEmpty()) {
                        discountVal = Double.parseDouble(amtStr);
                        double pct = sub > 0 ? (discountVal / sub) * 100.0 : 0.0;
                        edtDiscountPct.setText(String.format(Locale.getDefault(), "%.1f", pct));
                    } else {
                        if (!pctStr.isEmpty()) {
                            double pct = Double.parseDouble(pctStr);
                            discountVal = sub * (pct / 100.0);
                        } else if (!amtStr.isEmpty()) {
                            discountVal = Double.parseDouble(amtStr);
                        }
                    }

                    double finalTotal = sub - discountVal;
                    if (finalTotal < 0) finalTotal = 0;
                    txtLiveTotal.setText("TOTAL: LKR " + String.format(Locale.getDefault(), "%,.2f", finalTotal));
                } catch (Exception ignored) {}
                isUpdating = false;
            }
        };

        // Textwatchers for interactive live calculations
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview.run();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        };

        edtOverridePrice.addTextChangedListener(watcher);
        edtQtyInput.addTextChangedListener(watcher);
        edtDiscountPct.addTextChangedListener(watcher);
        edtDiscountAmt.addTextChangedListener(watcher);

        // Buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.END);

        Button btnCancel = new Button(this);
        btnCancel.setText("Cancel");
        btnCancel.setTextColor(android.graphics.Color.WHITE);
        btnCancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#94A3B8")));
        btnRow.addView(btnCancel);

        View spacerBtn = new View(this);
        spacerBtn.setLayoutParams(new LinearLayout.LayoutParams(24, 1));
        btnRow.addView(spacerBtn);

        Button btnAddCart = new Button(this);
        btnAddCart.setText("Add to Cart");
        btnAddCart.setTextColor(android.graphics.Color.WHITE);
        btnAddCart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#10B981")));
        btnRow.addView(btnAddCart);

        layout.addView(btnRow);

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnAddCart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String overridePriceStr = edtOverridePrice.getText().toString().trim();
                    double uPrice = overridePriceStr.isEmpty() ? p.price : Double.parseDouble(overridePriceStr);

                    String qtyStr = edtQtyInput.getText().toString().trim();
                    int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);

                    double pct = 0.0;
                    String pctStr = edtDiscountPct.getText().toString().trim();
                    if (!pctStr.isEmpty()) pct = Double.parseDouble(pctStr);

                    double amt = 0.0;
                    String amtStr = edtDiscountAmt.getText().toString().trim();
                    if (!amtStr.isEmpty()) amt = Double.parseDouble(amtStr);

                    // Add to cart with custom parameters
                    addToCartCustom(p, qty, uPrice, pct, amt);
                    dialog.dismiss();
                } catch (Exception e) {
                    Toast.makeText(BillingActivity.this, "Invalid inputs entered.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void addToCartCustom(ProductModel p, int qty, double customPrice, double discountPercent, double discountAmount) {
        int left = p.qtyOnHand - p.qtyReserved;
        if (left <= 0) {
            Toast.makeText(BillingActivity.this, "Out of Stock.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (qty <= 0) {
            Toast.makeText(BillingActivity.this, "Enter valid quantity.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (qty > left) {
            Toast.makeText(BillingActivity.this, "Insufficient stock available offline.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean found = false;
        double sub = customPrice * qty;
        double discountVal = 0.0;

        if (discountPercent > 0) {
            discountVal = sub * (discountPercent / 100.0);
        } else if (discountAmount > 0) {
            discountVal = discountAmount;
        }

        double total = sub - discountVal;
        if (total < 0) total = 0;

        for (CartItemModel item : cartList) {
            if (item.productId == p.id) {
                if (item.quantity + qty > left) {
                    Toast.makeText(BillingActivity.this, "Cannot exceed available stock.", Toast.LENGTH_SHORT).show();
                    return;
                }
                item.quantity += qty;
                item.customPrice = customPrice;
                item.discountPercent = discountPercent;
                item.discountAmount = discountAmount;
                item.discountVal = (item.customPrice * item.quantity) * (discountPercent / 100.0) + discountAmount;
                item.total = (item.customPrice * item.quantity) - item.discountVal;
                if (item.total < 0) item.total = 0;
                found = true;
                break;
            }
        }

        if (!found) {
            CartItemModel item = new CartItemModel();
            item.productId = p.id;
            item.name = p.name;
            item.price = p.price;
            item.customPrice = customPrice;
            item.discountPercent = discountPercent;
            item.discountAmount = discountAmount;
            item.discountVal = discountVal;
            item.quantity = qty;
            item.activePrice = customPrice;
            item.total = total;
            cartList.add(item);
        }

        recalculateCart();
        Toast.makeText(BillingActivity.this, p.name + " added to cart.", Toast.LENGTH_SHORT).show();
    }

    private class ProductGridAdapter extends BaseAdapter {
        @Override
        public int getCount() { return productList.size(); }
        @Override
        public Object getItem(int position) { return productList.get(position); }
        @Override
        public long getItemId(int position) { return productList.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(BillingActivity.this).inflate(R.layout.item_product_grid, parent, false);
            }

            final ProductModel p = productList.get(position);

            ImageView imgProductGrid = convertView.findViewById(R.id.imgProductGrid);
            TextView lblProductNameGrid = convertView.findViewById(R.id.lblProductNameGrid);
            TextView lblCategoryGrid = convertView.findViewById(R.id.lblCategoryGrid);
            TextView lblPriceGrid = convertView.findViewById(R.id.lblPriceGrid);
            TextView lblStockGrid = convertView.findViewById(R.id.lblStockGrid);

            lblProductNameGrid.setText(p.name);
            lblCategoryGrid.setText(p.category);

            final double currentPrice = p.price;
            lblPriceGrid.setText(String.format(Locale.getDefault(), "LKR %.2f", currentPrice));

            final int available = p.qtyOnHand - p.qtyReserved;
            lblStockGrid.setText("Stock: " + available);

            if (available <= 0) {
                lblStockGrid.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                lblStockGrid.setText("Out of Stock");
            } else {
                lblStockGrid.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }

            if (imgProductGrid != null) {
                if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
                    File file = new File(p.localImagePath);
                    if (file.exists()) {
                        imgProductGrid.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));
                    } else {
                        imgProductGrid.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                } else {
                    imgProductGrid.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            }

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showProductConfigDialog(p);
                }
            });

            return convertView;
        }
    }

    private void setupCategorySpinner() {
        categoryList.clear();
        categoryList.add("All Categories");

        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            // Try querying the dedicated categories table loaded directly from item_categories
            Cursor cursor = db.rawQuery("SELECT name FROM categories ORDER BY name ASC", null);
            while (cursor.moveToNext()) {
                categoryList.add(cursor.getString(0));
            }
            cursor.close();

            // Fallback to distinct product category names if categories table is not yet seeded
            if (categoryList.size() <= 1) {
                cursor = db.rawQuery("SELECT DISTINCT category_name FROM products WHERE category_name IS NOT NULL AND category_name != '' AND category_name != 'null' ORDER BY category_name ASC", null);
                while (cursor.moveToNext()) {
                    categoryList.add(cursor.getString(0));
                }
                cursor.close();
            }
        } catch (Exception e) {
            android.util.Log.e("BillingCategory", "Error loading categories: " + e.getMessage());
        }

        ArrayAdapter<String> catAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, categoryList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(android.graphics.Color.WHITE);
                    ((TextView) v).setTextSize(14);
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(android.graphics.Color.WHITE);
                    v.setBackgroundColor(android.graphics.Color.parseColor("#1E293B"));
                    v.setPadding(16, 16, 16, 16);
                }
                return v;
            }
        };
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(catAdapter);

        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadCatalogItems(edtProductSearch.getText().toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void showCustomerSelectionDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(BillingActivity.this);
        
        // Inflate dialog view
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_start_route, null);
        builder.setView(dialogView);
        
        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.show();

        // Customise start route layout to read customer shop search
        TextView header = dialogView.findViewById(R.id.txtSelectedRoute); 
        if (header != null) {
            header.setText("Search & Select Customer Shop:");
            header.setTextColor(android.graphics.Color.WHITE);
        }

        final EditText edtSearch = dialogView.findViewById(R.id.edtRouteSearch);
        if (edtSearch != null) {
            edtSearch.setHint("🔍 Search Customer Shop...");
        }

        final ListView lstCusts = dialogView.findViewById(R.id.lstRoutes);
        
        // Hide starting odometer fields (they are not needed here!)
        View odoInput = dialogView.findViewById(R.id.edtStartOdoDialog);
        if (odoInput != null) {
            odoInput.setVisibility(View.GONE);
        }
        
        // Load customers from DB into customerList
        loadCustomers();

        final List<String> allCustNames = new ArrayList<>();
        for (CustomerModel c : customerList) {
            allCustNames.add(c.name + " (Bal: LKR " + String.format(Locale.getDefault(), "%,.2f", c.outstanding) + ")");
        }
        final List<String> filteredCustNames = new ArrayList<>(allCustNames);

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(BillingActivity.this, R.layout.item_route, filteredCustNames);
        if (lstCusts != null) {
            lstCusts.setAdapter(adapter);
            
            // Search text watcher
            if (edtSearch != null) {
                edtSearch.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        filteredCustNames.clear();
                        String filter = s.toString().toLowerCase().trim();
                        for (CustomerModel c : customerList) {
                            if (c.name.toLowerCase().contains(filter)) {
                                filteredCustNames.add(c.name + " (Bal: LKR " + String.format(Locale.getDefault(), "%,.2f", c.outstanding) + ")");
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void afterTextChanged(android.text.Editable s) {}
                });
            }

            // Click listener
            lstCusts.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    String chosenName = filteredCustNames.get(position);
                    for (CustomerModel c : customerList) {
                        String matchStr = c.name + " (Bal: LKR " + String.format(Locale.getDefault(), "%,.2f", c.outstanding) + ")";
                        if (matchStr.equalsIgnoreCase(chosenName)) {
                            selectedCustomer = c;
                            break;
                        }
                    }
                    if (selectedCustomer != null) {
                        String displayName = selectedCustomer.name + "\nOutstanding: LKR " + String.format(Locale.getDefault(), "%,.2f", selectedCustomer.outstanding);
                        txtSelectedCustomerName.setText(displayName);
                        dialog.dismiss();
                    }
                }
            });
        }

        Button btnCancel = dialogView.findViewById(R.id.btnCancelDialog);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (selectedCustomer == null) {
                        Toast.makeText(BillingActivity.this, "Billing requires a customer selection.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            });
        }

        // Hide "Start Trip" button in customer chooser
        View btnStartTrip = dialogView.findViewById(R.id.btnStartTripDialog);
        if (btnStartTrip != null) {
            btnStartTrip.setVisibility(View.GONE);
        }
    }
}
