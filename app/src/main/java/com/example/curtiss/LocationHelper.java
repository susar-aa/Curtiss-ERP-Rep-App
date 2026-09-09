package com.example.curtiss;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class LocationHelper {

    public interface LocationResultListener {
        void onLocationResult(double latitude, double longitude, boolean isFallback);
    }

    public static void captureCurrentLocation(Context context, final LocationResultListener listener) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            listener.onLocationResult(7.1824, 79.8801, true); // Default fallback
            return;
        }

        final boolean[] responded = {false};
        final android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (!responded[0]) {
                    responded[0] = true;
                    listener.onLocationResult(7.1824, 79.8801, true);
                }
            }
        };
        // 5 second timeout
        timeoutHandler.postDelayed(timeoutRunnable, 5000);

        try {
            final FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(Task<Location> task) {
                        Location loc = null;
                        if (task.isSuccessful() && task.getResult() != null) {
                            loc = task.getResult();
                        }
                        if (loc != null) {
                            if (!responded[0]) {
                                responded[0] = true;
                                timeoutHandler.removeCallbacks(timeoutRunnable);
                                listener.onLocationResult(loc.getLatitude(), loc.getLongitude(), false);
                            }
                        } else {
                            // Fallback to getLastLocation
                            try {
                                fusedLocationClient.getLastLocation()
                                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                                        @Override
                                        public void onComplete(Task<Location> lastTask) {
                                            if (responded[0]) return;
                                            
                                            Location lastLoc = null;
                                            if (lastTask.isSuccessful() && lastTask.getResult() != null) {
                                                lastLoc = lastTask.getResult();
                                            }
                                            if (lastLoc != null) {
                                                responded[0] = true;
                                                timeoutHandler.removeCallbacks(timeoutRunnable);
                                                listener.onLocationResult(lastLoc.getLatitude(), lastLoc.getLongitude(), false);
                                            } else {
                                                responded[0] = true;
                                                timeoutHandler.removeCallbacks(timeoutRunnable);
                                                listener.onLocationResult(7.1824, 79.8801, true); // Fallback standard
                                            }
                                        }
                                    });
                            } catch (SecurityException e) {
                                if (!responded[0]) {
                                    responded[0] = true;
                                    timeoutHandler.removeCallbacks(timeoutRunnable);
                                    listener.onLocationResult(7.1824, 79.8801, true);
                                }
                            }
                        }
                    }
                });
        } catch (SecurityException e) {
            if (!responded[0]) {
                responded[0] = true;
                timeoutHandler.removeCallbacks(timeoutRunnable);
                listener.onLocationResult(7.1824, 79.8801, true);
            }
        }
    }

    public static void captureCurrentLocationStrict(Context context, final LocationResultListener listener) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            listener.onLocationResult(0.0, 0.0, true);
            return;
        }

        final boolean[] responded = {false};
        final android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (!responded[0]) {
                    responded[0] = true;
                    listener.onLocationResult(0.0, 0.0, true);
                }
            }
        };
        // 5 second timeout
        timeoutHandler.postDelayed(timeoutRunnable, 5000);

        try {
            final FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(Task<Location> task) {
                        Location loc = null;
                        if (task.isSuccessful() && task.getResult() != null) {
                            loc = task.getResult();
                        }
                        if (loc != null) {
                            if (!responded[0]) {
                                responded[0] = true;
                                timeoutHandler.removeCallbacks(timeoutRunnable);
                                listener.onLocationResult(loc.getLatitude(), loc.getLongitude(), false);
                            }
                        } else {
                            try {
                                fusedLocationClient.getLastLocation()
                                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                                        @Override
                                        public void onComplete(Task<Location> lastTask) {
                                            if (responded[0]) return;
                                            
                                            Location lastLoc = null;
                                            if (lastTask.isSuccessful() && lastTask.getResult() != null) {
                                                lastLoc = lastTask.getResult();
                                            }
                                            if (lastLoc != null) {
                                                responded[0] = true;
                                                timeoutHandler.removeCallbacks(timeoutRunnable);
                                                listener.onLocationResult(lastLoc.getLatitude(), lastLoc.getLongitude(), false);
                                            } else {
                                                responded[0] = true;
                                                timeoutHandler.removeCallbacks(timeoutRunnable);
                                                listener.onLocationResult(0.0, 0.0, true);
                                            }
                                        }
                                    });
                            } catch (SecurityException e) {
                                if (!responded[0]) {
                                    responded[0] = true;
                                    timeoutHandler.removeCallbacks(timeoutRunnable);
                                    listener.onLocationResult(0.0, 0.0, true);
                                }
                            }
                        }
                    }
                });
        } catch (SecurityException e) {
            if (!responded[0]) {
                responded[0] = true;
                timeoutHandler.removeCallbacks(timeoutRunnable);
                listener.onLocationResult(0.0, 0.0, true);
            }
        }
    }

    public static boolean isLocationEnabled(Context context) {
        android.location.LocationManager lm = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = false;
        boolean networkEnabled = false;
        try {
            gpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        } catch (Exception e) {}
        try {
            networkEnabled = lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {}
        return gpsEnabled || networkEnabled;
    }

    public static boolean checkAndShowLocationSettings(final Context context) {
        if (!isLocationEnabled(context)) {
            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("⚠️ Location Services Disabled")
                    .setMessage("This task requires device location to be enabled. Please turn on location services in Settings to proceed.")
                    .setPositiveButton("Turn On Location", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            context.startActivity(intent);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return false;
        }
        return true;
    }
}
