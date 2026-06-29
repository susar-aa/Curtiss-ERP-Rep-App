package com.example.curtiss;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
        if (!prefs.contains("user_id")) {
            Log.d(TAG, "SyncWorker: No logged in user session. Skipping background sync.");
            return Result.failure();
        }

        int userId = prefs.getInt("user_id", 0);
        SyncManager syncManager = SyncManager.getInstance(context);

        if (!syncManager.tryAcquireSyncLock()) {
            Log.d(TAG, "SyncWorker: Sync lock is busy (another sync in progress). Retrying later.");
            return Result.retry();
        }

        try {
            Log.d(TAG, "SyncWorker: Initiating persistent background pull sync for user " + userId);
            boolean pullSuccess = syncManager.executePull(context, userId);
            if (!pullSuccess) {
                Log.e(TAG, "SyncWorker: executePull failed.");
                return Result.retry();
            }

            Log.d(TAG, "SyncWorker: Background pull sync completed successfully. Push sync is manual-only.");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "SyncWorker: Exception during background sync: " + e.getMessage(), e);
            return Result.retry();
        } finally {
            syncManager.releaseSyncLock();
        }
    }
}
