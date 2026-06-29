package com.example.curtiss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SecurePreferencesAndroidTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testGetSessionPrefs() {
        SharedPreferences prefs = SecurePreferences.getSessionPrefs(context);
        assertNotNull("Secure SharedPreferences should not be null", prefs);

        // Put and get string
        prefs.edit().putString("test_key", "test_value").commit();
        assertEquals("test_value", prefs.getString("test_key", ""));

        // Clean up
        prefs.edit().remove("test_key").commit();
    }
}
