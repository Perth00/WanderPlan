package com.example.mobiledegreefinalproject;

import android.content.Context;
import android.util.Log;

import com.example.mobiledegreefinalproject.database.Trip;
import com.example.mobiledegreefinalproject.database.TripActivity;
import com.example.mobiledegreefinalproject.repository.TripRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.ByteArrayOutputStream;
import android.graphics.Bitmap;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.graphics.drawable.Drawable;

import com.example.mobiledegreefinalproject.repository.BudgetRepository;
import com.example.mobiledegreefinalproject.model.Expense;

/**
 * Service to sync local database data to Firebase as JSON
 * This approach reduces crashes by storing data in JSON format
 * which is more resilient to data structure changes
 */
public class DataSyncService {
    private static final String TAG = "DataSyncService";
    // Follow the same Firebase structure as existing trip management
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_TRIPS = "trips";  
    private static final String COLLECTION_ACTIVITIES = "activities";
    
    private final Context context;
    private final TripRepository tripRepository;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final FirebaseStorage storage;
    private final Gson gson;
    private final ExecutorService executor;
    private final SyncPreferences syncPrefs;
    private final BudgetRepository budgetRepository;
    
    public DataSyncService(Context context) {
        this.context = context;
        this.tripRepository = TripRepository.getInstance(context);
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.storage = FirebaseStorage.getInstance();
        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .setPrettyPrinting()
                .create();
        this.executor = Executors.newSingleThreadExecutor();
        this.syncPrefs = new SyncPreferences(context);
        this.budgetRepository = BudgetRepository.getInstance(context);
    }
    
    public interface OnSyncCompleteListener {
        void onProgressUpdate(int progress, String message);
        void onSuccess(int tripsSynced, int activitiesSynced);
        void onError(String error);
    }
    
    /**
     * Sync all local data to Firebase as JSON
     */
    public void syncLocalDataToFirebase(OnSyncCompleteListener listener) {
        if (auth.getCurrentUser() == null) {
            Log.e(TAG, "Sync failed: User not authenticated");
            listener.onError("User not authenticated");
            return;
        }
        
        String userId = auth.getCurrentUser().getUid();
        String userEmail = auth.getCurrentUser().getEmail();
        
        if (userEmail == null || userEmail.trim().isEmpty()) {
            Log.e(TAG, "User email is null or empty");
            listener.onError("User email not available");
            return;
        }
        
        Log.d(TAG, "=== STARTING FIREBASE SYNC ===");
        Log.d(TAG, "User ID: " + userId);
        Log.d(TAG, "User Email: " + userEmail);
        
        // Test Firebase connectivity first
        testFirebaseConnection(userEmail, listener);
    }
    
