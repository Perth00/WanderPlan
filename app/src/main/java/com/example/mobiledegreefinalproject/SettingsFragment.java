package com.example.mobiledegreefinalproject;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private ImageView profileImage;
    private TextView profileName;
    private LinearLayout profileLayout;
    private LinearLayout themeLayout;
    private LinearLayout feedbackLayout;
    private LinearLayout aboutLayout;
    private LinearLayout loginLayout;
    private LinearLayout registerLayout;
    private LinearLayout logoutLayout;
    private View dividerLogout;
    private UserManager userManager;
    private android.app.ProgressDialog loadingDialog;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        userManager = UserManager.getInstance(requireContext());
        setupLoadingDialog();
        initViews(view);
        setupClickListeners();
        updateAuthenticationOptions();
        updateProfile();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh profile and authentication options when fragment becomes visible
        updateAuthenticationOptions();
        updateProfile();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up loading dialog
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private void initViews(View view) {
        profileImage = view.findViewById(R.id.iv_profile);
        profileName = view.findViewById(R.id.tv_profile_name);
        profileLayout = view.findViewById(R.id.layout_profile);
        themeLayout = view.findViewById(R.id.layout_theme);
        feedbackLayout = view.findViewById(R.id.layout_feedback);
        aboutLayout = view.findViewById(R.id.layout_about);
        loginLayout = view.findViewById(R.id.layout_login);
        registerLayout = view.findViewById(R.id.layout_register);
        logoutLayout = view.findViewById(R.id.layout_logout);
        dividerLogout = view.findViewById(R.id.divider_logout);
    }

    private void setupLoadingDialog() {
        if (getContext() != null) {
            loadingDialog = new android.app.ProgressDialog(getContext());
            loadingDialog.setTitle("Processing...");
            loadingDialog.setMessage("Please wait...");
            loadingDialog.setCancelable(false);
            loadingDialog.setCanceledOnTouchOutside(false);
        }
    }

    private void showLoading(String message) {
        if (loadingDialog != null && getContext() != null) {
            loadingDialog.setMessage(message);
            if (!loadingDialog.isShowing()) {
                loadingDialog.show();
            }
        }
    }

    private void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private void setupClickListeners() {
        profileLayout.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ProfileActivity.class);
            startActivity(intent);
        });
        
        themeLayout.setOnClickListener(v -> {
            // TODO: Implement theme selection
            if (getContext() != null) {
                android.widget.Toast.makeText(getContext(), 
                    "Theme selection coming soon!", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        
        feedbackLayout.setOnClickListener(v -> {
            // TODO: Implement feedback functionality
            if (getContext() != null) {
                android.widget.Toast.makeText(getContext(), 
                    "Feedback form coming soon!", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        
        aboutLayout.setOnClickListener(v -> {
            // TODO: Show about dialog
            showAboutDialog();
        });
        
        loginLayout.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            startActivity(intent);
        });
        
        registerLayout.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), RegisterActivity.class);
            startActivity(intent);
        });
        
        logoutLayout.setOnClickListener(v -> {
            showLogoutDialog();
        });
    }

    private void updateProfile() {
        if (userManager != null) {
            String userName = userManager.getUserName();
            profileName.setText(userName);
            
            // Update profile image based on user status
            updateProfileImage();
        }
    }
    
    private void updateProfileImage() {
        if (userManager == null) return;
        
        if (userManager.isLoggedIn()) {
            // Load user's profile image if available
            String imageUrl = userManager.getProfileImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                android.util.Log.d("SettingsFragment", "Loading user profile image: " + imageUrl);
                // Clear tint and padding for user image, load with circular crop
                profileImage.setImageTintList(null);
                profileImage.setPadding(0, 0, 0, 0); // Remove padding for user images
                
                // Add cache busting to ensure fresh image loading
                String cacheBustedUrl = imageUrl + "?timestamp=" + System.currentTimeMillis();
                
                com.bumptech.glide.Glide.with(this)
                    .load(cacheBustedUrl)
                    .transform(new com.bumptech.glide.load.resource.bitmap.CircleCrop())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .skipMemoryCache(true) // Skip memory cache
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE) // Skip disk cache
                    .into(profileImage);
            } else {
                // Logged in but no profile image - show default person icon with tint and padding
                android.util.Log.d("SettingsFragment", "Logged in user with no profile image");
                int padding = (int) (16 * getResources().getDisplayMetrics().density); // 16dp padding
                profileImage.setPadding(padding, padding, padding, padding);
                profileImage.setImageTintList(android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(R.color.primary)));
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
    }
    
    private void updateAuthenticationOptions() {
        if (userManager == null) return;
        
        if (userManager.isLoggedIn()) {
            // User is logged in - show logout option, hide login/register
            loginLayout.setVisibility(View.GONE);
            registerLayout.setVisibility(View.GONE);
            logoutLayout.setVisibility(View.VISIBLE);
            dividerLogout.setVisibility(View.VISIBLE);
        } else {
            // User is guest - show login/register options, hide logout
            loginLayout.setVisibility(View.VISIBLE);
            registerLayout.setVisibility(View.VISIBLE);
            logoutLayout.setVisibility(View.GONE);
            dividerLogout.setVisibility(View.GONE);
        }
    }

    private void showEditProfileDialog() {
        if (getContext() == null) return;
        
        EditText editText = new EditText(getContext());
        editText.setText(profileName.getText());
        
        new AlertDialog.Builder(getContext())
                .setTitle("Edit Profile Name")
                .setView(editText)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = editText.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        if (getActivity() instanceof MainActivity) {
                            MainActivity mainActivity = (MainActivity) getActivity();
                            mainActivity.setUserName(newName);
                            profileName.setText(newName);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAboutDialog() {
        if (getContext() == null) return;
        
        new AlertDialog.Builder(getContext())
                .setTitle("About WanderPlan")
                .setMessage("WanderPlan v1.0\n\nYour personal travel planning companion.\n\nDeveloped with ❤️ for adventure seekers.")
                .setPositiveButton("OK", null)
                .show();
    }
    
    private void showLogoutDialog() {
        if (getContext() == null) return;
        
        new AlertDialog.Builder(getContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out? Your data has been synced to the cloud.")
                .setPositiveButton("Sign Out", (dialog, which) -> {
                    // Show loading during logout process
                    showLoading("Signing out...");
                    
                    // Disable UI interactions temporarily
                    logoutLayout.setEnabled(false);
                    
                    // Add slight delay to show loading (Firebase logout is typically instant)
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        // Sign out user
                        userManager.signOut();
                        
                        hideLoading();
                        
                        // Update UI immediately
                        updateAuthenticationOptions();
                        updateProfile();
                        
                        // Show feedback
                        if (getContext() != null) {
                            android.widget.Toast.makeText(getContext(), 
                                "Signed out successfully", 
                                android.widget.Toast.LENGTH_SHORT).show();
                        }
                        
                        // Navigate to login screen
                        Intent intent = new Intent(getActivity(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        
                        // Close current activity
                        if (getActivity() != null) {
                            getActivity().finish();
                        }
                    }, 200); // Very fast logout
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
} 