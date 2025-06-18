package com.example.mobiledegreefinalproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ForgotPasswordActivity extends AppCompatActivity {

    private LinearLayout emailFormLayout;
    private LinearLayout confirmationLayout;
    private TextInputLayout emailInputLayout;
    private TextInputEditText emailEditText;
    private Button sendEmailButton;
    private TextView backToLoginText;
    private TextView resendEmailText;
    private TextView confirmationEmail;
    private Button helpButton;
    private Button debugButton;
    
    private UserManager userManager;
    private android.os.Handler resendTimerHandler;
    private Runnable resendTimerRunnable;
    private int resendCountdown = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        initViews();
        setupClickListeners();
        
        userManager = UserManager.getInstance(this);
    }

    private void initViews() {
        emailFormLayout = findViewById(R.id.email_form_layout);
        confirmationLayout = findViewById(R.id.confirmation_layout);
        emailInputLayout = findViewById(R.id.email_input_layout);
        emailEditText = findViewById(R.id.email_edit_text);
        sendEmailButton = findViewById(R.id.send_email_button);
        backToLoginText = findViewById(R.id.back_to_login_text);
        resendEmailText = findViewById(R.id.resend_email_button);
        confirmationEmail = findViewById(R.id.confirmation_email);
        helpButton = findViewById(R.id.help_button);
        debugButton = findViewById(R.id.debug_button);
    }

    private void setupClickListeners() {
        sendEmailButton.setOnClickListener(v -> handleSendEmail());
        
        backToLoginText.setOnClickListener(v -> {
            Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
        
        resendEmailText.setOnClickListener(v -> handleResendEmail());
        
        if (helpButton != null) {
            helpButton.setOnClickListener(v -> showTroubleshootingDialog());
        }
        
        if (debugButton != null) {
            debugButton.setOnClickListener(v -> {
                Intent intent = new Intent(ForgotPasswordActivity.this, PasswordResetTestActivity.class);
                startActivity(intent);
            });
        }
    }

    private void handleSendEmail() {
        String email = emailEditText.getText().toString().trim();

        // Reset error
        emailInputLayout.setError(null);

        // Validation
        if (email.isEmpty()) {
            emailInputLayout.setError("Email is required");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.setError("Please enter a valid email address");
            return;
        }

        // Disable button and show loading state
        sendEmailButton.setEnabled(false);
        sendEmailButton.setText("Sending...");
        
        // First check if user exists, then send reset email
        userManager.checkUserExists(email, new UserManager.OnAuthCompleteListener() {
            @Override
            public void onSuccess() {
                // User exists, now send reset email
                userManager.resetPassword(email, new UserManager.OnAuthCompleteListener() {
                    @Override
                    public void onSuccess() {
                        showConfirmationView(email);
                    }

                    @Override
                    public void onError(String error) {
                        sendEmailButton.setEnabled(true);
                        sendEmailButton.setText("Send Reset Email");
                        
                        // Show helpful error dialog instead of just input error
                        showErrorDialog("Reset Email Failed", error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                sendEmailButton.setEnabled(true);
                sendEmailButton.setText("Send Reset Email");
                
                // Show helpful dialog for user not found
                showUserNotFoundDialog(email);
            }
        });
    }

    private void handleResendEmail() {
        if (resendCountdown > 0) {
            Toast.makeText(this, "Please wait " + resendCountdown + " seconds before resending", 
                Toast.LENGTH_SHORT).show();
            return;
        }

        String email = confirmationEmail.getText().toString();
        
        // Disable button temporarily to prevent spam
        if (resendEmailText != null) {
            resendEmailText.setEnabled(false);
            resendEmailText.setText("Sending...");
        }
        
        userManager.resetPassword(email, new UserManager.OnAuthCompleteListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(ForgotPasswordActivity.this, 
                    "Reset email sent again to " + email + ". Check your inbox!", Toast.LENGTH_LONG).show();
                
                // Start 30-second countdown
                startResendCooldown(30);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ForgotPasswordActivity.this, 
                    "Failed to resend email: " + error, Toast.LENGTH_LONG).show();
                
                // Re-enable button immediately on error
                if (resendEmailText != null) {
                    resendEmailText.setEnabled(true);
                    resendEmailText.setText("Resend Email");
                }
            }
        });
    }

    private void startResendCooldown(int seconds) {
        resendCountdown = seconds;
        if (resendEmailText != null) {
            resendEmailText.setEnabled(false);
        }
        
        // Initialize timer handler if not exists
        if (resendTimerHandler == null) {
            resendTimerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        
        // Create timer runnable
        resendTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (resendCountdown > 0 && resendEmailText != null) {
                    // Update button text with countdown
                    resendEmailText.setText("Resend Email (" + resendCountdown + "s)");
                    resendCountdown--;
                    
                    // Continue countdown
                    resendTimerHandler.postDelayed(this, 1000);
                } else if (resendEmailText != null) {
                    // Countdown finished, enable button
                    resendEmailText.setEnabled(true);
                    resendEmailText.setText("Resend Email");
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
        if (resendEmailText != null) {
            resendEmailText.setEnabled(true);
            resendEmailText.setText("Resend Email");
        }
    }

    private void showConfirmationView(String email) {
        emailFormLayout.setVisibility(View.GONE);
        confirmationLayout.setVisibility(View.VISIBLE);
        confirmationEmail.setText(email);
        
        Toast.makeText(this, "Password reset email sent! Check your inbox.", Toast.LENGTH_LONG).show();
    }

    private void showErrorDialog(String title, String message) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message + "\n\nTroubleshooting tips:\n" +
                    "• Check your internet connection\n" +
                    "• Check your spam/junk folder\n" +
                    "• Make sure you entered the correct email\n" +
                    "• Wait a few minutes before trying again")
                .setPositiveButton("Try Again", (dialog, which) -> {
                    // Allow user to try again
                })
                .setNeutralButton("Contact Support", (dialog, which) -> {
                    // You could add contact support functionality here
                    Toast.makeText(this, "Please contact app support if the problem persists", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showUserNotFoundDialog(String email) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Account Not Found")
                .setMessage("No account found with the email address:\n" + email + 
                    "\n\nThis could mean:\n" +
                    "• You entered the wrong email address\n" +
                    "• You haven't created an account yet\n" +
                    "• You signed up with a different email")
                .setPositiveButton("Try Different Email", (dialog, which) -> {
                    // Clear email field for retry
                    emailEditText.setText("");
                    emailEditText.requestFocus();
                })
                .setNeutralButton("Create Account", (dialog, which) -> {
                    // Navigate to register page
                    Intent intent = new Intent(ForgotPasswordActivity.this, RegisterActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTroubleshootingDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Not Receiving Reset Email?")
                .setMessage("If you're not receiving the password reset email, try these steps:\n\n" +
                    "📧 Check your spam/junk folder\n" +
                    "⏱️ Wait 5-10 minutes for delivery\n" +
                    "📱 Check if email notifications are enabled\n" +
                    "✉️ Make sure you entered the correct email\n" +
                    "🔄 Try resending the email\n" +
                    "📶 Check your internet connection\n\n" +
                    "Still having issues? Contact our support team.")
                .setPositiveButton("Resend Email", (dialog, which) -> {
                    handleResendEmail();
                })
                .setNeutralButton("Contact Support", (dialog, which) -> {
                    Toast.makeText(this, "Please contact app support for further assistance", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        stopResendCooldown();
        super.onDestroy();
    }
} 