    /**
     * Sync a single trip and its activities following existing Firebase structure
     */
    private void syncTripAsJson(String userEmail, Trip trip, AtomicInteger syncedBudgetEntries, OnTripSyncListener listener) {
        try {
            Log.d(TAG, "🔄 Syncing trip: " + trip.getTitle() + " (ID: " + trip.getId() + ")");
            
            // Convert trip to standard Firebase format (like existing trip management)
            Map<String, Object> tripData = createTripFirebaseData(trip);
            
            // Get activities for this trip
            List<TripActivity> activities = tripRepository.getActivitiesForTripSync(trip.getId());
            Log.d(TAG, "Found " + activities.size() + " activities for trip: " + trip.getTitle());
            
            // Store in Firebase using same structure as existing trip management
            String collectionPath = COLLECTION_USERS + "/" + userEmail + "/" + COLLECTION_TRIPS;
            
            Log.d(TAG, "📤 Uploading to Firebase:");
            Log.d(TAG, "   Collection: " + collectionPath);
            Log.d(TAG, "   Data size: " + tripData.size() + " fields");
            
            firestore.collection(COLLECTION_USERS)
                    .document(userEmail)
                    .collection(COLLECTION_TRIPS)
                    .add(tripData)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "✅ Successfully synced trip to Firebase: " + trip.getTitle());
                        
                        // Now sync activities and budget for this trip
                        String tripFirebaseId = documentReference.getId();
                        
                        // Update local trip with Firebase ID for budget sync
                        trip.setFirebaseId(tripFirebaseId);
                        tripRepository.updateTripFirebaseId(trip.getId(), tripFirebaseId);
                        
                        syncActivitiesForTrip(userEmail, tripFirebaseId, activities, new OnTripSyncListener() {
                            @Override
                            public void onTripSynced(int activitiesCount) {
                                // After activities are synced, sync the budget for this trip
                                syncBudgetForTrip(trip.getId(), userEmail, new OnBudgetSyncCompleteListener() {
                                                                         @Override
                                    public void onBudgetSyncComplete(int budgetEntriesSynced) {
                                        syncedBudgetEntries.addAndGet(budgetEntriesSynced);
                                        Log.d(TAG, "✅ Trip sync complete: " + trip.getTitle() + 
                                               " (Activities: " + activitiesCount + ", Budget entries: " + budgetEntriesSynced + ")");
                                        listener.onTripSynced(activitiesCount);
                                    }
                                    
                                                                         @Override
                                    public void onBudgetSyncError(String error) {
                                        Log.w(TAG, "⚠️ Budget sync failed for trip " + trip.getTitle() + ": " + error);
                                        // Don't fail the entire trip sync - activities are already synced
                                        // Budget entries count remains 0 for this trip
                                        listener.onTripSynced(activitiesCount);
                                    }
                                });
                            }
                            
                            @Override
                            public void onError(String error) {
                                listener.onError(error);
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to sync trip to Firebase: " + trip.getTitle(), e);
                        Log.e(TAG, "   Error type: " + e.getClass().getSimpleName());
                        Log.e(TAG, "   Error message: " + e.getMessage());
                        listener.onError("Firebase upload failed: " + e.getMessage());
                    });
                    
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating trip JSON for: " + trip.getTitle(), e);
            listener.onError("Failed to convert trip to JSON: " + e.getMessage());
        }
    }
    
    /**
     * Create Firebase data for a trip (following existing trip management structure)
     */
    private Map<String, Object> createTripFirebaseData(Trip trip) {
        Map<String, Object> data = new HashMap<>();
        
        // Basic trip data (same as existing trip management)
        data.put("title", trip.getTitle());
        data.put("destination", trip.getDestination());
        data.put("startDate", trip.getStartDate());
        data.put("endDate", trip.getEndDate());
        data.put("createdAt", trip.getCreatedAt());
        data.put("updatedAt", trip.getUpdatedAt());
        
        return data;
    }
    
    /**
     * Sync activities for a trip to Firebase (with image upload support)
     */
    private void syncActivitiesForTrip(String userEmail, String tripFirebaseId, List<TripActivity> activities, OnTripSyncListener listener) {
        if (activities.isEmpty()) {
            listener.onTripSynced(0);
            return;
        }
        
        Log.d(TAG, "🖼️ Syncing " + activities.size() + " activities with image support");
        AtomicInteger completedActivities = new AtomicInteger(0);
        
        // Count activities with images for progress tracking
        int activitiesWithImages = 0;
        for (TripActivity activity : activities) {
            if (hasLocalImageToUpload(activity)) {
                activitiesWithImages++;
                Log.d(TAG, "📷 Activity '" + activity.getTitle() + "' has local image: " + activity.getImageUrl());
            } else {
                String imageUrl = activity.getImageUrl();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Log.d(TAG, "🔗 Activity '" + activity.getTitle() + "' has existing Firebase image: " + imageUrl);
                } else {
                    Log.d(TAG, "📝 Activity '" + activity.getTitle() + "' has no image");
                }
            }
        }
        Log.d(TAG, "🖼️ Found " + activitiesWithImages + " activities with local images to upload");
        
