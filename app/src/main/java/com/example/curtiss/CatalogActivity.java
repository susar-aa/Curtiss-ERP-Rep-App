package com.example.curtiss;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
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
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CatalogActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private EditText edtProductSearch;
    private Spinner spinnerCategory;
    private GridView gridProducts;
    private ImageButton btnBack;

    // Redesigned layouts and controls
    private View layoutExpandedSearch;
    private ImageButton btnSearchToggle;
    private ImageButton btnClearSearch;
    private TextView txtHeaderTitle;
    private String selectedCategoryName = "All Categories";

    private List<ProductModel> productList = new ArrayList<>();
    private List<String> categoryList = new ArrayList<>();
    private ProductGridAdapter productGridAdapter;

    private static class ProductModel {
        int id, qtyOnHand, qtyReserved;
        String name, category, localImagePath;
        double price, wholesalePrice;
        String sku, sampleCode, variationsJson;
        String brand, description;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);

        dbHelper = DatabaseHelper.getInstance(this);

        edtProductSearch = findViewById(R.id.edtProductSearch);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        gridProducts = findViewById(R.id.gridProducts);
        btnBack = findViewById(R.id.btnBack);

        // Bind expanded layouts and controls
        layoutExpandedSearch = findViewById(R.id.layoutExpandedSearch);
        btnSearchToggle = findViewById(R.id.btnSearchToggle);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        txtHeaderTitle = findViewById(R.id.txtHeaderTitle);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (btnSearchToggle != null) {
            btnSearchToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (layoutExpandedSearch != null) {
                        if (layoutExpandedSearch.getVisibility() == View.GONE) {
                            layoutExpandedSearch.setVisibility(View.VISIBLE);
                            if (txtHeaderTitle != null) txtHeaderTitle.setVisibility(View.GONE);
                            if (edtProductSearch != null) {
                                edtProductSearch.requestFocus();
                                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) {
                                    imm.showSoftInput(edtProductSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                                }
                            }
                        } else {
                            layoutExpandedSearch.setVisibility(View.GONE);
                            if (txtHeaderTitle != null) txtHeaderTitle.setVisibility(View.VISIBLE);
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

        setupCategorySpinner();

        edtProductSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadCatalogItems(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadCatalogItems("");
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

        try {
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
                p.sku = cursor.getString(cursor.getColumnIndexOrThrow("sku"));
                p.sampleCode = cursor.getString(cursor.getColumnIndexOrThrow("sample_code"));
                p.variationsJson = cursor.getString(cursor.getColumnIndexOrThrow("variations_json"));
                p.brand = cursor.getString(cursor.getColumnIndexOrThrow("brand"));
                p.description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
                productList.add(p);
            }
            cursor.close();
        } catch (Exception e) {
            android.util.Log.e("CatalogActivity", "Error loading products: " + e.getMessage());
        }

        // Prioritize exact matches at the top when searching
        if (filter != null && !filter.trim().isEmpty()) {
            final String searchLower = filter.toLowerCase().trim();
            java.util.Collections.sort(productList, new java.util.Comparator<ProductModel>() {
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

        if (productGridAdapter == null) {
            productGridAdapter = new ProductGridAdapter();
            gridProducts.setAdapter(productGridAdapter);
        } else {
            productGridAdapter.notifyDataSetChanged();
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
        addQuickViewSpecRow(specsLayout, "Retail Price", "LKR " + String.format(Locale.getDefault(), "%,.2f", p.price));
        addQuickViewSpecRow(specsLayout, "Wholesale Price", p.wholesalePrice > 0 ? "LKR " + String.format(Locale.getDefault(), "%,.2f", p.wholesalePrice) : "N/A");
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
                android.util.Log.e("CatalogActivity", "Error parsing variations spec: " + e.getMessage());
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
                convertView = LayoutInflater.from(CatalogActivity.this).inflate(R.layout.item_product_grid, parent, false);
            }

            final ProductModel p = productList.get(position);

            ImageView imgProductGrid = convertView.findViewById(R.id.imgProductGrid);
            TextView lblProductNameGrid = convertView.findViewById(R.id.lblProductNameGrid);
            TextView lblCategoryGrid = convertView.findViewById(R.id.lblCategoryGrid);
            TextView lblPriceGrid = convertView.findViewById(R.id.lblPriceGrid);
            TextView lblStockGrid = convertView.findViewById(R.id.lblStockGrid);

            lblProductNameGrid.setText(p.name);
            lblCategoryGrid.setText(p.category);

            lblPriceGrid.setText(String.format(Locale.getDefault(), "LKR %.2f", p.price));

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

            // Click listener for Quick View
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showProductQuickViewDialog(p);
                }
            });

            // Long click listener for Quick View
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
}
