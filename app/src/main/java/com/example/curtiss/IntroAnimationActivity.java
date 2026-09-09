package com.example.curtiss;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;

public class IntroAnimationActivity extends AppCompatActivity {

    private boolean hasNavigated = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable safetyTimeoutRunnable;
    private LottieAnimationView lottieIntroAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(android.graphics.Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setContentView(R.layout.activity_intro_animation);

        lottieIntroAnimation = findViewById(R.id.lottieIntroAnimation);

        if (lottieIntroAnimation != null) {
            lottieIntroAnimation.addAnimatorListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            navigateToSplash();
                        }
                    }, 3000);
                }
            });
        }

        // Safety timeout to ensure transition to SplashActivity after 6500ms total
        safetyTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                navigateToSplash();
            }
        };
        mainHandler.postDelayed(safetyTimeoutRunnable, 6500);
    }

    private synchronized void navigateToSplash() {
        if (hasNavigated) return;
        hasNavigated = true;

        if (mainHandler != null && safetyTimeoutRunnable != null) {
            mainHandler.removeCallbacks(safetyTimeoutRunnable);
        }
        if (lottieIntroAnimation != null) {
            lottieIntroAnimation.cancelAnimation();
        }

        Intent intent = new Intent(IntroAnimationActivity.this, SplashActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainHandler != null && safetyTimeoutRunnable != null) {
            mainHandler.removeCallbacks(safetyTimeoutRunnable);
        }
    }
}
