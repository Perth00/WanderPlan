package com.example.mobiledegreefinalproject;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Test activity to debug Firebase connectivity and data storage
 * Use this to verify Firebase is working properly
 */
public class FirebaseTestActivity extends Activity {
    private static final String TAG = "FirebaseTest";
    
    private TextView statusText;
    private Button testConnectionBtn;
    private Button testSyncBtn;
    private Button checkDataBtn;
    private Button backBtn;
    
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create simple layout programmatically
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        // Status text
        statusText = new TextView(this);
        statusText.setText("Firebase Test Activity\n\nReady to test...");
        statusText.setTextSize(16);
        statusText.setPadding(0, 0, 0, 32);
        layout.addView(statusText);
        
        // Test connection button
        testConnectionBtn = new Button(this);
        testConnectionBtn.setText("🔗 Test Firebase Connection");
        testConnectionBtn.setOnClickListener(v -> testFirebaseConnection());
        layout.addView(testConnectionBtn);
        
        // Test sync button
        testSyncBtn = new Button(this);
        testSyncBtn.setText("🔄 Test Data Sync");
        testSyncBtn.setOnClickListener(v -> testDataSync());
        layout.addView(testSyncBtn);
        
        // Check data button
        checkDataBtn = new Button(this);
        checkDataBtn.setText("📊 Check Firebase Data");
        checkDataBtn.setOnClickListener(v -> checkFirebaseData());
        layout.addView(checkDataBtn);
        
        // Test image storage button
        Button testImageBtn = new Button(this);
        testImageBtn.setText("📷 Test Image Storage");
        testImageBtn.setOnClickListener(v -> testImageStorage());
        layout.addView(testImageBtn);
        
        // Test budget data button
        Button testBudgetBtn = new Button(this);
        testBudgetBtn.setText("💰 Test Budget Data");
        testBudgetBtn.setOnClickListener(v -> testBudgetData());
        layout.addView(testBudgetBtn);
        
        // Back button
        backBtn = new Button(this);
        backBtn.setText("⬅️ Back to Login");
        backBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
        layout.addView(backBtn);
        
