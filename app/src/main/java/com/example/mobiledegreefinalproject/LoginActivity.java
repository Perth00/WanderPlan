package com.example.mobiledegreefinalproject;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout emailInputLayout;
    private TextInputLayout passwordInputLayout;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private ImageView passwordToggle;
    private Button loginButton;
    private TextView forgotPasswordText;
    private TextView registerText;
    private TextView continueAsGuestText;
    
    private boolean isPasswordVisible = false;
    private UserManager userManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setupClickListeners();
        
        userManager = UserManager.getInstance(this);
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

        // Disable button to prevent multiple submissions
        loginButton.setEnabled(false);
        
        // Login with Firebase
        userManager.loginUser(email, password, new UserManager.OnAuthCompleteListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                
                // Navigate to MainActivity
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                loginButton.setEnabled(true);
                
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
} 