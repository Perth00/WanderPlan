package com.example.mobiledegreefinalproject;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 2000; // 2 seconds
    private static final String PREFS_NAME = "WanderPlanPrefs";
    private static final String FIRST_LAUNCH_KEY = "firstLaunch";

    private ImageView logoImageView;
    private TextView appNameTextView;
    private TextView taglineTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        initViews();
        startAnimations();
        navigateAfterDelay();
    }

    private void initViews() {
        logoImageView = findViewById(R.id.iv_logo);
        appNameTextView = findViewById(R.id.tv_app_name);
        taglineTextView = findViewById(R.id.tv_tagline);
    }

    private void startAnimations() {
        // Load animations
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);

        // Apply animations with delays
        logoImageView.startAnimation(fadeIn);
        
        appNameTextView.postDelayed(() -> {
            appNameTextView.setVisibility(android.view.View.VISIBLE);
            appNameTextView.startAnimation(slideUp);
        }, 500);

        taglineTextView.postDelayed(() -> {
            taglineTextView.setVisibility(android.view.View.VISIBLE);
            taglineTextView.startAnimation(fadeIn);
        }, 1000);
    }

    private void navigateAfterDelay() {
        new Handler().postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean isFirstLaunch = prefs.getBoolean(FIRST_LAUNCH_KEY, true);

            Intent intent;
            if (isFirstLaunch) {
                intent = new Intent(SplashActivity.this, OnboardingActivity.class);
                // Mark first launch as completed
                prefs.edit().putBoolean(FIRST_LAUNCH_KEY, false).apply();
            } else {
                intent = new Intent(SplashActivity.this, MainActivity.class);
            }

            startActivity(intent);
            finish();
        }, SPLASH_DURATION);
    }
} 