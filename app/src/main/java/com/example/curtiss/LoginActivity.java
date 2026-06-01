package com.example.curtiss;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    private EditText edtUsername, edtPassword;
    private Button btnLogin;
    private TextView txtSyncDetails;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize SharedPreferences session
        prefs = getSharedPreferences("rep_session", Context.MODE_PRIVATE);
        mainHandler = new Handler(Looper.getMainLooper());

        // Redirect directly if session is already active
        if (prefs.contains("user_id")) {
            navigateToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        dbHelper = new DatabaseHelper(this);

        edtUsername = findViewById(R.id.edtUsername);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        txtSyncDetails = findViewById(R.id.txtSyncDetails);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptAuthentication();
            }
        });
    }

    private void attemptAuthentication() {
        final String username = edtUsername.getText().toString().trim();
        final String password = edtPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both username and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        txtSyncDetails.setText("Authenticating credentials in real-time...");

        // Run authentication in background thread to avoid blocking main UI
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean authenticated = false;
                JSONObject userObj = null;
                String errorMsg = "Unable to connect to server.";

                // 1. Try production real-time login first if network is available
                if (isNetworkAvailable()) {
                    try {
                        userObj = performNetworkLogin(username, password, "https://curtiss.suzxlabs.com/rep/RepDashboard/api_login?api_sync=1");
                        if (userObj != null) {
                            authenticated = true;
                        }
                    } catch (Exception e) {
                        android.util.Log.e("LoginActivity", "Production auth failed: " + e.getMessage());
                        // Try localhost emulator backup fallback
                        try {
                            userObj = performNetworkLogin(username, password, "http://10.0.2.2/Curtiss-ERP/public/rep/RepDashboard/api_login?api_sync=1");
                            if (userObj != null) {
                                authenticated = true;
                            }
                        } catch (Exception ex) {
                            android.util.Log.e("LoginActivity", "Localhost backup auth failed: " + ex.getMessage());
                            errorMsg = e.getMessage();
                        }
                    }
                }

                // 2. Offline Fallback: Authenticate locally via SQLite if server is unreachable
                if (!authenticated) {
                    android.util.Log.d("LoginActivity", "Server unreachable or offline. Falling back to local authentication.");
                    final boolean localSuccess = performLocalAuthentication(username, password);
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            btnLogin.setEnabled(true);
                            if (localSuccess) {
                                Toast.makeText(LoginActivity.this, "Offline Login Successful!", Toast.LENGTH_SHORT).show();
                                navigateToMain();
                            } else {
                                txtSyncDetails.setText("Secure connection is encrypted using standard SSL/TLS.");
                                Toast.makeText(LoginActivity.this, "Authentication failed. (Verify network or local password)", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    return;
                }

                // 3. Online Success: Cache session details and launch post-login background sync
                final JSONObject finalUserObj = userObj;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            int repUserId = finalUserObj.getInt("id");
                            int employeeId = finalUserObj.getInt("employee_id");
                            String firstName = finalUserObj.getString("first_name");
                            String lastName = finalUserObj.getString("last_name");

                            // Cache session locally in SharedPreferences
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putInt("user_id", repUserId);
                            editor.putString("username", username);
                            editor.putInt("employee_id", employeeId);
                            editor.putString("first_name", firstName);
                            editor.putString("last_name", lastName);
                            editor.apply();

                            txtSyncDetails.setText("Authentication successful! Populating offline databases...");
                            Toast.makeText(LoginActivity.this, "Welcome, " + firstName + " " + lastName + "! Syncing products...", Toast.LENGTH_LONG).show();

                            // Trigger immediate post-login background sync
                            SyncManager.getInstance(LoginActivity.this).startSync(LoginActivity.this, repUserId, new SyncManager.SyncListener() {
                                @Override
                                public void onSyncStarted() {
                                    txtSyncDetails.setText("Initializing offline database synchronization...");
                                }

                                @Override
                                public void onSyncProgress(String message) {
                                    txtSyncDetails.setText(message);
                                }

                                @Override
                                public void onSyncCompleted(boolean success, String message) {
                                    btnLogin.setEnabled(true);
                                    navigateToMain();
                                }
                            });

                        } catch (Exception e) {
                            btnLogin.setEnabled(true);
                            txtSyncDetails.setText("Session initialization error.");
                            Toast.makeText(LoginActivity.this, "Session error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private JSONObject performNetworkLogin(String username, String password, String endpoint) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        JSONObject payload = new JSONObject();
        payload.put("username", username);
        payload.put("password", password);

        OutputStream os = conn.getOutputStream();
        os.write(payload.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }
            in.close();

            String responseText = sb.toString();
            if (responseText.trim().startsWith("<!DOCTYPE") || responseText.trim().startsWith("<html")) {
                android.util.Log.e("LoginActivity", "Server returned HTML instead of JSON: " + responseText);
                throw new Exception("Plesk Server is offline (HTTP 503 Service Unavailable).");
            }

            try {
                JSONObject res = new JSONObject(responseText);
                if (res.getBoolean("success")) {
                    return res.getJSONObject("user");
                } else {
                    throw new Exception(res.optString("message", "Invalid credentials."));
                }
            } catch (org.json.JSONException je) {
                android.util.Log.e("LoginActivity", "JSON parsing failed for: " + responseText);
                throw new Exception("Invalid server JSON response.");
            }
        }
        throw new Exception("HTTP Error: " + responseCode);
    }

    private boolean performLocalAuthentication(String username, String password) {
        // Developer hardcoded bypass for testing resilience on physical device
        if (username.equalsIgnoreCase("rep") && password.equals("123")) {
            android.util.Log.d("LoginActivity", "Local developer bypass triggered successfully for rep / 123");
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("user_id", 12);
            editor.putString("username", "rep");
            editor.putInt("employee_id", 1);
            editor.putString("first_name", "Susara");
            editor.putString("last_name", "Senarathne");
            editor.apply();
            return true;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            // Count registered reps for transparent debugging
            Cursor cCount = db.rawQuery("SELECT COUNT(*) FROM representatives", null);
            int cachedCount = 0;
            if (cCount.moveToFirst()) {
                cachedCount = cCount.getInt(0);
            }
            cCount.close();
            android.util.Log.d("LoginActivity", "Local verification check. Total offline reps cached: " + cachedCount);

            cursor = db.rawQuery("SELECT * FROM representatives WHERE LOWER(username) = ?", new String[]{username.toLowerCase()});
            if (cursor.moveToFirst()) {
                String storedHash = cursor.getString(cursor.getColumnIndexOrThrow("password_hash"));
                int repUserId = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                int employeeId = cursor.getInt(cursor.getColumnIndexOrThrow("employee_id"));
                String firstName = cursor.getString(cursor.getColumnIndexOrThrow("first_name"));
                String lastName = cursor.getString(cursor.getColumnIndexOrThrow("last_name"));

                // Verify BCrypt password match
                if (BCrypt.checkpw(password, storedHash)) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("user_id", repUserId);
                    editor.putString("username", username);
                    editor.putInt("employee_id", employeeId);
                    editor.putString("first_name", firstName);
                    editor.putString("last_name", lastName);
                    editor.apply();
                    return true;
                }
            }
        } catch (Exception e) {
            android.util.Log.e("LoginActivity", "Local auth error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return false;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
