package com.example.mobiledegreefinalproject;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class UserManager {
    private static final String TAG = "UserManager";
    private static final String PREFS_NAME = "WanderPlanPrefs";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_PROFILE_IMAGE_URL = "profile_image_url";
    private static final String KEY_IS_GUEST = "is_guest";
    
    private static UserManager instance;
    private Context context;
    private SharedPreferences prefs;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;
    
    private UserManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
        this.storage = FirebaseStorage.getInstance();
    }
    
    public static synchronized UserManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserManager(context);
        }
        return instance;
    }
    
    // Authentication state
    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }
    
    public boolean isGuest() {
        return prefs.getBoolean(KEY_IS_GUEST, false);
    }
    
    public void setGuestMode(boolean isGuest) {
        prefs.edit().putBoolean(KEY_IS_GUEST, isGuest).apply();
    }
    
    // User information getters
    public String getUserName() {
        if (isLoggedIn()) {
            // First try to get from local cache (which is synced from Firestore)
            String cachedName = prefs.getString(KEY_USER_NAME, null);
            if (cachedName != null && !cachedName.isEmpty()) {
                return cachedName;
            }
            // Fallback to Firebase Auth display name
            FirebaseUser user = auth.getCurrentUser();
            return user.getDisplayName() != null ? user.getDisplayName() : "User";
        } else {
            return prefs.getString(KEY_USER_NAME, "Guest");
        }
    }
    
    public String getUserEmail() {
        if (isLoggedIn()) {
            FirebaseUser user = auth.getCurrentUser();
            return user.getEmail();
        } else {
            return prefs.getString(KEY_USER_EMAIL, "");
        }
    }
    
    public String getProfileImageUrl() {
        if (isLoggedIn()) {
            // First try to get from local cache (which is synced from Firestore)
            String cachedImageUrl = prefs.getString(KEY_PROFILE_IMAGE_URL, null);
            Log.d(TAG, "Getting profile image URL - Cached: " + cachedImageUrl);
            if (cachedImageUrl != null && !cachedImageUrl.isEmpty()) {
                return cachedImageUrl;
            }
            // Fallback to Firebase Auth photo URL
            FirebaseUser user = auth.getCurrentUser();
            String authUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";
            Log.d(TAG, "Getting profile image URL - Auth fallback: " + authUrl);
            return authUrl;
        } else {
            String guestUrl = prefs.getString(KEY_PROFILE_IMAGE_URL, "");
            Log.d(TAG, "Getting profile image URL - Guest: " + guestUrl);
            return guestUrl;
        }
    }
    
    // Local data setters (for guest mode)
    public void setUserName(String name) {
        if (isLoggedIn()) {
            // Update local cache immediately for instant UI feedback
            prefs.edit().putString(KEY_USER_NAME, name).apply();
            // Update Firebase profile
            updateFirebaseProfile(name, null, null);
        } else {
            // Update local storage
            prefs.edit().putString(KEY_USER_NAME, name).apply();
        }
    }
    
    public void setUserName(String name, OnProfileUpdateListener listener) {
        // ALWAYS update local cache first for instant response
        prefs.edit().putString(KEY_USER_NAME, name).apply();
        Log.d(TAG, "Name updated locally instantly: " + name);
        
        if (isLoggedIn()) {
            // Update Firebase in background (fast callback)
            updateFirebaseProfile(name, null, listener);
        } else {
            // Guest mode - instant success
            if (listener != null) {
                // Call success immediately on main thread
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    listener.onSuccess();
                });
            }
        }
    }
    
    public void setUserEmail(String email) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply();
    }
    
    public void setProfileImageUrl(String url) {
        Log.d(TAG, "Setting profile image URL: " + url);
        // Always update local cache immediately for instant UI feedback
        prefs.edit().putString(KEY_PROFILE_IMAGE_URL, url).apply();
        Log.d(TAG, "Profile image URL saved to local cache");
        
        if (isLoggedIn()) {
            // Also update Firebase profile for logged-in users
            Log.d(TAG, "Updating Firebase profile with image URL");
            updateFirebaseProfile(null, url, null);
        }
    }
    
    // Firebase operations
    public void registerUser(String email, String password, String fullName, 
                           OnAuthCompleteListener listener) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        // Send email verification before allowing login
                        sendEmailVerification(user, new OnEmailVerificationListener() {
                            @Override
                            public void onSuccess() {
                                // Create user profile in Firestore
                                createUserProfile(user.getUid(), fullName, email);
                                setGuestMode(false);
                                Log.d(TAG, "Registration successful, email verification sent");
                                listener.onSuccess();
                            }

                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Failed to send verification email: " + error);
                                // Still allow registration but inform user about verification issue
                                createUserProfile(user.getUid(), fullName, email);
                                setGuestMode(false);
                                listener.onError("Account created but failed to send verification email: " + error);
                            }
                        });
                    } else {
                        listener.onError("Registration failed: User creation error");
                    }
                } else {
                    listener.onError(task.getException() != null ? 
                        task.getException().getMessage() : "Registration failed");
                }
            });
    }

    // Send email verification
    public void sendEmailVerification(FirebaseUser user, OnEmailVerificationListener listener) {
        if (user != null) {
            user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Email verification sent successfully");
                        listener.onSuccess();
                    } else {
                        String error = task.getException() != null ? 
                            task.getException().getMessage() : "Failed to send verification email";
                        Log.e(TAG, "Email verification failed: " + error);
                        listener.onError(error);
                    }
                });
        } else {
            listener.onError("No user logged in");
        }
    }

    // Check if current user's email is verified
    public boolean isEmailVerified() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.isEmailVerified();
    }

    // Resend email verification
    public void resendEmailVerification(OnEmailVerificationListener listener) {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            sendEmailVerification(user, listener);
        } else {
            listener.onError("No user logged in");
        }
    }
    
    public void loginUser(String email, String password, OnAuthCompleteListener listener) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null && !user.isEmailVerified()) {
                        // Email not verified - sign out and show error
                        auth.signOut();
                        listener.onError("Please verify your email before logging in. Check your inbox for the verification link.");
                        return;
                    }
                    
                    setGuestMode(false);
                    // Sync user data from Firebase BEFORE calling success
                    syncUserDataFromFirebase(new OnDataSyncListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Login successful - user data synced");
                            listener.onSuccess();
                        }

                        @Override
                        public void onError(String error) {
                            Log.w(TAG, "Login successful but data sync failed: " + error);
                            // Still call success since login was successful
                            listener.onSuccess();
                        }
                    });
                } else {
                    listener.onError(task.getException() != null ? 
                        task.getException().getMessage() : "Login failed");
                }
            });
    }
    
    public void resetPassword(String email, OnAuthCompleteListener listener) {
        Log.d(TAG, "Attempting to send password reset email to: " + email);
        
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Password reset email sent successfully to: " + email);
                    listener.onSuccess();
                } else {
                    String error = task.getException() != null ? 
                        task.getException().getMessage() : "Password reset failed";
                    Log.e(TAG, "Password reset failed for " + email + ": " + error);
                    
                    // Provide more user-friendly error messages
                    String userFriendlyError = error;
                    if (error.contains("user-not-found")) {
                        userFriendlyError = "No account found with this email address. Please check your email or create a new account.";
                    } else if (error.contains("invalid-email")) {
                        userFriendlyError = "Invalid email address format. Please enter a valid email.";
                    } else if (error.contains("network-request-failed")) {
                        userFriendlyError = "Network error. Please check your internet connection and try again.";
                    } else if (error.contains("too-many-requests")) {
                        userFriendlyError = "Too many reset attempts. Please wait a few minutes before trying again.";
                    }
                    
                    listener.onError(userFriendlyError);
                }
            });
    }

    // Check if user exists before attempting password reset
    public void checkUserExists(String email, OnAuthCompleteListener listener) {
        Log.d(TAG, "Checking if user exists: " + email);
        
        auth.fetchSignInMethodsForEmail(email)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    java.util.List<String> signInMethods = task.getResult().getSignInMethods();
                    if (signInMethods != null && !signInMethods.isEmpty()) {
                        Log.d(TAG, "User exists with email: " + email);
                        listener.onSuccess();
                    } else {
                        Log.d(TAG, "No user found with email: " + email);
                        listener.onError("No account found with this email address");
                    }
                } else {
                    String error = task.getException() != null ? 
                        task.getException().getMessage() : "Failed to check user";
                    Log.e(TAG, "Error checking user existence: " + error);
                    listener.onError(error);
                }
            });
    }
    
    public void signOut() {
        auth.signOut();
        clearLocalData();
    }
    
    public void continueAsGuest(String guestName) {
        setGuestMode(true);
        setUserName(guestName != null ? guestName : "Guest");
    }
    
    // Firebase Firestore operations
    private void createUserProfile(String uid, String fullName, String email) {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("fullName", fullName);
        userProfile.put("email", email);
        userProfile.put("createdAt", System.currentTimeMillis());
        
        firestore.collection("users").document(uid)
            .set(userProfile)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "User profile created"))
            .addOnFailureListener(e -> Log.e(TAG, "Error creating user profile", e));
    }
    
    private void updateFirebaseProfile(String name, String photoUrl, OnProfileUpdateListener listener) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (listener != null) {
                listener.onError("User not authenticated");
            }
            return;
        }
        
        String uid = user.getUid();
        Map<String, Object> updates = new HashMap<>();
        
        if (name != null) {
            updates.put("fullName", name);
        }
        if (photoUrl != null) {
            updates.put("profileImageUrl", photoUrl);
        }
        
        if (updates.isEmpty()) {
            if (listener != null) {
                listener.onSuccess();
            }
            return;
        }
        
        firestore.collection("users").document(uid)
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Profile updated successfully");
                if (listener != null) {
                    listener.onSuccess();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating profile", e);
                if (listener != null) {
                    listener.onError(e.getMessage());
                }
            });
    }
    
    private void syncUserDataFromFirebase() {
        syncUserDataFromFirebase(null);
    }
    
    public void syncUserDataFromFirebase(OnDataSyncListener listener) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (listener != null) {
                listener.onError("User not authenticated");
            }
            return;
        }
        
        String uid = user.getUid();
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    String fullName = document.getString("fullName");
                    String profileImageUrl = document.getString("profileImageUrl");
                    
                    Log.d(TAG, "Syncing user data - fullName: " + fullName + ", profileImageUrl: " + profileImageUrl);
                    
                    // Update local cache
                    if (fullName != null && !fullName.isEmpty()) {
                        prefs.edit().putString(KEY_USER_NAME, fullName).apply();
                        Log.d(TAG, "Updated local name cache: " + fullName);
                    }
                    if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                        prefs.edit().putString(KEY_PROFILE_IMAGE_URL, profileImageUrl).apply();
                        Log.d(TAG, "Updated local image URL cache: " + profileImageUrl);
                    }
                    
                    Log.d(TAG, "User data synced successfully");
                    if (listener != null) {
                        listener.onSuccess();
                    }
                } else {
                    Log.w(TAG, "User document does not exist");
                    if (listener != null) {
                        listener.onError("User profile not found");
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error syncing user data", e);
                if (listener != null) {
                    listener.onError(e.getMessage());
                }
            });
    }
    
    // Lightning-fast memory upload (no file I/O)
    public void uploadProfileImageFromMemory(byte[] imageData, OnImageUploadListener listener) {
        if (!isLoggedIn()) {
            // For guest mode, save as base64 string locally (fast)
            String base64 = android.util.Base64.encodeToString(imageData, android.util.Base64.DEFAULT);
            setProfileImageUrl("data:image/jpeg;base64," + base64);
            listener.onSuccess("data:image/jpeg;base64," + base64);
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (listener != null) {
                listener.onError("User not authenticated");
            }
            return;
        }

        try {
            String fileName = "profile_" + user.getUid() + "_" + System.currentTimeMillis() + ".jpg";
            StorageReference imageRef = storage.getReference()
                .child("profile_images")
                .child(fileName);

            // Direct byte array upload - SUPER FAST
            Log.d(TAG, "Starting lightning upload: " + imageData.length + " bytes");
            
            imageRef.putBytes(imageData)
                .addOnSuccessListener(taskSnapshot -> {
                    imageRef.getDownloadUrl().addOnSuccessListener(downloadUrl -> {
                        String url = downloadUrl.toString();
                        Log.d(TAG, "Lightning upload complete: " + url);
                        
                        // IMMEDIATELY save to local cache
                        prefs.edit().putString(KEY_PROFILE_IMAGE_URL, url).apply();
                        Log.d(TAG, "URL cached instantly: " + url);
                        
                        // Update Firestore in background (don't wait)
                        updateFirebaseProfile(null, url, new OnProfileUpdateListener() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Background Firestore update complete");
                            }
                            @Override
                            public void onError(String error) {
                                Log.w(TAG, "Background Firestore update failed: " + error);
                            }
                        });
                        
                        // Call success immediately
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (listener != null) {
                                listener.onSuccess(url);
                            }
                        });
                        
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting download URL", e);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (listener != null) {
                                listener.onError("Failed to get image URL");
                            }
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error uploading image bytes", e);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (listener != null) {
                            listener.onError("Upload failed: " + e.getMessage());
                        }
                    });
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Memory upload error", e);
            if (listener != null) {
                listener.onError("Upload error: " + e.getMessage());
            }
        }
    }

    // Upload profile image
    public void uploadProfileImage(Uri imageUri, OnImageUploadListener listener) {
        if (!isLoggedIn()) {
            // For guest mode, just save local path
            setProfileImageUrl(imageUri.toString());
            listener.onSuccess(imageUri.toString());
            return;
        }
        
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (listener != null) {
                listener.onError("User not authenticated");
            }
            return;
        }
        
        // Check if Firebase Storage is properly configured
        try {
            String fileName = "profile_" + user.getUid() + ".jpg";
            StorageReference imageRef = storage.getReference()
                .child("profile_images")
                .child(fileName);
            
            // Test storage access first
            imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    imageRef.getDownloadUrl().addOnSuccessListener(downloadUrl -> {
                        String url = downloadUrl.toString();
                        Log.d(TAG, "Image uploaded successfully: " + url);
                        
                        // IMMEDIATELY save the image URL to local storage for instant access
                        prefs.edit().putString(KEY_PROFILE_IMAGE_URL, url).apply();
                        Log.d(TAG, "Image URL saved to local cache immediately: " + url);
                        
                        // Update Firestore with the new profile image URL
                        updateFirebaseProfile(null, url, new OnProfileUpdateListener() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Profile image URL saved to Firestore successfully");
                                Log.d(TAG, "Calling upload success callback with URL: " + url);
                                // Ensure callback is called on main thread
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    if (listener != null) {
                                        listener.onSuccess(url);
                                    }
                                });
                            }

                            @Override
                            public void onError(String error) {
                                Log.e(TAG, "Failed to save image URL to Firestore: " + error);
                                Log.d(TAG, "Still calling upload success callback with URL: " + url);
                                // Still call success since the image was uploaded successfully
                                // Ensure callback is called on main thread
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    if (listener != null) {
                                        listener.onSuccess(url);
                                    }
                                });
                            }
                        });
                        
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting download URL", e);
                        // Ensure callback is called on main thread
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            if (listener != null) {
                                listener.onError("Failed to get image URL. Please check Firebase Storage configuration.");
                            }
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error uploading image", e);
                    
                    // Build error message as final variable
                    final String errorMessage;
                    if (e.getMessage() != null && e.getMessage().contains("Object does not exist")) {
                        errorMessage = "Image upload failed. Firebase Storage is not properly configured. Please contact support.";
                    } else if (e.getMessage() != null && e.getMessage().contains("permission")) {
                        errorMessage = "Image upload failed. Storage permission denied. Please check Firebase Storage rules.";
                    } else {
                        errorMessage = "Image upload failed. Please try again later.";
                    }
                    
                    // Ensure callback is called on main thread
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (listener != null) {
                            listener.onError(errorMessage);
                        }
                    });
                });
        } catch (Exception e) {
            Log.e(TAG, "Firebase Storage not configured", e);
            if (listener != null) {
                listener.onError("Image upload feature is currently unavailable. Please try again later.");
            }
        }
    }
    
    private void clearLocalData() {
        prefs.edit()
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_PROFILE_IMAGE_URL)
            .remove(KEY_IS_GUEST)
            .apply();
    }
    
    // Callback interfaces
    public interface OnAuthCompleteListener {
        void onSuccess();
        void onError(String error);
    }
    
    public interface OnImageUploadListener {
        void onSuccess(String imageUrl);
        void onError(String error);
    }
    
    public interface OnProfileUpdateListener {
        void onSuccess();
        void onError(String error);
    }
    
    public interface OnDataSyncListener {
        void onSuccess();
        void onError(String error);
    }

    public interface OnEmailVerificationListener {
        void onSuccess();
        void onError(String error);
    }
} 