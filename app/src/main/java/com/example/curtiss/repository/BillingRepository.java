package com.example.curtiss.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.curtiss.BillingActivity;
import com.example.curtiss.DatabaseHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BillingRepository {
    private final DatabaseHelper dbHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BillingRepository(Context context) {
        dbHelper = DatabaseHelper.getInstance(context);
    }
    
    // Abstracting out the DB calls
    public LiveData<List<String>> getCategories() {
        MutableLiveData<List<String>> categoriesLiveData = new MutableLiveData<>();
        executor.execute(() -> {
            List<String> categories = new ArrayList<>();
            categories.add("All Categories");
            android.database.Cursor cursor = dbHelper.getReadableDatabase().rawQuery("SELECT DISTINCT category_name FROM products WHERE category_name IS NOT NULL AND category_name != '' ORDER BY category_name ASC", null);
            if (cursor.moveToFirst()) {
                do {
                    categories.add(cursor.getString(0));
                } while (cursor.moveToNext());
            }
            cursor.close();
            categoriesLiveData.postValue(categories);
        });
        return categoriesLiveData;
    }
    
    public LiveData<List<BillingActivity.CustomerModel>> getCustomers() {
        MutableLiveData<List<BillingActivity.CustomerModel>> customersLiveData = new MutableLiveData<>();
        executor.execute(() -> {
            List<BillingActivity.CustomerModel> list = new ArrayList<>();
            android.database.Cursor cursor = dbHelper.getReadableDatabase().rawQuery("SELECT * FROM customers WHERE status = 'active' ORDER BY name ASC", null);
            if (cursor.moveToFirst()) {
                do {
                    BillingActivity.CustomerModel cm = new BillingActivity.CustomerModel();
                    cm.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                    cm.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    cm.phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"));
                    cm.address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                    cm.territory = cursor.getString(cursor.getColumnIndexOrThrow("territory"));
                    
                    int creditLimitIdx = cursor.getColumnIndex("credit_limit");
                    if (creditLimitIdx != -1) {
                        cm.creditLimit = new java.math.BigDecimal(cursor.getDouble(creditLimitIdx));
                    }
                    
                    int outstandingIdx = cursor.getColumnIndex("outstanding_amount");
                    if (outstandingIdx != -1) {
                        cm.outstanding = new java.math.BigDecimal(cursor.getDouble(outstandingIdx));
                    }
                    list.add(cm);
                } while (cursor.moveToNext());
            }
            cursor.close();
            customersLiveData.postValue(list);
        });
        return customersLiveData;
    }
}
