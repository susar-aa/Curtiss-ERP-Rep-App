package com.example.curtiss;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LoginActivityTest {

    @Rule
    public ActivityScenarioRule<LoginActivity> activityScenarioRule =
            new ActivityScenarioRule<>(LoginActivity.class);

    @Test
    public void testUiComponentsVisible() {
        // Verify username input field is visible
        onView(withId(R.id.edtUsername)).check(matches(isDisplayed()));

        // Verify password input field is visible
        onView(withId(R.id.edtPassword)).check(matches(isDisplayed()));

        // Verify login button is visible and enabled initially
        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()));
        onView(withId(R.id.btnLogin)).check(matches(isEnabled()));
    }

    @Test
    public void testEmptyInputAuthFailureDoesNotCrash() {
        // Clear inputs
        onView(withId(R.id.edtUsername)).perform(clearText(), closeSoftKeyboard());
        onView(withId(R.id.edtPassword)).perform(clearText(), closeSoftKeyboard());

        // Click login button
        onView(withId(R.id.btnLogin)).perform(click());

        // Verify elements are still visible (no crash occurred, and we are still on the login page)
        onView(withId(R.id.edtUsername)).check(matches(isDisplayed()));
        onView(withId(R.id.btnLogin)).check(matches(isEnabled()));
    }

    @Test
    public void testInputTypingFlow() {
        // Type some mock username and password
        onView(withId(R.id.edtUsername)).perform(typeText("test_user"), closeSoftKeyboard());
        onView(withId(R.id.edtPassword)).perform(typeText("password123"), closeSoftKeyboard());

        // Verify login button is clicked
        onView(withId(R.id.btnLogin)).perform(click());
    }
}
