package com.example.curtiss;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class PushWorker extends Worker {

    private static final String TAG = "PushWorker";

    public PushWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
        
        if (!prefs.contains("user_id")) {
            Log.d(TAG, "PushWorker: No logged in user session. Skipping instant push.");
            return Result.failure();
        }

        int userId = prefs.getInt("user_id", 0);
        SyncManager syncManager = SyncManager.getInstance(context);

        Log.d(TAG, "PushWorker: Initiating instant background push sync for user " + userId);
        
        boolean acquired = false;
        try {
            acquired = syncManager.tryAcquireSyncLock();
            if (!acquired) {
                Log.d(TAG, "PushWorker: Sync already in progress, deferring push.");
                return Result.retry();
            }
            
            boolean pushSuccess = syncManager.executePushSafe(context, userId);
            
            if (pushSuccess) {
                Log.d(TAG, "PushWorker: Instant background push sync completed successfully.");
                return Result.success();
            } else {
                Log.e(TAG, "PushWorker: executePushSafe returned false, scheduling retry.");
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "PushWorker: Exception during instant push sync: " + e.getMessage(), e);
            return Result.retry();
        } finally {
            if (acquired) {
                syncManager.releaseSyncLock();
            }
        }
    }
}
