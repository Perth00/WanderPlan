package com.example.mobiledegreefinalproject;

import android.content.Context;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/**
 * Debug helper to diagnose Firebase data loading issues
 * This will help identify exactly what's happening with activity loading
 */
public class FirebaseDebugHelper {
    private static final String TAG = "FirebaseDebugHelper";
    
    public interface DebugCompleteListener {
        void onDebugComplete(String report);
    }
    
    /**
     * Comprehensive Firebase data structure analysis
     */
    public static void diagnoseFirebaseDataIssues(Context context, DebugCompleteListener listener) {
        StringBuilder report = new StringBuilder();
        report.append("🔍 FIREBASE DATA DIAGNOSIS REPORT\n");
        report.append("=====================================\n\n");
        
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        
        // Check authentication
        if (auth.getCurrentUser() == null) {
            report.append("❌ CRITICAL: User not authenticated\n");
            listener.onDebugComplete(report.toString());
            return;
        }
        
        String userEmail = auth.getCurrentUser().getEmail();
        report.append("✅ User authenticated: ").append(userEmail).append("\n\n");
        
        // Step 1: Check user document structure
        report.append("🔍 STEP 1: Checking user document structure\n");
        firestore.collection("users")
                .document(userEmail)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        report.append("✅ User document exists\n");
                        report.append("   Data: ").append(userDoc.getData()).append("\n\n");
                    } else {
                        report.append("❌ User document does not exist\n\n");
                    }
                    
