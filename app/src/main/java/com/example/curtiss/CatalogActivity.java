package com.example.curtiss;

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
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
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

    private List<ProductModel> productList = new ArrayList<>();
    private List<String> categoryList = new ArrayList<>();
    private ProductGridAdapter productGridAdapter;

    private static class ProductModel {
        int id, qtyOnHand, qtyReserved;
        String name, category, localImagePath;
        double price, wholesalePrice;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);

        dbHelper = new DatabaseHelper(this);

        edtProductSearch = findViewById(R.id.edtProductSearch);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        gridProducts = findViewById(R.id.gridProducts);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

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
            Cursor cursor = db.rawQuery("SELECT name FROM categories ORDER BY name ASC", null);
            while (cursor.moveToNext()) {
                categoryList.add(cursor.getString(0));
            }
            cursor.close();

            if (categoryList.size() <= 1) {
                cursor = db.rawQuery("SELECT DISTINCT category_name FROM products WHERE category_name IS NOT NULL AND category_name != '' AND category_name != 'null' ORDER BY category_name ASC", null);
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
                productList.add(p);
            }
            cursor.close();
        } catch (Exception e) {
            android.util.Log.e("CatalogActivity", "Error loading products: " + e.getMessage());
        }

        if (productGridAdapter == null) {
            productGridAdapter = new ProductGridAdapter();
            gridProducts.setAdapter(productGridAdapter);
        } else {
            productGridAdapter.notifyDataSetChanged();
        }
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

            return convertView;
        }
    }
}
