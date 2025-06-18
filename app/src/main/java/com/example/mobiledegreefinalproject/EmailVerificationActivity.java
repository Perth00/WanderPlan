package com.example.mobiledegreefinalproject;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EmailVerificationActivity extends AppCompatActivity {

    private TextView emailText;
    private TextView instructionText;
    private Button resendButton;
    private Button checkVerificationButton;
    private Button changeEmailButton;
    private UserManager userManager;
    private String userEmail;
    private Handler verificationCheckHandler;
    private Runnable verificationCheckRunnable;
    private Handler resendTimerHandler;
    private Runnable resendTimerRunnable;
    private int resendCountdown = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);

        initViews();
        setupUserManager();
        setupClickListeners();
        startAutoVerificationCheck();
        startResendCooldown(30); // Start with 30 seconds cooldown
    }

    private void initViews() {
        emailText = findViewById(R.id.email_text);
        instructionText = findViewById(R.id.instruction_text);
        resendButton = findViewById(R.id.resend_button);
        checkVerificationButton = findViewById(R.id.check_verification_button);
        changeEmailButton = findViewById(R.id.change_email_button);
    }

    private void setupUserManager() {
        userManager = UserManager.getInstance(this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userEmail = user.getEmail();
            emailText.setText(userEmail);
            instructionText.setText("We've sent a verification link to " + userEmail + 
                ". Please check your email and click the verification link to activate your account.");
        } else {
            // No user logged in, go back to login
            navigateToLogin();
        }
    }

    private void setupClickListeners() {
        resendButton.setOnClickListener(v -> resendVerificationEmail());
        checkVerificationButton.setOnClickListener(v -> checkEmailVerification());
        changeEmailButton.setOnClickListener(v -> changeEmail());
    }

    private void startAutoVerificationCheck() {
        verificationCheckHandler = new Handler(Looper.getMainLooper());
        verificationCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkEmailVerificationSilently();
                // Check every 5 seconds
                verificationCheckHandler.postDelayed(this, 5000);
            }
        };
        // Start checking after 3 seconds
        verificationCheckHandler.postDelayed(verificationCheckRunnable, 3000);
    }

    private void stopAutoVerificationCheck() {
        if (verificationCheckHandler != null && verificationCheckRunnable != null) {
            verificationCheckHandler.removeCallbacks(verificationCheckRunnable);
        }
    }

    private void resendVerificationEmail() {
        if (resendCountdown > 0) {
            Toast.makeText(this, "Please wait " + resendCountdown + " seconds before resending", 
                Toast.LENGTH_SHORT).show();
            return;
        }

        resendButton.setEnabled(false);
        resendButton.setText("Sending...");

        userManager.resendEmailVerification(new UserManager.OnEmailVerificationListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(EmailVerificationActivity.this, 
                        "Verification email sent to " + userEmail, Toast.LENGTH_LONG).show();
                    
                    // Start 30-second countdown
                    startResendCooldown(30);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(EmailVerificationActivity.this, 
                        "Failed to send email: " + error, Toast.LENGTH_LONG).show();
                    resendButton.setEnabled(true);
                    resendButton.setText("Resend Email");
                });
            }
        });
    }

    private void startResendCooldown(int seconds) {
        resendCountdown = seconds;
        resendButton.setEnabled(false);
        
        // Initialize timer handler if not exists
        if (resendTimerHandler == null) {
            resendTimerHandler = new Handler(Looper.getMainLooper());
        }
        
        // Create timer runnable
        resendTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (resendCountdown > 0) {
                    // Update button text with countdown
                    resendButton.setText("Resend Email (" + resendCountdown + "s)");
                    resendCountdown--;
                    
                    // Continue countdown
                    resendTimerHandler.postDelayed(this, 1000);
                } else {
                    // Countdown finished, enable button
                    resendButton.setEnabled(true);
                    resendButton.setText("Resend Email");
                }
            }
        };
        
        // Start the countdown immediately
        resendTimerRunnable.run();
    }

    private void stopResendCooldown() {
        if (resendTimerHandler != null && resendTimerRunnable != null) {
            resendTimerHandler.removeCallbacks(resendTimerRunnable);
        }
        resendCountdown = 0;
        if (resendButton != null) {
            resendButton.setEnabled(true);
            resendButton.setText("Resend Email");
        }
    }

    private void checkEmailVerification() {
        checkVerificationButton.setEnabled(false);
        checkVerificationButton.setText("Checking...");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            user.reload().addOnCompleteListener(task -> {
                runOnUiThread(() -> {
                    checkVerificationButton.setEnabled(true);
                    checkVerificationButton.setText("Check Verification");
                    
                    if (user.isEmailVerified()) {
                        onEmailVerified();
                    } else {
                        Toast.makeText(this, 
                            "Email not verified yet. Please check your inbox and click the verification link.", 
                            Toast.LENGTH_LONG).show();
                    }
                });
            });
        } else {
            runOnUiThread(() -> {
                checkVerificationButton.setEnabled(true);
                checkVerificationButton.setText("Check Verification");
                navigateToLogin();
            });
        }
    }

    private void checkEmailVerificationSilently() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            user.reload().addOnCompleteListener(task -> {
                if (user.isEmailVerified()) {
                    runOnUiThread(this::onEmailVerified);
                }
            });
        }
    }

    private void onEmailVerified() {
        stopAutoVerificationCheck();
        stopResendCooldown();
        
        Toast.makeText(this, "Email verified successfully! Welcome to WanderPlan!", 
            Toast.LENGTH_LONG).show();
        
        // Navigate to MainActivity
        Intent intent = new Intent(EmailVerificationActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void changeEmail() {
        stopAutoVerificationCheck();
        FirebaseAuth.getInstance().signOut();
        navigateToRegister();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(EmailVerificationActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToRegister() {
        Intent intent = new Intent(EmailVerificationActivity.this, RegisterActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        stopAutoVerificationCheck();
        stopResendCooldown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Prevent going back during verification process
        Toast.makeText(this, "Please verify your email to continue", Toast.LENGTH_SHORT).show();
    }
} 