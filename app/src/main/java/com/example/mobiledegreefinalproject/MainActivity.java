package com.example.mobiledegreefinalproject;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;
    private UserManager userManager;
    
    // Interface for communicating between fragments
    public interface TripDeletionListener {
        void onTripDeleted(int tripId);
    }

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
        
        // Check if activity is finishing to prevent crashes during logout
        if (isFinishing()) {
            android.util.Log.w("MainActivity", "Activity is finishing - skipping onResume operations");
            return;
        }
        
        // Refresh user data when app becomes active
        if (userManager != null && userManager.isLoggedIn()) {
            userManager.syncUserDataFromFirebase(new UserManager.OnDataSyncListener() {
                @Override
                public void onSuccess() {
                    // Check if activity is still valid
                    if (!isFinishing() && !isDestroyed()) {
                        android.util.Log.d("MainActivity", "User data refreshed successfully on resume");
                    }
                }

                @Override
                public void onError(String error) {
                    // Check if activity is still valid before logging
                    if (!isFinishing() && !isDestroyed()) {
                        android.util.Log.w("MainActivity", "Failed to refresh user data on resume: " + error);
                    }
                }
            });
        }
    }

    private void initViews() {
        bottomNavigation = findViewById(R.id.bottom_navigation);
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            // Check if activity is finishing to prevent crashes
            if (isFinishing()) {
                android.util.Log.w("MainActivity", "Activity is finishing - ignoring navigation");
                return false;
            }
            
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
                try {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, selectedFragment)
                            .commit();
                    return true;
                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error replacing fragment", e);
                    return false;
                }
            }
            return false;
        });
    }

    /**
     * Call this method when a trip is deleted to notify all relevant fragments
     */
    public void notifyTripDeleted(int tripId) {
        android.util.Log.d("MainActivity", "=== MAIN ACTIVITY TRIP DELETION NOTIFICATION ===");
        android.util.Log.d("MainActivity", "Notifying fragments about trip deletion: " + tripId);
        
        // Find and notify BudgetFragment if it's currently active or in backstack
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        android.util.Log.d("MainActivity", "Current fragment: " + (currentFragment != null ? currentFragment.getClass().getSimpleName() : "null"));
        
        boolean budgetFragmentNotified = false;
        
        if (currentFragment instanceof BudgetFragment) {
            android.util.Log.d("MainActivity", "Notifying current BudgetFragment");
            ((BudgetFragment) currentFragment).onTripDeleted(tripId);
            budgetFragmentNotified = true;
        }
        
        // Also notify if there's a BudgetFragment in the backstack
        android.util.Log.d("MainActivity", "Checking fragments in backstack, total fragments: " + getSupportFragmentManager().getFragments().size());
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            android.util.Log.d("MainActivity", "Fragment in backstack: " + fragment.getClass().getSimpleName());
            if (fragment instanceof BudgetFragment && fragment != currentFragment) {
                android.util.Log.d("MainActivity", "Notifying BudgetFragment in backstack");
                ((BudgetFragment) fragment).onTripDeleted(tripId);
                budgetFragmentNotified = true;
            }
        }
        
        if (!budgetFragmentNotified) {
            android.util.Log.w("MainActivity", "No BudgetFragment found to notify!");
        }
        
        android.util.Log.d("MainActivity", "=== NOTIFICATION COMPLETE ===");
    }

    public String getUserName() {
        return userManager != null ? userManager.getUserName() : "Guest";
    }

    public void setUserName(String userName) {
        if (userManager != null) {
            userManager.setUserName(userName);
        }
    }
    
    public UserManager getUserManager() {
        return userManager;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        android.util.Log.d("MainActivity", "MainActivity destroyed - cleaning up");
        // Clear references to prevent memory leaks
        userManager = null;
    }
}
