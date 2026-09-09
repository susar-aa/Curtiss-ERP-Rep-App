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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
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

    private Spinner spinnerPaymentMethod, spinnerCategory, spinnerPaymentTerm; private int selectedPaymentTermPos = 0; private View cardFloatingSearch; private View layoutHeader;
    private boolean isResumingDraft = false;
    private boolean isShowingResumeDialog = false;

    private EditText edtProductSearch, edtDiscount, edtDirectDiscountPct;
    private RecyclerView lstProducts, lstCartSummary;
    private TextView txtCartItemsCount, txtCartSalesSum, txtSubtotal, txtTax, txtNetTotal, txtRoundingAdjustment, txtCheckoutOutstanding;
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
    private androidx.recyclerview.widget.RecyclerView gridProducts;
    private ProductGridRecyclerAdapter productGridAdapter;
    private boolean isVisualMode = false;

    private static final android.util.LruCache<String, android.graphics.Bitmap> THUMBNAIL_CACHE =
            new android.util.LruCache<String, android.graphics.Bitmap>(150) {
                @Override
                protected int sizeOf(String key, android.graphics.Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };

    private static android.graphics.Bitmap getCachedThumbnail(String path, int sampleSize) {
        if (path == null || path.isEmpty()) return null;
        String key = path + "_" + sampleSize;
        android.graphics.Bitmap cached = THUMBNAIL_CACHE.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        try {
            java.io.File file = new java.io.File(path);
            if (!file.exists()) return null;
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap != null) {
                THUMBNAIL_CACHE.put(key, bitmap);
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private DatabaseHelper dbHelper;
    private List<CustomerModel> customerList = new ArrayList<>();
    private List<ProductModel> productList = new ArrayList<>();
    private List<CartItemModel> cartList = new ArrayList<>();

    private ProductAdapter productAdapter;
    private CartAdapter cartAdapter;
    private long currentRouteLocalId = -1;
    private long editInvoiceId = -1;
    private double checkoutLatitude = 7.1824;
    private double checkoutLongitude = 79.8801;

    // Redesigned layouts and controls
    private LinearLayout layoutCartViewMode, layoutCartCheckoutMode, layoutCheckoutDetails, layoutExpandedSearch;
    private View layoutBillingLoading;
    private TextView txtCartOverlayTitle, txtOverlayCustomerName, txtOverlayCustomerBalance, txtOverlayTotalItems, txtOverlayTotalValue, txtCheckoutCustomerName, txtCheckoutItemSummary;
    private Button btnOverlayChangeCustomer, btnOverlayEditCustomer, btnCheckout;
    private ImageButton btnSearchToggle, btnClearSearch;
    private boolean isGlobalDiscountPercent = false;
    private View cardCartBadge;
    private TextView txtCartCount;
    private boolean isCurrentlyCheckoutMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(android.graphics.Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setContentView(R.layout.activity_billing);

        dbHelper = DatabaseHelper.getInstance(this);

        // Bind layouts
        layoutBillingLoading = findViewById(R.id.layoutBillingLoading);
        txtSelectedCustomerName = findViewById(R.id.txtOverlayCustomerName);
        btnChangeCustomer = findViewById(R.id.btnOverlayChangeCustomer);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerPaymentMethod = findViewById(R.id.spinnerPaymentMethod);
        spinnerPaymentTerm = findViewById(R.id.spinnerPaymentTerm); cardFloatingSearch = findViewById(R.id.cardFloatingSearch);

        edtProductSearch = findViewById(R.id.edtProductSearch);
        edtDiscount = findViewById(R.id.edtDiscount);
        edtDirectDiscountPct = findViewById(R.id.edtDirectDiscountPct);
        lstProducts = findViewById(R.id.lstProducts);
        lstProducts.setLayoutManager(new LinearLayoutManager(this));
        lstCartSummary = findViewById(R.id.lstCartSummary);
        lstCartSummary.setLayoutManager(new LinearLayoutManager(this));

        txtCartItemsCount = findViewById(R.id.txtOverlayTotalItems);
        txtCartSalesSum = findViewById(R.id.txtOverlayTotalValue);
        txtSubtotal = findViewById(R.id.txtSubtotal);
        txtTax = findViewById(R.id.txtTax);
        txtNetTotal = findViewById(R.id.txtNetTotal);
        txtRoundingAdjustment = findViewById(R.id.txtRoundingAdjustment);
        layoutRoundingAdjustment = findViewById(R.id.layoutRoundingAdjustment);
        txtCheckoutOutstanding = findViewById(R.id.txtCheckoutOutstanding);

        layoutCartOverlay = findViewById(R.id.layoutCartOverlay);
        btnViewCart = null;
        
        btnConfirmCheckout = findViewById(R.id.btnConfirmCheckout);
        btnCancelCart = findViewById(R.id.btnCancelCart);
        
        android.widget.Button btnEmptyCart = findViewById(R.id.btnEmptyCart);
        if (btnEmptyCart != null) {
            btnEmptyCart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new androidx.appcompat.app.AlertDialog.Builder(BillingActivity.this)
                        .setTitle("Empty Cart")
                        .setMessage("Are you sure you want to completely empty the cart?")
                        .setPositiveButton("Empty", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                cartList.clear();
                                clearDraftBill();
                                recalculateCart();
                                if (layoutCartOverlay != null) {
                                    layoutCartOverlay.setVisibility(View.GONE);
                                }
                                Toast.makeText(BillingActivity.this, "Cart emptied.", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                }
            });
        }

        btnModeStandard = findViewById(R.id.btnModeStandard);
        btnModeVisual = findViewById(R.id.btnModeVisual);
        gridProducts = findViewById(R.id.gridProducts);
        if (gridProducts != null) {
            gridProducts.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
        }

        // Bind redesigned layouts and controls
        // Note: layoutExpandedSearch, btnSearchToggle, btnClearSearch are removed from the
        // new XML (search is always visible in the floating capsule). They resolve to null
        // and are already guarded by null checks below — no crash will occur.
        layoutExpandedSearch = null;
        btnSearchToggle = null;
        btnClearSearch = null;
        btnCheckout = findViewById(R.id.btnCheckout);
        cardCartBadge = findViewById(R.id.cardCartBadge);
        txtCartCount = findViewById(R.id.txtCartCount);

        txtCartOverlayTitle = findViewById(R.id.txtCartOverlayTitle);
        // layoutCartViewMode / layoutCartCheckoutMode removed in redesign — single unified overlay now
        layoutCartViewMode = null;
        txtOverlayCustomerName = findViewById(R.id.txtOverlayCustomerName);
        txtOverlayCustomerBalance = findViewById(R.id.txtOverlayCustomerBalance);
        btnOverlayChangeCustomer = findViewById(R.id.btnOverlayChangeCustomer);
        btnOverlayEditCustomer = findViewById(R.id.btnOverlayEditCustomer);
        if (btnOverlayEditCustomer != null) {
            btnOverlayEditCustomer.setVisibility(View.GONE);
        }
        txtOverlayTotalItems = findViewById(R.id.txtOverlayTotalItems);
        txtOverlayTotalValue = findViewById(R.id.txtOverlayTotalValue);

        // txtCheckoutCustomerName / txtCheckoutItemSummary / layoutCartCheckoutMode removed in redesign
        layoutCartCheckoutMode = null;
        txtCheckoutCustomerName = null;
        txtCheckoutItemSummary = null;
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
                // Design system: black = active, #F2F2F7 = inactive
                btnModeStandard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#000000")));
                btnModeStandard.setTextColor(android.graphics.Color.WHITE);
                btnModeVisual.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F2F2F7")));
                btnModeVisual.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
            }
        });

        btnModeVisual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isVisualMode = true;
                lstProducts.setVisibility(View.GONE);
                gridProducts.setVisibility(View.VISIBLE);
                // Design system: black = active, #F2F2F7 = inactive
                btnModeVisual.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#000000")));
                btnModeVisual.setTextColor(android.graphics.Color.WHITE);
                btnModeStandard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F2F2F7")));
                btnModeStandard.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
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
        long incomingEditId = getIntent().getLongExtra("edit_invoice_id", -1);
        if (incomingEditId != -1) {
            editInvoiceId = incomingEditId;
            loadInvoiceForEditing(editInvoiceId);
            validateSelectedCustomerAndPrompt(null);
        } else if (hasDraftBill()) {
            android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_resume_cart, null);
            final androidx.appcompat.app.AlertDialog resumeDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

            if (resumeDialog.getWindow() != null) {
                resumeDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }

            android.widget.Button btnResumeDraft = dialogView.findViewById(R.id.btnResumeDraft);
            android.widget.Button btnClearDraft = dialogView.findViewById(R.id.btnClearDraft);

            btnResumeDraft.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isShowingResumeDialog = false;
                    resumeDialog.dismiss();
                    resumeDraftBill();
                    validateSelectedCustomerAndPrompt(null);
                }
            });

            btnClearDraft.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isShowingResumeDialog = false;
                    resumeDialog.dismiss();
                    clearDraftBill();
                    showCustomerSelectionDialog();
                }
            });

            isShowingResumeDialog = true;
            resumeDialog.show();
        } else {
            showCustomerSelectionDialog();
        }

        // View Cart overlay / Cart Badge / Checkout action
        if (btnViewCart != null) {
            btnViewCart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCartOverlay(false);
                }
            });
        }

        if (cardCartBadge != null) {
            cardCartBadge.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCartOverlay(false);
                }
            });
        }

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
                        if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.VISIBLE);
                    if (layoutHeader != null) layoutHeader.setVisibility(View.VISIBLE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            RecyclerView list1 = findViewById(R.id.gridProducts);
            RecyclerView list2 = findViewById(R.id.lstProducts);
            if (list1 != null) list1.setRenderEffect(null);
            if (list2 != null) list2.setRenderEffect(null);
        }
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
                        isGlobalDiscountPercent = true;
                        String pctStr = edtDirectDiscountPct.getText().toString().trim();
                        if (!pctStr.isEmpty()) {
                            java.math.BigDecimal pct = CurrencyUtils.toBigDecimal(pctStr);
                            java.math.BigDecimal discountVal = CurrencyUtils.calculatePercentage(subtotal, pct);
                            edtDiscount.setText(String.format(Locale.getDefault(), "%.2f", discountVal));
                        } else {
                            edtDiscount.setText("");
                        }
                    } else if (edtDiscount.hasFocus()) {
                        isGlobalDiscountPercent = false;
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

        final Button btnTabCheckout = findViewById(R.id.btnTabCheckout);
        final Button btnTabItems = findViewById(R.id.btnTabItems);
        final LinearLayout layoutTabCheckoutContainer = findViewById(R.id.layoutTabCheckoutContainer);

        if (btnTabCheckout != null && btnTabItems != null && layoutTabCheckoutContainer != null && lstCartSummary != null) {
            btnTabCheckout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    layoutTabCheckoutContainer.setVisibility(View.VISIBLE);
                    lstCartSummary.setVisibility(View.GONE);
                    btnTabCheckout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#000000")));
                    btnTabCheckout.setTextColor(android.graphics.Color.WHITE);
                    btnTabItems.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
                    btnTabItems.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                }
            });

            btnTabItems.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    layoutTabCheckoutContainer.setVisibility(View.GONE);
                    lstCartSummary.setVisibility(View.VISIBLE);
                    btnTabItems.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#000000")));
                    btnTabItems.setTextColor(android.graphics.Color.WHITE);
                    btnTabCheckout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
                    btnTabCheckout.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                }
            });
        }

        // Checkout Button Trigger
        btnConfirmCheckout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processCheckoutOffline();
            }
        });

        recalculateCart();
    }

    private void showCartOverlay(boolean checkoutMode) {
        if (cartList.isEmpty()) {
            Toast.makeText(this, "Your shopping cart is empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.GONE);
        if (layoutHeader != null) layoutHeader.setVisibility(View.GONE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            RecyclerView list1 = findViewById(R.id.gridProducts);
            RecyclerView list2 = findViewById(R.id.lstProducts);
            android.graphics.RenderEffect blur = android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP);
            if (list1 != null && list1.getVisibility() == View.VISIBLE) list1.setRenderEffect(blur);
            if (list2 != null && list2.getVisibility() == View.VISIBLE) list2.setRenderEffect(blur);
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

        isCurrentlyCheckoutMode = true; // For confirm button logic to pass

        if (txtCartOverlayTitle != null) txtCartOverlayTitle.setText("Review Order");
        if (btnConfirmCheckout != null) {
            btnConfirmCheckout.setText("Confirm Bill");
            btnConfirmCheckout.setEnabled(true);
            btnConfirmCheckout.setVisibility(View.VISIBLE);
        }

        // Reset tabs based on checkoutMode
        LinearLayout layoutTabCheckoutContainer = findViewById(R.id.layoutTabCheckoutContainer);
        Button btnTabCheckout = findViewById(R.id.btnTabCheckout);
        Button btnTabItems = findViewById(R.id.btnTabItems);
        
        if (checkoutMode) {
            if (layoutTabCheckoutContainer != null) layoutTabCheckoutContainer.setVisibility(View.VISIBLE);
            if (lstCartSummary != null) lstCartSummary.setVisibility(View.GONE);
            if (btnTabCheckout != null) {
                btnTabCheckout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#000000")));
                btnTabCheckout.setTextColor(android.graphics.Color.WHITE);
            }
            if (btnTabItems != null) {
                btnTabItems.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
                btnTabItems.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
            }
        } else {
            if (layoutTabCheckoutContainer != null) layoutTabCheckoutContainer.setVisibility(View.GONE);
            if (lstCartSummary != null) lstCartSummary.setVisibility(View.VISIBLE);
            if (btnTabCheckout != null) {
                btnTabCheckout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
                btnTabCheckout.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
            }
            if (btnTabItems != null) {
                btnTabItems.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#000000")));
                btnTabItems.setTextColor(android.graphics.Color.WHITE);
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
            
            // Read GPS coordinates
            int latIdx = cursor.getColumnIndex("latitude");
            int lngIdx = cursor.getColumnIndex("longitude");
            c.latitude = (latIdx != -1) ? cursor.getDouble(latIdx) : 0.0;
            c.longitude = (lngIdx != -1) ? cursor.getDouble(lngIdx) : 0.0;
            
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

        // Prepend placeholder so no term is selected by default
        PaymentTermModel placeholder = new PaymentTermModel();
        placeholder.id = -1;
        placeholder.name = "-- Select Payment Term --";
        placeholder.daysDue = 0;
        paymentTermList.add(placeholder);

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
                    ((TextView) v).setTextColor(android.graphics.Color.BLACK);
                    ((TextView) v).setTextSize(14);
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(android.graphics.Color.BLACK);
                    v.setBackgroundColor(android.graphics.Color.WHITE);
                    v.setPadding(16, 16, 16, 16);
                }
                return v;
            }
        };
        termAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaymentTerm.setAdapter(termAdapter);

        // Add selection listener to Payment Term to automatically update Payment Method spinner
        spinnerPaymentTerm.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedPaymentTermPos = position;
                if (!paymentTermList.isEmpty() && position >= 0 && position < paymentTermList.size()) {
                    PaymentTermModel term = paymentTermList.get(position);
                    if (term.name != null) {
                        String nameUpper = term.name.toUpperCase();
                        if (nameUpper.contains("CREDIT")) {
                            selectOrAddPaymentMethod("Credit");
                        } else if (nameUpper.contains("CHEQUE")) {
                            selectOrAddPaymentMethod("Cheque");
                        } else {
                            if (spinnerPaymentMethod != null && spinnerPaymentMethod.getSelectedItem() != null) {
                                String currentMethod = spinnerPaymentMethod.getSelectedItem().toString();
                                if (currentMethod.equalsIgnoreCase("Credit") || currentMethod.equalsIgnoreCase("Cheque")) {
                                    selectOrAddPaymentMethod("Cash");
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void selectOrAddPaymentMethod(String method) {
        if (spinnerPaymentMethod == null || method == null || method.isEmpty()) return;
        
        for (int i = 0; i < spinnerPaymentMethod.getCount(); i++) {
            if (spinnerPaymentMethod.getItemAtPosition(i).toString().equalsIgnoreCase(method)) {
                spinnerPaymentMethod.setSelection(i);
                return;
            }
        }
        
        try {
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerPaymentMethod.getAdapter();
            if (adapter != null) {
                adapter.add(method);
                adapter.notifyDataSetChanged();
                spinnerPaymentMethod.setSelection(spinnerPaymentMethod.getCount() - 1);
            }
        } catch (Exception e) {
            android.util.Log.e("BillingActivity", "Error adding payment method to spinner: " + e.getMessage());
        }
    }

    private void loadCatalogItems(final String filter) {
        if (layoutBillingLoading != null && (productList == null || productList.isEmpty())) {
            layoutBillingLoading.setVisibility(View.VISIBLE);
        }

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                final List<ProductModel> loaded = new ArrayList<>();
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

                try {
                    Cursor cursor = db.rawQuery(query, args);
                    while (cursor.moveToNext()) {
                        ProductModel p = new ProductModel();
                        p.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                        p.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                        p.category = cursor.getString(cursor.getColumnIndexOrThrow("category_name"));
                        p.price = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("wholesale_price")));
                        p.wholesalePrice = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("wholesale_price")));
                        p.qtyOnHand = cursor.getInt(cursor.getColumnIndexOrThrow("quantity_on_hand"));
                        p.qtyReserved = cursor.getInt(cursor.getColumnIndexOrThrow("quantity_reserved"));
                        p.localImagePath = cursor.getString(cursor.getColumnIndexOrThrow("local_image_path"));
                        p.sku = cursor.getString(cursor.getColumnIndexOrThrow("sku"));
                        p.sampleCode = cursor.getString(cursor.getColumnIndexOrThrow("sample_code"));
                        p.variationsJson = cursor.getString(cursor.getColumnIndexOrThrow("variations_json"));
                        p.brand = cursor.getString(cursor.getColumnIndexOrThrow("brand"));
                        p.description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
                        loaded.add(p);
                    }
                    cursor.close();
                } catch (Exception e) {
                    android.util.Log.e("BillingActivity", "Error loading catalog items: " + e.getMessage());
                }

                // Prioritize exact matches at the top when searching
                if (filter != null && !filter.trim().isEmpty()) {
                    final String searchLower = filter.toLowerCase().trim();
                    java.util.Collections.sort(loaded, new java.util.Comparator<ProductModel>() {
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

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (BillingActivity.this.isFinishing() || BillingActivity.this.isDestroyed()) {
                            return;
                        }
                        if (layoutBillingLoading != null) {
                            layoutBillingLoading.setVisibility(View.GONE);
                        }
                        productList.clear();
                        productList.addAll(loaded);

                        if (productAdapter == null) {
                            productAdapter = new ProductAdapter();
                            lstProducts.setAdapter(productAdapter);
                        } else {
                            productAdapter.notifyDataSetChanged();
                        }

                        if (productGridAdapter == null) {
                            productGridAdapter = new ProductGridRecyclerAdapter();
                            gridProducts.setAdapter(productGridAdapter);
                        } else {
                            productGridAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        });
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

        java.math.BigDecimal customerOutstanding = (selectedCustomer != null && selectedCustomer.outstanding != null) ? selectedCustomer.outstanding : java.math.BigDecimal.ZERO;
        java.math.BigDecimal netTotalWithOutstanding = netTotal.add(customerOutstanding);

        if (txtCartItemsCount != null) {
            txtCartItemsCount.setText(String.valueOf(totalItemsCount));
        }
        if (txtCartCount != null) {
            txtCartCount.setText(String.valueOf(totalItemsCount));
        }
        txtCartSalesSum.setText(CurrencyUtils.formatLKR(netTotal));

        txtSubtotal.setText(CurrencyUtils.formatLKR(subtotal));
        txtTax.setText(CurrencyUtils.formatLKR(taxBreakdown));
        if (txtCheckoutOutstanding != null) {
            txtCheckoutOutstanding.setText(CurrencyUtils.formatLKR(customerOutstanding));
        }
        txtNetTotal.setText(CurrencyUtils.formatLKR(netTotalWithOutstanding));

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
        if (productAdapter != null) {
            productAdapter.notifyDataSetChanged();
        }
        if (productGridAdapter != null) {
            productGridAdapter.notifyDataSetChanged();
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
        if (spinnerPaymentTerm != null && selectedPaymentTermPos <= 0) {
            Toast.makeText(this, "Please select a payment term before checkout.", Toast.LENGTH_LONG).show();
            if (btnConfirmCheckout != null) {
                btnConfirmCheckout.setEnabled(true);
            }
            return;
        }
        if (btnConfirmCheckout != null) {
            btnConfirmCheckout.setEnabled(false);
        }

        showGlobalLoadingDialog("Acquiring GPS location...");
        LocationHelper.captureCurrentLocation(this, new LocationHelper.LocationResultListener() {
            @Override
            public void onLocationResult(double latitude, double longitude, boolean isFallback) {
                checkoutLatitude = latitude;
                checkoutLongitude = longitude;
                dismissGlobalLoadingDialog();
                if (isFallback) {
                    Toast.makeText(BillingActivity.this, "⚠️ GPS unavailable. Invoice location set to default.", Toast.LENGTH_LONG).show();
                }
                processDiscountPromptsAndCheckout();
            }
        });
    }

    private void processDiscountPromptsAndCheckout() {
        android.util.Log.d("BillingActivity", "=== PROCESS DISCOUNT PROMPTS & CHECKOUT STARTED ===");
        final List<DiscountCheckResult> itemDiscounts = evaluateItemDiscounts();
        final DiscountCheckResult billDiscount = evaluateBillDiscount();
        android.util.Log.d("BillingActivity", "Prompts summary: Qualified Item/Category Rules=" + itemDiscounts.size() + ", Qualified Bill Discount=" + (billDiscount != null ? billDiscount.name : "None"));
        showItemDiscountPrompts(itemDiscounts, 0, billDiscount, true);
    }

    private void checkAndPromptItemDiscountsOnAddToCart() {
        final List<DiscountCheckResult> itemDiscounts = evaluateItemDiscounts();
        if (itemDiscounts != null && !itemDiscounts.isEmpty()) {
            android.util.Log.d("BillingActivity", "Item added to cart qualified for " + itemDiscounts.size() + " promo rules. Prompting user immediately.");
            showItemDiscountPrompts(itemDiscounts, 0, null, false);
        }
    }

    private void showItemDiscountPrompts(final List<DiscountCheckResult> itemDiscounts, final int index, final DiscountCheckResult billDiscount, final boolean isFinalCheckout) {
        if (index < itemDiscounts.size()) {
            final DiscountCheckResult rule = itemDiscounts.get(index);
            android.util.Log.i("BillingActivity", "Displaying Item Discount Prompt [" + (index + 1) + "/" + itemDiscounts.size() + "]: Rule ID=" + rule.ruleId + ", Name='" + rule.name + "', Type=" + rule.ruleType + ", Reward=" + rule.rewardType);

            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);

            if ("percentage".equalsIgnoreCase(rule.rewardType)) {
                builder.setTitle("Promotional Discount Offer!");
                builder.setMessage("Your cart qualifies for a " + String.format(Locale.getDefault(), "%.1f", rule.rewardVal) + "% discount under rule \"" + rule.name + "\".\n\nWould you like to accept and apply this promotional discount?");
                builder.setPositiveButton("Accept", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        android.util.Log.i("BillingActivity", "User ACCEPTED percentage discount rule ID " + rule.ruleId);
                        for (CartItemModel item : cartList) {
                            if ("item_wise".equalsIgnoreCase(rule.ruleType) && (item.productId == rule.targetItemId || (item.variationOptionId > 0 && item.variationOptionId == rule.targetItemId))) {
                                item.discountPercent = java.math.BigDecimal.valueOf(rule.rewardVal);
                                item.isPercentDiscountActive = true;
                                android.util.Log.d("BillingActivity", " Applied " + rule.rewardVal + "% discount to item: " + item.name);
                            } else if ("category_wise".equalsIgnoreCase(rule.ruleType)) {
                                SQLiteDatabase db = dbHelper.getReadableDatabase();
                                Cursor catCheck = db.rawQuery("SELECT category_name FROM products WHERE id = ?", new String[]{String.valueOf(item.productId)});
                                if (catCheck != null) {
                                    if (catCheck.moveToFirst()) {
                                        String itemCatName = catCheck.getString(0);
                                        if (itemCatName != null && !rule.targetCategoryName.isEmpty() && itemCatName.equalsIgnoreCase(rule.targetCategoryName)) {
                                            item.discountPercent = java.math.BigDecimal.valueOf(rule.rewardVal);
                                            item.isPercentDiscountActive = true;
                                            android.util.Log.d("BillingActivity", " Applied category " + rule.rewardVal + "% discount to item: " + item.name);
                                        }
                                    }
                                    catCheck.close();
                                }
                            }
                        }
                        recalculateCart();
                        showItemDiscountPrompts(itemDiscounts, index + 1, billDiscount, isFinalCheckout);
                    }
                });
                builder.setNegativeButton("Reject", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        android.util.Log.i("BillingActivity", "User REJECTED percentage discount rule ID " + rule.ruleId);
                        dialog.dismiss();
                        showItemDiscountPrompts(itemDiscounts, index + 1, billDiscount, isFinalCheckout);
                    }
                });
            } else {
                // Default: Free Issue
                final int freeQty = (int) rule.rewardVal;
                final int addQty = rule.additionalFreeQty;

                String msg;
                if (addQty > 0 && addQty < freeQty) {
                    msg = "Your cart quantity has qualified you for a tier upgrade! You now qualify for " + freeQty + " free unit(s) of \"" + rule.targetItemName + "\" under promotional rule \"" + rule.name + "\" (an additional " + addQty + " free unit).\n\nWould you like to accept this offer?";
                    builder.setTitle("Free Issue Tier Upgrade!");
                } else {
                    msg = "You qualify for " + freeQty + " free unit(s) of \"" + rule.targetItemName + "\" under promotional rule \"" + rule.name + "\".\n\nWould you like to accept this offer?";
                    builder.setTitle("Free Issue Offer!");
                }
                builder.setMessage(msg);

                builder.setPositiveButton("Accept", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        android.util.Log.i("BillingActivity", "User ACCEPTED free issue rule ID " + rule.ruleId + " (Total target " + freeQty + " units of " + rule.targetItemName + ")");
                        
                        CartItemModel existingFreeItem = null;
                        for (CartItemModel item : cartList) {
                            boolean isMatch = (item.productId == rule.targetItemId) || (item.variationOptionId > 0 && item.variationOptionId == rule.targetItemId);
                            if (isMatch && item.customPrice.compareTo(java.math.BigDecimal.ZERO) == 0 && item.total.compareTo(java.math.BigDecimal.ZERO) == 0) {
                                existingFreeItem = item;
                                break;
                            }
                        }

                        if (existingFreeItem != null) {
                            existingFreeItem.quantity = freeQty;
                            android.util.Log.i("BillingActivity", "Updated existing free issue item quantity in cart to " + freeQty);
                        } else {
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
                            android.util.Log.i("BillingActivity", "Added new free issue item to cart with qty " + freeQty);
                        }

                        recalculateCart();
                        showItemDiscountPrompts(itemDiscounts, index + 1, billDiscount, isFinalCheckout);
                    }
                });
                builder.setNegativeButton("Reject", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        android.util.Log.i("BillingActivity", "User REJECTED free issue rule ID " + rule.ruleId);
                        dialog.dismiss();
                        showItemDiscountPrompts(itemDiscounts, index + 1, billDiscount, isFinalCheckout);
                    }
                });
            }
            builder.create().show();
        } else {
            if (isFinalCheckout) {
                showBillDiscountPrompt(billDiscount);
            }
        }
    }

    private void showBillDiscountPrompt(final DiscountCheckResult billDiscount) {
        if (billDiscount != null) {
            String currentDiscountText = edtDiscount.getText().toString().trim();
            double currentDiscount = currentDiscountText.isEmpty() ? 0.0 : Double.parseDouble(currentDiscountText);

            if (currentDiscount == 0.0) {
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
                builder.setTitle("Global Bill Discount Offer!");
                builder.setMessage("Your subtotal qualifies for a " + String.format(Locale.getDefault(), "%.1f", billDiscount.rewardVal) + "% global bill discount under \"" + billDiscount.name + "\".\n\nWould you like to accept and apply this discount to the final bill?");
                builder.setPositiveButton("Accept", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
                        for (CartItemModel item : cartList) {
                            subtotal = subtotal.add(item.total);
                        }
                        java.math.BigDecimal discountAmt = subtotal.multiply(java.math.BigDecimal.valueOf(billDiscount.rewardVal)).divide(java.math.BigDecimal.valueOf(100.0), 2, java.math.RoundingMode.HALF_UP);

                        if (billDiscount.discountCap > 0 && discountAmt.doubleValue() > billDiscount.discountCap) {
                            discountAmt = java.math.BigDecimal.valueOf(billDiscount.discountCap);
                        }

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
        if (!LocationHelper.checkAndShowLocationSettings(this)) {
            return;
        }
        showGlobalLoadingDialog("Recording invoice in local database...");
        CustomerModel customer = selectedCustomer;

        PaymentTermModel selectedTerm = null;
        int termPos = selectedPaymentTermPos;
        if (!paymentTermList.isEmpty() && termPos >= 0 && termPos < paymentTermList.size()) {
            selectedTerm = paymentTermList.get(termPos);
        }

        // Fix B-06: Read payment method from spinner instead of hardcoding "Term"
        String payment = "Cash"; // safe default
        try {
            if (spinnerPaymentMethod != null && spinnerPaymentMethod.getSelectedItem() != null) {
                payment = spinnerPaymentMethod.getSelectedItem().toString();
            }
        } catch (Exception e) {
            android.util.Log.w("BillingActivity", "Could not read payment method from spinner, defaulting to Cash: " + e.getMessage());
        }

        // Override payment method to the exact payment term name if credit or cheque based
        if (selectedTerm != null && selectedTerm.name != null && selectedTerm.id != -1) {
            String termNameUpper = selectedTerm.name.toUpperCase();
            if (termNameUpper.contains("CREDIT") || termNameUpper.contains("CHEQUE")) {
                payment = selectedTerm.name;
            }
        }

        String discountStr = edtDiscount.getText().toString().trim();
        java.math.BigDecimal discount = CurrencyUtils.toBigDecimal(discountStr);

        String discountType = isGlobalDiscountPercent ? "%" : "Rs";
        double discountRate = 0.0;
        if (isGlobalDiscountPercent) {
            String pctStr = edtDirectDiscountPct.getText().toString().trim();
            if (!pctStr.isEmpty()) {
                discountRate = CurrencyUtils.toBigDecimal(pctStr).doubleValue();
            }
        } else {
            if (!discountStr.isEmpty()) {
                discountRate = discount.doubleValue();
            }
        }

        final List<CartItemModel> snapshotItems = new ArrayList<>(cartList);
        final String snapshotPayment = payment;
        final java.math.BigDecimal snapshotDiscount = discount;

        java.math.BigDecimal cartSubtotal = java.math.BigDecimal.ZERO;
        for (CartItemModel itm : cartList) {
            cartSubtotal = cartSubtotal.add(itm.total != null ? itm.total : java.math.BigDecimal.ZERO);
        }
        final java.math.BigDecimal snapshotSubtotal = cartSubtotal;

        CheckoutService.performCheckout(
            this,
            dbHelper,
            editInvoiceId,
            customer,
            currentRouteLocalId,
            cartList,
            payment,
            selectedTerm,
            discount,
            discountType,
            discountRate,
            checkoutLatitude,
            checkoutLongitude,
            new CheckoutService.CheckoutListener() {
                @Override
                public void onCheckoutSuccess(String invoiceNum, String customerName, String phone, java.math.BigDecimal netTotal) {
                    dismissGlobalLoadingDialog();
                    clearDraftBill();
                    editInvoiceId = -1; // Reset edit mode
                    showShareBillDialog(invoiceNum, customerName, phone, snapshotSubtotal, snapshotDiscount, netTotal, snapshotPayment, snapshotItems);
                    
                    // Trigger instant background sync for Real-Time Stock
                    try {
                        int uid = SecurePreferences.getSessionPrefs(BillingActivity.this).getInt("user_id", 0);
                        if (uid > 0) {
                            SyncManager.getInstance(BillingActivity.this).enqueueInstantPushSync();
                        }
                    } catch (Exception e) {
                        android.util.Log.e("BillingActivity", "Failed to trigger instant sync: " + e.getMessage());
                    }
                }

                @Override
                public void onCheckoutFailed(String message) {
                    dismissGlobalLoadingDialog();
                    if (btnConfirmCheckout != null) {
                        btnConfirmCheckout.setEnabled(true);
                    }
                    Toast.makeText(BillingActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        );
    }

    private boolean isRuleDateActive(String startDate, String endDate) {
        if ((startDate == null || startDate.isEmpty() || startDate.equalsIgnoreCase("null")) &&
            (endDate == null || endDate.isEmpty() || endDate.equalsIgnoreCase("null"))) {
            return true;
        }
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String todayStr = sdf.format(new java.util.Date());
            java.util.Date today = sdf.parse(todayStr);
            if (startDate != null && !startDate.isEmpty() && !startDate.equalsIgnoreCase("null")) {
                java.util.Date start = sdf.parse(startDate);
                if (today.before(start)) return false;
            }
            if (endDate != null && !endDate.isEmpty() && !endDate.equalsIgnoreCase("null")) {
                java.util.Date end = sdf.parse(endDate);
                if (today.after(end)) return false;
            }
        } catch (Exception e) {
            // Ignore date parsing issues
        }
        return true;
    }

    private List<DiscountCheckResult> evaluateItemDiscounts() {
        List<DiscountCheckResult> qualified = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            android.util.Log.d("BillingActivity", "=== START EVALUATING ITEM DISCOUNTS ===");
            android.util.Log.d("BillingActivity", "Cart item count: " + cartList.size());
            for (CartItemModel ci : cartList) {
                android.util.Log.d("BillingActivity", " Cart Item: productId=" + ci.productId + ", name='" + ci.name + "', variation='" + ci.selectedVariation + "', varOptionId=" + ci.variationOptionId + ", qty=" + ci.quantity + ", price=" + ci.customPrice + ", total=" + ci.total);
            }

            cursor = db.rawQuery(
                "SELECT r.id, r.name, r.rule_type, COALESCE(r.reward_type, 'free_issue'), r.target_item_id, p.name as target_item_name, " +
                "r.target_category_id, r.start_date, r.end_date, r.discount_cap, " +
                "t.min_threshold, COALESCE(t.max_threshold, 0.0), t.reward_val " +
                "FROM discount_rules r " +
                "JOIN discount_rule_tiers t ON r.id = t.rule_id " +
                "LEFT JOIN products p ON r.target_item_id = p.id " +
                "WHERE r.status = 'Active' AND r.rule_type IN ('item_wise', 'category_wise') " +
                "ORDER BY r.id ASC, t.min_threshold DESC", null
            );

            android.util.Log.d("BillingActivity", "Active discount rules count in DB: " + (cursor != null ? cursor.getCount() : 0));

            List<Integer> processedRules = new ArrayList<>();
            while (cursor.moveToNext()) {
                int ruleId = cursor.getInt(0);
                if (processedRules.contains(ruleId)) {
                    continue;
                }

                String name = cursor.getString(1);
                String ruleType = cursor.getString(2);
                String rewardType = cursor.getString(3);
                int targetItemId = cursor.getInt(4);
                String targetItemName = cursor.getString(5);
                int targetCatId = cursor.getInt(6);
                String startDate = cursor.getString(7);
                String endDate = cursor.getString(8);
                double discountCap = cursor.getDouble(9);
                double minThresh = cursor.getDouble(10);
                double maxThresh = cursor.getDouble(11);
                double rewardVal = cursor.getDouble(12);

                android.util.Log.d("BillingActivity", "Evaluating Rule ID " + ruleId + " ('" + name + "'): type=" + ruleType + ", reward=" + rewardType + ", targetItemId=" + targetItemId + ", targetCatId=" + targetCatId + ", minThresh=" + minThresh + ", maxThresh=" + maxThresh + ", start=" + startDate + ", end=" + endDate);

                if (!isRuleDateActive(startDate, endDate)) {
                    android.util.Log.d("BillingActivity", " Rule ID " + ruleId + " SKIPPED: Date range inactive (Start: " + startDate + ", End: " + endDate + ")");
                    continue;
                }

                double matchingCartQty = 0.0;
                double existingFreeQty = 0.0;

                if ("item_wise".equalsIgnoreCase(ruleType)) {
                    for (CartItemModel item : cartList) {
                        boolean isMatch = (item.productId == targetItemId) || (item.variationOptionId > 0 && item.variationOptionId == targetItemId);
                        android.util.Log.d("BillingActivity", "  Item Check: cartProductId=" + item.productId + ", varOptionId=" + item.variationOptionId + " vs targetItemId=" + targetItemId + " -> match=" + isMatch);
                        if (isMatch) {
                            if (item.customPrice.compareTo(java.math.BigDecimal.ZERO) == 0 && item.total.compareTo(java.math.BigDecimal.ZERO) == 0) {
                                existingFreeQty += item.quantity;
                                android.util.Log.d("BillingActivity", "  Rule ID " + ruleId + ": Found existing free issue qty=" + item.quantity + " in cart.");
                            } else {
                                matchingCartQty += item.quantity;
                            }
                        }
                    }
                } else if ("category_wise".equalsIgnoreCase(ruleType)) {
                    String targetCatName = "";
                    if (targetCatId > 0) {
                        try {
                            Cursor cCursor = db.rawQuery("SELECT name FROM categories WHERE id = ?", new String[]{String.valueOf(targetCatId)});
                            if (cCursor != null) {
                                if (cCursor.moveToFirst()) {
                                    targetCatName = cCursor.getString(0);
                                }
                                cCursor.close();
                            }
                        } catch (Exception ignored) {}
                    }

                    for (CartItemModel item : cartList) {
                        Cursor catCheck = db.rawQuery("SELECT category_name FROM products WHERE id = ?", new String[]{String.valueOf(item.productId)});
                        if (catCheck != null) {
                            if (catCheck.moveToFirst()) {
                                String itemCatName = catCheck.getString(0);
                                if (itemCatName != null && !targetCatName.isEmpty() && itemCatName.equalsIgnoreCase(targetCatName)) {
                                    if (item.customPrice.compareTo(java.math.BigDecimal.ZERO) == 0 && item.total.compareTo(java.math.BigDecimal.ZERO) == 0) {
                                        existingFreeQty += item.quantity;
                                    } else {
                                        matchingCartQty += item.quantity;
                                    }
                                }
                            }
                            catCheck.close();
                        }
                    }
                }

                boolean isQualified = false;
                int targetFreeQty = 0;
                int additionalFreeQty = 0;

                if ("free_issue".equalsIgnoreCase(rewardType)) {
                    targetFreeQty = (int) rewardVal;
                    additionalFreeQty = targetFreeQty - (int) existingFreeQty;
                    if (matchingCartQty >= minThresh && (maxThresh <= 0 || matchingCartQty <= maxThresh) && additionalFreeQty > 0) {
                        isQualified = true;
                    }
                } else {
                    boolean alreadyHasPercent = false;
                    for (CartItemModel item : cartList) {
                        if (item.isPercentDiscountActive) {
                            alreadyHasPercent = true;
                            break;
                        }
                    }
                    if (matchingCartQty >= minThresh && (maxThresh <= 0 || matchingCartQty <= maxThresh) && !alreadyHasPercent) {
                        isQualified = true;
                    }
                }

                android.util.Log.d("BillingActivity", " Rule ID " + ruleId + " Result: matchingCartQty=" + matchingCartQty + " (Min: " + minThresh + ", Max: " + maxThresh + "), rewardVal=" + rewardVal + ", existingFreeQty=" + existingFreeQty + ", additionalFreeQty=" + additionalFreeQty + " -> Qualified=" + isQualified);

                if (isQualified) {
                    processedRules.add(ruleId);
                    DiscountCheckResult res = new DiscountCheckResult();
                    res.ruleId = ruleId;
                    res.name = name;
                    res.ruleType = ruleType;
                    res.rewardType = rewardType;
                    res.targetItemId = targetItemId;
                    res.targetItemName = (targetItemName != null && !targetItemName.isEmpty()) ? targetItemName : "Product";
                    res.targetCategoryId = targetCatId;
                    res.targetCategoryName = "Category";
                    res.minThreshold = minThresh;
                    res.maxThreshold = maxThresh;
                    res.rewardVal = rewardVal;
                    res.additionalFreeQty = additionalFreeQty;
                    res.discountCap = discountCap;
                    qualified.add(res);
                    android.util.Log.i("BillingActivity", " Rule ID " + ruleId + " ('" + name + "') QUALIFIED! Target Free=" + targetFreeQty + " (Additional: " + additionalFreeQty + ")");
                } else {
                    android.util.Log.d("BillingActivity", " Rule ID " + ruleId + " DISQUALIFIED.");
                }
            }
        } catch (Exception e) {
            android.util.Log.e("BillingActivity", "Error evaluating item/category discounts", e);
        } finally {
            if (cursor != null) cursor.close();
            android.util.Log.d("BillingActivity", "=== END EVALUATING ITEM DISCOUNTS: " + qualified.size() + " rules qualified ===");
        }
        return qualified;
    }

    private DiscountCheckResult evaluateBillDiscount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
            for (CartItemModel item : cartList) {
                if (item.customPrice.compareTo(java.math.BigDecimal.ZERO) > 0 || item.total.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    subtotal = subtotal.add(item.total);
                }
            }
            android.util.Log.d("BillingActivity", "=== EVALUATING BILL DISCOUNTS === Subtotal=" + subtotal);

            cursor = db.rawQuery(
                "SELECT r.id, r.name, r.rule_type, COALESCE(r.reward_type, 'percentage'), r.start_date, r.end_date, r.discount_cap, " +
                "t.min_threshold, COALESCE(t.max_threshold, 0.0), t.reward_val " +
                "FROM discount_rules r " +
                "JOIN discount_rule_tiers t ON r.id = t.rule_id " +
                "WHERE r.status = 'Active' AND r.rule_type = 'bill_wise' " +
                "ORDER BY t.min_threshold DESC", null
            );

            while (cursor.moveToNext()) {
                String startDate = cursor.getString(4);
                String endDate = cursor.getString(5);
                if (!isRuleDateActive(startDate, endDate)) {
                    continue;
                }

                double minThresh = cursor.getDouble(7);
                double maxThresh = cursor.getDouble(8);
                double rewardVal = cursor.getDouble(9);
                double discountCap = cursor.getDouble(6);

                if (subtotal.doubleValue() >= minThresh && (maxThresh <= 0 || subtotal.doubleValue() <= maxThresh)) {
                    DiscountCheckResult res = new DiscountCheckResult();
                    res.ruleId = cursor.getInt(0);
                    res.name = cursor.getString(1);
                    res.ruleType = cursor.getString(2);
                    res.rewardType = cursor.getString(3);
                    res.minThreshold = minThresh;
                    res.maxThreshold = maxThresh;
                    res.rewardVal = rewardVal;
                    res.discountCap = discountCap;
                    android.util.Log.i("BillingActivity", "Bill Discount QUALIFIED: " + res.name + " (" + rewardVal + "%)");
                    return res;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("BillingActivity", "Error evaluating bill discounts", e);
        } finally {
            if (cursor != null) cursor.close();
            android.util.Log.d("BillingActivity", "=== END EVALUATING BILL DISCOUNTS ===");
        }
        return null;
    }

    private void showShareBillDialog(
            final String invoiceNum,
            final String customerName,
            final String customerPhone,
            final java.math.BigDecimal subtotal,
            final java.math.BigDecimal discount,
            final java.math.BigDecimal grandTotal,
            final String paymentMethod,
            final List<CartItemModel> items
    ) {
        dismissGlobalLoadingDialog();
        final Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar);
        dialog.setCancelable(false);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_checkout_success, null);
        dialog.setContentView(dialogView);

        // Bind Views
        TextView txtInvoice = dialogView.findViewById(R.id.txtDialogSuccessInvoice);
        TextView txtCustomer = dialogView.findViewById(R.id.txtDialogCustomerName);
        TextView txtPhone = dialogView.findViewById(R.id.txtDialogCustomerPhone);
        TextView txtTotal = dialogView.findViewById(R.id.txtDialogGrandTotal);
        ImageView imgQr = dialogView.findViewById(R.id.imgDialogQr);
        View btnWhatsApp = dialogView.findViewById(R.id.btnDialogWhatsApp);
        View btnDone = dialogView.findViewById(R.id.btnDialogDone);

        if (txtInvoice != null) txtInvoice.setText(invoiceNum);
        if (txtCustomer != null) txtCustomer.setText(customerName != null ? customerName : "N/A");
        if (txtPhone != null) txtPhone.setText(customerPhone != null && !customerPhone.isEmpty() ? customerPhone : "N/A");
        if (txtTotal != null) txtTotal.setText(CurrencyUtils.formatLKR(grandTotal));

        // Construct Self-Contained Encoded Digital Invoice URL for QR and WhatsApp Sharing
        String constructedUrl = "https://curtiss.suzxlabs.com/sales/show/" + invoiceNum;
        try {
            org.json.JSONObject payload = new org.json.JSONObject();
            payload.put("inv", invoiceNum);
            payload.put("cust", customerName != null ? customerName : "Customer");
            payload.put("phone", customerPhone != null ? customerPhone : "");
            payload.put("date", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date()));
            payload.put("pay", paymentMethod != null ? paymentMethod : "Cash");
            android.content.SharedPreferences sessionPrefs = SecurePreferences.getSessionPrefs(BillingActivity.this);
            String rFirst = sessionPrefs.getString("first_name", "");
            String rLast = sessionPrefs.getString("last_name", "");
            String rName = (rFirst + " " + rLast).trim();
            if (rName.isEmpty()) {
                rName = sessionPrefs.getString("username", "");
            }
            String rPhone = sessionPrefs.getString("phone", "");
            if (rPhone.isEmpty()) {
                rPhone = sessionPrefs.getString("mobile", "");
            }
            payload.put("rep", rName);
            payload.put("rep_phone", rPhone);
            payload.put("sub", subtotal != null ? subtotal.doubleValue() : (grandTotal != null ? grandTotal.doubleValue() : 0.0));
            payload.put("disc", discount != null ? discount.doubleValue() : 0.0);
            payload.put("total", grandTotal != null ? grandTotal.doubleValue() : 0.0);

            org.json.JSONArray itemsArr = new org.json.JSONArray();
            if (items != null) {
                for (CartItemModel it : items) {
                    org.json.JSONObject itemObj = new org.json.JSONObject();
                    itemObj.put("n", it.name != null ? it.name : "Product");
                    itemObj.put("q", it.quantity);
                    double p = it.customPrice != null ? it.customPrice.doubleValue() : (it.price != null ? it.price.doubleValue() : 0.0);
                    itemObj.put("p", p);
                    itemObj.put("t", it.total != null ? it.total.doubleValue() : (p * it.quantity));
                    itemsArr.put(itemObj);
                }
            }
            payload.put("items", itemsArr);

            String jsonString = payload.toString();
            String encodedData = android.util.Base64.encodeToString(jsonString.getBytes("UTF-8"), android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
            constructedUrl = "https://curtiss.suzxlabs.com/sales/show/" + invoiceNum + "?data=" + Uri.encode(encodedData);
        } catch (Exception e) {
            android.util.Log.e("BillingActivity", "Error building digital invoice payload", e);
        }
        final String digitalInvoiceUrl = constructedUrl;

        if (btnWhatsApp != null) {
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

                    StringBuilder msg = new StringBuilder();
                    msg.append("Dear ").append(customerName != null ? customerName : "Customer").append(",\n\n");
                    msg.append("Thank you for your business with Curtiss!\n");
                    msg.append("📄 *Invoice No:* ").append(invoiceNum).append("\n");
                    msg.append("💰 *Total Amount:* ").append(CurrencyUtils.formatLKR(grandTotal != null ? grandTotal : java.math.BigDecimal.ZERO)).append("\n\n");
                    msg.append("🌐 *View Your Digital Invoice:*\n").append(digitalInvoiceUrl).append("\n\n");
                    msg.append("Thank you!");

                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        String waUrl = "https://api.whatsapp.com/send?phone=" + cleanPhone + "&text=" + Uri.encode(msg.toString());
                        intent.setData(Uri.parse(waUrl));
                        intent.setPackage("com.whatsapp");
                        startActivity(intent);
                    } catch (Exception e) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            String waUrl = "https://api.whatsapp.com/send?phone=" + cleanPhone + "&text=" + Uri.encode(msg.toString());
                            intent.setData(Uri.parse(waUrl));
                            startActivity(intent);
                        } catch (Exception ex) {
                            Toast.makeText(BillingActivity.this, "WhatsApp is not installed on this device.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }

        if (btnDone != null) {
            btnDone.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    finish();
                }
            });
        }

        // Set layout params for Dialog window with transparent background for rounded corners
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();
        if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.GONE);
        if (layoutHeader != null) layoutHeader.setVisibility(View.GONE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            RecyclerView list1 = findViewById(R.id.gridProducts);
            RecyclerView list2 = findViewById(R.id.lstProducts);
            android.graphics.RenderEffect blur = android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP);
            if (list1 != null && list1.getVisibility() == View.VISIBLE) list1.setRenderEffect(blur);
            if (list2 != null && list2.getVisibility() == View.VISIBLE) list2.setRenderEffect(blur);
        }
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                if (layoutCartOverlay == null || layoutCartOverlay.getVisibility() != View.VISIBLE) {
                    if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.VISIBLE);
                    if (layoutHeader != null) layoutHeader.setVisibility(View.VISIBLE);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        RecyclerView list1 = findViewById(R.id.gridProducts);
                        RecyclerView list2 = findViewById(R.id.lstProducts);
                        if (list1 != null) list1.setRenderEffect(null);
                        if (list2 != null) list2.setRenderEffect(null);
                    }
                }
            }
        });

        // Load QR Code in background thread with encoded payload URL
        if (imgQr != null) {
            String qrApiUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + Uri.encode(digitalInvoiceUrl);
            loadQrCode(qrApiUrl, imgQr);
        }
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

    private void loadInvoiceForEditing(long editId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM invoices WHERE id = ?", new String[]{String.valueOf(editId)});
        if (c.moveToFirst()) {
            long customerId = c.getLong(c.getColumnIndexOrThrow("customer_id"));
            String paymentMethod = c.getString(c.getColumnIndexOrThrow("payment_method"));
            double discount = c.getDouble(c.getColumnIndexOrThrow("discount"));
            long paymentTermId = c.isNull(c.getColumnIndexOrThrow("payment_term_id")) ? -1 : c.getLong(c.getColumnIndexOrThrow("payment_term_id"));

            // Resolve Customer
            selectedCustomer = null;
            Cursor custCursor = db.rawQuery("SELECT * FROM customers WHERE id = ? OR (server_id = ? AND server_id > 0)", new String[]{String.valueOf(customerId), String.valueOf(customerId)});
            if (custCursor.moveToFirst()) {
                CustomerModel cust = new CustomerModel();
                cust.id = custCursor.getInt(custCursor.getColumnIndexOrThrow("id"));
                cust.serverId = custCursor.getInt(custCursor.getColumnIndexOrThrow("server_id"));
                cust.name = DatabaseHelper.safeGetString(custCursor, "name", "");
                cust.phone = DatabaseHelper.safeGetString(custCursor, "phone", "");
                cust.whatsapp = DatabaseHelper.safeGetString(custCursor, "whatsapp", "");
                cust.email = DatabaseHelper.safeGetString(custCursor, "email", "");
                cust.address = DatabaseHelper.safeGetString(custCursor, "address", "");
                cust.territory = DatabaseHelper.safeGetString(custCursor, "territory", "");
                cust.outstanding = CurrencyUtils.toBigDecimal(custCursor.getDouble(custCursor.getColumnIndexOrThrow("outstanding")));
                cust.mcaId = custCursor.getInt(custCursor.getColumnIndexOrThrow("mca_id"));
                cust.mcaName = DatabaseHelper.safeGetString(custCursor, "mca_name", "");
                cust.creditLimit = CurrencyUtils.toBigDecimal(custCursor.getDouble(custCursor.getColumnIndexOrThrow("credit_limit")));
                cust.customerType = DatabaseHelper.safeGetString(custCursor, "customer_type", "");
                cust.notes = DatabaseHelper.safeGetString(custCursor, "notes", "");
                int latIdx = custCursor.getColumnIndex("latitude");
                int lngIdx = custCursor.getColumnIndex("longitude");
                cust.latitude = (latIdx != -1) ? custCursor.getDouble(latIdx) : 0.0;
                cust.longitude = (lngIdx != -1) ? custCursor.getDouble(lngIdx) : 0.0;
                selectedCustomer = cust;
            }
            custCursor.close();

            if (selectedCustomer != null) {
                String displayName = selectedCustomer.name;
                String balText = "Outstanding: LKR " + String.format(Locale.getDefault(), "%,.2f", selectedCustomer.outstanding.doubleValue());

                if (txtOverlayCustomerName != null) txtOverlayCustomerName.setText(displayName);
                if (txtOverlayCustomerBalance != null) {
                    txtOverlayCustomerBalance.setText(String.format(Locale.getDefault(), "Outstanding: LKR %.2f | Limit: LKR %.2f", selectedCustomer.outstanding.doubleValue(), selectedCustomer.creditLimit.doubleValue()));
                }
                if (txtCheckoutCustomerName != null) txtCheckoutCustomerName.setText(displayName);

                if (txtSelectedCustomerName != null) {
                    String displayNameFull = selectedCustomer.name + "\nOutstanding: LKR " + String.format(Locale.getDefault(), "%,.2f", selectedCustomer.outstanding.doubleValue());
                    txtSelectedCustomerName.setText(displayNameFull);
                }
            }

            // Select Payment Method
            if (paymentMethod != null && !paymentMethod.isEmpty()) {
                selectOrAddPaymentMethod(paymentMethod);
            }

            // Select Payment Term
            if (spinnerPaymentTerm != null && paymentTermId != -1) {
                for (int i = 0; i < paymentTermList.size(); i++) {
                    if (paymentTermList.get(i).id == paymentTermId) {
                        selectedPaymentTermPos = i; if (!paymentTermList.isEmpty() && i < paymentTermList.size()) spinnerPaymentTerm.setSelection(i);
                        break;
                    }
                }
            }

            // Set Discount
            String discountType = DatabaseHelper.safeGetString(c, "discount_type", "Rs");
            double discountRate = DatabaseHelper.safeGetDouble(c, "discount_rate", 0.0);

            if ("%".equals(discountType)) {
                isGlobalDiscountPercent = true;
                edtDiscount.setText("");
                edtDirectDiscountPct.setText(String.format(Locale.getDefault(), "%.1f", discountRate));
            } else {
                isGlobalDiscountPercent = false;
                edtDirectDiscountPct.setText("");
                if (discountRate > 0) {
                    edtDiscount.setText(String.format(Locale.getDefault(), "%.2f", discountRate));
                } else if (discount > 0) {
                    edtDiscount.setText(String.format(Locale.getDefault(), "%.2f", discount));
                } else {
                    edtDiscount.setText("");
                }
            }

            // Load Invoice Items into cartList
            cartList.clear();
            Cursor itemCursor = db.rawQuery("SELECT * FROM invoice_items WHERE invoice_id = ?", new String[]{String.valueOf(editId)});
            while (itemCursor.moveToNext()) {
                CartItemModel item = new CartItemModel();
                item.productId = itemCursor.getInt(itemCursor.getColumnIndexOrThrow("product_id"));
                item.name = itemCursor.getString(itemCursor.getColumnIndexOrThrow("product_name"));
                item.quantity = itemCursor.getInt(itemCursor.getColumnIndexOrThrow("quantity"));
                double unitPrice = itemCursor.getDouble(itemCursor.getColumnIndexOrThrow("unit_price"));
                double discVal = itemCursor.getDouble(itemCursor.getColumnIndexOrThrow("discount_val"));
                double total = itemCursor.getDouble(itemCursor.getColumnIndexOrThrow("total"));

                item.price = java.math.BigDecimal.valueOf(unitPrice);
                item.wholesalePrice = java.math.BigDecimal.valueOf(unitPrice);
                item.activePrice = java.math.BigDecimal.valueOf(unitPrice);
                item.customPrice = java.math.BigDecimal.valueOf(unitPrice);

                String discType = DatabaseHelper.safeGetString(itemCursor, "discount_type", "Rs");
                double itemDiscRate = DatabaseHelper.safeGetDouble(itemCursor, "discount_rate", 0.0);
                if ("%".equals(discType)) {
                    item.isPercentDiscountActive = true;
                    item.discountPercent = java.math.BigDecimal.valueOf(itemDiscRate);
                    item.discountAmount = java.math.BigDecimal.ZERO;
                } else {
                    item.isPercentDiscountActive = false;
                    item.discountPercent = java.math.BigDecimal.ZERO;
                    item.discountAmount = java.math.BigDecimal.valueOf(itemDiscRate);
                }
                item.discountVal = java.math.BigDecimal.valueOf(discVal);
                item.total = java.math.BigDecimal.valueOf(total);
                item.selectedVariation = DatabaseHelper.safeGetString(itemCursor, "selected_variation", "");
                item.variationOptionId = DatabaseHelper.safeGetInt(itemCursor, "variation_option_id", 0);

                cartList.add(item);
            }
            itemCursor.close();

            setupCartSummary();
        }
        c.close();
    }

    private void saveDraftBill() {
        if (editInvoiceId != -1 || isResumingDraft || isShowingResumeDialog) return; // Do not save drafts for edit mode, during resume, or while waiting for resume decision
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
                draft.put("payment_term_pos", selectedPaymentTermPos);
            }
            // Fix B-05: persist payment method selection so it is restored on draft resume
            if (spinnerPaymentMethod != null) {
                draft.put("payment_method_pos", spinnerPaymentMethod.getSelectedItemPosition());
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
                itemObj.put("variation_option_id", item.variationOptionId);
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
        isResumingDraft = true;
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("billing_draft_prefs", MODE_PRIVATE);
            String jsonStr = prefs.getString("draft_json", null);
            if (jsonStr == null) return;

            org.json.JSONObject draft = new org.json.JSONObject(jsonStr);

            // 1. Restore customer
            if (draft.has("customer_id")) {
                int custId = draft.getInt("customer_id");
                if (customerList.isEmpty()) {
                    loadCustomers();
                }
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
                selectedPaymentTermPos = termPos; if (!paymentTermList.isEmpty() && termPos < paymentTermList.size()) spinnerPaymentTerm.setSelection(termPos);
            }

            // Fix B-05: Restore payment method spinner selection
            int methodPos = draft.optInt("payment_method_pos", 0);
            if (spinnerPaymentMethod != null && methodPos >= 0 && methodPos < spinnerPaymentMethod.getCount()) {
                spinnerPaymentMethod.setSelection(methodPos);
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
                int varOptId = itemObj.optInt("variation_option_id", 0);
                String notes = itemObj.optString("notes", "");

                Cursor cursor = db.rawQuery("SELECT * FROM products WHERE id = ?", new String[]{String.valueOf(prodId)});
                if (cursor.moveToFirst()) {
                    CartItemModel item = new CartItemModel();
                    item.productId = prodId;
                    item.quantity = qty;
                    String pName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    if (selVar != null && !selVar.isEmpty()) {
                        item.name = pName + " - " + selVar;
                    } else {
                        item.name = pName;
                    }
                    item.price = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("wholesale_price")));
                    item.wholesalePrice = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("wholesale_price")));
                    item.customPrice = customPrice;
                    item.discountPercent = discPercent;
                    item.discountAmount = discAmount;
                    item.discountVal = discValItem;
                    item.isPercentDiscountActive = isPercentActive;
                    item.selectedVariation = selVar;
                    item.variationOptionId = varOptId;
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
        } finally {
            isResumingDraft = false;
        }
    }

    private static class ZoomImageView extends androidx.appcompat.widget.AppCompatImageView {
        private float mScaleFactor = 1.0f;
        private float mPosX = 0f;
        private float mPosY = 0f;
        private float mLastTouchX;
        private float mLastTouchY;
        private int mActivePointerId = android.view.MotionEvent.INVALID_POINTER_ID;
        private android.view.ScaleGestureDetector mScaleDetector;
        private android.view.GestureDetector mGestureDetector;

        public ZoomImageView(android.content.Context context) {
            super(context);
            mScaleDetector = new android.view.ScaleGestureDetector(context, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(android.view.ScaleGestureDetector detector) {
                    mScaleFactor *= detector.getScaleFactor();
                    mScaleFactor = Math.max(1.0f, Math.min(mScaleFactor, 5.0f));
                    if (mScaleFactor == 1.0f) {
                        mPosX = 0f;
                        mPosY = 0f;
                    }
                    applyTransform();
                    return true;
                }
            });

            mGestureDetector = new android.view.GestureDetector(context, new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDoubleTap(android.view.MotionEvent e) {
                    if (mScaleFactor > 1.0f) {
                        mScaleFactor = 1.0f;
                        mPosX = 0f;
                        mPosY = 0f;
                    } else {
                        mScaleFactor = 2.5f;
                    }
                    applyTransform();
                    return true;
                }
            });
        }

        private void applyTransform() {
            setScaleX(mScaleFactor);
            setScaleY(mScaleFactor);
            setTranslationX(mPosX);
            setTranslationY(mPosY);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent ev) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            mScaleDetector.onTouchEvent(ev);
            mGestureDetector.onTouchEvent(ev);

            final int action = ev.getActionMasked();
            switch (action) {
                case android.view.MotionEvent.ACTION_DOWN: {
                    final int pointerIndex = ev.getActionIndex();
                    final float x = ev.getX(pointerIndex);
                    final float y = ev.getY(pointerIndex);
                    mLastTouchX = x;
                    mLastTouchY = y;
                    mActivePointerId = ev.getPointerId(0);
                    break;
                }
                case android.view.MotionEvent.ACTION_MOVE: {
                    final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                    if (pointerIndex != -1 && mScaleFactor > 1.0f) {
                        final float x = ev.getX(pointerIndex);
                        final float y = ev.getY(pointerIndex);
                        final float dx = x - mLastTouchX;
                        final float dy = y - mLastTouchY;
                        mPosX += dx;
                        mPosY += dy;
                        applyTransform();
                        mLastTouchX = x;
                        mLastTouchY = y;
                    }
                    break;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: {
                    mActivePointerId = android.view.MotionEvent.INVALID_POINTER_ID;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    break;
                }
                case android.view.MotionEvent.ACTION_POINTER_UP: {
                    final int pointerIndex = ev.getActionIndex();
                    final int pointerId = ev.getPointerId(pointerIndex);
                    if (pointerId == mActivePointerId) {
                        final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                        mLastTouchX = ev.getX(newPointerIndex);
                        mLastTouchY = ev.getY(newPointerIndex);
                        mActivePointerId = ev.getPointerId(newPointerIndex);
                    }
                    break;
                }
            }
            return true;
        }
    }

    private void showProductQuickViewDialog(final ProductModel p) {
        final androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setBackgroundColor(android.graphics.Color.WHITE);
        scrollView.setVerticalScrollBarEnabled(false);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.WHITE);

        final float dp = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int dialogWidth = (int) (screenW * 0.92f);

        // 1. HERO IMAGE (edge-to-edge, clips top corners to match dialog radius)
        android.widget.FrameLayout heroFrame = new android.widget.FrameLayout(this);
        int heroSize = dialogWidth;
        int maxHeroH = (int) (screenH * 0.48f);
        if (heroSize > maxHeroH) heroSize = maxHeroH;
        android.widget.LinearLayout.LayoutParams heroLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, heroSize);
        heroFrame.setLayoutParams(heroLp);
        android.graphics.drawable.GradientDrawable heroClip = new android.graphics.drawable.GradientDrawable();
        heroClip.setColor(android.graphics.Color.parseColor("#F2F2F7"));
        heroClip.setCornerRadii(new float[]{28*dp, 28*dp, 28*dp, 28*dp, 0, 0, 0, 0});
        heroFrame.setBackground(heroClip);
        heroFrame.setClipToOutline(true);

        final ZoomImageView zoomImage = new ZoomImageView(this);
        android.widget.FrameLayout.LayoutParams zoomLp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        zoomImage.setLayoutParams(zoomLp);
        zoomImage.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
            android.graphics.Bitmap bm = getCachedThumbnail(p.localImagePath, 1);
            if (bm != null) zoomImage.setImageBitmap(bm);
            else zoomImage.setImageResource(android.R.drawable.ic_menu_gallery);
        } else {
            zoomImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        heroFrame.addView(zoomImage);

        // Floating close button top-right
        android.widget.TextView btnClose = new android.widget.TextView(this);
        btnClose.setText("✕");
        btnClose.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        btnClose.setTextSize(16);
        btnClose.setTypeface(null, android.graphics.Typeface.BOLD);
        btnClose.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable closeBg = new android.graphics.drawable.GradientDrawable();
        closeBg.setColor(android.graphics.Color.parseColor("#F0F0F0"));
        closeBg.setAlpha(220);
        closeBg.setCornerRadius(99 * dp);
        btnClose.setBackground(closeBg);
        android.widget.FrameLayout.LayoutParams closeLp = new android.widget.FrameLayout.LayoutParams(
                (int)(36*dp), (int)(36*dp));
        closeLp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        closeLp.setMargins(0, (int)(12*dp), (int)(12*dp), 0);
        btnClose.setLayoutParams(closeLp);
        heroFrame.addView(btnClose);

        root.addView(heroFrame);

        // 2. PADDED CONTENT SECTION
        android.widget.LinearLayout contentPad = new android.widget.LinearLayout(this);
        contentPad.setOrientation(android.widget.LinearLayout.VERTICAL);
        contentPad.setPadding((int)(20*dp),(int)(16*dp),(int)(20*dp),(int)(16*dp));
        contentPad.setBackgroundColor(android.graphics.Color.WHITE);

        // 2a. Category pill + Brand (left) | SKU + Sample Code (right)
        android.widget.LinearLayout metaRow = new android.widget.LinearLayout(this);
        metaRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        metaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.LinearLayout.LayoutParams metaLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.setMargins(0, 0, 0, (int)(8*dp));
        metaRow.setLayoutParams(metaLp);

        android.widget.TextView catPill = new android.widget.TextView(this);
        catPill.setText(p.category != null ? p.category.toUpperCase(Locale.getDefault()) : "PRODUCT");
        catPill.setTextColor(android.graphics.Color.parseColor("#636366"));
        catPill.setTextSize(10);
        catPill.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable catBg = new android.graphics.drawable.GradientDrawable();
        catBg.setColor(android.graphics.Color.parseColor("#F2F2F7"));
        catBg.setCornerRadius(8*dp);
        catPill.setBackground(catBg);
        catPill.setPadding((int)(8*dp),(int)(3*dp),(int)(8*dp),(int)(3*dp));
        metaRow.addView(catPill);

        if (p.brand != null && !p.brand.isEmpty()) {
            android.widget.TextView brandTxt = new android.widget.TextView(this);
            brandTxt.setText("   •   " + p.brand);
            brandTxt.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
            brandTxt.setTextSize(11);
            metaRow.addView(brandTxt);
        }

        android.widget.Space spacer1 = new android.widget.Space(this);
        spacer1.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
        metaRow.addView(spacer1);

        String skuText = (p.sku != null && !p.sku.isEmpty()) ? ("SKU: " + p.sku) : "";
        String sampleText = (p.sampleCode != null && !p.sampleCode.isEmpty()) ? ("SAMPLE: " + p.sampleCode) : "";
        String rightMeta = "";
        if (!skuText.isEmpty() && !sampleText.isEmpty()) rightMeta = skuText + "   •   " + sampleText;
        else if (!skuText.isEmpty()) rightMeta = skuText;
        else if (!sampleText.isEmpty()) rightMeta = sampleText;

        if (!rightMeta.isEmpty()) {
            android.widget.TextView txtRightMeta = new android.widget.TextView(this);
            txtRightMeta.setText(rightMeta);
            txtRightMeta.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
            txtRightMeta.setTextSize(11);
            txtRightMeta.setTypeface(null, android.graphics.Typeface.BOLD);
            txtRightMeta.setGravity(android.view.Gravity.END);
            metaRow.addView(txtRightMeta);
        }
        contentPad.addView(metaRow);

        // 2b. Product Name
        final android.widget.TextView txtTitle = new android.widget.TextView(this);
        txtTitle.setText(p.name);
        txtTitle.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        txtTitle.setTextSize(22);
        txtTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams titleLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, 0, 0, (int)(16*dp));
        txtTitle.setLayoutParams(titleLp);
        contentPad.addView(txtTitle);

        // 2c. Price & Stock Card (Spacious 2-column card with crisp vertical divider)
        android.widget.LinearLayout infoStrip = new android.widget.LinearLayout(this);
        infoStrip.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        infoStrip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable stripBg = new android.graphics.drawable.GradientDrawable();
        stripBg.setColor(android.graphics.Color.parseColor("#F8F8FB"));
        stripBg.setCornerRadius(16*dp);
        stripBg.setStroke((int)(1*dp), android.graphics.Color.parseColor("#EBEBF0"));
        infoStrip.setBackground(stripBg);
        infoStrip.setPadding((int)(20*dp),(int)(16*dp),(int)(20*dp),(int)(16*dp));
        android.widget.LinearLayout.LayoutParams stripLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        stripLp.setMargins(0, 0, 0, (int)(12*dp));
        infoStrip.setLayoutParams(stripLp);

        // Price Column
        android.widget.LinearLayout priceBlock = new android.widget.LinearLayout(this);
        priceBlock.setOrientation(android.widget.LinearLayout.VERTICAL);
        priceBlock.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        android.widget.TextView lblPrice = new android.widget.TextView(this);
        lblPrice.setText("WHOLESALE PRICE");
        lblPrice.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
        lblPrice.setTextSize(10);
        lblPrice.setTypeface(null, android.graphics.Typeface.BOLD);
        priceBlock.addView(lblPrice);
        final android.widget.TextView txtPrice = new android.widget.TextView(this);
        txtPrice.setText("LKR " + String.format(Locale.getDefault(), "%,.2f", p.wholesalePrice.doubleValue()));
        txtPrice.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        txtPrice.setTextSize(20);
        txtPrice.setTypeface(null, android.graphics.Typeface.BOLD);
        txtPrice.setPadding(0, (int)(4*dp), 0, 0);
        priceBlock.addView(txtPrice);
        infoStrip.addView(priceBlock);

        // Vertical divider
        View stripDiv1 = new View(this);
        android.widget.LinearLayout.LayoutParams div1Lp = new android.widget.LinearLayout.LayoutParams((int)(1*dp),(int)(40*dp));
        div1Lp.setMargins((int)(16*dp),0,(int)(16*dp),0);
        stripDiv1.setLayoutParams(div1Lp);
        stripDiv1.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E5"));
        infoStrip.addView(stripDiv1);

        // Stock Column
        int available = p.qtyOnHand - p.qtyReserved;
        android.widget.LinearLayout stockBlock = new android.widget.LinearLayout(this);
        stockBlock.setOrientation(android.widget.LinearLayout.VERTICAL);
        stockBlock.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        android.widget.TextView lblStock = new android.widget.TextView(this);
        lblStock.setText("AVAILABLE STOCK");
        lblStock.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
        lblStock.setTextSize(10);
        lblStock.setTypeface(null, android.graphics.Typeface.BOLD);
        stockBlock.addView(lblStock);
        final android.widget.TextView txtStock = new android.widget.TextView(this);
        if (available > 0) {
            txtStock.setText(available + " Units");
            txtStock.setTextColor(android.graphics.Color.parseColor("#30D158"));
        } else {
            txtStock.setText("Out of Stock");
            txtStock.setTextColor(android.graphics.Color.parseColor("#FF453A"));
        }
        txtStock.setTextSize(20);
        txtStock.setTypeface(null, android.graphics.Typeface.BOLD);
        txtStock.setPadding(0, (int)(4*dp), 0, 0);
        stockBlock.addView(txtStock);
        infoStrip.addView(stockBlock);

        contentPad.addView(infoStrip);

        // 2d. Description row (if available)
        if (p.description != null && !p.description.isEmpty()) {
            android.widget.TextView txtDesc = new android.widget.TextView(this);
            txtDesc.setText(p.description);
            txtDesc.setTextColor(android.graphics.Color.parseColor("#636366"));
            txtDesc.setTextSize(13);
            txtDesc.setLineSpacing(2*dp, 1.0f);
            android.widget.LinearLayout.LayoutParams descLp = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            descLp.setMargins(0,(int)(4*dp),0,0);
            txtDesc.setLayoutParams(descLp);
            contentPad.addView(txtDesc);
        }
        root.addView(contentPad);

        // 3. VARIATIONS CAROUSEL
        if (hasProductVariations(p) && p.variationsJson != null && !p.variationsJson.isEmpty() && !p.variationsJson.equals("null")) {
            try {
                org.json.JSONArray vars = new org.json.JSONArray(p.variationsJson);
                if (vars.length() > 0) {
                    // Thin divider
                    View sectionDivider = new View(this);
                    android.widget.LinearLayout.LayoutParams divLp = new android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int)(1*dp));
                    divLp.setMargins((int)(20*dp),0,(int)(20*dp),0);
                    sectionDivider.setLayoutParams(divLp);
                    sectionDivider.setBackgroundColor(android.graphics.Color.parseColor("#F2F2F7"));
                    root.addView(sectionDivider);

                    // Section header
                    android.widget.LinearLayout varHeader = new android.widget.LinearLayout(this);
                    varHeader.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                    varHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    varHeader.setPadding((int)(20*dp),(int)(14*dp),(int)(20*dp),(int)(8*dp));
                    android.widget.TextView varHeaderTxt = new android.widget.TextView(this);
                    varHeaderTxt.setText("VARIATIONS");
                    varHeaderTxt.setTextColor(android.graphics.Color.parseColor("#3C3C43"));
                    varHeaderTxt.setTextSize(13);
                    varHeaderTxt.setTypeface(null, android.graphics.Typeface.BOLD);
                    varHeaderTxt.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    varHeader.addView(varHeaderTxt);
                    android.widget.TextView varCount = new android.widget.TextView(this);
                    varCount.setText(vars.length() + " options  •  tap to select");
                    varCount.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                    varCount.setTextSize(11);
                    varHeader.addView(varCount);
                    root.addView(varHeader);

                    // Horizontal carousel
                    HorizontalScrollView varScroll = new HorizontalScrollView(this);
                    varScroll.setHorizontalScrollBarEnabled(false);
                    android.widget.LinearLayout.LayoutParams varScrollLp = new android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    varScrollLp.setMargins(0,0,0,(int)(12*dp));
                    varScroll.setLayoutParams(varScrollLp);

                    LinearLayout varContainer = new LinearLayout(this);
                    varContainer.setOrientation(LinearLayout.HORIZONTAL);
                    varContainer.setPadding((int)(20*dp),0,(int)(20*dp),(int)(20*dp));
                    varScroll.addView(varContainer);

                    final java.util.List<android.widget.FrameLayout> tileList = new java.util.ArrayList<>();

                    for (int i = 0; i < vars.length(); i++) {
                        final org.json.JSONObject vObj = vars.getJSONObject(i);
                        String optName = vObj.optString("attribute", vObj.optString("option_name", ""));
                        if (optName.startsWith(p.name + " - ")) {
                            optName = optName.substring(p.name.length() + 3);
                        } else if (optName.contains(" - ")) {
                            optName = optName.substring(optName.lastIndexOf(" - ") + 3);
                        }
                        final String varName = optName;
                        final int vQty = vObj.optInt("quantity_on_hand", vObj.optInt("qty", 0));
                        final String varImgPath = vObj.optString("image_path", vObj.optString("image", ""));
                        double calcPrice = vObj.optDouble("wholesale_price", 0.0);
                        if (calcPrice <= 0) calcPrice = vObj.optDouble("price", 0.0);
                        if (calcPrice <= 0) calcPrice = p.wholesalePrice.doubleValue();
                        final double varPrice = calcPrice;

                        // Tile: square FrameLayout
                        final android.widget.FrameLayout tile = new android.widget.FrameLayout(this);
                        android.graphics.drawable.GradientDrawable tileBg = new android.graphics.drawable.GradientDrawable();
                        tileBg.setColor(android.graphics.Color.parseColor("#F2F2F7"));
                        tileBg.setCornerRadius(16*dp);
                        tile.setBackground(tileBg);
                        tile.setClipToOutline(true);
                        int tileW = (int)(110*dp);
                        android.widget.LinearLayout.LayoutParams tileLp = new android.widget.LinearLayout.LayoutParams(tileW, tileW);
                        tileLp.setMargins(0,0,(int)(10*dp),0);
                        tile.setLayoutParams(tileLp);

                        // Image
                        ImageView tileImg = new ImageView(this);
                        tileImg.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                        tileImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        boolean imgLoaded = false;
                        if (!varImgPath.isEmpty()) {
                            android.graphics.Bitmap bm = getCachedThumbnail(varImgPath, 2);
                            if (bm != null) { tileImg.setImageBitmap(bm); imgLoaded = true; }
                        }
                        if (!imgLoaded && p.localImagePath != null && !p.localImagePath.isEmpty()) {
                            android.graphics.Bitmap bm = getCachedThumbnail(p.localImagePath, 2);
                            if (bm != null) { tileImg.setImageBitmap(bm); imgLoaded = true; }
                        }
                        if (!imgLoaded) tileImg.setImageResource(android.R.drawable.ic_menu_gallery);
                        tile.addView(tileImg);

                        // Bottom gradient scrim with name + price
                        android.widget.LinearLayout scrim = new android.widget.LinearLayout(this);
                        scrim.setOrientation(LinearLayout.VERTICAL);
                        scrim.setGravity(android.view.Gravity.BOTTOM);
                        android.widget.FrameLayout.LayoutParams scrimFp = new android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
                        scrim.setLayoutParams(scrimFp);
                        android.graphics.drawable.GradientDrawable scrimBg = new android.graphics.drawable.GradientDrawable(
                                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                                new int[]{android.graphics.Color.parseColor("#DD000000"), android.graphics.Color.TRANSPARENT});
                        scrim.setBackground(scrimBg);
                        scrim.setPadding((int)(7*dp),(int)(6*dp),(int)(6*dp),(int)(6*dp));
                        android.widget.TextView tileNameTv = new android.widget.TextView(this);
                        tileNameTv.setText(varName);
                        tileNameTv.setTextColor(android.graphics.Color.WHITE);
                        tileNameTv.setTextSize(11);
                        tileNameTv.setTypeface(null, android.graphics.Typeface.BOLD);
                        tileNameTv.setSingleLine(true);
                        tileNameTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        scrim.addView(tileNameTv);
                        android.widget.TextView tilePriceTv = new android.widget.TextView(this);
                        tilePriceTv.setText("LKR " + String.format(Locale.getDefault(), "%,.0f", varPrice));
                        tilePriceTv.setTextColor(android.graphics.Color.parseColor("#CCFFFFFF"));
                        tilePriceTv.setTextSize(10);
                        scrim.addView(tilePriceTv);
                        tile.addView(scrim);

                        // Stock badge top-right
                        android.widget.TextView stockBadge = new android.widget.TextView(this);
                        stockBadge.setText(vQty > 0 ? " " + vQty + " " : " 0 ");
                        stockBadge.setTextColor(android.graphics.Color.WHITE);
                        stockBadge.setTextSize(9);
                        stockBadge.setTypeface(null, android.graphics.Typeface.BOLD);
                        android.graphics.drawable.GradientDrawable sbBg = new android.graphics.drawable.GradientDrawable();
                        sbBg.setColor(android.graphics.Color.parseColor(vQty > 0 ? "#34C759" : "#FF3B30"));
                        sbBg.setCornerRadius(20*dp);
                        stockBadge.setBackground(sbBg);
                        android.widget.FrameLayout.LayoutParams sbLp = new android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                        sbLp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
                        sbLp.setMargins(0,(int)(6*dp),(int)(6*dp),0);
                        stockBadge.setLayoutParams(sbLp);
                        tile.addView(stockBadge);

                        tileList.add(tile);
                        tile.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                for (android.widget.FrameLayout t : tileList) {
                                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                                    bg.setColor(android.graphics.Color.parseColor("#F2F2F7"));
                                    bg.setCornerRadius(16*dp);
                                    t.setBackground(bg);
                                    t.setClipToOutline(true);
                                }
                                android.graphics.drawable.GradientDrawable selBg = new android.graphics.drawable.GradientDrawable();
                                selBg.setColor(android.graphics.Color.parseColor("#F2F2F7"));
                                selBg.setCornerRadius(16*dp);
                                selBg.setStroke((int)(3*dp), android.graphics.Color.parseColor("#1C1C1E"));
                                tile.setBackground(selBg);
                                tile.setClipToOutline(true);
                                boolean switched = false;
                                if (!varImgPath.isEmpty()) {
                                    android.graphics.Bitmap bm = getCachedThumbnail(varImgPath, 1);
                                    if (bm != null) { zoomImage.setImageBitmap(bm); switched = true; }
                                }
                                if (!switched && p.localImagePath != null && !p.localImagePath.isEmpty()) {
                                    android.graphics.Bitmap bm = getCachedThumbnail(p.localImagePath, 1);
                                    if (bm != null) zoomImage.setImageBitmap(bm);
                                }
                                txtTitle.setText(p.name + "  \u203a  " + varName);
                                txtPrice.setText("LKR " + String.format(Locale.getDefault(), "%,.2f", varPrice));
                                if (vQty > 0) {
                                    txtStock.setText(vQty + " Units");
                                    txtStock.setTextColor(android.graphics.Color.parseColor("#30D158"));
                                } else {
                                    txtStock.setText("Out of Stock");
                                    txtStock.setTextColor(android.graphics.Color.parseColor("#FF453A"));
                                }
                            }
                        });
                        varContainer.addView(tile);
                    }
                    root.addView(varScroll);
                }
            } catch (Exception e) {
                android.util.Log.e("BillingActivity", "QuickView variation error: " + e.getMessage());
            }
        }

        scrollView.addView(root);
        builder.setView(scrollView);

        final androidx.appcompat.app.AlertDialog dialog = builder.create();
                dialog.show();
        if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.GONE);
        if (layoutHeader != null) layoutHeader.setVisibility(View.GONE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            RecyclerView list1 = findViewById(R.id.gridProducts);
            RecyclerView list2 = findViewById(R.id.lstProducts);
            android.graphics.RenderEffect blur = android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP);
            if (list1 != null && list1.getVisibility() == View.VISIBLE) list1.setRenderEffect(blur);
            if (list2 != null && list2.getVisibility() == View.VISIBLE) list2.setRenderEffect(blur);
        }
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                if (layoutCartOverlay == null || layoutCartOverlay.getVisibility() != View.VISIBLE) {
                    if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.VISIBLE);
                    if (layoutHeader != null) layoutHeader.setVisibility(View.VISIBLE);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        RecyclerView list1 = findViewById(R.id.gridProducts);
                        RecyclerView list2 = findViewById(R.id.lstProducts);
                        if (list1 != null) list1.setRenderEffect(null);
                        if (list2 != null) list2.setRenderEffect(null);
                    }
                }
            }
        });

        if (dialog.getWindow() != null) {
            android.graphics.drawable.GradientDrawable winBg = new android.graphics.drawable.GradientDrawable();
            winBg.setColor(android.graphics.Color.WHITE);
            winBg.setCornerRadius(28*dp);
            dialog.getWindow().setBackgroundDrawable(winBg);
            dialog.getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
    }

    private void addQuickViewSpecRow(android.widget.LinearLayout container, java.lang.String label, java.lang.String value) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);
        
        android.widget.TextView txtLabel = new android.widget.TextView(this);
        txtLabel.setText(label);
        txtLabel.setTextColor(android.graphics.Color.parseColor("#8E8E93")); // iOS gray
        txtLabel.setTextSize(13);
        txtLabel.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        
        android.widget.TextView txtValue = new android.widget.TextView(this);
        txtValue.setText(value);
        txtValue.setTextColor(android.graphics.Color.BLACK);
        txtValue.setTextSize(13);
        txtValue.setTypeface(null, android.graphics.Typeface.BOLD);
        txtValue.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));
        
        row.addView(txtLabel);
        row.addView(txtValue);
        container.addView(row);
    }

    private android.graphics.drawable.GradientDrawable createRoundedBackground(String bgColor, String strokeColor, int cornerDp) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(android.graphics.Color.parseColor(bgColor));
        gd.setCornerRadius(cornerDp * getResources().getDisplayMetrics().density);
        if (strokeColor != null) {
            gd.setStroke((int) (1 * getResources().getDisplayMetrics().density), android.graphics.Color.parseColor(strokeColor));
        }
        return gd;
    }

    private void populateVariationChips(LinearLayout container, ProductModel p) {
        if (container == null) return;
        container.removeAllViews();
        if (!hasProductVariations(p) || p.variationsJson == null || p.variationsJson.isEmpty() || p.variationsJson.equals("null")) {
            container.setVisibility(View.GONE);
            return;
        }
        try {
            org.json.JSONArray vars = new org.json.JSONArray(p.variationsJson);
            if (vars.length() == 0) {
                container.setVisibility(View.GONE);
                return;
            }
            container.setVisibility(View.VISIBLE);
            for (int i = 0; i < vars.length(); i++) {
                org.json.JSONObject vObj = vars.getJSONObject(i);
                String optionName = vObj.optString("attribute", vObj.optString("option_name", ""));
                if (optionName.startsWith(p.name + " - ")) {
                    optionName = optionName.substring(p.name.length() + 3);
                } else if (optionName.contains(" - ")) {
                    optionName = optionName.substring(optionName.lastIndexOf(" - ") + 3);
                }
                int qty = vObj.optInt("quantity_on_hand", vObj.optInt("qty", 0));

                LinearLayout chip = new LinearLayout(this);
                chip.setOrientation(LinearLayout.HORIZONTAL);
                chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
                chip.setBackground(createRoundedBackground("#FFFFFF", "#E5E5EA", 12));
                chip.setPadding(
                    (int) (8 * getResources().getDisplayMetrics().density),
                    (int) (4 * getResources().getDisplayMetrics().density),
                    (int) (10 * getResources().getDisplayMetrics().density),
                    (int) (4 * getResources().getDisplayMetrics().density)
                );
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                chipParams.setMargins(0, 0, (int) (8 * getResources().getDisplayMetrics().density), 0);
                chip.setLayoutParams(chipParams);

                // Mini Image (20x20dp)
                ImageView miniImg = new ImageView(this);
                LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                    (int) (20 * getResources().getDisplayMetrics().density),
                    (int) (20 * getResources().getDisplayMetrics().density)
                );
                imgParams.setMargins(0, 0, (int) (6 * getResources().getDisplayMetrics().density), 0);
                miniImg.setLayoutParams(imgParams);
                miniImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
                String varImgPath = vObj.optString("image_path", vObj.optString("image", ""));
                boolean loaded = false;
                if (!varImgPath.isEmpty()) {
                    android.graphics.Bitmap cached = getCachedThumbnail(varImgPath, 4);
                    if (cached != null) {
                        miniImg.setImageBitmap(cached);
                        loaded = true;
                    }
                }
                if (!loaded && p.localImagePath != null && !p.localImagePath.isEmpty()) {
                    android.graphics.Bitmap cached = getCachedThumbnail(p.localImagePath, 4);
                    if (cached != null) {
                        miniImg.setImageBitmap(cached);
                        loaded = true;
                    }
                }
                if (!loaded) {
                    miniImg.setImageResource(android.R.drawable.ic_menu_gallery);
                }
                chip.addView(miniImg);

                TextView txtVar = new TextView(this);
                txtVar.setText(optionName + " (" + qty + ")");
                txtVar.setTextColor(android.graphics.Color.parseColor("#000000"));
                txtVar.setTextSize(11);
                txtVar.setTypeface(null, android.graphics.Typeface.BOLD);
                chip.addView(txtVar);

                container.addView(chip);
            }
        } catch (Exception e) {
            container.setVisibility(View.GONE);
        }
    }

    // Helper Models
    private static class DiscountCheckResult {
        int ruleId;
        String name;
        String ruleType;        // item_wise, category_wise, bill_wise
        String rewardType;      // free_issue, percentage
        int targetItemId;
        String targetItemName;
        int targetCategoryId;
        String targetCategoryName;
        double minThreshold;
        double maxThreshold;
        double rewardVal;
        int additionalFreeQty;
        double discountCap;
    }

    public static class PaymentTermModel {
        int id;
        String name;
        int daysDue;
    }

    public static class CustomerModel {
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
        double latitude;
        double longitude;
    }

    public static class ProductModel {
        int id, qtyOnHand, qtyReserved;
        String name, category, localImagePath;
        java.math.BigDecimal price = java.math.BigDecimal.ZERO;
        java.math.BigDecimal wholesalePrice = java.math.BigDecimal.ZERO;
        String sku, sampleCode, variationsJson;
        String brand, description;
    }

    public static class CartItemModel {
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
        int variationOptionId = 0;
        String notes = "";
        boolean isPercentDiscountActive = false;
    }

    // Product visual catalog list adapter
    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {
        @Override
        public int getItemCount() {
            return productList.size();
        }

        @Override
        public ProductViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(BillingActivity.this).inflate(R.layout.item_product, parent, false);
            return new ProductViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ProductViewHolder holder, int position) {
            final ProductModel p = productList.get(position);

            holder.lblProductName.setText(p.name);
            holder.lblCategory.setText(p.category);
            
            if (p.sampleCode != null && !p.sampleCode.trim().isEmpty()) {
                holder.lblSampleCode.setText("Sample Code: " + p.sampleCode);
                holder.lblSampleCode.setVisibility(View.VISIBLE);
            } else {
                holder.lblSampleCode.setVisibility(View.GONE);
            }

            final java.math.BigDecimal currentPrice = p.price;
            holder.lblPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", currentPrice.doubleValue()));

            int available = p.qtyOnHand - p.qtyReserved;
            holder.lblStock.setText("Available Stock: " + available);

            if (available <= 0) {
                holder.lblStock.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                holder.lblStock.setText("Out of Stock");
            } else {
                holder.lblStock.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }

            if (holder.imgProduct != null) {
                if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
                    File file = new File(p.localImagePath);
                    if (file.exists()) {
                        holder.imgProduct.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));
                    } else {
                        holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                } else {
                    holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            }

            populateVariationChips(holder.layoutVariations, p);

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showProductConfigDialog(p);
                }
            });

            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showProductQuickViewDialog(p);
                    return true;
                }
            });
        }

        class ProductViewHolder extends RecyclerView.ViewHolder {
            TextView lblProductName;
            TextView lblCategory;
            TextView lblSampleCode;
            TextView lblPrice;
            TextView lblStock;
            ImageView imgProduct;
            LinearLayout layoutVariations;

            ProductViewHolder(View itemView) {
                super(itemView);
                lblProductName = itemView.findViewById(R.id.lblProductName);
                lblCategory = itemView.findViewById(R.id.lblCategory);
                lblSampleCode = itemView.findViewById(R.id.lblSampleCode);
                lblPrice = itemView.findViewById(R.id.lblPrice);
                lblStock = itemView.findViewById(R.id.lblStock);
                imgProduct = itemView.findViewById(R.id.imgProduct);
                layoutVariations = itemView.findViewById(R.id.layoutVariations);
            }
        }
    }

    // Shopping Cart Summary popup adapter
    private class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {
        @Override
        public int getItemCount() {
            return cartList.size();
        }

        @Override
        public CartViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(BillingActivity.this).inflate(R.layout.item_cart, parent, false);
            return new CartViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final CartViewHolder holder, int position) {
            final CartItemModel item = cartList.get(position);
            holder.txtCartItemName.setText(item.name);

            if (item.discountVal.compareTo(java.math.BigDecimal.ZERO) > 0) {
                holder.txtCartItemDetails.setText(String.format(Locale.getDefault(), "Qty: %d  x  LKR %.2f (Less LKR %.2f Disc)  =  LKR %.2f", item.quantity, item.activePrice.doubleValue(), item.discountVal.doubleValue(), item.total.doubleValue()));
            } else {
                holder.txtCartItemDetails.setText(String.format(Locale.getDefault(), "Qty: %d  x  LKR %.2f   =   LKR %.2f", item.quantity, item.activePrice.doubleValue(), item.total.doubleValue()));
            }

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        CartItemModel cartItem = cartList.get(pos);
                        ProductModel product = getProductById(cartItem.productId);
                        if (product != null) {
                            showProductConfigDialog(product, cartItem);
                        } else {
                            Toast.makeText(BillingActivity.this, "Product details not found.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }

        class CartViewHolder extends RecyclerView.ViewHolder {
            TextView txtCartItemName;
            TextView txtCartItemDetails;

            CartViewHolder(View itemView) {
                super(itemView);
                txtCartItemName = itemView.findViewById(R.id.txtCartItemName);
                txtCartItemDetails = itemView.findViewById(R.id.txtCartItemDetails);
            }
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

    private boolean hasProductVariations(ProductModel p) {
        if (p == null || p.variationsJson == null || p.variationsJson.isEmpty() || p.variationsJson.equals("null")) {
            return false;
        }
        try {
            org.json.JSONArray parsedVars = new org.json.JSONArray(p.variationsJson);
            if (parsedVars.length() == 0) return false;
            boolean hasValidVar = false;
            for (int i = 0; i < parsedVars.length(); i++) {
                org.json.JSONObject vObj = parsedVars.getJSONObject(i);
                String name = vObj.optString("attribute", vObj.optString("option_name", ""));
                if (name != null && !name.trim().isEmpty()) {
                    hasValidVar = true;
                    break;
                }
            }
            return hasValidVar;
        } catch (Exception e) {
            return false;
        }
    }

    private void showProductConfigDialog(final ProductModel p) {
        showProductConfigDialog(p, null);
    }

    private void showProductConfigDialog(final ProductModel p, final CartItemModel existingItem) {
        final androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);

        final float dp = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int dialogWidth = (int) (screenW * 0.92f);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setBackgroundColor(android.graphics.Color.WHITE);
        scrollView.setVerticalScrollBarEnabled(false);

        // Root container (Pure White, modern padding)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int)(20*dp), (int)(20*dp), (int)(20*dp), (int)(20*dp));
        layout.setBackgroundColor(android.graphics.Color.WHITE);

        // 1. HEADER BAR (Image left | Title + Price middle | Close button right)
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.setMargins(0, 0, 0, (int)(16*dp));
        headerLayout.setLayoutParams(headerLp);

        // Product image in rounded light frame
        FrameLayout imgFrame = new FrameLayout(this);
        imgFrame.setBackground(createRoundedBackground("#F2F2F7", "#EBEBF0", 14));
        imgFrame.setClipToOutline(true);
        int imgSize = (int)(68*dp);
        LinearLayout.LayoutParams imgFrameLp = new LinearLayout.LayoutParams(imgSize, imgSize);
        imgFrame.setLayoutParams(imgFrameLp);

        ImageView imgProduct = new ImageView(this);
        imgProduct.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imgProduct.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
            android.graphics.Bitmap bm = getCachedThumbnail(p.localImagePath, 1);
            if (bm != null) imgProduct.setImageBitmap(bm);
            else imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
        } else {
            imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        imgFrame.addView(imgProduct);
        headerLayout.addView(imgFrame);

        // Title and price text container
        LinearLayout titleTextLayout = new LinearLayout(this);
        titleTextLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleTextParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleTextParams.setMargins((int)(14 * dp), 0, (int)(8 * dp), 0);
        titleTextLayout.setLayoutParams(titleTextParams);

        TextView txtTitle = new TextView(this);
        txtTitle.setText(p.name);
        txtTitle.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        txtTitle.setTextSize(19);
        txtTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        titleTextLayout.addView(txtTitle);

        TextView txtSub = new TextView(this);
        txtSub.setText("Wholesale: LKR " + String.format(Locale.getDefault(), "%,.2f", p.wholesalePrice.doubleValue()));
        txtSub.setTextColor(android.graphics.Color.parseColor("#5856D6"));
        txtSub.setTextSize(13);
        txtSub.setTypeface(null, android.graphics.Typeface.BOLD);
        txtSub.setPadding(0, (int)(4*dp), 0, 0);
        titleTextLayout.addView(txtSub);

        headerLayout.addView(titleTextLayout);

        // Top-right close circular button
        android.widget.TextView btnCloseHeader = new android.widget.TextView(this);
        btnCloseHeader.setText("\u2715");
        btnCloseHeader.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        btnCloseHeader.setTextSize(16);
        btnCloseHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCloseHeader.setGravity(android.view.Gravity.CENTER);
        btnCloseHeader.setBackground(createRoundedBackground("#F2F2F7", "#F2F2F7", 18));
        LinearLayout.LayoutParams closeHeadLp = new LinearLayout.LayoutParams((int)(36*dp), (int)(36*dp));
        btnCloseHeader.setLayoutParams(closeHeadLp);
        headerLayout.addView(btnCloseHeader);

        layout.addView(headerLayout);

        // Selected Variation (Horizontal Scroll View of Cards)
        final boolean hasVariations = hasProductVariations(p);
        final int[] selectedVarIndex = new int[]{-1};
        final boolean[] itemAdded = new boolean[]{false};

        org.json.JSONArray parsedVars = null;
        if (hasVariations) {
            try {
                parsedVars = new org.json.JSONArray(p.variationsJson);
                if (existingItem != null && existingItem.selectedVariation != null && !existingItem.selectedVariation.isEmpty()) {
                    for (int i = 0; i < parsedVars.length(); i++) {
                        org.json.JSONObject vObj = parsedVars.getJSONObject(i);
                        String name = vObj.optString("attribute", vObj.optString("option_name", ""));
                        if (name.equals(existingItem.selectedVariation)) {
                            selectedVarIndex[0] = i;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("BillingActivity", "Error parsing variations JSON: " + e.getMessage());
            }
        }
        final org.json.JSONArray vars = parsedVars;

        HorizontalScrollView varScrollView = new HorizontalScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        scrollParams.setMargins(0, 0, 0, (int)(14 * dp));
        varScrollView.setLayoutParams(scrollParams);
        varScrollView.setHorizontalScrollBarEnabled(false);

        final LinearLayout varLayout = new LinearLayout(this);
        varLayout.setOrientation(LinearLayout.HORIZONTAL);
        varLayout.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        varScrollView.addView(varLayout);

        if (hasVariations) {
            TextView lblVar = new TextView(this);
            lblVar.setText("SELECT A VARIATION");
            lblVar.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
            lblVar.setTextSize(11);
            lblVar.setTypeface(null, android.graphics.Typeface.BOLD);
            lblVar.setPadding(0, 0, 0, (int)(8 * dp));
            layout.addView(lblVar);
            layout.addView(varScrollView);
        }

        // 2. UNIT PRICE OVERRIDE CARD
        LinearLayout priceCard = new LinearLayout(this);
        priceCard.setOrientation(LinearLayout.VERTICAL);
        priceCard.setBackground(createRoundedBackground("#F8F8FB", "#EBEBF0", 16));
        priceCard.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));
        LinearLayout.LayoutParams priceCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        priceCardLp.setMargins(0, 0, 0, (int)(12*dp));
        priceCard.setLayoutParams(priceCardLp);

        TextView lblOverride = new TextView(this);
        lblOverride.setText("UNIT PRICE (LKR)");
        lblOverride.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
        lblOverride.setTextSize(11);
        lblOverride.setTypeface(null, android.graphics.Typeface.BOLD);
        lblOverride.setPadding(0, 0, 0, (int)(8*dp));
        priceCard.addView(lblOverride);

        final EditText edtOverridePrice = new EditText(this);
        edtOverridePrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edtOverridePrice.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        edtOverridePrice.setHintTextColor(android.graphics.Color.parseColor("#AEAEB2"));
        java.math.BigDecimal initialPrice = (existingItem != null && existingItem.customPrice.compareTo(java.math.BigDecimal.ZERO) > 0) ? existingItem.customPrice : p.wholesalePrice;
        edtOverridePrice.setText(initialPrice.toPlainString());
        edtOverridePrice.setTextSize(17);
        edtOverridePrice.setTypeface(null, android.graphics.Typeface.BOLD);
        edtOverridePrice.setBackground(createRoundedBackground("#FFFFFF", "#E0E0E5", 10));
        edtOverridePrice.setPadding((int)(12*dp), (int)(10*dp), (int)(12*dp), (int)(10*dp));
        priceCard.addView(edtOverridePrice);
        layout.addView(priceCard);

        // 3. QUANTITY SELECTOR CARD (Apple/POS Stepper layout)
        LinearLayout qtyCard = new LinearLayout(this);
        qtyCard.setOrientation(LinearLayout.HORIZONTAL);
        qtyCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
        qtyCard.setBackground(createRoundedBackground("#F8F8FB", "#EBEBF0", 16));
        qtyCard.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));
        LinearLayout.LayoutParams qtyCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qtyCardLp.setMargins(0, 0, 0, (int)(12*dp));
        qtyCard.setLayoutParams(qtyCardLp);

        LinearLayout qtyLeft = new LinearLayout(this);
        qtyLeft.setOrientation(LinearLayout.VERTICAL);
        qtyLeft.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView lblQty = new TextView(this);
        lblQty.setText("QUANTITY");
        lblQty.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        lblQty.setTextSize(14);
        lblQty.setTypeface(null, android.graphics.Typeface.BOLD);
        qtyLeft.addView(lblQty);

        TextView lblQtyHint = new TextView(this);
        lblQtyHint.setText("Tap or hold - / +");
        lblQtyHint.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
        lblQtyHint.setTextSize(11);
        lblQtyHint.setPadding(0, (int)(2*dp), 0, 0);
        qtyLeft.addView(lblQtyHint);
        qtyCard.addView(qtyLeft);

        // Stepper container on right
        LinearLayout stepperBox = new LinearLayout(this);
        stepperBox.setOrientation(LinearLayout.HORIZONTAL);
        stepperBox.setGravity(android.view.Gravity.CENTER_VERTICAL);
        stepperBox.setBackground(createRoundedBackground("#EBEBF0", "#EBEBF0", 24));
        stepperBox.setPadding((int)(4*dp), (int)(4*dp), (int)(4*dp), (int)(4*dp));

        Button btnMinus = new Button(this);
        btnMinus.setText("-");
        btnMinus.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        btnMinus.setTextSize(20);
        btnMinus.setTypeface(null, android.graphics.Typeface.BOLD);
        btnMinus.setBackground(createRoundedBackground("#FFFFFF", "#FFFFFF", 20));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams((int)(42*dp), (int)(42*dp));
        btnMinus.setLayoutParams(btnParams);

        final EditText edtQtyInput = new EditText(this);
        edtQtyInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        edtQtyInput.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        edtQtyInput.setHintTextColor(android.graphics.Color.parseColor("#AEAEB2"));
        int initialQty = existingItem != null ? existingItem.quantity : 1;
        edtQtyInput.setText(String.valueOf(initialQty));
        edtQtyInput.setTextSize(20);
        edtQtyInput.setTypeface(null, android.graphics.Typeface.BOLD);
        edtQtyInput.setGravity(android.view.Gravity.CENTER);
        edtQtyInput.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        LinearLayout.LayoutParams qtyInputLp = new LinearLayout.LayoutParams((int)(64*dp), ViewGroup.LayoutParams.WRAP_CONTENT);
        edtQtyInput.setLayoutParams(qtyInputLp);

        Button btnPlus = new Button(this);
        btnPlus.setText("+");
        btnPlus.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        btnPlus.setTextSize(20);
        btnPlus.setTypeface(null, android.graphics.Typeface.BOLD);
        btnPlus.setBackground(createRoundedBackground("#FFFFFF", "#FFFFFF", 20));
        btnPlus.setLayoutParams(btnParams);

        stepperBox.addView(btnMinus);
        stepperBox.addView(edtQtyInput);
        stepperBox.addView(btnPlus);
        qtyCard.addView(stepperBox);
        layout.addView(qtyCard);

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

        // 4. DISCOUNT CARD (Optional)
        LinearLayout discountCard = new LinearLayout(this);
        discountCard.setOrientation(LinearLayout.VERTICAL);
        discountCard.setBackground(createRoundedBackground("#F8F8FB", "#EBEBF0", 16));
        discountCard.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));
        LinearLayout.LayoutParams discountCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        discountCardLp.setMargins(0, 0, 0, (int)(14*dp));
        discountCard.setLayoutParams(discountCardLp);

        TextView lblDiscountHeader = new TextView(this);
        lblDiscountHeader.setText("DISCOUNTS (OPTIONAL)");
        lblDiscountHeader.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
        lblDiscountHeader.setTextSize(11);
        lblDiscountHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        lblDiscountHeader.setPadding(0, 0, 0, (int)(10*dp));
        discountCard.addView(lblDiscountHeader);

        LinearLayout discountRow = new LinearLayout(this);
        discountRow.setOrientation(LinearLayout.HORIZONTAL);

        // Percentage discount
        LinearLayout colPct = new LinearLayout(this);
        colPct.setOrientation(LinearLayout.VERTICAL);
        colPct.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView lblPct = new TextView(this);
        lblPct.setText("DISCOUNT (%)");
        lblPct.setTextColor(android.graphics.Color.parseColor("#636366"));
        lblPct.setTextSize(10);
        lblPct.setTypeface(null, android.graphics.Typeface.BOLD);
        lblPct.setPadding(0, 0, 0, (int)(6*dp));
        colPct.addView(lblPct);

        final EditText edtDiscountPct = new EditText(this);
        edtDiscountPct.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edtDiscountPct.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        edtDiscountPct.setHintTextColor(android.graphics.Color.parseColor("#AEAEB2"));
        edtDiscountPct.setHint("0.0%");
        if (existingItem != null && existingItem.discountPercent.compareTo(java.math.BigDecimal.ZERO) > 0) {
            edtDiscountPct.setText(existingItem.discountPercent.toPlainString());
        }
        edtDiscountPct.setTextSize(15);
        edtDiscountPct.setTypeface(null, android.graphics.Typeface.BOLD);
        edtDiscountPct.setBackground(createRoundedBackground("#FFFFFF", "#E0E0E5", 10));
        edtDiscountPct.setPadding((int)(12*dp), (int)(10*dp), (int)(12*dp), (int)(10*dp));
        colPct.addView(edtDiscountPct);
        discountRow.addView(colPct);

        // Spacer
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams((int)(16*dp), 1));
        discountRow.addView(spacer);

        // Amount discount
        LinearLayout colAmt = new LinearLayout(this);
        colAmt.setOrientation(LinearLayout.VERTICAL);
        colAmt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView lblAmt = new TextView(this);
        lblAmt.setText("DISCOUNT (LKR)");
        lblAmt.setTextColor(android.graphics.Color.parseColor("#636366"));
        lblAmt.setTextSize(10);
        lblAmt.setTypeface(null, android.graphics.Typeface.BOLD);
        lblAmt.setPadding(0, 0, 0, (int)(6*dp));
        colAmt.addView(lblAmt);

        final EditText edtDiscountAmt = new EditText(this);
        edtDiscountAmt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edtDiscountAmt.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        edtDiscountAmt.setHintTextColor(android.graphics.Color.parseColor("#AEAEB2"));
        edtDiscountAmt.setHint("0.00");
        if (existingItem != null && existingItem.discountAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
            edtDiscountAmt.setText(existingItem.discountAmount.toPlainString());
        } else if (existingItem != null && existingItem.discountVal.compareTo(java.math.BigDecimal.ZERO) > 0) {
            edtDiscountAmt.setText(existingItem.discountVal.toPlainString());
        }
        edtDiscountAmt.setTextSize(15);
        edtDiscountAmt.setTypeface(null, android.graphics.Typeface.BOLD);
        edtDiscountAmt.setBackground(createRoundedBackground("#FFFFFF", "#E0E0E5", 10));
        edtDiscountAmt.setPadding((int)(12*dp), (int)(10*dp), (int)(12*dp), (int)(10*dp));
        colAmt.addView(edtDiscountAmt);
        discountRow.addView(colAmt);

        discountCard.addView(discountRow);
        layout.addView(discountCard);

        // 5. LIVE TOTAL PREVIEW BANNER
        final TextView txtLiveTotal = new TextView(this);
        java.math.BigDecimal currentTotal = (existingItem != null) ? existingItem.total : p.wholesalePrice;
        txtLiveTotal.setText("TOTAL: " + CurrencyUtils.formatLKR(currentTotal));
        txtLiveTotal.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        txtLiveTotal.setTextSize(19);
        txtLiveTotal.setTypeface(null, android.graphics.Typeface.BOLD);
        txtLiveTotal.setBackground(createRoundedBackground("#F2F2F7", "#E5E5EA", 16));
        txtLiveTotal.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));
        txtLiveTotal.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bannerLp.setMargins(0, 0, 0, (int)(16*dp));
        txtLiveTotal.setLayoutParams(bannerLp);
        layout.addView(txtLiveTotal);

        // 6. ACTION BUTTONS ROW
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final Button btnCancel = new Button(this);
        btnCancel.setText("Cancel");
        btnCancel.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        btnCancel.setTextSize(15);
        btnCancel.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCancel.setAllCaps(false);
        btnCancel.setBackground(createRoundedBackground("#F2F2F7", "#F2F2F7", 25));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, (int)(50*dp), 1.0f);
        cancelParams.setMargins(0, 0, (int)(8*dp), 0);
        btnCancel.setLayoutParams(cancelParams);
        btnRow.addView(btnCancel);

        if (existingItem != null) {
            Button btnRemove = new Button(this);
            btnRemove.setText("Remove");
            btnRemove.setTextColor(android.graphics.Color.WHITE);
            btnRemove.setTextSize(15);
            btnRemove.setTypeface(null, android.graphics.Typeface.BOLD);
            btnRemove.setAllCaps(false);
            btnRemove.setBackground(createRoundedBackground("#FF3B30", "#FF3B30", 25));
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, (int)(50*dp), 1.0f);
            removeParams.setMargins((int)(8 * dp), 0, (int)(8 * dp), 0);
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
                                builder.create().dismiss();
                                Toast.makeText(BillingActivity.this, p.name + " removed from cart.", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                }
            });
        } else {
            View spacerBtn = new View(this);
            spacerBtn.setLayoutParams(new LinearLayout.LayoutParams((int)(12 * dp), 1));
            btnRow.addView(spacerBtn);
        }

        final Button btnAction = new Button(this);
        btnAction.setText(existingItem != null ? "Update" : "Add to Cart");
        btnAction.setTextColor(android.graphics.Color.WHITE);
        btnAction.setTextSize(15);
        btnAction.setTypeface(null, android.graphics.Typeface.BOLD);
        btnAction.setAllCaps(false);
        btnAction.setBackground(createRoundedBackground("#1C1C1E", "#1C1C1E", 25));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, (int)(50*dp), 1.5f);
        btnAction.setLayoutParams(actionParams);
        btnRow.addView(btnAction);
        layout.addView(btnRow);

        scrollView.addView(layout);
        builder.setView(scrollView);
        final androidx.appcompat.app.AlertDialog dialog = builder.create();
                dialog.show();
        if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.GONE);
        if (layoutHeader != null) layoutHeader.setVisibility(View.GONE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            RecyclerView list1 = findViewById(R.id.gridProducts);
            RecyclerView list2 = findViewById(R.id.lstProducts);
            android.graphics.RenderEffect blur = android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP);
            if (list1 != null && list1.getVisibility() == View.VISIBLE) list1.setRenderEffect(blur);
            if (list2 != null && list2.getVisibility() == View.VISIBLE) list2.setRenderEffect(blur);
        }
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                if (layoutCartOverlay == null || layoutCartOverlay.getVisibility() != View.VISIBLE) {
                    if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.VISIBLE);
                    if (layoutHeader != null) layoutHeader.setVisibility(View.VISIBLE);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        RecyclerView list1 = findViewById(R.id.gridProducts);
                        RecyclerView list2 = findViewById(R.id.lstProducts);
                        if (list1 != null) list1.setRenderEffect(null);
                        if (list2 != null) list2.setRenderEffect(null);
                    }
                }
            }
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(createRoundedBackground("#FFFFFF", "#FFFFFF", 28));
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnCloseHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        // Control State Management
        final Runnable updateControlsState = new Runnable() {
            @Override
            public void run() {
                boolean hasSelection = !hasVariations || selectedVarIndex[0] != -1;
                
                edtQtyInput.setEnabled(hasSelection);
                btnMinus.setEnabled(hasSelection);
                btnPlus.setEnabled(hasSelection);
                edtDiscountPct.setEnabled(hasSelection);
                edtDiscountAmt.setEnabled(hasSelection);
                edtOverridePrice.setEnabled(hasSelection);
                btnAction.setEnabled(hasSelection);
                
                if (!hasSelection) {
                    btnAction.setText("Select a Variation");
                    btnAction.setBackground(createRoundedBackground("#E5E5EA", "#E5E5EA", 25));
                    btnAction.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                    txtLiveTotal.setText("SELECT A VARIATION");
                    txtLiveTotal.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                } else {
                    btnAction.setText(existingItem != null ? "Update" : "Add to Cart");
                    btnAction.setBackground(createRoundedBackground("#1C1C1E", "#1C1C1E", 25));
                    btnAction.setTextColor(android.graphics.Color.WHITE);
                    txtLiveTotal.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
                    
                    if (hasVariations) {
                        try {
                            org.json.JSONObject vObj = vars.getJSONObject(selectedVarIndex[0]);
                            String varName = vObj.optString("attribute", vObj.optString("option_name", ""));
                            int totalStock = vObj.optInt("qty", vObj.optInt("quantity_on_hand", 0));
                            int reservedStock = dbHelper.getVariationReservedQty(p.id, varName);
                            int cartQty = 0;
                            for (CartItemModel item : cartList) {
                                if (item.productId == p.id && item.selectedVariation.equals(varName)) {
                                    if (existingItem == null || !existingItem.selectedVariation.equals(varName)) {
                                        cartQty += item.quantity;
                                    }
                                }
                            }
                            int available = totalStock - reservedStock - cartQty;
                            if (available <= 0) {
                                btnAction.setEnabled(false);
                                btnAction.setText("Out of Stock");
                                btnAction.setBackground(createRoundedBackground("#FF3B30", "#FF3B30", 25));
                                btnAction.setTextColor(android.graphics.Color.WHITE);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        };

        // Render variation tiles
        final Runnable[] renderVariationTilesHolder = new Runnable[1];
        renderVariationTilesHolder[0] = new Runnable() {
            @Override
            public void run() {
                varLayout.removeAllViews();
                if (!hasVariations || vars == null) return;
                
                try {
                    for (int i = 0; i < vars.length(); i++) {
                        final int index = i;
                        final org.json.JSONObject vObj = vars.getJSONObject(i);
                        final String varName = vObj.optString("attribute", vObj.optString("option_name", ""));
                        final int totalStock = vObj.optInt("qty", vObj.optInt("quantity_on_hand", 0));
                        double calculatedVarPrice = vObj.optDouble("wholesale_price", 0.0);
                        if (calculatedVarPrice <= 0) {
                            calculatedVarPrice = p.wholesalePrice.doubleValue();
                        }
                        if (calculatedVarPrice <= 0) {
                            calculatedVarPrice = vObj.optDouble("price", 0.0);
                        }
                        final double varPrice = calculatedVarPrice;

                        // Calculate real-time available stock
                        int reservedStock = dbHelper.getVariationReservedQty(p.id, varName);
                        int cartQty = 0;
                        for (CartItemModel item : cartList) {
                            if (item.productId == p.id && item.selectedVariation.equals(varName)) {
                                if (existingItem == null || !existingItem.selectedVariation.equals(varName)) {
                                    cartQty += item.quantity;
                                }
                            }
                        }
                        final int available = totalStock - reservedStock - cartQty;
                        
                        // Outer tile layout
                        LinearLayout tile = new LinearLayout(BillingActivity.this);
                        tile.setOrientation(LinearLayout.VERTICAL);
                        tile.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                        
                        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                            (int) (105 * dp),
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        tileParams.setMargins(0, 0, (int) (10 * dp), 0);
                        tile.setLayoutParams(tileParams);
                        tile.setPadding((int) (8 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
                        
                        // Styling: light theme rounded card & selection border
                        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                        gd.setCornerRadius(14 * dp);
                        if (selectedVarIndex[0] == index) {
                            gd.setColor(android.graphics.Color.WHITE);
                            gd.setStroke((int) (2 * dp), android.graphics.Color.parseColor("#1C1C1E"));
                        } else {
                            gd.setColor(android.graphics.Color.parseColor("#F8F8FB"));
                            gd.setStroke((int) (1 * dp), android.graphics.Color.parseColor("#EBEBF0"));
                        }
                        tile.setBackground(gd);
                        
                        // 1. Variation Image
                        ImageView imgVar = new ImageView(BillingActivity.this);
                        LinearLayout.LayoutParams imgVarParams = new LinearLayout.LayoutParams(
                            (int) (56 * dp),
                            (int) (56 * dp)
                        );
                        imgVarParams.setMargins(0, 0, 0, (int) (6 * dp));
                        imgVar.setLayoutParams(imgVarParams);
                        imgVar.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        
                        String varImgPath = vObj.optString("image_path", vObj.optString("image", ""));
                        boolean loaded = false;
                        if (!varImgPath.isEmpty()) {
                            android.graphics.Bitmap bm = getCachedThumbnail(varImgPath, 2);
                            if (bm != null) {
                                imgVar.setImageBitmap(bm);
                                loaded = true;
                            }
                        }
                        if (!loaded && p.localImagePath != null && !p.localImagePath.isEmpty()) {
                            android.graphics.Bitmap bm = getCachedThumbnail(p.localImagePath, 2);
                            if (bm != null) {
                                imgVar.setImageBitmap(bm);
                                loaded = true;
                            }
                        }
                        if (!loaded) {
                            imgVar.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                        tile.addView(imgVar);
                        
                        // 2. Variation Name
                        TextView txtVarName = new TextView(BillingActivity.this);
                        txtVarName.setText(varName);
                        txtVarName.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
                        txtVarName.setTextSize(12);
                        txtVarName.setTypeface(null, android.graphics.Typeface.BOLD);
                        txtVarName.setGravity(android.view.Gravity.CENTER);
                        txtVarName.setSingleLine(true);
                        txtVarName.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        tile.addView(txtVarName);
                        
                        // 3. Stock badge
                        TextView txtStockBadge = new TextView(BillingActivity.this);
                        txtStockBadge.setTextSize(10);
                        txtStockBadge.setTypeface(null, android.graphics.Typeface.BOLD);
                        txtStockBadge.setGravity(android.view.Gravity.CENTER);
                        txtStockBadge.setPadding((int) (6 * dp), (int) (2 * dp), (int) (6 * dp), (int) (2 * dp));
                        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        badgeParams.setMargins(0, (int) (6 * dp), 0, 0);
                        txtStockBadge.setLayoutParams(badgeParams);
                        
                        android.graphics.drawable.GradientDrawable bgBadge = new android.graphics.drawable.GradientDrawable();
                        bgBadge.setCornerRadius(20 * dp);
                        if (available <= 0) {
                            txtStockBadge.setText("OUT OF STOCK");
                            txtStockBadge.setTextColor(android.graphics.Color.WHITE);
                            bgBadge.setColor(android.graphics.Color.parseColor("#FF3B30"));
                        } else {
                            txtStockBadge.setText("STOCK: " + available);
                            txtStockBadge.setTextColor(android.graphics.Color.WHITE);
                            bgBadge.setColor(android.graphics.Color.parseColor("#30D158"));
                        }
                        txtStockBadge.setBackground(bgBadge);
                        tile.addView(txtStockBadge);
                        
                        tile.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                int oldSelection = selectedVarIndex[0];
                                selectedVarIndex[0] = index;
                                if (renderVariationTilesHolder[0] != null) renderVariationTilesHolder[0].run();
                                updateControlsState.run();
                                
                                if (oldSelection != index) {
                                    edtOverridePrice.setText(String.format(Locale.getDefault(), "%.2f", varPrice));
                                    edtQtyInput.requestFocus();
                                    edtQtyInput.selectAll();
                                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                    if (imm != null) {
                                        imm.showSoftInput(edtQtyInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                                    }
                                }
                            }
                        });
                        
                        varLayout.addView(tile);
                    }
                } catch (Exception e) {
                    android.util.Log.e("BillingActivity", "Error rendering variations: " + e.getMessage());
                }
            }
        };

        // Focus & soft keyboard on launch if no variations
        if (!hasVariations) {
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
        }

        final boolean[] percentActiveHolder = new boolean[]{ existingItem != null && existingItem.isPercentDiscountActive };

        // Live preview update
        final Runnable updatePreview = new Runnable() {
            private boolean isUpdating = false;
            @Override
            public void run() {
                if (isUpdating) return;
                isUpdating = true;
                try {
                    String overridePriceStr = edtOverridePrice.getText().toString().trim();
                    java.math.BigDecimal uPrice = overridePriceStr.isEmpty() ? p.wholesalePrice : CurrencyUtils.toBigDecimal(overridePriceStr);

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
                    
                    if (hasVariations && selectedVarIndex[0] == -1) {
                        txtLiveTotal.setText("SELECT A VARIATION");
                    } else {
                        txtLiveTotal.setText("TOTAL: " + CurrencyUtils.formatLKR(finalTotal));
                    }
                } catch (Exception ignored) {}
                isUpdating = false;
            }
        };

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
                    java.math.BigDecimal uPrice = overridePriceStr.isEmpty() ? p.wholesalePrice : CurrencyUtils.toBigDecimal(overridePriceStr);

                    String qtyStr = edtQtyInput.getText().toString().trim();
                    int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);

                    java.math.BigDecimal pct = java.math.BigDecimal.ZERO;
                    String pctStr = edtDiscountPct.getText().toString().trim();
                    if (!pctStr.isEmpty()) pct = CurrencyUtils.toBigDecimal(pctStr);

                    java.math.BigDecimal amt = java.math.BigDecimal.ZERO;
                    String amtStr = edtDiscountAmt.getText().toString().trim();
                    if (!amtStr.isEmpty()) amt = CurrencyUtils.toBigDecimal(amtStr);

                    String selectedVar = "";
                    int selectedVarId = 0;
                    if (hasVariations && selectedVarIndex[0] != -1 && vars != null) {
                        org.json.JSONObject vObj = vars.getJSONObject(selectedVarIndex[0]);
                        selectedVar = vObj.optString("attribute", vObj.optString("option_name", ""));
                        selectedVarId = vObj.optInt("id", 0);
                    }

                    if (hasVariations && selectedVarIndex[0] == -1) {
                        Toast.makeText(BillingActivity.this, "Please select a variation.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int left;
                    if (hasVariations) {
                        org.json.JSONObject vObj = vars.getJSONObject(selectedVarIndex[0]);
                        int totalStock = vObj.optInt("qty", vObj.optInt("quantity_on_hand", 0));
                        int reservedStock = dbHelper.getVariationReservedQty(p.id, selectedVar);
                        int cartQty = 0;
                        for (CartItemModel item : cartList) {
                            if (item.productId == p.id && item.selectedVariation.equals(selectedVar)) {
                                if (existingItem == null || !existingItem.selectedVariation.equals(selectedVar)) {
                                    cartQty += item.quantity;
                                }
                            }
                        }
                        left = totalStock - reservedStock - cartQty;
                    } else {
                        left = p.qtyOnHand - p.qtyReserved;
                        int cartQty = 0;
                        for (CartItemModel item : cartList) {
                            if (item.productId == p.id) {
                                if (existingItem == null) {
                                    cartQty += item.quantity;
                                }
                            }
                        }
                        left -= cartQty;
                    }

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
                        existingItem.variationOptionId = selectedVarId;
                        
                        if (hasVariations && !selectedVar.isEmpty()) {
                            existingItem.name = p.name + " - " + selectedVar;
                        } else {
                            existingItem.name = p.name;
                        }

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
                        checkAndPromptItemDiscountsOnAddToCart();
                        showCartOverlay(checkoutMode);
                        dialog.dismiss();
                        Toast.makeText(BillingActivity.this, "Cart updated.", Toast.LENGTH_SHORT).show();
                    } else {
                        CartItemModel duplicate = null;
                        for (CartItemModel item : cartList) {
                            boolean matches = false;
                            if (item.productId == p.id) {
                                if (hasVariations) {
                                    matches = item.selectedVariation != null && item.selectedVariation.equals(selectedVar);
                                } else {
                                    matches = (item.selectedVariation == null || item.selectedVariation.isEmpty());
                                }
                            }
                            if (matches) {
                                duplicate = item;
                                break;
                            }
                        }

                        final String finalSelectedVar = selectedVar;
                        final int finalSelectedVarId = selectedVarId;
                        final java.math.BigDecimal finalUPrice = uPrice;
                        final java.math.BigDecimal finalPct = pct;
                        final java.math.BigDecimal finalAmt = amt;
                        final boolean finalIsPercent = percentActiveHolder[0];
                        final int finalQty = qty;

                        if (duplicate != null) {
                            androidx.appcompat.app.AlertDialog.Builder warnBuilder = new androidx.appcompat.app.AlertDialog.Builder(BillingActivity.this);
                            
                            LinearLayout warnLayout = new LinearLayout(BillingActivity.this);
                            warnLayout.setOrientation(LinearLayout.VERTICAL);
                            warnLayout.setPadding((int)(24*dp), (int)(24*dp), (int)(24*dp), (int)(24*dp));
                            warnLayout.setBackgroundColor(android.graphics.Color.WHITE);
                            
                            TextView titleText = new TextView(BillingActivity.this);
                            titleText.setText("Item Already in Cart");
                            titleText.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
                            titleText.setTextSize(18);
                            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
                            titleText.setPadding(0, 0, 0, (int)(12*dp));
                            warnLayout.addView(titleText);
                            
                            TextView messageText = new TextView(BillingActivity.this);
                            messageText.setText("This item is already in your cart. Would you like to merge the quantity into the existing item, keep both as separate line items, or cancel?");
                            messageText.setTextColor(android.graphics.Color.parseColor("#636366"));
                            messageText.setTextSize(14);
                            messageText.setPadding(0, 0, 0, (int)(20*dp));
                            warnLayout.addView(messageText);
                            
                            LinearLayout warnBtnRow = new LinearLayout(BillingActivity.this);
                            warnBtnRow.setOrientation(LinearLayout.HORIZONTAL);
                            
                            Button btnMerge = new Button(BillingActivity.this);
                            btnMerge.setText("Merge Qty");
                            btnMerge.setAllCaps(false);
                            btnMerge.setTextColor(android.graphics.Color.WHITE);
                            btnMerge.setBackground(createRoundedBackground("#1C1C1E", "#1C1C1E", 20));
                            LinearLayout.LayoutParams mergeParams = new LinearLayout.LayoutParams(0, (int)(46*dp), 1.0f);
                            mergeParams.setMargins(0, 0, (int)(8*dp), 0);
                            btnMerge.setLayoutParams(mergeParams);
                            
                            Button btnKeepBoth = new Button(BillingActivity.this);
                            btnKeepBoth.setText("Keep Both");
                            btnKeepBoth.setAllCaps(false);
                            btnKeepBoth.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
                            btnKeepBoth.setBackground(createRoundedBackground("#F2F2F7", "#F2F2F7", 20));
                            LinearLayout.LayoutParams keepParams = new LinearLayout.LayoutParams(0, (int)(46*dp), 1.0f);
                            keepParams.setMargins(0, 0, (int)(8*dp), 0);
                            btnKeepBoth.setLayoutParams(keepParams);
                            
                            Button btnCancelWarn = new Button(BillingActivity.this);
                            btnCancelWarn.setText("Cancel");
                            btnCancelWarn.setAllCaps(false);
                            btnCancelWarn.setTextColor(android.graphics.Color.parseColor("#636366"));
                            btnCancelWarn.setBackground(createRoundedBackground("#FFFFFF", "#E5E5EA", 20));
                            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, (int)(46*dp), 1.0f);
                            btnCancelWarn.setLayoutParams(cancelParams);
                            
                            warnBtnRow.addView(btnMerge);
                            warnBtnRow.addView(btnKeepBoth);
                            warnBtnRow.addView(btnCancelWarn);
                            warnLayout.addView(warnBtnRow);
                            
                            warnBuilder.setView(warnLayout);
                            final androidx.appcompat.app.AlertDialog warnDialog = warnBuilder.create();
                            warnDialog.show();
                            if (warnDialog.getWindow() != null) {
                                warnDialog.getWindow().setBackgroundDrawable(createRoundedBackground("#FFFFFF", "#FFFFFF", 24));
                            }
                            
                            btnMerge.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    addToCartCustom(p, finalQty, finalUPrice, finalPct, finalAmt, finalIsPercent, finalSelectedVar, finalSelectedVarId, false);
                                    
                                    itemAdded[0] = true;
                                    btnCancel.setText("Done");
                                    edtQtyInput.setText("1");
                                    edtDiscountPct.setText("");
                                    edtDiscountAmt.setText("");
                                    if (hasVariations) {
                                        selectedVarIndex[0] = -1;
                                        edtOverridePrice.setText(p.wholesalePrice.toPlainString());
                                    }
                                    if (renderVariationTilesHolder[0] != null) renderVariationTilesHolder[0].run();
                                    updateControlsState.run();
                                    updatePreview.run();
                                    warnDialog.dismiss();
                                    if (!hasVariations) {
                                        dialog.dismiss();
                                    }
                                }
                            });

                            btnKeepBoth.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    addToCartCustom(p, finalQty, finalUPrice, finalPct, finalAmt, finalIsPercent, finalSelectedVar, finalSelectedVarId, true);
                                    
                                    itemAdded[0] = true;
                                    btnCancel.setText("Done");
                                    edtQtyInput.setText("1");
                                    edtDiscountPct.setText("");
                                    edtDiscountAmt.setText("");
                                    if (hasVariations) {
                                        selectedVarIndex[0] = -1;
                                        edtOverridePrice.setText(p.wholesalePrice.toPlainString());
                                    }
                                    if (renderVariationTilesHolder[0] != null) renderVariationTilesHolder[0].run();
                                    updateControlsState.run();
                                    updatePreview.run();
                                    warnDialog.dismiss();
                                    if (!hasVariations) {
                                        dialog.dismiss();
                                    }
                                }
                            });
                            
                            btnCancelWarn.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    warnDialog.dismiss();
                                }
                            });
                        } else {
                            addToCartCustom(p, qty, uPrice, pct, amt, percentActiveHolder[0], selectedVar, selectedVarId, false);
                            
                            itemAdded[0] = true;
                            btnCancel.setText("Done");
                            edtQtyInput.setText("1");
                            edtDiscountPct.setText("");
                            edtDiscountAmt.setText("");
                            if (hasVariations) {
                                selectedVarIndex[0] = -1;
                                edtOverridePrice.setText(p.wholesalePrice.toPlainString());
                            }
                            if (renderVariationTilesHolder[0] != null) renderVariationTilesHolder[0].run();
                            updateControlsState.run();
                            updatePreview.run();
                            if (!hasVariations) {
                                dialog.dismiss();
                            }
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(BillingActivity.this, "Invalid inputs entered.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Initialize state
        if (renderVariationTilesHolder[0] != null) renderVariationTilesHolder[0].run();
        updateControlsState.run();
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
            p.price = CurrencyUtils.toBigDecimal(cursor.getDouble(cursor.getColumnIndexOrThrow("wholesale_price")));
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
        addToCartCustom(p, qty, customPrice, discountPercent, discountAmount, isPercentActive, "", 0, false);
    }

    private void addToCartCustom(ProductModel p, int qty, java.math.BigDecimal customPrice, java.math.BigDecimal discountPercent, java.math.BigDecimal discountAmount, boolean isPercentActive, String selectedVar, int selectedVarId) {
        addToCartCustom(p, qty, customPrice, discountPercent, discountAmount, isPercentActive, selectedVar, selectedVarId, false);
    }

    private void addToCartCustom(ProductModel p, int qty, java.math.BigDecimal customPrice, java.math.BigDecimal discountPercent, java.math.BigDecimal discountAmount, boolean isPercentActive, String selectedVar, int selectedVarId, boolean forceNew) {
        boolean hasVariations = hasProductVariations(p);
        int left;
        if (hasVariations) {
            int totalStock = 0;
            try {
                org.json.JSONArray vars = new org.json.JSONArray(p.variationsJson);
                for (int i = 0; i < vars.length(); i++) {
                    org.json.JSONObject vObj = vars.getJSONObject(i);
                    String varName = vObj.optString("attribute", vObj.optString("option_name", ""));
                    if (varName.equals(selectedVar)) {
                        totalStock = vObj.optInt("qty", vObj.optInt("quantity_on_hand", 0));
                        break;
                    }
                }
            } catch (Exception ignored) {}
            int reservedStock = dbHelper.getVariationReservedQty(p.id, selectedVar);
            int cartQty = 0;
            for (CartItemModel item : cartList) {
                if (item.productId == p.id && item.selectedVariation.equals(selectedVar)) {
                    cartQty += item.quantity;
                }
            }
            left = totalStock - reservedStock - cartQty;
        } else {
            left = p.qtyOnHand - p.qtyReserved;
            int cartQty = 0;
            for (CartItemModel item : cartList) {
                if (item.productId == p.id) {
                    cartQty += item.quantity;
                }
            }
            left -= cartQty;
        }

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

        if (!forceNew) {
            for (CartItemModel item : cartList) {
                boolean matches = false;
                if (item.productId == p.id) {
                    if (hasVariations) {
                        matches = item.selectedVariation.equals(selectedVar);
                    } else {
                        matches = (item.selectedVariation == null || item.selectedVariation.isEmpty());
                    }
                }
                if (matches) {
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
        }

        if (!found) {
            CartItemModel item = new CartItemModel();
            item.productId = p.id;
            if (hasVariations && !selectedVar.isEmpty()) {
                item.name = p.name + " - " + selectedVar;
            } else {
                item.name = p.name;
            }
            item.price = p.price;
            item.customPrice = customPrice;
            item.discountPercent = discountPercent;
            item.discountAmount = discountAmount;
            item.isPercentDiscountActive = isPercentActive;
            item.discountVal = discountVal;
            item.quantity = qty;
            item.activePrice = customPrice;
            item.total = total;
            item.selectedVariation = selectedVar;
            item.variationOptionId = selectedVarId;
            cartList.add(item);
        }

        recalculateCart();
        checkAndPromptItemDiscountsOnAddToCart();
        
        String displayName = p.name;
        if (!selectedVar.isEmpty()) {
            displayName += " - " + selectedVar;
        }
        Toast.makeText(BillingActivity.this, displayName + " added to cart.", Toast.LENGTH_SHORT).show();
    }

    private class ProductGridRecyclerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ProductGridRecyclerAdapter.GridViewHolder> {
        @androidx.annotation.NonNull
        @Override
        public GridViewHolder onCreateViewHolder(@androidx.annotation.NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(BillingActivity.this).inflate(R.layout.item_product_grid, parent, false);
            return new GridViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull GridViewHolder holder, int position) {
            final ProductModel p = productList.get(position);

            // Item 1: Full Square Image (1:1 aspect ratio handled automatically by SquareMaterialCardView)

            holder.lblProductNameGrid.setText(p.name);
            holder.lblCategoryGrid.setText(p.category);

            final java.math.BigDecimal currentPrice = p.price;
            holder.lblPriceGrid.setText(String.format(Locale.getDefault(), "LKR %.2f", currentPrice.doubleValue()));

            final int available = p.qtyOnHand - p.qtyReserved;
            holder.lblStockGrid.setText("Stock: " + available);

            if (available <= 0) {
                holder.lblStockGrid.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                holder.lblStockGrid.setText("Out of Stock");
            } else {
                holder.lblStockGrid.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }

            if (holder.imgProductGrid != null) {
                if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
                    android.graphics.Bitmap cached = getCachedThumbnail(p.localImagePath, 2);
                    if (cached != null) {
                        holder.imgProductGrid.setImageBitmap(cached);
                    } else {
                        holder.imgProductGrid.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                } else {
                    holder.imgProductGrid.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            }

            if (holder.layoutVariationsGrid != null) {
                populateVariationChips(holder.layoutVariationsGrid, p);
            }

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showProductConfigDialog(p);
                }
            });

            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showProductQuickViewDialog(p);
                    return true;
                }
            });
        }

        @Override
        public int getItemCount() {
            return productList.size();
        }

        class GridViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            View cardImgContainerGrid;
            ImageView imgProductGrid;
            TextView lblProductNameGrid, lblCategoryGrid, lblPriceGrid, lblStockGrid;
            LinearLayout layoutVariationsGrid;

            GridViewHolder(View itemView) {
                super(itemView);
                cardImgContainerGrid = itemView.findViewById(R.id.cardImgContainerGrid);
                imgProductGrid = itemView.findViewById(R.id.imgProductGrid);
                lblProductNameGrid = itemView.findViewById(R.id.lblProductNameGrid);
                lblCategoryGrid = itemView.findViewById(R.id.lblCategoryGrid);
                lblPriceGrid = itemView.findViewById(R.id.lblPriceGrid);
                lblStockGrid = itemView.findViewById(R.id.lblStockGrid);
                layoutVariationsGrid = itemView.findViewById(R.id.layoutVariationsGrid);
            }
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
            final com.google.android.material.button.MaterialButton btn = new com.google.android.material.button.MaterialButton(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (int) (38 * getResources().getDisplayMetrics().density)
            );
            params.setMargins(0, 0, (int) (8 * getResources().getDisplayMetrics().density), 0);
            btn.setLayoutParams(params);
            btn.setPadding((int) (18 * getResources().getDisplayMetrics().density), 0, (int) (18 * getResources().getDisplayMetrics().density), 0);
            btn.setText(catName);
            btn.setTextSize(13);
            btn.setAllCaps(false);
            btn.setCornerRadius((int) (19 * getResources().getDisplayMetrics().density));
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            btn.setStrokeWidth(0);
            btn.setElevation(0);
            
            // Set style based on selection matching new UI (White / Monochrome iOS theme)
            if (catName.equalsIgnoreCase(selectedCategoryName)) {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#000000")));
                btn.setTextColor(android.graphics.Color.WHITE);
            } else {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F2F2F7")));
                btn.setTextColor(android.graphics.Color.parseColor("#000000"));
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

    private List<String> getMissingCustomerFields(CustomerModel customer) {
        List<String> missing = new ArrayList<>();
        if (customer == null) return missing;
        if (customer.name == null || customer.name.trim().isEmpty()) {
            missing.add("Shop Name");
        }
        if (customer.address == null || customer.address.trim().isEmpty()) {
            missing.add("Address");
        }

        // Fetch active route started today
        String activeRouteName = "";
        try {
            Cursor cRoute = dbHelper.getActiveRoute();
            if (cRoute != null) {
                if (cRoute.moveToFirst()) {
                    activeRouteName = cRoute.getString(cRoute.getColumnIndexOrThrow("route_name"));
                }
                cRoute.close();
            }
        } catch (Exception e) {
            android.util.Log.e("BillingActivity", "Error getting active route name: " + e.getMessage());
        }

        boolean routeMismatch = false;
        if (customer.mcaName != null && !customer.mcaName.trim().isEmpty()) {
            if (!activeRouteName.isEmpty() && !customer.mcaName.equalsIgnoreCase(activeRouteName)) {
                routeMismatch = true;
            }
        } else if (customer.territory != null && !customer.territory.trim().isEmpty()) {
            if (!activeRouteName.isEmpty() && !customer.territory.equalsIgnoreCase(activeRouteName)) {
                routeMismatch = true;
            }
        }

        if (customer.territory == null || customer.territory.trim().isEmpty() || routeMismatch) {
            missing.add("Route");
        }
        if (customer.phone == null || customer.phone.trim().isEmpty()) {
            missing.add("Phone Number");
        }
        if (customer.whatsapp == null || customer.whatsapp.trim().isEmpty()) {
            missing.add("WhatsApp Number");
        }
        if (customer.mcaName == null || customer.mcaName.trim().isEmpty() || routeMismatch) {
            missing.add("MCA");
        }
        if (customer.latitude == 0.0 && customer.longitude == 0.0) {
            missing.add("Location");
        }
        return missing;
    }

    private void proceedWithSelectedCustomer(android.app.Dialog customerSelectionDialog) {
        if (selectedCustomer == null) return;
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
        if (customerSelectionDialog != null) {
            customerSelectionDialog.dismiss();
        }
    }

    private void validateSelectedCustomerAndPrompt(final android.app.Dialog customerSelectionDialog) {
        if (selectedCustomer != null) {
            List<String> missing = getMissingCustomerFields(selectedCustomer);
            if (!missing.isEmpty()) {
                showCustomerInfoCompletionDialog(selectedCustomer, missing, customerSelectionDialog);
            } else {
                proceedWithSelectedCustomer(customerSelectionDialog);
            }
        }
    }

    private void showCustomerInfoCompletionDialog(final CustomerModel customer, final List<String> missingFields, final android.app.Dialog customerSelectionDialog) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(BillingActivity.this);
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(BillingActivity.this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setBackgroundColor(android.graphics.Color.WHITE);
        
        LinearLayout layout = new LinearLayout(BillingActivity.this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        scrollView.addView(layout);
        
        TextView titleView = new TextView(BillingActivity.this);
        titleView.setText("Complete Customer Profile");
        titleView.setTextSize(18);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(android.graphics.Color.parseColor("#000000"));
        titleView.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        layout.addView(titleView);
        
        TextView descView = new TextView(BillingActivity.this);
        descView.setText("This customer has missing information. Please fill in the required details before invoicing.");
        descView.setTextSize(13);
        descView.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
        descView.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, (int) (8 * getResources().getDisplayMetrics().density), 0, (int) (18 * getResources().getDisplayMetrics().density));
        descView.setLayoutParams(descParams);
        layout.addView(descView);

        final EditText edtName = new EditText(BillingActivity.this);
        final android.widget.Spinner spnRoute = new android.widget.Spinner(BillingActivity.this);
        final EditText edtPhone = new EditText(BillingActivity.this);
        edtPhone.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});
        final EditText edtWhatsApp = new EditText(BillingActivity.this);
        edtWhatsApp.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});
        final EditText edtAddress = new EditText(BillingActivity.this);
        
        final TextView txtLocationCoords = new TextView(BillingActivity.this);
        final double[] capturedCoords = { customer.latitude, customer.longitude };
        
        boolean routeSpinnerAdded = false;
        
        for (String field : missingFields) {
            if (field.equals("Route") || field.equals("MCA")) {
                if (!routeSpinnerAdded) {
                    TextView label = new TextView(BillingActivity.this);
                    label.setText("Territory Route");
                    label.setTextSize(13);
                    label.setTypeface(null, android.graphics.Typeface.BOLD);
                    label.setTextColor(android.graphics.Color.parseColor("#000000"));
                    LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    labelParams.setMargins(0, (int) (8 * getResources().getDisplayMetrics().density), 0, (int) (6 * getResources().getDisplayMetrics().density));
                    label.setLayoutParams(labelParams);
                    layout.addView(label);
                    
                    List<String> routesList = dbHelper.getTerritories();
                    if (routesList.isEmpty()) {
                        routesList.add("No routes available");
                    }
                    android.widget.ArrayAdapter<String> routeAdapter = new android.widget.ArrayAdapter<String>(BillingActivity.this, android.R.layout.simple_spinner_item, routesList) {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            View v = super.getView(position, convertView, parent);
                            if (v instanceof TextView) {
                                ((TextView) v).setTextColor(android.graphics.Color.parseColor("#000000"));
                                ((TextView) v).setTextSize(14);
                            }
                            return v;
                        }
                        @Override
                        public View getDropDownView(int position, View convertView, ViewGroup parent) {
                            View v = super.getDropDownView(position, convertView, parent);
                            v.setBackgroundColor(android.graphics.Color.parseColor("#F2F2F7"));
                            if (v instanceof TextView) {
                                ((TextView) v).setTextColor(android.graphics.Color.parseColor("#000000"));
                                ((TextView) v).setTextSize(14);
                            }
                            return v;
                        }
                    };
                    routeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spnRoute.setAdapter(routeAdapter);
                    
                    // Resolve today's active route to preselect it if mismatched or empty
                    String activeRouteName = "";
                    try {
                        Cursor cRoute = dbHelper.getActiveRoute();
                        if (cRoute != null) {
                            if (cRoute.moveToFirst()) {
                                activeRouteName = cRoute.getString(cRoute.getColumnIndexOrThrow("route_name"));
                            }
                            cRoute.close();
                        }
                    } catch (Exception e) {
                        android.util.Log.e("BillingActivity", "Error getting active route name: " + e.getMessage());
                    }

                    String routeToSelect = customer.mcaName;
                    if (routeToSelect == null || routeToSelect.trim().isEmpty() || (!activeRouteName.isEmpty() && !routeToSelect.equalsIgnoreCase(activeRouteName))) {
                        routeToSelect = activeRouteName;
                    }

                    if (routeToSelect != null && !routeToSelect.trim().isEmpty()) {
                        int pos = routesList.indexOf(routeToSelect);
                        if (pos != -1) {
                            spnRoute.setSelection(pos);
                        }
                    }

                    styleSpinner(spnRoute);
                    layout.addView(spnRoute);
                    routeSpinnerAdded = true;
                }
            } else {
                TextView label = new TextView(BillingActivity.this);
                label.setText(field);
                label.setTextSize(13);
                label.setTypeface(null, android.graphics.Typeface.BOLD);
                label.setTextColor(android.graphics.Color.parseColor("#000000"));
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                labelParams.setMargins(0, (int) (8 * getResources().getDisplayMetrics().density), 0, (int) (6 * getResources().getDisplayMetrics().density));
                label.setLayoutParams(labelParams);
                layout.addView(label);
                
                if (field.equals("Shop Name")) {
                    edtName.setText(customer.name != null ? customer.name : "");
                    styleEditText(edtName, "Enter Shop Name");
                    layout.addView(edtName);
                } else if (field.equals("Address")) {
                    edtAddress.setText(customer.address != null ? customer.address : "");
                    styleEditText(edtAddress, "Enter Address");
                    layout.addView(edtAddress);
                } else if (field.equals("Phone Number")) {
                    edtPhone.setText(customer.phone != null ? customer.phone : "");
                    edtPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
                    styleEditText(edtPhone, "Enter Phone Number");
                    layout.addView(edtPhone);
                } else if (field.equals("WhatsApp Number")) {
                    edtWhatsApp.setText(customer.whatsapp != null ? customer.whatsapp : "");
                    edtWhatsApp.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
                    styleEditText(edtWhatsApp, "Enter WhatsApp Number");
                    layout.addView(edtWhatsApp);
                } else if (field.equals("Location")) {
                    LinearLayout locLayout = new LinearLayout(BillingActivity.this);
                    locLayout.setOrientation(LinearLayout.VERTICAL);
                    locLayout.setPadding((int) (14 * getResources().getDisplayMetrics().density), (int) (14 * getResources().getDisplayMetrics().density), (int) (14 * getResources().getDisplayMetrics().density), (int) (14 * getResources().getDisplayMetrics().density));
                    android.graphics.drawable.GradientDrawable locBg = new android.graphics.drawable.GradientDrawable();
                    locBg.setColor(android.graphics.Color.parseColor("#F2F2F7"));
                    locBg.setCornerRadius(14 * getResources().getDisplayMetrics().density);
                    locLayout.setBackground(locBg);
                    
                    txtLocationCoords.setText(capturedCoords[0] != 0.0 ? String.format("GPS: %.5f, %.5f (Stored)", capturedCoords[0], capturedCoords[1]) : "GPS: Coordinates Missing");
                    txtLocationCoords.setTextSize(13);
                    txtLocationCoords.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                    locLayout.addView(txtLocationCoords);
                    
                    Button btnGetGps = new Button(BillingActivity.this);
                    btnGetGps.setText("Get GPS Location");
                    btnGetGps.setTextSize(12);
                    btnGetGps.setAllCaps(false);
                    btnGetGps.setTextColor(android.graphics.Color.WHITE);
                    btnGetGps.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#000000")));
                    LinearLayout.LayoutParams btnGpsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    btnGpsParams.setMargins(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
                    btnGetGps.setLayoutParams(btnGpsParams);
                    
                    btnGetGps.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            txtLocationCoords.setText("GPS: Acquiring coordinates...");
                            LocationHelper.captureCurrentLocationStrict(BillingActivity.this, new LocationHelper.LocationResultListener() {
                                @Override
                                public void onLocationResult(double latitude, double longitude, boolean isFallback) {
                                    if (latitude == 0.0 && longitude == 0.0) {
                                        txtLocationCoords.setText("GPS: Location Unavailable");
                                        new androidx.appcompat.app.AlertDialog.Builder(BillingActivity.this)
                                            .setTitle("GPS Location Unavailable")
                                            .setMessage("GPS is currently unavailable. Please ensure that Location services are turned on in your device system settings and that you are in a location with clear GPS reception.")
                                            .setPositiveButton("OK", null)
                                            .show();
                                    } else {
                                        capturedCoords[0] = latitude;
                                        capturedCoords[1] = longitude;
                                        txtLocationCoords.setText(String.format("GPS: %.5f, %.5f (Live)", latitude, longitude));
                                        Toast.makeText(BillingActivity.this, "GPS Location Tagged!", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    });
                    
                    locLayout.addView(btnGetGps);
                    layout.addView(locLayout);
                }
            }
        }
        
        LinearLayout buttonsLayout = new LinearLayout(BillingActivity.this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLayoutParams.setMargins(0, (int) (20 * getResources().getDisplayMetrics().density), 0, 0);
        buttonsLayout.setLayoutParams(btnLayoutParams);
        
        Button btnCancel = new Button(BillingActivity.this);
        btnCancel.setText("Cancel");
        btnCancel.setTextSize(14);
        btnCancel.setAllCaps(false);
        btnCancel.setTextColor(android.graphics.Color.parseColor("#000000"));
        btnCancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F2F2F7")));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        cancelParams.setMargins(0, 0, (int) (8 * getResources().getDisplayMetrics().density), 0);
        btnCancel.setLayoutParams(cancelParams);
        
        Button btnSave = new Button(BillingActivity.this);
        btnSave.setText("Save & Continue");
        btnSave.setTextSize(14);
        btnSave.setAllCaps(false);
        btnSave.setTextColor(android.graphics.Color.WHITE);
        btnSave.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#000000")));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        btnSave.setLayoutParams(saveParams);
        
        buttonsLayout.addView(btnCancel);
        buttonsLayout.addView(btnSave);
        layout.addView(buttonsLayout);
        
        builder.setView(scrollView);
        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
                dialog.show();
        if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.GONE);
        if (layoutHeader != null) layoutHeader.setVisibility(View.GONE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            RecyclerView list1 = findViewById(R.id.gridProducts);
            RecyclerView list2 = findViewById(R.id.lstProducts);
            android.graphics.RenderEffect blur = android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP);
            if (list1 != null && list1.getVisibility() == View.VISIBLE) list1.setRenderEffect(blur);
            if (list2 != null && list2.getVisibility() == View.VISIBLE) list2.setRenderEffect(blur);
        }
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                if (layoutCartOverlay == null || layoutCartOverlay.getVisibility() != View.VISIBLE) {
                    if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.VISIBLE);
                    if (layoutHeader != null) layoutHeader.setVisibility(View.VISIBLE);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        RecyclerView list1 = findViewById(R.id.gridProducts);
                        RecyclerView list2 = findViewById(R.id.lstProducts);
                        if (list1 != null) list1.setRenderEffect(null);
                        if (list2 != null) list2.setRenderEffect(null);
                    }
                }
            }
        });
        
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                Toast.makeText(BillingActivity.this, "Billing requires all mandatory profile fields.", Toast.LENGTH_LONG).show();
            }
        });
        
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String shopName = edtName.getText().toString().trim();
                String address = edtAddress.getText().toString().trim();
                String phone = edtPhone.getText().toString().trim();
                String whatsapp = edtWhatsApp.getText().toString().trim();
                
                boolean hasRouteSpinner = missingFields.contains("Route") || missingFields.contains("MCA");
                String selectedRoute = "";
                if (hasRouteSpinner) {
                    if (spnRoute.getSelectedItem() != null) {
                        selectedRoute = spnRoute.getSelectedItem().toString().trim();
                    }
                }
                
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues cv = new ContentValues();
                
                if (missingFields.contains("Shop Name") && !shopName.isEmpty()) {
                    cv.put("name", shopName);
                    customer.name = shopName;
                }
                if (missingFields.contains("Address") && !address.isEmpty()) {
                    cv.put("address", address);
                    customer.address = address;
                }
                if (hasRouteSpinner && !selectedRoute.isEmpty() && !selectedRoute.equalsIgnoreCase("No routes available")) {
                    String territoryName = dbHelper.getMainAreaNameByRoute(selectedRoute);
                    cv.put("mca_name", selectedRoute);
                    cv.put("territory", territoryName);
                    customer.mcaName = selectedRoute;
                    customer.territory = territoryName;
                }
                if (missingFields.contains("Phone Number") && !phone.isEmpty()) {
                    cv.put("phone", phone);
                    customer.phone = phone;
                }
                if (missingFields.contains("WhatsApp Number") && !whatsapp.isEmpty()) {
                    cv.put("whatsapp", whatsapp);
                    customer.whatsapp = whatsapp;
                }
                if (missingFields.contains("Location") && capturedCoords[0] != 0.0) {
                    cv.put("latitude", capturedCoords[0]);
                    cv.put("longitude", capturedCoords[1]);
                    customer.latitude = capturedCoords[0];
                    customer.longitude = capturedCoords[1];
                }
                
                if (cv.size() > 0) {
                    cv.put("is_synced", 0);
                    cv.put("is_profile_synced", 0);
                    cv.put("sync_source", "Billing Customer Information Completion");
                    db.update("customers", cv, "id = ?", new String[]{String.valueOf(customer.id)});
                }
                
                dialog.dismiss();
                proceedWithSelectedCustomer(customerSelectionDialog);
            }
        });
    }

    private void styleEditText(EditText editText, String hint) {
        editText.setHint(hint);
        editText.setHintTextColor(android.graphics.Color.parseColor("#8E8E93"));
        editText.setTextColor(android.graphics.Color.parseColor("#000000"));
        editText.setTextSize(14);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(android.graphics.Color.parseColor("#F2F2F7"));
        gd.setCornerRadius(12 * getResources().getDisplayMetrics().density);
        editText.setBackground(gd);
        int padding = (int) (14 * getResources().getDisplayMetrics().density);
        editText.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, (int) (12 * getResources().getDisplayMetrics().density));
        editText.setLayoutParams(params);
    }

    private void styleSpinner(android.widget.Spinner spinner) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(android.graphics.Color.parseColor("#F2F2F7"));
        gd.setCornerRadius(12 * getResources().getDisplayMetrics().density);
        spinner.setBackground(gd);
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        spinner.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, (int) (12 * getResources().getDisplayMetrics().density));
        spinner.setLayoutParams(params);
    }

    private static class CustomerSelectViewHolder extends RecyclerView.ViewHolder {
        TextView lblCustSelectName;
        TextView lblCustSelectRoute;
        TextView lblCustSelectDot;
        TextView lblCustSelectPhone;
        TextView lblCustSelectAddress;
        TextView lblCustSelectBal;
        com.google.android.material.card.MaterialCardView cardCustSelectBal;

        CustomerSelectViewHolder(View itemView) {
            super(itemView);
            lblCustSelectName = itemView.findViewById(R.id.lblCustSelectName);
            lblCustSelectRoute = itemView.findViewById(R.id.lblCustSelectRoute);
            lblCustSelectDot = itemView.findViewById(R.id.lblCustSelectDot);
            lblCustSelectPhone = itemView.findViewById(R.id.lblCustSelectPhone);
            lblCustSelectAddress = itemView.findViewById(R.id.lblCustSelectAddress);
            lblCustSelectBal = itemView.findViewById(R.id.lblCustSelectBal);
            cardCustSelectBal = itemView.findViewById(R.id.cardCustSelectBal);
        }
    }

    private void showCustomerSelectionDialog() {
        final android.app.Dialog dialog = new android.app.Dialog(BillingActivity.this, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_select_customer);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE));
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();

        if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.GONE);
        if (layoutHeader != null) layoutHeader.setVisibility(View.GONE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            RecyclerView list1 = findViewById(R.id.gridProducts);
            RecyclerView list2 = findViewById(R.id.lstProducts);
            android.graphics.RenderEffect blur = android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP);
            if (list1 != null && list1.getVisibility() == View.VISIBLE) list1.setRenderEffect(blur);
            if (list2 != null && list2.getVisibility() == View.VISIBLE) list2.setRenderEffect(blur);
        }
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                if (layoutCartOverlay == null || layoutCartOverlay.getVisibility() != View.VISIBLE) {
                    if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.VISIBLE);
                    if (layoutHeader != null) layoutHeader.setVisibility(View.VISIBLE);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        RecyclerView list1 = findViewById(R.id.gridProducts);
                        RecyclerView list2 = findViewById(R.id.lstProducts);
                        if (list1 != null) list1.setRenderEffect(null);
                        if (list2 != null) list2.setRenderEffect(null);
                    }
                }
            }
        });

        ImageButton btnBackCustomerSelect = dialog.findViewById(R.id.btnBackCustomerSelect);
        if (btnBackCustomerSelect != null) {
            btnBackCustomerSelect.setOnClickListener(new View.OnClickListener() {
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

        final TextView txtSubtitle = dialog.findViewById(R.id.txtSubtitleCustomerSelect);
        final TextView lblCount = dialog.findViewById(R.id.lblCustomerCount);
        final View layoutNoCustomers = dialog.findViewById(R.id.layoutNoCustomers);
        final View layoutCustomerLoading = dialog.findViewById(R.id.layoutCustomerLoading);
        final RecyclerView lstCustomerSelect = dialog.findViewById(R.id.lstCustomerSelect);
        final EditText edtCustomerSearch = dialog.findViewById(R.id.edtCustomerSearch);
        final ImageButton btnClearCustomerSearch = dialog.findViewById(R.id.btnClearCustomerSearch);

        final List<CustomerModel> filteredList = new ArrayList<>();
        if (lblCount != null) {
            lblCount.setText("Loading...");
        }
        if (layoutCustomerLoading != null) {
            layoutCustomerLoading.setVisibility(View.VISIBLE);
        }
        if (layoutNoCustomers != null) {
            layoutNoCustomers.setVisibility(View.GONE);
        }

        lstCustomerSelect.setLayoutManager(new LinearLayoutManager(BillingActivity.this));
        lstCustomerSelect.setHasFixedSize(true);
        
        final RecyclerView.Adapter<CustomerSelectViewHolder> selectAdapter = new RecyclerView.Adapter<CustomerSelectViewHolder>() {
            @Override
            public CustomerSelectViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View itemView = LayoutInflater.from(BillingActivity.this).inflate(R.layout.item_customer_select, parent, false);
                return new CustomerSelectViewHolder(itemView);
            }

            @Override
            public void onBindViewHolder(CustomerSelectViewHolder holder, int position) {
                final CustomerModel c = filteredList.get(position);
                holder.lblCustSelectName.setText(c.name);
                
                String terr = (c.territory != null && !c.territory.isEmpty()) ? c.territory : "Standard Route";
                holder.lblCustSelectRoute.setText("Route: " + terr);
                
                if (c.phone != null && !c.phone.trim().isEmpty()) {
                    holder.lblCustSelectPhone.setText(c.phone);
                    holder.lblCustSelectPhone.setVisibility(View.VISIBLE);
                    holder.lblCustSelectDot.setVisibility(View.VISIBLE);
                } else {
                    holder.lblCustSelectPhone.setVisibility(View.GONE);
                    holder.lblCustSelectDot.setVisibility(View.GONE);
                }

                if (c.address != null && !c.address.trim().isEmpty()) {
                    holder.lblCustSelectAddress.setText(c.address);
                    holder.lblCustSelectAddress.setVisibility(View.VISIBLE);
                } else {
                    holder.lblCustSelectAddress.setVisibility(View.GONE);
                }

                double bal = c.outstanding != null ? c.outstanding.doubleValue() : 0.0;
                if (bal <= 0.001) {
                    holder.lblCustSelectBal.setText("Bal: LKR 0.00");
                    holder.lblCustSelectBal.setTextColor(android.graphics.Color.parseColor("#34C759"));
                } else {
                    holder.lblCustSelectBal.setText("Bal: LKR " + String.format(Locale.getDefault(), "%,.2f", bal));
                    holder.lblCustSelectBal.setTextColor(android.graphics.Color.parseColor("#FF3B30"));
                }

                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selectedCustomer = c;
                        validateSelectedCustomerAndPrompt(dialog);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return filteredList.size();
            }
        };
        lstCustomerSelect.setAdapter(selectAdapter);

        // Load customers in background thread so dialog displays instantly
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                loadCustomers();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (BillingActivity.this.isFinishing() || BillingActivity.this.isDestroyed() || !dialog.isShowing()) {
                            return;
                        }
                        if (layoutCustomerLoading != null) {
                            layoutCustomerLoading.setVisibility(View.GONE);
                        }
                        if (txtSubtitle != null && !customerList.isEmpty() && customerList.get(0).territory != null && !customerList.get(0).territory.isEmpty()) {
                            txtSubtitle.setText("Active Route: " + customerList.get(0).territory);
                        }
                        filteredList.clear();
                        filteredList.addAll(customerList);
                        selectAdapter.notifyDataSetChanged();
                        if (lblCount != null) {
                            lblCount.setText(filteredList.size() + " Shops");
                        }
                        if (layoutNoCustomers != null) {
                            layoutNoCustomers.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    }
                });
            }
        });

        // Search text watcher
        if (edtCustomerSearch != null) {
            edtCustomerSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String filter = s.toString().toLowerCase().trim();
                    if (btnClearCustomerSearch != null) {
                        btnClearCustomerSearch.setVisibility(filter.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    filteredList.clear();
                    for (CustomerModel c : customerList) {
                        boolean matchName = c.name != null && c.name.toLowerCase().contains(filter);
                        boolean matchPhone = c.phone != null && c.phone.toLowerCase().contains(filter);
                        boolean matchAddress = c.address != null && c.address.toLowerCase().contains(filter);
                        boolean matchTerritory = c.territory != null && c.territory.toLowerCase().contains(filter);
                        if (matchName || matchPhone || matchAddress || matchTerritory) {
                            filteredList.add(c);
                        }
                    }
                    selectAdapter.notifyDataSetChanged();
                    if (lblCount != null) {
                        lblCount.setText(filteredList.size() + " Shops");
                    }
                    if (layoutNoCustomers != null) {
                        layoutNoCustomers.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (btnClearCustomerSearch != null) {
            btnClearCustomerSearch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (edtCustomerSearch != null) {
                        edtCustomerSearch.setText("");
                    }
                }
            });
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
        if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.GONE);
        if (layoutHeader != null) layoutHeader.setVisibility(View.GONE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            RecyclerView list1 = findViewById(R.id.gridProducts);
            RecyclerView list2 = findViewById(R.id.lstProducts);
            android.graphics.RenderEffect blur = android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP);
            if (list1 != null && list1.getVisibility() == View.VISIBLE) list1.setRenderEffect(blur);
            if (list2 != null && list2.getVisibility() == View.VISIBLE) list2.setRenderEffect(blur);
        }
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                if (layoutCartOverlay == null || layoutCartOverlay.getVisibility() != View.VISIBLE) {
                    if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.VISIBLE);
                    if (layoutHeader != null) layoutHeader.setVisibility(View.VISIBLE);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        RecyclerView list1 = findViewById(R.id.gridProducts);
                        RecyclerView list2 = findViewById(R.id.lstProducts);
                        if (list1 != null) list1.setRenderEffect(null);
                        if (list2 != null) list2.setRenderEffect(null);
                    }
                }
            }
        });

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
                cv.put("is_profile_synced", 0);

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
