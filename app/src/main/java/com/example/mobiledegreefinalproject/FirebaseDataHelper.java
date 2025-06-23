package com.example.mobiledegreefinalproject;

import android.util.Log;
import com.example.mobiledegreefinalproject.database.Trip;
import com.example.mobiledegreefinalproject.database.TripActivity;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to standardize Firebase data structure handling
 * Ensures consistency between saving and loading activities
 */
public class FirebaseDataHelper {
    private static final String TAG = "FirebaseDataHelper";
    
    /**
     * Convert TripActivity to Firebase data format
     * This is the SINGLE SOURCE OF TRUTH for activity data structure
     */
    public static Map<String, Object> activityToFirebaseData(TripActivity activity) {
        Map<String, Object> data = new HashMap<>();
        
        // Core activity fields - these must match exactly in loading
        data.put("title", activity.getTitle() != null ? activity.getTitle() : "");
        data.put("description", activity.getDescription() != null ? activity.getDescription() : "");
        data.put("location", activity.getLocation() != null ? activity.getLocation() : "");
        data.put("timeString", activity.getTimeString() != null ? activity.getTimeString() : "");
        data.put("imageUrl", activity.getImageUrl() != null ? activity.getImageUrl() : "");
        
        // Numeric fields
        data.put("dateTime", activity.getDateTime());
        data.put("dayNumber", activity.getDayNumber());
        data.put("latitude", activity.getLatitude());
        data.put("longitude", activity.getLongitude());
        data.put("createdAt", activity.getCreatedAt());
        data.put("updatedAt", activity.getUpdatedAt());
        
        // Metadata
        data.put("platform", "android");
        data.put("synced", true);
        
        return data;
    }
    
    /**
     * Convert Firebase document to TripActivity
     * This is the SINGLE SOURCE OF TRUTH for loading activities
     */
    public static TripActivity firebaseDataToActivity(QueryDocumentSnapshot doc, int tripId) {
        try {
            TripActivity activity = new TripActivity();
            
            // Set Firebase ID
            activity.setFirebaseId(doc.getId());
            activity.setTripId(tripId);
            
            // Load core fields - MUST match the save format exactly
            activity.setTitle(getStringField(doc, "title"));
            activity.setDescription(getStringField(doc, "description"));
            activity.setLocation(getStringField(doc, "location"));
            activity.setTimeString(getStringField(doc, "timeString"));
            activity.setImageUrl(getStringField(doc, "imageUrl"));
            
            // Load numeric fields
            activity.setDateTime(getLongField(doc, "dateTime"));
            activity.setDayNumber(getIntField(doc, "dayNumber"));
            activity.setLatitude(getDoubleField(doc, "latitude"));
            activity.setLongitude(getDoubleField(doc, "longitude"));
            activity.setCreatedAt(getLongField(doc, "createdAt"));
            activity.setUpdatedAt(getLongField(doc, "updatedAt"));
            
            // Mark as synced
            activity.setSynced(true);
            
            Log.d(TAG, "✅ Successfully parsed activity: " + activity.getTitle() + 
                      " (Day " + activity.getDayNumber() + ")");
            
            return activity;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error converting Firebase document to TripActivity", e);
            Log.e(TAG, "   Document ID: " + doc.getId());
            Log.e(TAG, "   Document data: " + doc.getData());
            return null;
        }
    }
    
    /**
     * Convert Trip to Firebase data format
     */
    public static Map<String, Object> tripToFirebaseData(Trip trip) {
        Map<String, Object> data = new HashMap<>();
        
        data.put("title", trip.getTitle() != null ? trip.getTitle() : "");
        data.put("destination", trip.getDestination() != null ? trip.getDestination() : "");
        data.put("startDate", trip.getStartDate());
        data.put("endDate", trip.getEndDate());
        data.put("mapImageUrl", trip.getMapImageUrl() != null ? trip.getMapImageUrl() : "");
        data.put("latitude", trip.getLatitude());
        data.put("longitude", trip.getLongitude());
        data.put("createdAt", trip.getCreatedAt());
        data.put("updatedAt", trip.getUpdatedAt());
        data.put("platform", "android");
        data.put("synced", true);
        
        return data;
    }
    
    /**
     * Helper methods for safe data extraction
     */
    private static String getStringField(QueryDocumentSnapshot doc, String fieldName) {
        try {
            Object value = doc.get(fieldName);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            Log.w(TAG, "Failed to get string field: " + fieldName, e);
            return "";
        }
    }
    
    private static long getLongField(QueryDocumentSnapshot doc, String fieldName) {
        try {
            Object value = doc.get(fieldName);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return 0L;
        } catch (Exception e) {
            Log.w(TAG, "Failed to get long field: " + fieldName, e);
            return 0L;
        }
    }
    
    private static int getIntField(QueryDocumentSnapshot doc, String fieldName) {
        try {
            Object value = doc.get(fieldName);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return 0;
        } catch (Exception e) {
            Log.w(TAG, "Failed to get int field: " + fieldName, e);
            return 0;
        }
    }
    
    private static double getDoubleField(QueryDocumentSnapshot doc, String fieldName) {
        try {
            Object value = doc.get(fieldName);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return 0.0;
        } catch (Exception e) {
            Log.w(TAG, "Failed to get double field: " + fieldName, e);
            return 0.0;
        }
    }
} 