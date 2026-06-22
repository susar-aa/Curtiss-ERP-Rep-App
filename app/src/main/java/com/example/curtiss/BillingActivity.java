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
import android.widget.ImageButton;
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
    private TextView txtCartItemsCount, txtCartSalesSum, txtSubtotal, txtTax, txtNetTotal, txtRoundingAdjustment;
    private RelativeLayout layoutRoundingAdjustment;

    private TextView txtSelectedCustomerName;
    private Button btnChangeCustomer;
    private CustomerModel selectedCustomer = null;
    private List<String> categoryList = new ArrayList<>();
    private String selectedCategoryName = "All Categories";
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

    // Redesigned layouts and controls
    private LinearLayout layoutCartViewMode, layoutCartCheckoutMode, layoutCheckoutDetails, layoutExpandedSearch;
    private TextView txtCartOverlayTitle, txtOverlayCustomerName, txtOverlayCustomerBalance, txtOverlayTotalItems, txtOverlayTotalValue, txtCheckoutCustomerName, txtCheckoutItemSummary;
    private Button btnOverlayChangeCustomer, btnOverlayEditCustomer, btnCheckout;
    private ImageButton btnSearchToggle, btnClearSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_billing);

        dbHelper = DatabaseHelper.getInstance(this);

        // Bind layouts
        txtSelectedCustomerName = findViewById(R.id.txtOverlayCustomerName);
        btnChangeCustomer = findViewById(R.id.btnOverlayChangeCustomer);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerPaymentMethod = findViewById(R.id.spinnerPaymentMethod);
        spinnerPaymentTerm = findViewById(R.id.spinnerPaymentTerm);

        edtProductSearch = findViewById(R.id.edtProductSearch);
        edtDiscount = findViewById(R.id.edtDiscount);
        edtDirectDiscountPct = findViewById(R.id.edtDirectDiscountPct);
        lstProducts = findViewById(R.id.lstProducts);
        lstCartSummary = findViewById(R.id.lstCartSummary);
        lstCartSummary.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                CartItemModel cartItem = cartList.get(position);
                ProductModel product = getProductById(cartItem.productId);
                if (product != null) {
                    showProductConfigDialog(product, cartItem);
                } else {
                    Toast.makeText(BillingActivity.this, "Product details not found.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        txtCartItemsCount = findViewById(R.id.txtOverlayTotalItems);
        txtCartSalesSum = findViewById(R.id.txtOverlayTotalValue);
        txtSubtotal = findViewById(R.id.txtSubtotal);
        txtTax = findViewById(R.id.txtTax);
        txtNetTotal = findViewById(R.id.txtNetTotal);
        txtRoundingAdjustment = findViewById(R.id.txtRoundingAdjustment);
        layoutRoundingAdjustment = findViewById(R.id.layoutRoundingAdjustment);

        layoutCartOverlay = findViewById(R.id.layoutCartOverlay);
        btnViewCart = findViewById(R.id.btnViewCart);
        btnCancelCart = findViewById(R.id.btnCancelCart);
        btnConfirmCheckout = findViewById(R.id.btnConfirmCheckout);

        btnModeStandard = findViewById(R.id.btnModeStandard);
        btnModeVisual = findViewById(R.id.btnModeVisual);
        gridProducts = findViewById(R.id.gridProducts);

        // Bind redesigned layouts and controls
        layoutExpandedSearch = findViewById(R.id.layoutExpandedSearch);
        btnSearchToggle = findViewById(R.id.btnSearchToggle);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        btnCheckout = findViewById(R.id.btnCheckout);

        txtCartOverlayTitle = findViewById(R.id.txtCartOverlayTitle);
        layoutCartViewMode = findViewById(R.id.layoutCartViewMode);
        txtOverlayCustomerName = findViewById(R.id.txtOverlayCustomerName);
        txtOverlayCustomerBalance = findViewById(R.id.txtOverlayCustomerBalance);
        btnOverlayChangeCustomer = findViewById(R.id.btnOverlayChangeCustomer);
        btnOverlayEditCustomer = findViewById(R.id.btnOverlayEditCustomer);
        txtOverlayTotalItems = findViewById(R.id.txtOverlayTotalItems);
        txtOverlayTotalValue = findViewById(R.id.txtOverlayTotalValue);

        layoutCartCheckoutMode = findViewById(R.id.layoutCartCheckoutMode);
        txtCheckoutCustomerName = findViewById(R.id.txtCheckoutCustomerName);
        txtCheckoutItemSummary = findViewById(R.id.txtCheckoutItemSummary);
        layoutCheckoutDetails = findViewById(R.id.layoutCheckoutDetails);

        // Bind Search Expand/Collapse actions
        if (btnSearchToggle != null) {
            btnSearchToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (layoutExpandedSearch != null) {
                        if (layoutExpandedSearch.getVisibility() == View.GONE) {
                            layoutExpandedSearch.setVisibility(View.VISIBLE);
                            if (edtProductSearch != null) {
                                edtProductSearch.requestFocus();
                                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) {
                                    imm.showSoftInput(edtProductSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                                }
                            }
                        } else {
                            layoutExpandedSearch.setVisibility(View.GONE);
                            if (edtProductSearch != null) {
                                edtProductSearch.setText("");
                                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) {
                                    imm.hideSoftInputFromWindow(edtProductSearch.getWindowToken(), 0);
                                }
                            }
                        }
                    }
                }
            });
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (edtProductSearch != null) {
                        edtProductSearch.setText("");
                    }
                }
            });
        }

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

        if (btnChangeCustomer != null) {
            btnChangeCustomer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCustomerSelectionDialog();
                }
            });
        }

        if (btnOverlayChangeCustomer != null) {
            btnOverlayChangeCustomer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCustomerSelectionDialog();
                }
            });
        }

        if (btnOverlayEditCustomer != null) {
            btnOverlayEditCustomer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditCustomerDialog();
                }
            });
        }

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
                String query = s.toString();
                View catScroll = findViewById(R.id.categoryHorizontalScroll);
                View tabSelection = findViewById(R.id.layoutSegmentedTabs);
                if (query.trim().isEmpty()) {
                    if (catScroll != null) catScroll.setVisibility(View.VISIBLE);
                    if (tabSelection != null) tabSelection.setVisibility(View.VISIBLE);
                } else {
                    if (catScroll != null) catScroll.setVisibility(View.GONE);
                    if (tabSelection != null) tabSelection.setVisibility(View.GONE);
                }
                loadCatalogItems(query);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Setup dynamic categories first, then let it trigger catalog loading
        setupCategorySpinner();

        // 🚨 Immediately trigger Customer Selection Dialog or draft resume at startup
        if (hasDraftBill()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Resume Invoice?")
                .setMessage("An unfinished bill exists. Would you like to resume it?")
                .setCancelable(false)
                .setPositiveButton("Resume", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        resumeDraftBill();
                    }
                })
                .setNegativeButton("Clear and New", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        clearDraftBill();
                        showCustomerSelectionDialog();
                    }
                })
                .show();
        } else {
            showCustomerSelectionDialog();
        }

        // View Cart overlay
        btnViewCart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCartOverlay(false);
            }
        });

        if (btnCheckout != null) {
            btnCheckout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCartOverlay(true);
                }
            });
        }

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
                    java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
                    for (CartItemModel item : cartList) {
                        subtotal = subtotal.add(item.total);
                    }

                    if (edtDirectDiscountPct.hasFocus()) {
                        String pctStr = edtDirectDiscountPct.getText().toString().trim();
                        if (!pctStr.isEmpty()) {
                            java.math.BigDecimal pct = CurrencyUtils.toBigDecimal(pctStr);
                            java.math.BigDecimal discountVal = CurrencyUtils.calculatePercentage(subtotal, pct);
                            edtDiscount.setText(String.format(Locale.getDefault(), "%.2f", discountVal));
                        } else {
                            edtDiscount.setText("");
                        }
                    } else if (edtDiscount.hasFocus()) {
                        String amtStr = edtDiscount.getText().toString().trim();
                        if (!amtStr.isEmpty() && subtotal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            java.math.BigDecimal amt = CurrencyUtils.toBigDecimal(amtStr);
                            java.math.BigDecimal pct = amt.multiply(java.math.BigDecimal.valueOf(100)).divide(subtotal, 1, java.math.RoundingMode.HALF_UP);
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

    private void showCartOverlay(boolean checkoutMode) {
        if (cartList.isEmpty()) {
            Toast.makeText(this, "Your shopping cart is empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        layoutCartOverlay.setVisibility(View.VISIBLE);
        recalculateCart();

        // Bind Customer info
        String custName = selectedCustomer != null ? selectedCustomer.name : "Select Customer...";
        java.math.BigDecimal balance = selectedCustomer != null ? selectedCustomer.outstanding : java.math.BigDecimal.ZERO;
        String balText = "Outstanding: LKR " + String.format(Locale.getDefault(), "%,.2f", balance.doubleValue());

        if (txtOverlayCustomerName != null) txtOverlayCustomerName.setText(custName);
        if (txtOverlayCustomerBalance != null) txtOverlayCustomerBalance.setText(balText);
        if (txtCheckoutCustomerName != null) txtCheckoutCustomerName.setText(custName);

        // Cart totals
        java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
        int totalItemsCount = 0;
        for (CartItemModel item : cartList) {
            subtotal = subtotal.add(item.total);
            totalItemsCount += item.quantity;
        }
        
        String discountStr = edtDiscount.getText().toString().trim();
        java.math.BigDecimal discount = CurrencyUtils.toBigDecimal(discountStr);
        java.math.BigDecimal netTotal = subtotal.subtract(discount);
        if (netTotal.compareTo(java.math.BigDecimal.ZERO) < 0) netTotal = java.math.BigDecimal.ZERO;

        if (txtOverlayTotalItems != null) txtOverlayTotalItems.setText(String.valueOf(totalItemsCount));
        if (txtOverlayTotalValue != null) txtOverlayTotalValue.setText(CurrencyUtils.formatLKR(netTotal));
        if (txtCheckoutItemSummary != null) txtCheckoutItemSummary.setText(totalItemsCount + " Items | Total: " + CurrencyUtils.formatLKR(netTotal));

        if (checkoutMode) {
            if (txtCartOverlayTitle != null) txtCartOverlayTitle.setText("BILL SUMMARY & CHECKOUT");
            if (layoutCartViewMode != null) layoutCartViewMode.setVisibility(View.GONE);
            if (layoutCartCheckoutMode != null) layoutCartCheckoutMode.setVisibility(View.VISIBLE);
            if (layoutCheckoutDetails != null) layoutCheckoutDetails.setVisibility(View.VISIBLE);
            if (btnConfirmCheckout != null) {
                btnConfirmCheckout.setText("Confirm Bill");
                btnConfirmCheckout.setEnabled(true);
                btnConfirmCheckout.setVisibility(View.VISIBLE);
            }
        } else {
            if (txtCartOverlayTitle != null) txtCartOverlayTitle.setText("CART REVIEW");
            if (layoutCartViewMode != null) layoutCartViewMode.setVisibility(View.VISIBLE);
            if (layoutCartCheckoutMode != null) layoutCartCheckoutMode.setVisibility(View.GONE);
            if (layoutCheckoutDetails != null) layoutCheckoutDetails.setVisibility(View.GONE);
            if (btnConfirmCheckout != null) {
                btnConfirmCheckout.setVisibility(View.GONE);
            }
        }
        
        setupCartSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupCategorySpinner();
        loadCatalogItems(edtProductSearch != null ? edtProductSearch.getText().toString() : "");
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
            c.whatsapp = cursor.getString(cursor.getColumnIndexOrThrow("whatsapp"));
            c.address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
            c.territory = cursor.getString(cursor.getColumnIndexOrThrow("territory"));
            c.outstanding = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("outstanding")));
            c.mcaId = cursor.getInt(cursor.getColumnIndexOrThrow("mca_id"));
            c.mcaName = cursor.getString(cursor.getColumnIndexOrThrow("mca_name"));
            c.email = cursor.getString(cursor.getColumnIndexOrThrow("email"));
            c.creditLimit = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("credit_limit")));
            c.customerType = cursor.getString(cursor.getColumnIndexOrThrow("customer_type"));
            c.notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"));
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

        String selectedCategory = selectedCategoryName;
        String query = "SELECT * FROM products WHERE status = 'active'";
        List<String> argsList = new ArrayList<>();

        if (filter != null && !filter.trim().isEmpty()) {
            String likeFilter = "%" + filter + "%";
            if (!"All Categories".equalsIgnoreCase(selectedCategory)) {
                query = "SELECT * FROM products WHERE (name LIKE ? OR sku LIKE ? OR sample_code LIKE ? OR variations_json LIKE ? OR brand LIKE ?) AND category_name = ? AND status = 'active'";
                argsList.add(likeFilter);
                argsList.add(likeFilter);
                argsList.add(likeFilter);
                argsList.add(likeFilter);
                argsList.add(likeFilter);
                argsList.add(selectedCategory);
            } else {
                query = "SELECT * FROM products WHERE (name LIKE ? OR sku LIKE ? OR sample_code LIKE ? OR variations_json LIKE ? OR brand LIKE ?) AND status = 'active'";
                argsList.add(likeFilter);
                argsList.add(likeFilter);
                argsList.add(likeFilter);
                argsList.add(likeFilter);
                argsList.add(likeFilter);
            }
        } else {
            if (!"All Categories".equalsIgnoreCase(selectedCategory)) {
                query = "SELECT * FROM products WHERE category_name = ? AND status = 'active'";
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
            p.price = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("price")));
            p.wholesalePrice = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("wholesale_price")));
            p.qtyOnHand = cursor.getInt(cursor.getColumnIndexOrThrow("quantity_on_hand"));
            p.qtyReserved = cursor.getInt(cursor.getColumnIndexOrThrow("quantity_reserved"));
            p.localImagePath = cursor.getString(cursor.getColumnIndexOrThrow("local_image_path"));
            p.sku = cursor.getString(cursor.getColumnIndexOrThrow("sku"));
            p.sampleCode = cursor.getString(cursor.getColumnIndexOrThrow("sample_code"));
            p.variationsJson = cursor.getString(cursor.getColumnIndexOrThrow("variations_json"));
            p.brand = cursor.getString(cursor.getColumnIndexOrThrow("brand"));
            p.description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
            productList.add(p);
        }
        cursor.close();

        // Prioritize exact matches at the top when searching
        if (filter != null && !filter.trim().isEmpty()) {
            final String searchLower = filter.toLowerCase().trim();
            java.util.Collections.sort(productList, new java.util.Comparator<ProductModel>() {
                @Override
                public int compare(ProductModel p1, ProductModel p2) {
                    int score1 = getScore(p1, searchLower);
                    int score2 = getScore(p2, searchLower);
                    return Integer.compare(score1, score2);
                }

                private int getScore(ProductModel p, String query) {
                    String name = p.name != null ? p.name.toLowerCase() : "";
                    String sku = p.sku != null ? p.sku.toLowerCase() : "";
                    String sampleCode = p.sampleCode != null ? p.sampleCode.toLowerCase() : "";

                    // 1. Exact match on SKU or Sample Code
                    if (sku.equals(query) || sampleCode.equals(query)) {
                        return 1;
                    }
                    // 2. Exact match on Name
                    if (name.equals(query)) {
                        return 2;
                    }
                    // 3. SKU or Sample Code starts with query
                    if (sku.startsWith(query) || sampleCode.startsWith(query)) {
                        return 3;
                    }
                    // 4. Name starts with query
                    if (name.startsWith(query)) {
                        return 4;
                    }
                    // 5. SKU or Sample Code contains query
                    if (sku.contains(query) || sampleCode.contains(query)) {
                        return 5;
                    }
                    // 6. Name contains query
                    if (name.contains(query)) {
                        return 6;
                    }
                    return 7;
                }
            });
        }

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
        java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
        java.math.BigDecimal subtotalUnrounded = java.math.BigDecimal.ZERO;
        int totalItemsCount = 0;

        for (CartItemModel item : cartList) {
            java.math.BigDecimal price = item.customPrice.compareTo(java.math.BigDecimal.ZERO) > 0 ? item.customPrice : item.price;
            item.activePrice = price;

            java.math.BigDecimal itemSubtotal = price.multiply(java.math.BigDecimal.valueOf(item.quantity));
            java.math.BigDecimal unroundedDisc = java.math.BigDecimal.ZERO;
            java.math.BigDecimal roundedDisc = java.math.BigDecimal.ZERO;

            if (item.isPercentDiscountActive && item.discountPercent.compareTo(java.math.BigDecimal.ZERO) > 0) {
                unroundedDisc = itemSubtotal.multiply(item.discountPercent).divide(java.math.BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
                roundedDisc = CurrencyUtils.calculatePercentage(itemSubtotal, item.discountPercent);
            } else if (!item.isPercentDiscountActive && item.discountAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
                unroundedDisc = item.discountAmount;
                roundedDisc = item.discountAmount;
            }

            item.discountVal = roundedDisc;
            item.total = itemSubtotal.subtract(roundedDisc);
            if (item.total.compareTo(java.math.BigDecimal.ZERO) < 0) {
                item.total = java.math.BigDecimal.ZERO;
            }

            java.math.BigDecimal unroundedTotal = itemSubtotal.subtract(unroundedDisc);
            if (unroundedTotal.compareTo(java.math.BigDecimal.ZERO) < 0) {
                unroundedTotal = java.math.BigDecimal.ZERO;
            }

            subtotal = subtotal.add(item.total);
            subtotalUnrounded = subtotalUnrounded.add(unroundedTotal);
            totalItemsCount += item.quantity;
        }

        String discountStr = edtDiscount.getText().toString().trim();
        java.math.BigDecimal discount = CurrencyUtils.toBigDecimal(discountStr);
        java.math.BigDecimal unroundedDiscount = discount;

        String directPctStr = edtDirectDiscountPct.getText().toString().trim();
        if (!directPctStr.isEmpty() && edtDirectDiscountPct.hasFocus()) {
            java.math.BigDecimal directPct = CurrencyUtils.toBigDecimal(directPctStr);
            unroundedDiscount = subtotalUnrounded.multiply(directPct).divide(java.math.BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
            discount = CurrencyUtils.calculatePercentage(subtotal, directPct);
        }

        java.math.BigDecimal netTotal = subtotal.subtract(discount);
        if (netTotal.compareTo(java.math.BigDecimal.ZERO) < 0) netTotal = java.math.BigDecimal.ZERO;

        java.math.BigDecimal netTotalUnrounded = subtotalUnrounded.subtract(unroundedDiscount);
        if (netTotalUnrounded.compareTo(java.math.BigDecimal.ZERO) < 0) netTotalUnrounded = java.math.BigDecimal.ZERO;

        java.math.BigDecimal roundingAdj = netTotal.subtract(netTotalUnrounded).setScale(2, java.math.RoundingMode.HALF_UP);

        java.math.BigDecimal taxBreakdown = java.math.BigDecimal.ZERO;

        txtCartItemsCount.setText(totalItemsCount + " Items in Cart");
        txtCartSalesSum.setText(CurrencyUtils.formatLKR(netTotal));

        txtSubtotal.setText(CurrencyUtils.formatLKR(subtotal));
        txtTax.setText(CurrencyUtils.formatLKR(taxBreakdown));
        txtNetTotal.setText(CurrencyUtils.formatLKR(netTotal));

        if (layoutRoundingAdjustment != null && txtRoundingAdjustment != null) {
            if (roundingAdj.compareTo(java.math.BigDecimal.ZERO) != 0) {
                txtRoundingAdjustment.setText(CurrencyUtils.formatLKR(roundingAdj));
                layoutRoundingAdjustment.setVisibility(android.view.View.VISIBLE);
            } else {
                layoutRoundingAdjustment.setVisibility(android.view.View.GONE);
            }
        }

        saveDraftBill();
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
            Toast.makeText(this, "Please select a customer before confirming the bill.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cartList.isEmpty()) {
            Toast.makeText(this, "Your shopping cart is empty.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (btnConfirmCheckout != null) {
            btnConfirmCheckout.setEnabled(false);
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
                    freeItem.price = java.math.BigDecimal.ZERO;
                    freeItem.wholesalePrice = java.math.BigDecimal.ZERO;
                    freeItem.activePrice = java.math.BigDecimal.ZERO;
                    freeItem.quantity = freeQty;
                    freeItem.customPrice = java.math.BigDecimal.ZERO;
                    freeItem.discountPercent = java.math.BigDecimal.ZERO;
                    freeItem.discountAmount = java.math.BigDecimal.ZERO;
                    freeItem.discountVal = java.math.BigDecimal.ZERO;
                    freeItem.total = java.math.BigDecimal.ZERO;
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
                        java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
                        for (CartItemModel item : cartList) {
                            subtotal = subtotal.add(item.total);
                        }
                        java.math.BigDecimal discountAmt = subtotal.multiply(java.math.BigDecimal.valueOf(billDiscount.rewardVal)).divide(java.math.BigDecimal.valueOf(100.0), 2, java.math.RoundingMode.HALF_UP);
                        edtDiscount.setText(String.format(Locale.getDefault(), "%.2f", discountAmt.doubleValue()));
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
        showGlobalLoadingDialog("Recording invoice in local database...");
        CustomerModel customer = selectedCustomer;
        String payment = "Term";

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int dbRetries = 5;
        int dbAttempt = 0;
        
        while (dbAttempt < dbRetries) {
            dbAttempt++;
            try {
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

                    java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
                    for (CartItemModel item : cartList) {
                        subtotal = subtotal.add(item.total);
                    }

                    String discountStr = edtDiscount.getText().toString().trim();
                    java.math.BigDecimal discount = CurrencyUtils.toBigDecimal(discountStr);

                    java.math.BigDecimal netTotal = subtotal.subtract(discount);
                    if (netTotal.compareTo(java.math.BigDecimal.ZERO) < 0) netTotal = java.math.BigDecimal.ZERO;
                    java.math.BigDecimal tax = java.math.BigDecimal.ZERO;

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

                    String uuidString = java.util.UUID.randomUUID().toString();
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
                    cvHeader.put("subtotal", subtotal.doubleValue());
                    cvHeader.put("discount", discount.doubleValue());
                    cvHeader.put("tax", tax.doubleValue());
                    cvHeader.put("grand_total", netTotal.doubleValue());
                    cvHeader.put("payment_method", payment);
                    cvHeader.put("latitude", capturedLat);
                    cvHeader.put("longitude", capturedLng);
                    cvHeader.put("is_synced", 0);
                    cvHeader.put("uuid", uuidString);
                    cvHeader.put("sync_status", 1); // 1 = Pending Sync
                    cvHeader.put("sync_attempts", 0);

                    long localInvId = db.insert("invoices", null, cvHeader);

                    for (CartItemModel item : cartList) {
                        ContentValues cvItem = new ContentValues();
                        cvItem.put("invoice_id", localInvId);
                        cvItem.put("product_id", item.productId);
                        cvItem.put("product_name", item.name);
                        cvItem.put("quantity", item.quantity);
                        cvItem.put("unit_price", item.activePrice.doubleValue());
                        cvItem.put("discount_val", item.discountVal.doubleValue());
                        cvItem.put("total", item.total.doubleValue());

                        db.insert("invoice_items", null, cvItem);
                        db.execSQL("UPDATE products SET quantity_reserved = quantity_reserved + " + item.quantity + " WHERE id = " + item.productId);
                    }

                    // Create initial sync logs entry
                    try {
                        ContentValues cvLog = new ContentValues();
                        cvLog.put("bill_id", localInvId);
                        cvLog.put("uuid", uuidString);
                        cvLog.put("created_time", dateString);
                        cvLog.put("upload_started", "");
                        cvLog.put("upload_completed", "");
                        cvLog.put("erp_response", "");
                        cvLog.put("failure_reason", "");
                        cvLog.put("retry_count", 0);
                        db.insert("sync_logs", null, cvLog);
                    } catch (Exception ex) {
                        android.util.Log.e("BillingActivity", "Error creating initial sync log: " + ex.getMessage());
                    }

                    db.setTransactionSuccessful();
                    clearDraftBill();

                    // Auto sync on checkout removed in favor of manual sync.

                    showShareBillDialog(invoiceNum, customer.name, customer.phone, netTotal);
                    break; // Success, break retry loop
                } finally {
                    db.endTransaction();
                }
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().contains("locked") || e.getMessage().contains("BUSY") || e.getMessage().contains("code 5"))) {
                    android.util.Log.w("BillingActivity", "Database locked during checkout. Attempt " + dbAttempt + " of " + dbRetries + ". Retrying...");
                    if (dbAttempt >= dbRetries) {
                        dismissGlobalLoadingDialog();
                        if (btnConfirmCheckout != null) {
                            btnConfirmCheckout.setEnabled(true);
                        }
                        Toast.makeText(this, "Checkout failed (database locked): " + e.getMessage(), Toast.LENGTH_LONG).show();
                        break;
                    }
                    try {
                        Thread.sleep(200 * dbAttempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        dismissGlobalLoadingDialog();
                        if (btnConfirmCheckout != null) {
                            btnConfirmCheckout.setEnabled(true);
                        }
                        Toast.makeText(this, "Checkout interrupted: " + ie.getMessage(), Toast.LENGTH_SHORT).show();
                        break;
                    }
                } else {
                    dismissGlobalLoadingDialog();
                    if (btnConfirmCheckout != null) {
                        btnConfirmCheckout.setEnabled(true);
                    }
                    Toast.makeText(this, "Checkout failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    break;
                }
            }
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
                        if (item.customPrice.compareTo(java.math.BigDecimal.ZERO) == 0 && item.total.compareTo(java.math.BigDecimal.ZERO) == 0) {
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
            java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
            for (CartItemModel item : cartList) {
                subtotal = subtotal.add(item.total);
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

                if (subtotal.doubleValue() >= minThresh) {
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

    private void showShareBillDialog(final String invoiceNum, final String customerName, final String customerPhone, final java.math.BigDecimal grandTotal) {
        dismissGlobalLoadingDialog();
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

        // Customer & Total Card
        LinearLayout cardLayout = new LinearLayout(this);
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setBackgroundColor(android.graphics.Color.parseColor("#1E293B")); // Slate 800
        cardLayout.setPadding(32, 24, 32, 24);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 24);
        cardLayout.setLayoutParams(cardParams);

        TextView txtCardHeader = new TextView(this);
        txtCardHeader.setText("TRANSACTION SUMMARY");
        txtCardHeader.setTextColor(android.graphics.Color.parseColor("#38BDF8")); // Sky 400
        txtCardHeader.setTextSize(12);
        txtCardHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        txtCardHeader.setPadding(0, 0, 0, 12);
        cardLayout.addView(txtCardHeader);

        TextView txtCustomer = new TextView(this);
        txtCustomer.setText("Customer: " + customerName);
        txtCustomer.setTextColor(android.graphics.Color.WHITE);
        txtCustomer.setTextSize(15);
        txtCustomer.setTypeface(null, android.graphics.Typeface.BOLD);
        txtCustomer.setPadding(0, 0, 0, 4);
        cardLayout.addView(txtCustomer);

        TextView txtPhone = new TextView(this);
        txtPhone.setText("Phone: " + (customerPhone != null && !customerPhone.isEmpty() ? customerPhone : "N/A"));
        txtPhone.setTextColor(android.graphics.Color.parseColor("#94A3B8")); // Slate 400
        txtPhone.setTextSize(13);
        txtPhone.setPadding(0, 0, 0, 16);
        cardLayout.addView(txtPhone);

        View divider = new View(this);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
        divParams.setMargins(0, 0, 0, 16);
        divider.setLayoutParams(divParams);
        divider.setBackgroundColor(android.graphics.Color.parseColor("#334155")); // Slate 700
        cardLayout.addView(divider);

        TextView txtTotalTitle = new TextView(this);
        txtTotalTitle.setText("BILL TOTAL");
        txtTotalTitle.setTextColor(android.graphics.Color.parseColor("#94A3B8")); // Slate 400
        txtTotalTitle.setTextSize(11);
        txtTotalTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        txtTotalTitle.setPadding(0, 0, 0, 4);
        cardLayout.addView(txtTotalTitle);

        TextView txtTotalVal = new TextView(this);
        txtTotalVal.setText(CurrencyUtils.formatLKR(grandTotal));
        txtTotalVal.setTextColor(android.graphics.Color.parseColor("#22C55E")); // Green 500
        txtTotalVal.setTextSize(22);
        txtTotalVal.setTypeface(null, android.graphics.Typeface.BOLD);
        cardLayout.addView(txtTotalVal);

        container.addView(cardLayout);

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

    private boolean hasDraftBill() {
        android.content.SharedPreferences prefs = getSharedPreferences("billing_draft_prefs", MODE_PRIVATE);
        return prefs.contains("draft_json");
    }

    private void clearDraftBill() {
        android.content.SharedPreferences prefs = getSharedPreferences("billing_draft_prefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    private void saveDraftBill() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("billing_draft_prefs", MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();

            if (selectedCustomer == null && cartList.isEmpty()) {
                editor.clear().apply();
                return;
            }

            org.json.JSONObject draft = new org.json.JSONObject();
            if (selectedCustomer != null) {
                draft.put("customer_id", selectedCustomer.id);
            }

            draft.put("discount_val", edtDiscount.getText().toString());
            draft.put("discount_pct", edtDirectDiscountPct.getText().toString());
            if (spinnerPaymentTerm != null) {
                draft.put("payment_term_pos", spinnerPaymentTerm.getSelectedItemPosition());
            }

            org.json.JSONArray itemsArr = new org.json.JSONArray();
            for (CartItemModel item : cartList) {
                org.json.JSONObject itemObj = new org.json.JSONObject();
                itemObj.put("product_id", item.productId);
                itemObj.put("quantity", item.quantity);
                itemObj.put("custom_price", item.customPrice.doubleValue());
                itemObj.put("discount_percent", item.discountPercent.doubleValue());
                itemObj.put("discount_amount", item.discountAmount.doubleValue());
                itemObj.put("discount_val", item.discountVal.doubleValue());
                itemObj.put("is_percent_discount_active", item.isPercentDiscountActive);
                itemObj.put("selected_variation", item.selectedVariation != null ? item.selectedVariation : "");
                itemObj.put("notes", item.notes != null ? item.notes : "");
                itemsArr.put(itemObj);
            }
            draft.put("items", itemsArr);

            editor.putString("draft_json", draft.toString());
            editor.apply();
        } catch (Exception e) {
            android.util.Log.e("BillingActivity", "Error saving draft: " + e.getMessage());
        }
    }

    private void resumeDraftBill() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("billing_draft_prefs", MODE_PRIVATE);
            String jsonStr = prefs.getString("draft_json", null);
            if (jsonStr == null) return;

            org.json.JSONObject draft = new org.json.JSONObject(jsonStr);

            // 1. Restore customer
            if (draft.has("customer_id")) {
                int custId = draft.getInt("customer_id");
                for (CustomerModel c : customerList) {
                    if (c.id == custId) {
                        selectedCustomer = c;
                        break;
                    }
                }
                if (selectedCustomer != null) {
                    String displayName = selectedCustomer.name;
                    String balText = "Outstanding: LKR " + String.format(Locale.getDefault(), "%,.2f", selectedCustomer.outstanding.doubleValue());
                    if (txtOverlayCustomerName != null) txtOverlayCustomerName.setText(displayName);
                    if (txtOverlayCustomerBalance != null) txtOverlayCustomerBalance.setText(balText);
                    if (txtCheckoutCustomerName != null) txtCheckoutCustomerName.setText(displayName);
                    if (txtSelectedCustomerName != null) {
                        String displayNameFull = selectedCustomer.name + "\nOutstanding: LKR " + String.format(Locale.getDefault(), "%,.2f", selectedCustomer.outstanding.doubleValue());
                        txtSelectedCustomerName.setText(displayNameFull);
                    }
                }
            }

            // 2. Restore discounts
            String discVal = draft.optString("discount_val", "");
            String discPct = draft.optString("discount_pct", "");
            edtDiscount.setText(discVal);
            edtDirectDiscountPct.setText(discPct);

            // 3. Restore payment term
            int termPos = draft.optInt("payment_term_pos", 0);
            if (spinnerPaymentTerm != null && termPos >= 0) {
                spinnerPaymentTerm.setSelection(termPos);
            }

            // 4. Restore items
            cartList.clear();
            org.json.JSONArray itemsArr = draft.getJSONArray("items");
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            for (int i = 0; i < itemsArr.length(); i++) {
                org.json.JSONObject itemObj = itemsArr.getJSONObject(i);
                int prodId = itemObj.getInt("product_id");
                int qty = itemObj.getInt("quantity");
                java.math.BigDecimal customPrice = CurrencyUtils.toBigDecimal(itemObj.optDouble("custom_price", 0.0));
                java.math.BigDecimal discPercent = CurrencyUtils.toBigDecimal(itemObj.optDouble("discount_percent", 0.0));
                java.math.BigDecimal discAmount = CurrencyUtils.toBigDecimal(itemObj.optDouble("discount_amount", 0.0));
                java.math.BigDecimal discValItem = CurrencyUtils.toBigDecimal(itemObj.optDouble("discount_val", 0.0));
                boolean isPercentActive = itemObj.optBoolean("is_percent_discount_active", false);
                String selVar = itemObj.optString("selected_variation", "");
                String notes = itemObj.optString("notes", "");

                Cursor cursor = db.rawQuery("SELECT * FROM products WHERE id = ?", new String[]{String.valueOf(prodId)});
                if (cursor.moveToFirst()) {
                    CartItemModel item = new CartItemModel();
                    item.productId = prodId;
                    item.quantity = qty;
                    item.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    item.price = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("price")));
                    item.wholesalePrice = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("wholesale_price")));
                    item.customPrice = customPrice;
                    item.discountPercent = discPercent;
                    item.discountAmount = discAmount;
                    item.discountVal = discValItem;
                    item.isPercentDiscountActive = isPercentActive;
                    item.selectedVariation = selVar;
                    item.notes = notes;

                    java.math.BigDecimal basePrice = item.wholesalePrice.compareTo(java.math.BigDecimal.ZERO) > 0 ? item.wholesalePrice : item.price;
                    java.math.BigDecimal unitPrice = item.customPrice.compareTo(java.math.BigDecimal.ZERO) > 0 ? item.customPrice : basePrice;
                    item.activePrice = unitPrice;

                    java.math.BigDecimal itemSubtotal = unitPrice.multiply(java.math.BigDecimal.valueOf(item.quantity));
                    java.math.BigDecimal calculatedDisc = java.math.BigDecimal.ZERO;
                    if (item.isPercentDiscountActive && item.discountPercent.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        calculatedDisc = CurrencyUtils.calculatePercentage(itemSubtotal, item.discountPercent);
                    } else if (!item.isPercentDiscountActive && item.discountAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        calculatedDisc = item.discountAmount;
                    }
                    item.discountVal = CurrencyUtils.round(calculatedDisc);
                    item.total = itemSubtotal.subtract(item.discountVal);
                    if (item.total.compareTo(java.math.BigDecimal.ZERO) < 0) item.total = java.math.BigDecimal.ZERO;

                    cartList.add(item);
                }
                cursor.close();
            }

            setupCartSummary();
        } catch (Exception e) {
            android.util.Log.e("BillingActivity", "Error resuming draft: " + e.getMessage());
        }
    }

    private static class ZoomImageView extends androidx.appcompat.widget.AppCompatImageView {
        private float mScaleFactor = 1.0f;
        private android.view.ScaleGestureDetector mScaleDetector;

        public ZoomImageView(android.content.Context context) {
            super(context);
            mScaleDetector = new android.view.ScaleGestureDetector(context, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(android.view.ScaleGestureDetector detector) {
                    mScaleFactor *= detector.getScaleFactor();
                    mScaleFactor = Math.max(1.0f, Math.min(mScaleFactor, 5.0f));
                    setScaleX(mScaleFactor);
                    setScaleY(mScaleFactor);
                    return true;
                }
            });
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent ev) {
            mScaleDetector.onTouchEvent(ev);
            if (mScaleFactor > 1.0f) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            return true;
        }
    }

    private void showProductQuickViewDialog(final ProductModel p) {
        final androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setBackgroundColor(android.graphics.Color.parseColor("#0F172A")); // Slate 900
        
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);
        
        // Title
        android.widget.TextView txtTitle = new android.widget.TextView(this);
        txtTitle.setText(p.name);
        txtTitle.setTextColor(android.graphics.Color.WHITE);
        txtTitle.setTextSize(22);
        txtTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        txtTitle.setPadding(0, 0, 0, 8);
        root.addView(txtTitle);

        // SKU and Category Subheader
        android.widget.TextView txtSub = new android.widget.TextView(this);
        String skuText = (p.sku != null && !p.sku.isEmpty()) ? p.sku : "N/A";
        String sampleCodeText = (p.sampleCode != null && !p.sampleCode.isEmpty()) ? p.sampleCode : "N/A";
        txtSub.setText("Category: " + p.category + "  |  SKU: " + skuText + "  |  Sample: " + sampleCodeText);
        txtSub.setTextColor(android.graphics.Color.parseColor("#94A3B8")); // Slate 400
        txtSub.setTextSize(13);
        txtSub.setPadding(0, 0, 0, 24);
        root.addView(txtSub);

        // Image Container
        android.widget.FrameLayout imgContainer = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams imgContainerParams = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int) (260 * getResources().getDisplayMetrics().density));
        imgContainerParams.setMargins(0, 0, 0, 24);
        imgContainer.setLayoutParams(imgContainerParams);
        imgContainer.setBackgroundColor(android.graphics.Color.parseColor("#1E293B")); // Slate 800 background
        
        final ZoomImageView zoomImage = new ZoomImageView(this);
        android.widget.FrameLayout.LayoutParams imgParams = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        zoomImage.setLayoutParams(imgParams);
        zoomImage.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        
        if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
            java.io.File file = new java.io.File(p.localImagePath);
            if (file.exists()) {
                zoomImage.setImageBitmap(android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath()));
            } else {
                zoomImage.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            zoomImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        imgContainer.addView(zoomImage);
        root.addView(imgContainer);

        // Specifications List
        android.widget.LinearLayout specsLayout = new android.widget.LinearLayout(this);
        specsLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        specsLayout.setBackgroundColor(android.graphics.Color.parseColor("#1E293B")); // Slate 800
        specsLayout.setPadding(32, 24, 32, 24);
        android.widget.LinearLayout.LayoutParams specsParams = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        specsParams.setMargins(0, 0, 0, 24);
        specsLayout.setLayoutParams(specsParams);
        
        if (p.brand != null && !p.brand.isEmpty()) {
            addQuickViewSpecRow(specsLayout, "Brand", p.brand);
        }
        if (p.description != null && !p.description.isEmpty()) {
            addQuickViewSpecRow(specsLayout, "Description", p.description);
        }
        addQuickViewSpecRow(specsLayout, "Retail Price", "LKR " + String.format(Locale.getDefault(), "%,.2f", p.price.doubleValue()));
        addQuickViewSpecRow(specsLayout, "Wholesale Price", p.wholesalePrice.compareTo(java.math.BigDecimal.ZERO) > 0 ? "LKR " + String.format(Locale.getDefault(), "%,.2f", p.wholesalePrice.doubleValue()) : "N/A");
        addQuickViewSpecRow(specsLayout, "Physical Stock", String.valueOf(p.qtyOnHand));
        addQuickViewSpecRow(specsLayout, "Reserved Stock", String.valueOf(p.qtyReserved));
        int available = p.qtyOnHand - p.qtyReserved;
        addQuickViewSpecRow(specsLayout, "Available Stock", String.valueOf(available));
        
        if (p.variationsJson != null && !p.variationsJson.isEmpty() && !p.variationsJson.equals("null")) {
            try {
                org.json.JSONArray vars = new org.json.JSONArray(p.variationsJson);
                java.lang.StringBuilder varsBuilder = new java.lang.StringBuilder();
                for (int i = 0; i < vars.length(); i++) {
                    org.json.JSONObject vObj = vars.getJSONObject(i);
                    java.lang.String optionName = vObj.optString("option_name", "");
                    int qty = vObj.optInt("quantity_on_hand", 0);
                    if (i > 0) varsBuilder.append("\n");
                    varsBuilder.append("• ").append(optionName).append(" (Stock: ").append(qty).append(")");
                }
                if (varsBuilder.length() > 0) {
                    addQuickViewSpecRow(specsLayout, "Variations", varsBuilder.toString());
                }
            } catch (Exception e) {
                android.util.Log.e("BillingActivity", "Error parsing variations spec: " + e.getMessage());
            }
        }
        
        root.addView(specsLayout);

        // Buttons
        android.widget.Button btnClose = new android.widget.Button(this);
        btnClose.setText("Close Quick View");
        btnClose.setTextColor(android.graphics.Color.WHITE);
        btnClose.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#3B82F6")));
        
        root.addView(btnClose);
        scrollView.addView(root);
        builder.setView(scrollView);
        
        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    private void addQuickViewSpecRow(android.widget.LinearLayout container, java.lang.String label, java.lang.String value) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);
        
        android.widget.TextView txtLabel = new android.widget.TextView(this);
        txtLabel.setText(label);
        txtLabel.setTextColor(android.graphics.Color.parseColor("#94A3B8")); // Slate 400
        txtLabel.setTextSize(13);
        txtLabel.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        
        android.widget.TextView txtValue = new android.widget.TextView(this);
        txtValue.setText(value);
        txtValue.setTextColor(android.graphics.Color.WHITE);
        txtValue.setTextSize(13);
        txtValue.setTypeface(null, android.graphics.Typeface.BOLD);
        txtValue.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));
        
        row.addView(txtLabel);
        row.addView(txtValue);
        container.addView(row);
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
        String whatsapp;
        String address;
        String territory;
        java.math.BigDecimal outstanding = java.math.BigDecimal.ZERO;
        int mcaId;
        String mcaName;
        String email;
        java.math.BigDecimal creditLimit = java.math.BigDecimal.ZERO;
        String customerType;
        String notes;
    }

    private static class ProductModel {
        int id, qtyOnHand, qtyReserved;
        String name, category, localImagePath;
        java.math.BigDecimal price = java.math.BigDecimal.ZERO;
        java.math.BigDecimal wholesalePrice = java.math.BigDecimal.ZERO;
        String sku, sampleCode, variationsJson;
        String brand, description;
    }

    private static class CartItemModel {
        int productId, quantity;
        String name;
        java.math.BigDecimal price = java.math.BigDecimal.ZERO;
        java.math.BigDecimal wholesalePrice = java.math.BigDecimal.ZERO;
        java.math.BigDecimal activePrice = java.math.BigDecimal.ZERO;
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        java.math.BigDecimal customPrice = java.math.BigDecimal.ZERO;
        java.math.BigDecimal discountPercent = java.math.BigDecimal.ZERO;
        java.math.BigDecimal discountAmount = java.math.BigDecimal.ZERO;
        java.math.BigDecimal discountVal = java.math.BigDecimal.ZERO;
        String selectedVariation = "";
        String notes = "";
        boolean isPercentDiscountActive = false;
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
            TextView lblSampleCode = convertView.findViewById(R.id.lblSampleCode);
            TextView lblPrice = convertView.findViewById(R.id.lblPrice);
            TextView lblStock = convertView.findViewById(R.id.lblStock);

            lblProductName.setText(p.name);
            lblCategory.setText(p.category);
            
            if (p.sampleCode != null && !p.sampleCode.trim().isEmpty()) {
                lblSampleCode.setText("Sample Code: " + p.sampleCode);
                lblSampleCode.setVisibility(View.VISIBLE);
            } else {
                lblSampleCode.setVisibility(View.GONE);
            }

            final java.math.BigDecimal currentPrice = p.price;
            lblPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", currentPrice.doubleValue()));

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

            convertView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showProductQuickViewDialog(p);
                    return true;
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

            if (item.discountVal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                text2.setText(String.format(Locale.getDefault(), "Qty: %d  x  LKR %.2f (Less LKR %.2f Disc)  =  LKR %.2f", item.quantity, item.activePrice.doubleValue(), item.discountVal.doubleValue(), item.total.doubleValue()));
            } else {
                text2.setText(String.format(Locale.getDefault(), "Qty: %d  x  LKR %.2f   =   LKR %.2f", item.quantity, item.activePrice.doubleValue(), item.total.doubleValue()));
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
        showProductConfigDialog(p, null);
    }

    private void showProductConfigDialog(final ProductModel p, final CartItemModel existingItem) {
        final androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        // Root container
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);
        layout.setBackgroundColor(android.graphics.Color.parseColor("#1E293B"));

        // Header containing image and title details
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        headerLayout.setPadding(0, 0, 0, 16);

        // Product image
        ImageView imgProduct = new ImageView(this);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                (int)(64 * getResources().getDisplayMetrics().density), 
                (int)(64 * getResources().getDisplayMetrics().density));
        imgProduct.setLayoutParams(imgParams);
        imgProduct.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
            java.io.File file = new java.io.File(p.localImagePath);
            if (file.exists()) {
                imgProduct.setImageBitmap(android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath()));
            } else {
                imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        headerLayout.addView(imgProduct);

        // Title and price text container
        LinearLayout titleTextLayout = new LinearLayout(this);
        titleTextLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleTextParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleTextParams.setMargins((int)(12 * getResources().getDisplayMetrics().density), 0, 0, 0);
        titleTextLayout.setLayoutParams(titleTextParams);

        // Title
        TextView txtTitle = new TextView(this);
        txtTitle.setText(p.name);
        txtTitle.setTextColor(android.graphics.Color.WHITE);
        txtTitle.setTextSize(16);
        txtTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        titleTextLayout.addView(txtTitle);

        // Subtitle
        TextView txtSub = new TextView(this);
        txtSub.setText("Original Price: LKR " + String.format(Locale.getDefault(), "%,.2f", p.price.doubleValue()));
        txtSub.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
        txtSub.setTextSize(12);
        txtSub.setPadding(0, 4, 0, 0);
        titleTextLayout.addView(txtSub);

        headerLayout.addView(titleTextLayout);
        layout.addView(headerLayout);

        // Selected Variation (if applicable)
        boolean hasVariations = (p.variationsJson != null && !p.variationsJson.isEmpty() && !p.variationsJson.equals("null"));
        final Spinner spinnerVar;
        if (hasVariations) {
            final List<String> variationList = new ArrayList<>();
            try {
                org.json.JSONArray vars = new org.json.JSONArray(p.variationsJson);
                for (int i = 0; i < vars.length(); i++) {
                    org.json.JSONObject vObj = vars.getJSONObject(i);
                    String opt = vObj.optString("option_name", "");
                    int qty = vObj.optInt("quantity_on_hand", 0);
                    variationList.add(opt + " (Stock: " + qty + ")");
                }
            } catch (Exception e) {
                android.util.Log.e("BillingActivity", "Error parsing variations in dialog: " + e.getMessage());
            }

            if (!variationList.isEmpty()) {
                TextView lblVar = new TextView(this);
                lblVar.setText("SELECTED VARIATION:");
                lblVar.setTextColor(android.graphics.Color.parseColor("#3B82F6"));
                lblVar.setTextSize(11);
                lblVar.setTypeface(null, android.graphics.Typeface.BOLD);
                lblVar.setPadding(0, 8, 0, 4);
                layout.addView(lblVar);

                spinnerVar = new Spinner(this);
                spinnerVar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
                ArrayAdapter<String> varAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, variationList) {
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
                            ((TextView) v).setBackgroundColor(android.graphics.Color.parseColor("#1E293B"));
                            ((TextView) v).setTextSize(14);
                        }
                        return v;
                    }
                };
                spinnerVar.setAdapter(varAdapter);
                layout.addView(spinnerVar);

                if (existingItem != null && existingItem.selectedVariation != null && !existingItem.selectedVariation.isEmpty()) {
                    for (int i = 0; i < variationList.size(); i++) {
                        if (variationList.get(i).startsWith(existingItem.selectedVariation)) {
                            spinnerVar.setSelection(i);
                            break;
                        }
                    }
                }
            } else {
                spinnerVar = null;
            }
        } else {
            spinnerVar = null;
        }

        // 1. Price override input
        TextView lblOverride = new TextView(this);
        lblOverride.setText("UNIT PRICE (LKR):");
        lblOverride.setTextColor(android.graphics.Color.parseColor("#3B82F6"));
        lblOverride.setTextSize(11);
        lblOverride.setTypeface(null, android.graphics.Typeface.BOLD);
        lblOverride.setPadding(0, 12, 0, 0);
        layout.addView(lblOverride);

        final EditText edtOverridePrice = new EditText(this);
        edtOverridePrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edtOverridePrice.setTextColor(android.graphics.Color.WHITE);
        edtOverridePrice.setHintTextColor(android.graphics.Color.parseColor("#94A3B8"));
        java.math.BigDecimal initialPrice = (existingItem != null && existingItem.customPrice.compareTo(java.math.BigDecimal.ZERO) > 0) ? existingItem.customPrice : p.price;
        edtOverridePrice.setText(initialPrice.toPlainString());
        edtOverridePrice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
        layout.addView(edtOverridePrice);

        // 2. Quantity input
        TextView lblQty = new TextView(this);
        lblQty.setText("QUANTITY:");
        lblQty.setTextColor(android.graphics.Color.parseColor("#3B82F6"));
        lblQty.setTextSize(11);
        lblQty.setTypeface(null, android.graphics.Typeface.BOLD);
        lblQty.setPadding(0, 12, 0, 0);
        layout.addView(lblQty);

        LinearLayout qtyRow = new LinearLayout(this);
        qtyRow.setOrientation(LinearLayout.HORIZONTAL);
        qtyRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        qtyRow.setPadding(0, 4, 0, 4);

        Button btnMinus = new Button(this);
        btnMinus.setText("−");
        btnMinus.setTextColor(android.graphics.Color.WHITE);
        btnMinus.setTextSize(18);
        btnMinus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                (int)(48 * getResources().getDisplayMetrics().density), 
                (int)(48 * getResources().getDisplayMetrics().density));
        btnMinus.setLayoutParams(btnParams);

        final EditText edtQtyInput = new EditText(this);
        edtQtyInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        edtQtyInput.setTextColor(android.graphics.Color.WHITE);
        edtQtyInput.setHintTextColor(android.graphics.Color.parseColor("#94A3B8"));
        int initialQty = existingItem != null ? existingItem.quantity : 1;
        edtQtyInput.setText(String.valueOf(initialQty));
        edtQtyInput.setGravity(android.view.Gravity.CENTER);
        edtQtyInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        inputParams.setMargins((int)(12 * getResources().getDisplayMetrics().density), 0, (int)(12 * getResources().getDisplayMetrics().density), 0);
        edtQtyInput.setLayoutParams(inputParams);

        Button btnPlus = new Button(this);
        btnPlus.setText("+");
        btnPlus.setTextColor(android.graphics.Color.WHITE);
        btnPlus.setTextSize(18);
        btnPlus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
        btnPlus.setLayoutParams(btnParams);

        qtyRow.addView(btnMinus);
        qtyRow.addView(edtQtyInput);
        qtyRow.addView(btnPlus);
        layout.addView(qtyRow);

        final android.os.Handler autoIncrementHandler = new android.os.Handler();
        class AutoRepeater implements View.OnTouchListener {
            private final int direction; // -1 for dec, 1 for inc
            private boolean isPressed = false;
            
            AutoRepeater(int direction) {
                this.direction = direction;
            }
            
            private final Runnable updateTask = new Runnable() {
                @Override
                public void run() {
                    if (!isPressed) return;
                    changeQty();
                    autoIncrementHandler.postDelayed(this, 100);
                }
            };
            
            private void changeQty() {
                try {
                    String val = edtQtyInput.getText().toString().trim();
                    int current = val.isEmpty() ? 0 : Integer.parseInt(val);
                    int next = current + direction;
                    if (next < 1) next = 1;
                    edtQtyInput.setText(String.valueOf(next));
                    edtQtyInput.setSelection(edtQtyInput.getText().length());
                } catch (Exception ignored) {}
            }
            
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch(event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        isPressed = true;
                        changeQty();
                        autoIncrementHandler.postDelayed(updateTask, 400);
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        isPressed = false;
                        autoIncrementHandler.removeCallbacks(updateTask);
                        return true;
                }
                return false;
            }
        }
        
        btnMinus.setOnTouchListener(new AutoRepeater(-1));
        btnPlus.setOnTouchListener(new AutoRepeater(1));

        // 3. Discount inputs
        LinearLayout discountRow = new LinearLayout(this);
        discountRow.setOrientation(LinearLayout.HORIZONTAL);
        discountRow.setPadding(0, 12, 0, 0);

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
        if (existingItem != null && existingItem.discountPercent.compareTo(java.math.BigDecimal.ZERO) > 0) {
            edtDiscountPct.setText(existingItem.discountPercent.toPlainString());
        }
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
        if (existingItem != null && existingItem.discountAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
            edtDiscountAmt.setText(existingItem.discountAmount.toPlainString());
        } else if (existingItem != null && existingItem.discountVal.compareTo(java.math.BigDecimal.ZERO) > 0) {
            edtDiscountAmt.setText(existingItem.discountVal.toPlainString());
        }
        edtDiscountAmt.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155")));
        colAmt.addView(edtDiscountAmt);

        discountRow.addView(colAmt);
        layout.addView(discountRow);

        // Live Total Preview Label
        final TextView txtLiveTotal = new TextView(this);
        java.math.BigDecimal currentTotal = (existingItem != null) ? existingItem.total : p.price;
        txtLiveTotal.setText("TOTAL: " + CurrencyUtils.formatLKR(currentTotal));
        txtLiveTotal.setTextColor(android.graphics.Color.parseColor("#10B981"));
        txtLiveTotal.setTextSize(16);
        txtLiveTotal.setTypeface(null, android.graphics.Typeface.BOLD);
        txtLiveTotal.setPadding(0, 16, 0, 12);
        txtLiveTotal.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        layout.addView(txtLiveTotal);

        builder.setView(layout);
        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        edtQtyInput.requestFocus();
        edtQtyInput.postDelayed(new Runnable() {
            @Override
            public void run() {
                edtQtyInput.selectAll();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(edtQtyInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 150);

        final boolean[] percentActiveHolder = new boolean[]{ existingItem != null && existingItem.isPercentDiscountActive };

        // live preview update helper
        final Runnable updatePreview = new Runnable() {
            private boolean isUpdating = false;
            @Override
            public void run() {
                if (isUpdating) return;
                isUpdating = true;
                try {
                    String overridePriceStr = edtOverridePrice.getText().toString().trim();
                    java.math.BigDecimal uPrice = overridePriceStr.isEmpty() ? p.price : CurrencyUtils.toBigDecimal(overridePriceStr);

                    String qtyStr = edtQtyInput.getText().toString().trim();
                    int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);

                    java.math.BigDecimal sub = uPrice.multiply(java.math.BigDecimal.valueOf(qty));
                    java.math.BigDecimal discountVal = java.math.BigDecimal.ZERO;

                    String pctStr = edtDiscountPct.getText().toString().trim();
                    String amtStr = edtDiscountAmt.getText().toString().trim();

                    if (edtDiscountPct.hasFocus()) {
                        percentActiveHolder[0] = true;
                        if (!pctStr.isEmpty()) {
                            java.math.BigDecimal pct = CurrencyUtils.toBigDecimal(pctStr);
                            discountVal = CurrencyUtils.calculatePercentage(sub, pct);
                            edtDiscountAmt.setText(String.format(Locale.getDefault(), "%.2f", discountVal));
                        } else {
                            edtDiscountAmt.setText("");
                        }
                    } else if (edtDiscountAmt.hasFocus()) {
                        percentActiveHolder[0] = false;
                        if (!amtStr.isEmpty()) {
                            discountVal = CurrencyUtils.toBigDecimal(amtStr);
                            java.math.BigDecimal pct = sub.compareTo(java.math.BigDecimal.ZERO) > 0 ? discountVal.multiply(java.math.BigDecimal.valueOf(100)).divide(sub, 1, java.math.RoundingMode.HALF_UP) : java.math.BigDecimal.ZERO;
                            edtDiscountPct.setText(String.format(Locale.getDefault(), "%.1f", pct));
                        } else {
                            edtDiscountPct.setText("");
                        }
                    } else {
                        if (percentActiveHolder[0]) {
                            if (!pctStr.isEmpty()) {
                                java.math.BigDecimal pct = CurrencyUtils.toBigDecimal(pctStr);
                                discountVal = CurrencyUtils.calculatePercentage(sub, pct);
                            }
                        } else {
                            if (!amtStr.isEmpty()) {
                                discountVal = CurrencyUtils.toBigDecimal(amtStr);
                            }
                        }
                    }

                    java.math.BigDecimal finalTotal = sub.subtract(discountVal);
                    if (finalTotal.compareTo(java.math.BigDecimal.ZERO) < 0) finalTotal = java.math.BigDecimal.ZERO;
                    txtLiveTotal.setText("TOTAL: " + CurrencyUtils.formatLKR(finalTotal));
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
        btnRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        Button btnCancel = new Button(this);
        btnCancel.setText("Cancel");
        btnCancel.setTextColor(android.graphics.Color.WHITE);
        btnCancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#94A3B8")));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        btnCancel.setLayoutParams(cancelParams);
        btnRow.addView(btnCancel);

        if (existingItem != null) {
            Button btnRemove = new Button(this);
            btnRemove.setText("Remove");
            btnRemove.setTextColor(android.graphics.Color.WHITE);
            btnRemove.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#EF4444")));
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            removeParams.setMargins((int)(8 * getResources().getDisplayMetrics().density), 0, (int)(8 * getResources().getDisplayMetrics().density), 0);
            btnRemove.setLayoutParams(removeParams);
            btnRow.addView(btnRemove);

            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new androidx.appcompat.app.AlertDialog.Builder(BillingActivity.this)
                        .setTitle("Remove Item")
                        .setMessage("Remove this item from the cart?")
                        .setPositiveButton("Remove", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialogConfirm, int which) {
                                cartList.remove(existingItem);
                                boolean checkoutMode = (layoutCartCheckoutMode != null && layoutCartCheckoutMode.getVisibility() == View.VISIBLE);
                                if (cartAdapter != null) {
                                    cartAdapter.notifyDataSetChanged();
                                }
                                recalculateCart();
                                showCartOverlay(checkoutMode);
                                dialogConfirm.dismiss();
                                dialog.dismiss();
                                Toast.makeText(BillingActivity.this, p.name + " removed from cart.", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                }
            });
        } else {
            View spacerBtn = new View(this);
            spacerBtn.setLayoutParams(new LinearLayout.LayoutParams((int)(16 * getResources().getDisplayMetrics().density), 1));
            btnRow.addView(spacerBtn);
        }

        Button btnAction = new Button(this);
        btnAction.setText(existingItem != null ? "Update" : "Add to Cart");
        btnAction.setTextColor(android.graphics.Color.WHITE);
        btnAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#10B981")));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
        btnAction.setLayoutParams(actionParams);
        btnRow.addView(btnAction);

        layout.addView(btnRow);

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String overridePriceStr = edtOverridePrice.getText().toString().trim();
                    java.math.BigDecimal uPrice = overridePriceStr.isEmpty() ? p.price : CurrencyUtils.toBigDecimal(overridePriceStr);

                    String qtyStr = edtQtyInput.getText().toString().trim();
                    int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);

                    java.math.BigDecimal pct = java.math.BigDecimal.ZERO;
                    String pctStr = edtDiscountPct.getText().toString().trim();
                    if (!pctStr.isEmpty()) pct = CurrencyUtils.toBigDecimal(pctStr);

                    java.math.BigDecimal amt = java.math.BigDecimal.ZERO;
                    String amtStr = edtDiscountAmt.getText().toString().trim();
                    if (!amtStr.isEmpty()) amt = CurrencyUtils.toBigDecimal(amtStr);

                    String selectedVar = "";
                    if (hasVariations && spinnerVar != null) {
                        String selectedVarFull = spinnerVar.getSelectedItem().toString();
                        int idx = selectedVarFull.indexOf(" (Stock:");
                        selectedVar = idx != -1 ? selectedVarFull.substring(0, idx) : selectedVarFull;
                    }

                    int left = p.qtyOnHand - p.qtyReserved;
                    int oldQty = existingItem != null ? existingItem.quantity : 0;
                    if (left + oldQty <= 0) {
                        Toast.makeText(BillingActivity.this, "Out of Stock.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (qty <= 0) {
                        Toast.makeText(BillingActivity.this, "Enter valid quantity.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (qty > left + oldQty) {
                        Toast.makeText(BillingActivity.this, "Insufficient stock available offline.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (existingItem != null) {
                        existingItem.quantity = qty;
                        existingItem.customPrice = uPrice;
                        existingItem.activePrice = uPrice;
                        existingItem.discountPercent = pct;
                        existingItem.discountAmount = amt;
                        existingItem.isPercentDiscountActive = percentActiveHolder[0];
                        existingItem.selectedVariation = selectedVar;

                        java.math.BigDecimal subItem = uPrice.multiply(java.math.BigDecimal.valueOf(qty));
                        java.math.BigDecimal discountValItem = java.math.BigDecimal.ZERO;
                        if (existingItem.isPercentDiscountActive && pct.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            discountValItem = CurrencyUtils.calculatePercentage(subItem, pct);
                        } else if (!existingItem.isPercentDiscountActive && amt.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            discountValItem = amt;
                        }
                        existingItem.discountVal = discountValItem;
                        existingItem.total = subItem.subtract(discountValItem);
                        if (existingItem.total.compareTo(java.math.BigDecimal.ZERO) < 0) existingItem.total = java.math.BigDecimal.ZERO;

                        boolean checkoutMode = (layoutCartCheckoutMode != null && layoutCartCheckoutMode.getVisibility() == View.VISIBLE);
                        if (cartAdapter != null) {
                            cartAdapter.notifyDataSetChanged();
                        }
                        recalculateCart();
                        showCartOverlay(checkoutMode);
                        dialog.dismiss();
                        Toast.makeText(BillingActivity.this, "Cart updated.", Toast.LENGTH_SHORT).show();
                    } else {
                        // Add to cart with custom parameters
                        addToCartCustom(p, qty, uPrice, pct, amt, percentActiveHolder[0]);
                        if (hasVariations && !selectedVar.isEmpty()) {
                            // Find the newly added item and assign selected variation
                            for (CartItemModel item : cartList) {
                                if (item.productId == p.id) {
                                    item.selectedVariation = selectedVar;
                                    break;
                                }
                            }
                        }
                        dialog.dismiss();
                    }
                } catch (Exception e) {
                    Toast.makeText(BillingActivity.this, "Invalid inputs entered.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private ProductModel getProductById(int prodId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM products WHERE id = ?", new String[]{String.valueOf(prodId)});
        ProductModel p = null;
        if (cursor.moveToFirst()) {
            p = new ProductModel();
            p.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            p.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
            p.category = cursor.getString(cursor.getColumnIndexOrThrow("category_name"));
            p.price = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("price")));
            p.wholesalePrice = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("wholesale_price")));
            p.qtyOnHand = cursor.getInt(cursor.getColumnIndexOrThrow("quantity_on_hand"));
            p.qtyReserved = cursor.getInt(cursor.getColumnIndexOrThrow("quantity_reserved"));
            p.localImagePath = cursor.getString(cursor.getColumnIndexOrThrow("local_image_path"));
            p.sku = cursor.getString(cursor.getColumnIndexOrThrow("sku"));
            p.sampleCode = cursor.getString(cursor.getColumnIndexOrThrow("sample_code"));
            p.variationsJson = cursor.getString(cursor.getColumnIndexOrThrow("variations_json"));
            p.brand = cursor.getString(cursor.getColumnIndexOrThrow("brand"));
            p.description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
        }
        cursor.close();
        return p;
    }

    private void addToCartCustom(ProductModel p, int qty, java.math.BigDecimal customPrice, java.math.BigDecimal discountPercent, java.math.BigDecimal discountAmount, boolean isPercentActive) {
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
        java.math.BigDecimal sub = customPrice.multiply(java.math.BigDecimal.valueOf(qty));
        java.math.BigDecimal discountVal = java.math.BigDecimal.ZERO;

        if (isPercentActive && discountPercent.compareTo(java.math.BigDecimal.ZERO) > 0) {
            discountVal = CurrencyUtils.calculatePercentage(sub, discountPercent);
        } else if (!isPercentActive && discountAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
            discountVal = discountAmount;
        }

        java.math.BigDecimal total = sub.subtract(discountVal);
        if (total.compareTo(java.math.BigDecimal.ZERO) < 0) total = java.math.BigDecimal.ZERO;

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
                item.isPercentDiscountActive = isPercentActive;

                java.math.BigDecimal itemSub = item.customPrice.multiply(java.math.BigDecimal.valueOf(item.quantity));
                java.math.BigDecimal itemDisc = java.math.BigDecimal.ZERO;
                if (item.isPercentDiscountActive && item.discountPercent.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    itemDisc = CurrencyUtils.calculatePercentage(itemSub, item.discountPercent);
                } else if (!item.isPercentDiscountActive && item.discountAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    itemDisc = item.discountAmount;
                }
                item.discountVal = itemDisc;
                item.total = itemSub.subtract(item.discountVal);
                if (item.total.compareTo(java.math.BigDecimal.ZERO) < 0) item.total = java.math.BigDecimal.ZERO;
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
            item.isPercentDiscountActive = isPercentActive;
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

            final java.math.BigDecimal currentPrice = p.price;
            lblPriceGrid.setText(String.format(Locale.getDefault(), "LKR %.2f", currentPrice.doubleValue()));

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

            convertView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showProductQuickViewDialog(p);
                    return true;
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
            Cursor cursor = db.rawQuery("SELECT name FROM categories WHERE status = 'active' ORDER BY name ASC", null);
            while (cursor.moveToNext()) {
                categoryList.add(cursor.getString(0));
            }
            cursor.close();

            // Fallback to distinct product category names if categories table is not yet seeded
            if (categoryList.size() <= 1) {
                cursor = db.rawQuery("SELECT DISTINCT category_name FROM products WHERE category_name IS NOT NULL AND category_name != '' AND category_name != 'null' AND status = 'active' ORDER BY category_name ASC", null);
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
        if (spinnerCategory != null) {
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

        setupHorizontalCategories();
    }

    private void setupHorizontalCategories() {
        final LinearLayout layoutHorizontalCategories = findViewById(R.id.layoutHorizontalCategories);
        if (layoutHorizontalCategories == null) return;
        
        layoutHorizontalCategories.removeAllViews();
        
        for (final String catName : categoryList) {
            final Button btn = new Button(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (int) (36 * getResources().getDisplayMetrics().density)
            );
            params.setMargins(0, 0, (int) (8 * getResources().getDisplayMetrics().density), 0);
            btn.setLayoutParams(params);
            btn.setPadding((int) (14 * getResources().getDisplayMetrics().density), 0, (int) (14 * getResources().getDisplayMetrics().density), 0);
            btn.setText(catName);
            btn.setTextSize(12);
            btn.setAllCaps(false);
            
            // Set style based on selection
            if (catName.equalsIgnoreCase(selectedCategoryName)) {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#3B82F6")));
                btn.setTextColor(android.graphics.Color.WHITE);
            } else {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1E293B")));
                btn.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
            }
            
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedCategoryName = catName;
                    setupHorizontalCategories(); // redraw selection state
                    loadCatalogItems(edtProductSearch.getText().toString());
                }
            });
            
            layoutHorizontalCategories.addView(btn);
        }
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
            allCustNames.add(c.name + " (Bal: LKR " + String.format(Locale.getDefault(), "%,.2f", c.outstanding.doubleValue()) + ")");
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
                                filteredCustNames.add(c.name + " (Bal: LKR " + String.format(Locale.getDefault(), "%,.2f", c.outstanding.doubleValue()) + ")");
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
                        String matchStr = c.name + " (Bal: LKR " + String.format(Locale.getDefault(), "%,.2f", c.outstanding.doubleValue()) + ")";
                        if (matchStr.equalsIgnoreCase(chosenName)) {
                            selectedCustomer = c;
                            break;
                        }
                    }
                    if (selectedCustomer != null) {
                        String displayName = selectedCustomer.name;
                        String balText = "Outstanding: LKR " + String.format(Locale.getDefault(), "%,.2f", selectedCustomer.outstanding.doubleValue());

                        if (txtOverlayCustomerName != null) txtOverlayCustomerName.setText(displayName);
                        if (txtOverlayCustomerBalance != null) txtOverlayCustomerBalance.setText(balText);
                        if (txtCheckoutCustomerName != null) txtCheckoutCustomerName.setText(displayName);

                        if (txtSelectedCustomerName != null) {
                            String displayNameFull = selectedCustomer.name + "\nOutstanding: LKR " + String.format(Locale.getDefault(), "%,.2f", selectedCustomer.outstanding.doubleValue());
                            txtSelectedCustomerName.setText(displayNameFull);
                        }
                        saveDraftBill();
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

    private void showEditCustomerDialog() {
        if (selectedCustomer == null) {
            Toast.makeText(this, "Please select a customer first.", Toast.LENGTH_SHORT).show();
            return;
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(BillingActivity.this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_customer, null);
        builder.setView(dialogView);

        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.show();

        // Bind fields
        final EditText edtName = dialogView.findViewById(R.id.edtEditCustomerName);
        final EditText edtPhone = dialogView.findViewById(R.id.edtEditCustomerPhone);
        final EditText edtWhatsapp = dialogView.findViewById(R.id.edtEditCustomerWhatsapp);
        final EditText edtEmail = dialogView.findViewById(R.id.edtEditCustomerEmail);
        final EditText edtAddress = dialogView.findViewById(R.id.edtEditCustomerAddress);
        final EditText edtTerritory = dialogView.findViewById(R.id.edtEditCustomerTerritory);
        final EditText edtRoute = dialogView.findViewById(R.id.edtEditCustomerRoute);
        final EditText edtType = dialogView.findViewById(R.id.edtEditCustomerType);
        final EditText edtCreditLimit = dialogView.findViewById(R.id.edtEditCustomerCreditLimit);
        final EditText edtNotes = dialogView.findViewById(R.id.edtEditCustomerNotes);

        Button btnCancel = dialogView.findViewById(R.id.btnCancelEditCustomer);
        Button btnSave = dialogView.findViewById(R.id.btnSaveEditCustomer);

        // Prepopulate fields
        edtName.setText(selectedCustomer.name != null ? selectedCustomer.name : "");
        edtPhone.setText(selectedCustomer.phone != null ? selectedCustomer.phone : "");
        edtWhatsapp.setText(selectedCustomer.whatsapp != null ? selectedCustomer.whatsapp : "");
        edtEmail.setText(selectedCustomer.email != null ? selectedCustomer.email : "");
        edtAddress.setText(selectedCustomer.address != null ? selectedCustomer.address : "");
        edtTerritory.setText(selectedCustomer.territory != null ? selectedCustomer.territory : "");
        edtRoute.setText(selectedCustomer.mcaName != null ? selectedCustomer.mcaName : "");
        edtType.setText(selectedCustomer.customerType != null ? selectedCustomer.customerType : "");
        edtCreditLimit.setText(String.valueOf(selectedCustomer.creditLimit));
        edtNotes.setText(selectedCustomer.notes != null ? selectedCustomer.notes : "");

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = edtName.getText().toString().trim();
                String phone = edtPhone.getText().toString().trim();
                String whatsapp = edtWhatsapp.getText().toString().trim();
                String email = edtEmail.getText().toString().trim();
                String address = edtAddress.getText().toString().trim();
                String territory = edtTerritory.getText().toString().trim();
                String route = edtRoute.getText().toString().trim();
                String type = edtType.getText().toString().trim();
                String creditLimitStr = edtCreditLimit.getText().toString().trim();
                String notes = edtNotes.getText().toString().trim();

                if (name.isEmpty()) {
                    Toast.makeText(BillingActivity.this, "Customer name is required.", Toast.LENGTH_SHORT).show();
                    return;
                }

                java.math.BigDecimal creditLimit = java.math.BigDecimal.ZERO;
                if (!creditLimitStr.isEmpty()) {
                    try {
                        creditLimit = CurrencyUtils.toBigDecimal(creditLimitStr);
                    } catch (Exception e) {}
                }

                // Update database
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("name", name);
                cv.put("phone", phone);
                cv.put("whatsapp", whatsapp);
                cv.put("email", email);
                cv.put("address", address);
                cv.put("territory", territory);
                cv.put("mca_name", route);
                cv.put("customer_type", type);
                cv.put("credit_limit", creditLimit.doubleValue());
                cv.put("notes", notes);
                cv.put("is_synced", 0);

                int rows = db.update("customers", cv, "id = ?", new String[]{String.valueOf(selectedCustomer.id)});
                if (rows > 0) {
                    Toast.makeText(BillingActivity.this, "Customer updated successfully!", Toast.LENGTH_SHORT).show();
                    
                    // Update current selectedCustomer model reference in memory
                    selectedCustomer.name = name;
                    selectedCustomer.phone = phone;
                    selectedCustomer.whatsapp = whatsapp;
                    selectedCustomer.email = email;
                    selectedCustomer.address = address;
                    selectedCustomer.territory = territory;
                    selectedCustomer.mcaName = route;
                    selectedCustomer.customerType = type;
                    selectedCustomer.creditLimit = creditLimit;
                    selectedCustomer.notes = notes;

                    // Update UI immediately in billing screen (both in overlay and main screen)
                    String displayName = selectedCustomer.name;
                    String balText = "Outstanding: LKR " + String.format(Locale.getDefault(), "%,.2f", selectedCustomer.outstanding.doubleValue());

                    if (txtOverlayCustomerName != null) txtOverlayCustomerName.setText(displayName);
                    if (txtOverlayCustomerBalance != null) txtOverlayCustomerBalance.setText(balText);
                    if (txtCheckoutCustomerName != null) txtCheckoutCustomerName.setText(displayName);

                    if (txtSelectedCustomerName != null) {
                        String displayNameFull = selectedCustomer.name + "\nOutstanding: LKR " + String.format(Locale.getDefault(), "%,.2f", selectedCustomer.outstanding.doubleValue());
                        txtSelectedCustomerName.setText(displayNameFull);
                    }

                    saveDraftBill();

                    // Auto sync on customer edit removed in favor of manual sync.
                } else {
                    Toast.makeText(BillingActivity.this, "Failed to update customer.", Toast.LENGTH_SHORT).show();
                }

                dialog.dismiss();
            }
        });
    }

    private android.app.ProgressDialog progressDialog;

    private void showGlobalLoadingDialog(String message) {
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

    private void dismissGlobalLoadingDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
