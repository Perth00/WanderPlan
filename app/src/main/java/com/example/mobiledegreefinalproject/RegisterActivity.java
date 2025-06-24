package com.example.mobiledegreefinalproject;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout nameInputLayout;
    private TextInputLayout emailInputLayout;
    private TextInputLayout passwordInputLayout;
    private TextInputLayout confirmPasswordInputLayout;
    private TextInputEditText nameEditText;
    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private TextInputEditText confirmPasswordEditText;
    private ImageView passwordToggle;
    private ImageView confirmPasswordToggle;
    private Button registerButton;
    private Button skipButton;
    private TextView loginText;
    private ProgressBar loadingProgressBar;
    
    private UserManager userManager;
    
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupClickListeners();
        
        userManager = UserManager.getInstance(this);
    }

    private void initViews() {
        nameInputLayout = findViewById(R.id.name_input_layout);
        emailInputLayout = findViewById(R.id.email_input_layout);
        passwordInputLayout = findViewById(R.id.password_input_layout);
        confirmPasswordInputLayout = findViewById(R.id.confirm_password_input_layout);
        nameEditText = findViewById(R.id.name_edit_text);
        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        confirmPasswordEditText = findViewById(R.id.confirm_password_edit_text);
        passwordToggle = findViewById(R.id.password_toggle);
        confirmPasswordToggle = findViewById(R.id.confirm_password_toggle);
        registerButton = findViewById(R.id.register_button);
        skipButton = findViewById(R.id.skip_button);
        loginText = findViewById(R.id.login_text);
        loadingProgressBar = findViewById(R.id.loading_progress_bar);
    }

    private void setupClickListeners() {
        registerButton.setOnClickListener(v -> handleRegister());
        
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        confirmPasswordToggle.setOnClickListener(v -> toggleConfirmPasswordVisibility());
        
        skipButton.setOnClickListener(v -> handleSkip());
        
        loginText.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void handleRegister() {
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        // Reset errors
        nameInputLayout.setError(null);
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);
        confirmPasswordInputLayout.setError(null);

        // Validation
        if (name.isEmpty()) {
            nameInputLayout.setError("Full name is required");
            return;
        }
        
        if (email.isEmpty()) {
            emailInputLayout.setError("Email is required");
            return;
        }
        
        if (!isValidEmail(email)) {
            emailInputLayout.setError("Please enter a valid email address");
            return;
        }
        
        if (password.isEmpty()) {
            passwordInputLayout.setError("Password is required");
            return;
        }
        
        if (password.length() < 6) {
            passwordInputLayout.setError("Password must be at least 6 characters");
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            confirmPasswordInputLayout.setError("Passwords don't match");
            return;
        }

        showLoading(true);
        
        // Register with Firebase
        userManager.registerUser(email, password, name, new UserManager.OnAuthCompleteListener() {
            @Override
            public void onSuccess() {
                showLoading(false);
                Toast.makeText(RegisterActivity.this, 
                    "Registration successful! Please verify your email.", Toast.LENGTH_LONG).show();
                
                // Navigate to EmailVerificationActivity
                Intent intent = new Intent(RegisterActivity.this, EmailVerificationActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                showLoading(false);
                
                // Check if it's an email verification error (user created but email failed)
                if (error.contains("failed to send verification email")) {
                    Toast.makeText(RegisterActivity.this, 
                        "Account created! " + error, Toast.LENGTH_LONG).show();
                    
                    // Still navigate to verification page
                    Intent intent = new Intent(RegisterActivity.this, EmailVerificationActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                Toast.makeText(RegisterActivity.this, 
                    "Registration failed: " + error, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void handleSkip() {
        String name = nameEditText.getText().toString().trim();
        if (name.isEmpty()) {
            name = "Guest";
        }
        
        // Continue as guest
        userManager.continueAsGuest(name);
        
        Toast.makeText(this, "Welcome, " + name + "! You're using guest mode.", Toast.LENGTH_LONG).show();
        
        // Navigate to MainActivity
        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            loadingProgressBar.setVisibility(android.view.View.VISIBLE);
            registerButton.setEnabled(false);
            nameEditText.setEnabled(false);
            emailEditText.setEnabled(false);
            passwordEditText.setEnabled(false);
            confirmPasswordEditText.setEnabled(false);
            skipButton.setEnabled(false);
            loginText.setClickable(false);
        } else {
            loadingProgressBar.setVisibility(android.view.View.GONE);
            registerButton.setEnabled(true);
            nameEditText.setEnabled(true);
            emailEditText.setEnabled(true);
            passwordEditText.setEnabled(true);
            confirmPasswordEditText.setEnabled(true);
            skipButton.setEnabled(true);
            loginText.setClickable(true);
        }
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            passwordToggle.setImageResource(R.drawable.ic_visibility_off);
            isPasswordVisible = false;
        } else {
            passwordEditText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            passwordToggle.setImageResource(R.drawable.ic_visibility);
            isPasswordVisible = true;
        }
        passwordEditText.setSelection(passwordEditText.getText().length());
    }

    private void toggleConfirmPasswordVisibility() {
        if (isConfirmPasswordVisible) {
            confirmPasswordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            confirmPasswordToggle.setImageResource(R.drawable.ic_visibility_off);
            isConfirmPasswordVisible = false;
        } else {
            confirmPasswordEditText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            confirmPasswordToggle.setImageResource(R.drawable.ic_visibility);
            isConfirmPasswordVisible = true;
        }
        confirmPasswordEditText.setSelection(confirmPasswordEditText.getText().length());
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        // Basic email validation pattern
        String emailPattern = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email.matches(emailPattern);
    }
} 