                    // Step 2: Check trips collection
                    checkTripsCollection(firestore, userEmail, report, listener);
                })
                .addOnFailureListener(e -> {
                    report.append("❌ Failed to access user document: ").append(e.getMessage()).append("\n\n");
                    checkTripsCollection(firestore, userEmail, report, listener);
                });
    }
    
    private static void checkTripsCollection(FirebaseFirestore firestore, String userEmail, 
                                           StringBuilder report, DebugCompleteListener listener) {
        report.append("🔍 STEP 2: Checking trips collection\n");
        firestore.collection("users")
                .document(userEmail)
                .collection("trips")
                .get()
                .addOnSuccessListener(tripsQuery -> {
                    report.append("✅ Trips collection accessible\n");
                    report.append("   Found ").append(tripsQuery.size()).append(" trips\n\n");
                    
                    if (tripsQuery.isEmpty()) {
                        report.append("❌ NO TRIPS FOUND - This is likely the issue!\n");
                        report.append("   📝 Recommendation: Check if data was saved with correct email/structure\n\n");
                        listener.onDebugComplete(report.toString());
                        return;
                    }
                    
                    // Analyze each trip
                    for (QueryDocumentSnapshot tripDoc : tripsQuery) {
                        report.append("📍 Trip: ").append(tripDoc.getId()).append("\n");
                        report.append("   Title: ").append(tripDoc.getString("title")).append("\n");
                        report.append("   Destination: ").append(tripDoc.getString("destination")).append("\n");
                        report.append("   Data fields: ").append(tripDoc.getData().keySet()).append("\n\n");
                    }
                    
                    // Step 3: Check activities for first trip
                    QueryDocumentSnapshot firstTrip = (QueryDocumentSnapshot) tripsQuery.getDocuments().get(0);
                    checkActivitiesForTrip(firestore, userEmail, firstTrip.getId(), 
                                         firstTrip.getString("title"), report, listener);
                })
                .addOnFailureListener(e -> {
                    report.append("❌ Failed to access trips collection: ").append(e.getMessage()).append("\n");
                    report.append("   📝 This could be a permissions issue or incorrect collection path\n\n");
                    listener.onDebugComplete(report.toString());
                });
    }
    
    private static void checkActivitiesForTrip(FirebaseFirestore firestore, String userEmail, 
                                             String tripId, String tripTitle, 
                                             StringBuilder report, DebugCompleteListener listener) {
        report.append("🔍 STEP 3: Checking activities for trip: ").append(tripTitle).append("\n");
        firestore.collection("users")
                .document(userEmail)
                .collection("trips")
                .document(tripId)
                .collection("activities")
                .get()
                .addOnSuccessListener(activitiesQuery -> {
                    report.append("✅ Activities collection accessible\n");
                    report.append("   Found ").append(activitiesQuery.size()).append(" activities\n\n");
                    
                    if (activitiesQuery.isEmpty()) {
                        report.append("❌ NO ACTIVITIES FOUND FOR THIS TRIP\n");
                        report.append("   📝 This is likely the main issue!\n");
                        report.append("   📝 Possible causes:\n");
                        report.append("      - Activities were saved to a different trip ID\n");
                        report.append("      - Activities were saved with different data structure\n");
                        report.append("      - Activities were never actually saved to Firebase\n\n");
                    } else {
                        // Analyze first activity structure
                        QueryDocumentSnapshot firstActivity = (QueryDocumentSnapshot) activitiesQuery.getDocuments().get(0);
                        report.append("📋 Sample Activity Analysis:\n");
                        report.append("   ID: ").append(firstActivity.getId()).append("\n");
                        report.append("   Data fields: ").append(firstActivity.getData().keySet()).append("\n");
                        
                        // Check required fields
                        String[] requiredFields = {"title", "description", "location", "dateTime", "dayNumber"};
                        for (String field : requiredFields) {
                            Object value = firstActivity.get(field);
                            report.append("   ").append(field).append(": ");
                            if (value != null) {
                                report.append("✅ ").append(value).append(" (").append(value.getClass().getSimpleName()).append(")\n");
                            } else {
                                report.append("❌ NULL\n");
                            }
                        }
                        report.append("\n");
                    }
                    
                    // Step 4: Check alternative data locations
                    checkAlternativeDataLocations(firestore, userEmail, report, listener);
                })
                .addOnFailureListener(e -> {
                    report.append("❌ Failed to access activities collection: ").append(e.getMessage()).append("\n");
                    report.append("   📝 This could be a permissions issue or path problem\n\n");
                    checkAlternativeDataLocations(firestore, userEmail, report, listener);
                });
    }
    
    private static void checkAlternativeDataLocations(FirebaseFirestore firestore, String userEmail,
                                                    StringBuilder report, DebugCompleteListener listener) {
        report.append("🔍 STEP 4: Checking alternative data locations\n");
        
        // Check if data was saved in JSON format (from DataRetrievalService)
        firestore.collection("user_data_json")
                .document(userEmail.replace(".", "_")) // Some systems replace dots in emails
                .get()
                .addOnSuccessListener(jsonDoc -> {
                    if (jsonDoc.exists()) {
                        report.append("✅ Found JSON format data\n");
                        report.append("   Data: ").append(jsonDoc.getData()).append("\n\n");
                    } else {
                        report.append("❌ No JSON format data found\n\n");
                    }
                    
                    // Final recommendations
                    generateRecommendations(report);
                    listener.onDebugComplete(report.toString());
                })
                .addOnFailureListener(e -> {
                    report.append("❌ Failed to check JSON data: ").append(e.getMessage()).append("\n\n");
                    generateRecommendations(report);
                    listener.onDebugComplete(report.toString());
                });
    }
    
    private static void generateRecommendations(StringBuilder report) {
        report.append("🛠️ RECOMMENDATIONS\n");
        report.append("==================\n");
        report.append("1. Verify data was actually saved to Firebase during sync\n");
        report.append("2. Check if correct user email is being used\n");
        report.append("3. Ensure Firebase rules allow read access\n");
        report.append("4. Try manual sync to save test data\n");
        report.append("5. Use Firebase console to verify data structure\n\n");
        
        report.append("📱 NEXT STEPS\n");
        report.append("=============\n");
        report.append("1. Run this diagnosis after saving some activities\n");
        report.append("2. Compare the data structure between save and load\n");
        report.append("3. Check Firebase Authentication rules\n");
        report.append("4. Verify network connectivity during operations\n");
    }
} 