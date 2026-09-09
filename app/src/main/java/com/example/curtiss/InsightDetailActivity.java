package com.example.curtiss;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class InsightDetailActivity extends AppCompatActivity {

    public static final String EXTRA_INSIGHT_TYPE = "extra_insight_type";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_START_DATE = "extra_start_date";
    public static final String EXTRA_END_DATE = "extra_end_date";
    public static final String EXTRA_PERIOD_LABEL = "extra_period_label";

    private String insightType = "";
    private String title = "Insight Detail";
    private String startDate = "";
    private String endDate = "";
    private String periodLabel = "";

    private ImageView btnBack;
    private TextView txtDetailTitle;
    private TextView txtDetailPeriod;
    private TextView txtDetailStatus;
    private SwipeRefreshLayout swipeRefreshDetail;
    private TextView txtSummaryHeader;
    private TextView txtSummaryMain;
    private TextView txtSummarySub;
    private TextView txtDetailEmpty;
    private LinearLayout layoutDetailContainer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insight_detail);

        if (getIntent() != null) {
            insightType = getIntent().getStringExtra(EXTRA_INSIGHT_TYPE);
            title = getIntent().getStringExtra(EXTRA_TITLE);
            startDate = getIntent().getStringExtra(EXTRA_START_DATE);
            endDate = getIntent().getStringExtra(EXTRA_END_DATE);
            periodLabel = getIntent().getStringExtra(EXTRA_PERIOD_LABEL);
        }

        btnBack = findViewById(R.id.btnBack);
        txtDetailTitle = findViewById(R.id.txtDetailTitle);
        txtDetailPeriod = findViewById(R.id.txtDetailPeriod);
        txtDetailStatus = findViewById(R.id.txtDetailStatus);
        swipeRefreshDetail = findViewById(R.id.swipeRefreshDetail);
        txtSummaryHeader = findViewById(R.id.txtSummaryHeader);
        txtSummaryMain = findViewById(R.id.txtSummaryMain);
        txtSummarySub = findViewById(R.id.txtSummarySub);
        txtDetailEmpty = findViewById(R.id.txtDetailEmpty);
        layoutDetailContainer = findViewById(R.id.layoutDetailContainer);

        if (title != null && !title.isEmpty()) {
            txtDetailTitle.setText(title);
        }
        if (periodLabel != null && !periodLabel.isEmpty()) {
            txtDetailPeriod.setText("Period: " + periodLabel);
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        swipeRefreshDetail.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadRealtimeAnalytics();
            }
        });

        loadRealtimeAnalytics();
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

    private void loadRealtimeAnalytics() {
        if (!isNetworkAvailable()) {
            swipeRefreshDetail.setRefreshing(false);
            txtDetailStatus.setText("⚠️ Offline: Internet connection required for live server insights");
            txtDetailStatus.setBackgroundColor(0xFFEF4444);
            txtDetailEmpty.setVisibility(View.VISIBLE);
            txtDetailEmpty.setText("No network connection available. Please connect to internet.");
            layoutDetailContainer.removeAllViews();
            return;
        }

        txtDetailStatus.setText("🟢 Real-Time Server Analytics (100% Online)");
        txtDetailStatus.setBackgroundColor(0xFF3B82F6);
        txtDetailEmpty.setVisibility(View.VISIBLE);
        txtDetailEmpty.setText("Fetching live performance analytics from server...");

        final android.content.SharedPreferences prefs = SecurePreferences.getSessionPrefs(this);
        final int userId = prefs.getInt("user_id", 0);
        final String baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");

        new AsyncTask<Void, Void, String>() {
            @Override
            protected void onPreExecute() {
                swipeRefreshDetail.setRefreshing(true);
            }

            @Override
            protected String doInBackground(Void... voids) {
                HttpURLConnection conn = null;
                try {
                    String urlStr = baseUrl + "/rep/RepDashboard/api_get_performance_analytics?user_id=" + userId + "&start_date=" + startDate + "&end_date=" + endDate;
                    URL url = new URL(urlStr);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("X-User-ID", String.valueOf(userId));
                String token = prefs.getString("api_token", "");
                if (!token.isEmpty()) { conn.setRequestProperty("Authorization", "Bearer " + token); }
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        br.close();
                        return sb.toString();
                    }
                } catch (Exception e) {
                    return null;
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(String result) {
                swipeRefreshDetail.setRefreshing(false);
                if (result == null || result.isEmpty()) {
                    txtDetailEmpty.setText("Failed to load server analytics. Please try again.");
                    Toast.makeText(InsightDetailActivity.this, "Network error loading analytics", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    JSONObject json = new JSONObject(result);
                    if (json.optBoolean("success", false)) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            txtDetailEmpty.setVisibility(View.GONE);
                            renderInsightData(data);
                            return;
                        }
                    }
                    String msg = json.optString("message", "Could not load insight data");
                    txtDetailEmpty.setText(msg);
                } catch (Exception e) {
                    txtDetailEmpty.setText("Error parsing analytics JSON from server.");
                }
            }
        }.execute();
    }

    private void renderInsightData(JSONObject data) {
        layoutDetailContainer.removeAllViews();

        if ("trend".equals(insightType)) {
            renderTrendInsight(data);
        } else if ("kpi".equals(insightType)) {
            renderKpiScorecard(data);
        } else if ("visits".equals(insightType)) {
            renderVisitsInsight(data);
        } else if ("payments".equals(insightType)) {
            renderPaymentsInsight(data);
        } else if ("categories".equals(insightType)) {
            renderCategoriesInsight(data);
        } else if ("routes".equals(insightType)) {
            renderRoutesInsight(data);
        } else if ("top_customers_products".equals(insightType)) {
            renderTopCustomersAndProducts(data);
        } else {
            txtDetailEmpty.setVisibility(View.VISIBLE);
            txtDetailEmpty.setText("Unknown insight type.");
        }
    }

    private void renderTrendInsight(JSONObject data) {
        double netSales = data.optDouble("net_sales", 0);
        double totalCollections = data.optDouble("total_collections", 0);

        txtSummaryHeader.setText("MONTHLY INVOICED vs COLLECTIONS");
        txtSummaryMain.setText(formatCurrency(netSales));
        txtSummarySub.setText("Collections: " + formatCurrency(totalCollections));

        JSONArray salesTrend = data.optJSONArray("sales_trend");
        if (salesTrend == null || salesTrend.length() == 0) {
            addEmptyRow("No daily trend data available for this month.");
            return;
        }

        double maxSale = 0;
        for (int i = 0; i < salesTrend.length(); i++) {
            JSONObject day = salesTrend.optJSONObject(i);
            if (day != null) {
                double sale = day.optDouble("sales_amount", day.optDouble("sale", 0));
                if (sale > maxSale) maxSale = sale;
            }
        }
        if (maxSale == 0) maxSale = 1.0;

        for (int i = salesTrend.length() - 1; i >= 0; i--) {
            JSONObject day = salesTrend.optJSONObject(i);
            if (day == null) continue;
            String date = day.optString("label", day.optString("date", ""));
            double sale = day.optDouble("sales_amount", day.optDouble("sale", 0));
            int count = day.optInt("invoice_count", day.optInt("count", 1));

            if (sale <= 0 && count <= 0) continue;

            int percent = (int) Math.min(100, Math.round((sale / maxSale) * 100));
            addBarRow(date, formatCurrency(sale), percent, count + " Invoices Issued");
        }
    }

    private void renderKpiScorecard(JSONObject data) {
        double overallScore = data.optDouble("overall_score", 0);
        txtSummaryHeader.setText("OVERALL PERFORMANCE SCORE");
        txtSummaryMain.setText(String.format(Locale.US, "%.1f / 100 pts", overallScore));
        txtSummarySub.setText("Weighted score across all 6 performance KPIs");

        JSONObject kpis = data.optJSONObject("kpi_scores");
        if (kpis == null) {
            addEmptyRow("No KPI scorecard data available.");
            return;
        }

        addKpiCard(kpis.optJSONObject("sales"), "Sales Volume Target", "LKR");
        addKpiCard(kpis.optJSONObject("productive_visits"), "Productive Visits Target", "Visits");
        addKpiCard(kpis.optJSONObject("working_days"), "Working Days Target", "Days");
        addKpiCard(kpis.optJSONObject("collection_efficiency"), "Collection Efficiency Target", "%");
        addKpiCard(kpis.optJSONObject("new_customers"), "New Customers Added", "Customers");
        addKpiCard(kpis.optJSONObject("active_customers"), "Active Buying Customers", "Customers");
    }

    private void addKpiCard(JSONObject kpi, String defaultTitle, String unit) {
        if (kpi == null) return;
        View row = LayoutInflater.from(this).inflate(R.layout.item_insight_kpi_row, layoutDetailContainer, false);

        TextView txtKpiTitle = row.findViewById(R.id.txtKpiTitle);
        TextView txtKpiScorePill = row.findViewById(R.id.txtKpiScorePill);
        TextView txtKpiTarget = row.findViewById(R.id.txtKpiTarget);
        TextView txtKpiAchieved = row.findViewById(R.id.txtKpiAchieved);
        TextView txtKpiNotes = row.findViewById(R.id.txtKpiNotes);
        ProgressBar progressBarKpi = row.findViewById(R.id.progressBarKpi);

        String title = kpi.optString("name", kpi.optString("title", defaultTitle));
        double achieved = kpi.optDouble("actual", kpi.optDouble("achieved", 0));
        double target = kpi.optDouble("target", 0);
        double weight = kpi.optDouble("weight", 0);
        double score = kpi.optDouble("contribution", kpi.optDouble("score", 0));
        double percentage = kpi.optDouble("achievement_pct", kpi.optDouble("percentage", 0));

        txtKpiTitle.setText(title);
        txtKpiScorePill.setText(String.format(Locale.US, "%.1f / %.0f pts", score, weight));

        if ("LKR".equals(unit)) {
            txtKpiTarget.setText("Target: " + formatCurrency(target) + " | Weight: " + (int) weight + "%");
            txtKpiAchieved.setText("Achieved: " + formatCurrency(achieved) + " (" + (int) percentage + "%)");
        } else if ("%".equals(unit)) {
            txtKpiTarget.setText("Target: " + String.format(Locale.US, "%.1f%%", target) + " | Weight: " + (int) weight + "%");
            txtKpiAchieved.setText("Achieved: " + String.format(Locale.US, "%.1f%%", achieved) + " (" + (int) percentage + "% of target)");
        } else {
            txtKpiTarget.setText("Target: " + (int) target + " " + unit + " | Weight: " + (int) weight + "%");
            txtKpiAchieved.setText("Achieved: " + (int) achieved + " " + unit + " (" + (int) percentage + "%)");
        }

        progressBarKpi.setProgress((int) Math.min(100, Math.round(percentage)));
        if (percentage >= 100) {
            txtKpiNotes.setText("✓ Target Achieved! Maximum points earned.");
            txtKpiNotes.setTextColor(0xFF10B981);
        } else {
            txtKpiNotes.setText("In progress — keep pushing to reach " + (int) weight + " points.");
            txtKpiNotes.setTextColor(0xFF94A3B8);
        }

        layoutDetailContainer.addView(row);
    }

    private void renderVisitsInsight(JSONObject data) {
        double prodRate = data.optDouble("productive_visit_rate", 0);
        int totalVisits = data.optInt("total_visited", 0);
        int prodVisits = data.optInt("productive_visits", 0);
        int unprodVisits = data.optInt("unproductive_visits", 0);

        txtSummaryHeader.setText("PRODUCTIVE VISIT CONVERSION");
        txtSummaryMain.setText(String.format(Locale.US, "%.1f%%", prodRate));
        txtSummarySub.setText(prodVisits + " productive out of " + totalVisits + " total visits");

        int prodPercent = totalVisits > 0 ? (int) Math.round(((double) prodVisits / totalVisits) * 100) : 0;
        int unprodPercent = totalVisits > 0 ? (int) Math.round(((double) unprodVisits / totalVisits) * 100) : 0;

        addBarRow("Productive Visits (Orders Placed)", prodVisits + " Visits", prodPercent, prodPercent + "% conversion rate");
        addBarRow("Unproductive Visits (No Order)", unprodVisits + " Visits", unprodPercent, unprodPercent + "% of total visits");

        JSONArray unprodList = data.optJSONArray("recent_unprod");
        if (unprodList != null && unprodList.length() > 0) {
            addSectionHeader("RECENT UNPRODUCTIVE VISITS & REASONS");
            for (int i = 0; i < unprodList.length(); i++) {
                JSONObject uv = unprodList.optJSONObject(i);
                if (uv == null) continue;
                String custName = uv.optString("customer_name", "Unknown Shop");
                String reason = uv.optString("reason", "No reason provided");
                String date = uv.optString("visit_date", "");
                addBarRow(custName, reason, 0, "Visited on " + date);
            }
        }
    }

    private void renderPaymentsInsight(JSONObject data) {
        double totalCollections = data.optDouble("total_collections", 0);
        double cash = data.optDouble("cash_collections", 0);
        double cheque = data.optDouble("cheque_collections", 0);
        double bank = data.optDouble("bank_collections", 0);
        double collEfficiency = data.optDouble("collection_efficiency", 0);
        double outstanding = data.optDouble("outstanding_amount", 0);

        txtSummaryHeader.setText("TOTAL COLLECTIONS & EFFICIENCY");
        txtSummaryMain.setText(formatCurrency(totalCollections));
        txtSummarySub.setText(String.format(Locale.US, "Collection Efficiency: %.1f%% | Outstanding: ", collEfficiency) + formatCurrency(outstanding));

        int cashPct = totalCollections > 0 ? (int) Math.round((cash / totalCollections) * 100) : 0;
        int chequePct = totalCollections > 0 ? (int) Math.round((cheque / totalCollections) * 100) : 0;
        int bankPct = totalCollections > 0 ? (int) Math.round((bank / totalCollections) * 100) : 0;

        addBarRow("Cash Payments", formatCurrency(cash), cashPct, cashPct + "% of total collections");
        addBarRow("Cheque Payments", formatCurrency(cheque), chequePct, chequePct + "% of total collections");
        addBarRow("Bank Transfers", formatCurrency(bank), bankPct, bankPct + "% of total collections");
    }

    private void renderCategoriesInsight(JSONObject data) {
        JSONArray categories = data.optJSONArray("top_categories");
        double netSales = data.optDouble("net_sales", 0);

        txtSummaryHeader.setText("TOTAL REVENUE ACROSS CATEGORIES");
        txtSummaryMain.setText(formatCurrency(netSales));
        txtSummarySub.setText("Breakdown by product category");

        if (categories == null || categories.length() == 0) {
            addEmptyRow("No category sales recorded for this period.");
            return;
        }

        double maxCat = 0;
        for (int i = 0; i < categories.length(); i++) {
            JSONObject cat = categories.optJSONObject(i);
            if (cat != null) {
                double val = cat.optDouble("total_sales", cat.optDouble("total", 0));
                if (val > maxCat) maxCat = val;
            }
        }
        if (maxCat == 0) maxCat = 1.0;

        for (int i = 0; i < categories.length(); i++) {
            JSONObject cat = categories.optJSONObject(i);
            if (cat == null) continue;
            String name = cat.optString("category_name", "Uncategorized");
            double val = cat.optDouble("total_sales", cat.optDouble("total", 0));
            int qty = cat.optInt("qty", cat.optInt("invoice_count", 0));

            int pct = (int) Math.min(100, Math.round((val / maxCat) * 100));
            addBarRow(name, formatCurrency(val), pct, (qty > 0 ? qty + " units sold" : "Category Sales"));
        }
    }

    private void renderRoutesInsight(JSONObject data) {
        JSONArray routes = data.optJSONArray("routes_detail");
        int totalRoutes = data.optInt("total_routes", 0);
        int completedRoutes = data.optInt("completed_routes", 0);

        txtSummaryHeader.setText("ROUTE COVERAGE & SALES");
        txtSummaryMain.setText(completedRoutes + " / " + totalRoutes + " Routes Completed");
        txtSummarySub.setText("Comparative sales across assigned territories");

        if (routes == null || routes.length() == 0) {
            addEmptyRow("No route activity recorded for this period.");
            return;
        }

        double maxRouteSale = 0;
        for (int i = 0; i < routes.length(); i++) {
            JSONObject r = routes.optJSONObject(i);
            if (r != null) {
                double val = r.optDouble("sales", 0);
                if (val > maxRouteSale) maxRouteSale = val;
            }
        }
        if (maxRouteSale == 0) maxRouteSale = 1.0;

        for (int i = 0; i < routes.length(); i++) {
            JSONObject r = routes.optJSONObject(i);
            if (r == null) continue;
            String routeName = r.optString("route_name", "Direct Sale");
            double sales = r.optDouble("sales", 0);
            int invoices = r.optInt("invoices", 0);
            int prodVisits = r.optInt("productive_visits", 0);

            int pct = (int) Math.min(100, Math.round((sales / maxRouteSale) * 100));
            addBarRow(routeName, formatCurrency(sales), pct, invoices + " invoices | " + prodVisits + " productive visits");
        }
    }

    private void renderTopCustomersAndProducts(JSONObject data) {
        JSONArray topCust = data.optJSONArray("top_customers");
        JSONArray topProd = data.optJSONArray("top_products");

        txtSummaryHeader.setText("LEADERBOARDS & TOP SALES");
        txtSummaryMain.setText("Top Buyers & SKUs");
        txtSummarySub.setText("Ranked performance for " + periodLabel);

        addSectionHeader("👑 TOP BUYING CUSTOMERS");
        if (topCust != null && topCust.length() > 0) {
            for (int i = 0; i < topCust.length(); i++) {
                JSONObject c = topCust.optJSONObject(i);
                if (c == null) continue;
                String name = c.optString("customer_name", "Customer");
                double val = c.optDouble("total_sales", c.optDouble("total", 0));
                int inv = c.optInt("bills", c.optInt("invoices", 0));
                addRankRow("#" + (i + 1), name, inv + " Invoices Issued", formatCurrency(val));
            }
        } else {
            addEmptyRow("No customer rankings available.");
        }

        addSectionHeader("📦 TOP SELLING PRODUCTS");
        if (topProd != null && topProd.length() > 0) {
            for (int i = 0; i < topProd.length(); i++) {
                JSONObject p = topProd.optJSONObject(i);
                if (p == null) continue;
                String name = p.optString("product_name", "Product");
                double val = p.optDouble("total_sales", p.optDouble("total", 0));
                int qty = p.optInt("qty", 0);
                addRankRow("#" + (i + 1), name, qty + " Units Sold", formatCurrency(val));
            }
        } else {
            addEmptyRow("No product sales rankings available.");
        }
    }

    private void addBarRow(String label, String value, int percent, String subtext) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_insight_bar_row, layoutDetailContainer, false);
        TextView txtBarLabel = row.findViewById(R.id.txtBarLabel);
        TextView txtBarValue = row.findViewById(R.id.txtBarValue);
        TextView txtBarSubtext = row.findViewById(R.id.txtBarSubtext);
        ProgressBar progressBar = row.findViewById(R.id.progressBar);

        txtBarLabel.setText(label);
        txtBarValue.setText(value);
        txtBarSubtext.setText(subtext);
        progressBar.setProgress(percent);

        layoutDetailContainer.addView(row);
    }

    private void addRankRow(String rankNumber, String title, String subtitle, String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_insight_rank_row, layoutDetailContainer, false);
        TextView txtRankNumber = row.findViewById(R.id.txtRankNumber);
        TextView txtRankTitle = row.findViewById(R.id.txtRankTitle);
        TextView txtRankSubtitle = row.findViewById(R.id.txtRankSubtitle);
        TextView txtRankValue = row.findViewById(R.id.txtRankValue);

        txtRankNumber.setText(rankNumber);
        txtRankTitle.setText(title);
        txtRankSubtitle.setText(subtitle);
        txtRankValue.setText(value);

        layoutDetailContainer.addView(row);
    }

    private void addSectionHeader(String headerText) {
        TextView txt = new TextView(this);
        txt.setText(headerText);
        txt.setTextColor(0xFF94A3B8);
        txt.setTextSize(12);
        txt.setTypeface(null, android.graphics.Typeface.BOLD);
        txt.setPadding(8, 24, 8, 8);
        layoutDetailContainer.addView(txt);
    }

    private void addEmptyRow(String emptyMessage) {
        TextView txt = new TextView(this);
        txt.setText(emptyMessage);
        txt.setTextColor(0xFF94A3B8);
        txt.setTextSize(13);
        txt.setPadding(16, 24, 16, 24);
        txt.setGravity(android.view.Gravity.CENTER);
        layoutDetailContainer.addView(txt);
    }

    private String formatCurrency(double val) {
        return String.format(Locale.US, "LKR %,.2f", val);
    }
}
