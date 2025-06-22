package com.example.mobiledegreefinalproject.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.mobiledegreefinalproject.UserManager;
import com.example.mobiledegreefinalproject.model.Expense;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BudgetRepository {
    private static final String TAG = "BudgetRepository";
    private static final String PREFS_NAME = "BudgetFragment";
    
    private static BudgetRepository INSTANCE;
    private Context context;
    private SharedPreferences sharedPreferences;
    private Gson gson;
    private FirebaseFirestore firestore;
    private UserManager userManager;
    private ExecutorService executor;

    private BudgetRepository(Context context) {
        this.context = context.getApplicationContext();
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.firestore = FirebaseFirestore.getInstance();
        this.userManager = UserManager.getInstance(context);
        this.executor = Executors.newFixedThreadPool(3);
    }

    public static synchronized BudgetRepository getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new BudgetRepository(context);
        }
        return INSTANCE;
    }

    // Interfaces for callbacks
    public interface OnBudgetOperationListener {
        void onSuccess();
        void onError(String error);
    }

    public interface OnBudgetSyncListener {
        void onSuccess();
        void onError(String error);
        void onSyncRequired(int localBudgetCount, int firebaseBudgetCount);
    }
    
    public interface OnBudgetFetchListener {
        void onSuccess();
        void onError(String error);
    }

    // Save budget data to local storage
    public void saveBudgetDataLocally(Map<Integer, Double> tripBudgets, Map<Integer, List<Expense>> tripExpenses, int selectedTripId, double totalBudget) {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            // Save trip budgets
            String budgetsJson = gson.toJson(tripBudgets);
            editor.putString("trip_budgets", budgetsJson);
            
            // Save trip expenses
            String expensesJson = gson.toJson(tripExpenses);
            editor.putString("trip_expenses", expensesJson);
            
            // Save selected trip ID
            editor.putInt("selected_trip_id", selectedTripId);
            
            // Save total budget
            editor.putLong("total_budget", Double.doubleToLongBits(totalBudget));
            
            editor.apply();
            
            Log.d(TAG, "Saved budget data locally");
        } catch (Exception e) {
            Log.e(TAG, "Error saving budget data locally", e);
        }
    }

    // Load budget data from local storage
    public BudgetData loadBudgetDataLocally() {
        try {
            BudgetData data = new BudgetData();
            
            // Load trip budgets
            String budgetsJson = sharedPreferences.getString("trip_budgets", "{}");
            Type budgetType = new TypeToken<Map<Integer, Double>>(){}.getType();
            Map<Integer, Double> savedBudgets = gson.fromJson(budgetsJson, budgetType);
            if (savedBudgets != null) {
                data.tripBudgets.putAll(savedBudgets);
            }
            
            // Load trip expenses
            String expensesJson = sharedPreferences.getString("trip_expenses", "{}");
            Type expenseType = new TypeToken<Map<Integer, List<Expense>>>(){}.getType();
            Map<Integer, List<Expense>> savedExpenses = gson.fromJson(expensesJson, expenseType);
            if (savedExpenses != null) {
                data.tripExpenses.putAll(savedExpenses);
            }
            
            // Load selected trip ID
            data.selectedTripId = sharedPreferences.getInt("selected_trip_id", -1);
            
            // Load total budget
            data.totalBudget = Double.longBitsToDouble(sharedPreferences.getLong("total_budget", 
                Double.doubleToLongBits(2000.0)));
            
            Log.d(TAG, "Loaded budget data: " + data.tripBudgets.size() + " trip budgets, " + 
                  data.tripExpenses.size() + " trip expenses");
            
            return data;
        } catch (Exception e) {
            Log.e(TAG, "Error loading budget data", e);
            return new BudgetData();
        }
    }

    // Clear local budget data
    public void clearLocalBudgetData() {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove("trip_budgets");
            editor.remove("trip_expenses");
            editor.remove("selected_trip_id");
            editor.remove("total_budget");
            editor.apply();
            
            Log.d(TAG, "Cleared local budget data");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing local budget data", e);
        }
    }

    // Check if user has unsynced local budget data
    public void checkBudgetSyncStatus(OnBudgetSyncListener listener) {
        if (!userManager.isLoggedIn()) {
            listener.onError("User not logged in");
            return;
        }
        
        executor.execute(() -> {
            try {
                BudgetData localData = loadBudgetDataLocally();
                
                // Count local budget entries
                int expenseCount = 0;
                for (List<Expense> expenses : localData.tripExpenses.values()) {
                    expenseCount += expenses.size();
                }
                
                // Calculate total budget entries (expenses + budgets)
                final int totalBudgetEntries = expenseCount + localData.tripBudgets.size();
                
                Log.d(TAG, "Budget sync status: " + totalBudgetEntries + " local budget entries");
                
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (totalBudgetEntries > 0) {
                        // There are unsynced local budget entries
                        listener.onSyncRequired(totalBudgetEntries, 0);
                    } else {
                        // No sync needed
                        listener.onSuccess();
                    }
                });
                    
            } catch (Exception e) {
                Log.e(TAG, "Error checking budget sync status", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    listener.onError("Failed to check budget sync status: " + e.getMessage());
                });
            }
        });
    }

    // Sync local budget data to Firebase
    public void syncLocalBudgetDataToFirebase(OnBudgetSyncListener listener) {
        if (!userManager.isLoggedIn()) {
            listener.onError("User not logged in");
            return;
        }
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "Starting sync of local budget data to Firebase");
                
                BudgetData localData = loadBudgetDataLocally();
                
                if (localData.tripBudgets.isEmpty() && localData.tripExpenses.isEmpty()) {
                    Log.d(TAG, "No local budget data found");
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onSuccess();
                    });
                    return;
                }
                
                // Counter for tracking completion
                final int[] totalOperations = {0};
                final int[] completed = {0};
                final int[] successful = {0};
                final boolean[] hasError = {false};
                final StringBuilder errorMessages = new StringBuilder();
                
                // Count total operations needed
                totalOperations[0] += localData.tripBudgets.size(); // Budget entries
                for (List<Expense> expenses : localData.tripExpenses.values()) {
                    totalOperations[0] += expenses.size(); // Expense entries
                }
                
                if (totalOperations[0] == 0) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onSuccess();
                    });
                    return;
                }
                
                String userEmail = userManager.getUserEmail();
                if (userEmail == null || userEmail.trim().isEmpty()) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onError("User email not available");
                    });
                    return;
                }
                
                // Sync trip budgets
                for (Map.Entry<Integer, Double> budgetEntry : localData.tripBudgets.entrySet()) {
                    syncTripBudgetToFirebaseInternal(budgetEntry.getKey(), budgetEntry.getValue(), userEmail, 
                        new OnBudgetOperationListener() {
                            @Override
                            public void onSuccess() {
                                synchronized (completed) {
                                    completed[0]++;
                                    successful[0]++;
                                    checkSyncCompletion(completed[0], totalOperations[0], successful[0], 
                                        errorMessages, listener);
                                }
                            }
                            
                            @Override
                            public void onError(String error) {
                                synchronized (completed) {
                                    completed[0]++;
                                    hasError[0] = true;
                                    errorMessages.append("Budget sync error: ").append(error).append("; ");
                                    checkSyncCompletion(completed[0], totalOperations[0], successful[0], 
                                        errorMessages, listener);
                                }
                            }
                        });
                }
                
                // Sync trip expenses
                for (Map.Entry<Integer, List<Expense>> expenseEntry : localData.tripExpenses.entrySet()) {
                    for (Expense expense : expenseEntry.getValue()) {
                        expense.setTripId(expenseEntry.getKey()); // Ensure tripId is set
                        syncExpenseToFirebaseInternal(expense, userEmail, new OnBudgetOperationListener() {
                            @Override
                            public void onSuccess() {
                                synchronized (completed) {
                                    completed[0]++;
                                    successful[0]++;
                                    checkSyncCompletion(completed[0], totalOperations[0], successful[0], 
                                        errorMessages, listener);
                                }
                            }
                            
                            @Override
                            public void onError(String error) {
                                synchronized (completed) {
                                    completed[0]++;
                                    hasError[0] = true;
                                    errorMessages.append("Expense sync error: ").append(error).append("; ");
                                    checkSyncCompletion(completed[0], totalOperations[0], successful[0], 
                                        errorMessages, listener);
                                }
                            }
                        });
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error syncing budget data to Firebase", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    listener.onError("Failed to sync budget data: " + e.getMessage());
                });
            }
        });
    }
    
    private void checkSyncCompletion(int completed, int total, int successful, 
            StringBuilder errorMessages, OnBudgetSyncListener listener) {
        if (completed >= total) {
            // All operations completed
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (successful == total) {
                    Log.d(TAG, "All budget data synced successfully");
                    listener.onSuccess();
                } else {
                    String message = "Partial sync: " + successful + "/" + total + " budget entries synced";
                    if (errorMessages.length() > 0) {
                        message += ". Errors: " + errorMessages.toString();
                    }
                    listener.onError(message);
                }
            });
        }
    }

    // Sync a single trip budget to Firebase (public method)
    public void syncTripBudgetToFirebase(int tripId, double budget, String userEmail, OnBudgetOperationListener listener) {
        syncTripBudgetToFirebaseInternal(tripId, budget, userEmail, listener);
    }
    
    // Sync a single expense to Firebase (public method)
    public void syncExpenseToFirebase(Expense expense, String userEmail, OnBudgetOperationListener listener) {
        syncExpenseToFirebaseInternal(expense, userEmail, listener);
    }
    
    // Sync a single trip budget to Firebase (internal)
    private void syncTripBudgetToFirebaseInternal(int tripId, double budget, String userEmail, OnBudgetOperationListener listener) {
        try {
            // First get the trip's Firebase ID
            executor.execute(() -> {
                try {
                    TripRepository tripRepo = TripRepository.getInstance(context);
                    com.example.mobiledegreefinalproject.database.Trip trip = tripRepo.getTripByIdSync(tripId);
                    
                    if (trip == null || trip.getFirebaseId() == null || trip.getFirebaseId().isEmpty()) {
                        Log.w(TAG, "Cannot sync budget - trip not found or not synced to Firebase");
                        if (listener != null) {
                            listener.onError("Trip not synced to Firebase");
                        }
                        return;
                    }
                    
                    Map<String, Object> budgetData = new HashMap<>();
                    budgetData.put("totalBudget", budget);
                    budgetData.put("tripId", tripId);
                    budgetData.put("createdAt", System.currentTimeMillis());
                    budgetData.put("updatedAt", System.currentTimeMillis());
                    
                    // Store under: Users/{uid}/Trips/{tripFirebaseId}/Budget/tripBudget
                    firestore.collection("users")
                        .document(userEmail)
                        .collection("trips")
                        .document(trip.getFirebaseId())
                        .collection("budget")
                        .document("tripBudget")
                        .set(budgetData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Trip budget synced to Firebase successfully");
                            if (listener != null) {
                                listener.onSuccess();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error syncing trip budget to Firebase", e);
                            if (listener != null) {
                                listener.onError("Failed to sync trip budget: " + e.getMessage());
                            }
                        });
                        
                } catch (Exception e) {
                    Log.e(TAG, "Exception getting trip for budget sync", e);
                    if (listener != null) {
                        listener.onError("Failed to get trip information: " + e.getMessage());
                    }
                }
            });
                
        } catch (Exception e) {
            Log.e(TAG, "Exception in trip budget Firebase sync", e);
            if (listener != null) {
                listener.onError("Sync setup failed: " + e.getMessage());
            }
        }
    }

    // Sync a single expense to Firebase (internal)
    private void syncExpenseToFirebaseInternal(Expense expense, String userEmail, OnBudgetOperationListener listener) {
        try {
            // First get the trip's Firebase ID
            executor.execute(() -> {
                try {
                    TripRepository tripRepo = TripRepository.getInstance(context);
                    com.example.mobiledegreefinalproject.database.Trip trip = tripRepo.getTripByIdSync(expense.getTripId());
                    
                    if (trip == null || trip.getFirebaseId() == null || trip.getFirebaseId().isEmpty()) {
                        Log.w(TAG, "Cannot sync expense - trip not found or not synced to Firebase");
                        if (listener != null) {
                            listener.onError("Trip not synced to Firebase");
                        }
                        return;
                    }
                    
                    Map<String, Object> expenseData = new HashMap<>();
                    expenseData.put("title", expense.getTitle());
                    expenseData.put("amount", expense.getAmount());
                    expenseData.put("category", expense.getCategory().name());
                    expenseData.put("timestamp", expense.getTimestamp());
                    expenseData.put("note", expense.getNote() != null ? expense.getNote() : "");
                    expenseData.put("tripId", expense.getTripId());
                    expenseData.put("createdAt", System.currentTimeMillis());
                    expenseData.put("updatedAt", System.currentTimeMillis());
                    
                    String originalExpenseId = expense.getId();
                    final String expenseId = (originalExpenseId == null || originalExpenseId.isEmpty()) 
                        ? "expense_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000)
                        : originalExpenseId;
                    
                    // Store under: Users/{uid}/Trips/{tripFirebaseId}/Budget/{expenseId}
                    firestore.collection("users")
                        .document(userEmail)
                        .collection("trips")
                        .document(trip.getFirebaseId())
                        .collection("budget")
                        .document(expenseId)
                        .set(expenseData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Expense synced to Firebase successfully");
                            expense.setFirebaseId(expenseId);
                            expense.setSynced(true);
                            if (listener != null) {
                                listener.onSuccess();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error syncing expense to Firebase", e);
                            if (listener != null) {
                                listener.onError("Failed to sync expense: " + e.getMessage());
                            }
                        });
                        
                } catch (Exception e) {
                    Log.e(TAG, "Exception getting trip for expense sync", e);
                    if (listener != null) {
                        listener.onError("Failed to get trip information: " + e.getMessage());
                    }
                }
            });
                
        } catch (Exception e) {
            Log.e(TAG, "Exception in expense Firebase sync", e);
            if (listener != null) {
                listener.onError("Sync setup failed: " + e.getMessage());
            }
        }
    }

    // Delete budget records for a specific trip from Firebase
    public void deleteTripBudgetRecordsFromFirebase(int tripId, OnBudgetOperationListener listener) {
        if (!userManager.isLoggedIn()) {
            if (listener != null) {
                listener.onSuccess(); // No Firebase to clean up
            }
            return;
        }
        
        String userEmail = userManager.getUserEmail();
        if (userEmail == null || userEmail.trim().isEmpty()) {
            if (listener != null) {
                listener.onError("User email not available");
            }
            return;
        }
        
        Log.d(TAG, "Deleting budget records for trip ID: " + tripId);
        
        // First get the trip's Firebase ID
        executor.execute(() -> {
            try {
                TripRepository tripRepo = TripRepository.getInstance(context);
                com.example.mobiledegreefinalproject.database.Trip trip = tripRepo.getTripByIdSync(tripId);
                
                if (trip == null || trip.getFirebaseId() == null || trip.getFirebaseId().isEmpty()) {
                    Log.w(TAG, "Cannot delete budget records - trip not found or not synced to Firebase");
                    if (listener != null) {
                        listener.onSuccess(); // No Firebase to clean up
                    }
                    return;
                }
                
                // Delete entire budget collection for this trip
                firestore.collection("users")
                    .document(userEmail)
                    .collection("trips")
                    .document(trip.getFirebaseId())
                    .collection("budget")
                    .get()
            .addOnSuccessListener(querySnapshot -> {
                if (querySnapshot.isEmpty()) {
                    Log.d(TAG, "No budget records found for trip " + tripId);
                    if (listener != null) {
                        listener.onSuccess();
                    }
                    return;
                }
                
                Log.d(TAG, "Found " + querySnapshot.size() + " budget records to delete");
                
                // Delete each document
                int totalDocs = querySnapshot.size();
                final int[] deletedCount = {0};
                final boolean[] hasError = {false};
                
                for (DocumentSnapshot document : querySnapshot) {
                    document.getReference().delete()
                        .addOnSuccessListener(aVoid -> {
                            synchronized (deletedCount) {
                                deletedCount[0]++;
                                Log.d(TAG, "Deleted budget record: " + document.getId());
                                
                                if (deletedCount[0] >= totalDocs) {
                                    // All deletions completed
                                    if (listener != null) {
                                        if (hasError[0]) {
                                            listener.onError("Some budget records could not be deleted");
                                        } else {
                                            listener.onSuccess();
                                        }
                                    }
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
                                    if (listener != null) {
                                        listener.onError("Some budget records could not be deleted");
                                    }
                                }
                            }
                        });
                }
            })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching budget records for deletion", e);
                        if (listener != null) {
                            listener.onError("Failed to fetch budget records for deletion: " + e.getMessage());
                        }
                    });
                    
            } catch (Exception e) {
                Log.e(TAG, "Exception getting trip for budget deletion", e);
                if (listener != null) {
                    listener.onError("Failed to get trip information: " + e.getMessage());
                }
            }
        });
    }
    
    // Fetch budget data from Firebase for all user trips
    public void fetchBudgetDataFromFirebase(OnBudgetFetchListener listener) {
        if (!userManager.isLoggedIn()) {
            if (listener != null) {
                listener.onError("User not logged in");
            }
            return;
        }
        
        String userEmail = userManager.getUserEmail();
        if (userEmail == null || userEmail.trim().isEmpty()) {
            if (listener != null) {
                listener.onError("User email not available");
            }
            return;
        }
        
        Log.d(TAG, "Fetching budget data from Firebase for user: " + userEmail);
        
        executor.execute(() -> {
            try {
                // Get all user trips first
                TripRepository tripRepo = TripRepository.getInstance(context);
                List<com.example.mobiledegreefinalproject.database.Trip> allTrips = tripRepo.getAllTripsSync();
                
                BudgetData fetchedData = new BudgetData();
                final int[] tripsProcessed = {0};
                final int totalTrips = allTrips.size();
                
                if (totalTrips == 0) {
                    Log.d(TAG, "No trips found, budget fetch complete");
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (listener != null) {
                            listener.onSuccess();
                        }
                    });
                    return;
                }
                
                // Fetch budget data for each trip
                for (com.example.mobiledegreefinalproject.database.Trip trip : allTrips) {
                    if (trip.getFirebaseId() == null || trip.getFirebaseId().isEmpty()) {
                        synchronized (tripsProcessed) {
                            tripsProcessed[0]++;
                            if (tripsProcessed[0] >= totalTrips) {
                                // All trips processed, save data and notify completion
                                saveFetchedBudgetData(fetchedData, listener);
                            }
                        }
                        continue;
                    }
                    
                    // Fetch budget data for this trip
                    firestore.collection("users")
                        .document(userEmail)
                        .collection("trips")
                        .document(trip.getFirebaseId())
                        .collection("budget")
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            try {
                                List<Expense> tripExpenses = new ArrayList<>();
                                double tripBudget = 2000.0; // Default
                                
                                for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshot) {
                                    if (doc.getId().equals("tripBudget")) {
                                        // This is the trip budget document
                                        Double budget = doc.getDouble("totalBudget");
                                        if (budget != null) {
                                            tripBudget = budget;
                                        }
                                    } else {
                                        // This is an expense document
                                        Expense expense = parseExpenseFromFirebase(doc);
                                        if (expense != null) {
                                            expense.setTripId(trip.getId());
                                            expense.setFirebaseId(doc.getId());
                                            expense.setSynced(true);
                                            tripExpenses.add(expense);
                                        }
                                    }
                                }
                                
                                synchronized (fetchedData) {
                                    if (tripBudget != 2000.0) { // Only store non-default budgets
                                        fetchedData.tripBudgets.put(trip.getId(), tripBudget);
                                    }
                                    if (!tripExpenses.isEmpty()) {
                                        fetchedData.tripExpenses.put(trip.getId(), tripExpenses);
                                    }
                                }
                                
                                Log.d(TAG, "Fetched budget data for trip " + trip.getTitle() + 
                                    ": budget=" + tripBudget + ", expenses=" + tripExpenses.size());
                                
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing budget data for trip " + trip.getTitle(), e);
                            }
                            
                            synchronized (tripsProcessed) {
                                tripsProcessed[0]++;
                                if (tripsProcessed[0] >= totalTrips) {
                                    // All trips processed, save data and notify completion
                                    saveFetchedBudgetData(fetchedData, listener);
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error fetching budget data for trip " + trip.getTitle(), e);
                            
                            synchronized (tripsProcessed) {
                                tripsProcessed[0]++;
                                if (tripsProcessed[0] >= totalTrips) {
                                    // All trips processed, save data and notify completion
                                    saveFetchedBudgetData(fetchedData, listener);
                                }
                            }
                        });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching budget data from Firebase", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (listener != null) {
                        listener.onError("Failed to fetch budget data: " + e.getMessage());
                    }
                });
            }
        });
    }
    
    private void saveFetchedBudgetData(BudgetData fetchedData, OnBudgetFetchListener listener) {
        try {
            // Save the fetched data to local storage
            BudgetData existingData = loadBudgetDataLocally();
            
            // Merge Firebase data with existing local data (Firebase takes precedence)
            existingData.tripBudgets.putAll(fetchedData.tripBudgets);
            existingData.tripExpenses.putAll(fetchedData.tripExpenses);
            
            // Save merged data
            saveBudgetDataLocally(existingData.tripBudgets, existingData.tripExpenses, 
                existingData.selectedTripId, existingData.totalBudget);
            
            Log.d(TAG, "Successfully saved fetched budget data: " + 
                fetchedData.tripBudgets.size() + " trip budgets, " + 
                fetchedData.tripExpenses.size() + " trip expenses");
            
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (listener != null) {
                    listener.onSuccess();
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving fetched budget data", e);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (listener != null) {
                    listener.onError("Failed to save fetched budget data: " + e.getMessage());
                }
            });
        }
    }
    
    private Expense parseExpenseFromFirebase(com.google.firebase.firestore.QueryDocumentSnapshot doc) {
        try {
            String title = doc.getString("title");
            Double amount = doc.getDouble("amount");
            String categoryString = doc.getString("category");
            Long timestamp = doc.getLong("timestamp");
            String note = doc.getString("note");
            
            if (title == null || amount == null || categoryString == null || timestamp == null) {
                Log.w(TAG, "Missing required fields in expense document: " + doc.getId());
                return null;
            }
            
            Expense.Category category;
            try {
                category = Expense.Category.valueOf(categoryString);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid category in expense document: " + categoryString);
                category = Expense.Category.OTHER;
            }
            
            Expense expense = new Expense(title, amount, category, note);
            expense.setTimestamp(timestamp);
            expense.setId(doc.getId());
            
            return expense;
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing expense from Firebase document", e);
            return null;
        }
    }

    // Data class for holding budget data
    public static class BudgetData {
        public Map<Integer, Double> tripBudgets = new HashMap<>();
        public Map<Integer, List<Expense>> tripExpenses = new HashMap<>();
        public int selectedTripId = -1;
        public double totalBudget = 2000.0;
    }
} 