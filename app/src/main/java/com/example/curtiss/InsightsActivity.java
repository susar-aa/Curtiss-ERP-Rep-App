package com.example.curtiss;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class InsightsActivity extends AppCompatActivity {

    private Spinner spinnerMonth;
    private TextView txtOfflineBanner;
    private ImageView btnBack;

    private String startDate = "";
    private String endDate = "";
    private String selectedPeriodLabel = "";

    private final List<String> monthLabels = new ArrayList<>();
    private final List<String> monthStartDates = new ArrayList<>();
    private final List<String> monthEndDates = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insights);

        spinnerMonth = findViewById(R.id.spinnerMonth);
        txtOfflineBanner = findViewById(R.id.txtOfflineBanner);
        btnBack = findViewById(R.id.btnBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        setupMonthSpinner();
        setupInsightButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkNetworkStatus();
    }

    private void checkNetworkStatus() {
        if (!isNetworkAvailable()) {
            if (txtOfflineBanner != null) {
                txtOfflineBanner.setVisibility(View.VISIBLE);
            }
        } else {
            if (txtOfflineBanner != null) {
                txtOfflineBanner.setVisibility(View.GONE);
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                    return capabilities != null && (
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
                }
                return false;
            } else {
                android.net.NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        }
        return false;
    }

    private void setupMonthSpinner() {
        monthLabels.clear();
        monthStartDates.clear();
        monthEndDates.clear();

        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMM yyyy", Locale.US);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < 12; i++) {
            Calendar monthCal = (Calendar) cal.clone();
            monthCal.add(Calendar.MONTH, -i);

            String label = monthYearFormat.format(monthCal.getTime());
            if (i == 0) {
                label += " (Current Month)";
            }
            monthLabels.add(label);

            monthCal.set(Calendar.DAY_OF_MONTH, 1);
            monthStartDates.add(dateFormat.format(monthCal.getTime()));

            monthCal.set(Calendar.DAY_OF_MONTH, monthCal.getActualMaximum(Calendar.DAY_OF_MONTH));
            monthEndDates.add(dateFormat.format(monthCal.getTime()));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_month, monthLabels);
        adapter.setDropDownViewResource(R.layout.item_spinner_month_dropdown);
        spinnerMonth.setAdapter(adapter);

        if (!monthStartDates.isEmpty()) {
            startDate = monthStartDates.get(0);
            endDate = monthEndDates.get(0);
            selectedPeriodLabel = monthLabels.get(0);
        }

        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < monthStartDates.size()) {
                    startDate = monthStartDates.get(position);
                    endDate = monthEndDates.get(position);
                    selectedPeriodLabel = monthLabels.get(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupInsightButtons() {
        bindInsightButton(R.id.btnInsightTrend, "trend", "Daily Sales & Collections Trend");
        bindInsightButton(R.id.btnInsightKpi, "kpi", "KPI Performance Scorecard");
        bindInsightButton(R.id.btnInsightVisits, "visits", "Shop Visits & Unproductive Analysis");
        bindInsightButton(R.id.btnInsightPayments, "payments", "Payment Methods Distribution");
        bindInsightButton(R.id.btnInsightCategories, "categories", "Top Sales by Category");
        bindInsightButton(R.id.btnInsightRoutes, "routes", "Route-by-Route Comparison");
        bindInsightButton(R.id.btnInsightTop, "top_customers_products", "Top Customers & Products");
    }

    private void bindInsightButton(int viewId, final String insightType, final String title) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isNetworkAvailable()) {
                        Toast.makeText(InsightsActivity.this, "Internet connection required to view real-time Insights.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(InsightsActivity.this, InsightDetailActivity.class);
                    intent.putExtra(InsightDetailActivity.EXTRA_INSIGHT_TYPE, insightType);
                    intent.putExtra(InsightDetailActivity.EXTRA_TITLE, title);
                    intent.putExtra(InsightDetailActivity.EXTRA_START_DATE, startDate);
                    intent.putExtra(InsightDetailActivity.EXTRA_END_DATE, endDate);
                    intent.putExtra(InsightDetailActivity.EXTRA_PERIOD_LABEL, selectedPeriodLabel);
                    startActivity(intent);
                }
            });
        }
    }
}
