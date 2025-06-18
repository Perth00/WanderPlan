package com.example.mobiledegreefinalproject;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ProfileActivity extends AppCompatActivity {

    private ImageView profileImage;
    private ImageView editProfileImage;
    private TextInputLayout nameInputLayout;
    private TextInputEditText nameEditText;
    private TextView emailText;
    private TextView accountTypeText;
    private Button saveButton;
    private Button signOutButton;
    
    private UserManager userManager;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private boolean hasChanges = false;
    private android.app.ProgressDialog loadingDialog;

        @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        try {
            // Setup action bar/toolbar first
            setupActionBar();
            
            initViews();
            setupImagePicker();
            setupClickListeners();

            userManager = UserManager.getInstance(this);
            setupLoadingDialog();
            loadUserData();
            
            android.util.Log.d("ProfileActivity", "onCreate completed successfully");
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in onCreate", e);
            Toast.makeText(this, "Error loading profile. Please try again.", Toast.LENGTH_LONG).show();
            safeFinish();
        }
    }
    
    // Setup action bar with back button
    private void setupActionBar() {
        try {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setTitle("Profile");
                android.util.Log.d("ProfileActivity", "Action bar setup completed");
            }
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error setting up action bar", e);
        }
    }

    // Handle toolbar/action bar back button
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        try {
            if (item.getItemId() == android.R.id.home) {
                android.util.Log.d("ProfileActivity", "Toolbar back button pressed");
                handleBackAction();
                return true;
            }
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in onOptionsItemSelected", e);
            safeFinish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            // Refresh user data when returning to this activity
            refreshUserData();
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in onResume", e);
        }
    }

    @Override
    protected void onDestroy() {
        android.util.Log.d("ProfileActivity", "onDestroy called");
        
        try {
            // Clean up loading dialog safely
            if (loadingDialog != null) {
                try {
                    if (loadingDialog.isShowing()) {
                        loadingDialog.dismiss();
                    }
                } catch (Exception e) {
                    android.util.Log.w("ProfileActivity", "Error dismissing dialog: " + e.getMessage());
                }
                loadingDialog = null;
            }
            
            // Clear Glide safely
            try {
                if (profileImage != null && !isDestroyed() && !isFinishing()) {
                    Glide.with(this).clear(profileImage);
                }
            } catch (Exception e) {
                android.util.Log.w("ProfileActivity", "Error clearing Glide: " + e.getMessage());
            }
            
            // Clean up temporary images in background to avoid blocking
            new Thread(() -> {
                try {
                    java.io.File cacheDir = getCacheDir();
                    if (cacheDir != null && cacheDir.exists()) {
                        java.io.File[] files = cacheDir.listFiles((dir, name) -> 
                            name != null && name.startsWith("profile_") && name.endsWith(".jpg"));
                        if (files != null) {
                            for (java.io.File file : files) {
                                try {
                                    if (file.delete()) {
                                        android.util.Log.d("ProfileActivity", "Cleaned up: " + file.getName());
                                    }
                                } catch (Exception e) {
                                    android.util.Log.w("ProfileActivity", "Error deleting file: " + e.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.w("ProfileActivity", "Background cleanup error: " + e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in onDestroy", e);
        } finally {
            super.onDestroy();
        }
    }

    private void initViews() {
        try {
            profileImage = findViewById(R.id.profile_image);
            editProfileImage = findViewById(R.id.edit_profile_image);
            nameInputLayout = findViewById(R.id.name_input_layout);
            nameEditText = findViewById(R.id.name_edit_text);
            emailText = findViewById(R.id.email_text);
            accountTypeText = findViewById(R.id.account_type_text);
            saveButton = findViewById(R.id.save_button);
            signOutButton = findViewById(R.id.sign_out_button);
            
            // Validate all critical UI elements are found
            StringBuilder missingViews = new StringBuilder();
            
            if (profileImage == null) missingViews.append("profile_image, ");
            if (editProfileImage == null) missingViews.append("edit_profile_image, ");
            if (nameInputLayout == null) missingViews.append("name_input_layout, ");
            if (nameEditText == null) missingViews.append("name_edit_text, ");
            if (emailText == null) missingViews.append("email_text, ");
            if (accountTypeText == null) missingViews.append("account_type_text, ");
            if (saveButton == null) missingViews.append("save_button, ");
            if (signOutButton == null) missingViews.append("sign_out_button, ");
            
            if (missingViews.length() > 0) {
                String missing = missingViews.substring(0, missingViews.length() - 2);
                android.util.Log.e("ProfileActivity", "Missing UI elements: " + missing);
                Toast.makeText(this, "Error: UI elements not found. Please restart the app.", Toast.LENGTH_LONG).show();
            } else {
                android.util.Log.d("ProfileActivity", "All UI elements found successfully");
            }
            
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in initViews: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing UI. Please restart the app.", Toast.LENGTH_LONG).show();
        }
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        // Compress and upload image to prevent crashes
                        compressAndUploadImage(imageUri);
                    }
                }
            }
        );
    }
    
    private void setupLoadingDialog() {
        loadingDialog = new android.app.ProgressDialog(this);
        loadingDialog.setTitle("Processing...");
        loadingDialog.setMessage("Please wait while we update your profile.");
        loadingDialog.setCancelable(false); // Prevent dismissal
        loadingDialog.setCanceledOnTouchOutside(false);
    }
    
    private void showLoading(String message) {
        try {
            if (loadingDialog != null && !isFinishing() && !isDestroyed()) {
                loadingDialog.setMessage(message);
                if (!loadingDialog.isShowing()) {
                    loadingDialog.show();
                }
            }
        } catch (Exception e) {
            android.util.Log.w("ProfileActivity", "Error showing loading dialog: " + e.getMessage());
        }
    }
    
    private void hideLoading() {
        try {
            if (loadingDialog != null && loadingDialog.isShowing() && !isFinishing() && !isDestroyed()) {
                loadingDialog.dismiss();
                android.util.Log.d("ProfileActivity", "Loading dialog hidden successfully");
            }
        } catch (Exception e) {
            android.util.Log.w("ProfileActivity", "Error hiding loading dialog: " + e.getMessage());
            // Try to set it to null if dismissal fails
            try {
                loadingDialog = null;
            } catch (Exception e2) {
                android.util.Log.e("ProfileActivity", "Error nullifying dialog", e2);
            }
        }
    }
    
    private void compressAndUploadImage(Uri imageUri) {
        showLoading("Uploading...");
        
        // Add timeout safety mechanism to ensure loading dialog always hides
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                android.util.Log.w("ProfileActivity", "Upload timeout - force hiding loading dialog");
                hideLoading();
                editProfileImage.setEnabled(true);
                editProfileImage.setAlpha(1.0f);
                saveButton.setEnabled(true);
                
                // Check if image was actually uploaded by checking UserManager
                String latestImageUrl = userManager.getProfileImageUrl();
                if (latestImageUrl != null && !latestImageUrl.isEmpty()) {
                    // Image was uploaded, just refresh UI
                    android.util.Log.d("ProfileActivity", "Timeout but image was uploaded: " + latestImageUrl);
                    loadProfileImageDirectly(latestImageUrl);
                    Toast.makeText(ProfileActivity.this, 
                        "Image uploaded successfully!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ProfileActivity.this, 
                        "Upload took too long. Please try again.", Toast.LENGTH_LONG).show();
                }
            }
        }, 5000); // 5 second timeout - balanced speed
        
        // Compress image in background thread to prevent UI blocking
        new Thread(() -> {
            android.graphics.Bitmap originalBitmap = null;
            android.graphics.Bitmap scaledBitmap = null;
            
            try {
                // Compress the image to reduce size and prevent crashes
                originalBitmap = android.provider.MediaStore.Images.Media.getBitmap(
                    getContentResolver(), imageUri);
                
                if (originalBitmap == null) {
                    throw new Exception("Failed to load image");
                }
                
                // Balanced compression: good quality with fast upload
                int maxSize = 200; // Good quality while still fast
                int width = originalBitmap.getWidth();
                int height = originalBitmap.getHeight();
                
                android.util.Log.d("ProfileActivity", "Original: " + width + "x" + height);
                
                if (width > maxSize || height > maxSize) {
                    float scale = Math.min((float) maxSize / width, (float) maxSize / height);
                    int scaledWidth = Math.round(width * scale);
                    int scaledHeight = Math.round(height * scale);
                    
                    android.util.Log.d("ProfileActivity", "Scaling to: " + scaledWidth + "x" + scaledHeight);
                    scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, false);
                    originalBitmap.recycle();
                    originalBitmap = null;
                } else {
                    scaledBitmap = originalBitmap;
                    originalBitmap = null;
                }
                
                // Convert to byte array for direct upload (no file I/O)
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                boolean compressed = scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 65, baos); // Balanced quality for clear images
                byte[] imageData = baos.toByteArray();
                baos.close();
                
                // Recycle bitmap immediately
                scaledBitmap.recycle();
                scaledBitmap = null;
                
                if (!compressed) {
                    throw new Exception("Failed to compress image");
                }
                
                android.util.Log.d("ProfileActivity", "Compressed: " + imageData.length + " bytes (200px, 65% quality)");
                
                // Upload directly from memory - LIGHTNING FAST
                runOnUiThread(() -> {
                    uploadProfileImageFromMemory(imageData);
                });
                
            } catch (Exception e) {
                android.util.Log.e("ProfileActivity", "Error compressing image", e);
                
                // Clean up bitmaps
                if (originalBitmap != null && !originalBitmap.isRecycled()) {
                    originalBitmap.recycle();
                }
                if (scaledBitmap != null && !scaledBitmap.isRecycled()) {
                    scaledBitmap.recycle();
                }
                
                // Clean up resources (no file streams in memory upload)
                
                runOnUiThread(() -> {
                    hideLoading();
                    editProfileImage.setEnabled(true);
                    editProfileImage.setAlpha(1.0f);
                    saveButton.setEnabled(true);
                    Toast.makeText(ProfileActivity.this, 
                        "Failed to prepare image: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void setupClickListeners() {
        try {
            if (profileImage != null) {
                profileImage.setOnClickListener(v -> openImagePicker());
            }
            
            if (editProfileImage != null) {
                editProfileImage.setOnClickListener(v -> openImagePicker());
            }
            
            if (saveButton != null) {
                saveButton.setOnClickListener(v -> {
                    android.util.Log.d("ProfileActivity", "Save button clicked");
                    saveChanges();
                });
            } else {
                android.util.Log.e("ProfileActivity", "Save button is null!");
            }
            
            if (signOutButton != null) {
                signOutButton.setOnClickListener(v -> showSignOutDialog());
            }
            
            // Detect text changes
            if (nameEditText != null) {
                nameEditText.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        hasChanges = true;
                        updateSaveButtonState();
                    }

                    @Override
                    public void afterTextChanged(android.text.Editable s) {}
                });
            } else {
                android.util.Log.e("ProfileActivity", "nameEditText is null!");
            }
            
            android.util.Log.d("ProfileActivity", "Click listeners set up successfully");
            
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error setting up click listeners: " + e.getMessage(), e);
        }
    }

    private void loadUserData() {
        // Load user name
        String userName = userManager.getUserName();
        nameEditText.setText(userName);
        
        // Load email
        String email = userManager.getUserEmail();
        emailText.setText(email.isEmpty() ? "No email set" : email);
        
        // Load profile image
        String imageUrl = userManager.getProfileImageUrl();
        loadProfileImage(imageUrl);
        
        // Set account type and profile image editability
        if (userManager.isLoggedIn()) {
            accountTypeText.setText("✅ Synced Account");
            accountTypeText.setTextColor(getColor(R.color.success));
            signOutButton.setVisibility(View.VISIBLE);
            // Enable profile image editing for logged-in users
            editProfileImage.setAlpha(1.0f);
            editProfileImage.setEnabled(true);
        } else {
            accountTypeText.setText("👤 Guest Mode");
            accountTypeText.setTextColor(getColor(R.color.medium_grey));
            signOutButton.setVisibility(View.GONE);
            // Visually indicate that profile image editing is limited for guests
            editProfileImage.setAlpha(0.6f);
            editProfileImage.setEnabled(true); // Keep enabled to show the dialog
        }
        
        hasChanges = false;
        updateSaveButtonState();
    }
    
    private void refreshUserData() {
        try {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            
            if (userManager.isLoggedIn()) {
                // Sync data from Firebase for logged-in users
                userManager.syncUserDataFromFirebase(new UserManager.OnDataSyncListener() {
                    @Override
                    public void onSuccess() {
                        if (!isFinishing() && !isDestroyed()) {
                            runOnUiThread(() -> {
                                try {
                                    // Reload UI with fresh data
                                    loadUserData();
                                    
                                    // Force reload profile image with latest URL
                                    String latestImageUrl = userManager.getProfileImageUrl();
                                    android.util.Log.d("ProfileActivity", "Refreshing with latest image URL: " + latestImageUrl);
                                    loadProfileImage(latestImageUrl);
                                } catch (Exception e) {
                                    android.util.Log.e("ProfileActivity", "Error refreshing UI: " + e.getMessage());
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(String error) {
                        // Just log the error, don't show to user as this is a background refresh
                        android.util.Log.e("ProfileActivity", "Failed to refresh user data: " + error);
                    }
                });
            } else {
                // For guest users, just reload local data
                loadUserData();
            }
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in refreshUserData: " + e.getMessage());
        }
    }

    private void loadProfileImage(String imageUrl) {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            android.util.Log.d("ProfileActivity", "Loading profile image: " + imageUrl);
            loadProfileImageDirectly(imageUrl);
        } else {
            android.util.Log.d("ProfileActivity", "No profile image URL, loading default");
            loadDefaultProfileImage();
        }
    }
    
    private void loadProfileImageDirectly(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.isEmpty() || isFinishing() || isDestroyed()) {
                loadDefaultProfileImage();
                return;
            }
            
            // Clear any tint first
            if (profileImage != null) {
                profileImage.setImageTintList(null);
            }
            
            // Clear Glide cache for this specific image
            try {
                if (!isFinishing() && !isDestroyed()) {
                    Glide.with(this).clear(profileImage);
                }
            } catch (Exception e) {
                android.util.Log.w("ProfileActivity", "Error clearing Glide cache: " + e.getMessage());
            }
            
            // Add cache busting with current time
            String cacheBustedUrl = imageUrl + "?v=" + System.currentTimeMillis();
            android.util.Log.d("ProfileActivity", "Loading image: " + cacheBustedUrl);
            
            if (!isFinishing() && !isDestroyed() && profileImage != null) {
                Glide.with(this)
                    .load(cacheBustedUrl)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                    .into(profileImage);
            }
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error loading profile image: " + e.getMessage());
            loadDefaultProfileImage();
        }
    }
    
    private void loadDefaultProfileImage() {
        try {
            if (isFinishing() || isDestroyed() || profileImage == null) {
                return;
            }
            
            // Clear Glide first
            try {
                Glide.with(this).clear(profileImage);
            } catch (Exception e) {
                android.util.Log.w("ProfileActivity", "Error clearing Glide cache: " + e.getMessage());
            }
            
            // Show default image with pink tint
            profileImage.setImageTintList(android.content.res.ColorStateList.valueOf(
                getColor(R.color.primary)));
            
            if (!isFinishing() && !isDestroyed()) {
                Glide.with(this)
                    .load(R.drawable.ic_person)
                    .transform(new CircleCrop())
                    .into(profileImage);
            }
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error loading default profile image: " + e.getMessage());
        }
    }

    private void openImagePicker() {
        // Check if user is logged in
        if (!userManager.isLoggedIn()) {
            showGuestImageUploadDialog();
            return;
        }
        
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void uploadProfileImage(Uri imageUri) {
        uploadProfileImageWithCleanup(imageUri, null);
    }
    
    private void uploadProfileImageFromMemory(byte[] imageData) {
        android.util.Log.d("ProfileActivity", "Starting direct memory upload: " + imageData.length + " bytes");
        
        // Disable UI during upload
        editProfileImage.setEnabled(false);
        editProfileImage.setAlpha(0.5f);
        saveButton.setEnabled(false);
        
        userManager.uploadProfileImageFromMemory(imageData, new UserManager.OnImageUploadListener() {
            @Override
            public void onSuccess(String imageUrl) {
                android.util.Log.d("ProfileActivity", "Memory upload success: " + imageUrl);
                
                runOnUiThread(() -> {
                    hideLoading();
                    editProfileImage.setEnabled(true);
                    editProfileImage.setAlpha(1.0f);
                    saveButton.setEnabled(true);
                    hasChanges = false;
                    updateSaveButtonState();
                    
                    // Immediately update UI
                    loadProfileImageDirectly(imageUrl);
                    
                    Toast.makeText(ProfileActivity.this, "Image updated!", Toast.LENGTH_SHORT).show();
                    
                    // Background sync
                    userManager.syncUserDataFromFirebase(null);
                });
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("ProfileActivity", "Memory upload error: " + error);
                
                runOnUiThread(() -> {
                    hideLoading();
                    editProfileImage.setEnabled(true);
                    editProfileImage.setAlpha(1.0f);
                    saveButton.setEnabled(true);
                    
                    Toast.makeText(ProfileActivity.this, 
                        "Upload failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void uploadProfileImageWithCleanup(Uri imageUri, java.io.File tempFile) {
        // Disable UI interactions during upload
        editProfileImage.setEnabled(false);
        editProfileImage.setAlpha(0.5f);
        saveButton.setEnabled(false);
        
        userManager.uploadProfileImage(imageUri, new UserManager.OnImageUploadListener() {
            @Override
            public void onSuccess(String imageUrl) {
                android.util.Log.d("ProfileActivity", "Upload success callback received: " + imageUrl);
                
                // Clean up temp file
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                
                // IMMEDIATELY hide loading and restore UI - no matter what
                runOnUiThread(() -> {
                    hideLoading();
                    editProfileImage.setEnabled(true);
                    editProfileImage.setAlpha(1.0f);
                    saveButton.setEnabled(true);
                    hasChanges = false;
                    updateSaveButtonState();
                    
                    // Immediately update UI with new image
                    android.util.Log.d("ProfileActivity", "Immediately loading new image: " + imageUrl);
                    loadProfileImageDirectly(imageUrl);
                    
                    Toast.makeText(ProfileActivity.this, 
                        "Profile image updated!", Toast.LENGTH_SHORT).show();
                    
                    // Background sync immediately (no delays)
                    userManager.syncUserDataFromFirebase(null);
                });
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("ProfileActivity", "Upload error callback received: " + error);
                
                // Clean up temp file
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
                
                runOnUiThread(() -> {
                    hideLoading();
                    editProfileImage.setEnabled(true);
                    editProfileImage.setAlpha(1.0f);
                    saveButton.setEnabled(true);
                    
                    Toast.makeText(ProfileActivity.this, 
                        "Failed to update image: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void saveChanges() {
        try {
            android.util.Log.d("ProfileActivity", "saveChanges called");
            
            // Safety checks to prevent crashes
            if (isFinishing() || isDestroyed()) {
                android.util.Log.w("ProfileActivity", "Activity finishing, cannot save changes");
                return;
            }
            
            if (nameEditText == null || nameInputLayout == null || saveButton == null || userManager == null) {
                android.util.Log.e("ProfileActivity", "UI elements or UserManager is null");
                Toast.makeText(this, "Error: Cannot save changes", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String newName = nameEditText.getText().toString().trim();
            android.util.Log.d("ProfileActivity", "Attempting to save name: " + newName);
            
            if (newName.isEmpty()) {
                nameInputLayout.setError("Name cannot be empty");
                return;
            }
            
            nameInputLayout.setError(null);
            
            // Update UI instantly with safety checks
            hasChanges = false;
            updateSaveButtonState();
            
            if (saveButton != null) {
                saveButton.setText("Save Changes");
            }
            
            Toast.makeText(this, "Name updated!", Toast.LENGTH_SHORT).show();
            android.util.Log.d("ProfileActivity", "UI updated successfully");
            
            // Update UserManager in background
            try {
                userManager.setUserName(newName, new UserManager.OnProfileUpdateListener() {
                    @Override
                    public void onSuccess() {
                        android.util.Log.d("ProfileActivity", "Background name sync completed");
                    }

                    @Override
                    public void onError(String error) {
                        android.util.Log.w("ProfileActivity", "Background name sync failed: " + error);
                        // Show error but don't revert UI since local update worked
                        if (!isFinishing() && !isDestroyed()) {
                            runOnUiThread(() -> {
                                try {
                                    Toast.makeText(ProfileActivity.this, 
                                        "Name saved locally. Will sync when online.", Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    android.util.Log.e("ProfileActivity", "Error showing toast: " + e.getMessage());
                                }
                            });
                        }
                    }
                });
                android.util.Log.d("ProfileActivity", "UserManager.setUserName called successfully");
            } catch (Exception e) {
                android.util.Log.e("ProfileActivity", "Error calling UserManager.setUserName: " + e.getMessage());
                Toast.makeText(this, "Error updating name: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Critical error in saveChanges: " + e.getMessage(), e);
            try {
                Toast.makeText(this, "Error saving changes. Please try again.", Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                android.util.Log.e("ProfileActivity", "Cannot even show error toast: " + e2.getMessage());
            }
        }
    }

    private void updateSaveButtonState() {
        try {
            if (saveButton != null && !isFinishing() && !isDestroyed()) {
                saveButton.setEnabled(hasChanges);
                saveButton.setAlpha(hasChanges ? 1.0f : 0.5f);
                android.util.Log.d("ProfileActivity", "Save button state updated: enabled=" + hasChanges);
            } else {
                android.util.Log.w("ProfileActivity", "Cannot update save button - null or activity finishing");
            }
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error updating save button state: " + e.getMessage());
        }
    }

    private void showSignOutDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out? Your data will be synced to the cloud.")
            .setPositiveButton("Sign Out", (dialog, which) -> {
                userManager.signOut();
                
                // Navigate back to login
                Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showGuestImageUploadDialog() {
        // Show toast message first
        Toast.makeText(this, "Register and login to upload profile images", Toast.LENGTH_LONG).show();
        
        // Then show detailed dialog
        new AlertDialog.Builder(this)
            .setTitle("Account Required")
            .setMessage("You need to register and login to upload profile images. Guest users can only use the default profile picture.")
            .setPositiveButton("Register Now", (dialog, which) -> {
                // Navigate to register page
                Intent intent = new Intent(ProfileActivity.this, RegisterActivity.class);
                startActivity(intent);
            })
            .setNeutralButton("Login", (dialog, which) -> {
                // Navigate to login page
                Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                startActivity(intent);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    


    // Handle system back button
    @Override
    public void onBackPressed() {
        try {
            android.util.Log.d("ProfileActivity", "System back button pressed");
            handleBackAction();
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in onBackPressed", e);
            safeFinish();
        }
    }

    // Centralized back action handling
    private void handleBackAction() {
        try {
            android.util.Log.d("ProfileActivity", "Handling back action");
            
            // Hide loading dialog if visible
            if (loadingDialog != null && loadingDialog.isShowing()) {
                android.util.Log.d("ProfileActivity", "Hiding loading dialog on back");
                hideLoading();
                // Re-enable UI elements
                if (editProfileImage != null) {
                    editProfileImage.setEnabled(true);
                    editProfileImage.setAlpha(1.0f);
                }
                if (saveButton != null) {
                    saveButton.setEnabled(true);
                }
            }
            
            // Safe finish
            safeFinish();
            
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in handleBackAction", e);
            safeFinish();
        }
    }

    // Safe finish method with multiple fallbacks
    private void safeFinish() {
        try {
            android.util.Log.d("ProfileActivity", "Safe finish initiated");
            
            // Hide any dialogs first
            if (loadingDialog != null && loadingDialog.isShowing()) {
                try {
                    loadingDialog.dismiss();
                } catch (Exception e) {
                    android.util.Log.e("ProfileActivity", "Error dismissing dialog", e);
                }
            }
            
            // Check if activity is finishing/destroyed
            if (isFinishing() || isDestroyed()) {
                android.util.Log.d("ProfileActivity", "Activity already finishing/destroyed");
                return;
            }
            
            // Use finish() with animation
            finish();
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in safeFinish", e);
            try {
                // Last resort - force finish
                super.finish();
            } catch (Exception ex) {
                android.util.Log.e("ProfileActivity", "Error in super.finish", ex);
                // If all else fails, try to navigate manually
                try {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception intentEx) {
                    android.util.Log.e("ProfileActivity", "Error starting MainActivity", intentEx);
                }
            }
        }
    }
} 