package com.example.curtiss;

import android.Manifest;
import android.content.Context;
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
        void onLocationResult(double latitude, double longitude);
    }

    public static void captureCurrentLocation(Context context, LocationResultListener listener) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            listener.onLocationResult(7.1824, 79.8801); // Default fallback
            return;
        }

        try {
            FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnCompleteListener(new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(Task<Location> task) {
                        Location loc = null;
                        if (task.isSuccessful() && task.getResult() != null) {
                            loc = task.getResult();
                        }
                        if (loc != null) {
                            listener.onLocationResult(loc.getLatitude(), loc.getLongitude());
                        } else {
                            // Fallback to getLastLocation
                            try {
                                fusedLocationClient.getLastLocation()
                                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                                        @Override
                                        public void onComplete(Task<Location> lastTask) {
                                            Location lastLoc = null;
                                            if (lastTask.isSuccessful() && lastTask.getResult() != null) {
                                                lastLoc = lastTask.getResult();
                                            }
                                            if (lastLoc != null) {
                                                listener.onLocationResult(lastLoc.getLatitude(), lastLoc.getLongitude());
                                            } else {
                                                listener.onLocationResult(7.1824, 79.8801); // Fallback standard
                                            }
                                        }
                                    });
                            } catch (SecurityException e) {
                                listener.onLocationResult(7.1824, 79.8801);
                            }
                        }
                    }
                });
        } catch (SecurityException e) {
            listener.onLocationResult(7.1824, 79.8801);
        }
    }
}
