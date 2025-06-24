package com.example.mobiledegreefinalproject;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;

public class SettingsFragment extends Fragment {

    private ImageView profileImage;
    private TextView profileName;
    private LinearLayout profileLayout;
    private LinearLayout themeLayout;
    private LinearLayout syncLayout;
    private LinearLayout feedbackLayout;
    private LinearLayout aboutLayout;
    private LinearLayout loginLayout;
    private LinearLayout registerLayout;
    private LinearLayout logoutLayout;
    private View dividerLogout;
    private UserManager userManager;
    private SyncPreferences syncPrefs;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        try {
            userManager = UserManager.getInstance(requireContext());
            syncPrefs = new SyncPreferences(requireContext());
            initViews(view);
            setupClickListeners();
            updateAuthenticationOptions();
            updateProfile();
        } catch (Exception e) {
            android.util.Log.e("SettingsFragment", "Error in onViewCreated", e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error loading settings", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        try {
            updateAuthenticationOptions();
            updateProfile();
            updateSyncVisibility();
        } catch (Exception e) {
            android.util.Log.e("SettingsFragment", "Error in onResume", e);
        }
    }

    private void initViews(View view) {
        profileImage = view.findViewById(R.id.iv_profile);
        profileName = view.findViewById(R.id.tv_profile_name);
        profileLayout = view.findViewById(R.id.layout_profile);
        themeLayout = view.findViewById(R.id.layout_theme);
        syncLayout = view.findViewById(R.id.layout_sync);
        feedbackLayout = view.findViewById(R.id.layout_feedback);
        aboutLayout = view.findViewById(R.id.layout_about);
        loginLayout = view.findViewById(R.id.layout_login);
        registerLayout = view.findViewById(R.id.layout_register);
        logoutLayout = view.findViewById(R.id.layout_logout);
        dividerLogout = view.findViewById(R.id.divider_logout);
    }

