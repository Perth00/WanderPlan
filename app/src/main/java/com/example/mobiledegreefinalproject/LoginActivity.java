package com.example.mobiledegreefinalproject;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends BaseActivity {

    private TextInputLayout emailInputLayout;
    private TextInputLayout passwordInputLayout;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private ImageView passwordToggle;
    private Button loginButton;
    private TextView forgotPasswordText;
    private TextView registerText;
    private TextView continueAsGuestText;
    private ProgressBar loadingProgressBar;
    
    private boolean isPasswordVisible = false;
    private UserManager userManager;
    private SyncPreferences syncPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setupClickListeners();
        
        userManager = UserManager.getInstance(this);
        syncPrefs = new SyncPreferences(this);
    }

    private void initViews() {
        emailInputLayout = findViewById(R.id.email_input_layout);
        passwordInputLayout = findViewById(R.id.password_input_layout);
        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        passwordToggle = findViewById(R.id.password_toggle);
        loginButton = findViewById(R.id.login_button);
        forgotPasswordText = findViewById(R.id.forgot_password_text);
        registerText = findViewById(R.id.register_text);
        continueAsGuestText = findViewById(R.id.continue_as_guest_text);
        loadingProgressBar = findViewById(R.id.loading_progress_bar);
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> handleLogin());
        
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        
        forgotPasswordText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });
        
        registerText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
        
        continueAsGuestText.setOnClickListener(v -> {
            handleGuestMode();
        });
        
        // Hidden debug option - long press to access Firebase test
        continueAsGuestText.setOnLongClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, FirebaseTestActivity.class);
            startActivity(intent);
            return true;
        });
    }

    private void handleLogin() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Basic validation
        if (email.isEmpty()) {
            emailInputLayout.setError("Email is required");
            return;
        }
        
        if (password.isEmpty()) {
            passwordInputLayout.setError("Password is required");
            return;
        }

        // Clear errors
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);

        showLoading(true);

        // Login with Firebase
        userManager.loginUser(email, password, new UserManager.OnAuthCompleteListener() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                
                // Check for trip sync after successful login
                checkTripSyncStatus();
            }

            @Override
            public void onError(String error) {
                showLoading(false);
                
                // Check if it's an email verification error
                if (error.contains("verify your email")) {
                    showEmailVerificationDialog(email, error);
                } else {
                Toast.makeText(LoginActivity.this, 
                    "Login failed: " + error, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            // Hide password
            passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            passwordToggle.setImageResource(R.drawable.ic_visibility_off);
            isPasswordVisible = false;
        } else {
            // Show password
            passwordEditText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            passwordToggle.setImageResource(R.drawable.ic_visibility);
            isPasswordVisible = true;
        }
        
        // Move cursor to end
        passwordEditText.setSelection(passwordEditText.getText().length());
    }
    
    private void handleGuestMode() {
        // Show confirmation dialog for guest mode
        new android.app.AlertDialog.Builder(this)
                .setTitle("Continue as Guest")
                .setMessage("You can explore the app without an account, but your data won't be saved to the cloud. You can always create an account later.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    // Set guest mode in UserManager
                    userManager.continueAsGuest("Guest User");
                    
                    // Show success message
                    Toast.makeText(LoginActivity.this, "Welcome, Guest!", Toast.LENGTH_SHORT).show();
                    
                    // Navigate to MainActivity
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEmailVerificationDialog(String email, String error) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Email Verification Required")
                .setMessage(error + "\n\nWould you like us to resend the verification email to " + email + "?")
                .setPositiveButton("Resend Email", (dialog, which) -> {
                    resendVerificationEmail(email);
                })
                .setNeutralButton("Go to Verification", (dialog, which) -> {
                    // Try to log in temporarily to access verification page
                    userManager.loginUser(email, passwordEditText.getText().toString().trim(), 
                        new UserManager.OnAuthCompleteListener() {
                            @Override
                            public void onSuccess() {
                                // This shouldn't happen since email isn't verified, but just in case
                                navigateToMainActivity();
                            }

                            @Override
                            public void onError(String error) {
                                // Sign in with unverified email to access verification page
                                navigateToEmailVerification();
                            }
                        });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resendVerificationEmail(String email) {
        // Try to sign in temporarily to resend verification
        String password = passwordEditText.getText().toString().trim();
        
        userManager.loginUser(email, password, new UserManager.OnAuthCompleteListener() {
            @Override
            public void onSuccess() {
                // This shouldn't happen if email isn't verified
                navigateToMainActivity();
            }

            @Override
            public void onError(String error) {
                // Sign in failed due to verification, but we can still try to resend
                // We need to temporarily create the user session to send verification
                com.google.firebase.auth.FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            com.google.firebase.auth.FirebaseUser user = 
                                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                            if (user != null) {
                                userManager.sendEmailVerification(user, new UserManager.OnEmailVerificationListener() {
                                    @Override
                                    public void onSuccess() {
                                        Toast.makeText(LoginActivity.this, 
                                            "Verification email sent to " + email, Toast.LENGTH_LONG).show();
                                        // Sign out after sending email
                                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                                    }

                                    @Override
                                    public void onError(String error) {
                                        Toast.makeText(LoginActivity.this, 
                                            "Failed to send verification email: " + error, Toast.LENGTH_LONG).show();
                                        // Sign out after failure
                                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                                    }
                                });
                            }
                        } else {
                            Toast.makeText(LoginActivity.this, 
                                "Cannot resend verification email. Please try registering again.", 
                                Toast.LENGTH_LONG).show();
                        }
                    });
            }
        });
    }

    private void navigateToEmailVerification() {
        Intent intent = new Intent(LoginActivity.this, EmailVerificationActivity.class);
        startActivity(intent);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void checkTripSyncStatus() {
        // Check if user has local data and ask what to do (don't auto-sync)
        com.example.mobiledegreefinalproject.repository.TripRepository repo = 
            com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
        
        new Thread(() -> {
            try {
                java.util.List<com.example.mobiledegreefinalproject.database.Trip> localTrips = repo.getAllTripsSync();
                runOnUiThread(() -> {
                    if (localTrips.size() > 0) {
                        // User has local data - ask what they want to do
                        showSyncChoiceDialog(localTrips.size());
                    } else {
                        // No local data - check if Firebase has data
                        loadCloudDataOnly();
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("LoginActivity", "Error checking local trips", e);
                runOnUiThread(() -> {
                    // On error, just proceed to main activity
                    navigateToMainActivity();
                });
            }
        }).start();
    }
    
    private void loadCloudDataOnly() {
        android.util.Log.d("LoginActivity", "No local data found, checking for cloud data");
        
        // Use the existing TripRepository to check for Firebase data
        com.example.mobiledegreefinalproject.repository.TripRepository repo = 
            com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
        
        // Show loading dialog
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setTitle("☁️ Loading Cloud Data");
        progressDialog.setMessage("Checking for your data in Firebase...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        repo.fetchTripsFromFirebase(new com.example.mobiledegreefinalproject.repository.TripRepository.OnTripSyncListener() {
            @Override
            public void onSuccess() {
                // Check how many trips were loaded
                new Thread(() -> {
                    try {
                        java.util.List<com.example.mobiledegreefinalproject.database.Trip> trips = repo.getAllTripsSync();
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            if (trips.size() > 0) {
                                showCloudDataLoadedDialog(trips.size(), 0);
                            } else {
                                navigateToMainActivity();
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            navigateToMainActivity();
                        });
                    }
                }).start();
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    android.util.Log.w("LoginActivity", "No cloud data found: " + error);
                    navigateToMainActivity();
                });
            }
        });
    }
    
    private void clearLocalTripsAndLoadFirebaseTrips() {
        android.util.Log.d("LoginActivity", "Clearing local data and loading cloud data in JSON format");
        
        // Show loading dialog
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setTitle("🗑️ Clearing Local Data");
        progressDialog.setMessage("Deleting local trips and budget data...");
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.show();
        
        // First clear all local data
        new Thread(() -> {
            try {
                progressDialog.setMessage("🗑️ Clearing local trips...");
                
                // Clear local trips and activities
                com.example.mobiledegreefinalproject.repository.TripRepository repo = 
                    com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
                repo.clearAllLocalTrips();
                
                progressDialog.setProgress(30);
                progressDialog.setMessage("🗑️ Clearing budget data...");
                
                // Clear budget data
                UserManager userManager = UserManager.getInstance(this);
                userManager.deleteTripBudgetRecords(-1, new UserManager.OnBudgetSyncListener() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            progressDialog.setProgress(60);
                            progressDialog.setMessage("☁️ Loading cloud data...");
                            loadCloudData(progressDialog);
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        android.util.Log.w("LoginActivity", "Failed to clear budget data: " + error);
                        // Continue anyway
                        runOnUiThread(() -> {
                            progressDialog.setProgress(60);
                            progressDialog.setMessage("☁️ Loading cloud data...");
                            loadCloudData(progressDialog);
                        });
                    }
                    
                    @Override
                    public void onSyncRequired(int localBudgetCount, int firebaseBudgetCount) {
                        // Not relevant for clearing
                    }
                });
                
            } catch (Exception e) {
                android.util.Log.e("LoginActivity", "Error clearing local data", e);
                runOnUiThread(() -> {
                    progressDialog.setProgress(60);
                    progressDialog.setMessage("☁️ Loading cloud data...");
                    loadCloudData(progressDialog);
                });
            }
        }).start();
    }
    
    private void loadCloudData(android.app.ProgressDialog progressDialog) {
        progressDialog.setTitle("☁️ Loading Cloud Data");
        progressDialog.setMessage("Retrieving your data from Firebase...");
        progressDialog.setProgress(70);
        
        // Use the new DataRetrievalService to get JSON data
        DataRetrievalService retrievalService = new DataRetrievalService(this);
        retrievalService.retrieveDataFromFirebase(new DataRetrievalService.OnRetrievalCompleteListener() {
            @Override
            public void onProgressUpdate(int progress, String message) {
                runOnUiThread(() -> {
                    // Adjust progress to account for the clearing phase (already at 70%)
                    int adjustedProgress = 70 + (progress * 30 / 100);
                    progressDialog.setProgress(adjustedProgress);
                    progressDialog.setMessage(message);
                });
            }
            
            @Override
            public void onSuccess(int tripsRetrieved, int activitiesRetrieved) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    
                    if (tripsRetrieved > 0) {
                        showCloudDataLoadedDialog(tripsRetrieved, activitiesRetrieved);
                    } else {
                        // No cloud data found, proceed to main activity
                        showNoCloudDataDialog();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showCloudLoadErrorDialog(error);
                });
            }
        });
    }
    
    private void showNoCloudDataDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("ℹ️ No Cloud Data")
                .setMessage("🗑️ Local data has been cleared.\n\n" +
                           "ℹ️ No cloud data found to restore.\n\n" +
                           "You can start creating new trips!")
                .setPositiveButton("Continue", (dialog, which) -> {
                    navigateToMainActivity();
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }
    
    private void showCloudDataLoadedDialog(int tripsLoaded, int activitiesLoaded) {
        // Build message without showing "Activities: 0" when there are no activities
        String activitiesText = activitiesLoaded > 0 ? "\n📝 Activities: " + activitiesLoaded : "";
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("✅ Cloud Data Loaded")
                .setMessage("🎉 Successfully loaded your data from the cloud!\n\n" +
                           "📊 Data restored from Firebase JSON:\n" +
                           "🧳 Trips: " + tripsLoaded + 
                           activitiesText + "\n\n" +
                           "Your local database has been updated with your cloud data.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    navigateToMainActivity();
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }
    
    private void showCloudLoadErrorDialog(String error) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("❌ Cloud Load Failed")
                .setMessage("⚠️ There was an issue loading your cloud data:\n\n" + error + 
                           "\n\nYou can:\n" +
                           "• Try again\n" +
                           "• Continue with local data\n" +
                           "• Your local data is still available")
                .setPositiveButton("Try Again", (dialog, which) -> {
                    clearLocalTripsAndLoadFirebaseTrips();
                })
                .setNegativeButton("Continue with Local", (dialog, which) -> {
                    navigateToMainActivity();
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
    
    private void showSyncPromptDialog() {
        // Check if user has disabled sync on login
        if (!syncPrefs.shouldSyncOnLogin()) {
            navigateToMainActivity();
            return;
        }
        
        // Check if user has local data
        new Thread(() -> {
            com.example.mobiledegreefinalproject.repository.TripRepository repo = 
                com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
            
            java.util.List<com.example.mobiledegreefinalproject.database.Trip> localTrips = repo.getAllTripsSync();
            
            runOnUiThread(() -> {
                if (localTrips.isEmpty()) {
                    // No local data, just load from cloud
                    clearLocalTripsAndLoadFirebaseTrips();
                } else {
                    // Has local data, show sync choice dialog
                    showSyncChoiceDialog(localTrips.size());
                }
            });
        }).start();
    }
    
    private void showSyncChoiceDialog(int localTripsCount) {
        String message = "📱 Welcome back!\n\n" +
                        "You have " + localTripsCount + " trips stored locally.\n\n" +
                        "Choose what to do with your data:\n\n" +
                        "🔄 BACKUP TO CLOUD:\n" +
                        "• Upload your local trips to Firebase\n" +
                        "• Upload all activity images to Firebase Storage\n" +
                        "• Your local data stays safe on device\n" +
                        "• Create cloud backup for access anywhere\n\n" +
                        "☁️ USE CLOUD ONLY:\n" +
                        "• Delete ALL local trips & activities\n" +
                        "• Download cloud data (if any exists)\n" +
                        "• ⚠️ WARNING: Local data will be lost!";
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("🔄 Data Sync Choice")
                .setMessage(message)
                .setPositiveButton("🔄 Backup to Cloud", (dialog, which) -> {
                    startDataSync();
                })
                .setNegativeButton("☁️ Use Cloud Only", (dialog, which) -> {
                    showClearLocalDataConfirmation();
                })
                .setCancelable(false)
                .show();
    }
    
    private void showClearLocalDataConfirmation() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("⚠️ Delete Local Data?")
                .setMessage("🗑️ This will permanently delete:\n\n" +
                           "• All local trips\n" +
                           "• All local activities  \n" +
                           "• All local budget data\n\n" +
                           "Then download your cloud data (if any).\n\n" +
                           "Are you sure you want to continue?")
                .setPositiveButton("🗑️ Yes, Delete Local Data", (dialog, which) -> {
                    clearLocalTripsAndLoadFirebaseTrips();
                })
                .setNegativeButton("❌ Cancel", (dialog, which) -> {
                    showSyncChoiceDialog(-1); // Go back to sync choice
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
    
    private void showUseCloudOnlyConfirmDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("⚠️ Confirm Cloud Only")
                .setMessage("This will clear your local data and use only cloud data.\n\nAre you sure you want to proceed?")
                .setPositiveButton("Yes, Use Cloud Only", (dialog, which) -> {
                    clearLocalTripsAndLoadFirebaseTrips();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    showSyncPromptDialog();
                })
                .show();
    }
    
    private void startDataSync() {
        // Show progress dialog
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setTitle("🔄 Creating Cloud Backup");
        progressDialog.setMessage("Uploading your trips to Firebase (local data preserved)...");
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.show();
        
        // Create and start sync service
        DataSyncService syncService = new DataSyncService(this);
        syncService.syncLocalDataToFirebase(new DataSyncService.OnSyncCompleteListener() {
            @Override
            public void onProgressUpdate(int progress, String message) {
                runOnUiThread(() -> {
                    progressDialog.setProgress(progress);
                    progressDialog.setMessage(message);
                });
            }
            
            @Override
            public void onSuccess(int tripsSynced, int activitiesSynced) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showSyncSuccessDialog(tripsSynced, activitiesSynced);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    showSyncErrorDialog(error);
                });
            }
        });
    }
    
    private void showSyncSuccessDialog(int tripsSynced, int activitiesSynced) {
        showBackupCompleteDialog(tripsSynced, activitiesSynced);
    }
    
    private void showSyncErrorDialog(String error) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("⚠️ Sync Failed")
                .setMessage("An error occurred during data sync:\n\n" + error)
                .setPositiveButton("Try Again Later", (dialog, which) -> navigateToMainActivity())
                .setCancelable(false)
                .show();
    }
    
    private void showBackupCompleteDialog(int tripsSynced, int activitiesSynced) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
            LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_backup_complete, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        TextView tripsSyncedText = dialogView.findViewById(R.id.tv_trips_synced);
        TextView activitiesSyncedText = dialogView.findViewById(R.id.tv_activities_synced);
        Button continueButton = dialogView.findViewById(R.id.btn_continue);

        tripsSyncedText.setText("✈️ Trips: " + tripsSynced);
        activitiesSyncedText.setText("📝 Activities: " + activitiesSynced);

        AlertDialog dialog = builder.create();

        continueButton.setOnClickListener(v -> {
            dialog.dismiss();
            navigateToMainActivity();
        });

        dialog.show();
    }
    
    private void showComingSoonSyncDialog() {
        // This method is now replaced by the actual sync functionality above
        startDataSync();
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            loadingProgressBar.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
            emailEditText.setEnabled(false);
            passwordEditText.setEnabled(false);
            registerText.setClickable(false);
            forgotPasswordText.setClickable(false);
            continueAsGuestText.setClickable(false);
        } else {
            loadingProgressBar.setVisibility(View.GONE);
            loginButton.setEnabled(true);
            emailEditText.setEnabled(true);
            passwordEditText.setEnabled(true);
            registerText.setClickable(true);
            forgotPasswordText.setClickable(true);
            continueAsGuestText.setClickable(true);
        }
    }
} 