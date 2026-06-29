package com.example.curtiss;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import android.util.Log;

public class SecurePreferences {
    private static final String TAG = "SecurePreferences";
    private static final String PREF_FILE_NAME = "rep_session_secure";

    public static SharedPreferences getSessionPrefs(Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                PREF_FILE_NAME,
                masterKeyAlias,
                context.getApplicationContext(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Error initializing EncryptedSharedPreferences, falling back to standard plaintext preferences: " + e.getMessage());
            return context.getSharedPreferences("rep_session_fallback", Context.MODE_PRIVATE);
        }
    }
}
