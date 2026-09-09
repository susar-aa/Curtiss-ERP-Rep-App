package com.example.curtiss;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.LruCache;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CatalogActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private EditText edtProductSearch;
    private Spinner spinnerCategory;
    private RecyclerView gridProducts;
    private RecyclerView lstProducts;
    private ImageButton btnBack;
    private MaterialButton btnModeStandard, btnModeVisual;
    private MaterialCardView cardFloatingSearch;
    private LinearLayout layoutHeader;
    private ImageButton btnClearSearch;
    private TextView txtHeaderTitle;
    private View layoutCatalogLoading;
    private String selectedCategoryName = "All Categories";
    private boolean isVisualMode = false;

    private List<ProductModel> productList = new ArrayList<>();
    private List<DisplayItem> displayItems = new ArrayList<>();
    private List<String> categoryList = new ArrayList<>();
    private ProductGridAdapter productGridAdapter;
    private ProductAdapter productAdapter;

    private static final LruCache<String, Bitmap> THUMBNAIL_CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };

    public static Bitmap getCachedThumbnail(String path, int sampleSize) {
        if (path == null || path.isEmpty()) return null;
        String key = path + "_" + sampleSize;
        Bitmap cached = THUMBNAIL_CACHE.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        try {
            File file = new File(path);
            if (!file.exists()) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap != null) {
                THUMBNAIL_CACHE.put(key, bitmap);
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    public static class ProductModel {
        int id, qtyOnHand, qtyReserved;
        String name, category, localImagePath;
        Double price, wholesalePrice;
        String sku, sampleCode, variationsJson;
        String brand, description;
    }

    private static class DisplayItem {
        static final int TYPE_NORMAL = 0;
        static final int TYPE_PARENT = 1;
        static final int TYPE_VARIATION = 2;

        int type;
        ProductModel parentProduct;
        JSONObject variationJsonObj; // null for normal and parent
        String variationName;
        double variationPrice;
        int variationQtyOnHand;
        int variationQtyReserved;
    }

    // Zoomable Image View implementation
    private static class ZoomImageView extends androidx.appcompat.widget.AppCompatImageView {
        private float mScaleFactor = 1.0f;
        private android.view.ScaleGestureDetector mScaleDetector;

        public ZoomImageView(Context context) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(android.graphics.Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setContentView(R.layout.activity_catalog);

        dbHelper = DatabaseHelper.getInstance(this);

        layoutCatalogLoading = findViewById(R.id.layoutCatalogLoading);
        layoutHeader = findViewById(R.id.layoutHeader);
        txtHeaderTitle = findViewById(R.id.txtHeaderTitle);
        btnBack = findViewById(R.id.btnBack);
        edtProductSearch = findViewById(R.id.edtProductSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        cardFloatingSearch = findViewById(R.id.cardFloatingSearch);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        gridProducts = findViewById(R.id.gridProducts);
        lstProducts = findViewById(R.id.lstProducts);
        btnModeStandard = findViewById(R.id.btnModeStandard);
        btnModeVisual = findViewById(R.id.btnModeVisual);

        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        if (gridProducts != null) {
            GridLayoutManager glm = new GridLayoutManager(this, 2);
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (productGridAdapter != null) {
                        int type = productGridAdapter.getItemViewType(position);
                        if (type == DisplayItem.TYPE_PARENT) {
                            return 2; // Full width span for parent card
                        }
                    }
                    return 1;
                }
            });
            gridProducts.setLayoutManager(glm);
        }

        if (lstProducts != null) {
            lstProducts.setLayoutManager(new LinearLayoutManager(this));
        }

        setupModeToggle();
        setupCategorySpinner();

        if (edtProductSearch != null) {
            edtProductSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String query = s.toString();
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(query.trim().isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    View catScroll = findViewById(R.id.categoryHorizontalScroll);
                    if (query.trim().isEmpty()) {
                        if (catScroll != null) catScroll.setVisibility(View.VISIBLE);
                    } else {
                        if (catScroll != null) catScroll.setVisibility(View.GONE);
                    }
                    loadCatalogItems(query);
                }

                @Override
                public void afterTextChanged(Editable s) {}
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

        loadCatalogItems("");
    }

    private void setupModeToggle() {
        if (btnModeStandard != null && btnModeVisual != null) {
            btnModeStandard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isVisualMode = false;
                    if (lstProducts != null) lstProducts.setVisibility(View.VISIBLE);
                    if (gridProducts != null) gridProducts.setVisibility(View.GONE);
                    btnModeStandard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#000000")));
                    btnModeStandard.setTextColor(Color.WHITE);
                    btnModeVisual.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
                    btnModeVisual.setTextColor(Color.parseColor("#8E8E93"));
                }
            });

            btnModeVisual.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isVisualMode = true;
                    if (lstProducts != null) lstProducts.setVisibility(View.GONE);
                    if (gridProducts != null) gridProducts.setVisibility(View.VISIBLE);
                    btnModeVisual.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#000000")));
                    btnModeVisual.setTextColor(Color.WHITE);
                    btnModeStandard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
                    btnModeStandard.setTextColor(Color.parseColor("#8E8E93"));
                }
            });
        }
    }

    private void setupCategorySpinner() {
        categoryList.clear();
        categoryList.add("All Categories");

        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT name FROM categories WHERE status = 'active' ORDER BY name ASC", null);
            while (cursor.moveToNext()) {
                categoryList.add(cursor.getString(0));
            }
            cursor.close();

            if (categoryList.size() <= 1) {
                cursor = db.rawQuery("SELECT DISTINCT category_name FROM products WHERE category_name IS NOT NULL AND category_name != '' AND category_name != 'null' AND status = 'active' ORDER BY category_name ASC", null);
                while (cursor.moveToNext()) {
                    categoryList.add(cursor.getString(0));
                }
                cursor.close();
            }
        } catch (Exception e) {
            android.util.Log.e("CatalogActivity", "Error loading categories: " + e.getMessage());
        }

        if (spinnerCategory != null) {
            ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryList);
            catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCategory.setAdapter(catAdapter);

            spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (edtProductSearch != null) {
                        loadCatalogItems(edtProductSearch.getText().toString());
                    }
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
        float dp = getResources().getDisplayMetrics().density;

        for (final String catName : categoryList) {
            final MaterialButton btn = new MaterialButton(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (int) (38 * dp)
            );
            params.setMargins(0, 0, (int) (8 * dp), 0);
            btn.setLayoutParams(params);
            btn.setPadding((int) (16 * dp), 0, (int) (16 * dp), 0);
            btn.setText(catName);
            btn.setTextSize(13);
            btn.setAllCaps(false);
            btn.setCornerRadius((int) (50 * dp));
            btn.setInsetTop(0);
            btn.setInsetBottom(0);

            if (catName.equalsIgnoreCase(selectedCategoryName)) {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#000000")));
                btn.setTextColor(Color.WHITE);
                btn.setTypeface(null, Typeface.BOLD);
            } else {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F2F2F7")));
                btn.setTextColor(Color.parseColor("#1C1C1E"));
                btn.setTypeface(null, Typeface.NORMAL);
            }

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedCategoryName = catName;
                    setupHorizontalCategories();
                    if (edtProductSearch != null) {
                        loadCatalogItems(edtProductSearch.getText().toString());
                    }
                }
            });

            layoutHorizontalCategories.addView(btn);
        }
    }

    private void loadCatalogItems(final String filter) {
        if (layoutCatalogLoading != null && (displayItems == null || displayItems.isEmpty())) {
            layoutCatalogLoading.setVisibility(View.VISIBLE);
        }

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                final List<ProductModel> loaded = new ArrayList<>();
                final List<DisplayItem> loadedDisplay = new ArrayList<>();
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
                        p.price = cursor.getDouble(cursor.getColumnIndexOrThrow("wholesale_price"));
                        p.wholesalePrice = cursor.getDouble(cursor.getColumnIndexOrThrow("wholesale_price"));
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
                    android.util.Log.e("CatalogActivity", "Error loading products: " + e.getMessage());
                }

                if (filter != null && !filter.trim().isEmpty()) {
                    final String searchLower = filter.toLowerCase().trim();
                    Collections.sort(loaded, new Comparator<ProductModel>() {
                        @Override
                        public int compare(ProductModel o1, ProductModel o2) {
                            boolean exact1 = o1.name.toLowerCase().trim().equals(searchLower) ||
                                    (o1.sku != null && o1.sku.toLowerCase().trim().equals(searchLower)) ||
                                    (o1.sampleCode != null && o1.sampleCode.toLowerCase().trim().equals(searchLower));
                            boolean exact2 = o2.name.toLowerCase().trim().equals(searchLower) ||
                                    (o2.sku != null && o2.sku.toLowerCase().trim().equals(searchLower)) ||
                                    (o2.sampleCode != null && o2.sampleCode.toLowerCase().trim().equals(searchLower));
                            if (exact1 && !exact2) return -1;
                            if (!exact1 && exact2) return 1;
                            return 0;
                        }
                    });
                }

                for (ProductModel p : loaded) {
                    boolean hasVariations = false;
                    JSONArray vars = null;
                    if (p.variationsJson != null && !p.variationsJson.trim().isEmpty() && !p.variationsJson.equals("null")) {
                        try {
                            vars = new JSONArray(p.variationsJson);
                            if (vars.length() > 0) {
                                hasVariations = true;
                            }
                        } catch (Exception e) {}
                    }

                    if (!hasVariations) {
                        DisplayItem item = new DisplayItem();
                        item.type = DisplayItem.TYPE_NORMAL;
                        item.parentProduct = p;
                        loadedDisplay.add(item);
                    } else {
                        DisplayItem parentItem = new DisplayItem();
                        parentItem.type = DisplayItem.TYPE_PARENT;
                        parentItem.parentProduct = p;
                        loadedDisplay.add(parentItem);

                        for (int i = 0; i < vars.length(); i++) {
                            try {
                                JSONObject vObj = vars.getJSONObject(i);
                                DisplayItem varItem = new DisplayItem();
                                varItem.type = DisplayItem.TYPE_VARIATION;
                                varItem.parentProduct = p;
                                varItem.variationJsonObj = vObj;
                                String varName = vObj.optString("attribute", vObj.optString("option_name", vObj.optString("name", vObj.optString("option", ""))));
                                if (varName.startsWith(p.name + " - ")) {
                                    varName = varName.substring(p.name.length() + 3);
                                } else if (varName.contains(" - ")) {
                                    varName = varName.substring(varName.lastIndexOf(" - ") + 3);
                                }
                                varItem.variationName = varName;
                                double varPrice = vObj.optDouble("wholesale_price", 0.0);
                                if (varPrice <= 0) {
                                    varPrice = p.price != null ? p.price : 0.0;
                                }
                                if (varPrice <= 0) {
                                    varPrice = vObj.optDouble("price", 0.0);
                                }
                                varItem.variationPrice = varPrice;

                                if (vObj.has("quantity_on_hand")) {
                                    varItem.variationQtyOnHand = vObj.optInt("quantity_on_hand", 0);
                                } else {
                                    varItem.variationQtyOnHand = vObj.optInt("quantity", vObj.optInt("qty", 0));
                                }
                                if (vObj.has("quantity_reserved")) {
                                    varItem.variationQtyReserved = vObj.optInt("quantity_reserved", 0);
                                } else {
                                    varItem.variationQtyReserved = 0;
                                }
                                loadedDisplay.add(varItem);
                            } catch (Exception e) {}
                        }
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (CatalogActivity.this.isFinishing() || CatalogActivity.this.isDestroyed()) {
                            return;
                        }
                        if (layoutCatalogLoading != null) {
                            layoutCatalogLoading.setVisibility(View.GONE);
                        }
                        productList.clear();
                        productList.addAll(loaded);
                        displayItems.clear();
                        displayItems.addAll(loadedDisplay);

                        if (productAdapter == null) {
                            productAdapter = new ProductAdapter();
                            if (lstProducts != null) lstProducts.setAdapter(productAdapter);
                        } else {
                            productAdapter.notifyDataSetChanged();
                        }

                        if (productGridAdapter == null) {
                            productGridAdapter = new ProductGridAdapter();
                            if (gridProducts != null) gridProducts.setAdapter(productGridAdapter);
                        } else {
                            productGridAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        });
    }

    private boolean hasProductVariations(ProductModel p) {
        if (p == null || p.variationsJson == null || p.variationsJson.isEmpty() || p.variationsJson.equals("null")) {
            return false;
        }
        try {
            JSONArray parsedVars = new JSONArray(p.variationsJson);
            if (parsedVars.length() == 0) return false;
            for (int i = 0; i < parsedVars.length(); i++) {
                JSONObject vObj = parsedVars.getJSONObject(i);
                String name = vObj.optString("attribute", vObj.optString("option_name", ""));
                if (name != null && !name.trim().isEmpty()) {
                    return true;
                }
            }
        } catch (Exception e) {}
        return false;
    }

    private void showProductQuickViewDialog(final ProductModel p) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        float dp = getResources().getDisplayMetrics().density;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int dialogWidth = Math.min((int)(dm.widthPixels * 0.92f), (int)(500 * dp));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // 1. HERO IMAGE SECTION WITH CLOSE BUTTON
        FrameLayout heroFrame = new FrameLayout(this);
        FrameLayout.LayoutParams heroLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(280 * dp));
        heroFrame.setLayoutParams(heroLp);
        GradientDrawable heroClip = new GradientDrawable();
        heroClip.setColor(Color.parseColor("#F2F2F7"));
        heroClip.setCornerRadii(new float[]{28*dp, 28*dp, 28*dp, 28*dp, 0, 0, 0, 0});
        heroFrame.setBackground(heroClip);
        heroFrame.setClipToOutline(true);

        final ZoomImageView zoomImage = new ZoomImageView(this);
        FrameLayout.LayoutParams zoomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        zoomImage.setLayoutParams(zoomLp);
        zoomImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
            Bitmap bm = getCachedThumbnail(p.localImagePath, 1);
            if (bm != null) zoomImage.setImageBitmap(bm);
            else zoomImage.setImageResource(android.R.drawable.ic_menu_gallery);
        } else {
            zoomImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        heroFrame.addView(zoomImage);

        // Floating close button top-right
        TextView btnClose = new TextView(this);
        btnClose.setText("✕");
        btnClose.setTextColor(Color.parseColor("#1C1C1E"));
        btnClose.setTextSize(16);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setGravity(android.view.Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.parseColor("#F0F0F0"));
        closeBg.setAlpha(220);
        closeBg.setCornerRadius(99 * dp);
        btnClose.setBackground(closeBg);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                (int)(36 * dp), (int)(36 * dp));
        closeLp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        closeLp.setMargins(0, (int)(12 * dp), (int)(12 * dp), 0);
        btnClose.setLayoutParams(closeLp);
        heroFrame.addView(btnClose);

        root.addView(heroFrame);

        // 2. PADDED CONTENT SECTION
        LinearLayout contentPad = new LinearLayout(this);
        contentPad.setOrientation(LinearLayout.VERTICAL);
        contentPad.setPadding((int)(20 * dp), (int)(16 * dp), (int)(20 * dp), (int)(16 * dp));
        contentPad.setBackgroundColor(Color.WHITE);

        // 2a. Category pill + Brand (left) | SKU + Sample Code (right)
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.setMargins(0, 0, 0, (int)(8 * dp));
        metaRow.setLayoutParams(metaLp);

        TextView catPill = new TextView(this);
        catPill.setText(p.category != null ? p.category.toUpperCase(Locale.getDefault()) : "PRODUCT");
        catPill.setTextColor(Color.parseColor("#636366"));
        catPill.setTextSize(10);
        catPill.setTypeface(null, Typeface.BOLD);
        GradientDrawable catBg = new GradientDrawable();
        catBg.setColor(Color.parseColor("#F2F2F7"));
        catBg.setCornerRadius(8 * dp);
        catPill.setBackground(catBg);
        catPill.setPadding((int)(8 * dp), (int)(3 * dp), (int)(8 * dp), (int)(3 * dp));
        metaRow.addView(catPill);

        if (p.brand != null && !p.brand.isEmpty()) {
            TextView brandTxt = new TextView(this);
            brandTxt.setText("   •   " + p.brand);
            brandTxt.setTextColor(Color.parseColor("#8E8E93"));
            brandTxt.setTextSize(11);
            metaRow.addView(brandTxt);
        }

        Space spacer1 = new Space(this);
        spacer1.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        metaRow.addView(spacer1);

        String skuText = (p.sku != null && !p.sku.isEmpty()) ? ("SKU: " + p.sku) : "";
        String sampleText = (p.sampleCode != null && !p.sampleCode.isEmpty()) ? ("SAMPLE: " + p.sampleCode) : "";
        String rightMeta = "";
        if (!skuText.isEmpty() && !sampleText.isEmpty()) rightMeta = skuText + "   •   " + sampleText;
        else if (!skuText.isEmpty()) rightMeta = skuText;
        else if (!sampleText.isEmpty()) rightMeta = sampleText;

        if (!rightMeta.isEmpty()) {
            TextView txtRightMeta = new TextView(this);
            txtRightMeta.setText(rightMeta);
            txtRightMeta.setTextColor(Color.parseColor("#8E8E93"));
            txtRightMeta.setTextSize(11);
            txtRightMeta.setTypeface(null, Typeface.BOLD);
            txtRightMeta.setGravity(android.view.Gravity.END);
            metaRow.addView(txtRightMeta);
        }
        contentPad.addView(metaRow);

        // 2b. Product Name
        final TextView txtTitle = new TextView(this);
        txtTitle.setText(p.name);
        txtTitle.setTextColor(Color.parseColor("#1C1C1E"));
        txtTitle.setTextSize(22);
        txtTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, 0, 0, (int)(16 * dp));
        txtTitle.setLayoutParams(titleLp);
        contentPad.addView(txtTitle);

        // 2c. Price & Stock Card (Spacious 2-column card with vertical divider)
        LinearLayout infoStrip = new LinearLayout(this);
        infoStrip.setOrientation(LinearLayout.HORIZONTAL);
        infoStrip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        GradientDrawable stripBg = new GradientDrawable();
        stripBg.setColor(Color.parseColor("#F8F8FB"));
        stripBg.setCornerRadius(16 * dp);
        stripBg.setStroke((int)(1 * dp), Color.parseColor("#EBEBF0"));
        infoStrip.setBackground(stripBg);
        infoStrip.setPadding((int)(20 * dp), (int)(16 * dp), (int)(20 * dp), (int)(16 * dp));
        LinearLayout.LayoutParams stripLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stripLp.setMargins(0, 0, 0, (int)(12 * dp));
        infoStrip.setLayoutParams(stripLp);

        // Price Column
        LinearLayout priceBlock = new LinearLayout(this);
        priceBlock.setOrientation(LinearLayout.VERTICAL);
        priceBlock.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        TextView lblPrice = new TextView(this);
        lblPrice.setText("WHOLESALE PRICE");
        lblPrice.setTextColor(Color.parseColor("#8E8E93"));
        lblPrice.setTextSize(10);
        lblPrice.setTypeface(null, Typeface.BOLD);
        priceBlock.addView(lblPrice);
        final TextView txtPrice = new TextView(this);
        double displayPrice = (p.wholesalePrice != null && p.wholesalePrice > 0) ? p.wholesalePrice : (p.price != null ? p.price : 0.0);
        txtPrice.setText("LKR " + String.format(Locale.getDefault(), "%,.2f", displayPrice));
        txtPrice.setTextColor(Color.parseColor("#1C1C1E"));
        txtPrice.setTextSize(20);
        txtPrice.setTypeface(null, Typeface.BOLD);
        txtPrice.setPadding(0, (int)(4 * dp), 0, 0);
        priceBlock.addView(txtPrice);
        infoStrip.addView(priceBlock);

        // Vertical divider
        View stripDiv1 = new View(this);
        LinearLayout.LayoutParams div1Lp = new LinearLayout.LayoutParams((int)(1 * dp), (int)(40 * dp));
        div1Lp.setMargins((int)(16 * dp), 0, (int)(16 * dp), 0);
        stripDiv1.setLayoutParams(div1Lp);
        stripDiv1.setBackgroundColor(Color.parseColor("#E0E0E5"));
        infoStrip.addView(stripDiv1);

        // Stock Column
        int available = p.qtyOnHand - p.qtyReserved;
        LinearLayout stockBlock = new LinearLayout(this);
        stockBlock.setOrientation(LinearLayout.VERTICAL);
        stockBlock.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        TextView lblStock = new TextView(this);
        lblStock.setText("AVAILABLE STOCK");
        lblStock.setTextColor(Color.parseColor("#8E8E93"));
        lblStock.setTextSize(10);
        lblStock.setTypeface(null, Typeface.BOLD);
        stockBlock.addView(lblStock);
        final TextView txtStock = new TextView(this);
        if (available > 0) {
            txtStock.setText(available + " Units");
            txtStock.setTextColor(Color.parseColor("#30D158"));
        } else {
            txtStock.setText("Out of Stock");
            txtStock.setTextColor(Color.parseColor("#FF453A"));
        }
        txtStock.setTextSize(20);
        txtStock.setTypeface(null, Typeface.BOLD);
        txtStock.setPadding(0, (int)(4 * dp), 0, 0);
        stockBlock.addView(txtStock);
        infoStrip.addView(stockBlock);

        contentPad.addView(infoStrip);

        // 2d. Description row (if available)
        if (p.description != null && !p.description.isEmpty()) {
            TextView txtDesc = new TextView(this);
            txtDesc.setText(p.description);
            txtDesc.setTextColor(Color.parseColor("#636366"));
            txtDesc.setTextSize(13);
            txtDesc.setLineSpacing(2 * dp, 1.0f);
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descLp.setMargins(0, (int)(4 * dp), 0, 0);
            txtDesc.setLayoutParams(descLp);
            contentPad.addView(txtDesc);
        }
        root.addView(contentPad);

        // 3. VARIATIONS CAROUSEL
        if (hasProductVariations(p) && p.variationsJson != null && !p.variationsJson.isEmpty() && !p.variationsJson.equals("null")) {
            try {
                JSONArray vars = new JSONArray(p.variationsJson);
                if (vars.length() > 0) {
                    View sectionDivider = new View(this);
                    LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, (int)(1 * dp));
                    divLp.setMargins((int)(20 * dp), 0, (int)(20 * dp), 0);
                    sectionDivider.setLayoutParams(divLp);
                    sectionDivider.setBackgroundColor(Color.parseColor("#F2F2F7"));
                    root.addView(sectionDivider);

                    LinearLayout varHeader = new LinearLayout(this);
                    varHeader.setOrientation(LinearLayout.HORIZONTAL);
                    varHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    varHeader.setPadding((int)(20 * dp), (int)(14 * dp), (int)(20 * dp), (int)(8 * dp));
                    TextView varHeaderTxt = new TextView(this);
                    varHeaderTxt.setText("VARIATIONS");
                    varHeaderTxt.setTextColor(Color.parseColor("#3C3C43"));
                    varHeaderTxt.setTextSize(13);
                    varHeaderTxt.setTypeface(null, Typeface.BOLD);
                    varHeaderTxt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    varHeader.addView(varHeaderTxt);
                    TextView varCount = new TextView(this);
                    varCount.setText(vars.length() + " options  •  tap to preview");
                    varCount.setTextColor(Color.parseColor("#8E8E93"));
                    varCount.setTextSize(11);
                    varHeader.addView(varCount);
                    root.addView(varHeader);

                    HorizontalScrollView varScroll = new HorizontalScrollView(this);
                    varScroll.setHorizontalScrollBarEnabled(false);
                    LinearLayout.LayoutParams varScrollLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    varScrollLp.setMargins(0, 0, 0, (int)(12 * dp));
                    varScroll.setLayoutParams(varScrollLp);

                    LinearLayout varContainer = new LinearLayout(this);
                    varContainer.setOrientation(LinearLayout.HORIZONTAL);
                    varContainer.setPadding((int)(20 * dp), 0, (int)(20 * dp), (int)(20 * dp));
                    varScroll.addView(varContainer);

                    final List<FrameLayout> tileList = new ArrayList<>();

                    for (int i = 0; i < vars.length(); i++) {
                        final JSONObject vObj = vars.getJSONObject(i);
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
                        if (calcPrice <= 0 && p.wholesalePrice != null) calcPrice = p.wholesalePrice;
                        final double varPrice = calcPrice;

                        final FrameLayout tile = new FrameLayout(this);
                        GradientDrawable tileBg = new GradientDrawable();
                        tileBg.setColor(Color.parseColor("#F2F2F7"));
                        tileBg.setCornerRadius(16 * dp);
                        tile.setBackground(tileBg);
                        tile.setClipToOutline(true);
                        int tileW = (int)(110 * dp);
                        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(tileW, tileW);
                        tileLp.setMargins(0, 0, (int)(10 * dp), 0);
                        tile.setLayoutParams(tileLp);

                        ImageView tileImg = new ImageView(this);
                        tileImg.setLayoutParams(new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        tileImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        boolean imgLoaded = false;
                        if (!varImgPath.isEmpty()) {
                            Bitmap bm = getCachedThumbnail(varImgPath, 2);
                            if (bm != null) { tileImg.setImageBitmap(bm); imgLoaded = true; }
                        }
                        if (!imgLoaded && p.localImagePath != null && !p.localImagePath.isEmpty()) {
                            Bitmap bm = getCachedThumbnail(p.localImagePath, 2);
                            if (bm != null) { tileImg.setImageBitmap(bm); imgLoaded = true; }
                        }
                        if (!imgLoaded) tileImg.setImageResource(android.R.drawable.ic_menu_gallery);
                        tile.addView(tileImg);

                        LinearLayout scrim = new LinearLayout(this);
                        scrim.setOrientation(LinearLayout.VERTICAL);
                        scrim.setGravity(android.view.Gravity.BOTTOM);
                        scrim.setLayoutParams(new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        GradientDrawable scrimBg = new GradientDrawable(
                                GradientDrawable.Orientation.BOTTOM_TOP,
                                new int[]{Color.parseColor("#DD000000"), Color.TRANSPARENT});
                        scrim.setBackground(scrimBg);
                        scrim.setPadding((int)(7 * dp), (int)(6 * dp), (int)(6 * dp), (int)(6 * dp));
                        TextView tileNameTv = new TextView(this);
                        tileNameTv.setText(varName);
                        tileNameTv.setTextColor(Color.WHITE);
                        tileNameTv.setTextSize(11);
                        tileNameTv.setTypeface(null, Typeface.BOLD);
                        tileNameTv.setSingleLine(true);
                        tileNameTv.setEllipsize(TextUtils.TruncateAt.END);
                        scrim.addView(tileNameTv);
                        TextView tilePriceTv = new TextView(this);
                        tilePriceTv.setText("LKR " + String.format(Locale.getDefault(), "%,.0f", varPrice));
                        tilePriceTv.setTextColor(Color.parseColor("#CCFFFFFF"));
                        tilePriceTv.setTextSize(10);
                        scrim.addView(tilePriceTv);
                        tile.addView(scrim);

                        TextView stockBadge = new TextView(this);
                        stockBadge.setText(vQty > 0 ? " " + vQty + " " : " 0 ");
                        stockBadge.setTextColor(Color.WHITE);
                        stockBadge.setTextSize(9);
                        stockBadge.setTypeface(null, Typeface.BOLD);
                        GradientDrawable sbBg = new GradientDrawable();
                        sbBg.setColor(Color.parseColor(vQty > 0 ? "#34C759" : "#FF3B30"));
                        sbBg.setCornerRadius(20 * dp);
                        stockBadge.setBackground(sbBg);
                        FrameLayout.LayoutParams sbLp = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        sbLp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
                        sbLp.setMargins(0, (int)(6 * dp), (int)(6 * dp), 0);
                        stockBadge.setLayoutParams(sbLp);
                        tile.addView(stockBadge);

                        tileList.add(tile);
                        tile.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                float dpVal = getResources().getDisplayMetrics().density;
                                for (FrameLayout t : tileList) {
                                    GradientDrawable bg = new GradientDrawable();
                                    bg.setColor(Color.parseColor("#F2F2F7"));
                                    bg.setCornerRadius(16 * dpVal);
                                    t.setBackground(bg);
                                    t.setClipToOutline(true);
                                }
                                GradientDrawable selBg = new GradientDrawable();
                                selBg.setColor(Color.parseColor("#F2F2F7"));
                                selBg.setCornerRadius(16 * dpVal);
                                selBg.setStroke((int)(3 * dpVal), Color.parseColor("#1C1C1E"));
                                tile.setBackground(selBg);
                                tile.setClipToOutline(true);

                                boolean switched = false;
                                if (!varImgPath.isEmpty()) {
                                    Bitmap bm = getCachedThumbnail(varImgPath, 1);
                                    if (bm != null) { zoomImage.setImageBitmap(bm); switched = true; }
                                }
                                if (!switched && p.localImagePath != null && !p.localImagePath.isEmpty()) {
                                    Bitmap bm = getCachedThumbnail(p.localImagePath, 1);
                                    if (bm != null) zoomImage.setImageBitmap(bm);
                                }
                                txtTitle.setText(p.name + "  ›  " + varName);
                                txtPrice.setText("LKR " + String.format(Locale.getDefault(), "%,.2f", varPrice));
                                if (vQty > 0) {
                                    txtStock.setText(vQty + " Units");
                                    txtStock.setTextColor(Color.parseColor("#30D158"));
                                } else {
                                    txtStock.setText("Out of Stock");
                                    txtStock.setTextColor(Color.parseColor("#FF453A"));
                                }
                            }
                        });
                        varContainer.addView(tile);
                    }
                    root.addView(varScroll);
                }
            } catch (Exception e) {
                android.util.Log.e("CatalogActivity", "QuickView variation error: " + e.getMessage());
            }
        }

        scrollView.addView(root);
        builder.setView(scrollView);

        final AlertDialog dialog = builder.create();
        dialog.show();

        if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.GONE);
        if (layoutHeader != null) layoutHeader.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect blur = RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP);
            if (gridProducts != null && gridProducts.getVisibility() == View.VISIBLE) gridProducts.setRenderEffect(blur);
            if (lstProducts != null && lstProducts.getVisibility() == View.VISIBLE) lstProducts.setRenderEffect(blur);
        }

        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                if (cardFloatingSearch != null) cardFloatingSearch.setVisibility(View.VISIBLE);
                if (layoutHeader != null) layoutHeader.setVisibility(View.VISIBLE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (gridProducts != null) gridProducts.setRenderEffect(null);
                    if (lstProducts != null) lstProducts.setRenderEffect(null);
                }
            }
        });

        if (dialog.getWindow() != null) {
            GradientDrawable winBg = new GradientDrawable();
            winBg.setColor(Color.WHITE);
            winBg.setCornerRadius(28 * dp);
            dialog.getWindow().setBackgroundDrawable(winBg);
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    private class ProductGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return displayItems.get(position).type;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == DisplayItem.TYPE_PARENT) {
                View view = LayoutInflater.from(CatalogActivity.this).inflate(R.layout.item_product_parent, parent, false);
                return new ParentViewHolder(view);
            } else {
                View view = LayoutInflater.from(CatalogActivity.this).inflate(R.layout.item_product_grid, parent, false);
                return new GridViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
            final DisplayItem item = displayItems.get(position);

            if (holder instanceof ParentViewHolder) {
                ParentViewHolder h = (ParentViewHolder) holder;
                final ProductModel p = item.parentProduct;

                h.lblParentProductName.setText(p.name);
                h.lblParentCategory.setText(p.category);

                String skuText = (p.sku != null && !p.sku.isEmpty()) ? p.sku : "";
                String sampleText = (p.sampleCode != null && !p.sampleCode.isEmpty()) ? p.sampleCode : "";
                if (!skuText.isEmpty() && !sampleText.isEmpty()) {
                    h.lblParentSku.setText("SKU: " + skuText + " | Sample: " + sampleText);
                    h.lblParentSku.setVisibility(View.VISIBLE);
                } else if (!skuText.isEmpty()) {
                    h.lblParentSku.setText("SKU: " + skuText);
                    h.lblParentSku.setVisibility(View.VISIBLE);
                } else if (!sampleText.isEmpty()) {
                    h.lblParentSku.setText("Sample: " + sampleText);
                    h.lblParentSku.setVisibility(View.VISIBLE);
                } else {
                    h.lblParentSku.setVisibility(View.GONE);
                }

                if (h.imgParentProduct != null) {
                    if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
                        Bitmap bm = getCachedThumbnail(p.localImagePath, 2);
                        if (bm != null) {
                            h.imgParentProduct.setImageBitmap(bm);
                        } else {
                            h.imgParentProduct.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    } else {
                        h.imgParentProduct.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                }

                h.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showProductQuickViewDialog(p);
                    }
                });

            } else if (holder instanceof GridViewHolder) {
                GridViewHolder h = (GridViewHolder) holder;
                final ProductModel p = item.parentProduct;

                if (item.type == DisplayItem.TYPE_VARIATION) {
                    h.lblProductNameGrid.setText(item.variationName);
                    h.lblPriceGrid.setText(String.format(Locale.getDefault(), "LKR %.2f", item.variationPrice));
                    h.lblCategoryGrid.setText(p.category);

                    int available = item.variationQtyOnHand - item.variationQtyReserved;
                    if (available <= 0) {
                        h.lblStockGrid.setTextColor(Color.parseColor("#FF3B30"));
                        h.lblStockGrid.setText("Out of Stock");
                    } else {
                        h.lblStockGrid.setTextColor(Color.parseColor("#34C759"));
                        h.lblStockGrid.setText("Qty: " + available);
                    }

                    if (h.lblVariationsGrid != null) {
                        h.lblVariationsGrid.setVisibility(View.GONE);
                    }

                    if (h.imgProductGrid != null) {
                        String varImgPath = "";
                        if (item.variationJsonObj != null) {
                            varImgPath = item.variationJsonObj.optString("image_path", item.variationJsonObj.optString("local_image_path", ""));
                        }
                        if (varImgPath.isEmpty()) {
                            varImgPath = p.localImagePath;
                        }

                        if (varImgPath != null && !varImgPath.isEmpty()) {
                            Bitmap bm = getCachedThumbnail(varImgPath, 2);
                            if (bm != null) {
                                h.imgProductGrid.setImageBitmap(bm);
                            } else {
                                h.imgProductGrid.setImageResource(android.R.drawable.ic_menu_gallery);
                            }
                        } else {
                            h.imgProductGrid.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    }

                    final ProductModel variationModel = new ProductModel();
                    variationModel.id = p.id;
                    variationModel.name = p.name + " - " + item.variationName;
                    variationModel.category = p.category;
                    variationModel.price = item.variationPrice;
                    variationModel.wholesalePrice = item.variationPrice;
                    variationModel.qtyOnHand = item.variationQtyOnHand;
                    variationModel.qtyReserved = item.variationQtyReserved;
                    variationModel.localImagePath = p.localImagePath;
                    variationModel.sku = p.sku;
                    variationModel.sampleCode = p.sampleCode;
                    variationModel.variationsJson = p.variationsJson;
                    variationModel.brand = p.brand;
                    variationModel.description = p.description;

                    h.itemView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showProductQuickViewDialog(variationModel);
                        }
                    });

                } else {
                    h.lblProductNameGrid.setText(p.name);
                    h.lblCategoryGrid.setText(p.category);
                    double displayPrice = (p.wholesalePrice != null && p.wholesalePrice > 0) ? p.wholesalePrice : (p.price != null ? p.price : 0.0);
                    h.lblPriceGrid.setText(String.format(Locale.getDefault(), "LKR %.2f", displayPrice));

                    int available = p.qtyOnHand - p.qtyReserved;
                    if (available <= 0) {
                        h.lblStockGrid.setTextColor(Color.parseColor("#FF3B30"));
                        h.lblStockGrid.setText("Out of Stock");
                    } else {
                        h.lblStockGrid.setTextColor(Color.parseColor("#34C759"));
                        h.lblStockGrid.setText("Qty: " + available);
                    }

                    if (h.lblVariationsGrid != null) {
                        h.lblVariationsGrid.setVisibility(View.GONE);
                    }

                    if (h.imgProductGrid != null) {
                        if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
                            Bitmap bm = getCachedThumbnail(p.localImagePath, 2);
                            if (bm != null) {
                                h.imgProductGrid.setImageBitmap(bm);
                            } else {
                                h.imgProductGrid.setImageResource(android.R.drawable.ic_menu_gallery);
                            }
                        } else {
                            h.imgProductGrid.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    }

                    h.itemView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showProductQuickViewDialog(p);
                        }
                    });
                }
            }
        }

        class ParentViewHolder extends RecyclerView.ViewHolder {
            ImageView imgParentProduct;
            TextView lblParentProductName;
            TextView lblParentCategory;
            TextView lblParentSku;

            ParentViewHolder(View itemView) {
                super(itemView);
                imgParentProduct = itemView.findViewById(R.id.imgParentProduct);
                lblParentProductName = itemView.findViewById(R.id.lblParentProductName);
                lblParentCategory = itemView.findViewById(R.id.lblParentCategory);
                lblParentSku = itemView.findViewById(R.id.lblParentSku);
            }
        }

        class GridViewHolder extends RecyclerView.ViewHolder {
            ImageView imgProductGrid;
            TextView lblProductNameGrid;
            TextView lblCategoryGrid;
            TextView lblPriceGrid;
            TextView lblStockGrid;
            TextView lblVariationsGrid;

            GridViewHolder(View itemView) {
                super(itemView);
                imgProductGrid = itemView.findViewById(R.id.imgProductGrid);
                lblProductNameGrid = itemView.findViewById(R.id.lblProductNameGrid);
                lblCategoryGrid = itemView.findViewById(R.id.lblCategoryGrid);
                lblPriceGrid = itemView.findViewById(R.id.lblPriceGrid);
                lblStockGrid = itemView.findViewById(R.id.lblStockGrid);
                lblVariationsGrid = itemView.findViewById(R.id.lblVariationsGrid);
            }
        }
    }

    private class ProductAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return displayItems.get(position).type;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == DisplayItem.TYPE_PARENT) {
                View view = LayoutInflater.from(CatalogActivity.this).inflate(R.layout.item_product_parent, parent, false);
                return new ParentViewHolder(view);
            } else if (viewType == DisplayItem.TYPE_VARIATION) {
                View view = LayoutInflater.from(CatalogActivity.this).inflate(R.layout.item_product_variation, parent, false);
                return new VariationViewHolder(view);
            } else {
                View view = LayoutInflater.from(CatalogActivity.this).inflate(R.layout.item_product, parent, false);
                return new NormalViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
            final DisplayItem item = displayItems.get(position);

            if (holder instanceof ParentViewHolder) {
                ParentViewHolder h = (ParentViewHolder) holder;
                final ProductModel p = item.parentProduct;

                h.lblParentProductName.setText(p.name);
                h.lblParentCategory.setText(p.category);

                String skuText = (p.sku != null && !p.sku.isEmpty()) ? p.sku : "";
                String sampleText = (p.sampleCode != null && !p.sampleCode.isEmpty()) ? p.sampleCode : "";
                if (!skuText.isEmpty() && !sampleText.isEmpty()) {
                    h.lblParentSku.setText("SKU: " + skuText + " | Sample: " + sampleText);
                    h.lblParentSku.setVisibility(View.VISIBLE);
                } else if (!skuText.isEmpty()) {
                    h.lblParentSku.setText("SKU: " + skuText);
                    h.lblParentSku.setVisibility(View.VISIBLE);
                } else if (!sampleText.isEmpty()) {
                    h.lblParentSku.setText("Sample: " + sampleText);
                    h.lblParentSku.setVisibility(View.VISIBLE);
                } else {
                    h.lblParentSku.setVisibility(View.GONE);
                }

                if (h.imgParentProduct != null) {
                    if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
                        Bitmap bm = getCachedThumbnail(p.localImagePath, 2);
                        if (bm != null) {
                            h.imgParentProduct.setImageBitmap(bm);
                        } else {
                            h.imgParentProduct.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    } else {
                        h.imgParentProduct.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                }

                h.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showProductQuickViewDialog(p);
                    }
                });

            } else if (holder instanceof VariationViewHolder) {
                VariationViewHolder h = (VariationViewHolder) holder;
                final ProductModel p = item.parentProduct;

                h.lblVarName.setText(item.variationName);
                h.lblVarPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", item.variationPrice));

                int available = item.variationQtyOnHand - item.variationQtyReserved;
                if (available <= 0) {
                    h.lblVarStock.setTextColor(Color.parseColor("#FF3B30"));
                    h.lblVarStock.setText("Out of Stock");
                } else {
                    h.lblVarStock.setTextColor(Color.parseColor("#34C759"));
                    h.lblVarStock.setText("Qty: " + available);
                }

                if (h.imgVarProduct != null) {
                    String varImgPath = "";
                    if (item.variationJsonObj != null) {
                        varImgPath = item.variationJsonObj.optString("image_path", item.variationJsonObj.optString("local_image_path", ""));
                    }
                    if (varImgPath.isEmpty()) {
                        varImgPath = p.localImagePath;
                    }

                    if (varImgPath != null && !varImgPath.isEmpty()) {
                        Bitmap bm = getCachedThumbnail(varImgPath, 2);
                        if (bm != null) {
                            h.imgVarProduct.setImageBitmap(bm);
                        } else {
                            h.imgVarProduct.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    } else {
                        h.imgVarProduct.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                }

                final ProductModel variationModel = new ProductModel();
                variationModel.id = p.id;
                variationModel.name = p.name + " - " + item.variationName;
                variationModel.category = p.category;
                variationModel.price = item.variationPrice;
                variationModel.wholesalePrice = item.variationPrice;
                variationModel.qtyOnHand = item.variationQtyOnHand;
                variationModel.qtyReserved = item.variationQtyReserved;
                variationModel.localImagePath = p.localImagePath;
                variationModel.sku = p.sku;
                variationModel.sampleCode = p.sampleCode;
                variationModel.variationsJson = p.variationsJson;
                variationModel.brand = p.brand;
                variationModel.description = p.description;

                h.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showProductQuickViewDialog(variationModel);
                    }
                });

            } else if (holder instanceof NormalViewHolder) {
                NormalViewHolder h = (NormalViewHolder) holder;
                final ProductModel p = item.parentProduct;

                h.lblProductName.setText(p.name);
                h.lblCategory.setText(p.category);

                if (p.sampleCode != null && !p.sampleCode.trim().isEmpty()) {
                    h.lblSampleCode.setText("Sample Code: " + p.sampleCode);
                    h.lblSampleCode.setVisibility(View.VISIBLE);
                } else {
                    h.lblSampleCode.setVisibility(View.GONE);
                }

                if (h.imgProduct != null) {
                    if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
                        Bitmap bm = getCachedThumbnail(p.localImagePath, 2);
                        if (bm != null) {
                            h.imgProduct.setImageBitmap(bm);
                        } else {
                            h.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    } else {
                        h.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                }

                if (h.lblVariations != null) {
                    h.lblVariations.setVisibility(View.GONE);
                }

                double displayPrice = (p.wholesalePrice != null && p.wholesalePrice > 0) ? p.wholesalePrice : (p.price != null ? p.price : 0.0);
                h.lblPrice.setText(String.format(Locale.getDefault(), "LKR %.2f", displayPrice));

                int available = p.qtyOnHand - p.qtyReserved;
                if (available <= 0) {
                    h.lblStock.setTextColor(Color.parseColor("#FF3B30"));
                    h.lblStock.setText("Out of Stock");
                } else {
                    h.lblStock.setTextColor(Color.parseColor("#34C759"));
                    h.lblStock.setText(available + " units");
                }

                h.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showProductQuickViewDialog(p);
                    }
                });
            }
        }

        class ParentViewHolder extends RecyclerView.ViewHolder {
            ImageView imgParentProduct;
            TextView lblParentProductName;
            TextView lblParentCategory;
            TextView lblParentSku;

            ParentViewHolder(View itemView) {
                super(itemView);
                imgParentProduct = itemView.findViewById(R.id.imgParentProduct);
                lblParentProductName = itemView.findViewById(R.id.lblParentProductName);
                lblParentCategory = itemView.findViewById(R.id.lblParentCategory);
                lblParentSku = itemView.findViewById(R.id.lblParentSku);
            }
        }

        class VariationViewHolder extends RecyclerView.ViewHolder {
            ImageView imgVarProduct;
            TextView lblVarName;
            TextView lblVarPrice;
            TextView lblVarStock;

            VariationViewHolder(View itemView) {
                super(itemView);
                imgVarProduct = itemView.findViewById(R.id.imgVarProduct);
                lblVarName = itemView.findViewById(R.id.lblVarName);
                lblVarPrice = itemView.findViewById(R.id.lblVarPrice);
                lblVarStock = itemView.findViewById(R.id.lblVarStock);
            }
        }

        class NormalViewHolder extends RecyclerView.ViewHolder {
            ImageView imgProduct;
            TextView lblProductName;
            TextView lblCategory;
            TextView lblSampleCode;
            TextView lblVariations;
            TextView lblPrice;
            TextView lblStock;

            NormalViewHolder(View itemView) {
                super(itemView);
                imgProduct = itemView.findViewById(R.id.imgProduct);
                lblProductName = itemView.findViewById(R.id.lblProductName);
                lblCategory = itemView.findViewById(R.id.lblCategory);
                lblSampleCode = itemView.findViewById(R.id.lblSampleCode);
                lblVariations = itemView.findViewById(R.id.lblVariations);
                lblPrice = itemView.findViewById(R.id.lblPrice);
                lblStock = itemView.findViewById(R.id.lblStock);
            }
        }
    }
}
