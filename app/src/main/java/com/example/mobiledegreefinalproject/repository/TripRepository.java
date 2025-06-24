package com.example.mobiledegreefinalproject.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.mobiledegreefinalproject.UserManager;
import com.example.mobiledegreefinalproject.database.Trip;
import com.example.mobiledegreefinalproject.database.TripActivity;
import com.example.mobiledegreefinalproject.database.TripActivityDao;
import com.example.mobiledegreefinalproject.database.TripDao;
import com.example.mobiledegreefinalproject.database.WanderPlanDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TripRepository {

    private static final String TAG = "TripRepository";
    private static final boolean FORCE_LOCAL_ONLY = false; // Set to true to test without Firebase
    private static volatile TripRepository INSTANCE;
    
    private final TripDao tripDao;
    private final TripActivityDao activityDao;
    private final UserManager userManager;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;
    private final ExecutorService executor;
    
    // Add a set to track activities being deleted to prevent race conditions
    private final Set<String> activitiesBeingDeleted = new HashSet<>();
    private final Map<String, Long> deletionTimestamps = new HashMap<>();
    private static final long DELETION_TIMEOUT_MS = 5000; // FIXED: 5 seconds timeout instead of 30 seconds for faster cleanup
    
    // CRITICAL FIX: Add flag to disable real-time updates when using direct Firebase loading
    private boolean realTimeUpdatesEnabled = true;
    
    // NUCLEAR PROTECTION: Completely block operations when in Firebase-only mode
    private boolean isNuclearFirebaseMode = false;
    
    private TripRepository(Context context) {
        try {
            Log.d(TAG, "Initializing TripRepository");
            WanderPlanDatabase database = WanderPlanDatabase.getInstance(context);
            tripDao = database.tripDao();
            activityDao = database.tripActivityDao();
            userManager = UserManager.getInstance(context);
            firestore = FirebaseFirestore.getInstance();
            storage = FirebaseStorage.getInstance();
            executor = Executors.newFixedThreadPool(4);
            Log.d(TAG, "TripRepository initialized successfully");
            
            // Test database connectivity
            executor.execute(() -> {
                try {
                    int tripCount = tripDao.getTripCount();
                    Log.d(TAG, "Database test successful. Current trip count: " + tripCount);
                } catch (Exception e) {
                    Log.e(TAG, "Database test failed", e);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TripRepository", e);
            throw e;
        }
    }
    
    public static TripRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TripRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TripRepository(context);
                }
            }
        }
        return INSTANCE;
    }
    
    // Call this method when user logs in to start real-time sync
    public void initializeRealtimeSync() {
        if (userManager.isLoggedIn()) {
            Log.d(TAG, "Initializing real-time Firebase sync for logged-in user");
            setupFirebaseTripsListener();
            setupAllActivitiesListeners();
        } else {
            Log.d(TAG, "User not logged in, skipping real-time sync initialization");
        }
    }
    
    // Trip operations - Smart data source switching
    public LiveData<List<Trip>> getAllTrips() {
        if (userManager.isLoggedIn()) {
            // For logged-in users: Use Firebase as primary source, local as cache
            Log.d(TAG, "User logged in - using Firebase-first data retrieval");
            return getTripsFromFirebaseFirst();
        } else {
            // For guests: Use local database only
            Log.d(TAG, "Guest user - using local database only");
            return tripDao.getAllTrips();
        }
    }
    
    // Firebase-first data retrieval for logged-in users with real-time sync
    private LiveData<List<Trip>> getTripsFromFirebaseFirst() {
        Log.d(TAG, "=== FIREBASE-FIRST DATA RETRIEVAL WITH REAL-TIME SYNC ===");
        
        // First, run immediate cleanup of any existing duplicates
        executor.execute(() -> {
            cleanupDuplicateTripsAfterSync();
        });
        
        // Return local data immediately for fast UI
        LiveData<List<Trip>> localData = tripDao.getAllTrips();
        
        // Set up real-time Firebase listener for automatic sync
        setupFirebaseTripsListener();
        
        // Also do initial fetch for immediate sync and cleanup duplicates
        executor.execute(() -> {
            fetchTripsFromFirebase(new OnTripSyncListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Initial Firebase data refresh completed successfully");
                    // Clean up any duplicate trips that might exist after syncing
                    cleanupDuplicateTripsAfterSync();
                    
                    // Schedule another cleanup after a delay to catch any late duplicates
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        executor.execute(() -> {
                            cleanupDuplicateTripsAfterSync();
                        });
                    }, 5000);
                }
                
                @Override
                public void onError(String error) {
                    Log.w(TAG, "Initial Firebase data refresh failed: " + error);
                    // Still run cleanup on local data
                    cleanupDuplicateTripsAfterSync();
                }
            });
        });
        
        return localData;
    }
    
    // Clean up duplicate trips after Firebase sync
    private void cleanupDuplicateTripsAfterSync() {
        executor.execute(() -> {
            try {
                Log.d(TAG, "=== STARTING COMPREHENSIVE DUPLICATE CLEANUP ===");
                
                List<Trip> allTrips = tripDao.getAllTripsSync();
                Log.d(TAG, "Total trips before cleanup: " + allTrips.size());
                
                // Group trips by content (title + destination + dates) to find duplicates
                Map<String, List<Trip>> tripGroups = new LinkedHashMap<>();
                
                for (Trip trip : allTrips) {
                    String contentKey = trip.getTitle().trim().toLowerCase() + "|" + 
                                       trip.getDestination().trim().toLowerCase() + "|" + 
                                       trip.getStartDate() + "|" + trip.getEndDate();
                    
                    if (!tripGroups.containsKey(contentKey)) {
                        tripGroups.put(contentKey, new ArrayList<>());
                    }
                    tripGroups.get(contentKey).add(trip);
                }
                
                int removedCount = 0;
                
                // Process each group of potentially duplicate trips
                for (Map.Entry<String, List<Trip>> entry : tripGroups.entrySet()) {
                    List<Trip> duplicates = entry.getValue();
                    
                    if (duplicates.size() > 1) {
                        Log.d(TAG, "Found " + duplicates.size() + " duplicate trips with content: " + entry.getKey());
                        
                        // Sort duplicates: Firebase trips first, then by creation date
                        duplicates.sort((trip1, trip2) -> {
                            // Firebase trips have higher priority
                            boolean trip1HasFirebase = trip1.getFirebaseId() != null && !trip1.getFirebaseId().isEmpty();
                            boolean trip2HasFirebase = trip2.getFirebaseId() != null && !trip2.getFirebaseId().isEmpty();
                            
                            if (trip1HasFirebase && !trip2HasFirebase) return -1;
                            if (!trip1HasFirebase && trip2HasFirebase) return 1;
                            
                            // If both have Firebase ID or both don't, sort by creation date (newer first)
                            return Long.compare(trip2.getCreatedAt(), trip1.getCreatedAt());
                        });
                        
                        // Keep the first trip (highest priority) and remove the rest
                        Trip keepTrip = duplicates.get(0);
                        Log.d(TAG, "Keeping trip: " + keepTrip.getTitle() + " (ID: " + keepTrip.getId() + 
                              ", Firebase: " + (keepTrip.getFirebaseId() != null ? keepTrip.getFirebaseId() : "none") + ")");
                        
                        // Remove all other duplicates
                        for (int i = 1; i < duplicates.size(); i++) {
                            Trip duplicateTrip = duplicates.get(i);
                            
                            Log.d(TAG, "Removing duplicate trip: " + duplicateTrip.getTitle() + " (ID: " + duplicateTrip.getId() + 
                                  ", Firebase: " + (duplicateTrip.getFirebaseId() != null ? duplicateTrip.getFirebaseId() : "none") + ")");
                            
                            // Move any activities from duplicate to the kept trip
                            try {
                                moveActivitiesFromLocalToFirebaseTrip(duplicateTrip, keepTrip);
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to move activities from duplicate trip", e);
                            }
                            
                            // Delete the duplicate trip
                            try {
                                tripDao.deleteTrip(duplicateTrip);
                                removedCount++;
                                Log.d(TAG, "✓ Successfully removed duplicate trip: " + duplicateTrip.getTitle());
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to delete duplicate trip: " + duplicateTrip.getTitle(), e);
                            }
                        }
                    }
                }
                
                // Additional cleanup: Remove trips with exactly the same Firebase ID (shouldn't happen but just in case)
                Map<String, Trip> firebaseIdMap = new HashMap<>();
                List<Trip> finalTrips = tripDao.getAllTripsSync();
                
                for (Trip trip : finalTrips) {
                    if (trip.getFirebaseId() != null && !trip.getFirebaseId().isEmpty()) {
                        Trip existing = firebaseIdMap.get(trip.getFirebaseId());
                        if (existing != null) {
                            // Duplicate Firebase ID - keep the newer one
                            if (trip.getUpdatedAt() > existing.getUpdatedAt()) {
                                tripDao.deleteTrip(existing);
                                firebaseIdMap.put(trip.getFirebaseId(), trip);
                                removedCount++;
                                Log.d(TAG, "Removed older trip with duplicate Firebase ID: " + existing.getTitle());
                            } else {
                                tripDao.deleteTrip(trip);
                                removedCount++;
                                Log.d(TAG, "Removed newer trip with duplicate Firebase ID: " + trip.getTitle());
                            }
                        } else {
                            firebaseIdMap.put(trip.getFirebaseId(), trip);
                        }
                    }
                }
                
                int finalTripCount = tripDao.getAllTripsSync().size();
                
                if (removedCount > 0) {
                    Log.d(TAG, "=== CLEANUP COMPLETED ===");
                    Log.d(TAG, "Removed " + removedCount + " duplicate trips");
                    Log.d(TAG, "Trip count: " + allTrips.size() + " → " + finalTripCount);
                } else {
                    Log.d(TAG, "=== CLEANUP COMPLETED - No duplicates found ===");
                    Log.d(TAG, "Final trip count: " + finalTripCount);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error during comprehensive duplicate cleanup", e);
            }
        });
    }
    
    // Move activities from local trip to Firebase trip before deleting local trip
    private void moveActivitiesFromLocalToFirebaseTrip(Trip localTrip, Trip firebaseTrip) {
        try {
            List<TripActivity> localActivities = activityDao.getActivitiesForTripSync(localTrip.getId());
            
            if (!localActivities.isEmpty()) {
                Log.d(TAG, "Moving " + localActivities.size() + " activities from local trip to Firebase trip");
                
                for (TripActivity activity : localActivities) {
                    // Check if similar activity already exists in Firebase trip
                    List<TripActivity> firebaseActivities = activityDao.getActivitiesForTripSync(firebaseTrip.getId());
                    boolean duplicateExists = false;
                    
                    for (TripActivity firebaseActivity : firebaseActivities) {
                        if (activitiesAreSimilar(activity, firebaseActivity)) {
                            duplicateExists = true;
                            break;
                        }
                    }
                    
                    if (!duplicateExists) {
                        // Move activity to Firebase trip
                        activity.setTripId(firebaseTrip.getId());
                        activity.setFirebaseId(null); // Reset Firebase ID so it gets synced again
                        activity.setSynced(false);
                        activityDao.updateActivity(activity);
                        Log.d(TAG, "Moved activity '" + activity.getTitle() + "' to Firebase trip");
                    } else {
                        Log.d(TAG, "Activity '" + activity.getTitle() + "' already exists in Firebase trip, skipping");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error moving activities from local to Firebase trip", e);
        }
    }
    
    // Check if two activities are similar (same title, time, and description)
    private boolean activitiesAreSimilar(TripActivity activity1, TripActivity activity2) {
        if (activity1 == null || activity2 == null) return false;
        
        boolean titleMatch = (activity1.getTitle() != null && activity1.getTitle().equals(activity2.getTitle()));
        boolean timeMatch = (activity1.getDateTime() == activity2.getDateTime());
        boolean descMatch = Objects.equals(activity1.getDescription(), activity2.getDescription());
        
        return titleMatch && timeMatch && descMatch;
    }
    
    public List<Trip> getUnsyncedTripsSync() {
        return tripDao.getUnsyncedTrips();
    }
    
    public List<Trip> getAllTripsSync() {
        return tripDao.getAllTripsSync();
    }
    
    public void clearUnsyncedTrips() {
        executor.execute(() -> {
            try {
                List<Trip> unsyncedTrips = tripDao.getUnsyncedTrips();
                for (Trip trip : unsyncedTrips) {
                    tripDao.deleteTrip(trip);
                }
                Log.d(TAG, "Cleared " + unsyncedTrips.size() + " unsynced trips");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing unsynced trips", e);
            }
        });
    }
    
    public void clearFirebaseTripsFromLocal() {
        executor.execute(() -> {
            try {
                List<Trip> allTrips = tripDao.getAllTripsSync();
                for (Trip trip : allTrips) {
                    if (trip.getFirebaseId() != null && !trip.getFirebaseId().isEmpty()) {
                        // Delete Firebase-synced trips
                        tripDao.deleteTrip(trip);
                    }
                }
                Log.d(TAG, "Cleared Firebase-synced trips from local storage");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing Firebase trips", e);
            }
        });
    }
    
    public void clearAllLocalTrips() {
        executor.execute(() -> {
            try {
                List<Trip> allTrips = tripDao.getAllTripsSync();
                for (Trip trip : allTrips) {
                    // Delete all trips (this will cascade delete all activities)
                    tripDao.deleteTrip(trip);
                }
                Log.d(TAG, "Cleared all " + allTrips.size() + " local trips from database");
            } catch (Exception e) {
                Log.e(TAG, "Error clearing all local trips", e);
            }
        });
    }
    
    public void forceSyncTripToFirebase(Trip trip, OnTripOperationListener listener) {
        if (!userManager.isLoggedIn()) {
            if (listener != null) {
                listener.onError("User not logged in");
            }
            return;
        }
        
        // Call the existing private sync method directly
        syncTripToFirebase(trip, listener);
    }
    
    /**
     * Update trip Firebase ID in database
     */
    public void updateTripFirebaseId(int tripId, String firebaseId) {
        executor.execute(() -> {
            try {
                tripDao.updateTripFirebaseId(tripId, firebaseId);
                Log.d(TAG, "Updated Firebase ID for trip " + tripId + ": " + firebaseId);
            } catch (Exception e) {
                Log.e(TAG, "Error updating trip Firebase ID", e);
            }
        });
    }
    
    // Public method to manually trigger duplicate cleanup
    public void forceCleanupDuplicateTrips(OnTripSyncListener listener) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "=== MANUAL DUPLICATE CLEANUP REQUESTED ===");
                
                // First, run the comprehensive cleanup
                cleanupDuplicateTripsAfterSync();
                
                // Wait a moment for cleanup to complete
                Thread.sleep(1000);
                
                // Get final count
                int finalCount = tripDao.getAllTripsSync().size();
                Log.d(TAG, "Manual cleanup completed. Final trip count: " + finalCount);
                
                if (listener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onSuccess();
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error during manual duplicate cleanup", e);
                if (listener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onError("Cleanup failed: " + e.getMessage());
                    });
                }
            }
        });
    }
    
    public void fetchTripsFromFirebase(OnTripSyncListener listener) {
        if (!userManager.isLoggedIn()) {
            if (listener != null) {
                listener.onError("User not logged in");
            }
            return;
        }
        
        String userId = userManager.getUserEmail();
        if (userId == null || userId.isEmpty()) {
            if (listener != null) {
                listener.onError("User ID not available");
            }
            return;
        }
        
        Log.d(TAG, "Fetching trips from Firebase for user: " + userId);
        
        firestore.collection("users")
            .document(userId)
            .collection("trips")
            .get()
            .addOnSuccessListener(querySnapshot -> {
                executor.execute(() -> {
                    try {
                        Log.d(TAG, "Found " + querySnapshot.size() + " trips in Firebase");
                        final int totalTrips = querySnapshot.size();
                        
                        if (totalTrips == 0) {
                            if (listener != null) {
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    listener.onSuccess();
                                });
                            }
                            return;
                        }
                        
                        for (com.google.firebase.firestore.QueryDocumentSnapshot tripDoc : querySnapshot) {
                            processFirebaseTripDocument(tripDoc);
                        }
                        
                        if (listener != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                listener.onSuccess();
                            });
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Firebase trips", e);
                        if (listener != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                listener.onError("Error processing trips: " + e.getMessage());
                            });
                        }
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching trips from Firebase", e);
                if (listener != null) {
                    listener.onError("Failed to fetch trips: " + e.getMessage());
                }
            });
    }
    
    public LiveData<Trip> getTripById(int tripId) {
        return tripDao.getTripById(tripId);
    }
    
    public Trip getTripByIdSync(int tripId) {
        return tripDao.getTripByIdSync(tripId);
    }
    
    public void insertTrip(Trip trip, OnTripOperationListener listener) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Attempting to insert trip into database");
                Log.d(TAG, "Trip details: " + trip.getTitle() + ", " + trip.getDestination() + 
                      ", start: " + trip.getStartDate() + ", end: " + trip.getEndDate());
                
                // Add validation
                if (trip == null) {
                    Log.e(TAG, "Trip object is null");
                    if (listener != null) {
                        listener.onError("Invalid trip data");
                    }
                    return;
                }
                
                if (tripDao == null) {
                    Log.e(TAG, "TripDao is null");
                    if (listener != null) {
                        listener.onError("Database not initialized");
                    }
                    return;
                }
                
                long tripId = tripDao.insertTrip(trip);
                trip.setId((int) tripId);
                
                Log.d(TAG, "Trip inserted successfully with ID: " + tripId);
                
                // Check user login state safely
                boolean isUserLoggedIn = false;
                try {
                    isUserLoggedIn = userManager != null && userManager.isLoggedIn();
                } catch (Exception e) {
                    Log.w(TAG, "Error checking login state: " + e.getMessage());
                    isUserLoggedIn = false;
                }
                
                if (isUserLoggedIn && !FORCE_LOCAL_ONLY) {
                    Log.d(TAG, "User is logged in, attempting Firebase sync");
                    // Add timeout protection for Firebase sync
                    syncTripToFirebaseWithTimeout(trip, listener, 30000);
                } else {
                    if (FORCE_LOCAL_ONLY) {
                        Log.d(TAG, "FORCE_LOCAL_ONLY mode enabled, skipping Firebase sync");
                    } else {
                        Log.d(TAG, "User is guest or login check failed, skipping Firebase sync");
                    }
                    if (listener != null) {
                        listener.onSuccess((int) tripId);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Detailed error inserting trip", e);
                if (listener != null) {
                    try {
                        listener.onError("Failed to save trip: " + e.getMessage());
                    } catch (Exception callbackError) {
                        Log.e(TAG, "Error in error callback", callbackError);
                    }
                }
            }
        });
    }
    
    public void updateTrip(Trip trip, OnTripOperationListener listener) {
        executor.execute(() -> {
            try {
                tripDao.updateTrip(trip);
                
                if (userManager.isLoggedIn() && trip.getFirebaseId() != null) {
                    syncTripToFirebase(trip, listener);
                } else {
                    if (listener != null) {
                        listener.onSuccess(trip.getId());
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error updating trip", e);
                if (listener != null) {
                    listener.onError("Failed to update trip: " + e.getMessage());
                }
            }
        });
    }
    
    public void deleteTrip(Trip trip, OnTripOperationListener listener) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Deleting trip: " + trip.getTitle());
                
                // Get all activities for this trip before deletion
                List<TripActivity> activities = activityDao.getActivitiesForTripSync(trip.getId());
                Log.d(TAG, "Found " + activities.size() + " activities to delete");
                
                // Delete from local database first (cascades to activities)
                tripDao.deleteTrip(trip);
                Log.d(TAG, "Trip deleted from local database");
                
                if (userManager.isLoggedIn() && trip.getFirebaseId() != null) {
                    // Delete from Firebase (trip and all activities + images)
                    deleteFirebaseTripWithActivities(trip, activities, listener);
                } else {
                    Log.d(TAG, "Guest mode or no Firebase ID, deletion complete");
                    if (listener != null) {
                        listener.onSuccess(trip.getId());
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error deleting trip", e);
                if (listener != null) {
                    listener.onError("Failed to delete trip: " + e.getMessage());
                }
            }
        });
    }
    
    private void deleteFirebaseTripWithActivities(Trip trip, List<TripActivity> activities, OnTripOperationListener listener) {
        String userEmail = userManager.getUserEmail();
        String tripFirebaseId = trip.getFirebaseId();
        
        Log.d(TAG, "Deleting Firebase trip and " + activities.size() + " activities");
        
        // Delete all activity images from Firebase Storage first
        for (TripActivity activity : activities) {
            if (activity.getImageUrl() != null && activity.getImageUrl().startsWith("https://")) {
                deleteImageFromFirebaseStorage(activity.getImageUrl());
            }
        }
        
        // Delete all activities from Firestore
        firestore.collection("users")
            .document(userEmail)
            .collection("trips")
            .document(tripFirebaseId)
            .collection("activities")
            .get()
            .addOnSuccessListener(querySnapshot -> {
                Log.d(TAG, "Found " + querySnapshot.size() + " activities in Firebase to delete");
                
                // Delete each activity document
                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                    doc.getReference().delete();
                }
                
                // Delete associated budget records before deleting the trip
                deleteTripBudgetRecords(trip.getId(), userEmail, () -> {
                    // Finally delete the trip document
                    firestore.collection("users")
                        .document(userEmail)
                        .collection("trips")
                        .document(tripFirebaseId)
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Trip, activities, and budget records deleted from Firebase successfully");
                            if (listener != null) {
                                listener.onSuccess(trip.getId());
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error deleting trip from Firebase", e);
                            if (listener != null) {
                                listener.onError("Failed to delete trip from Firebase: " + e.getMessage());
                            }
                        });
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching activities for trip deletion", e);
                if (listener != null) {
                    listener.onError("Failed to delete trip activities: " + e.getMessage());
                }
            });
    }
    
    private void deleteImageFromFirebaseStorage(String imageUrl) {
        try {
            Log.d(TAG, "🖼️ Deleting image from Firebase Storage (async): " + imageUrl);
            com.google.firebase.storage.StorageReference imageRef = storage.getReferenceFromUrl(imageUrl);
            imageRef.delete()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "✅ Image deleted from Firebase Storage (async)"))
                .addOnFailureListener(e -> Log.w(TAG, "⚠️ Failed to delete image from Firebase Storage (async)", e));
        } catch (Exception e) {
            Log.w(TAG, "Error deleting image from Firebase Storage (async)", e);
        }
    }
    
    private void deleteTripBudgetRecords(int tripId, String userEmail, Runnable onComplete) {
        Log.d(TAG, "Deleting budget records for trip ID: " + tripId);
        
        // First get the trip's Firebase ID
        executor.execute(() -> {
            try {
                Trip trip = tripDao.getTripByIdSync(tripId);
                
                if (trip == null || trip.getFirebaseId() == null || trip.getFirebaseId().isEmpty()) {
                    Log.w(TAG, "Cannot delete budget records - trip not found or not synced to Firebase");
                    onComplete.run(); // Continue anyway
                    return;
                }
                
                // CRITICAL FIX: Use correct Firebase collection path that matches BudgetRepository
                // Delete entire budget collection for this trip: users/{email}/trips/{tripId}/budget/*
                firestore.collection("users")
                    .document(userEmail)
                    .collection("trips")
                    .document(trip.getFirebaseId())
                    .collection("budget")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (querySnapshot.isEmpty()) {
                            Log.d(TAG, "No budget records found for trip " + tripId);
                            onComplete.run();
                            return;
                        }
                        
                        Log.d(TAG, "Found " + querySnapshot.size() + " budget records to delete for trip " + tripId);
                        
                        // Delete each budget document
                        int totalDocs = querySnapshot.size();
                        final int[] deletedCount = {0};
                        final boolean[] hasError = {false};
                        
                        for (com.google.firebase.firestore.DocumentSnapshot document : querySnapshot) {
                            document.getReference().delete()
                                .addOnSuccessListener(aVoid -> {
                                    synchronized (deletedCount) {
                                        deletedCount[0]++;
                                        Log.d(TAG, "Deleted budget record: " + document.getId());
                                        
                                        if (deletedCount[0] >= totalDocs) {
                                            // All deletions completed
                                            if (hasError[0]) {
                                                Log.w(TAG, "Some budget records could not be deleted, but continuing");
                                            } else {
                                                Log.d(TAG, "All budget records deleted successfully for trip " + tripId);
                                            }
                                            onComplete.run();
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    synchronized (deletedCount) {
                                        deletedCount[0]++;
                                        hasError[0] = true;
                                        Log.e(TAG, "Error deleting budget record: " + document.getId(), e);
                                        
                                        if (deletedCount[0] >= totalDocs) {
                                            // All deletions completed
                                            Log.w(TAG, "Budget record deletion completed with errors for trip " + tripId);
                                            onComplete.run();
                                        }
                                    }
                                });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching budget records for deletion", e);
                        // Continue anyway - don't let budget deletion failure block trip deletion
                        onComplete.run();
                    });
                    
            } catch (Exception e) {
                Log.e(TAG, "Exception getting trip for budget deletion", e);
                onComplete.run(); // Continue anyway
            }
        });
    }
    
    public LiveData<List<TripActivity>> getActivitiesForTrip(int tripId) {
        if (userManager.isLoggedIn()) {
            return getActivitiesFromFirebaseFirst(tripId);
        } else {
            return activityDao.getActivitiesForTrip(tripId);
        }
    }
    
    // Firebase-first activity retrieval for logged-in users (with controlled refresh)
    private LiveData<List<TripActivity>> getActivitiesFromFirebaseFirst(int tripId) {
        Log.d(TAG, "=== FIREBASE-FIRST ACTIVITY RETRIEVAL for trip: " + tripId + " ===");
        
        // CRITICAL FIX: DO NOT clean up duplicates for Firebase users!
        // This was causing data to disappear when the cleanup modified local data
        // cleanupLocalDuplicateActivities(tripId); // DISABLED - causes UI flicker
        Log.d(TAG, "SKIPPING duplicate cleanup for Firebase user to prevent UI interference");
        
        // First, return local data immediately for fast UI
        LiveData<List<TripActivity>> localData = activityDao.getActivitiesForTrip(tripId);
        
        // Only refresh from Firebase if local data seems outdated (no recent activity)
        executor.execute(() -> {
            try {
                // Check if we have recent local activities
                List<TripActivity> localActivities = activityDao.getActivitiesForTripSync(tripId);
                long currentTime = System.currentTimeMillis();
                boolean hasRecentActivity = false;
                
                for (TripActivity activity : localActivities) {
                    if (currentTime - activity.getUpdatedAt() < 60000) { // Less than 1 minute old
                        hasRecentActivity = true;
                        break;
                    }
                }
                
                if (hasRecentActivity) {
                    Log.d(TAG, "Local data is recent, skipping Firebase refresh for trip: " + tripId);
                    // CRITICAL FIX: Disable cleanup that deletes synced Firebase activities
                    // cleanupLocalDuplicateActivities(tripId); // DISABLED - was deleting displayed Firebase data!
                    return;
                }
                
                // Get trip's Firebase ID for refresh
                Trip trip = tripDao.getTripByIdSync(tripId);
                if (trip != null && trip.getFirebaseId() != null && !trip.getFirebaseId().isEmpty()) {
                    Log.d(TAG, "Local data is outdated, refreshing from Firebase for trip: " + trip.getTitle());
                    
                    // Refresh activities from Firebase (less frequently)
                    fetchActivitiesForTrip(trip.getFirebaseId(), tripId, () -> {
                        Log.d(TAG, "Firebase activity refresh completed for trip: " + tripId);
                    });
                } else {
                    Log.d(TAG, "Trip has no Firebase ID, using local data only");
                }
            } catch (Exception e) {
                Log.w(TAG, "Error during Firebase activity refresh: " + e.getMessage());
                // Continue with local data
            }
        });
        
        return localData;
    }
    
    private void fetchActivitiesForTrip(String tripFirebaseId, int localTripId, Runnable onComplete) {
        Log.d(TAG, "Fetching activities for trip Firebase ID: " + tripFirebaseId + ", local ID: " + localTripId);
        
        String userId = userManager.getUserEmail();
        firestore.collection("users")
            .document(userId)
            .collection("trips")
            .document(tripFirebaseId)
            .collection("activities")
            .get()
            .addOnSuccessListener(activitySnapshot -> {
                executor.execute(() -> {
                    try {
                        Log.d(TAG, "Found " + activitySnapshot.size() + " activities for trip " + tripFirebaseId);
                        
                        // IMPROVED: Only remove duplicates instead of clearing all Firebase activities
                        List<TripActivity> existingActivities = activityDao.getActivitiesForTripSync(localTripId);
                        Log.d(TAG, "Found " + existingActivities.size() + " existing local activities for comparison");
                        
                        // Now process all Firebase activities
                        for (com.google.firebase.firestore.QueryDocumentSnapshot activityDoc : activitySnapshot) {
                            processFirebaseActivityDocument(activityDoc, localTripId);
                        }
                        
                        Log.d(TAG, "Completed processing activities for trip " + tripFirebaseId);
                        onComplete.run();
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Firebase activities", e);
                        onComplete.run(); // Complete anyway to avoid hanging
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error fetching activities for trip " + tripFirebaseId, e);
                onComplete.run(); // Complete anyway to avoid hanging
            });
    }
    
    public LiveData<List<TripActivity>> getActivitiesForDay(int tripId, int dayNumber) {
        return activityDao.getActivitiesForDay(tripId, dayNumber);
    }
    
    // CRITICAL FIX: Add synchronous method for safe deletion operations
    public List<TripActivity> getActivitiesForTripSync(int tripId) {
        return activityDao.getActivitiesForTripSync(tripId);
    }

    public void insertActivity(TripActivity activity, OnActivityOperationListener listener) {
        executor.execute(() -> {
            try {
                long localId = activityDao.insertActivity(activity);
                activity.setId((int) localId);
                Log.d(TAG, "Activity '" + activity.getTitle() + "' inserted locally with ID: " + localId);

                if (!userManager.isLoggedIn()) {
                    if (listener != null) runOnUiThread(() -> listener.onSuccess(activity.getId()));
                    return;
                }
                syncActivityToFirebase(activity, listener);
            } catch (Exception e) {
                Log.e(TAG, "Error during local activity insertion", e);
                if (listener != null) runOnUiThread(() -> listener.onError(e.getMessage()));
            }
        });
    }

    public void updateActivity(TripActivity activity, OnActivityOperationListener listener) {
        executor.execute(() -> {
            try {
                activityDao.updateActivity(activity);
                Log.d(TAG, "Activity '" + activity.getTitle() + "' updated locally.");

                if (!userManager.isLoggedIn()) {
                    if (listener != null) runOnUiThread(() -> listener.onSuccess(activity.getId()));
                    return;
                }
                syncActivityToFirebase(activity, listener);
            } catch (Exception e) {
                Log.e(TAG, "Error during local activity update", e);
                if (listener != null) runOnUiThread(() -> listener.onError(e.getMessage()));
            }
        });
    }

    public void deleteActivity(TripActivity activity, OnActivityOperationListener listener) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Starting activity deletion: " + activity.getTitle() + " (ID: " + activity.getId() + ")");
                
                // Quick check if activity exists and delete immediately for responsive UI
                TripActivity existingActivity = null;
                try {
                    existingActivity = activityDao.getActivityByIdSync(activity.getId());
                } catch (Exception e) {
                    Log.e(TAG, "Error checking activity existence", e);
                }
                
                if (existingActivity == null) {
                    Log.w(TAG, "Activity no longer exists, deletion already completed");
                    if (listener != null) {
                        listener.onSuccess(activity.getId());
                    }
                    return;
                }
                
                // Check for rapid duplicate deletion attempts
                String activityKey = "activity_" + activity.getId();
                synchronized (activitiesBeingDeleted) {
                    if (activitiesBeingDeleted.contains(activityKey)) {
                        Log.w(TAG, "Activity is already being deleted: " + activity.getTitle());
                        if (listener != null) {
                            listener.onSuccess(activity.getId());
                        }
                        return;
                    }
                    activitiesBeingDeleted.add(activityKey);
                }
                
                try {
                    // Delete from local database immediately for responsive UI
                    activityDao.deleteActivity(existingActivity);
                    Log.d(TAG, "✅ Activity deleted from local database: " + activity.getTitle());
                    
                    // Always report success immediately since local deletion worked
                    if (listener != null) {
                        listener.onSuccess(existingActivity.getId());
                    }
                    
                    // Handle Firebase deletion in background (don't wait for it)
                    if (userManager.isLoggedIn() && existingActivity.getFirebaseId() != null && !existingActivity.getFirebaseId().isEmpty()) {
                        deleteFromFirebaseBackground(existingActivity);
                    }
                    
                } finally {
                    // Always clean up the deletion flag
                    synchronized (activitiesBeingDeleted) {
                        activitiesBeingDeleted.remove(activityKey);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in activity deletion", e);
                // Clean up deletion tracking on exception
                String activityKey = "activity_" + activity.getId();
                synchronized (activitiesBeingDeleted) {
                    activitiesBeingDeleted.remove(activityKey);
                }
                if (listener != null) {
                    listener.onError("Failed to delete activity: " + e.getMessage());
                }
            }
        });
    }
    
    private void deleteFromFirebaseBackground(TripActivity activity) {
        // Background Firebase deletion - doesn't affect UI responsiveness
        try {
            Trip trip = tripDao.getTripByIdSync(activity.getTripId());
            if (trip == null || trip.getFirebaseId() == null) {
                Log.d(TAG, "No Firebase trip found, skipping Firebase deletion");
                return;
            }
            
            Log.d(TAG, "Deleting from Firebase in background: " + activity.getTitle());
            deleteActivityFromFirebase(activity, trip, new OnActivityOperationListener() {
                @Override
                public void onSuccess(int activityId) {
                    Log.d(TAG, "✅ Background Firebase deletion successful: " + activity.getTitle());
                }
                
                @Override
                public void onError(String error) {
                    Log.w(TAG, "⚠️ Background Firebase deletion failed (local deletion was successful): " + error);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Error in background Firebase deletion: " + e.getMessage());
        }
    }
    
    private void deleteActivityFromFirebase(TripActivity activity, Trip trip, OnActivityOperationListener listener) {
        Log.d(TAG, "Deleting Firebase resources for activity: " + activity.getTitle());
        
        String userEmail = userManager.getUserEmail();
        
        // Check if we need to delete an image first
        boolean hasFirebaseImage = activity.getImageUrl() != null && 
                                 activity.getImageUrl().startsWith("https://firebasestorage.googleapis.com");
        
        if (hasFirebaseImage) {
            Log.d(TAG, "Deleting image first, then Firestore document for: " + activity.getTitle());
            // Delete image first, then delete Firestore document
            deleteImageFromFirebaseStorageSync(activity.getImageUrl(), () -> {
                // After image deletion completes (or fails), delete the Firestore document
                deleteFirestoreDocument(activity, trip, userEmail, listener);
            });
        } else {
            Log.d(TAG, "No Firebase image to delete, deleting Firestore document directly for: " + activity.getTitle());
            // No image to delete, proceed directly to Firestore document deletion
            deleteFirestoreDocument(activity, trip, userEmail, listener);
        }
    }
    
    private void deleteFirestoreDocument(TripActivity activity, Trip trip, String userEmail, OnActivityOperationListener listener) {
        // Delete activity document from Firestore
        if (activity.getFirebaseId() != null && !activity.getFirebaseId().isEmpty()) {
            firestore.collection("users")
                .document(userEmail)
                .collection("trips")
                .document(trip.getFirebaseId())
                .collection("activities")
                .document(activity.getFirebaseId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Firebase activity document deleted successfully: " + activity.getTitle());
                    if (listener != null) {
                        listener.onSuccess(activity.getId());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "⚠️ Firebase activity deletion failed: " + e.getMessage());
                    // Local deletion already succeeded, so consider this successful
                    if (listener != null) {
                        listener.onSuccess(activity.getId());
                    }
                });
        } else {
            Log.d(TAG, "No Firebase ID to delete, operation complete");
            if (listener != null) {
                listener.onSuccess(activity.getId());
            }
        }
    }
    
    private void deleteImageFromFirebaseStorageSync(String imageUrl, Runnable onComplete) {
        try {
            Log.d(TAG, "🖼️ Deleting image from Firebase Storage: " + imageUrl);
            com.google.firebase.storage.StorageReference imageRef = storage.getReferenceFromUrl(imageUrl);
            imageRef.delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Image deleted from Firebase Storage successfully");
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "⚠️ Failed to delete image from Firebase Storage: " + e.getMessage());
                    // Continue even if image deletion fails
                    onComplete.run();
                });
        } catch (Exception e) {
            Log.w(TAG, "Error deleting image from Firebase Storage", e);
            // Continue even if there's an exception
            onComplete.run();
        }
    }
    
    // CRITICAL FIX: Add timeout-protected Firebase deletion to prevent hanging operations
    private void deleteActivityFromFirebaseWithTimeout(TripActivity activity, Trip trip, OnActivityOperationListener listener, long timeoutMs) {
        Log.d(TAG, "Starting timeout-protected Firebase deletion for activity: " + activity.getTitle());
        
        // Create a timeout handler
        final boolean[] completed = {false};
        android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        Runnable timeoutRunnable = () -> {
            synchronized (completed) {
                if (!completed[0]) {
                    completed[0] = true;
                    Log.w(TAG, "Firebase deletion timeout for activity: " + activity.getTitle());
                    if (listener != null) {
                        listener.onSuccess(activity.getId()); // Consider local deletion successful
                    }
                }
            }
        };
        
        // Set timeout
        timeoutHandler.postDelayed(timeoutRunnable, timeoutMs);
        
        // Perform the actual deletion
        deleteActivityFromFirebase(activity, trip, new OnActivityOperationListener() {
            @Override
            public void onSuccess(int activityId) {
                synchronized (completed) {
                    if (!completed[0]) {
                        completed[0] = true;
                        timeoutHandler.removeCallbacks(timeoutRunnable);
                        Log.d(TAG, "Firebase deletion completed successfully for activity: " + activity.getTitle());
                        if (listener != null) {
                            listener.onSuccess(activityId);
                        }
                    }
                }
            }
            
            @Override
            public void onError(String error) {
                synchronized (completed) {
                    if (!completed[0]) {
                        completed[0] = true;
                        timeoutHandler.removeCallbacks(timeoutRunnable);
                        Log.w(TAG, "Firebase deletion failed for activity: " + activity.getTitle() + " - " + error);
                        if (listener != null) {
                            listener.onSuccess(activity.getId()); // Consider local deletion successful
                        }
                    }
                }
            }
        });
    }
    
    private void syncTripToFirebase(Trip trip, OnTripOperationListener listener) {
        syncTripToFirebaseWithTimeout(trip, listener, 15000); // 15 second default timeout
    }
    
    private void syncTripToFirebaseWithTimeout(Trip trip, OnTripOperationListener listener, long timeoutMs) {
        if (!userManager.isLoggedIn()) {
            if (listener != null) {
                listener.onSuccess(trip.getId());
            }
            return;
        }
        
        try {
            String userEmail = userManager.getUserEmail();
            if (userEmail == null || userEmail.trim().isEmpty()) {
                Log.e(TAG, "User email is null or empty");
                if (listener != null) {
                    listener.onError("User authentication error");
                }
                return;
            }
            
            Map<String, Object> tripData = new HashMap<>();
            tripData.put("title", trip.getTitle());
            tripData.put("destination", trip.getDestination());
            tripData.put("startDate", trip.getStartDate());
            tripData.put("endDate", trip.getEndDate());
            tripData.put("createdAt", trip.getCreatedAt());
            tripData.put("updatedAt", trip.getUpdatedAt());
            
            // Set up timeout handler
            final boolean[] operationCompleted = {false};
            android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            
            Runnable timeoutRunnable = () -> {
                synchronized (operationCompleted) {
                    if (!operationCompleted[0]) {
                        operationCompleted[0] = true;
                        Log.w(TAG, "Firebase sync timed out after " + timeoutMs + "ms");
                        if (listener != null) {
                            listener.onError("Sync timed out - trip saved locally");
                        }
                    }
                }
            };
            
            timeoutHandler.postDelayed(timeoutRunnable, timeoutMs);
            
            if (trip.getFirebaseId() != null) {
                // Update existing trip
                firestore.collection("users")
                    .document(userEmail)
                    .collection("trips")
                    .document(trip.getFirebaseId())
                    .set(tripData)
                    .addOnSuccessListener(aVoid -> {
                        synchronized (operationCompleted) {
                            if (!operationCompleted[0]) {
                                operationCompleted[0] = true;
                                timeoutHandler.removeCallbacks(timeoutRunnable);
                                
                                try {
                                    tripDao.markTripAsSynced(trip.getId());
                                } catch (Exception e) {
                                    Log.w(TAG, "Failed to mark trip as synced", e);
                                }
                                
                                if (listener != null) {
                                    listener.onSuccess(trip.getId());
                                }
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        synchronized (operationCompleted) {
                            if (!operationCompleted[0]) {
                                operationCompleted[0] = true;
                                timeoutHandler.removeCallbacks(timeoutRunnable);
                                Log.e(TAG, "Error syncing trip to Firebase", e);
                                if (listener != null) {
                                    listener.onError("Failed to sync trip: " + e.getMessage());
                                }
                            }
                        }
                    });
            } else {
                // Create new trip
                firestore.collection("users")
                    .document(userEmail)
                    .collection("trips")
                    .add(tripData)
                    .addOnSuccessListener(documentReference -> {
                        synchronized (operationCompleted) {
                            if (!operationCompleted[0]) {
                                operationCompleted[0] = true;
                                timeoutHandler.removeCallbacks(timeoutRunnable);
                                
                                try {
                                    String firebaseId = documentReference.getId();
                                    tripDao.updateTripFirebaseId(trip.getId(), firebaseId);
                                } catch (Exception e) {
                                    Log.w(TAG, "Failed to update Firebase ID", e);
                                }
                                
                                if (listener != null) {
                                    listener.onSuccess(trip.getId());
                                }
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        synchronized (operationCompleted) {
                            if (!operationCompleted[0]) {
                                operationCompleted[0] = true;
                                timeoutHandler.removeCallbacks(timeoutRunnable);
                                Log.e(TAG, "Error creating trip in Firebase", e);
                                if (listener != null) {
                                    listener.onError("Failed to sync trip: " + e.getMessage());
                                }
                            }
                        }
                    });
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Exception in Firebase sync setup", e);
            if (listener != null) {
                listener.onError("Sync setup failed: " + e.getMessage());
            }
        }
    }
    
    // New method to sync trip with all its activities to Firebase
    public void syncTripWithActiviesToFirebase(Trip trip, OnTripOperationListener listener) {
        if (!userManager.isLoggedIn()) {
            if (listener != null) {
                listener.onError("User not logged in");
            }
            return;
        }
        
        Log.d(TAG, "Syncing trip with activities to Firebase: " + trip.getTitle());
        
        // First sync the trip
        syncTripToFirebase(trip, new OnTripOperationListener() {
            @Override
            public void onSuccess(int tripId) {
                Log.d(TAG, "Trip synced successfully, now syncing activities");
                
                // Trip synced successfully, now sync all activities
                executor.execute(() -> {
                    try {
                        List<TripActivity> activities = activityDao.getActivitiesForTripSync(tripId);
                        
                        if (activities.isEmpty()) {
                            Log.d(TAG, "No activities to sync for trip: " + trip.getTitle());
                            if (listener != null) {
                                listener.onSuccess(tripId);
                            }
                            return;
                        }
                        
                        Log.d(TAG, "Syncing " + activities.size() + " activities for trip: " + trip.getTitle());
                        
                        final int[] completed = {0};
                        final int[] successful = {0};
                        final boolean[] hasError = {false};
                        final StringBuilder errorMessages = new StringBuilder();
                        final int totalActivities = activities.size();
                        
                        // Sync each activity
                        for (TripActivity activity : activities) {
                            syncActivityToFirebase(activity, new OnActivityOperationListener() {
                                @Override
                                public void onSuccess(int activityId) {
                                    synchronized (completed) {
                                        completed[0]++;
                                        successful[0]++;
                                        Log.d(TAG, "Activity synced successfully (" + completed[0] + "/" + totalActivities + ")");
                                        
                                        if (completed[0] >= totalActivities) {
                                            // All activities processed
                                            if (listener != null) {
                                                if (successful[0] == totalActivities) {
                                                    Log.d(TAG, "All activities synced successfully for trip: " + trip.getTitle());
                                                    listener.onSuccess(tripId);
                                                } else {
                                                    String message = "Trip synced but only " + successful[0] + "/" + totalActivities + " activities synced";
                                                    if (errorMessages.length() > 0) {
                                                        message += ". Errors: " + errorMessages.toString();
                                                    }
                                                    listener.onError(message);
                                                }
                                            }
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
                                        Log.e(TAG, "Failed to sync activity: " + error + " (" + completed[0] + "/" + totalActivities + ")");
                                        
                                        if (completed[0] >= totalActivities) {
                                            // All activities processed
                                            if (listener != null) {
                                                String message = "Trip synced but activity sync failed: " + successful[0] + "/" + totalActivities + " activities synced";
                                                if (errorMessages.length() > 0) {
                                                    message += ". Errors: " + errorMessages.toString();
                                                }
                                                listener.onError(message);
                                            }
                                        }
                                    }
                                }
                            });
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error syncing activities for trip", e);
                        if (listener != null) {
                            listener.onError("Trip synced but failed to sync activities: " + e.getMessage());
                        }
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to sync trip: " + error);
                if (listener != null) {
                    listener.onError("Failed to sync trip: " + error);
                }
            }
        });
    }
    
    private void syncActivityToFirebase(TripActivity activity, OnActivityOperationListener listener) {
        if (!userManager.isLoggedIn()) {
            if (listener != null) runOnUiThread(() -> listener.onSuccess(activity.getId()));
            return;
        }
        performFirebaseSync(activity, listener);
    }

    private void performFirebaseSync(TripActivity activity, OnActivityOperationListener listener) {
        executor.execute(() -> {
            try {
                Trip trip = tripDao.getTripByIdSync(activity.getTripId());
                if (trip == null || trip.getFirebaseId() == null) {
                    if (listener != null) runOnUiThread(() -> listener.onError("Trip not synced to Firebase"));
                    return;
                }

                Map<String, Object> activityData = new HashMap<>();
                activityData.put("title", activity.getTitle());
                activityData.put("description", activity.getDescription());
                activityData.put("location", activity.getLocation());
                activityData.put("dateTime", activity.getDateTime());
                activityData.put("dayNumber", activity.getDayNumber());
                activityData.put("timeString", activity.getTimeString());
                activityData.put("latitude", activity.getLatitude());
                activityData.put("longitude", activity.getLongitude());
                activityData.put("createdAt", activity.getCreatedAt());
                activityData.put("updatedAt", activity.getUpdatedAt());

                // Check for local image path first
                String imagePath = activity.getImageLocalPath();
                if (imagePath == null || imagePath.isEmpty()) {
                    // If no local path, check if imageUrl is actually a local path
                    String imageUrl = activity.getImageUrl();
                    if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.startsWith("https://")) {
                        imagePath = imageUrl;
                    }
                }

                Log.d(TAG, "Image path to process: " + imagePath);

                if (imagePath != null && !imagePath.isEmpty()) {
                    // We have a local image to upload
                    Log.d(TAG, "Uploading local image to Firebase: " + imagePath);
                    File imageFile = new File(imagePath);
                    if (!imageFile.exists()) {
                        Log.e(TAG, "Image file does not exist: " + imagePath);
                        if (listener != null) runOnUiThread(() -> listener.onError("Image file not found"));
                        return;
                    }

                    String imageName = "activity_" + System.currentTimeMillis() + "_" + imageFile.getName();
                    StorageReference imageRef = storage.getReference().child("activity_images").child(imageName);

                    // Upload the file
                    imageRef.putFile(Uri.fromFile(imageFile))
                            .addOnSuccessListener(taskSnapshot -> {
                                Log.d(TAG, "Image uploaded successfully");
                                // Get the download URL
                                imageRef.getDownloadUrl()
                                        .addOnSuccessListener(uri -> {
                                            String firebaseUrl = uri.toString();
                                            Log.d(TAG, "Got Firebase URL: " + firebaseUrl);
                                            activity.setImageUrl(firebaseUrl);
                                            activity.setImageLocalPath(null); // Clear local path
                                            activityData.put("imageUrl", firebaseUrl);
                                            saveActivityDataToFirestore(activity, trip, listener, activityData);
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to get download URL", e);
                                            if (listener != null) runOnUiThread(() -> 
                                                listener.onError("Failed to get image URL: " + e.getMessage()));
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to upload image", e);
                                if (listener != null) runOnUiThread(() -> 
                                    listener.onError("Failed to upload image: " + e.getMessage()));
                            });
                } else if (activity.getImageUrl() != null && activity.getImageUrl().startsWith("https://")) {
                    // Already has a Firebase URL
                    Log.d(TAG, "Using existing Firebase URL: " + activity.getImageUrl());
                    activityData.put("imageUrl", activity.getImageUrl());
                    saveActivityDataToFirestore(activity, trip, listener, activityData);
                } else {
                    // No image
                    Log.d(TAG, "No image to upload");
                    activityData.put("imageUrl", "");
                    saveActivityDataToFirestore(activity, trip, listener, activityData);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in performFirebaseSync", e);
                if (listener != null) runOnUiThread(() -> listener.onError(e.getMessage()));
            }
        });
    }

    private void saveActivityDataToFirestore(TripActivity activity, Trip trip, OnActivityOperationListener listener, Map<String, Object> activityData) {
        String userEmail = userManager.getUserEmail();
        if (userEmail == null || userEmail.isEmpty()) {
            if (listener != null) runOnUiThread(() -> listener.onError("User not logged in"));
            return;
        }

        String tripFirebaseId = trip.getFirebaseId();
        if (tripFirebaseId == null || tripFirebaseId.isEmpty()) {
            if (listener != null) runOnUiThread(() -> listener.onError("Trip is not synced to Firebase yet."));
            return;
        }
        
        // If activity has a Firebase ID, it's an update. Otherwise, it's a new creation.
        if (activity.getFirebaseId() != null && !activity.getFirebaseId().isEmpty()) {
            // UPDATE existing activity
            Log.d(TAG, "Updating existing activity in Firestore with ID: " + activity.getFirebaseId());
            firestore.collection("users").document(userEmail)
                    .collection("trips").document(tripFirebaseId)
                    .collection("activities").document(activity.getFirebaseId())
                    .set(activityData, com.google.firebase.firestore.SetOptions.merge()) // Use merge to avoid overwriting fields
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Successfully updated activity in Firestore: " + activity.getTitle());
                        executor.execute(() -> {
                            activity.setSynced(true); // Ensure sync status is true
                            activityDao.updateActivity(activity);
                            Log.d(TAG, "Local activity updated after Firestore sync.");
                        });
                        if (listener != null) runOnUiThread(() -> listener.onSuccess(activity.getId()));
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update activity in Firestore", e);
                        if (listener != null) runOnUiThread(() -> listener.onError(e.getMessage()));
                    });
        } else {
            // CREATE new activity
            Log.d(TAG, "Creating new activity in Firestore for trip: " + trip.getTitle());
            firestore.collection("users").document(userEmail)
                    .collection("trips").document(tripFirebaseId)
                    .collection("activities").add(activityData)
                    .addOnSuccessListener(documentReference -> {
                        String firebaseId = documentReference.getId();
                        Log.d(TAG, "Activity data saved to Firestore with ID: " + firebaseId);

                        // Update the local activity with the Firebase ID and sync status
                        executor.execute(() -> {
                            activity.setFirebaseId(firebaseId);
                            activity.setSynced(true);
                            activityDao.updateActivity(activity);
                            Log.d(TAG, "Local activity updated with new Firebase ID: " + firebaseId);
                        });

                        if (listener != null) runOnUiThread(() -> listener.onSuccess(activity.getId()));
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to create new activity in Firestore", e);
                        if (listener != null) runOnUiThread(() -> listener.onError(e.getMessage()));
                    });
        }
    }

    private static void runOnUiThread(Runnable runnable) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
    }

    // Clean up duplicate activities in local database
    private void cleanupLocalDuplicateActivities(int tripId) {
        // NUCLEAR PROTECTION: Absolutely block cleanup in Firebase-only mode
        if (isNuclearFirebaseMode) {
            Log.w(TAG, "🔥🚨 NUCLEAR PROTECTION: Blocking cleanupLocalDuplicateActivities in Firebase-only mode!");
            return;
        }
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "Checking for local duplicate activities in trip: " + tripId);
                List<TripActivity> activities = activityDao.getActivitiesForTripSync(tripId);
                
                Map<String, TripActivity> seenActivities = new HashMap<>();
                List<TripActivity> duplicatesToDelete = new ArrayList<>();
                
                for (TripActivity activity : activities) {
                    // Create a unique key based on title and time (within 1 minute)
                    String key = activity.getTitle() + "_" + (activity.getDateTime() / 60000); // Group by minute
                    
                    if (seenActivities.containsKey(key)) {
                        TripActivity existing = seenActivities.get(key);
                        
                        // Keep the one with Firebase ID if available, or the newer one
                        if (activity.getFirebaseId() != null && existing.getFirebaseId() == null) {
                            duplicatesToDelete.add(existing);
                            seenActivities.put(key, activity);
                        } else if (activity.getFirebaseId() == null && existing.getFirebaseId() != null) {
                            duplicatesToDelete.add(activity);
                        } else if (activity.getUpdatedAt() > existing.getUpdatedAt()) {
                            duplicatesToDelete.add(existing);
                            seenActivities.put(key, activity);
                        } else {
                            duplicatesToDelete.add(activity);
                        }
                    } else {
                        seenActivities.put(key, activity);
                    }
                }
                
                // Delete duplicates
                for (TripActivity duplicate : duplicatesToDelete) {
                    Log.d(TAG, "Removing duplicate activity: " + duplicate.getTitle() + " (ID: " + duplicate.getId() + ")");
                    activityDao.deleteActivity(duplicate);
                }
                
                if (duplicatesToDelete.size() > 0) {
                    Log.d(TAG, "Cleaned up " + duplicatesToDelete.size() + " duplicate activities for trip: " + tripId);
                } else {
                    Log.d(TAG, "No duplicate activities found for trip: " + tripId);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up local duplicate activities", e);
            }
        });
    }

    // Force sync all activities from Firebase to local database
    public void forceSyncAllActivities(OnTripSyncListener listener) {
        if (!userManager.isLoggedIn()) {
            if (listener != null) {
                listener.onError("User not logged in");
            }
            return;
        }
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "Starting force sync of all activities from Firebase");
                
                // Get all trips with Firebase IDs
                List<Trip> allTrips = tripDao.getAllTripsSync();
                int tripCount = 0;
                
                // Count trips that have Firebase IDs
                for (Trip trip : allTrips) {
                    if (trip.getFirebaseId() != null && !trip.getFirebaseId().isEmpty()) {
                        tripCount++;
                    }
                }
                
                final int totalTrips = tripCount; // Make it final for lambda access
                
                if (totalTrips == 0) {
                    Log.d(TAG, "No Firebase trips found, sync complete");
                    if (listener != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            listener.onSuccess();
                        });
                    }
                    return;
                }
                
                Log.d(TAG, "Force syncing activities for " + totalTrips + " trips");
                
                // Counter for completed operations
                final int[] completed = {0};
                final boolean[] hasError = {false};
                final StringBuilder errorMessage = new StringBuilder();
                
                // Sync activities for each trip
                for (Trip trip : allTrips) {
                    if (trip.getFirebaseId() != null && !trip.getFirebaseId().isEmpty()) {
                        fetchActivitiesForTrip(trip.getFirebaseId(), trip.getId(), () -> {
                            synchronized (completed) {
                                completed[0]++;
                                Log.d(TAG, "Completed sync for trip " + trip.getTitle() + " (" + completed[0] + "/" + totalTrips + ")");
                                
                                // Check if all trips are completed
                                if (completed[0] >= totalTrips) {
                                    Log.d(TAG, "Force sync of all activities completed");
                                    
                                    // CRITICAL FIX: Disable cleanup that deletes synced Firebase activities
                                    // cleanupLocalDuplicateActivities(-1); // DISABLED - was deleting displayed Firebase data!
                                    
                                    if (listener != null) {
                                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                            if (hasError[0]) {
                                                listener.onError("Partial sync failure: " + errorMessage.toString());
                                            } else {
                                                listener.onSuccess();
                                            }
                                        });
                                    }
                                }
                            }
                        });
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error during force sync of all activities", e);
                if (listener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onError("Force sync failed: " + e.getMessage());
                    });
                }
            }
        });
    }

    // Clean up orphaned Firebase activities (activities without valid parent trips)
    public void cleanupOrphanedFirebaseActivities(OnTripSyncListener listener) {
        if (!userManager.isLoggedIn()) {
            if (listener != null) {
                listener.onError("User not logged in");
            }
            return;
        }
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "Starting cleanup of orphaned Firebase activities");
                
                // Get all activities from local database
                List<TripActivity> allActivities = activityDao.getAllActivitiesSync();
                List<Trip> allTrips = tripDao.getAllTripsSync();
                
                // Create a set of valid trip IDs
                Set<Integer> validTripIds = new HashSet<>();
                for (Trip trip : allTrips) {
                    validTripIds.add(trip.getId());
                }
                
                // Find orphaned activities
                List<TripActivity> orphanedActivities = new ArrayList<>();
                for (TripActivity activity : allActivities) {
                    if (!validTripIds.contains(activity.getTripId())) {
                        orphanedActivities.add(activity);
                        Log.d(TAG, "Found orphaned activity: " + activity.getTitle() + " (Trip ID: " + activity.getTripId() + ")");
                    }
                }
                
                // Delete orphaned activities
                for (TripActivity orphaned : orphanedActivities) {
                    activityDao.deleteActivity(orphaned);
                    Log.d(TAG, "Deleted orphaned activity: " + orphaned.getTitle());
                }
                
                if (orphanedActivities.size() > 0) {
                    Log.d(TAG, "Cleaned up " + orphanedActivities.size() + " orphaned activities");
                } else {
                    Log.d(TAG, "No orphaned activities found");
                }
                
                // Report success
                if (listener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onSuccess();
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up orphaned Firebase activities", e);
                if (listener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onError("Cleanup failed: " + e.getMessage());
                    });
                }
            }
        });
    }

    // Real-time Firebase listeners for automatic sync
    private void setupFirebaseTripsListener() {
        if (!userManager.isLoggedIn()) {
            Log.d(TAG, "User not logged in, skipping real-time trip listener");
            return;
        }
        
        String userId = userManager.getUserEmail();
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "No user email, skipping real-time trip listener");
            return;
        }
        
        Log.d(TAG, "Setting up real-time Firebase trips listener");
        
        firestore.collection("users")
            .document(userId)
            .collection("trips")
            .addSnapshotListener((querySnapshot, error) -> {
                if (error != null) {
                    Log.w(TAG, "Real-time trips listener error: " + error.getMessage());
                    return;
                }
                
                if (querySnapshot != null) {
                    Log.d(TAG, "Real-time trips update received: " + querySnapshot.size() + " trips");
                    
                    executor.execute(() -> {
                        try {
                            // Process changes without fetching all data again
                            for (com.google.firebase.firestore.DocumentChange change : querySnapshot.getDocumentChanges()) {
                                processFirebaseTripChange(change);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing real-time trip changes", e);
                        }
                    });
                }
            });
    }

    private void setupFirebaseActivitiesListener(String tripFirebaseId, int localTripId) {
        if (!userManager.isLoggedIn()) {
            return;
        }
        
        String userId = userManager.getUserEmail();
        if (userId == null || userId.isEmpty()) {
            return;
        }
        
        Log.d(TAG, "Setting up real-time Firebase activities listener for trip: " + tripFirebaseId);
        
        firestore.collection("users")
            .document(userId)
            .collection("trips")
            .document(tripFirebaseId)
            .collection("activities")
            .addSnapshotListener((querySnapshot, error) -> {
                if (error != null) {
                    Log.w(TAG, "Real-time activities listener error: " + error.getMessage());
                    return;
                }
                
                if (querySnapshot != null) {
                    Log.d(TAG, "Real-time activities update received: " + querySnapshot.size() + " activities");
                    
                    // CRITICAL FIX: Check if real-time updates are enabled
                    if (!realTimeUpdatesEnabled) {
                        Log.d(TAG, "Real-time updates DISABLED - skipping to prevent UI interference");
                        return;
                    }
                    
                    executor.execute(() -> {
                        try {
                            for (com.google.firebase.firestore.DocumentChange change : querySnapshot.getDocumentChanges()) {
                                processFirebaseActivityChange(change, localTripId);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing real-time activity changes", e);
                        }
                    });
                }
            });
    }

    private void processFirebaseTripChange(com.google.firebase.firestore.DocumentChange change) {
        try {
            com.google.firebase.firestore.QueryDocumentSnapshot doc = change.getDocument();
            String firebaseId = doc.getId();
            
            switch (change.getType()) {
                case ADDED:
                    Log.d(TAG, "Real-time: Trip added - " + firebaseId);
                    processFirebaseTripDocument(doc);
                    break;
                case MODIFIED:
                    Log.d(TAG, "Real-time: Trip modified - " + firebaseId);
                    processFirebaseTripDocument(doc);
                    break;
                case REMOVED:
                    Log.d(TAG, "Real-time: Trip removed - " + firebaseId);
                    Trip existingTrip = tripDao.getTripByFirebaseId(firebaseId);
                    if (existingTrip != null) {
                        tripDao.deleteTrip(existingTrip);
                        Log.d(TAG, "Real-time: Removed local trip - " + existingTrip.getTitle());
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing Firebase trip change", e);
        }
    }

    private void processFirebaseActivityChange(com.google.firebase.firestore.DocumentChange change, int localTripId) {
        try {
            com.google.firebase.firestore.QueryDocumentSnapshot doc = change.getDocument();
            String firebaseId = doc.getId();
            
            // Check if this activity is being deleted - if so, ignore Firebase updates
            synchronized (activitiesBeingDeleted) {
                // Clean up expired deletion tracking first
                cleanupExpiredDeletions();
                
                if (activitiesBeingDeleted.contains(firebaseId)) {
                    Log.d(TAG, "Real-time: Ignoring Firebase change for activity being deleted: " + firebaseId);
                    return;
                }
            }
            
            switch (change.getType()) {
                case ADDED:
                    Log.d(TAG, "Real-time: Activity added - " + firebaseId);
                    processFirebaseActivityDocument(doc, localTripId);
                    break;
                case MODIFIED:
                    Log.d(TAG, "Real-time: Activity modified - " + firebaseId);
                    processFirebaseActivityDocument(doc, localTripId);
                    break;
                case REMOVED:
                    Log.d(TAG, "Real-time: Activity removed - " + firebaseId);
                    TripActivity existingActivity = activityDao.getActivityByFirebaseId(firebaseId);
                    if (existingActivity != null) {
                        activityDao.deleteActivity(existingActivity);
                        Log.d(TAG, "Real-time: Removed local activity - " + existingActivity.getTitle());
                    }
                    // Clean up deletion tracking when Firebase confirms deletion
                    synchronized (activitiesBeingDeleted) {
                        activitiesBeingDeleted.remove(firebaseId);
                        deletionTimestamps.remove(firebaseId);
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing Firebase activity change", e);
        }
    }

    private void processFirebaseTripDocument(com.google.firebase.firestore.QueryDocumentSnapshot doc) {
        try {
            Trip trip = new Trip();
            trip.setFirebaseId(doc.getId());
            trip.setTitle(doc.getString("title"));
            trip.setDestination(doc.getString("destination"));
            
            Long startDate = doc.getLong("startDate");
            Long endDate = doc.getLong("endDate");
            if (startDate != null) trip.setStartDate(startDate);
            if (endDate != null) trip.setEndDate(endDate);
            
            Long createdAt = doc.getLong("createdAt");
            Long updatedAt = doc.getLong("updatedAt");
            if (createdAt != null) trip.setCreatedAt(createdAt);
            if (updatedAt != null) trip.setUpdatedAt(updatedAt);
            
            trip.setSynced(true);
            
            // ENHANCED DUPLICATE DETECTION: Check by Firebase ID first
            Trip existingByFirebaseId = tripDao.getTripByFirebaseId(trip.getFirebaseId());
            
            // Also check for content-based duplicates (same title, destination, dates)
            List<Trip> allTrips = tripDao.getAllTripsSync();
            Trip existingBySimilarity = null;
            
            for (Trip localTrip : allTrips) {
                if (localTrip.getTitle().equals(trip.getTitle()) && 
                    localTrip.getDestination().equals(trip.getDestination()) &&
                    localTrip.getStartDate() == trip.getStartDate() &&
                    localTrip.getEndDate() == trip.getEndDate()) {
                    
                    // This is the same trip content-wise
                    existingBySimilarity = localTrip;
                    break;
                }
            }
            
            if (existingByFirebaseId != null) {
                // Update existing trip by Firebase ID
                trip.setId(existingByFirebaseId.getId());
                tripDao.updateTrip(trip);
                Log.d(TAG, "Real-time: Updated existing trip (by Firebase ID) - " + trip.getTitle());
                
            } else if (existingBySimilarity != null) {
                // Found duplicate by content
                if (existingBySimilarity.getFirebaseId() == null || existingBySimilarity.getFirebaseId().isEmpty()) {
                    // Update local trip with Firebase ID - this prevents the duplicate
                    trip.setId(existingBySimilarity.getId());
                    tripDao.updateTrip(trip);
                    Log.d(TAG, "Real-time: Updated local trip with Firebase ID - " + trip.getTitle());
                    
                } else if (!existingBySimilarity.getFirebaseId().equals(trip.getFirebaseId())) {
                    // Different Firebase IDs for same content - remove the old one and use Firebase version
                    tripDao.deleteTrip(existingBySimilarity);
                    long tripId = tripDao.insertTrip(trip);
                    Log.d(TAG, "Real-time: Replaced duplicate trip with different Firebase ID - " + trip.getTitle());
                    setupFirebaseActivitiesListener(trip.getFirebaseId(), (int) tripId);
                    
                } else {
                    // Same Firebase ID - this shouldn't happen but handle it
                    trip.setId(existingBySimilarity.getId());
                    tripDao.updateTrip(trip);
                    Log.d(TAG, "Real-time: Updated trip (same Firebase ID) - " + trip.getTitle());
                }
                
            } else {
                // Completely new trip - but do one final check for partial matches
                boolean shouldInsert = true;
                
                // Check if we have too many similar trips already (safety check)
                int similarCount = 0;
                for (Trip localTrip : allTrips) {
                    if (localTrip.getTitle().equals(trip.getTitle())) {
                        similarCount++;
                    }
                }
                
                if (similarCount >= 3) {
                    Log.w(TAG, "Too many trips with same title '" + trip.getTitle() + "' - skipping to prevent spam");
                    shouldInsert = false;
                }
                
                if (shouldInsert) {
                    // Insert new trip
                    long tripId = tripDao.insertTrip(trip);
                    Log.d(TAG, "Real-time: Inserted new trip - " + trip.getTitle() + " with local ID: " + tripId);
                    
                    // Set up activities listener for this trip
                    setupFirebaseActivitiesListener(trip.getFirebaseId(), (int) tripId);
                    
                    // Schedule cleanup after a delay to remove any potential duplicates
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        executor.execute(this::cleanupDuplicateTripsAfterSync);
                    }, 2000);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing Firebase trip document", e);
        }
    }

    private void processFirebaseActivityDocument(com.google.firebase.firestore.QueryDocumentSnapshot doc, int localTripId) {
        // NUCLEAR PROTECTION: Absolutely prevent all local operations in Firebase-only mode
        if (isNuclearFirebaseMode) {
            Log.w(TAG, "🔥🚨 NUCLEAR PROTECTION: Blocking processFirebaseActivityDocument in Firebase-only mode!");
            return;
        }
        
        // CRITICAL FIX: Check if real-time updates are disabled to prevent local database modifications
        if (!realTimeUpdatesEnabled) {
            Log.d(TAG, "Real-time updates DISABLED - skipping Firebase activity processing to prevent local DB changes");
            return;
        }
        
        try {
            String firebaseId = doc.getId();
            
            // Check if this activity is being deleted - if so, don't process it
            synchronized (activitiesBeingDeleted) {
                // Clean up expired deletion tracking first
                cleanupExpiredDeletions();
                
                if (activitiesBeingDeleted.contains(firebaseId)) {
                    Log.d(TAG, "Skipping processing of activity being deleted: " + firebaseId);
                    return;
                }
            }
            
            TripActivity activity = new TripActivity();
            activity.setFirebaseId(firebaseId);
            activity.setTripId(localTripId);
            activity.setTitle(doc.getString("title"));
            activity.setDescription(doc.getString("description"));
            activity.setLocation(doc.getString("location"));
            
            Long dateTime = doc.getLong("dateTime");
            if (dateTime != null) activity.setDateTime(dateTime);
            
            Long dayNumber = doc.getLong("dayNumber");
            if (dayNumber != null) activity.setDayNumber(dayNumber.intValue());
            
            activity.setImageUrl(doc.getString("imageUrl"));
            
            Long createdAt = doc.getLong("createdAt");
            Long updatedAt = doc.getLong("updatedAt");
            if (createdAt != null) activity.setCreatedAt(createdAt);
            if (updatedAt != null) activity.setUpdatedAt(updatedAt);
            
            activity.setSynced(true);
            
            // Enhanced duplicate check - check by Firebase ID AND by content similarity
            TripActivity existingByFirebaseId = activityDao.getActivityByFirebaseId(activity.getFirebaseId());
            
            // Also check for duplicates by title and trip ID (in case Firebase ID is missing locally)
            List<TripActivity> localActivities = activityDao.getActivitiesForTripSync(localTripId);
            TripActivity existingBySimilarity = null;
            for (TripActivity local : localActivities) {
                if (local.getTitle().equals(activity.getTitle()) && 
                    Math.abs(local.getDateTime() - activity.getDateTime()) < 60000) { // Within 1 minute
                    existingBySimilarity = local;
                    break;
                }
            }
            
            if (existingByFirebaseId != null) {
                // Update existing activity by Firebase ID
                activity.setId(existingByFirebaseId.getId());
                activityDao.updateActivity(activity);
                Log.d(TAG, "Updated existing activity (by Firebase ID): " + activity.getTitle());
            } else if (existingBySimilarity != null) {
                // CRITICAL FIX: Check if this is truly a duplicate or just similar
                if (existingBySimilarity.getFirebaseId() == null || existingBySimilarity.getFirebaseId().isEmpty()) {
                    // Update local activity with Firebase ID
                    activity.setId(existingBySimilarity.getId());
                    activityDao.updateActivity(activity);
                    Log.d(TAG, "Updated local activity with Firebase ID: " + activity.getTitle() + " -> " + activity.getFirebaseId());
                } else if (!existingBySimilarity.getFirebaseId().equals(activity.getFirebaseId())) {
                    // Different Firebase IDs - this is a true duplicate, remove the old one
                    activityDao.deleteActivity(existingBySimilarity);
                    long activityId = activityDao.insertActivity(activity);
                    Log.d(TAG, "Replaced duplicate activity with different Firebase ID: " + activity.getTitle());
                } else {
                    // Same Firebase ID - just update
                    activity.setId(existingBySimilarity.getId());
                    activityDao.updateActivity(activity);
                    Log.d(TAG, "Updated existing activity (same Firebase ID): " + activity.getTitle());
                }
            } else {
                // Insert new activity only if no duplicates found
                long activityId = activityDao.insertActivity(activity);
                Log.d(TAG, "Inserted NEW Firebase activity: " + activity.getTitle() + " with local ID: " + activityId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing Firebase activity document", e);
        }
    }

    // Enhanced method to set up activities listeners for all trips
    public void setupAllActivitiesListeners() {
        if (!userManager.isLoggedIn()) {
            Log.d(TAG, "User not logged in, skipping activities listeners setup");
            return;
        }
        
        executor.execute(() -> {
            try {
                List<Trip> trips = tripDao.getAllTripsSync();
                for (Trip trip : trips) {
                    if (trip.getFirebaseId() != null && !trip.getFirebaseId().isEmpty()) {
                        setupFirebaseActivitiesListener(trip.getFirebaseId(), trip.getId());
                    }
                }
                Log.d(TAG, "Set up activities listeners for " + trips.size() + " trips");
            } catch (Exception e) {
                Log.e(TAG, "Error setting up activities listeners", e);
            }
        });
    }
    
    // CRITICAL FIX: Force sync activities for a specific trip to refresh Firebase IDs
    public void forceSyncTripActivities(int tripId, OnTripSyncListener listener) {
        if (!userManager.isLoggedIn()) {
            if (listener != null) {
                listener.onError("User not logged in");
            }
            return;
        }
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "Force syncing activities for trip ID: " + tripId);
                
                // Get trip's Firebase ID
                Trip trip = tripDao.getTripByIdSync(tripId);
                if (trip == null || trip.getFirebaseId() == null || trip.getFirebaseId().isEmpty()) {
                    Log.w(TAG, "Trip not found or has no Firebase ID");
                    if (listener != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            listener.onError("Trip not synced to Firebase");
                        });
                    }
                    return;
                }
                
                Log.d(TAG, "Refreshing activities for trip: " + trip.getTitle() + " (Firebase ID: " + trip.getFirebaseId() + ")");
                
                // Fetch fresh data from Firebase
                fetchActivitiesForTrip(trip.getFirebaseId(), tripId, () -> {
                    Log.d(TAG, "Force sync completed for trip: " + trip.getTitle());
                    
                    if (listener != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            listener.onSuccess();
                        });
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error during force sync of trip activities", e);
                if (listener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onError("Force sync failed: " + e.getMessage());
                    });
                }
            }
        });
    }

    // Clean up expired deletion tracking to prevent memory leaks and stuck states
    private void cleanupExpiredDeletions() {
        long currentTime = System.currentTimeMillis();
        List<String> expiredIds = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : deletionTimestamps.entrySet()) {
            if (currentTime - entry.getValue() > DELETION_TIMEOUT_MS) {
                expiredIds.add(entry.getKey());
            }
        }
        
        for (String expiredId : expiredIds) {
            activitiesBeingDeleted.remove(expiredId);
            deletionTimestamps.remove(expiredId);
            Log.d(TAG, "Cleaned up expired deletion tracking for: " + expiredId);
        }
    }

    // CRITICAL FIX: Methods to control real-time updates
    public void disableRealTimeUpdates() {
        Log.d(TAG, "🔴 DISABLING real-time Firebase updates to prevent UI interference");
        realTimeUpdatesEnabled = false;
    }
    
    public void enableRealTimeUpdates() {
        Log.d(TAG, "🟢 ENABLING real-time Firebase updates");
        realTimeUpdatesEnabled = true;
    }
    
    // NUCLEAR PROTECTION METHODS
    public void enableNuclearFirebaseMode() {
        Log.d(TAG, "🔥🔥🔥 NUCLEAR: Enabling complete Firebase-only mode - ALL local operations blocked");
        isNuclearFirebaseMode = true;
        disableRealTimeUpdates();
    }
    
    public void disableNuclearFirebaseMode() {
        Log.d(TAG, "🔥 NUCLEAR: Disabling Firebase-only mode - local operations allowed");
        isNuclearFirebaseMode = false;
        enableRealTimeUpdates();
    }

    // Interfaces
    public interface OnTripOperationListener {
        void onSuccess(int tripId);
        void onError(String error);
    }
    
    public interface OnActivityOperationListener {
        void onSuccess(int activityId);
        void onError(String error);
    }
    
    public interface OnTripSyncListener {
        void onSuccess();
        void onError(String error);
    }

    // NUCLEAR DELETE: Optimized deletion for Firebase-only mode
    public void deleteActivityNuclear(TripActivity activity, OnActivityOperationListener listener) {
        if (!isNuclearFirebaseMode) {
            // Fall back to normal deletion if not in nuclear mode
            deleteActivity(activity, listener);
            return;
        }
        
        Log.d(TAG, "🔥 NUCLEAR DELETE: Optimized Firebase-only deletion for: " + activity.getTitle());
        
        executor.execute(() -> {
            try {
                // In nuclear mode, prioritize Firebase deletion over local
                if (userManager.isLoggedIn() && activity.getFirebaseId() != null && !activity.getFirebaseId().isEmpty()) {
                    // Delete from Firebase first in nuclear mode
                    Trip trip = tripDao.getTripByIdSync(activity.getTripId());
                    if (trip != null && trip.getFirebaseId() != null) {
                        Log.d(TAG, "🔥 NUCLEAR: Deleting from Firebase first");
                        deleteActivityFromFirebase(activity, trip, new OnActivityOperationListener() {
                            @Override
                            public void onSuccess(int activityId) {
                                // After Firebase deletion, remove from local
                                try {
                                    activityDao.deleteActivity(activity);
                                    Log.d(TAG, "🔥 NUCLEAR: Local deletion completed after Firebase");
                                    if (listener != null) {
                                        listener.onSuccess(activityId);
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "🔥 NUCLEAR: Local deletion failed but Firebase succeeded", e);
                                    // Firebase succeeded, so consider this successful
                                    if (listener != null) {
                                        listener.onSuccess(activityId);
                                    }
                                }
                            }
                            
                            @Override
                            public void onError(String error) {
                                Log.w(TAG, "🔥 NUCLEAR: Firebase deletion failed, trying local fallback");
                                try {
                                    activityDao.deleteActivity(activity);
                                    Log.d(TAG, "🔥 NUCLEAR: Local fallback deletion successful");
                                    if (listener != null) {
                                        listener.onSuccess(activity.getId());
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "🔥 NUCLEAR: Both Firebase and local deletion failed", e);
                                    if (listener != null) {
                                        listener.onError("Nuclear deletion failed: " + error);
                                    }
                                }
                            }
                        });
                        return;
                    }
                }
                
                // Fallback to local deletion if no Firebase ID
                activityDao.deleteActivity(activity);
                Log.d(TAG, "🔥 NUCLEAR: Local-only deletion completed");
                if (listener != null) {
                    listener.onSuccess(activity.getId());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "🔥 NUCLEAR: Deletion failed", e);
                if (listener != null) {
                    listener.onError("Nuclear deletion failed: " + e.getMessage());
                }
            }
        });
    }

    // Callback interface for image uploads
    interface ImageUploadCallback {
        void onUrlReady(String url);
        void onError(String error);
    }

    public void updateActivityFirebaseId(int activityId, String firebaseId) {
        executor.execute(() -> activityDao.updateActivityFirebaseId(activityId, firebaseId));
    }
}