    private void setupClickListeners() {
        profileLayout.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(getActivity(), ProfileActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                android.util.Log.e("SettingsFragment", "Error starting ProfileActivity", e);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Error opening profile", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        themeLayout.setOnClickListener(v -> showThemeSelectionDialog());
        
        if (syncLayout != null) {
            syncLayout.setOnClickListener(v -> showSyncSettingsDialog());
            // Show/hide sync option based on authentication status
            updateSyncVisibility();
        }
        
        feedbackLayout.setOnClickListener(v -> showFeedbackDialog());
        aboutLayout.setOnClickListener(v -> showAboutDialog());
        
        loginLayout.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                android.util.Log.e("SettingsFragment", "Error starting LoginActivity", e);
            }
        });
        
        registerLayout.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(getActivity(), RegisterActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                android.util.Log.e("SettingsFragment", "Error starting RegisterActivity", e);
            }
        });
        
        logoutLayout.setOnClickListener(v -> showLogoutDialog());
    }

    private void updateProfile() {
        try {
            if (userManager != null) {
                String userName = userManager.getUserName();
                if (userName != null && profileName != null) {
                    profileName.setText(userName);
                }
                updateProfileImage();
            }
        } catch (Exception e) {
            android.util.Log.e("SettingsFragment", "Error updating profile", e);
        }
    }
    
    private void updateProfileImage() {
        try {
            if (userManager == null || profileImage == null) return;
            
            if (userManager.isLoggedIn()) {
                // Load user's profile image if available
                String imageUrl = userManager.getProfileImageUrl();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    android.util.Log.d("SettingsFragment", "Loading user profile image: " + imageUrl);
                    
                    // Load actual user image with Glide
                    profileImage.setPadding(0, 0, 0, 0); // Remove padding for actual images
                    profileImage.setImageTintList(null); // Remove tint for actual images
                    
                    String cacheBustedUrl = imageUrl + "?timestamp=" + System.currentTimeMillis();
                    
                    Glide.with(this)
                        .load(cacheBustedUrl)
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .into(profileImage);
                        
                    android.util.Log.d("SettingsFragment", "User profile image loaded successfully");
                } else {
                    // Logged in but no profile image - show default person icon with tint and padding
                    android.util.Log.d("SettingsFragment", "Logged in user with no profile image");
                    int padding = (int) (16 * getResources().getDisplayMetrics().density); // 16dp padding
                    profileImage.setPadding(padding, padding, padding, padding);
                    profileImage.setImageTintList(android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.secondary)));
                    profileImage.setImageResource(R.drawable.ic_person);
                }
            } else {
                // Guest user - show default person icon with pink tint and padding
                android.util.Log.d("SettingsFragment", "Guest user - showing default icon");
                int padding = (int) (16 * getResources().getDisplayMetrics().density); // 16dp padding
                profileImage.setPadding(padding, padding, padding, padding);
                profileImage.setImageTintList(android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(R.color.primary)));
                profileImage.setImageResource(R.drawable.ic_person);
            }
        } catch (Exception e) {
            android.util.Log.e("SettingsFragment", "Error updating profile image", e);
            // Fallback to simple default
            try {
                if (profileImage != null) {
                    int padding = (int) (16 * getResources().getDisplayMetrics().density);
                    profileImage.setPadding(padding, padding, padding, padding);
                    profileImage.setImageResource(R.drawable.ic_person);
                    profileImage.setImageTintList(android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.primary)));
                }
            } catch (Exception e2) {
                android.util.Log.e("SettingsFragment", "Error in fallback image loading", e2);
            }
        }
    }
    
    private void updateAuthenticationOptions() {
        try {
            if (userManager == null) return;
            
            if (userManager.isLoggedIn()) {
                // User is logged in - show logout option, hide login/register
                if (loginLayout != null) loginLayout.setVisibility(View.GONE);
                if (registerLayout != null) registerLayout.setVisibility(View.GONE);
                if (logoutLayout != null) logoutLayout.setVisibility(View.VISIBLE);
                if (dividerLogout != null) dividerLogout.setVisibility(View.VISIBLE);
            } else {
                // User is guest - show login/register options, hide logout
                if (loginLayout != null) loginLayout.setVisibility(View.VISIBLE);
                if (registerLayout != null) registerLayout.setVisibility(View.VISIBLE);
                if (logoutLayout != null) logoutLayout.setVisibility(View.GONE);
                if (dividerLogout != null) dividerLogout.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            android.util.Log.e("SettingsFragment", "Error updating auth options", e);
        }
    }

    private void updateSyncVisibility() {
        try {
            if (syncLayout == null) return;
            
            if (userManager.isLoggedIn()) {
                syncLayout.setVisibility(View.GONE);
            } else {
                syncLayout.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            android.util.Log.e("SettingsFragment", "Error updating sync visibility", e);
        }
    }

    private void showAboutDialog() {
        try {
            if (getContext() == null) return;
            
            new AlertDialog.Builder(getContext())
                    .setTitle("About WanderPlan")
                    .setMessage("WanderPlan v1.0\n\nYour personal travel planning companion.\n\nDeveloped with ❤️ for adventure seekers.")
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            android.util.Log.e("SettingsFragment", "Error showing about dialog", e);
        }
    }
    
    private void showLogoutDialog() {
        try {
            if (getContext() == null || userManager == null) return;
            
            new AlertDialog.Builder(getContext())
                    .setTitle("Sign Out")
                    .setMessage("Are you sure you want to sign out?")
                    .setPositiveButton("Sign Out", (dialog, which) -> {
                        userManager.signOut();
                        updateAuthenticationOptions();
                        updateProfile();
                        
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Signed out successfully", Toast.LENGTH_SHORT).show();
                        }
                        
                        // Navigate to login screen
                        Intent intent = new Intent(getActivity(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        
                        if (getActivity() != null) {
                            getActivity().finish();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            android.util.Log.e("SettingsFragment", "Error showing logout dialog", e);
        }
    }
    
    private void showThemeSelectionDialog() {
        try {
            if (getContext() == null) return;
            
            String[] themes = {"Light Theme", "Dark Theme", "System Default"};
            
            new AlertDialog.Builder(getContext())
                    .setTitle("Select Theme")
                    .setItems(themes, (dialog, which) -> {
                        String selectedTheme = themes[which];
                        if (getContext() != null) {
                            Toast.makeText(getContext(), 
                                selectedTheme + " selected! (Feature coming soon)", 
                                Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            android.util.Log.e("SettingsFragment", "Error showing theme dialog", e);
        }
    }
    
    private void showSyncSettingsDialog() {
        if (userManager == null || syncPrefs == null) return;
        
        if (!userManager.isLoggedIn()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("🔄 Data Sync")
                    .setMessage("Data sync is only available for logged-in users.\n\nPlease log in to access sync settings.")
                    .setPositiveButton("Login", (dialog, which) -> {
                        Intent intent = new Intent(getActivity(), LoginActivity.class);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        
        String syncSummary = syncPrefs.getSyncSummary();
        boolean autoSyncEnabled = syncPrefs.isAutoSyncEnabled();
        boolean syncOnLogin = syncPrefs.shouldSyncOnLogin();
        
        String message = "Current sync status:\n" + syncSummary + "\n\n" +
                        "Auto Sync: " + (autoSyncEnabled ? "✅ Enabled" : "❌ Disabled") + "\n" +
                        "Sync on Login: " + (syncOnLogin ? "✅ Enabled" : "❌ Disabled") + "\n\n" +
                        "Choose an action:";
        
        new AlertDialog.Builder(requireContext())
                .setTitle("🔄 Sync Settings")
                .setMessage(message)
                .setPositiveButton("🔄 Sync Now", (dialog, which) -> {
                    performManualSync();
                })
                .setNeutralButton("⚙️ Settings", (dialog, which) -> {
                    showSyncPreferencesDialog();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void performManualSync() {
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getContext());
        progressDialog.setTitle("🔄 Syncing Data");
        progressDialog.setMessage("Converting local data to JSON and uploading...");
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.show();
        
        DataSyncService syncService = new DataSyncService(requireContext());
        syncService.syncLocalDataToFirebase(new DataSyncService.OnSyncCompleteListener() {
            @Override
            public void onProgressUpdate(int progress, String message) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressDialog.setProgress(progress);
                        progressDialog.setMessage(message);
                    });
                }
            }
            
            @Override
            public void onSuccess(int tripsSynced, int activitiesSynced) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        
                        // Build message without showing "Activities: 0" when there are no activities
                        String activitiesText = activitiesSynced > 0 ? "\n📝 Activities: " + activitiesSynced : "";
                        
                        new AlertDialog.Builder(requireContext())
                                .setTitle("✅ Sync Complete")
                                .setMessage("🎉 Success!\n\n" +
                                           "📊 Data synced to Firebase as JSON:\n" +
                                           "🧳 Trips: " + tripsSynced + 
                                           activitiesText + "\n\n" +
                                           "Your data is now safely backed up in the cloud!")
                                .setPositiveButton("OK", null)
                                .show();
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressDialog.dismiss();
                        
                        new AlertDialog.Builder(requireContext())
                                .setTitle("❌ Sync Failed")
                                .setMessage("⚠️ There was an issue syncing your data:\n\n" + error)
                                .setPositiveButton("OK", null)
                                .show();
                    });
                }
            }
        });
    }
    
    private void showSyncPreferencesDialog() {
        boolean autoSync = syncPrefs.isAutoSyncEnabled();
        boolean syncOnLogin = syncPrefs.shouldSyncOnLogin();
        
        String[] options = {
            (autoSync ? "✅" : "☐") + " Auto Sync (every 24 hours)",
            (syncOnLogin ? "✅" : "☐") + " Sync on Login"
        };
        
        boolean[] checkedItems = {autoSync, syncOnLogin};
        
        new AlertDialog.Builder(requireContext())
                .setTitle("⚙️ Sync Preferences")
                .setMultiChoiceItems(options, checkedItems, (dialog, which, isChecked) -> {
                    switch (which) {
                        case 0:
                            syncPrefs.setAutoSyncEnabled(isChecked);
                            break;
                        case 1:
                            syncPrefs.setSyncOnLogin(isChecked);
                            break;
                    }
                })
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFeedbackDialog() {
        try {
            if (getContext() == null) return;
            
            android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(50, 40, 50, 10);
            
            final android.widget.EditText feedbackInput = new android.widget.EditText(getContext());
            feedbackInput.setHint("Share your feedback, suggestions, or report issues...");
            feedbackInput.setMinLines(3);
            feedbackInput.setMaxLines(8);
            layout.addView(feedbackInput);
            
            new AlertDialog.Builder(getContext())
                    .setTitle("Send Feedback")
                    .setView(layout)
                    .setPositiveButton("Send", (dialog, which) -> {
                        String feedback = feedbackInput.getText().toString().trim();
                        if (feedback.isEmpty()) {
                            if (getContext() != null) {
                                Toast.makeText(getContext(), "Please enter your feedback", Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        
                        if (getContext() != null) {
                            Toast.makeText(getContext(), 
                                "Thank you for your feedback! We'll review it soon.", 
                                Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            android.util.Log.e("SettingsFragment", "Error showing feedback dialog", e);
        }
    }
} 