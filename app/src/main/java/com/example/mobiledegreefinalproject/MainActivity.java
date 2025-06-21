package com.example.mobiledegreefinalproject;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;
    private UserManager userManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupBottomNavigation();
        
        userManager = UserManager.getInstance(this);
        
        // Sync user data from Firebase if logged in
        if (userManager.isLoggedIn()) {
            userManager.syncUserDataFromFirebase(new UserManager.OnDataSyncListener() {
                @Override
                public void onSuccess() {
                    android.util.Log.d("MainActivity", "User data synced successfully on app start");
                }

                @Override
                public void onError(String error) {
                    android.util.Log.w("MainActivity", "Failed to sync user data on app start: " + error);
                }
            });
        }
        
        // Set default fragment if no saved state
        if (savedInstanceState == null) {
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh user data when app becomes active
        if (userManager != null && userManager.isLoggedIn()) {
            userManager.syncUserDataFromFirebase(new UserManager.OnDataSyncListener() {
                @Override
                public void onSuccess() {
                    android.util.Log.d("MainActivity", "User data refreshed successfully on resume");
                }

                @Override
                public void onError(String error) {
                    android.util.Log.w("MainActivity", "Failed to refresh user data on resume: " + error);
                }
            });
        }
    }

    private void initViews() {
        bottomNavigation = findViewById(R.id.bottom_navigation);
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_trips) {
                selectedFragment = new TripsFragment();
            } else if (itemId == R.id.nav_budget) {
                selectedFragment = new BudgetFragment();
            } else if (itemId == R.id.nav_settings) {
                selectedFragment = new SettingsFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
                return true;
            }
            return false;
        });
    }

    public String getUserName() {
        return userManager.getUserName();
    }

    public void setUserName(String userName) {
        userManager.setUserName(userName);
    }
    
    public UserManager getUserManager() {
        return userManager;
    }}
