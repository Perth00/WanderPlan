package com.example.mobiledegreefinalproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.mobiledegreefinalproject.adapter.OnboardingAdapter;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private Button nextButton;
    private Button getStartedButton;
    private TextView skipButton;
    
    private OnboardingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        initViews();
        setupViewPager();
        setupClickListeners();
    }

    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        nextButton = findViewById(R.id.btn_next);
        getStartedButton = findViewById(R.id.btn_get_started);
        skipButton = findViewById(R.id.tv_skip);
    }

    private void setupViewPager() {
        adapter = new OnboardingAdapter(this);
        viewPager.setAdapter(adapter);

        // Setup tab layout with view pager
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    // Tab indicator will be handled by the layout
                }).attach();

        // Handle page changes
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateButtonVisibility(position);
            }
        });
    }

    private void setupClickListeners() {
        skipButton.setOnClickListener(v -> navigateToMain());
        
        nextButton.setOnClickListener(v -> {
            int currentItem = viewPager.getCurrentItem();
            if (currentItem < adapter.getItemCount() - 1) {
                viewPager.setCurrentItem(currentItem + 1, true);
            }
        });

        getStartedButton.setOnClickListener(v -> navigateToMain());
    }

    private void updateButtonVisibility(int position) {
        if (position == adapter.getItemCount() - 1) {
            // Last page
            nextButton.setVisibility(View.GONE);
            getStartedButton.setVisibility(View.VISIBLE);
            skipButton.setVisibility(View.GONE);
        } else {
            // First and middle pages
            nextButton.setVisibility(View.VISIBLE);
            getStartedButton.setVisibility(View.GONE);
            skipButton.setVisibility(View.VISIBLE);
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(OnboardingActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
} 