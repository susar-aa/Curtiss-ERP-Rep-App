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
        prefs = SecurePreferences.getSessionPrefs(this);
        mainHandler = new Handler(Looper.getMainLooper());

        // Redirect directly if session is already active
        if (prefs.contains("user_id")) {
            navigateToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        dbHelper = DatabaseHelper.getInstance(this);

        edtUsername = findViewById(R.id.edtUsername);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        txtSyncDetails = findViewById(R.id.txtSyncDetails);
        
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            txtSyncDetails.setText("Secure connection is encrypted using standard SSL/TLS.\nApp Version: v" + versionName);
        } catch (Exception e) {
            txtSyncDetails.setText("Secure connection is encrypted using standard SSL/TLS.");
        }

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptAuthentication();
            }
        });

        txtSyncDetails.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showBaseUrlDialog();
                return true;
            }
        });
    }

    private void setViewsEnabled(final boolean enabled) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                edtUsername.setEnabled(enabled);
                edtPassword.setEnabled(enabled);
                btnLogin.setEnabled(enabled);
                if (enabled) {
                    btnLogin.setText("AUTHENTICATE SECURELY");
                } else {
                    btnLogin.setText("AUTHENTICATING...");
                }
            }
        });
    }

    private void attemptAuthentication() {
        final String username = edtUsername.getText().toString().trim();
        final String password = edtPassword.getText().toString().trim();

        // 1. Input Validation
        boolean hasError = false;
        if (username.isEmpty()) {
            edtUsername.setError("Username cannot be empty");
            edtUsername.requestFocus();
            hasError = true;
        } else if (username.length() < 3) {
            edtUsername.setError("Username must be at least 3 characters");
            edtUsername.requestFocus();
            hasError = true;
        } else if (username.contains(" ")) {
            edtUsername.setError("Username cannot contain spaces");
            edtUsername.requestFocus();
            hasError = true;
        }

        if (password.isEmpty()) {
            edtPassword.setError("Password cannot be empty");
            if (!hasError) {
                edtPassword.requestFocus();
            }
            hasError = true;
        } else if (password.length() < 4) {
            edtPassword.setError("Password must be at least 4 characters");
            if (!hasError) {
                edtPassword.requestFocus();
            }
            hasError = true;
        }

        if (hasError) {
            return;
        }

        setViewsEnabled(false);
        txtSyncDetails.setText("Authenticating credentials in real-time...");

        // Run authentication in background thread to avoid blocking main UI
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean authenticated = false;
                JSONObject userObj = null;
                String errorMsg = "Unable to connect to server.";

                // 1. Try saved base_url first (if set), otherwise fall back to sequence
                if (isNetworkAvailable()) {
                    String savedBaseUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com").trim();
                    if (savedBaseUrl.isEmpty()) {
                        savedBaseUrl = "https://curtiss.suzxlabs.com";
                    }
                    try {
                        userObj = performNetworkLogin(username, password, savedBaseUrl + "/rep/RepDashboard/api_login?api_sync=1");
                        if (userObj != null) {
                            authenticated = true;
                            prefs.edit().putString("base_url", savedBaseUrl).apply();
                        }
                    } catch (Exception e) {
                        android.util.Log.e("LoginActivity", "Saved base URL auth failed: " + e.getMessage());
                        errorMsg = e.getMessage();
                        
                        // Try production fallback direct "curtiss.suzxlabs.com"
                        if (!savedBaseUrl.equalsIgnoreCase("https://curtiss.suzxlabs.com") && 
                            !savedBaseUrl.equalsIgnoreCase("https://curtiss.suzxlabs.com/")) {
                            try {
                                userObj = performNetworkLogin(username, password, "https://curtiss.suzxlabs.com/rep/RepDashboard/api_login?api_sync=1");
                                if (userObj != null) {
                                    authenticated = true;
                                    prefs.edit().putString("base_url", "https://curtiss.suzxlabs.com").apply();
                                }
                            } catch (Exception ex3) {
                                android.util.Log.e("LoginActivity", "Production auth fallback failed: " + ex3.getMessage());
                                errorMsg = ex3.getMessage();
                            }
                        }
                    }
                }

                // 2. Offline Fallback: Authenticate locally via SQLite if server is unreachable
                if (!authenticated) {
                    android.util.Log.d("LoginActivity", "Server unreachable or offline. Falling back to local authentication.");
                    final boolean localSuccess = performLocalAuthentication(username, password);
                    final String finalError = errorMsg;
                    
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setViewsEnabled(true);
                            if (localSuccess) {
                                Toast.makeText(LoginActivity.this, "Offline Login Successful!", Toast.LENGTH_SHORT).show();
                                navigateToSplash();
                            } else {
                                txtSyncDetails.setText("Secure connection is encrypted using standard SSL/TLS.");
                                Toast.makeText(LoginActivity.this, "Login Failed: " + finalError, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    return;
                }

                // 3. Online Success: Cache session details and launch SplashActivity for background sync
                final JSONObject finalUserObj = userObj;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            int repUserId = finalUserObj.getJSONObject("user").getInt("id");
                            int employeeId = finalUserObj.getJSONObject("user").getInt("employee_id");
                            String firstName = finalUserObj.getJSONObject("user").getString("first_name");
                            String lastName = finalUserObj.getJSONObject("user").getString("last_name");
                            String token = finalUserObj.optString("token", "");

                            // Cache session locally in SharedPreferences
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putInt("user_id", repUserId);
                            editor.putString("username", username);
                            editor.putString("api_token", token);
                            editor.putInt("employee_id", employeeId);
                            editor.putString("first_name", firstName);
                            editor.putString("last_name", lastName);
                            editor.apply();

                            // Store password hash locally for offline login
                            try {
                                SQLiteDatabase db = dbHelper.getWritableDatabase();
                                db.execSQL("CREATE TABLE IF NOT EXISTS representatives (id INTEGER PRIMARY KEY, username TEXT UNIQUE, password_hash TEXT, employee_id INTEGER, first_name TEXT, last_name TEXT)");
                                String localHash = BCrypt.hashpw(password, BCrypt.gensalt());
                                android.content.ContentValues cv = new android.content.ContentValues();
                                cv.put("id", repUserId);
                                cv.put("username", username);
                                cv.put("password_hash", localHash);
                                cv.put("employee_id", employeeId);
                                cv.put("first_name", firstName);
                                cv.put("last_name", lastName);
                                db.insertWithOnConflict("representatives", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                            } catch (Exception dbEx) {
                                android.util.Log.e("LoginActivity", "Error caching password hash: " + dbEx.getMessage());
                            }

                            Toast.makeText(LoginActivity.this, "Welcome, " + firstName + " " + lastName + "!", Toast.LENGTH_LONG).show();
                            navigateToSplash();

                        } catch (Exception e) {
                            setViewsEnabled(true);
                            txtSyncDetails.setText("Session initialization error.");
                            Toast.makeText(LoginActivity.this, "Session error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void navigateToSplash() {
        Intent intent = new Intent(this, SplashActivity.class);
        startActivity(intent);
        finish();
    }

    private JSONObject performNetworkLogin(String username, String password, String endpoint) throws Exception {
        boolean isLocal = endpoint.contains("10.0.2.2") || endpoint.contains("192.168.");
        int maxRetries = isLocal ? 1 : 3;
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(isLocal ? 2500 : 8000);
                conn.setReadTimeout(isLocal ? 2500 : 8000);

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
                        throw new Exception("Plesk Server is offline (HTTP 503 Service Unavailable) or redirected.");
                    }

                    try {
                        JSONObject res = new JSONObject(responseText);
                        if (res.getBoolean("success")) {
                            return res;
                        } else {
                            throw new Exception(res.optString("message", "Invalid credentials."));
                        }
                    } catch (org.json.JSONException je) {
                        android.util.Log.e("LoginActivity", "JSON parsing failed for: " + responseText);
                        throw new Exception("Invalid server JSON response.");
                    }
                } else {
                    throw new Exception("HTTP Error: " + responseCode);
                }
            } catch (Exception e) {
                lastException = e;
                android.util.Log.e("LoginActivity", "Login network attempt " + attempt + " failed: " + e.getMessage());
                // If it is a credentials failure or business logic error, we should NOT retry
                if (e.getMessage() != null && (e.getMessage().contains("credentials") || e.getMessage().contains("Password") || e.getMessage().contains("username"))) {
                    throw e;
                }
                if (attempt >= maxRetries) {
                    throw e;
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ie;
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new Exception("Authentication request failed.");
    }

    private boolean performLocalAuthentication(String username, String password) {
        // Local developer bypass removed for production security
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

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void showBaseUrlDialog() {
        final EditText input = new EditText(this);
        String currentUrl = prefs.getString("base_url", "https://curtiss.suzxlabs.com");
        input.setText(currentUrl);
        input.setSelection(currentUrl.length());

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Configure Base URL")
            .setMessage("Enter the server base URL (e.g., http://192.168.1.6/Curtiss-ERP/public):")
            .setView(input)
            .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    String newUrl = input.getText().toString().trim();
                    if (!newUrl.isEmpty()) {
                        prefs.edit().putString("base_url", newUrl).apply();
                        Toast.makeText(LoginActivity.this, "Base URL updated: " + newUrl, Toast.LENGTH_LONG).show();
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