        for (TripActivity activity : activities) {
            // Check if activity has a local image that needs uploading
            if (hasLocalImageToUpload(activity)) {
                Log.d(TAG, "📷 Activity has local image, uploading: " + activity.getTitle());
                uploadActivityImageAndSync(userEmail, tripFirebaseId, activity, completedActivities, activities.size(), listener);
            } else {
                // No image or already has Firebase URL, sync directly
                Log.d(TAG, "📝 Activity has no local image, syncing directly: " + activity.getTitle());
                syncActivityToFirestore(userEmail, tripFirebaseId, activity, null, completedActivities, activities.size(), listener);
            }
        }
    }
    
    /**
     * Check if activity has a local image that needs to be uploaded
     */
    private boolean hasLocalImageToUpload(TripActivity activity) {
        String imageUrl = activity.getImageUrl();
        if (imageUrl == null || imageUrl.isEmpty()) {
            return false;
        }
        
        // Check if it's a local file path (not a Firebase URL)
        return !imageUrl.startsWith("https://firebasestorage.googleapis.com") && 
               (imageUrl.startsWith("file://") || imageUrl.startsWith("content://") || 
                imageUrl.startsWith("/") || imageUrl.contains("/activity_images/"));
    }
    
    /**
     * Upload activity image to Firebase Storage then sync to Firestore
     * (Using Glide like AddActivityActivity for proper URI handling)
     */
    private void uploadActivityImageAndSync(String userEmail, String tripFirebaseId, TripActivity activity, 
                                          AtomicInteger completedActivities, int totalActivities, OnTripSyncListener listener) {
        try {
            String imageUrl = activity.getImageUrl();
            Log.d(TAG, "📤 Processing image for upload: " + imageUrl);
            
            // Use Glide to load the image (same as AddActivityActivity) - this handles content:// URIs properly
            try {
                Glide.with(context)
                        .asBitmap()
                        .load(imageUrl)
                        .override(1024, 1024) // Reasonable size for Firebase
                        .timeout(20000) // 20 second timeout
                        .into(new CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                                Log.d(TAG, "✅ Bitmap loaded successfully via Glide: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                                uploadBitmapToFirebase(bitmap, userEmail, tripFirebaseId, activity, completedActivities, totalActivities, listener);
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {
                                Log.w(TAG, "⚠️ Glide load cleared for: " + imageUrl + ", syncing without image");
                                syncActivityToFirestore(userEmail, tripFirebaseId, activity, null, 
                                                      completedActivities, totalActivities, listener);
                            }

                            @Override
                            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                Log.w(TAG, "⚠️ Glide failed to load image: " + imageUrl + ", syncing without image");
                                syncActivityToFirestore(userEmail, tripFirebaseId, activity, null, 
                                                      completedActivities, totalActivities, listener);
                            }
                        });
            } catch (IllegalArgumentException e) {
                // Context might be destroyed or invalid
                Log.w(TAG, "⚠️ Context invalid for Glide, using fallback image loading for: " + imageUrl, e);
                loadImageWithFallback(imageUrl, userEmail, tripFirebaseId, activity, completedActivities, totalActivities, listener);
            }
                    
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Error setting up Glide image loading for activity: " + activity.getTitle(), e);
            // Sync without image
            syncActivityToFirestore(userEmail, tripFirebaseId, activity, null, 
                                  completedActivities, totalActivities, listener);
        }
    }
    
    /**
     * Upload bitmap to Firebase Storage (extracted from uploadActivityImageAndSync)
     */
    private void uploadBitmapToFirebase(Bitmap bitmap, String userEmail, String tripFirebaseId, TripActivity activity,
                                      AtomicInteger completedActivities, int totalActivities, OnTripSyncListener listener) {
        try {
            // Compress bitmap to byte array (same as existing code)
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteStream); // Same quality as AddActivityActivity
            byte[] data = byteStream.toByteArray();
            
            // Limit file size to prevent crashes (same as existing code)
            if (data.length > 1024 * 1024 * 2) { // 2MB limit
                Log.w(TAG, "⚠️ Image too large (" + (data.length / 1024 / 1024) + "MB), syncing without image");
                syncActivityToFirestore(userEmail, tripFirebaseId, activity, null, 
                                      completedActivities, totalActivities, listener);
                return;
            }
            
            // Create unique filename for Firebase Storage
            String fileName = "activity_" + System.currentTimeMillis() + "_" + 
                            activity.getTitle().replaceAll("[^a-zA-Z0-9]", "") + ".jpg";
            
            StorageReference storageRef = storage.getReference()
                    .child("activity_images")
                    .child(fileName);
            
            Log.d(TAG, "📤 Uploading image to Firebase Storage: " + fileName + " (" + (data.length / 1024) + "KB)");
            
            // Upload with putBytes (same as AddActivityActivity)
            storageRef.putBytes(data)
                    .addOnSuccessListener(taskSnapshot -> {
                        Log.d(TAG, "✅ Image uploaded successfully: " + fileName);
                        
                        // Get download URL
                        storageRef.getDownloadUrl()
                                .addOnSuccessListener(downloadUri -> {
                                    String firebaseImageUrl = downloadUri.toString();
                                    Log.d(TAG, "🔗 Got Firebase image URL: " + firebaseImageUrl);
                                    
                                    // Now sync activity with Firebase image URL
                                    syncActivityToFirestore(userEmail, tripFirebaseId, activity, firebaseImageUrl, 
                                                          completedActivities, totalActivities, listener);
                                })
                                .addOnFailureListener(e -> {
                                    Log.w(TAG, "⚠️ Failed to get download URL for " + fileName + ", syncing without image", e);
                                    // Sync without image URL
                                    syncActivityToFirestore(userEmail, tripFirebaseId, activity, null, 
                                                          completedActivities, totalActivities, listener);
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "⚠️ Failed to upload image for activity: " + activity.getTitle() + ", syncing without image", e);
                        // Sync without image
                        syncActivityToFirestore(userEmail, tripFirebaseId, activity, null, 
                                              completedActivities, totalActivities, listener);
                    });
                    
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Error processing bitmap for Firebase upload", e);
            // Sync without image
            syncActivityToFirestore(userEmail, tripFirebaseId, activity, null, 
                                  completedActivities, totalActivities, listener);
        }
    }
    
    /**
     * Fallback image loading when Glide fails (context destroyed, etc.)
     */
    private void loadImageWithFallback(String imageUrl, String userEmail, String tripFirebaseId, TripActivity activity,
                                     AtomicInteger completedActivities, int totalActivities, OnTripSyncListener listener) {
        Log.d(TAG, "🔄 Using fallback image loading for: " + imageUrl);
        
        // Run on background thread to avoid blocking UI
        executor.execute(() -> {
            try {
                Bitmap bitmap = loadBitmapFromUriManually(imageUrl);
                if (bitmap != null) {
                    Log.d(TAG, "✅ Bitmap loaded via fallback: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                    uploadBitmapToFirebase(bitmap, userEmail, tripFirebaseId, activity, completedActivities, totalActivities, listener);
                } else {
                    Log.w(TAG, "⚠️ Fallback image loading failed, syncing without image");
                    syncActivityToFirestore(userEmail, tripFirebaseId, activity, null, 
                                          completedActivities, totalActivities, listener);
                }
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Exception in fallback image loading", e);
                syncActivityToFirestore(userEmail, tripFirebaseId, activity, null, 
                                      completedActivities, totalActivities, listener);
            }
        });
    }
    
    /**
     * Manual bitmap loading as fallback (when Glide fails)
     */
    private Bitmap loadBitmapFromUriManually(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        
        try {
            Bitmap bitmap = null;
            
            if (imageUrl.startsWith("content://")) {
                // Handle content URI (from gallery) - might fail if URI is expired
                try {
                    android.content.ContentResolver contentResolver = context.getContentResolver();
                    java.io.InputStream inputStream = contentResolver.openInputStream(android.net.Uri.parse(imageUrl));
                    bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "⚠️ Failed to load content URI (might be expired): " + imageUrl, e);
                }
            } else if (imageUrl.startsWith("file://")) {
                // Handle file URI
                String filePath = android.net.Uri.parse(imageUrl).getPath();
                if (filePath != null) {
                    bitmap = android.graphics.BitmapFactory.decodeFile(filePath);
                }
            } else if (imageUrl.startsWith("/") || imageUrl.contains("/activity_images/")) {
                // Handle direct file path (including internal storage paths)
                bitmap = android.graphics.BitmapFactory.decodeFile(imageUrl);
                Log.d(TAG, "📁 Loading from file path: " + imageUrl);
            }
            
            if (bitmap != null) {
                // Resize if too large
                int maxDimension = 1024;
                if (bitmap.getWidth() > maxDimension || bitmap.getHeight() > maxDimension) {
                    float scale = Math.min(
                        (float) maxDimension / bitmap.getWidth(),
                        (float) maxDimension / bitmap.getHeight()
                    );
                    int newWidth = Math.round(bitmap.getWidth() * scale);
                    int newHeight = Math.round(bitmap.getHeight() * scale);
                    bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                }
                
                Log.d(TAG, "📷 Loaded bitmap manually: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                return bitmap;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in manual bitmap loading from: " + imageUrl, e);
        }
        
        return null;
    }

    
    /**
     * Sync activity to Firestore with optional Firebase image URL (with duplicate prevention)
     */
    private void syncActivityToFirestore(String userEmail, String tripFirebaseId, TripActivity activity, 
                                       String firebaseImageUrl, AtomicInteger completedActivities, 
                                       int totalActivities, OnTripSyncListener listener) {
        
        // CRITICAL FIX: Check for duplicates before creating new activity
        firestore.collection(COLLECTION_USERS)
                .document(userEmail)
                .collection(COLLECTION_TRIPS)
                .document(tripFirebaseId)
                .collection(COLLECTION_ACTIVITIES)
                .whereEqualTo("title", activity.getTitle())
                .whereEqualTo("dateTime", activity.getDateTime())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Activity already exists, update it instead of creating new one
                        Log.d(TAG, "🔄 Activity already exists in Firebase, updating: " + activity.getTitle());
                        String existingDocId = querySnapshot.getDocuments().get(0).getId();
                        updateExistingActivity(userEmail, tripFirebaseId, existingDocId, activity, firebaseImageUrl, 
                                             completedActivities, totalActivities, listener);
                    } else {
                        // No duplicate found, create new activity
                        Log.d(TAG, "➕ Creating new activity in Firebase: " + activity.getTitle());
                        createNewActivity(userEmail, tripFirebaseId, activity, firebaseImageUrl, 
                                        completedActivities, totalActivities, listener);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "⚠️ Error checking for duplicates, creating new activity: " + activity.getTitle(), e);
                    // If duplicate check fails, proceed with creating new activity
                    createNewActivity(userEmail, tripFirebaseId, activity, firebaseImageUrl, 
                                    completedActivities, totalActivities, listener);
                });
    }
    
    /**
     * Update existing activity in Firestore
     */
    private void updateExistingActivity(String userEmail, String tripFirebaseId, String documentId, 
                                      TripActivity activity, String firebaseImageUrl, 
                                      AtomicInteger completedActivities, int totalActivities, 
                                      OnTripSyncListener listener) {
        
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("description", activity.getDescription());
        updateData.put("location", activity.getLocation());
        updateData.put("timeString", activity.getTimeString());
        updateData.put("updatedAt", System.currentTimeMillis());
        
        // Only update image URL if we have a new Firebase URL
        if (firebaseImageUrl != null) {
            updateData.put("imageUrl", firebaseImageUrl);
            Log.d(TAG, "🖼️ Updating with new Firebase image URL");
        }
        
        firestore.collection(COLLECTION_USERS)
                .document(userEmail)
                .collection(COLLECTION_TRIPS)
                .document(tripFirebaseId)
                .collection(COLLECTION_ACTIVITIES)
                .document(documentId)
                .update(updateData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Activity updated in Firestore: " + activity.getTitle());
                    int completed = completedActivities.incrementAndGet();
                    if (completed == totalActivities) {
                        listener.onTripSynced(totalActivities);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "⚠️ Failed to update activity in Firestore: " + activity.getTitle(), e);
                    int completed = completedActivities.incrementAndGet();
                    if (completed == totalActivities) {
                        listener.onTripSynced(totalActivities);
                    }
                });
    }
    
    /**
     * Create new activity in Firestore
     */
    private void createNewActivity(String userEmail, String tripFirebaseId, TripActivity activity, 
                                 String firebaseImageUrl, AtomicInteger completedActivities, 
                                 int totalActivities, OnTripSyncListener listener) {
        
        // Create activity data (same as existing activity management)
        Map<String, Object> activityData = new HashMap<>();
        activityData.put("title", activity.getTitle());
        activityData.put("description", activity.getDescription());
        activityData.put("location", activity.getLocation());
        activityData.put("dateTime", activity.getDateTime());
        activityData.put("dayNumber", activity.getDayNumber());
        activityData.put("timeString", activity.getTimeString());
        activityData.put("createdAt", activity.getCreatedAt());
        activityData.put("updatedAt", activity.getUpdatedAt());
        
        // Add image URL (Firebase URL if uploaded, existing URL if already Firebase, or empty if none)
        if (firebaseImageUrl != null) {
            activityData.put("imageUrl", firebaseImageUrl);
            Log.d(TAG, "🖼️ Including Firebase image URL in new activity");
        } else if (activity.getImageUrl() != null && activity.getImageUrl().startsWith("https://firebasestorage.googleapis.com")) {
            activityData.put("imageUrl", activity.getImageUrl());
            Log.d(TAG, "🖼️ Including existing Firebase image URL in new activity");
        } else {
            activityData.put("imageUrl", "");
            Log.d(TAG, "📝 No image URL for new activity");
        }
        
        // Store in Firebase following existing structure: users/{email}/trips/{tripId}/activities
        firestore.collection(COLLECTION_USERS)
                .document(userEmail)
                .collection(COLLECTION_TRIPS)
                .document(tripFirebaseId)
                .collection(COLLECTION_ACTIVITIES)
                .add(activityData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "✅ New activity created in Firestore: " + activity.getTitle());
                    int completed = completedActivities.incrementAndGet();
                    if (completed == totalActivities) {
                        listener.onTripSynced(totalActivities);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "⚠️ Failed to create activity in Firestore: " + activity.getTitle(), e);
                    int completed = completedActivities.incrementAndGet();
                    if (completed == totalActivities) {
                        listener.onTripSynced(totalActivities);
                    }
                });
    }
    
    /**
     * Create a summary document of the sync operation
     */
    private void createSyncSummary(String userEmail, int tripsSynced, int activitiesSynced, Runnable onComplete) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("tripsSynced", tripsSynced);
        summary.put("activitiesSynced", activitiesSynced);
        summary.put("syncedAt", System.currentTimeMillis());
        summary.put("platform", "android");
        
        firestore.collection(COLLECTION_USERS)
                .document(userEmail)
                .set(summary, com.google.firebase.firestore.SetOptions.merge())
                .addOnCompleteListener(task -> onComplete.run());
    }
    
    /**
     * Test Firebase connectivity before syncing
     */
    private void testFirebaseConnection(String userEmail, OnSyncCompleteListener listener) {
        Log.d(TAG, "Testing Firebase connection...");
        
        // Test write to Firebase using same structure as trip management
        Map<String, Object> testData = new HashMap<>();
        testData.put("test", true);
        testData.put("timestamp", System.currentTimeMillis());
        testData.put("userEmail", userEmail);
        
        firestore.collection("connectivity_test")
                .document(userEmail)
                .set(testData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Firebase connectivity test PASSED");
                    // Delete test document
                    firestore.collection("connectivity_test").document(userEmail).delete();
                    // Proceed with actual sync
                    performActualSync(userEmail, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Firebase connectivity test FAILED", e);
                    listener.onError("Firebase connection failed: " + e.getMessage());
                });
    }
    
    /**
     * Perform the actual sync after connectivity test passes
     */
    private void performActualSync(String userEmail, OnSyncCompleteListener listener) {
        executor.execute(() -> {
            try {
                // Record sync start
                syncPrefs.setLastSyncStatus(SyncPreferences.SyncStatus.IN_PROGRESS);
                
                // Step 1: Get all local data
                listener.onProgressUpdate(10, "📊 Reading local data...");
                
                List<Trip> localTrips = tripRepository.getAllTripsSync();
                Log.d(TAG, "Found " + localTrips.size() + " local trips to sync");
                
                if (localTrips.isEmpty()) {
                    listener.onProgressUpdate(100, "✅ No local data to sync");
                    syncPrefs.recordSuccessfulSync(0, 0);
                    listener.onSuccess(0, 0);
                    return;
                }
                
                AtomicInteger totalTrips = new AtomicInteger(localTrips.size());
                AtomicInteger syncedTrips = new AtomicInteger(0);
                AtomicInteger syncedActivities = new AtomicInteger(0);
                AtomicInteger syncedBudgetEntries = new AtomicInteger(0);
                AtomicInteger completedTrips = new AtomicInteger(0);
                
                listener.onProgressUpdate(20, "🔄 Uploading " + localTrips.size() + " trips (with images)...");
                
                // Step 2: Sync each trip with its activities
                for (Trip trip : localTrips) {
                    syncTripAsJson(userEmail, trip, syncedBudgetEntries, new OnTripSyncListener() {
                        @Override
                        public void onTripSynced(int activitiesCount) {
                            int completed = completedTrips.incrementAndGet();
                            int synced = syncedTrips.incrementAndGet();
                            syncedActivities.addAndGet(activitiesCount);
                            
                            int progress = 20 + (completed * 70 / totalTrips.get());
                            listener.onProgressUpdate(progress, 
                                "✅ Synced trip " + completed + "/" + totalTrips.get() + 
                                " (" + activitiesCount + " activities, " + syncedBudgetEntries.get() + " budget entries)");
                            
                            if (completed == totalTrips.get()) {
                                // All trips completed
                                listener.onProgressUpdate(90, "🎯 Finalizing sync...");
                                
                                // Create summary document
                                createSyncSummary(userEmail, synced, syncedActivities.get(), () -> {
                                    listener.onProgressUpdate(100, "✅ Sync complete!");
                                    // Record successful sync
                                    syncPrefs.recordSuccessfulSync(synced, syncedActivities.get());
                                    listener.onSuccess(synced, syncedActivities.get());
                                });
                            }
                        }
                        
                        @Override
                        public void onError(String error) {
                            int completed = completedTrips.incrementAndGet();
                            Log.e(TAG, "Failed to sync trip: " + trip.getTitle() + " - " + error);
                            
                            if (completed == totalTrips.get()) {
                                // All trips completed (with some failures)
                                listener.onSuccess(syncedTrips.get(), syncedActivities.get());
                            }
                        }
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error during data sync", e);
                syncPrefs.recordFailedSync();
                listener.onError("Sync failed: " + e.getMessage());
            }
        });
    }

    /**
     * Interface for trip sync callbacks
     */
    private interface OnTripSyncListener {
        void onTripSynced(int activitiesCount);
        void onError(String error);
    }
    
    private interface OnBudgetSyncCompleteListener {
        void onBudgetSyncComplete(int budgetEntriesSynced);
        void onBudgetSyncError(String error);
    }
    
    /**
     * Sync budget data for a specific trip (follows BudgetRepository pattern)
     */
    private void syncBudgetForTrip(int tripId, String userEmail, OnBudgetSyncCompleteListener listener) {
        Log.d(TAG, "💰 Starting budget sync for trip ID: " + tripId);
        
        executor.execute(() -> {
            try {
                // Load local budget data
                BudgetRepository.BudgetData localBudgetData = budgetRepository.loadBudgetDataLocally();
                
                int budgetEntriesSynced = 0;
                boolean hasErrors = false;
                StringBuilder errorMessages = new StringBuilder();
                
                // Check if this trip has budget data
                Double tripBudget = localBudgetData.tripBudgets.get(tripId);
                List<Expense> tripExpenses = localBudgetData.tripExpenses.get(tripId);
                
                if (tripBudget == null && (tripExpenses == null || tripExpenses.isEmpty())) {
                    Log.d(TAG, "💰 No budget data found for trip " + tripId);
                    listener.onBudgetSyncComplete(0);
                    return;
                }
                
                Log.d(TAG, "💰 Found budget data for trip " + tripId + ":");
                Log.d(TAG, "   Budget: " + (tripBudget != null ? "RM" + tripBudget : "none"));
                Log.d(TAG, "   Expenses: " + (tripExpenses != null ? tripExpenses.size() : 0));
                
                // Sync trip budget if exists
                if (tripBudget != null) {
                    try {
                        // Use synchronous approach for cleaner error handling
                        syncTripBudgetSync(tripId, tripBudget, userEmail);
                        budgetEntriesSynced++;
                        Log.d(TAG, "✅ Trip budget synced: RM" + tripBudget);
                    } catch (Exception e) {
                        hasErrors = true;
                        errorMessages.append("Budget sync error: ").append(e.getMessage()).append("; ");
                        Log.w(TAG, "⚠️ Failed to sync trip budget: " + e.getMessage());
                    }
                }
                
                // Sync expenses if exist
                if (tripExpenses != null && !tripExpenses.isEmpty()) {
                    for (Expense expense : tripExpenses) {
                        try {
                            expense.setTripId(tripId); // Ensure trip ID is set
                            syncExpenseSync(expense, userEmail);
                            budgetEntriesSynced++;
                            Log.d(TAG, "✅ Expense synced: " + expense.getTitle() + " (RM" + expense.getAmount() + ")");
                        } catch (Exception e) {
                            hasErrors = true;
                            errorMessages.append("Expense sync error: ").append(e.getMessage()).append("; ");
                            Log.w(TAG, "⚠️ Failed to sync expense " + expense.getTitle() + ": " + e.getMessage());
                        }
                    }
                }
                
                // Report results
                final int finalBudgetEntriesSynced = budgetEntriesSynced;
                final boolean finalHasErrors = hasErrors;
                final String finalErrorMessages = errorMessages.toString();
                
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (finalHasErrors && finalBudgetEntriesSynced == 0) {
                        listener.onBudgetSyncError("Failed to sync budget data: " + finalErrorMessages);
                    } else {
                        if (finalHasErrors) {
                            Log.w(TAG, "⚠️ Partial budget sync: " + finalBudgetEntriesSynced + " entries synced with errors: " + finalErrorMessages);
                        }
                        listener.onBudgetSyncComplete(finalBudgetEntriesSynced);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error in budget sync for trip " + tripId, e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    listener.onBudgetSyncError("Budget sync setup failed: " + e.getMessage());
                });
            }
        });
    }
    
    /**
     * Synchronous trip budget sync (blocks until complete)
     */
    private void syncTripBudgetSync(int tripId, double budget, String userEmail) throws Exception {
        final Exception[] syncException = new Exception[1];
        final boolean[] syncComplete = new boolean[1];
        
        budgetRepository.syncTripBudgetToFirebase(tripId, budget, userEmail, new BudgetRepository.OnBudgetOperationListener() {
            @Override
            public void onSuccess() {
                synchronized (syncComplete) {
                    syncComplete[0] = true;
                    syncComplete.notify();
                }
            }
            
            @Override
            public void onError(String error) {
                synchronized (syncComplete) {
                    syncException[0] = new Exception(error);
                    syncComplete[0] = true;
                    syncComplete.notify();
                }
            }
        });
        
        // Wait for completion
        synchronized (syncComplete) {
            while (!syncComplete[0]) {
                try {
                    syncComplete.wait(30000); // 30 second timeout
                    if (!syncComplete[0]) {
                        throw new Exception("Budget sync timeout");
                    }
                } catch (InterruptedException e) {
                    throw new Exception("Budget sync interrupted");
                }
            }
        }
        
        if (syncException[0] != null) {
            throw syncException[0];
        }
    }
    
    /**
     * Synchronous expense sync (blocks until complete)
     */
    private void syncExpenseSync(Expense expense, String userEmail) throws Exception {
        final Exception[] syncException = new Exception[1];
        final boolean[] syncComplete = new boolean[1];
        
        budgetRepository.syncExpenseToFirebase(expense, userEmail, new BudgetRepository.OnBudgetOperationListener() {
            @Override
            public void onSuccess() {
                synchronized (syncComplete) {
                    syncComplete[0] = true;
                    syncComplete.notify();
                }
            }
            
            @Override
            public void onError(String error) {
                synchronized (syncComplete) {
                    syncException[0] = new Exception(error);
                    syncComplete[0] = true;
                    syncComplete.notify();
                }
            }
        });
        
        // Wait for completion
        synchronized (syncComplete) {
            while (!syncComplete[0]) {
                try {
                    syncComplete.wait(30000); // 30 second timeout
                    if (!syncComplete[0]) {
                        throw new Exception("Expense sync timeout");
                    }
                } catch (InterruptedException e) {
                    throw new Exception("Expense sync interrupted");
                }
            }
        }
        
        if (syncException[0] != null) {
            throw syncException[0];
        }
    }
} 