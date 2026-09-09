package com.example.curtiss;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class LocationTrackingService extends Service {

    private static final String TAG = "LocationTrackingService";
    private static final String CHANNEL_ID = "LocationTrackingChannel";
    private static final int NOTIFICATION_ID = 999;
    
    // Configurable/overrideable Firebase Realtime Database URL
    public static final String DEFAULT_DATABASE_URL = "https://curtiss-erp-cc0c0-default-rtdb.firebaseio.com/";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseReference dbRef;
    private int userId;
    private String username;
    private String fullName;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Location tracking service started");
        
        // Load representative session info
        SharedPreferences prefs = SecurePreferences.getSessionPrefs(this);
        userId = prefs.getInt("user_id", 0);
        username = prefs.getString("username", "unknown");
        String firstName = prefs.getString("first_name", "");
        String lastName = prefs.getString("last_name", "");
        fullName = (firstName + " " + lastName).trim();

        if (userId <= 0) {
            Log.e(TAG, "User session not active, stopping service.");
            stopSelf();
            return;
        }

        // Initialize Firebase programmatically without google-services.json
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseOptions options = new FirebaseOptions.Builder()
                        .setApiKey("AIzaSyFakePlaceholderKey1234567890ABCDEF")
                        .setApplicationId(getPackageName())
                        .setProjectId("curtiss-erp-cc0c0")
                        .setDatabaseUrl(DEFAULT_DATABASE_URL)
                        .build();
                FirebaseApp.initializeApp(this, options);
                Log.d(TAG, "Firebase initialized programmatically with URL: " + DEFAULT_DATABASE_URL);
            }
            dbRef = FirebaseDatabase.getInstance().getReference("locations").child("rep_" + userId);
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization failed: " + e.getMessage());
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification("Acquiring GPS location..."));

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    updateLocationInFirebase(location);
                }
            }
        };

        requestLocationUpdates();
    }

    private void requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "ACCESS_FINE_LOCATION permission not granted");
            stopSelf();
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000) // Update every 30s
                .setMinUpdateIntervalMillis(15000) // minimum interval 15s
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            Log.d(TAG, "Location updates requested successfully");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException requesting location updates: " + e.getMessage());
        }
    }

    private void updateLocationInFirebase(Location location) {
        if (dbRef == null) {
            Log.w(TAG, "Firebase DatabaseReference is null, skipping update");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("latitude", location.getLatitude());
        data.put("longitude", location.getLongitude());
        data.put("timestamp", System.currentTimeMillis());
        data.put("username", username);
        data.put("full_name", fullName);
        data.put("status", "Active Route");

        dbRef.setValue(data)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Location pushed to Firebase: " + location.getLatitude() + ", " + location.getLongitude()))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to push location to Firebase: " + e.getMessage()));

        // Update notification description
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            String txt = "Latitude: " + String.format("%.4f", location.getLatitude()) + ", Longitude: " + String.format("%.4f", location.getLongitude());
            manager.notify(NOTIFICATION_ID, getNotification(txt));
        }
    }

    private Notification getNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Curtiss Live Location Service")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Curtiss Location Tracking Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Stopping location tracking service");
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        
        // Mark representative offline in Firebase on service exit
        if (dbRef != null) {
            Map<String, Object> offlineData = new HashMap<>();
            offlineData.put("status", "Offline");
            offlineData.put("timestamp", System.currentTimeMillis());
            dbRef.updateChildren(offlineData);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Helper: Start this service safely
    public static void startService(Context context) {
        Intent intent = new Intent(context, LocationTrackingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    // Helper: Stop this service safely
    public static void stopService(Context context) {
        Intent intent = new Intent(context, LocationTrackingService.class);
        context.stopService(intent);
    }
}
