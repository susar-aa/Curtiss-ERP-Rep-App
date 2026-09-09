package com.example.curtiss;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class CurtissMessagingService extends FirebaseMessagingService {
    private static final String TAG = "CurtissMessagingService";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());

            Map<String, String> data = remoteMessage.getData();
            if (data.containsKey("type") && "stock_update".equals(data.get("type"))) {
                try {
                    int productId = Integer.parseInt(data.get("product_id"));
                    double newStockQty = Double.parseDouble(data.get("stock_qty"));
                    double reservedQty = Double
                            .parseDouble(data.containsKey("reserved_qty") ? data.get("reserved_qty") : "0");

                    DatabaseHelper dbHelper = new DatabaseHelper(this);
                    boolean success = dbHelper.updateStockLocally(productId, newStockQty, reservedQty);

                    if (success) {
                        Log.i(TAG, "Stock for product " + productId + " successfully updated to " + newStockQty);
                    } else {
                        Log.e(TAG, "Failed to update stock for product " + productId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing stock update payload: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed token: " + token);

        // Save token to SharedPreferences to be sent to server later
        getSharedPreferences("fcm_prefs", MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .putBoolean("token_synced", false)
                .apply();
    }
}
