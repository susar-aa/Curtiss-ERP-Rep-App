package com.example.curtiss;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private Spinner spinnerMonth;
    private TextView txtOfflineBanner;
    private RelativeLayout layoutLoadingOverlay;

    // Overall banner
    private TextView txtOverallScore, txtTotalEarnings, txtBaseSalary;
    // KPI Cards
    private TextView txtNetSales, txtNetSalesSub;
    private TextView txtSalesNeeded, txtSalesNeededSub;
    private TextView txtAvgSalesDay, txtAvgSalesDaySub;
    private TextView txtCollections, txtCollectionsSub;
    private TextView txtCollectionsNeeded, txtCollectionsNeededSub;
    private TextView txtTotalCredit, txtTotalCreditSub;
    private TextView txtProdVisits, txtProdVisitsSub;
    private TextView txtWorkingDays, txtWorkingDaysSub;
    // Earnings breakdown
    private TextView txtCommissionAmount, txtCommissionDesc;
    private TextView txtIncentiveAmount, txtIncentiveDesc;
    private TextView txtPvBonusAmount, txtPvBonusDesc;
    private TextView txtWdBonusAmount, txtWdBonusDesc;
    private TextView txtCollBonusAmount, txtCollBonusDesc;
    private TextView txtTotalEarningsFooter;

    private SharedPreferences prefs;
    private int userId;
    private String baseUrl;

    private String startDate = "";
    private String endDate = "";
    private boolean isInitialSelect = true;

    private List<String> monthStartDates = new ArrayList<>();
    private List<String> monthEndDates = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        prefs = SecurePreferences.getSessionPrefs(this);
        userId = prefs.getInt("user_id", 0);
        baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");

        initViews();
        setupMonthSpinner();
        setupListeners();
        setupBottomNavigation();
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipeRefreshDashboard);
        spinnerMonth = findViewById(R.id.spinnerMonth);
        txtOfflineBanner = findViewById(R.id.txtOfflineBanner);
        layoutLoadingOverlay = findViewById(R.id.layoutLoadingOverlay);

        txtOverallScore = findViewById(R.id.txtOverallScore);
        txtTotalEarnings = findViewById(R.id.txtTotalEarnings);
        txtBaseSalary = findViewById(R.id.txtBaseSalary);

        txtNetSales = findViewById(R.id.txtNetSales);
        txtNetSalesSub = findViewById(R.id.txtNetSalesSub);
        txtSalesNeeded = findViewById(R.id.txtSalesNeeded);
        txtSalesNeededSub = findViewById(R.id.txtSalesNeededSub);
        txtAvgSalesDay = findViewById(R.id.txtAvgSalesDay);
        txtAvgSalesDaySub = findViewById(R.id.txtAvgSalesDaySub);
        txtCollections = findViewById(R.id.txtCollections);
        txtCollectionsSub = findViewById(R.id.txtCollectionsSub);
        txtCollectionsNeeded = findViewById(R.id.txtCollectionsNeeded);
        txtCollectionsNeededSub = findViewById(R.id.txtCollectionsNeededSub);
        txtTotalCredit = findViewById(R.id.txtTotalCredit);
        txtTotalCreditSub = findViewById(R.id.txtTotalCreditSub);
        txtProdVisits = findViewById(R.id.txtProdVisits);
        txtProdVisitsSub = findViewById(R.id.txtProdVisitsSub);
        txtWorkingDays = findViewById(R.id.txtWorkingDays);
        txtWorkingDaysSub = findViewById(R.id.txtWorkingDaysSub);

        txtCommissionAmount = findViewById(R.id.txtCommissionAmount);
        txtCommissionDesc = findViewById(R.id.txtCommissionDesc);
        txtIncentiveAmount = findViewById(R.id.txtIncentiveAmount);
        txtIncentiveDesc = findViewById(R.id.txtIncentiveDesc);
        txtPvBonusAmount = findViewById(R.id.txtPvBonusAmount);
        txtPvBonusDesc = findViewById(R.id.txtPvBonusDesc);
        txtWdBonusAmount = findViewById(R.id.txtWdBonusAmount);
        txtWdBonusDesc = findViewById(R.id.txtWdBonusDesc);
        txtCollBonusAmount = findViewById(R.id.txtCollBonusAmount);
        txtCollBonusDesc = findViewById(R.id.txtCollBonusDesc);
        txtTotalEarningsFooter = findViewById(R.id.txtTotalEarningsFooter);
    }

    private void setupMonthSpinner() {
        List<String> monthLabels = new ArrayList<>();
        monthStartDates.clear();
        monthEndDates.clear();

        SimpleDateFormat labelFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 12; i++) {
            Calendar monthCal = (Calendar) cal.clone();
            monthCal.add(Calendar.MONTH, -i);

            String label = labelFormat.format(monthCal.getTime());
            if (i == 0) {
                label += " (Current Month)";
            }
            monthLabels.add(label);

            // 1st day of the month
            monthCal.set(Calendar.DAY_OF_MONTH, 1);
            monthStartDates.add(dateFormat.format(monthCal.getTime()));

            // Last day of the month
            monthCal.set(Calendar.DAY_OF_MONTH, monthCal.getActualMaximum(Calendar.DAY_OF_MONTH));
            monthEndDates.add(dateFormat.format(monthCal.getTime()));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_month, monthLabels);
        adapter.setDropDownViewResource(R.layout.item_spinner_month_dropdown);
        spinnerMonth.setAdapter(adapter);

        // Default to current month
        if (!monthStartDates.isEmpty()) {
            startDate = monthStartDates.get(0);
            endDate = monthEndDates.get(0);
        }

        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < monthStartDates.size()) {
                    startDate = monthStartDates.get(position);
                    endDate = monthEndDates.get(position);
                    checkConnectionAndLoad(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupListeners() {
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                checkConnectionAndLoad(false);
            }
        });
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
            } else {
                @SuppressWarnings("deprecation")
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        }
        return false;
    }

    private void showOfflineWarning() {
        txtOfflineBanner.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Internet connection required to view Sales Dashboard.", Toast.LENGTH_LONG).show();
    }

    private void checkConnectionAndLoad(boolean showOverlay) {
        if (!isNetworkAvailable()) {
            showOfflineWarning();
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            return;
        }

        txtOfflineBanner.setVisibility(View.GONE);
        new FetchAnalyticsTask(showOverlay).execute();
    }

    // 100% Online real-time analytics task
    private class FetchAnalyticsTask extends AsyncTask<Void, Void, String> {
        private boolean showOverlay;

        public FetchAnalyticsTask(boolean showOverlay) {
            this.showOverlay = showOverlay;
        }

        @Override
        protected void onPreExecute() {
            if (showOverlay && layoutLoadingOverlay != null) {
                layoutLoadingOverlay.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected String doInBackground(Void... voids) {
            HttpURLConnection conn = null;
            try {
                StringBuilder urlBuilder = new StringBuilder(baseUrl);
                urlBuilder.append("/rep/RepDashboard/api_get_performance_analytics");
                urlBuilder.append("?user_id=").append(userId);
                if (startDate != null && !startDate.isEmpty()) {
                    urlBuilder.append("&start_date=").append(URLEncoder.encode(startDate, "UTF-8"));
                }
                if (endDate != null && !endDate.isEmpty()) {
                    urlBuilder.append("&end_date=").append(URLEncoder.encode(endDate, "UTF-8"));
                }

                URL url = new URL(urlBuilder.toString());
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("X-User-ID", String.valueOf(userId));
                String token = prefs.getString("api_token", "");
                if (!token.isEmpty()) { conn.setRequestProperty("Authorization", "Bearer " + token); }
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        sb.append(line);
                    }
                    in.close();
                    return sb.toString();
                } else {
                    return "{\"success\":false,\"message\":\"Server responded with code " + responseCode + "\"}";
                }
            } catch (Exception e) {
                return "{\"success\":false,\"message\":\"Connection error: " + e.getMessage() + "\"}";
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (layoutLoadingOverlay != null) layoutLoadingOverlay.setVisibility(View.GONE);
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

            if (result == null) {
                Toast.makeText(DashboardActivity.this, "Empty response from server", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                JSONObject response = new JSONObject(result);
                if (response.optBoolean("success", false)) {
                    JSONObject data = response.optJSONObject("data");
                    if (data != null) {
                        populateDashboard(data);
                    }
                } else {
                    String msg = response.optString("message", "Failed to load dashboard data.");
                    Toast.makeText(DashboardActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(DashboardActivity.this, "Error parsing server response", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void populateDashboard(JSONObject data) {
        // Overall Banner
        double overallScore = data.optDouble("overall_score", 0.0);
        txtOverallScore.setText(String.format(Locale.US, "Score: %.0f%%", overallScore));

        JSONObject payroll = data.optJSONObject("payroll");
        double baseSalary = payroll != null ? payroll.optDouble("base_salary", 0.0) : 0.0;
        double totalEarnings = payroll != null ? payroll.optDouble("total_earnings", 0.0) : 0.0;
        txtTotalEarnings.setText(formatCurrency(totalEarnings));
        txtBaseSalary.setText("Base Salary: " + formatCurrency(baseSalary) + "/month");

        // Card 1: Net Sales
        double netSales = data.optDouble("net_sales", 0.0);
        int invCount = data.optInt("invoice_count", 0);
        double returns = data.optDouble("total_returns", 0.0);
        txtNetSales.setText(formatCurrency(netSales));
        txtNetSalesSub.setText(invCount + " invoices · Ret " + formatCurrency(returns));

        // Card 2: Sales Needed
        double salesTarget = data.optDouble("sales_target", 0.0);
        double salesNeeded = data.optDouble("sales_needed_for_target", Math.max(0.0, salesTarget - netSales));
        if (salesTarget > 0 && salesNeeded <= 0) {
            txtSalesNeeded.setText("✓ Achieved!");
            txtSalesNeeded.setTextColor(0xFF10B981); // Emerald green
        } else {
            txtSalesNeeded.setText(formatCurrency(salesNeeded));
            txtSalesNeeded.setTextColor(0xFFFBBF24); // Amber
        }
        txtSalesNeededSub.setText("Target: " + formatCurrency(salesTarget));

        // Card 3: Avg Daily Sales Needed
        double avgSalesDay = data.optDouble("avg_sales_needed_per_day", 0.0);
        int remainingDays = data.optInt("remaining_working_days", 0);
        if (salesNeeded <= 0) {
            txtAvgSalesDay.setText("Rs 0");
        } else {
            txtAvgSalesDay.setText(formatCurrency(avgSalesDay));
        }
        txtAvgSalesDaySub.setText(remainingDays + " working days left");

        // Card 4: Total Collections
        double collections = data.optDouble("total_collections", 0.0);
        double efficiency = data.optDouble("collection_efficiency", 0.0);
        txtCollections.setText(formatCurrency(collections));
        txtCollectionsSub.setText(String.format(Locale.US, "Efficiency: %.1f%%", efficiency));

        // Card 5: Collections Needed
        double collNeeded = data.optDouble("collections_needed_for_target", 0.0);
        double collTargetPct = data.optDouble("collection_target_pct", 80.0);
        double targetCollAmount = data.optDouble("target_collection_amount", 0.0);
        if (targetCollAmount > 0 && collNeeded <= 0) {
            txtCollectionsNeeded.setText("✓ Achieved!");
            txtCollectionsNeeded.setTextColor(0xFF10B981);
        } else {
            txtCollectionsNeeded.setText(formatCurrency(collNeeded));
            txtCollectionsNeeded.setTextColor(0xFFA855F7); // Purple
        }
        txtCollectionsNeededSub.setText(String.format(Locale.US, "Target: %.0f%% (%s)", collTargetPct, formatCurrency(targetCollAmount)));

        // Card 6: Total Credit Outstanding
        double outstanding = data.optDouble("total_outstanding", 0.0);
        txtTotalCredit.setText(formatCurrency(outstanding));
        txtTotalCreditSub.setText("Total Credit Outstanding");

        // Card 7: Productive Visits
        int prodVisits = data.optInt("productive_visits", 0);
        JSONObject targets = data.optJSONObject("targets");
        int pvTarget = targets != null ? targets.optInt("productive_visits_target", 0) : 0;
        txtProdVisits.setText(String.valueOf(prodVisits));
        txtProdVisitsSub.setText("Target: " + pvTarget + " bills");

        // Card 8: Working Days
        int workingDays = data.optInt("working_days", 0);
        int wdTarget = targets != null ? targets.optInt("working_days_target", 0) : 0;
        txtWorkingDays.setText(String.valueOf(workingDays));
        txtWorkingDaysSub.setText("Target: " + wdTarget + " days");

        // Earnings Breakdown Table
        if (payroll != null) {
            double commAmount = payroll.optDouble("sales_commission", 0.0);
            JSONObject pSettings = payroll.optJSONObject("settings");
            double commRate = pSettings != null ? pSettings.optDouble("sales_commission_pct", 0.0) : 0.0;
            txtCommissionAmount.setText("+ " + formatCurrency(commAmount));
            txtCommissionDesc.setText(String.format(Locale.US, "%.2f%% of Collections", commRate));

            double incAmount = payroll.optDouble("sales_incentive", 0.0);
            double incRate = pSettings != null ? pSettings.optDouble("sales_incentive_pct", 0.0) : 0.0;
            double incCap = pSettings != null ? pSettings.optDouble("sales_incentive_max_limit", 0.0) : 0.0;
            txtIncentiveAmount.setText("+ " + formatCurrency(incAmount));
            txtIncentiveDesc.setText(String.format(Locale.US, "Rate: %.2f%% · Cap: %s", incRate, formatCurrency(incCap)));

            double pvBonus = payroll.optDouble("productive_visits_bonus", 0.0);
            double pvBonusRate = payroll.optDouble("productive_visits_bonus_rate", 0.0);
            txtPvBonusAmount.setText("+ " + formatCurrency(pvBonus));
            if (pvTarget > 0 && prodVisits >= pvTarget) {
                txtPvBonusDesc.setText("✓ Hit (" + prodVisits + "/" + pvTarget + " bills)");
            } else if (pvTarget > 0) {
                txtPvBonusDesc.setText("Short (" + prodVisits + "/" + pvTarget + " bills)");
            } else {
                txtPvBonusDesc.setText("No target set");
            }

            double wdBonus = payroll.optDouble("working_days_bonus", 0.0);
            double wdBonusRate = payroll.optDouble("working_days_bonus_rate", 0.0);
            txtWdBonusAmount.setText("+ " + formatCurrency(wdBonus));
            if (wdTarget > 0 && workingDays >= wdTarget) {
                txtWdBonusDesc.setText("✓ Hit (" + workingDays + "/" + wdTarget + " days)");
            } else if (wdTarget > 0) {
                txtWdBonusDesc.setText("Short (" + workingDays + "/" + wdTarget + " days)");
            } else {
                txtWdBonusDesc.setText("No target set");
            }

            double collBonus = payroll.optDouble("collection_bonus", 0.0);
            double collBonusRate = payroll.optDouble("collection_bonus_rate", 0.0);
            txtCollBonusAmount.setText("+ " + formatCurrency(collBonus));
            if (collTargetPct > 0 && efficiency >= collTargetPct) {
                txtCollBonusDesc.setText(String.format(Locale.US, "✓ Hit (%.1f%%/%.0f%%)", efficiency, collTargetPct));
            } else if (collTargetPct > 0) {
                txtCollBonusDesc.setText(String.format(Locale.US, "Short (%.1f%%/%.0f%%)", efficiency, collTargetPct));
            } else {
                txtCollBonusDesc.setText("No target set");
            }

            txtTotalEarningsFooter.setText(formatCurrency(totalEarnings));
        }
    }

    private String formatCurrency(double amount) {
        return String.format(Locale.US, "Rs %,.2f", amount);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_dashboard);
            bottomNavigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int itemId = item.getItemId();
                    if (itemId == R.id.nav_home) {
                        Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        finish();
                        return true;
                    } else if (itemId == R.id.nav_customers) {
                        Intent intent = new Intent(DashboardActivity.this, CustomerActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        finish();
                        return true;
                    } else if (itemId == R.id.nav_history) {
                        Intent intent = new Intent(DashboardActivity.this, HistoryActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        finish();
                        return true;
                    } else if (itemId == R.id.nav_dashboard) {
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_dashboard);
        }
    }
}
