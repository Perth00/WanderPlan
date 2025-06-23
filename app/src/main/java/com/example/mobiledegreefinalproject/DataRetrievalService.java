package com.example.mobiledegreefinalproject;

import android.content.Context;
import android.util.Log;

import com.example.mobiledegreefinalproject.database.Trip;
import com.example.mobiledegreefinalproject.database.TripActivity;
import com.example.mobiledegreefinalproject.repository.TripRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service to retrieve JSON data from Firebase and restore it to local database
 * Complements DataSyncService for full sync functionality
 */
public class DataRetrievalService {
    private static final String TAG = "DataRetrievalService";
    private static final String COLLECTION_USER_DATA = "user_data_json";
    private static final String COLLECTION_TRIPS = "trips_json";
    
    private final Context context;
    private final TripRepository tripRepository;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final ExecutorService executor;
    
    public DataRetrievalService(Context context) {
        this.context = context;
        this.tripRepository = TripRepository.getInstance(context);
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    public interface OnRetrievalCompleteListener {
        void onProgressUpdate(int progress, String message);
        void onSuccess(int tripsRetrieved, int activitiesRetrieved);
        void onError(String error);
    }
    
    /**
     * Retrieve all user data from Firebase JSON format and restore to local database
     */
    public void retrieveDataFromFirebase(OnRetrievalCompleteListener listener) {
        if (auth.getCurrentUser() == null) {
            listener.onError("User not authenticated");
            return;
        }
        
        String userId = auth.getCurrentUser().getUid();
        Log.d(TAG, "Starting data retrieval for user: " + userId);
        
        listener.onProgressUpdate(10, "🔍 Searching for cloud data...");
        
        // Check if user has synced data
        firestore.collection(COLLECTION_USER_DATA)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        listener.onProgressUpdate(100, "ℹ️ No cloud data found");
                        listener.onSuccess(0, 0);
                        return;
                    }
                    
                    // Get sync summary
                    Map<String, Object> summary = documentSnapshot.getData();
                    Object tripsSyncedObj = summary.get("tripsSynced");
                    Object activitiesSyncedObj = summary.get("activitiesSynced");
                    
                    int expectedTrips = tripsSyncedObj instanceof Number ? 
                            ((Number) tripsSyncedObj).intValue() : 0;
                    int expectedActivities = activitiesSyncedObj instanceof Number ? 
                            ((Number) activitiesSyncedObj).intValue() : 0;
                    
                    listener.onProgressUpdate(20, "📊 Found " + expectedTrips + " trips in cloud");
                    
                    // Retrieve all trip documents
                    retrieveTripsFromFirebase(userId, expectedTrips, expectedActivities, listener);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check user data", e);
                    listener.onError("Failed to access cloud data: " + e.getMessage());
                });
    }
    
    /**
     * Retrieve all trips from Firebase
     */
    private void retrieveTripsFromFirebase(String userId, int expectedTrips, int expectedActivities,
                                         OnRetrievalCompleteListener listener) {
        firestore.collection(COLLECTION_USER_DATA)
                .document(userId)
                .collection(COLLECTION_TRIPS)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    executor.execute(() -> {
                        try {
                            List<Trip> retrievedTrips = new ArrayList<>();
                            List<TripActivity> retrievedActivities = new ArrayList<>();
                            
                            listener.onProgressUpdate(30, "🔄 Converting JSON to local data...");
                            
                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                Map<String, Object> data = doc.getData();
                                
                                // Convert JSON back to Trip object
                                Trip trip = convertJsonToTrip(data);
                                if (trip != null) {
                                    retrievedTrips.add(trip);
                                    
                                    // Convert activities
                                    Object activitiesObj = data.get("activities");
                                    if (activitiesObj instanceof Map) {
                                        Map<String, Object> activitiesMap = (Map<String, Object>) activitiesObj;
                                        List<TripActivity> tripActivities = convertJsonToActivities(activitiesMap, trip.getId());
                                        retrievedActivities.addAll(tripActivities);
                                    }
                                }
                            }
                            
                            listener.onProgressUpdate(60, "💾 Saving to local database...");
                            
                            // Save to local database
                            saveToLocalDatabase(retrievedTrips, retrievedActivities, listener);
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing retrieved data", e);
                            listener.onError("Failed to process cloud data: " + e.getMessage());
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to retrieve trips", e);
                    listener.onError("Failed to retrieve trips from cloud: " + e.getMessage());
                });
    }
    
    /**
     * Convert JSON data back to Trip object
     */
    private Trip convertJsonToTrip(Map<String, Object> data) {
        try {
            Trip trip = new Trip();
            
            // Basic fields
            if (data.get("id") instanceof Number) {
                trip.setId(((Number) data.get("id")).intValue());
            }
            trip.setFirebaseId((String) data.get("firebaseId"));
            trip.setTitle((String) data.get("title"));
            trip.setDestination((String) data.get("destination"));
            
            if (data.get("startDate") instanceof Number) {
                trip.setStartDate(((Number) data.get("startDate")).longValue());
            }
            if (data.get("endDate") instanceof Number) {
                trip.setEndDate(((Number) data.get("endDate")).longValue());
            }
            
            trip.setMapImageUrl((String) data.get("mapImageUrl"));
            
            if (data.get("latitude") instanceof Number) {
                trip.setLatitude(((Number) data.get("latitude")).doubleValue());
            }
            if (data.get("longitude") instanceof Number) {
                trip.setLongitude(((Number) data.get("longitude")).doubleValue());
            }
            if (data.get("createdAt") instanceof Number) {
                trip.setCreatedAt(((Number) data.get("createdAt")).longValue());
            }
            if (data.get("updatedAt") instanceof Number) {
                trip.setUpdatedAt(((Number) data.get("updatedAt")).longValue());
            }
            
            trip.setSynced(true); // Mark as synced since it came from Firebase
            
            return trip;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting JSON to Trip", e);
            return null;
        }
    }
    
    /**
     * Convert JSON activities data back to TripActivity objects
     */
    private List<TripActivity> convertJsonToActivities(Map<String, Object> activitiesMap, int tripId) {
        List<TripActivity> activities = new ArrayList<>();
        
        try {
            for (Map.Entry<String, Object> entry : activitiesMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> activityData = (Map<String, Object>) entry.getValue();
                    TripActivity activity = convertJsonToActivity(activityData, tripId);
                    if (activity != null) {
                        activities.add(activity);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting activities JSON", e);
        }
        
        return activities;
    }
    
    /**
     * Convert JSON data back to TripActivity object
     */
    private TripActivity convertJsonToActivity(Map<String, Object> data, int tripId) {
        try {
            TripActivity activity = new TripActivity();
            
            if (data.get("id") instanceof Number) {
                activity.setId(((Number) data.get("id")).intValue());
            }
            activity.setTripId(tripId);
            activity.setFirebaseId((String) data.get("firebaseId"));
            activity.setTitle((String) data.get("title"));
            activity.setDescription((String) data.get("description"));
            activity.setLocation((String) data.get("location"));
            
            if (data.get("dateTime") instanceof Number) {
                activity.setDateTime(((Number) data.get("dateTime")).longValue());
            }
            if (data.get("dayNumber") instanceof Number) {
                activity.setDayNumber(((Number) data.get("dayNumber")).intValue());
            }
            
            activity.setTimeString((String) data.get("timeString"));
            activity.setImageUrl((String) data.get("imageUrl"));
            activity.setImageLocalPath((String) data.get("imageLocalPath"));
            
            if (data.get("latitude") instanceof Number) {
                activity.setLatitude(((Number) data.get("latitude")).doubleValue());
            }
            if (data.get("longitude") instanceof Number) {
                activity.setLongitude(((Number) data.get("longitude")).doubleValue());
            }
            if (data.get("createdAt") instanceof Number) {
                activity.setCreatedAt(((Number) data.get("createdAt")).longValue());
            }
            if (data.get("updatedAt") instanceof Number) {
                activity.setUpdatedAt(((Number) data.get("updatedAt")).longValue());
            }
            
            activity.setSynced(true); // Mark as synced since it came from Firebase
            
            return activity;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting JSON to TripActivity", e);
            return null;
        }
    }
    
    /**
     * Save retrieved data to local database
     */
    private void saveToLocalDatabase(List<Trip> trips, List<TripActivity> activities, 
                                   OnRetrievalCompleteListener listener) {
        if (trips.isEmpty()) {
            listener.onProgressUpdate(100, "ℹ️ No data to restore");
            listener.onSuccess(0, 0);
            return;
        }
        
        // Clear existing local data first
        tripRepository.clearAllLocalTrips();
        
        listener.onProgressUpdate(80, "💾 Saving " + trips.size() + " trips...");
        
        // Insert trips first, then activities
        insertTripsSequentially(trips, activities, 0, listener);
    }
    
    /**
     * Insert trips one by one to handle foreign key constraints
     */
    private void insertTripsSequentially(List<Trip> trips, List<TripActivity> allActivities, 
                                       int index, OnRetrievalCompleteListener listener) {
        if (index >= trips.size()) {
            // All trips inserted, now count results
            listener.onProgressUpdate(100, "✅ Data restore complete!");
            listener.onSuccess(trips.size(), allActivities.size());
            return;
        }
        
        Trip trip = trips.get(index);
        
        tripRepository.insertTrip(trip, new TripRepository.OnTripOperationListener() {
            @Override
            public void onSuccess(int tripId) {
                // Update trip ID for activities
                for (TripActivity activity : allActivities) {
                    if (activity.getTripId() == trip.getId()) {
                        activity.setTripId(tripId);
                    }
                }
                
                // Insert activities for this trip
                List<TripActivity> tripActivities = new ArrayList<>();
                for (TripActivity activity : allActivities) {
                    if (activity.getTripId() == tripId) {
                        tripActivities.add(activity);
                    }
                }
                
                insertActivitiesForTrip(tripActivities, 0, () -> {
                    // Continue with next trip
                    int progress = 80 + ((index + 1) * 15 / trips.size());
                    listener.onProgressUpdate(progress, "💾 Saved trip " + (index + 1) + "/" + trips.size());
                    insertTripsSequentially(trips, allActivities, index + 1, listener);
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to insert trip: " + trip.getTitle() + " - " + error);
                // Continue with next trip even if this one failed
                insertTripsSequentially(trips, allActivities, index + 1, listener);
            }
        });
    }
    
    /**
     * Insert activities for a trip
     */
    private void insertActivitiesForTrip(List<TripActivity> activities, int index, Runnable onComplete) {
        if (index >= activities.size()) {
            onComplete.run();
            return;
        }
        
        TripActivity activity = activities.get(index);
        
        tripRepository.insertActivity(activity, new TripRepository.OnActivityOperationListener() {
            @Override
            public void onSuccess(int activityId) {
                insertActivitiesForTrip(activities, index + 1, onComplete);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to insert activity: " + activity.getTitle() + " - " + error);
                // Continue with next activity even if this one failed
                insertActivitiesForTrip(activities, index + 1, onComplete);
            }
        });
    }
} 