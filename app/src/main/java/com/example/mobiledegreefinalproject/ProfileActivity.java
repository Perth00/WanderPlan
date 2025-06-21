package com.example.mobiledegreefinalproject;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import android.widget.Button;
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

    private TextView profileTitle;
    private ImageView profileImage;
    private ImageView editProfileImage;
    private TextInputLayout nameInputLayout;
    private TextInputEditText nameEditText;
    private TextView emailText;
    private TextView accountTypeText;
    private Button saveButton;
    private Button goBackButton;
    private Button signOutButton;
    
    private UserManager userManager;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private boolean hasChanges = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
        setContentView(R.layout.activity_profile);
            
            // Hide the default action bar to avoid conflicts
            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }

        initViews();
        setupImagePicker();
        setupClickListeners();
        
        userManager = UserManager.getInstance(this);
        loadUserData();
            
            android.util.Log.d("ProfileActivity", "onCreate completed successfully");
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in onCreate", e);
            Toast.makeText(this, "Error loading profile. Please try again.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            // Refresh user data when returning to this activity
            loadUserData();
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in onResume", e);
        }
    }

    private void initViews() {
        try {
            profileTitle = findViewById(R.id.profile_title);
        profileImage = findViewById(R.id.profile_image);
        editProfileImage = findViewById(R.id.edit_profile_image);
        nameInputLayout = findViewById(R.id.name_input_layout);
        nameEditText = findViewById(R.id.name_edit_text);
        emailText = findViewById(R.id.email_text);
        accountTypeText = findViewById(R.id.account_type_text);
        saveButton = findViewById(R.id.save_button);
            goBackButton = findViewById(R.id.go_back_button);
        signOutButton = findViewById(R.id.sign_out_button);
            
            // Validate all critical UI elements are found
            StringBuilder missingViews = new StringBuilder();
            
            if (profileTitle == null) missingViews.append("profile_title, ");
            if (profileImage == null) missingViews.append("profile_image, ");
            if (editProfileImage == null) missingViews.append("edit_profile_image, ");
            if (nameInputLayout == null) missingViews.append("name_input_layout, ");
            if (nameEditText == null) missingViews.append("name_edit_text, ");
            if (emailText == null) missingViews.append("email_text, ");
            if (accountTypeText == null) missingViews.append("account_type_text, ");
            if (saveButton == null) missingViews.append("save_button, ");
            if (goBackButton == null) missingViews.append("go_back_button, ");
            if (signOutButton == null) missingViews.append("sign_out_button, ");
            
            if (missingViews.length() > 0) {
                String missing = missingViews.toString();
                android.util.Log.e("ProfileActivity", "Missing views: " + missing);
                throw new RuntimeException("Critical UI elements not found: " + missing);
            }
            
            android.util.Log.d("ProfileActivity", "All views initialized successfully");
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error initializing views", e);
            throw e;
        }
    }

    private void setupImagePicker() {
        try {
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                    try {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                                android.util.Log.d("ProfileActivity", "Image selected: " + imageUri);
                        uploadProfileImage(imageUri);
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("ProfileActivity", "Error handling image selection", e);
                        Toast.makeText(this, "Error processing selected image", Toast.LENGTH_SHORT).show();
                    }
                }
            );
            android.util.Log.d("ProfileActivity", "Image picker setup completed");
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error setting up image picker", e);
            }
    }

    private void setupClickListeners() {
        try {
            // Profile image click - open image picker
        profileImage.setOnClickListener(v -> openImagePicker());
        editProfileImage.setOnClickListener(v -> openImagePicker());
        
            // Save button
        saveButton.setOnClickListener(v -> saveChanges());
        
            // Go back button
            goBackButton.setOnClickListener(v -> goBackToPreviousPage());
            
            // Sign out button
        signOutButton.setOnClickListener(v -> showSignOutDialog());
        
            // Text change listener for name field
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
            
            android.util.Log.d("ProfileActivity", "Click listeners setup completed");
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error setting up click listeners", e);
        }
    }

    private void loadUserData() {
        try {
            // Load user data safely
        String email = userManager.getUserEmail();
            String name = userManager.getUserName();
            String profileImageUrl = userManager.getProfileImageUrl();
        
            if (email != null && !email.isEmpty()) {
                emailText.setText(email);
        
                // Show account status
            accountTypeText.setText("✅ Synced Account");
                accountTypeText.setTextColor(getResources().getColor(R.color.success, null));
                signOutButton.setVisibility(android.view.View.VISIBLE);
        } else {
                emailText.setText("Guest User");
            accountTypeText.setText("👤 Guest Mode");
                accountTypeText.setTextColor(getResources().getColor(R.color.medium_grey, null));
                signOutButton.setVisibility(android.view.View.GONE);
            }
            
            if (name != null && !name.isEmpty()) {
                nameEditText.setText(name);
            }
            
            // Load profile image
            if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                updateProfileImageDisplay(profileImageUrl);
            } else {
                loadDefaultProfileImage();
        }
        
        hasChanges = false;
        updateSaveButtonState();
            
            android.util.Log.d("ProfileActivity", "User data loaded successfully");
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error loading user data", e);
            // Set default values on error
            emailText.setText("Error loading data");
            nameEditText.setText("");
            loadDefaultProfileImage();
        }
    }

    private void loadDefaultProfileImage() {
        try {
            // Cache buster to force reload
            String cacheKey = "?cb=" + System.currentTimeMillis();
            
            Glide.with(this)
                .load(R.drawable.ic_person)
                .transform(new CircleCrop())
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(profileImage);
                
            android.util.Log.d("ProfileActivity", "Default profile image loaded");
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error loading default profile image", e);
            // Fallback: set image directly
            try {
                profileImage.setImageResource(R.drawable.ic_person);
            } catch (Exception e2) {
                android.util.Log.e("ProfileActivity", "Error setting fallback image", e2);
            }
        }
    }

    private void openImagePicker() {
        try {
            if (!userManager.isLoggedIn()) {
                showGuestImageUploadDialog();
                return;
            }
            
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
            
            android.util.Log.d("ProfileActivity", "Image picker launched");
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error opening image picker", e);
            Toast.makeText(this, "Error opening image picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadProfileImage(Uri imageUri) {
        try {
            if (!userManager.isLoggedIn()) {
                Toast.makeText(this, "Please log in to upload images", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();
            
            // Show uploading state
            editProfileImage.setImageResource(R.drawable.ic_settings);
            editProfileImage.setColorFilter(getResources().getColor(R.color.medium_grey, null));
            
            android.util.Log.d("ProfileActivity", "Starting image upload process");
            
            // Upload to Firebase with compression and proper handling
        userManager.uploadProfileImage(imageUri, new UserManager.OnImageUploadListener() {
            @Override
            public void onSuccess(String imageUrl) {
                    android.util.Log.d("ProfileActivity", "Image upload successful: " + imageUrl);
                    
                runOnUiThread(() -> {
                        try {
                            // Show success state
                            editProfileImage.setImageResource(R.drawable.ic_add);
                            editProfileImage.setColorFilter(getResources().getColor(R.color.success, null));
                            
                            // Update the displayed image
                            updateProfileImageDisplay(imageUrl);
                            
                            // Show success message
                            Toast.makeText(ProfileActivity.this, "Profile picture updated successfully!", Toast.LENGTH_SHORT).show();
                            
                            // Reset icon color after 2 seconds
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                try {
                                    editProfileImage.setColorFilter(getResources().getColor(R.color.white, null));
                                } catch (Exception e) {
                                    android.util.Log.e("ProfileActivity", "Error resetting icon color", e);
                                }
                            }, 2000);
                            
                        } catch (Exception e) {
                            android.util.Log.e("ProfileActivity", "Error updating UI after upload", e);
                        }
                });
            }

            @Override
            public void onError(String error) {
                    android.util.Log.e("ProfileActivity", "Image upload failed: " + error);
                    
                runOnUiThread(() -> {
                        try {
                            // Show error state
                            editProfileImage.setImageResource(R.drawable.ic_add);
                            editProfileImage.setColorFilter(getResources().getColor(R.color.white, null));
                            
                            Toast.makeText(ProfileActivity.this, "Failed to upload image: " + error, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            android.util.Log.e("ProfileActivity", "Error updating UI after upload error", e);
                        }
                });
            }
        });
            
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error in uploadProfileImage", e);
            Toast.makeText(this, "Error uploading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            
            // Reset icon state
            editProfileImage.setImageResource(R.drawable.ic_add);
            editProfileImage.setColorFilter(getResources().getColor(R.color.white, null));
        }
    }

    private void updateProfileImageDisplay(String imageUrl) {
        try {
            if (imageUrl != null && !imageUrl.isEmpty()) {
                android.util.Log.d("ProfileActivity", "Loading profile image from URL: " + imageUrl);
                
                // Cache buster to force reload
                String imageUrlWithCache = imageUrl + "?cb=" + System.currentTimeMillis();
                
                Glide.with(this)
                    .load(imageUrlWithCache)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(profileImage);
                    
                android.util.Log.d("ProfileActivity", "Profile image display updated");
            } else {
                android.util.Log.d("ProfileActivity", "No image URL provided, loading default");
                loadDefaultProfileImage();
            }
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error updating profile image display", e);
            loadDefaultProfileImage();
        }
    }

    private void saveChanges() {
        try {
        String newName = nameEditText.getText().toString().trim();
        
        if (newName.isEmpty()) {
            nameInputLayout.setError("Name cannot be empty");
            return;
        }
        
            // Clear any previous errors
        nameInputLayout.setError(null);
            
            // Save the name
        userManager.setUserName(newName);
        
        hasChanges = false;
        updateSaveButtonState();
        
            Toast.makeText(this, "Changes saved successfully!", Toast.LENGTH_SHORT).show();
            android.util.Log.d("ProfileActivity", "Profile changes saved: " + newName);
            
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error saving changes", e);
            Toast.makeText(this, "Error saving changes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSaveButtonState() {
        try {
        saveButton.setEnabled(hasChanges);
            saveButton.setAlpha(hasChanges ? 1.0f : 0.6f);
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error updating save button state", e);
        }
    }

    private void showSignOutDialog() {
        try {
        new AlertDialog.Builder(this)
            .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out", (dialog, which) -> {
                userManager.signOut();
                    Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show();
                
                    // Go back to login
                    Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            })
            .setNegativeButton("Cancel", null)
            .show();
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error showing sign out dialog", e);
        }
    }

    private void showGuestImageUploadDialog() {
        try {
            new AlertDialog.Builder(this)
                .setTitle("Account Required")
                .setMessage("You need to create an account to upload profile pictures. Would you like to create an account now?")
                .setPositiveButton("Create Account", (dialog, which) -> {
                    Intent intent = new Intent(this, RegisterActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error showing guest dialog", e);
        }
    }

    private void goBackToPreviousPage() {
        try {
            android.util.Log.d("ProfileActivity", "Go back button clicked");
            finish();
        } catch (Exception e) {
            android.util.Log.e("ProfileActivity", "Error going back", e);
            Toast.makeText(this, "Error going back", Toast.LENGTH_SHORT).show();
        }
    }
} 