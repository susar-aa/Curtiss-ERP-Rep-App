package com.example.curtiss;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncLogsActivity extends AppCompatActivity {

    private TextView txtStatTotal, txtStatSynced, txtStatFailed, txtStatPending;
    private EditText edtSearchLogs;
    private ListView lstLogs;
    private Button btnBack, btnForceSync;

    private DatabaseHelper dbHelper;
    private List<Map<String, Object>> allLogs = new ArrayList<>();
    private List<Map<String, Object>> filteredLogs = new ArrayList<>();
    private LogAdapter adapter;
    private int representativeUserId = 12;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_logs);

        dbHelper = DatabaseHelper.getInstance(this);
        representativeUserId = getSharedPreferences("rep_session", MODE_PRIVATE).getInt("user_id", 12);

        // Bind views
        txtStatTotal = findViewById(R.id.txtStatTotal);
        txtStatSynced = findViewById(R.id.txtStatSynced);
        txtStatFailed = findViewById(R.id.txtStatFailed);
        txtStatPending = findViewById(R.id.txtStatPending);
        edtSearchLogs = findViewById(R.id.edtSearchLogs);
        lstLogs = findViewById(R.id.lstLogs);
        btnBack = findViewById(R.id.btnBack);
        btnForceSync = findViewById(R.id.btnForceSync);
        btnForceSync.setVisibility(View.GONE);

        adapter = new LogAdapter();
        lstLogs.setAdapter(adapter);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        edtSearchLogs.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterLogs(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        // 1. Load Stats
        int total = 0;
        int synced = 0;
        int failed = 0;
        int pending = 0;

        Cursor cStat = db.rawQuery("SELECT " +
                "(SELECT COUNT(*) FROM sync_logs), " +
                "(SELECT COUNT(*) FROM invoices WHERE sync_status = 3), " +
                "(SELECT COUNT(*) FROM invoices WHERE sync_status = 4), " +
                "(SELECT COUNT(*) FROM invoices WHERE sync_status IN (1, 2))", null);
        if (cStat.moveToFirst()) {
            total = cStat.getInt(0);
            synced = cStat.getInt(1);
            failed = cStat.getInt(2);
            pending = cStat.getInt(3);
        }
        cStat.close();

        txtStatTotal.setText(String.valueOf(total));
        txtStatSynced.setText(String.valueOf(synced));
        txtStatFailed.setText(String.valueOf(failed));
        txtStatPending.setText(String.valueOf(pending));

        // 2. Load Logs List
        allLogs.clear();
        Cursor cursor = db.rawQuery("SELECT l.*, i.sync_status AS current_status, i.invoice_number " +
                "FROM sync_logs l LEFT JOIN invoices i ON l.bill_id = i.id ORDER BY l.id DESC", null);
        while (cursor.moveToNext()) {
            Map<String, Object> log = new HashMap<>();
            log.put("id", cursor.getInt(cursor.getColumnIndexOrThrow("id")));
            log.put("bill_id", cursor.getInt(cursor.getColumnIndexOrThrow("bill_id")));
            log.put("uuid", cursor.getString(cursor.getColumnIndexOrThrow("uuid")));
            log.put("created_time", cursor.getString(cursor.getColumnIndexOrThrow("created_time")));
            log.put("upload_started", cursor.getString(cursor.getColumnIndexOrThrow("upload_started")));
            log.put("upload_completed", cursor.getString(cursor.getColumnIndexOrThrow("upload_completed")));
            log.put("erp_response", cursor.getString(cursor.getColumnIndexOrThrow("erp_response")));
            log.put("failure_reason", cursor.getString(cursor.getColumnIndexOrThrow("failure_reason")));
            log.put("retry_count", cursor.getInt(cursor.getColumnIndexOrThrow("retry_count")));
            
            int status = cursor.isNull(cursor.getColumnIndexOrThrow("current_status")) ? 1 : cursor.getInt(cursor.getColumnIndexOrThrow("current_status"));
            log.put("status", status);

            String invNum = cursor.getString(cursor.getColumnIndexOrThrow("invoice_number"));
            log.put("invoice_number", invNum != null ? invNum : "N/A");

            allLogs.add(log);
        }
        cursor.close();

        filterLogs(edtSearchLogs.getText().toString());
    }

    private void filterLogs(String query) {
        filteredLogs.clear();
        String term = query.toLowerCase().trim();
        for (Map<String, Object> log : allLogs) {
            String uuid = String.valueOf(log.get("uuid")).toLowerCase();
            String billId = String.valueOf(log.get("bill_id"));
            String invNum = String.valueOf(log.get("invoice_number")).toLowerCase();
            if (term.isEmpty() || uuid.contains(term) || billId.contains(term) || invNum.contains(term)) {
                filteredLogs.add(log);
            }
        }
        adapter.notifyDataSetChanged();
    }

    // Manual sync UI triggers decommissioned.

    private class LogAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return filteredLogs.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredLogs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(SyncLogsActivity.this).inflate(R.layout.item_sync_log, parent, false);
            }

            final Map<String, Object> log = filteredLogs.get(position);

            TextView txtLogBillId = convertView.findViewById(R.id.txtLogBillId);
            TextView txtLogStatus = convertView.findViewById(R.id.txtLogStatus);
            TextView txtLogUuid = convertView.findViewById(R.id.txtLogUuid);
            TextView txtLogCreated = convertView.findViewById(R.id.txtLogCreated);
            TextView txtLogStarted = convertView.findViewById(R.id.txtLogStarted);
            TextView txtLogCompleted = convertView.findViewById(R.id.txtLogCompleted);
            TextView txtLogRetries = convertView.findViewById(R.id.txtLogRetries);
            TextView txtLogFailureReason = convertView.findViewById(R.id.txtLogFailureReason);
            Button btnLogRetry = convertView.findViewById(R.id.btnLogRetry);

            // Populate text fields
            final int billId = (int) log.get("bill_id");
            String invNum = String.valueOf(log.get("invoice_number"));
            txtLogBillId.setText("BILL ID: #" + billId + " (" + invNum + ")");
            txtLogUuid.setText("UUID: " + log.get("uuid"));
            txtLogCreated.setText("Created: " + log.get("created_time"));
            
            String started = String.valueOf(log.get("upload_started"));
            txtLogStarted.setText("Started: " + (started.isEmpty() ? "Not Started" : started));
            
            String completed = String.valueOf(log.get("upload_completed"));
            txtLogCompleted.setText("Completed: " + (completed.isEmpty() ? "Incomplete" : completed));
            
            txtLogRetries.setText("Retries: " + log.get("retry_count"));

            // Status style and logic
            int status = (int) log.get("status");
            if (status == 3) {
                txtLogStatus.setText("Synced");
                txtLogStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF10B981)); // Emerald
                txtLogFailureReason.setVisibility(View.GONE);
                btnLogRetry.setVisibility(View.GONE);
            } else if (status == 2) {
                txtLogStatus.setText("Syncing");
                txtLogStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF06B6D4)); // Cyan
                txtLogFailureReason.setVisibility(View.GONE);
                btnLogRetry.setVisibility(View.GONE);
            } else if (status == 4) {
                txtLogStatus.setText("Failed");
                txtLogStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444)); // Red
                String reason = String.valueOf(log.get("failure_reason"));
                txtLogFailureReason.setText("Reason: " + (reason.isEmpty() ? "Unknown server rejection" : reason));
                txtLogFailureReason.setVisibility(View.VISIBLE);
                btnLogRetry.setVisibility(View.VISIBLE);
                btnLogRetry.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444));
            } else {
                txtLogStatus.setText("Pending");
                txtLogStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF59E0B)); // Orange
                txtLogFailureReason.setVisibility(View.GONE);
                btnLogRetry.setVisibility(View.VISIBLE);
                btnLogRetry.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF59E0B));
            }

            btnLogRetry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    db.beginTransaction();
                    try {
                        ContentValues cvInv = new ContentValues();
                        cvInv.put("sync_status", 1);
                        cvInv.put("sync_attempts", 0);
                        cvInv.put("failure_reason", "");
                        db.update("invoices", cvInv, "id = ?", new String[]{String.valueOf(billId)});

                        db.execSQL("UPDATE sync_logs SET failure_reason = '', erp_response = 'Retrying...' WHERE bill_id = ?", new Object[]{billId});
                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }
                    loadData();

                    // Trigger background push sync automatically in the background
                    SyncManager.getInstance(SyncLogsActivity.this).startManualPushSync(SyncLogsActivity.this, representativeUserId, new SyncManager.SyncListener() {
                        @Override public void onSyncStarted() {}
                        @Override public void onSyncProgress(String message) {}
                        @Override public void onSyncCompleted(boolean success, String message) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    loadData();
                                }
                            });
                        }
                    });
                }
            });

            return convertView;
        }
    }
}
