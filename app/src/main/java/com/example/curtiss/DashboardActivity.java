package com.example.curtiss;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.curtiss.network.ApiClient;
import com.example.curtiss.network.ApiService;
import com.example.curtiss.network.models.DashboardAnalyticsResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

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

    private ExecutorService executorService;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        prefs = SecurePreferences.getSessionPrefs(this);
        userId = prefs.getInt("user_id", 0);
        baseUrl = prefs.getString("base_url", "https://falcon.trycurtiss.com");
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupMonthSpinner();
        setupListeners();
        setupBottomNavigation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    private void initViews() {
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
        // Removed SwipeRefreshLayout listener
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
            return;
        }

        txtOfflineBanner.setVisibility(View.GONE);
        fetchAnalyticsAsync(showOverlay);
    }

    private void fetchAnalyticsAsync(final boolean showOverlay) {
        if (showOverlay && layoutLoadingOverlay != null) {
            layoutLoadingOverlay.setVisibility(View.VISIBLE);
        }

        ApiService apiService = ApiClient.getClient(this).create(ApiService.class);
        apiService.getPerformanceAnalytics(userId, startDate, endDate).enqueue(new Callback<DashboardAnalyticsResponse>() {
            @Override
            public void onResponse(Call<DashboardAnalyticsResponse> call, Response<DashboardAnalyticsResponse> response) {
                if (layoutLoadingOverlay != null) layoutLoadingOverlay.setVisibility(View.GONE);
                
                if (response.isSuccessful() && response.body() != null) {
                    DashboardAnalyticsResponse res = response.body();
                    if (res.success && res.data != null) {
                        populateDashboard(res.data);
                    } else {
                        String msg = res.message != null ? res.message : "Failed to load dashboard data.";
                        Toast.makeText(DashboardActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(DashboardActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<DashboardAnalyticsResponse> call, Throwable t) {
                if (layoutLoadingOverlay != null) layoutLoadingOverlay.setVisibility(View.GONE);
                Toast.makeText(DashboardActivity.this, "Connection error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populateDashboard(DashboardAnalyticsResponse.Data data) {
        // Overall Banner
        double overallScore = data.overallScore;
        txtOverallScore.setText(String.format(Locale.US, "Score: %.0f%%", overallScore));

        DashboardAnalyticsResponse.Payroll payroll = data.payroll;
        double baseSalary = payroll != null ? payroll.baseSalary : 0.0;
        double totalEarnings = payroll != null ? payroll.totalEarnings : 0.0;
        txtTotalEarnings.setText(formatCurrency(totalEarnings));
        txtBaseSalary.setText("Base Salary: " + formatCurrency(baseSalary) + "/month");

        // Card 1: Net Sales
        double netSales = data.netSales;
        int invCount = data.invoiceCount;
        double returns = data.totalReturns;
        txtNetSales.setText(formatCurrency(netSales));
        txtNetSalesSub.setText(invCount + " invoices Â· Ret " + formatCurrency(returns));

        // Card 2: Sales Needed
        double salesTarget = data.salesTarget;
        double salesNeeded = data.salesNeededForTarget;
        if (salesTarget > 0 && salesNeeded <= 0) {
            txtSalesNeeded.setText("âœ“ Achieved!");
            txtSalesNeeded.setTextColor(0xFF10B981); // Emerald green
        } else {
            txtSalesNeeded.setText(formatCurrency(salesNeeded));
            txtSalesNeeded.setTextColor(0xFFFBBF24); // Amber
        }
        txtSalesNeededSub.setText("Target: " + formatCurrency(salesTarget));

        // Card 3: Avg Daily Sales Needed
        double avgSalesDay = data.avgSalesNeededPerDay;
        int remainingDays = data.remainingWorkingDays;
        if (salesNeeded <= 0) {
            txtAvgSalesDay.setText("Rs 0");
        } else {
            txtAvgSalesDay.setText(formatCurrency(avgSalesDay));
        }
        txtAvgSalesDaySub.setText(remainingDays + " working days left");

        // Card 4: Total Collections
        double collections = data.totalCollections;
        double efficiency = data.collectionEfficiency;
        txtCollections.setText(formatCurrency(collections));
        txtCollectionsSub.setText(String.format(Locale.US, "Efficiency: %.1f%%", efficiency));

        // Card 5: Collections Needed
        double collNeeded = data.collectionsNeededForTarget;
        double collTargetPct = data.collectionTargetPct;
        double targetCollAmount = data.targetCollectionAmount;
        if (targetCollAmount > 0 && collNeeded <= 0) {
            txtCollectionsNeeded.setText("âœ“ Achieved!");
            txtCollectionsNeeded.setTextColor(0xFF10B981);
        } else {
            txtCollectionsNeeded.setText(formatCurrency(collNeeded));
            txtCollectionsNeeded.setTextColor(0xFFA855F7); // Purple
        }
        txtCollectionsNeededSub.setText(String.format(Locale.US, "Target: %.0f%% (%s)", collTargetPct, formatCurrency(targetCollAmount)));

        // Card 6: Total Credit Outstanding
        double outstanding = data.totalOutstanding;
        txtTotalCredit.setText(formatCurrency(outstanding));
        txtTotalCreditSub.setText("Total Credit Outstanding");

        // Card 7: Productive Visits
        int prodVisits = data.productiveVisits;
        DashboardAnalyticsResponse.Targets targets = data.targets;
        int pvTarget = targets != null ? targets.productiveVisitsTarget : 0;
        txtProdVisits.setText(String.valueOf(prodVisits));
        txtProdVisitsSub.setText("Target: " + pvTarget + " bills");

        // Card 8: Working Days
        int workingDays = data.workingDays;
        int wdTarget = targets != null ? targets.workingDaysTarget : 0;
        txtWorkingDays.setText(String.valueOf(workingDays));
        txtWorkingDaysSub.setText("Target: " + wdTarget + " days");

        // Earnings Breakdown Table
        if (payroll != null) {
            double commAmount = payroll.salesCommission;
            DashboardAnalyticsResponse.Settings pSettings = payroll.settings;
            double commRate = pSettings != null ? pSettings.salesCommissionPct : 0.0;
            txtCommissionAmount.setText("+ " + formatCurrency(commAmount));
            txtCommissionDesc.setText(String.format(Locale.US, "%.2f%% of Collections", commRate));

            double incAmount = payroll.salesIncentive;
            double incRate = pSettings != null ? pSettings.salesIncentivePct : 0.0;
            double incCap = pSettings != null ? pSettings.salesIncentiveMaxLimit : 0.0;
            txtIncentiveAmount.setText("+ " + formatCurrency(incAmount));
            txtIncentiveDesc.setText(String.format(Locale.US, "Rate: %.2f%% Â· Cap: %s", incRate, formatCurrency(incCap)));

            double pvBonus = payroll.productiveVisitsBonus;
            double pvBonusRate = payroll.productiveVisitsBonusRate;
            txtPvBonusAmount.setText("+ " + formatCurrency(pvBonus));
            if (pvTarget > 0 && prodVisits >= pvTarget) {
                txtPvBonusDesc.setText("âœ“ Hit (" + prodVisits + "/" + pvTarget + " bills)");
            } else if (pvTarget > 0) {
                txtPvBonusDesc.setText("Short (" + prodVisits + "/" + pvTarget + " bills)");
            } else {
                txtPvBonusDesc.setText("No target set");
            }

            double wdBonus = payroll.workingDaysBonus;
            double wdBonusRate = payroll.workingDaysBonusRate;
            txtWdBonusAmount.setText("+ " + formatCurrency(wdBonus));
            if (wdTarget > 0 && workingDays >= wdTarget) {
                txtWdBonusDesc.setText("âœ“ Hit (" + workingDays + "/" + wdTarget + " days)");
            } else if (wdTarget > 0) {
                txtWdBonusDesc.setText("Short (" + workingDays + "/" + wdTarget + " days)");
            } else {
                txtWdBonusDesc.setText("No target set");
            }

            double collBonus = payroll.collectionBonus;
            double collBonusRate = payroll.collectionBonusRate;
            txtCollBonusAmount.setText("+ " + formatCurrency(collBonus));
            if (collTargetPct > 0 && efficiency >= collTargetPct) {
                txtCollBonusDesc.setText(String.format(Locale.US, "âœ“ Hit (%.1f%%/%.0f%%)", efficiency, collTargetPct));
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