        setContentView(layout);
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        
        updateStatus("Initialized. User: " + (auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : "Not logged in"));
    }
    
    private void updateStatus(String message) {
        Log.d(TAG, message);
        statusText.setText("Firebase Test Activity\n\n" + message);
    }
    
    private void testFirebaseConnection() {
        updateStatus("🔗 Testing Firebase connection...");
        
        if (auth.getCurrentUser() == null) {
            updateStatus("❌ Error: User not logged in");
            return;
        }
        
        String userId = auth.getCurrentUser().getUid();
        
        // Test data
        Map<String, Object> testData = new HashMap<>();
        testData.put("test", true);
        testData.put("timestamp", System.currentTimeMillis());
        testData.put("message", "Firebase connection test");
        
        firestore.collection("test_connection")
                .document(userId)
                .set(testData)
                .addOnSuccessListener(aVoid -> {
                    updateStatus("✅ Firebase connection SUCCESS!\n\nWrite test passed.");
                    
                    // Now test read
                    firestore.collection("test_connection")
                            .document(userId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    updateStatus("✅ Firebase connection SUCCESS!\n\nRead/Write tests passed.\n\nFirebase is working correctly!");
                                    
                                    // Clean up test document
                                    firestore.collection("test_connection").document(userId).delete();
                                } else {
                                    updateStatus("⚠️ Write succeeded but read failed");
                                }
                            })
                            .addOnFailureListener(e -> {
                                updateStatus("❌ Read test failed: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    updateStatus("❌ Firebase connection FAILED!\n\nError: " + e.getMessage());
                    Log.e(TAG, "Firebase connection test failed", e);
                });
    }
    
    private void testDataSync() {
        updateStatus("🔄 Testing data sync...");
        
        if (auth.getCurrentUser() == null) {
            updateStatus("❌ Error: User not logged in");
            return;
        }
        
        DataSyncService syncService = new DataSyncService(this);
        syncService.syncLocalDataToFirebase(new DataSyncService.OnSyncCompleteListener() {
            @Override
            public void onProgressUpdate(int progress, String message) {
                updateStatus("🔄 Sync progress: " + progress + "%\n" + message);
            }
            
            @Override
            public void onSuccess(int tripsSynced, int activitiesSynced) {
                updateStatus("✅ Sync SUCCESS!\n\nTrips synced: " + tripsSynced + "\nActivities synced: " + activitiesSynced);
                Toast.makeText(FirebaseTestActivity.this, "Sync completed successfully!", Toast.LENGTH_LONG).show();
            }
            
            @Override
            public void onError(String error) {
                updateStatus("❌ Sync FAILED!\n\nError: " + error);
                Toast.makeText(FirebaseTestActivity.this, "Sync failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void checkFirebaseData() {
        updateStatus("📊 Checking Firebase data...");
        
        if (auth.getCurrentUser() == null) {
            updateStatus("❌ Error: User not logged in");
            return;
        }
        
        String userId = auth.getCurrentUser().getUid();
        
        // Check user data summary
        firestore.collection("user_data_json")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Map<String, Object> data = documentSnapshot.getData();
                        Object tripsCount = data.get("tripsSynced");
                        Object activitiesCount = data.get("activitiesSynced");
                        Object syncTime = data.get("syncedAt");
                        
                        String summary = "✅ User data found!\n\n" +
                                        "Trips synced: " + tripsCount + "\n" +
                                        "Activities synced: " + activitiesCount + "\n" +
                                        "Last sync: " + new java.util.Date((Long) syncTime);
                        
                        updateStatus(summary);
                        
                        // Now check actual trip documents
                        checkTripDocuments(userId);
                    } else {
                        updateStatus("ℹ️ No user data found in Firebase.\n\nUser hasn't synced data yet.");
                    }
                })
                .addOnFailureListener(e -> {
                    updateStatus("❌ Failed to check data: " + e.getMessage());
                });
    }
    
    private void checkTripDocuments(String userId) {
        firestore.collection("user_data_json")
                .document(userId)
                .collection("trips_json")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int tripCount = queryDocumentSnapshots.size();
                    StringBuilder summary = new StringBuilder();
                    summary.append("📊 Found ").append(tripCount).append(" trip documents:\n\n");
                    
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Map<String, Object> data = doc.getData();
                        String title = (String) data.get("title");
                        String destination = (String) data.get("destination");
                        Object activitiesCount = data.get("activities_count");
                        
                        summary.append("🧳 ").append(title)
                               .append(" (").append(destination).append(")")
                               .append(" - ").append(activitiesCount).append(" activities\n");
                    }
                    
                    updateStatus(summary.toString());
                })
                .addOnFailureListener(e -> {
                    updateStatus("❌ Failed to check trip documents: " + e.getMessage());
                });
    }

    private void testImageStorage() {
        try {
            // Check internal storage for guest images
            File imagesDir = new File(getFilesDir(), "activity_images");
            
            if (imagesDir.exists()) {
                File[] imageFiles = imagesDir.listFiles();
                if (imageFiles != null && imageFiles.length > 0) {
                    updateStatus("✅ Found " + imageFiles.length + " guest images in internal storage:\n" + 
                                 java.util.Arrays.toString(imageFiles));
                    
                    // Test if we can load one of the images
                    File testImage = imageFiles[0];
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(testImage.getAbsolutePath());
                    if (bitmap != null) {
                        updateStatus("✅ Successfully loaded test image: " + testImage.getName() + 
                                     " (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ")");
                    } else {
                        updateStatus("❌ Failed to load test image: " + testImage.getName());
                    }
                } else {
                    updateStatus("⚠️ No guest images found in internal storage. Create activities with images as guest first.");
                }
            } else {
                updateStatus("⚠️ Internal storage images directory doesn't exist. Create activities with images as guest first.");
            }
            
        } catch (Exception e) {
            updateStatus("❌ Error testing image storage: " + e.getMessage());
            android.util.Log.e("FirebaseTestActivity", "Error testing image storage", e);
        }
    }
    
    private void testBudgetData() {
        updateStatus("💰 Testing budget data...");
        
        try {
            com.example.mobiledegreefinalproject.repository.BudgetRepository budgetRepo = 
                com.example.mobiledegreefinalproject.repository.BudgetRepository.getInstance(this);
            
            // Load local budget data
            com.example.mobiledegreefinalproject.repository.BudgetRepository.BudgetData localData = 
                budgetRepo.loadBudgetDataLocally();
            
            int totalBudgetCount = localData.tripBudgets.size();
            int totalExpenseCount = 0;
            for (java.util.List<com.example.mobiledegreefinalproject.model.Expense> expenses : localData.tripExpenses.values()) {
                totalExpenseCount += expenses.size();
            }
            
            StringBuilder result = new StringBuilder();
            result.append("💰 Local Budget Data Summary:\n\n");
            result.append("Trip Budgets: ").append(totalBudgetCount).append("\n");
            result.append("Total Expenses: ").append(totalExpenseCount).append("\n\n");
            
            if (totalBudgetCount > 0) {
                result.append("Trip Budgets:\n");
                for (java.util.Map.Entry<Integer, Double> entry : localData.tripBudgets.entrySet()) {
                    result.append("Trip ").append(entry.getKey()).append(": RM").append(entry.getValue()).append("\n");
                }
                result.append("\n");
            }
            
            if (totalExpenseCount > 0) {
                result.append("Expenses by Trip:\n");
                for (java.util.Map.Entry<Integer, java.util.List<com.example.mobiledegreefinalproject.model.Expense>> entry : localData.tripExpenses.entrySet()) {
                    result.append("Trip ").append(entry.getKey()).append(": ").append(entry.getValue().size()).append(" expenses\n");
                    for (com.example.mobiledegreefinalproject.model.Expense expense : entry.getValue()) {
                        result.append("  - ").append(expense.getTitle()).append(": RM").append(expense.getAmount()).append("\n");
                    }
                }
            }
            
            if (totalBudgetCount == 0 && totalExpenseCount == 0) {
                result.append("⚠️ No budget data found. Add some budgets and expenses first.");
            }
            
            updateStatus(result.toString());
            
        } catch (Exception e) {
            updateStatus("❌ Error testing budget data: " + e.getMessage());
            android.util.Log.e("FirebaseTestActivity", "Error testing budget data", e);
        }
    }
} 