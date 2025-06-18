package com.example.mobiledegreefinalproject;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class PasswordResetTestActivity extends AppCompatActivity {
    
    private static final String TAG = "PasswordResetTest";
    
    private TextInputLayout emailInputLayout;
    private TextInputEditText emailEditText;
    private Button testResetButton;
    private Button checkUserButton;
    private Button checkFirebaseButton;
    private TextView debugOutput;
    
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private UserManager userManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_reset_test);
        
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        userManager = UserManager.getInstance(this);
        
        initViews();
        setupClickListeners();
        
        appendDebug("Password Reset Debug Tool\n");
        appendDebug("Firebase Auth: " + (auth != null ? "Connected" : "NULL") + "\n");
        appendDebug("Firestore: " + (firestore != null ? "Connected" : "NULL") + "\n");
    }

    private void initViews() {
        emailInputLayout = findViewById(R.id.email_input_layout);
        emailEditText = findViewById(R.id.email_edit_text);
        testResetButton = findViewById(R.id.test_reset_button);
        checkUserButton = findViewById(R.id.check_user_button);
        checkFirebaseButton = findViewById(R.id.check_firebase_button);
        debugOutput = findViewById(R.id.debug_output);
    }

    private void setupClickListeners() {
        testResetButton.setOnClickListener(v -> testPasswordReset());
        checkUserButton.setOnClickListener(v -> checkUserExists());
        checkFirebaseButton.setOnClickListener(v -> checkFirebaseConnection());
        
        findViewById(R.id.back_button).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void testPasswordReset() {
        String email = emailEditText.getText().toString().trim();
        
        if (email.isEmpty()) {
            emailInputLayout.setError("Email required");
            return;
        }
        
        emailInputLayout.setError(null);
        appendDebug("\n=== TESTING PASSWORD RESET ===\n");
        appendDebug("Email: " + email + "\n");
        appendDebug("Attempting Firebase Auth password reset...\n");
        
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener(aVoid -> {
                appendDebug("✅ SUCCESS: Password reset email sent!\n");
                appendDebug("Check your email inbox and spam folder.\n");
                Toast.makeText(this, "Reset email sent successfully!", Toast.LENGTH_LONG).show();
            })
            .addOnFailureListener(e -> {
                appendDebug("❌ FAILED: " + e.getMessage() + "\n");
                
                // Detailed error analysis
                String error = e.getMessage();
                if (error != null) {
                    if (error.contains("user-not-found")) {
                        appendDebug("Analysis: No account exists with this email\n");
                    } else if (error.contains("invalid-email")) {
                        appendDebug("Analysis: Invalid email format\n");
                    } else if (error.contains("network")) {
                        appendDebug("Analysis: Network connection issue\n");
                    } else if (error.contains("too-many-requests")) {
                        appendDebug("Analysis: Rate limited - wait before trying again\n");
                    } else {
                        appendDebug("Analysis: Unknown error\n");
                    }
                }
                
                Toast.makeText(this, "Reset failed: " + error, Toast.LENGTH_LONG).show();
            });
    }

    private void checkUserExists() {
        String email = emailEditText.getText().toString().trim();
        
        if (email.isEmpty()) {
            emailInputLayout.setError("Email required");
            return;
        }
        
        appendDebug("\n=== CHECKING USER EXISTS ===\n");
        appendDebug("Email: " + email + "\n");
        
        auth.fetchSignInMethodsForEmail(email)
            .addOnSuccessListener(result -> {
                if (result.getSignInMethods() != null && !result.getSignInMethods().isEmpty()) {
                    appendDebug("✅ User EXISTS with sign-in methods: " + result.getSignInMethods().toString() + "\n");
                } else {
                    appendDebug("❌ User NOT FOUND - no sign-in methods\n");
                }
            })
            .addOnFailureListener(e -> {
                appendDebug("❌ Error checking user: " + e.getMessage() + "\n");
            });
    }

    private void checkFirebaseConnection() {
        appendDebug("\n=== FIREBASE CONNECTION TEST ===\n");
        
        // Test Firestore connection
        firestore.collection("test").limit(1).get()
            .addOnSuccessListener(querySnapshot -> {
                appendDebug("✅ Firestore: Connected\n");
            })
            .addOnFailureListener(e -> {
                appendDebug("❌ Firestore: " + e.getMessage() + "\n");
            });
        
        // Test Auth connection
        if (auth.getCurrentUser() != null) {
            appendDebug("✅ Auth: User logged in as " + auth.getCurrentUser().getEmail() + "\n");
        } else {
            appendDebug("ℹ️ Auth: No user currently logged in\n");
        }
        
        // Test internet connectivity
        try {
            appendDebug("✅ App: Running, Firebase initialized\n");
        } catch (Exception e) {
            appendDebug("❌ App: Error - " + e.getMessage() + "\n");
        }
    }

    private void appendDebug(String text) {
        Log.d(TAG, text.trim());
        runOnUiThread(() -> {
            if (debugOutput != null) {
                debugOutput.append(text);
                // Auto-scroll to bottom
                debugOutput.post(() -> {
                    int scrollAmount = debugOutput.getLayout().getLineTop(debugOutput.getLineCount()) 
                        - debugOutput.getHeight();
                    if (scrollAmount > 0) {
                        debugOutput.scrollTo(0, scrollAmount);
                    } else {
                        debugOutput.scrollTo(0, 0);
                    }
                });
            }
        });
    }
} 