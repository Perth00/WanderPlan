package com.example.mobiledegreefinalproject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserManager {
    private static final String TAG = "UserManager";
    private static final String PREFS_NAME = "WanderPlanPrefs";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_PROFILE_IMAGE_URL = "profile_image_url";
    private static final String KEY_IS_GUEST = "is_guest";

    // Broadcast action for profile updates
    public static final String ACTION_PROFILE_UPDATED = "com.example.mobiledegreefinalproject.PROFILE_UPDATED";

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

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null && !isGuest();
    }

    public boolean isGuest() {
        return prefs.getBoolean(KEY_IS_GUEST, false);
    }

    public void setGuestMode(boolean isGuest) {
        prefs.edit().putBoolean(KEY_IS_GUEST, isGuest).apply();
    }

    public String getUserName() {
        if (isGuest()) {
            return prefs.getString(KEY_USER_NAME, "Guest User");
        }
        
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            String firebaseName = user.getDisplayName();
            if (firebaseName != null && !firebaseName.trim().isEmpty()) {
                // Update local cache
                prefs.edit().putString(KEY_USER_NAME, firebaseName).apply();
                return firebaseName;
            }
        }
        
        // Fallback to cached name
        return prefs.getString(KEY_USER_NAME, "User");
    }

    public String getUserEmail() {
        if (isGuest()) {
            return "";
        }
        
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            return user.getEmail();
        }
        
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    public String getProfileImageUrl() {
        if (isGuest()) {
            return prefs.getString(KEY_PROFILE_IMAGE_URL, "");
        }
        
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.getPhotoUrl() != null) {
            String firebaseImageUrl = user.getPhotoUrl().toString();
            // Update local cache
            prefs.edit().putString(KEY_PROFILE_IMAGE_URL, firebaseImageUrl).apply();
            return firebaseImageUrl;
        }
        
        // Fallback to cached URL
        return prefs.getString(KEY_PROFILE_IMAGE_URL, "");
    }

    public void setUserName(String name) {
        prefs.edit().putString(KEY_USER_NAME, name).apply();
        broadcastProfileUpdate();
    }

    public void setUserName(String name, OnProfileUpdateListener listener) {
        if (isGuest()) {
            setUserName(name);
            if (listener != null) {
                listener.onSuccess();
            }
            return;
        }

        // Update Firebase profile
        updateFirebaseProfile(name, null, new OnProfileUpdateListener() {
            @Override
            public void onSuccess() {
                setUserName(name);
                if (listener != null) {
                    listener.onSuccess();
                }
            }

            @Override
            public void onError(String error) {
                if (listener != null) {
                    listener.onError(error);
                }
            }
        });
    }

    public void setUserEmail(String email) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply();
    }

    public void setProfileImageUrl(String url) {
        prefs.edit().putString(KEY_PROFILE_IMAGE_URL, url).apply();
        broadcastProfileUpdate();
    }

    public void registerUser(String email, String password, String fullName, 
                           OnAuthCompleteListener listener) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener(authResult -> {
                FirebaseUser user = authResult.getUser();
                if (user != null) {
                    // Create user profile in Firestore
                    createUserProfile(user.getUid(), fullName, email);
                    
                    // Send email verification
                    sendEmailVerification(user, new OnEmailVerificationListener() {
                        @Override
                        public void onSuccess() {
                            setUserName(fullName);
                            setUserEmail(email);
                            setGuestMode(false);
                            listener.onSuccess();
                        }

                        @Override
                        public void onError(String error) {
                            Log.w(TAG, "Email verification failed", new Exception(error));
                            // Still count as successful registration
                            setUserName(fullName);
                            setUserEmail(email);
                            setGuestMode(false);
                            listener.onSuccess();
                        }
                    });
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Registration failed", e);
                listener.onError(e.getMessage());
            });
    }

    public void sendEmailVerification(FirebaseUser user, OnEmailVerificationListener listener) {
        if (user != null) {
            user.sendEmailVerification()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Email verification sent successfully");
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send email verification", e);
                    listener.onError(e.getMessage());
                });
        } else {
            listener.onError("No user found");
        }
    }

    public boolean isEmailVerified() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.isEmailVerified();
    }

    public void resendEmailVerification(OnEmailVerificationListener listener) {
        FirebaseUser user = auth.getCurrentUser();
        sendEmailVerification(user, listener);
    }

    public void loginUser(String email, String password, OnAuthCompleteListener listener) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener(authResult -> {
                FirebaseUser user = authResult.getUser();
                if (user != null) {
                    setUserEmail(email);
                    setGuestMode(false);
                    
                    // Sync user data from Firebase
                    syncUserDataFromFirebase(new OnDataSyncListener() {
                        @Override
                        public void onSuccess() {
                            listener.onSuccess();
                        }

                        @Override
                        public void onError(String error) {
                            Log.w(TAG, "Data sync failed: " + error);
                            // Still count as successful login
                            listener.onSuccess();
                        }
                    });
                } else {
                    listener.onError("Login failed");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Login failed", e);
                listener.onError(e.getMessage());
            });
    }

    public void resetPassword(String email, OnAuthCompleteListener listener) {
        if (email == null || email.trim().isEmpty()) {
            listener.onError("Email is required");
            return;
        }

        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Password reset email sent to: " + email);
                listener.onSuccess();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to send password reset email", e);
                String errorMessage = e.getMessage();
                if (errorMessage != null && errorMessage.contains("no user record")) {
                    listener.onError("No account found with this email address");
                } else {
                    listener.onError("Failed to send reset email: " + errorMessage);
                }
            });
    }

    public void checkUserExists(String email, OnAuthCompleteListener listener) {
        auth.fetchSignInMethodsForEmail(email)
            .addOnSuccessListener(result -> {
                if (result.getSignInMethods() != null && !result.getSignInMethods().isEmpty()) {
                    listener.onSuccess(); // User exists
                } else {
                    listener.onError("No account found with this email");
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error checking user existence", e);
                listener.onError("Error checking email: " + e.getMessage());
            });
    }

    public void signOut() {
        auth.signOut();
        clearLocalData();
        clearTripData();
    }

    public void continueAsGuest(String guestName) {
        setGuestMode(true);
        setUserName(guestName);
    }

    private void createUserProfile(String uid, String fullName, String email) {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("name", fullName);
        userProfile.put("email", email);
        userProfile.put("createdAt", System.currentTimeMillis());

        firestore.collection("users").document(uid)
            .set(userProfile)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "User profile created"))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to create user profile", e));
    }

    private void updateFirebaseProfile(String name, String photoUrl, OnProfileUpdateListener listener) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (listener != null) {
                listener.onError("No user logged in");
            }
            return;
        }

        UserProfileChangeRequest.Builder builder = new UserProfileChangeRequest.Builder();
        
        if (name != null) {
            builder.setDisplayName(name);
        }
        
        if (photoUrl != null) {
            builder.setPhotoUri(Uri.parse(photoUrl));
        }

        user.updateProfile(builder.build())
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Firebase profile updated successfully");
                
                // Also update Firestore
                Map<String, Object> updates = new HashMap<>();
                if (name != null) {
                    updates.put("name", name);
                }
                if (photoUrl != null) {
                    updates.put("profileImageUrl", photoUrl);
                }
                
                if (!updates.isEmpty()) {
                    firestore.collection("users").document(user.getUid())
                        .update(updates)
                        .addOnSuccessListener(aVoid1 -> {
                            if (listener != null) {
                                listener.onSuccess();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "Failed to update Firestore profile", e);
                            // Still consider success if Firebase Auth update worked
                            if (listener != null) {
                                listener.onSuccess();
                            }
                        });
                } else {
                    if (listener != null) {
                        listener.onSuccess();
                    }
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to update Firebase profile", e);
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
        if (user == null || isGuest()) {
            if (listener != null) {
                listener.onError("No user to sync");
            }
            return;
        }

        // Get user data from Firestore
        firestore.collection("users").document(user.getUid())
            .get()
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String name = documentSnapshot.getString("name");
                    String profileImageUrl = documentSnapshot.getString("profileImageUrl");
                    
                    if (name != null && !name.trim().isEmpty()) {
                        setUserName(name);
                    }
                    
                    if (profileImageUrl != null && !profileImageUrl.trim().isEmpty()) {
                        setProfileImageUrl(profileImageUrl);
                    }
                    
                    Log.d(TAG, "User data synced from Firestore");
                } else {
                    Log.d(TAG, "No user document in Firestore");
                }
                
                // Also sync from Firebase Auth
                if (user.getDisplayName() != null) {
                    setUserName(user.getDisplayName());
                }
                
                if (user.getEmail() != null) {
                    setUserEmail(user.getEmail());
                }
                
                if (user.getPhotoUrl() != null) {
                    setProfileImageUrl(user.getPhotoUrl().toString());
                }
                
                if (listener != null) {
                    listener.onSuccess();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to sync user data from Firebase", e);
                if (listener != null) {
                    listener.onError(e.getMessage());
                }
            });
    }

    private void broadcastProfileUpdate() {
        Intent intent = new Intent(ACTION_PROFILE_UPDATED);
        context.sendBroadcast(intent);
    }

    public void uploadProfileImage(Uri imageUri, OnImageUploadListener listener) {
        if (isGuest()) {
            listener.onError("Image upload not available for guest users");
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            listener.onError("No user logged in");
            return;
        }

        try {
            String fileName = "profile_images/" + user.getUid() + "_" + System.currentTimeMillis() + ".jpg";
            StorageReference storageRef = storage.getReference().child(fileName);

            storageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    storageRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                        String imageUrl = downloadUri.toString();
                        
                        // Update Firebase profile
                        updateFirebaseProfile(null, imageUrl, new OnProfileUpdateListener() {
                            @Override
                            public void onSuccess() {
                                setProfileImageUrl(imageUrl);
                                listener.onSuccess(imageUrl);
                            }

                            @Override
                            public void onError(String error) {
                                listener.onError("Failed to update profile: " + error);
                            }
                        });
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get download URL", e);
                        listener.onError("Failed to get image URL: " + e.getMessage());
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload image", e);
                    listener.onError("Failed to upload image: " + e.getMessage());
                });
        } catch (Exception e) {
            Log.e(TAG, "Firebase Storage not configured", e);
            listener.onError("Image upload feature is currently unavailable. Please try again later.");
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

    private void clearTripData() {
        try {
            com.example.mobiledegreefinalproject.repository.TripRepository repo = 
                com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(context);
            repo.clearFirebaseTripsFromLocal();
            Log.d(TAG, "Firebase trip data cleared from local storage");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing trip data", e);
        }
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

    public interface OnTripSyncListener {
        void onSuccess();
        void onError(String error);
        void onSyncRequired(int localTripCount, int firebaseTripCount);
    }

    // Trip sync methods
    public void checkTripSyncStatus(OnTripSyncListener listener) {
        if (!isLoggedIn()) {
            listener.onError("User not logged in");
            return;
        }
        
        new Thread(() -> {
            try {
                com.example.mobiledegreefinalproject.repository.TripRepository repo = 
                    com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(context);
                
                // Get unsynced local trips
                List<com.example.mobiledegreefinalproject.database.Trip> unsyncedTrips = repo.getUnsyncedTripsSync();
                List<com.example.mobiledegreefinalproject.database.Trip> allTrips = repo.getAllTripsSync();
                
                int localTripCount = unsyncedTrips.size();
                int firebaseTripCount = allTrips.size() - localTripCount; // Trips that have Firebase IDs
                
                Log.d(TAG, "Trip sync status: " + localTripCount + " local trips, " + firebaseTripCount + " Firebase trips");
                
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (localTripCount > 0) {
                        // There are unsynced local trips
                        listener.onSyncRequired(localTripCount, firebaseTripCount);
                    } else {
                        // No sync needed
                        listener.onSuccess();
                    }
                });
                    
            } catch (Exception e) {
                Log.e(TAG, "Error checking trip sync status", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    listener.onError("Failed to check trip sync status: " + e.getMessage());
                });
            }
        }).start();
    }

    public void syncLocalTripsToFirebase(OnTripSyncListener listener) {
        if (!isLoggedIn()) {
            listener.onError("User not logged in");
            return;
        }
        
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting sync of local trips to Firebase");
                
                com.example.mobiledegreefinalproject.repository.TripRepository repo = 
                    com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(context);
                
                // Get all unsynced trips (trips with firebaseId = null or synced = false)
                List<com.example.mobiledegreefinalproject.database.Trip> unsyncedTrips = repo.getUnsyncedTripsSync();
                
                if (unsyncedTrips.isEmpty()) {
                    Log.d(TAG, "No unsynced trips found");
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onSuccess();
                    });
                    return;
                }
                
                Log.d(TAG, "Found " + unsyncedTrips.size() + " unsynced trips to sync");
                
                // Counter for tracking completion
                final int[] completed = {0};
                final int[] successful = {0};
                final boolean[] hasError = {false};
                final StringBuilder errorMessages = new StringBuilder();
                final int totalTrips = unsyncedTrips.size();
                
                // Sync each trip (this will also sync activities)
                for (com.example.mobiledegreefinalproject.database.Trip trip : unsyncedTrips) {
                    Log.d(TAG, "Syncing trip: " + trip.getTitle());
                    
                    repo.syncTripWithActiviesToFirebase(trip, new com.example.mobiledegreefinalproject.repository.TripRepository.OnTripOperationListener() {
                        @Override
                        public void onSuccess(int tripId) {
                            synchronized (completed) {
                                completed[0]++;
                                successful[0]++;
                                Log.d(TAG, "Trip synced successfully (" + completed[0] + "/" + totalTrips + ")");
                                
                                if (completed[0] >= totalTrips) {
                                    // All trips processed
                                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                        if (successful[0] == totalTrips) {
                                            Log.d(TAG, "All trips synced successfully");
                                            listener.onSuccess();
                                        } else {
                                            String message = "Partial sync: " + successful[0] + "/" + totalTrips + " trips synced";
                                            if (errorMessages.length() > 0) {
                                                message += ". Errors: " + errorMessages.toString();
                                            }
                                            listener.onError(message);
                                        }
                                    });
                                }
                            }
                        }
                        
                        @Override
                        public void onError(String error) {
                            synchronized (completed) {
                                completed[0]++;
                                hasError[0] = true;
                                if (errorMessages.length() > 0) {
                                    errorMessages.append(", ");
                                }
                                errorMessages.append(error);
                                Log.e(TAG, "Failed to sync trip: " + error + " (" + completed[0] + "/" + totalTrips + ")");
                                
                                if (completed[0] >= totalTrips) {
                                    // All trips processed
                                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                        String message = "Sync completed with errors: " + successful[0] + "/" + totalTrips + " trips synced";
                                        if (errorMessages.length() > 0) {
                                            message += ". Errors: " + errorMessages.toString();
                                        }
                                        listener.onError(message);
                                    });
                                }
                            }
                        }
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error during local trips sync", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    listener.onError("Failed to sync local trips: " + e.getMessage());
                });
            }
        }).start();
    }

    public void discardLocalTrips(OnTripSyncListener listener) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Discarding local unsynced trips");
                
                com.example.mobiledegreefinalproject.repository.TripRepository repo = 
                    com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(context);
                
                // Get all unsynced trips and delete them
                List<com.example.mobiledegreefinalproject.database.Trip> unsyncedTrips = repo.getUnsyncedTripsSync();
                
                Log.d(TAG, "Found " + unsyncedTrips.size() + " unsynced trips to discard");
                
                for (com.example.mobiledegreefinalproject.database.Trip trip : unsyncedTrips) {
                    Log.d(TAG, "Discarding trip: " + trip.getTitle());
                    // This will also delete all activities for the trip due to cascade
                    repo.deleteTrip(trip, null); // null listener since we don't care about Firebase deletion
                }
                
                Log.d(TAG, "Successfully discarded " + unsyncedTrips.size() + " local trips");
                
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    listener.onSuccess();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error discarding local trips", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    listener.onError("Failed to discard local trips: " + e.getMessage());
                });
            }
        }).start();
    }
}