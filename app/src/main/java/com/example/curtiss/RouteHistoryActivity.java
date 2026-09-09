package com.example.curtiss;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class RouteHistoryActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private ListView lstRoutes;
    private EditText edtRouteSearch;
    private Button btnStartDate, btnEndDate, btnClearFilters, btnPrevPage, btnNextPage, btnBack;
    private TextView txtPageStatus, txtOfflineBanner;
    private RelativeLayout layoutLoadingOverlay;

    private SharedPreferences prefs;
    private int userId;
    private String baseUrl;

    private ArrayList<JSONObject> routesList = new ArrayList<>();
    private RouteAdapter adapter;

    // Pagination & Filters State
    private int currentPage = 1;
    private int totalPages = 1;
    private final int limit = 10;
    private String searchQuery = "";
    private String startDate = "";
    private String endDate = "";

    private Calendar startCalendar = Calendar.getInstance();
    private Calendar endCalendar = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_history);

        prefs = SecurePreferences.getSessionPrefs(this);
        userId = prefs.getInt("user_id", 0);
        baseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");

        initViews();
        setupListeners();

        // Initial load
        checkConnectionAndLoad(true);
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        txtOfflineBanner = findViewById(R.id.txtOfflineBanner);
        edtRouteSearch = findViewById(R.id.edtRouteSearch);
        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate = findViewById(R.id.btnEndDate);
        btnClearFilters = findViewById(R.id.btnClearFilters);
        swipeRefresh = findViewById(R.id.swipeRefreshRouteHistory);
        lstRoutes = findViewById(R.id.lstRoutes);
        btnPrevPage = findViewById(R.id.btnPrevPage);
        btnNextPage = findViewById(R.id.btnNextPage);
        txtPageStatus = findViewById(R.id.txtPageStatus);
        layoutLoadingOverlay = findViewById(R.id.layoutLoadingOverlay);

        adapter = new RouteAdapter();
        lstRoutes.setAdapter(adapter);

        updatePaginationUi();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                checkConnectionAndLoad(false);
            }
        });

        // Search edit text with debounced listener
        edtRouteSearch.addTextChangedListener(new TextWatcher() {
            private android.os.Handler handler = new android.os.Handler();
            private Runnable runnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(final Editable s) {
                if (runnable != null) {
                    handler.removeCallbacks(runnable);
                }
                runnable = new Runnable() {
                    @Override
                    public void run() {
                        searchQuery = s.toString().trim();
                        currentPage = 1;
                        checkConnectionAndLoad(true);
                    }
                };
                handler.postDelayed(runnable, 500); // 500ms debounce
            }
        });

        // Date Pickers
        btnStartDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker(true);
            }
        });

        btnEndDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker(false);
            }
        });

        btnClearFilters.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDate = "";
                endDate = "";
                btnStartDate.setText("Start Date");
                btnEndDate.setText("End Date");
                btnClearFilters.setVisibility(View.GONE);
                currentPage = 1;
                checkConnectionAndLoad(true);
            }
        });

        // Pagination buttons
        btnPrevPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPage > 1) {
                    currentPage--;
                    checkConnectionAndLoad(true);
                }
            }
        });

        btnNextPage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPage < totalPages) {
                    currentPage++;
                    checkConnectionAndLoad(true);
                }
            }
        });

        // List item click
        lstRoutes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isNetworkAvailable()) {
                    showOfflineWarning();
                    return;
                }
                JSONObject route = routesList.get(position);
                int routeId = route.optInt("id", 0);
                if (routeId > 0) {
                    Intent intent = new Intent(RouteHistoryActivity.this, RouteDetailActivity.class);
                    intent.putExtra("route_id", routeId);
                    startActivity(intent);
                }
            }
        });
    }

    private void showDatePicker(final boolean isStart) {
        Calendar cal = isStart ? startCalendar : endCalendar;
        DatePickerDialog dialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String formatted = sdf.format(selected.getTime());
                
                if (isStart) {
                    startCalendar = selected;
                    startDate = formatted;
                    btnStartDate.setText("From: " + formatted);
                } else {
                    endCalendar = selected;
                    endDate = formatted;
                    btnEndDate.setText("To: " + formatted);
                }
                btnClearFilters.setVisibility(View.VISIBLE);
                currentPage = 1;
                checkConnectionAndLoad(true);
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.show();
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
        Toast.makeText(this, "Internet connection required to view Route History.", Toast.LENGTH_LONG).show();
    }

    private void checkConnectionAndLoad(boolean showOverlay) {
        if (!isNetworkAvailable()) {
            showOfflineWarning();
            swipeRefresh.setRefreshing(false);
            return;
        }

        txtOfflineBanner.setVisibility(View.GONE);
        new FetchHistoryTask(showOverlay).execute();
    }

    private void updatePaginationUi() {
        txtPageStatus.setText("Page " + currentPage + " of " + totalPages);
        btnPrevPage.setEnabled(currentPage > 1);
        btnNextPage.setEnabled(currentPage < totalPages);

        if (currentPage <= 1) {
            btnPrevPage.setAlpha(0.5f);
        } else {
            btnPrevPage.setAlpha(1.0f);
        }

        if (currentPage >= totalPages) {
            btnNextPage.setAlpha(0.5f);
        } else {
            btnNextPage.setAlpha(1.0f);
        }
    }

    // Task to fetch route history from server
    private class FetchHistoryTask extends AsyncTask<Void, Void, String> {
        private boolean showOverlay;

        public FetchHistoryTask(boolean showOverlay) {
            this.showOverlay = showOverlay;
        }

        @Override
        protected void onPreExecute() {
            if (showOverlay) {
                layoutLoadingOverlay.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected String doInBackground(Void... voids) {
            HttpURLConnection conn = null;
            try {
                StringBuilder urlBuilder = new StringBuilder(baseUrl);
                urlBuilder.append("/rep/RepDashboard/api_get_route_history");
                urlBuilder.append("?user_id=").append(userId);
                urlBuilder.append("&page=").append(currentPage);
                urlBuilder.append("&limit=").append(limit);
                urlBuilder.append("&search=").append(URLEncoder.encode(searchQuery, "UTF-8"));
                urlBuilder.append("&start_date=").append(URLEncoder.encode(startDate, "UTF-8"));
                urlBuilder.append("&end_date=").append(URLEncoder.encode(endDate, "UTF-8"));

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
            layoutLoadingOverlay.setVisibility(View.GONE);
            swipeRefresh.setRefreshing(false);

            if (result == null) {
                Toast.makeText(RouteHistoryActivity.this, "Empty response from server", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                JSONObject response = new JSONObject(result);
                if (response.optBoolean("success", false)) {
                    routesList.clear();
                    JSONArray data = response.optJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            routesList.add(data.getJSONObject(i));
                        }
                    }

                    JSONObject pagination = response.optJSONObject("pagination");
                    if (pagination != null) {
                        currentPage = pagination.optInt("page", 1);
                        totalPages = pagination.optInt("total_pages", 1);
                        if (totalPages <= 0) totalPages = 1;
                    }

                    adapter.notifyDataSetChanged();
                    updatePaginationUi();

                    if (routesList.isEmpty()) {
                        Toast.makeText(RouteHistoryActivity.this, "No route history records found.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    String msg = response.optString("message", "Error fetching data");
                    Toast.makeText(RouteHistoryActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                android.util.Log.e("RouteHistoryActivity", "JSON Parsing error", e);
                Toast.makeText(RouteHistoryActivity.this, "Failed to parse server data", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // List Adapter
    private class RouteAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return routesList.size();
        }

        @Override
        public Object getItem(int position) {
            return routesList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(RouteHistoryActivity.this).inflate(R.layout.item_route_history_row, parent, false);
            }

            JSONObject route = routesList.get(position);

            TextView txtRowRouteName = convertView.findViewById(R.id.txtRowRouteName);
            TextView txtRowRouteStatus = convertView.findViewById(R.id.txtRowRouteStatus);
            TextView txtRowRouteDate = convertView.findViewById(R.id.txtRowRouteDate);
            TextView txtRowBillsCount = convertView.findViewById(R.id.txtRowBillsCount);
            TextView txtRowSalesTotal = convertView.findViewById(R.id.txtRowSalesTotal);

            // Set Title/Name
            String name = route.optString("route_name", "Unknown Route");
            int id = route.optInt("id", 0);
            txtRowRouteName.setText("#RT-" + id + ": " + name);

            // Set Status Badge
            String status = route.optString("status", "Active");
            txtRowRouteStatus.setText(status.toUpperCase());
            if (status.equalsIgnoreCase("Active") || status.equalsIgnoreCase("Loading") || status.equalsIgnoreCase("Syncing")) {
                txtRowRouteStatus.setBackgroundColor(0xFF3B82F6); // Blue
            } else if (status.equalsIgnoreCase("Ended")) {
                txtRowRouteStatus.setBackgroundColor(0xFFF59E0B); // Orange
            } else {
                txtRowRouteStatus.setBackgroundColor(0xFF10B981); // Green (Completed/Finalized)
            }

            // Set Date
            String start = route.optString("start_time", "");
            String end = route.optString("end_time", "");
            if (!end.isEmpty() && !end.equals("null")) {
                txtRowRouteDate.setText(start + "  to  " + end);
            } else {
                txtRowRouteDate.setText("Started: " + start);
            }

            // Set bills & sales count
            int billsCount = route.optInt("total_bills", 0);
            double salesTotal = route.optDouble("total_sales", 0.0);

            txtRowBillsCount.setText(billsCount + " Invoices");
            txtRowSalesTotal.setText(String.format(Locale.getDefault(), "LKR %,.2f", salesTotal));

            return convertView;
        }
    }
}
