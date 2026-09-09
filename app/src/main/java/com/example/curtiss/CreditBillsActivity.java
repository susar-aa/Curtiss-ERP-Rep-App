package com.example.curtiss;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreditBillsActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private RecyclerView recyclerCreditBills;
    private TextView txtNoOutstanding;
    private EditText edtSearchCreditCustomer;
    private CreditBillsAdapter adapter;
    private List<Map<String, Object>> customersList = new ArrayList<>();
    private List<Map<String, Object>> filteredCustomersList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credit_bills);

        dbHelper = new DatabaseHelper(this);

        Toolbar toolbar = findViewById(R.id.toolbarCreditBills);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Outstanding Collections");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerCreditBills = findViewById(R.id.recyclerCreditBills);
        txtNoOutstanding = findViewById(R.id.txtNoOutstanding);
        edtSearchCreditCustomer = findViewById(R.id.edtSearchCreditCustomer);

        recyclerCreditBills.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CreditBillsAdapter();
        recyclerCreditBills.setAdapter(adapter);

        edtSearchCreditCustomer.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCustomers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadOutstandingCustomers();
    }

    private void loadOutstandingCustomers() {
        customersList.clear();
        Cursor cursor = dbHelper.getOutstandingCustomersByActiveRouteMainTerritory();
        
        if (cursor == null || cursor.getCount() == 0) {
            if (cursor != null) cursor.close();
            txtNoOutstanding.setVisibility(View.VISIBLE);
            recyclerCreditBills.setVisibility(View.GONE);
            edtSearchCreditCustomer.setVisibility(View.GONE);
            return;
        }

        txtNoOutstanding.setVisibility(View.GONE);
        recyclerCreditBills.setVisibility(View.VISIBLE);
        edtSearchCreditCustomer.setVisibility(View.VISIBLE);

        while (cursor.moveToNext()) {
            Map<String, Object> customer = new HashMap<>();
            customer.put("customer_id", cursor.getInt(cursor.getColumnIndexOrThrow("customer_id")));
            customer.put("customer_name", cursor.getString(cursor.getColumnIndexOrThrow("customer_name")));
            customer.put("customer_address", cursor.getString(cursor.getColumnIndexOrThrow("customer_address")));
            customer.put("total_outstanding", cursor.getDouble(cursor.getColumnIndexOrThrow("total_outstanding")));
            customersList.add(customer);
        }
        cursor.close();
        filterCustomers(edtSearchCreditCustomer.getText().toString());
    }

    private void filterCustomers(String query) {
        filteredCustomersList.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredCustomersList.addAll(customersList);
        } else {
            String lowerCaseQuery = query.toLowerCase().trim();
            for (Map<String, Object> customer : customersList) {
                String name = (String) customer.get("customer_name");
                if (name != null && name.toLowerCase().contains(lowerCaseQuery)) {
                    filteredCustomersList.add(customer);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private class CreditBillsAdapter extends RecyclerView.Adapter<CreditBillsAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(CreditBillsActivity.this).inflate(R.layout.item_credit_customer, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final Map<String, Object> customer = filteredCustomersList.get(position);
            
            holder.txtName.setText((String) customer.get("customer_name"));
            
            String address = (String) customer.get("customer_address");
            holder.txtAddress.setText(address != null && !address.trim().isEmpty() ? address : "N/A");
            
            double totalOutstanding = (Double) customer.get("total_outstanding");
            holder.txtBalance.setText(String.format("Total Arrears: LKR %,.2f", totalOutstanding));

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(CreditBillsActivity.this, CreditCheckoutActivity.class);
                intent.putExtra("customer_id", (Integer) customer.get("customer_id"));
                intent.putExtra("customer_name", (String) customer.get("customer_name"));
                intent.putExtra("total_outstanding", totalOutstanding);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return filteredCustomersList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtName, txtAddress, txtBalance;
            ViewHolder(View itemView) {
                super(itemView);
                txtName = itemView.findViewById(R.id.txtCreditCustomerName);
                txtAddress = itemView.findViewById(R.id.txtCreditCustomerAddress);
                txtBalance = itemView.findViewById(R.id.txtCreditCustomerBalance);
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadOutstandingCustomers(); // refresh if a payment was made
    }
}
