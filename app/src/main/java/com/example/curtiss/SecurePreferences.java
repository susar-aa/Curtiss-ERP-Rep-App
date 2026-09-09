package com.example.curtiss;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class SecurePreferences {
    private static final String TAG = "SecurePreferences";
    private static final String PREF_FILE_NAME = "rep_session_secure";
    private static final String FALLBACK_PREF_FILE = "rep_session_fallback";

    public static SharedPreferences getSessionPrefs(Context context) {
        // EncryptedSharedPreferences requires API 23+ (Android 6.0)
        // Gracefully fall back to plain SharedPreferences on older devices like Mi Pad 2 (API 22)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.w(TAG, "Device API " + Build.VERSION.SDK_INT + " < 23; using plain SharedPreferences");
            return context.getApplicationContext().getSharedPreferences(FALLBACK_PREF_FILE, Context.MODE_PRIVATE);
        }

        try {
            String masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(
                    androidx.security.crypto.MasterKeys.AES256_GCM_SPEC);
            return androidx.security.crypto.EncryptedSharedPreferences.create(
                PREF_FILE_NAME,
                masterKeyAlias,
                context.getApplicationContext(),
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Error initializing EncryptedSharedPreferences, falling back to standard plaintext preferences: " + e.getMessage());
            return context.getApplicationContext().getSharedPreferences(FALLBACK_PREF_FILE, Context.MODE_PRIVATE);
        }
    }
}
