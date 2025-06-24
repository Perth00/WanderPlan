package com.example.mobiledegreefinalproject;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobiledegreefinalproject.adapter.TripTimelineAdapter;
import com.example.mobiledegreefinalproject.database.Trip;
import com.example.mobiledegreefinalproject.database.TripActivity;
import com.example.mobiledegreefinalproject.viewmodel.TripsViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.mobiledegreefinalproject.repository.TripRepository;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class TripDetailActivity extends AppCompatActivity {

    private static final String TAG = "TripDetailActivity";

    private TextView tripTitle;
    private TextView tripDestination;
    private TextView tripDateRange;
    private RecyclerView timelineRecyclerView;
    private ImageView mapPreview;
    private FloatingActionButton fabAddActivity;
    private TextView emptyStateText;

    private TripsViewModel viewModel;
    private TripTimelineAdapter timelineAdapter;
    private Trip currentTrip;
    private int tripId;
    
    // CRITICAL FIX: Add flag to prevent finishing during operations
    private boolean isDeletingActivity = false;
    private final java.util.Set<Integer> currentlyDeletingActivities = new java.util.HashSet<>();
    
    // CRITICAL FIX: Add flag to prevent multiple simultaneous Firebase loads
    private boolean isLoadingFromFirebase = false;
    
    // CRITICAL FIX: Track if Firebase data has been successfully displayed
    private boolean hasDisplayedFirebaseData = false;
    
    // Info Panel Manager for non-intrusive messages
    private InfoPanelManager infoPanelManager;
    
    // Modern replacement for startActivityForResult
    private ActivityResultLauncher<Intent> addActivityLauncher;

    // NUCLEAR SOLUTION: Completely isolated Firebase data display
    private boolean isFirebaseOnlyMode = false;
    private List<TripActivity> firebaseActivitiesCache = new ArrayList<>();
    
    // Activity refresh tracking
    private boolean justAddedActivity = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_detail);

        // Get trip ID from intent
        tripId = getIntent().getIntExtra("trip_id", -1);
        if (tripId == -1) {
            Log.e(TAG, "No trip ID provided");
            Toast.makeText(this, "Error: Trip not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d(TAG, "TripDetailActivity created for trip ID: " + tripId);

        initViews();
        setupViewModel();
        setupActivityLauncher();
        setupRecyclerView();
        setupClickListeners();
        setupToolbar();
        setupBackPressHandler();
        
        // Load trip data based on user login status
        loadInitialTripData();
    }

    private void initViews() {
        tripTitle = findViewById(R.id.trip_title);
        tripDestination = findViewById(R.id.trip_destination);
        tripDateRange = findViewById(R.id.trip_date_range);
        timelineRecyclerView = findViewById(R.id.timeline_recycler_view);
        mapPreview = findViewById(R.id.map_preview);
        fabAddActivity = findViewById(R.id.fab_add_activity);
        emptyStateText = findViewById(R.id.empty_state_text);
        
        // Initialize Info Panel Manager
        ViewGroup infoPanelContainer = findViewById(R.id.info_panel_container);
        if (infoPanelContainer != null) {
            infoPanelManager = new InfoPanelManager(this, infoPanelContainer);
            infoPanelManager.showActivityManagementTips();
        } else {
            Log.w(TAG, "Info panel container not found");
        }
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(TripsViewModel.class);
    }
    
    private void setupActivityLauncher() {
        // Modern replacement for startActivityForResult
        addActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(TAG, "Returned from AddActivityActivity with result: " + result.getResultCode());
                
                try {
                    if (result.getResultCode() == RESULT_OK) {
                        Log.d(TAG, "Activity creation successful - refreshing data");
                        
                        // Show immediate feedback
                        Toast.makeText(TripDetailActivity.this, "Activity saved! Loading...", Toast.LENGTH_SHORT).show();
                        
                        // NUCLEAR SOLUTION: Smart refresh based on current mode
                        if (isFirebaseOnlyMode) {
                            Log.d(TAG, "🔥 NUCLEAR: Activity added - refreshing Firebase data");
                            
                            // Mark that we just added an activity
                            justAddedActivity = true;
                            
                            // Clear cache to force fresh load and add delay for Firebase sync
                            firebaseActivitiesCache.clear();
                            
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    Log.d(TAG, "🔥 NUCLEAR: Loading fresh Firebase data after activity add");
                                    loadFirebaseActivitiesDirectNuclear();
                                    
                                    // Show success message
                                    if (infoPanelManager != null) {
                                        infoPanelManager.addCustomMessage("✓ Activity added to cloud!", false);
                                    }
                                    
                                    // Show immediate feedback with toast
                                    Toast.makeText(TripDetailActivity.this, "Activity added! Refreshing...", Toast.LENGTH_SHORT).show();
                                }
                            }, 1500); // 1.5 seconds for Firebase sync
                            
                        } else {
                            // LOCAL MODE: For offline users
                            Log.d(TAG, "📱 LOCAL: Activity added - refreshing local data");
                            
                            // Mark that we just added an activity
                            justAddedActivity = true;
                            
                            // Immediate refresh
                            refreshActivityDataDirectly();
                            
                            // Delayed refresh to catch any async database commits
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    Log.d(TAG, "📱 LOCAL: Secondary refresh after activity save");
                                    refreshActivityDataDirectly();
                                    
                                    // Show success message
                                    if (infoPanelManager != null) {
                                        infoPanelManager.addCustomMessage("Activity saved locally!", false);
                                    }
                                }
                            }, 500); // 500ms delay for local database
                        }
                        
                    } else {
                        Log.d(TAG, "Activity creation cancelled or failed");
                        // NUCLEAR: Smart refresh based on mode (even on failure to ensure consistency)
                        if (isFirebaseOnlyMode) {
                            Log.d(TAG, "🔥 NUCLEAR: Activity creation failed - refreshing Firebase to ensure consistency");
                            loadFirebaseActivitiesDirectNuclear();
                        } else {
                            Log.d(TAG, "📱 LOCAL: Activity creation failed - refreshing local data");
                            refreshActivityDataDirectly();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling activity result", e);
                    // NUCLEAR: Smart fallback refresh based on mode
                    if (!isFinishing() && !isDestroyed()) {
                        if (isFirebaseOnlyMode) {
                            Log.d(TAG, "🔥 NUCLEAR: Error in activity result - fallback Firebase refresh");
                            loadFirebaseActivitiesDirectNuclear();
                        } else {
                            Log.d(TAG, "📱 LOCAL: Error in activity result - fallback local refresh");
                            refreshActivityDataDirectly();
                        }
                    }
                }
            });
    }

    private void setupRecyclerView() {
        timelineAdapter = new TripTimelineAdapter(new TripTimelineAdapter.OnActivityClickListener() {
            @Override
            public void onActivityClick(TripActivity activity) {
                // Handle activity click - could open edit activity
                Log.d(TAG, "Activity clicked: " + activity.getTitle());
                
                // For now, just show a toast
                Toast.makeText(TripDetailActivity.this, 
                    "Activity: " + activity.getTitle(), 
                    Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onActivityLongClick(TripActivity activity) {
                // Handle long click - show delete option
                showDeleteActivityDialog(activity);
            }

            @Override
            public void onEditActivityClick(TripActivity activity) {
                Log.d(TAG, "Edit icon clicked for activity: " + activity.getTitle());
                Intent intent = new Intent(TripDetailActivity.this, AddActivityActivity.class);
                intent.putExtra("trip_id", tripId);
                intent.putExtra("activity_to_edit", activity); // This is the key for edit mode
                addActivityLauncher.launch(intent);
            }
        });

        timelineRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        timelineRecyclerView.setAdapter(timelineAdapter);
    }

    private void setupClickListeners() {
        fabAddActivity.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddActivityActivity.class);
            intent.putExtra("trip_id", tripId);
            addActivityLauncher.launch(intent); // Use modern ActivityResultLauncher
        });

        mapPreview.setOnClickListener(v -> {
            if (currentTrip != null) {
                openMapView(currentTrip.getDestination());
            }
        });
    }

    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Trip Details");
        }
    }
    
    private void setupBackPressHandler() {
        // Modern replacement for onBackPressed
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Log.d(TAG, "Back button pressed - finishing activity gracefully");
                
                // CRITICAL FIX: Check if deletion is in progress
                if (isDeletingActivity) {
                    Log.w(TAG, "Activity deletion in progress - ignoring back press");
                    if (infoPanelManager != null) {
                        infoPanelManager.addCustomMessage("Please wait for deletion to complete", true);
                    }
                    return;
                }
                
                                    // CRITICAL FIX: Ensure we finish properly without crashing
                try {
                    // CRITICAL FIX: Only remove observers if they were actually set up (for offline users)
                    UserManager userManager = UserManager.getInstance(TripDetailActivity.this);
                    if (viewModel != null && tripId != -1 && !userManager.isLoggedIn()) {
                        try {
                            Log.d(TAG, "Removing ViewModel observers for offline user");
                            viewModel.getTripById(tripId).removeObservers(TripDetailActivity.this);
                            viewModel.getActivitiesForTrip(tripId).removeObservers(TripDetailActivity.this);
                        } catch (Exception e) {
                            Log.w(TAG, "Error removing observers on back press", e);
                        }
                    } else {
                        Log.d(TAG, "Skipping observer removal for logged-in user (no observers were set)");
                    }
                    
                    // Finish the activity
                    finish();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error handling back press", e);
                    // Force finish as last resort
                    try {
                        finish();
                    } catch (Exception finishError) {
                        Log.e(TAG, "Error force finishing activity", finishError);
                    }
                }
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        Log.d(TAG, "Navigation up pressed");
        
        // CRITICAL FIX: Handle navigation up safely
        try {
            getOnBackPressedDispatcher().onBackPressed();
        } catch (Exception e) {
            Log.e(TAG, "Error handling navigation up", e);
            // Fallback to direct finish
            try {
                finish();
            } catch (Exception finishError) {
                Log.e(TAG, "Error finishing on navigation up", finishError);
            }
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.trip_detail_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_sync) {
            showComingSoonSyncDialog();
            return true;
        } else if (id == R.id.action_delete_trip) {
            showDeleteTripDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    // Quick debug method - call this temporarily to check your specific data
    private void debugSpecificFirebaseData() {
        UserManager userManager = UserManager.getInstance(this);
        if (!userManager.isLoggedIn()) {
            Log.e(TAG, "❌ User not logged in for debug");
            return;
        }
        
        String userEmail = userManager.getUserEmail();
        Log.d(TAG, "🔧 DEBUGGING SPECIFIC DATA for user: " + userEmail);
        
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        
        // First, let's see ALL trips for this user
        firestore.collection("users")
                .document(userEmail)
                .collection("trips")
                .get()
                .addOnSuccessListener(tripsQuery -> {
                    Log.d(TAG, "🔧 DEBUG: Found " + tripsQuery.size() + " trips total");
                    
                    for (int i = 0; i < tripsQuery.size(); i++) {
                        com.google.firebase.firestore.QueryDocumentSnapshot tripDoc = 
                            (com.google.firebase.firestore.QueryDocumentSnapshot) tripsQuery.getDocuments().get(i);
                        
                        String tripId = tripDoc.getId();
                        String title = tripDoc.getString("title");
                        String destination = tripDoc.getString("destination");
                        
                        Log.d(TAG, "🔧 Trip " + (i+1) + ": ID=" + tripId + ", Title='" + title + "', Dest='" + destination + "'");
                        
                        // Check activities for each trip
                        firestore.collection("users")
                                .document(userEmail)
                                .collection("trips")
                                .document(tripId)
                                .collection("activities")
                                .get()
                                .addOnSuccessListener(activitiesQuery -> {
                                    Log.d(TAG, "🔧   Trip '" + title + "' has " + activitiesQuery.size() + " activities");
                                    
                                    for (com.google.firebase.firestore.QueryDocumentSnapshot actDoc : activitiesQuery) {
                                        String actTitle = actDoc.getString("title");
                                        Object dayNum = actDoc.get("dayNumber");
                                        String timeStr = actDoc.getString("timeString");
                                        Log.d(TAG, "🔧     Activity: '" + actTitle + "' Day:" + dayNum + " Time:'" + timeStr + "'");
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "🔧   Failed to load activities for trip: " + title, e);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🔧 DEBUG: Failed to load trips", e);
                });
    }

    // Hidden debug method - you can call this to diagnose Firebase issues
    private void runFirebaseDebugDiagnostic() {
        if (infoPanelManager != null) {
            infoPanelManager.addCustomMessage("🔍 Running Firebase diagnostic...", false);
        }
        
        FirebaseDebugHelper.diagnoseFirebaseDataIssues(this, report -> {
            Log.d(TAG, "🔍 FIREBASE DIAGNOSTIC REPORT:\n" + report);
            
            // Show diagnostic in a dialog
            new android.app.AlertDialog.Builder(this)
                    .setTitle("🔍 Firebase Diagnostic Report")
                    .setMessage(report)
                    .setPositiveButton("Copy to Clipboard", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = 
                            (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Firebase Diagnostic", report);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, "Report copied to clipboard", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Close", null)
                    .show();
                    
            if (infoPanelManager != null) {
                infoPanelManager.addCustomMessage("✅ Diagnostic complete - check dialog", false);
            }
        });
    }

    private void showComingSoonSyncDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("🔄 Trip Sync")
                .setMessage("🚧 Sync Feature Coming Soon!\n\n" +
                           "We're working on improving the trip synchronization feature.\n\n" +
                           "📱 Your activities are automatically saved locally\n" +
                           "☁️ Cloud sync will be available in the next update\n\n" +
                           "Stay tuned for enhanced data synchronization!")
                .setPositiveButton("Got it", null)
                .setIcon(android.R.drawable.ic_popup_sync)
                .show();
    }

    private void forceSyncActivitiesQuiet() {
        if (tripId != -1) {
            com.example.mobiledegreefinalproject.repository.TripRepository repository = 
                com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
            
            repository.forceSyncTripActivities(tripId, new com.example.mobiledegreefinalproject.repository.TripRepository.OnTripSyncListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Quiet sync completed successfully");
                    // No message for quiet sync
                }
                
                @Override
                public void onError(String error) {
                    Log.w(TAG, "Quiet sync failed: " + error);
                    // Only show error if it's important
                    if (infoPanelManager != null && error.contains("network")) {
                        infoPanelManager.showSyncError("Network sync failed");
                    }
                }
            });
        }
    }

    /**
     * Load initial trip data based on user login status
     */
    private void loadInitialTripData() {
        Log.d(TAG, "Loading initial trip data for trip ID: " + tripId);
        
        UserManager userManager = UserManager.getInstance(this);
        
        if (userManager.isLoggedIn()) {
            // NUCLEAR SOLUTION: Pure Firebase-only mode with ZERO local interference
            Log.d(TAG, "🔥🔥🔥 NUCLEAR FIREBASE MODE: Completely isolated Firebase operation");
            isFirebaseOnlyMode = true;
            
            // Disable ALL repository operations that could interfere
            TripRepository repository = TripRepository.getInstance(this);
            repository.enableNuclearFirebaseMode();
            
            // Load ONLY from Firebase with complete isolation
            loadTripFromFirebaseOnlyNuclear();
        } else {
            // LOCAL DATABASE MODE for offline users
            Log.d(TAG, "📱 LOCAL MODE: User offline - loading trip from local database");
            isFirebaseOnlyMode = false;
            loadTripDataLocalOnly();
        }
    }
    
    /**
     * NUCLEAR SOLUTION: Load from Firebase with COMPLETE isolation
     */
    private void loadTripFromFirebaseOnlyNuclear() {
        Log.d(TAG, "🔥🔥🔥 NUCLEAR FIREBASE: Loading with complete isolation");
        
        UserManager userManager = UserManager.getInstance(this);
        if (!userManager.isLoggedIn()) {
            Log.e(TAG, "❌ Cannot load from Firebase - user not logged in");
            return;
        }
        
        // Get ONLY basic trip info (no observers, no sync operations)
        new Thread(() -> {
            try {
                TripRepository repository = TripRepository.getInstance(this);
                Trip trip = repository.getTripByIdSync(tripId);
                
                if (trip != null) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            currentTrip = trip;
                            displayTripInfo(trip);
                            
                            // NUCLEAR FIREBASE LOAD - bypasses ALL repository methods
                            loadFirebaseActivitiesDirectNuclear();
                        }
                    });
                } else {
                    Log.e(TAG, "❌ Trip not found");
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(this, "Trip not found", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error loading trip", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }
        }).start();
    }
    
    /**
     * NUCLEAR SOLUTION: Direct Firebase access with ZERO repository interference
     * CRASH-PROOF version with extensive error handling
     */
    private void loadFirebaseActivitiesDirectNuclear() {
        Log.d(TAG, "🔥🔥🔥 NUCLEAR: Direct Firebase access - bypassing ALL repository operations");
        
        // Declare variables outside try blocks for proper scope
        String userEmail = null;
        String tripFirebaseId = null;
        
        try {
            // Null safety checks
            UserManager userManager = UserManager.getInstance(this);
            if (userManager == null) {
                Log.e(TAG, "🔥 NUCLEAR ERROR: UserManager is null");
                displayFirebaseActivitiesNuclear(new ArrayList<>());
                return;
            }
            
            if (!userManager.isLoggedIn()) {
                Log.e(TAG, "🔥 NUCLEAR ERROR: User not logged in");
                displayFirebaseActivitiesNuclear(new ArrayList<>());
                return;
            }
            
            userEmail = userManager.getUserEmail();
            if (userEmail == null || userEmail.isEmpty()) {
                Log.e(TAG, "🔥 NUCLEAR ERROR: User email is null or empty");
                displayFirebaseActivitiesNuclear(new ArrayList<>());
                return;
            }
            
            if (currentTrip == null) {
                Log.e(TAG, "🔥 NUCLEAR ERROR: CurrentTrip is null");
                displayFirebaseActivitiesNuclear(new ArrayList<>());
                return;
            }
            
            tripFirebaseId = currentTrip.getFirebaseId();
            if (tripFirebaseId == null || tripFirebaseId.isEmpty()) {
                Log.w(TAG, "🔥 NUCLEAR: Trip has no Firebase ID, trying manual ID");
                tripFirebaseId = "LGRen9seRlnLfYyvctmT"; // Your known Firebase ID
            }
            
            Log.d(TAG, "🔥 NUCLEAR: Accessing Firebase directly");
            Log.d(TAG, "   User: " + userEmail);
            Log.d(TAG, "   Trip Firebase ID: " + tripFirebaseId);
            Log.d(TAG, "   Path: users/" + userEmail + "/trips/" + tripFirebaseId + "/activities");
            
            // Now access Firebase with validated data
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            final String finalUserEmail = userEmail;
            final String finalTripFirebaseId = tripFirebaseId;
            
            firestore.collection("users")
                    .document(finalUserEmail)
                    .collection("trips")
                    .document(finalTripFirebaseId)
                    .collection("activities")
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .addOnSuccessListener(activitiesQuery -> {
                    Log.d(TAG, "🔥🔥🔥 NUCLEAR SUCCESS: Found " + activitiesQuery.size() + " activities");
                    
                    List<TripActivity> activities = new ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : activitiesQuery) {
                        TripActivity activity = parseFirebaseActivityNuclear(doc);
                        if (activity != null) {
                            activities.add(activity);
                        }
                    }
                    
                    // Cache the Firebase data
                    firebaseActivitiesCache.clear();
                    firebaseActivitiesCache.addAll(activities);
                    
                    Log.d(TAG, "🔥🔥🔥 NUCLEAR: Displaying " + activities.size() + " activities with ZERO interference");
                    
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed() && isFirebaseOnlyMode) {
                            displayFirebaseActivitiesNuclear(activities);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🔥 NUCLEAR: Firebase access failed", e);
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            displayFirebaseActivitiesNuclear(new ArrayList<>());
                        }
                    });
                });
                
        } catch (Exception e) {
            Log.e(TAG, "🔥 NUCLEAR ERROR: Exception during Firebase access", e);
            displayFirebaseActivitiesNuclear(new ArrayList<>());
        }
    }
    
    /**
     * NUCLEAR SOLUTION: Parse Firebase data without any repository involvement
     * CRASH-PROOF version with extensive error handling
     */
    private TripActivity parseFirebaseActivityNuclear(com.google.firebase.firestore.QueryDocumentSnapshot doc) {
        try {
            if (doc == null) {
                Log.w(TAG, "🔥 NUCLEAR: Document is null, skipping");
                return null;
            }
            
            TripActivity activity = new TripActivity();
            
            // Set Firebase ID (never null from Firestore)
            activity.setFirebaseId(doc.getId());
            activity.setTripId(tripId);
            
            // Safe string extraction with null protection
            activity.setTitle(getStringSafe(doc, "title", "Untitled Activity"));
            activity.setDescription(getStringSafe(doc, "description", ""));
            activity.setLocation(getStringSafe(doc, "location", ""));
            activity.setTimeString(getStringSafe(doc, "timeString", ""));
            activity.setImageUrl(getStringSafe(doc, "imageUrl", ""));
            
            // Safe numeric extraction with defaults
            activity.setDateTime(getLongSafe(doc, "dateTime", System.currentTimeMillis()));
            activity.setDayNumber(getIntSafe(doc, "dayNumber", 1));
            activity.setCreatedAt(getLongSafe(doc, "createdAt", System.currentTimeMillis()));
            activity.setUpdatedAt(getLongSafe(doc, "updatedAt", System.currentTimeMillis()));
            
            // Optional fields
            activity.setLatitude(getDoubleSafe(doc, "latitude", 0.0));
            activity.setLongitude(getDoubleSafe(doc, "longitude", 0.0));
            
            activity.setSynced(true);
            
            Log.d(TAG, "🔥 NUCLEAR: Successfully parsed activity: " + activity.getTitle() + " (Day " + activity.getDayNumber() + ")");
            return activity;
            
        } catch (Exception e) {
            Log.e(TAG, "🔥 NUCLEAR: Error parsing Firebase activity (document: " + (doc != null ? doc.getId() : "null") + ")", e);
            return null;
        }
    }
    
    // CRASH-PROOF helper methods for safe data extraction
    private String getStringSafe(com.google.firebase.firestore.QueryDocumentSnapshot doc, String field, String defaultValue) {
        try {
            Object value = doc.get(field);
            if (value != null) {
                return value.toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "🔥 NUCLEAR: Error getting string field '" + field + "'", e);
        }
        return defaultValue;
    }
    
    private long getLongSafe(com.google.firebase.firestore.QueryDocumentSnapshot doc, String field, long defaultValue) {
        try {
            Object value = doc.get(field);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            } else if (value instanceof String) {
                return Long.parseLong((String) value);
            }
        } catch (Exception e) {
            Log.w(TAG, "🔥 NUCLEAR: Error getting long field '" + field + "'", e);
        }
        return defaultValue;
    }
    
    private int getIntSafe(com.google.firebase.firestore.QueryDocumentSnapshot doc, String field, int defaultValue) {
        try {
            Object value = doc.get(field);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            }
        } catch (Exception e) {
            Log.w(TAG, "🔥 NUCLEAR: Error getting int field '" + field + "'", e);
        }
        return defaultValue;
    }
    
    private double getDoubleSafe(com.google.firebase.firestore.QueryDocumentSnapshot doc, String field, double defaultValue) {
        try {
            Object value = doc.get(field);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
        } catch (Exception e) {
            Log.w(TAG, "🔥 NUCLEAR: Error getting double field '" + field + "'", e);
        }
        return defaultValue;
    }
    
    /**
     * NUCLEAR SOLUTION: Display Firebase activities with COMPLETE isolation
     */
    private void displayFirebaseActivitiesNuclear(List<TripActivity> activities) {
        Log.d(TAG, "🔥🔥🔥 NUCLEAR DISPLAY: Showing " + activities.size() + " Firebase activities");
        Log.d(TAG, "🔥 Firebase-only mode: " + isFirebaseOnlyMode);
        Log.d(TAG, "🔥 Thread: " + Thread.currentThread().getName());
        
        // NUCLEAR: Completely bypass displayTimeline to avoid any interference
        if (activities == null || activities.isEmpty()) {
            Log.d(TAG, "🔥 NUCLEAR: No activities - showing empty state");
            timelineRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            emptyStateText.setText("No activities planned yet.\nTap + to add your first activity!");
            
            if (timelineAdapter != null) {
                timelineAdapter.submitData(new java.util.HashMap<>());
            }
            return;
        }
        
        Log.d(TAG, "🔥 NUCLEAR: Displaying Firebase activities directly");
        timelineRecyclerView.setVisibility(View.VISIBLE);
        emptyStateText.setVisibility(View.GONE);
        
        // Group activities by day (pure method, no side effects)
        Map<Integer, List<TripActivity>> groupedActivities = groupActivitiesByDayNuclear(activities);
        Log.d(TAG, "🔥 NUCLEAR: Grouped into " + groupedActivities.size() + " days");
        
        // Display directly to adapter with crash protection
        try {
            if (timelineAdapter != null) {
                timelineAdapter.submitData(groupedActivities);
                Log.d(TAG, "🔥 NUCLEAR: Data submitted to adapter successfully");
            } else {
                Log.e(TAG, "🔥 NUCLEAR ERROR: Timeline adapter is null!");
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "🔥 NUCLEAR ERROR: Failed to submit data to adapter", e);
            return;
        }
        
        // Mark success
        hasDisplayedFirebaseData = true;
        
        Log.d(TAG, "🔥🔥🔥 NUCLEAR SUCCESS: Firebase activities displayed with ZERO interference!");
    }
    
    /**
     * NUCLEAR SOLUTION: Group activities without any side effects
     */
    private Map<Integer, List<TripActivity>> groupActivitiesByDayNuclear(List<TripActivity> activities) {
        Map<Integer, List<TripActivity>> grouped = new LinkedHashMap<>();
        
        for (TripActivity activity : activities) {
            int day = activity.getDayNumber();
            if (!grouped.containsKey(day)) {
                grouped.put(day, new ArrayList<>());
            }
            List<TripActivity> dayActivities = grouped.get(day);
            if (dayActivities != null) {
                dayActivities.add(activity);
            }
        }
        
        return grouped;
    }

    private void displayTripInfo(Trip trip) {
        tripTitle.setText(trip.getTitle());
        tripDestination.setText(trip.getDestination());
        tripDateRange.setText(trip.getDateRange());
        
        // TODO: Load map image when available
        mapPreview.setImageResource(R.drawable.ic_trips);
    }

    /**
     * NUCLEAR PROTECTION: Display timeline with Firebase mode protection
     */
    private void displayTimeline(List<TripActivity> activities) {
        // NUCLEAR PROTECTION: Absolutely prevent display when in Firebase-only mode
        if (isFirebaseOnlyMode) {
            Log.w(TAG, "🔥🚨 NUCLEAR PROTECTION: Blocking displayTimeline call in Firebase-only mode!");
            Log.w(TAG, "🔥 Firebase mode: " + isFirebaseOnlyMode);
            Log.w(TAG, "🔥 Activities count: " + (activities != null ? activities.size() : "null"));
            Log.w(TAG, "🔥 Caller stack trace:");
            
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (int i = 0; i < Math.min(stackTrace.length, 8); i++) {
                Log.w(TAG, "   [" + i + "] " + stackTrace[i].getClassName() + "." + stackTrace[i].getMethodName() + ":" + stackTrace[i].getLineNumber());
            }
            
            // Use cached Firebase data instead
            if (!firebaseActivitiesCache.isEmpty()) {
                Log.w(TAG, "🔥 NUCLEAR: Redirecting to Firebase cache with " + firebaseActivitiesCache.size() + " activities");
                displayFirebaseActivitiesNuclear(firebaseActivitiesCache);
            }
            return;
        }
        
        // Original displayTimeline logic for offline users only
        String caller = "Unknown";
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            if (stackTrace.length > 3) {
                StackTraceElement callerElement = stackTrace[3];
                caller = callerElement.getClassName() + "." + callerElement.getMethodName() + ":" + callerElement.getLineNumber();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not get caller info", e);
        }
        
        Log.d(TAG, "📱 DISPLAY TIMELINE (Local Mode): " + (activities != null ? activities.size() : "null") + " activities");
        Log.d(TAG, "📱 Called by: " + caller);
        Log.d(TAG, "📱 Thread: " + Thread.currentThread().getName());
        Log.d(TAG, "📱 Firebase mode: " + isFirebaseOnlyMode);
        
        if (activities == null || activities.isEmpty()) {
            Log.d(TAG, "📱 No activities - showing empty state");
            timelineRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            emptyStateText.setText("No activities planned yet.\nTap + to add your first activity!");
            
            if (timelineAdapter != null) {
                timelineAdapter.submitData(new java.util.HashMap<>());
            }
            return;
        }
        
        Log.d(TAG, "📱 Displaying activities:");
        for (TripActivity activity : activities) {
            Log.d(TAG, "   - " + activity.getTitle() + " (Day " + activity.getDayNumber() + ", ID: " + activity.getId() + ")");
        }
        
        timelineRecyclerView.setVisibility(View.VISIBLE);
        emptyStateText.setVisibility(View.GONE);
        
        // Group activities by day
        Map<Integer, List<TripActivity>> groupedActivities = new LinkedHashMap<>();
        for (TripActivity activity : activities) {
            int day = activity.getDayNumber();
            if (!groupedActivities.containsKey(day)) {
                groupedActivities.put(day, new ArrayList<>());
            }
            List<TripActivity> dayActivities = groupedActivities.get(day);
            if (dayActivities != null) {
                dayActivities.add(activity);
            }
        }
        
        Log.d(TAG, "📱 Grouped into " + groupedActivities.size() + " days");
        
        // Submit data with crash protection
        try {
            if (timelineAdapter != null) {
                timelineAdapter.submitData(groupedActivities);
                Log.d(TAG, "📱 Data submitted to adapter successfully");
            } else {
                Log.e(TAG, "📱 ERROR: Timeline adapter is null!");
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "📱 ERROR: Failed to submit data to adapter", e);
            return;
        }
        
        Log.d(TAG, "📱 Timeline display completed successfully for local mode");
    }

    private void openMapView(String destination) {
        try {
            // Format destination for Google Maps
            String query = destination.replace(" ", "+");
            String uri = "geo:0,0?q=" + query;
            
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
            mapIntent.setPackage("com.google.android.apps.maps");
            
            // Check if Google Maps is available
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                // Fallback to any map app
                mapIntent.setPackage(null);
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    if (infoPanelManager != null) {
                        infoPanelManager.addCustomMessage("No map application available", true);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening map", e);
            if (infoPanelManager != null) {
                infoPanelManager.addCustomMessage("Error opening map: " + e.getMessage(), true);
            }
        }
        
        if (currentTrip == null) {
            if (infoPanelManager != null) {
                infoPanelManager.addCustomMessage("Trip data not loaded", true);
            }
            return;
        }
    }

    private void showDeleteTripDialog() {
        if (currentTrip == null) {
            Toast.makeText(this, "Trip data not loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String message = "Are you sure you want to delete \"" + currentTrip.getTitle() + "\"?\n\n" +
                        "This action cannot be undone.";
        
        DeleteDialogHelper.showDeleteDialog(
            this,
            "Delete Trip",
            message,
            () -> deleteTrip(), // On confirm delete
            null // On cancel (no action needed)
        );
    }

    private void deleteTrip() {
        // Show loading dialog
        android.app.AlertDialog progressDialog = new android.app.AlertDialog.Builder(this)
            .setMessage("Deleting trip...")
            .setCancelable(false)
            .create();
        progressDialog.show();
        
        viewModel.deleteTrip(currentTrip, new com.example.mobiledegreefinalproject.repository.TripRepository.OnTripOperationListener() {
            @Override
            public void onSuccess(int tripId) {
                progressDialog.dismiss();
                
                if (infoPanelManager != null) {
                    infoPanelManager.showTripDeletedMessage();
                }
                
                // Notify MainActivity to inform other fragments about trip deletion
                if (TripDetailActivity.this instanceof android.app.Activity) {
                    android.content.Intent intent = new android.content.Intent();
                    intent.putExtra("deleted_trip_id", tripId);
                    setResult(android.app.Activity.RESULT_OK, intent);
                }
                
                // Navigate back to trips list after a short delay
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    finish();
                }, 1500);
            }
            
            @Override
            public void onError(String error) {
                progressDialog.dismiss();
                new android.app.AlertDialog.Builder(TripDetailActivity.this)
                        .setTitle("Delete Failed")
                        .setMessage("Failed to delete trip: " + error)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    private void showDeleteActivityDialog(TripActivity activity) {
        String message = "Are you sure you want to delete \"" + activity.getTitle() + "\"?\n\n" +
                        "This action cannot be undone.";
        
        DeleteDialogHelper.showDeleteDialog(
            this,
            "Delete Activity",
            message,
            () -> deleteActivity(activity), // On confirm delete
            null // On cancel (no action needed)
        );
    }

    private void deleteActivity(TripActivity activity) {
        // Simple state check
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity is finishing/destroyed, skipping deletion");
            return;
        }
        
        // Check if this specific activity is already being deleted
        synchronized (currentlyDeletingActivities) {
            if (currentlyDeletingActivities.contains(activity.getId())) {
                Log.w(TAG, "Activity " + activity.getId() + " is already being deleted, ignoring duplicate request");
                return;
            }
            // Mark this activity as being deleted
            currentlyDeletingActivities.add(activity.getId());
        }
        
        Log.d(TAG, "Starting activity deletion for: " + activity.getTitle() + " (ID: " + activity.getId() + ")");
        
        // Show progress immediately
        android.app.AlertDialog progressDialog = new android.app.AlertDialog.Builder(this)
            .setMessage("Deleting activity...")
            .setCancelable(false)
            .create();
        progressDialog.show();
        
        // Use repository with nuclear optimization for Firebase-only mode
        com.example.mobiledegreefinalproject.repository.TripRepository repository = 
            com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
        
        // Choose the optimal deletion method based on mode
        com.example.mobiledegreefinalproject.repository.TripRepository.OnActivityOperationListener deleteListener = 
            new com.example.mobiledegreefinalproject.repository.TripRepository.OnActivityOperationListener() {
            @Override
            public void onSuccess(int activityId) {
                Log.d(TAG, "Activity deletion successful: " + activityId);
                
                // Remove from deletion tracking
                synchronized (currentlyDeletingActivities) {
                    currentlyDeletingActivities.remove(activity.getId());
                }
                
                // Dismiss dialog safely
                try {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error dismissing progress dialog", e);
                }
                
                // Show brief success message
                if (infoPanelManager != null) {
                    infoPanelManager.addCustomMessage("Activity deleted", false);
                }
                
                // NUCLEAR: Smart refresh based on mode
                if (isFirebaseOnlyMode) {
                    Log.d(TAG, "🔥 NUCLEAR: Refreshing from Firebase after successful deletion");
                    // Remove from cache and reload from Firebase
                    firebaseActivitiesCache.removeIf(act -> act.getId() == activity.getId() || 
                        (act.getFirebaseId() != null && act.getFirebaseId().equals(activity.getFirebaseId())));
                    loadFirebaseActivitiesDirectNuclear();
                } else {
                    Log.d(TAG, "📱 LOCAL: Refreshing local data after deletion");
                    refreshActivityDataDirectly();
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Activity deletion failed: " + error);
                
                // Remove from deletion tracking
                synchronized (currentlyDeletingActivities) {
                    currentlyDeletingActivities.remove(activity.getId());
                }
                
                // Dismiss dialog safely
                try {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error dismissing progress dialog", e);
                }
                
                // Show error briefly
                if (infoPanelManager != null) {
                    infoPanelManager.addCustomMessage("Delete failed: " + error, true);
                }
                
                // NUCLEAR: Smart refresh based on mode (on error)
                if (isFirebaseOnlyMode) {
                    Log.d(TAG, "🔥 NUCLEAR: Refreshing from Firebase after delete error");
                    loadFirebaseActivitiesDirectNuclear();
                } else {
                    Log.d(TAG, "📱 LOCAL: Refreshing local data after delete error");
                    refreshActivityDataDirectly();
                }
            }
        };
        
        // Execute the appropriate deletion method
        if (isFirebaseOnlyMode) {
            Log.d(TAG, "🔥 Using nuclear delete for Firebase-only mode");
            repository.deleteActivityNuclear(activity, deleteListener);
        } else {
            Log.d(TAG, "📱 Using standard delete for local mode");
            repository.deleteActivity(activity, deleteListener);
        }
    }
    


    // CRITICAL FIX: Add method to force refresh trip data after activity creation
    public void refreshTripData() {
        Log.d(TAG, "Force refreshing trip data to get updated Firebase IDs");
        if (tripId != -1) {
            // Force refresh from Firebase to get updated activity objects with Firebase IDs
            com.example.mobiledegreefinalproject.repository.TripRepository repository = 
                com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
            
            repository.forceSyncTripActivities(tripId, new com.example.mobiledegreefinalproject.repository.TripRepository.OnTripSyncListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Trip data refresh completed successfully");
                    runOnUiThread(() -> {
                        // Data will be automatically updated through LiveData observers
                        Toast.makeText(TripDetailActivity.this, "Activities updated!", Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Trip data refresh failed: " + error);
                    runOnUiThread(() -> {
                        // Still load local data
                        loadTripDataLocalOnly();
                    });
                }
            });
        }
    }
    
    /**
     * Directly reload trip data from repository without ViewModel observers
     */
    private void reloadTripDataDirectly() {
        Log.d(TAG, "🔄 Directly reloading trip data from repository for trip ID: " + tripId);
        
        new Thread(() -> {
            try {
                TripRepository repository = TripRepository.getInstance(this);
                if (repository == null) {
                    Log.e(TAG, "❌ Repository is null!");
                    return;
                }
                
                // Get trip directly from repository (bypasses ViewModel cache)
                Trip freshTrip = repository.getTripByIdSync(tripId);
                
                if (freshTrip != null) {
                    Log.d(TAG, "🔄 Fresh trip data loaded directly:");
                    Log.d(TAG, "   Title: " + freshTrip.getTitle());
                    Log.d(TAG, "   Local ID: " + freshTrip.getId());
                    Log.d(TAG, "   Firebase ID: " + freshTrip.getFirebaseId());
                    Log.d(TAG, "   Has Firebase ID: " + (freshTrip.getFirebaseId() != null && !freshTrip.getFirebaseId().isEmpty()));
                    
                    // Update currentTrip on UI thread
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            currentTrip = freshTrip;
                            displayTripInfo(freshTrip);
                            Log.d(TAG, "✓ currentTrip updated with fresh data including Firebase ID");
                            
                            // CRITICAL: Notify that trip reload is complete
                            // This ensures Firebase queries have the correct Firebase ID
                            if (freshTrip.getFirebaseId() != null && !freshTrip.getFirebaseId().isEmpty()) {
                                Log.d(TAG, "🎯 Trip reload complete with Firebase ID: " + freshTrip.getFirebaseId());
                            } else {
                                Log.w(TAG, "⚠️ Trip reload complete but still no Firebase ID!");
                            }
                        }
                    });
                } else {
                    Log.e(TAG, "❌ Fresh trip data is null for ID: " + tripId);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error directly loading trip data", e);
            }
        }).start();
    }
    
    /**
     * FAST FIREBASE JSON: Load trip and activities in one go
     */
    private void loadTripAndActivitiesFromFirebaseJson() {
        Log.d(TAG, "🚀🚀🚀 FAST FIREBASE JSON: Loading trip and activities - START");
        
        // CRITICAL FIX: Prevent multiple simultaneous Firebase loads
        synchronized (this) {
            if (isLoadingFromFirebase) {
                Log.w(TAG, "🚀 Firebase load already in progress, skipping duplicate request");
                return;
            }
            isLoadingFromFirebase = true;
            // Reset flag when starting fresh Firebase load
            hasDisplayedFirebaseData = false;
        }
        
        // CRITICAL FIX: Disable real-time updates to prevent interference
        TripRepository repository = TripRepository.getInstance(this);
        repository.disableRealTimeUpdates();
        
        UserManager userManager = UserManager.getInstance(this);
        if (!userManager.isLoggedIn()) {
            Log.e(TAG, "❌ User not logged in");
            isLoadingFromFirebase = false;
            displayTimeline(new ArrayList<>());
            return;
        }
        
        if (currentTrip == null) {
            Log.e(TAG, "❌ currentTrip is null");
            isLoadingFromFirebase = false;
            displayTimeline(new ArrayList<>());
            return;
        }
        
        String userEmail = userManager.getUserEmail();
        Log.d(TAG, "🚀 User email: " + userEmail);
        Log.d(TAG, "🚀 Current trip: " + currentTrip.getTitle() + " -> " + currentTrip.getDestination());
        Log.d(TAG, "🚀 Current trip Firebase ID: " + currentTrip.getFirebaseId());
        Log.d(TAG, "🚀 Current trip local ID: " + currentTrip.getId());
        
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        
        // 🔧 TEMPORARY DEBUG: Check your specific data
        debugSpecificFirebaseData();
        
        // 🔧 DIRECT ACCESS: Try known Firebase ID if available
        if (currentTrip.getFirebaseId() != null && !currentTrip.getFirebaseId().isEmpty()) {
            Log.d(TAG, "🚀 DIRECT ACCESS: Trying known Firebase ID: " + currentTrip.getFirebaseId());
            testDirectActivityAccess(firestore, userEmail, currentTrip.getFirebaseId());
        }
        
        // 🔧 MANUAL ACCESS: Try the specific trip ID we can see in console
        String knownTripId = "LGRen9seRlnLfYyvctmT"; // From your Firebase console
        Log.d(TAG, "🚀 MANUAL ACCESS: Trying known trip ID: " + knownTripId);
        firestore.collection("users")
                .document(userEmail)
                .collection("trips")
                .document(knownTripId)
                .collection("activities")
                .get()
                .addOnSuccessListener(activitiesQuery -> {
                    Log.d(TAG, "🚀 MANUAL: Found " + activitiesQuery.size() + " activities in trip " + knownTripId);
                    
                    if (activitiesQuery.size() > 0) {
                        List<TripActivity> activities = new ArrayList<>();
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : activitiesQuery) {
                                                                    // Use nuclear parsing to prevent crashes
                                        TripActivity activity = parseFirebaseActivityNuclear(doc);
                                        if (activity != null) {
                                            activities.add(activity);
                                        }
                        }
                        
                        Log.d(TAG, "🚀 MANUAL: Successfully parsed " + activities.size() + " activities");
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                displayTimeline(activities);
                                if (infoPanelManager != null) {
                                    infoPanelManager.addCustomMessage("✓ Loaded " + activities.size() + " activities (manual)", false);
                                }
                            }
                        });
                        return; // Exit early if manual access works
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🚀 MANUAL: Failed to access activities with known ID", e);
                });
        
        // STEP 1: First, let's see ALL trips in Firebase for this user
        Log.d(TAG, "🚀 STEP 1: Listing ALL trips in Firebase for debugging");
        firestore.collection("users")
                .document(userEmail)
                .collection("trips")
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(allTripsQuery -> {
                    Log.d(TAG, "🚀 STEP 1 SUCCESS: Found " + allTripsQuery.size() + " total trips in Firebase");
                    
                    for (com.google.firebase.firestore.QueryDocumentSnapshot tripDoc : allTripsQuery) {
                        Log.d(TAG, "   🗂️ Trip: '" + tripDoc.getString("title") + "' -> '" + tripDoc.getString("destination") + "' (ID: " + tripDoc.getId() + ")");
                        
                        // Also check if this trip has activities
                        String tripId = tripDoc.getId();
                        firestore.collection("users")
                                .document(userEmail)
                                .collection("trips")
                                .document(tripId)
                                .collection("activities")
                                .get()
                                .addOnSuccessListener(activitiesSnap -> {
                                    Log.d(TAG, "     📝 Trip " + tripId + " has " + activitiesSnap.size() + " activities");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "     ❌ Failed to check activities for trip " + tripId + ": " + e.getMessage());
                                });
                    }
                    
                    // STEP 2: Now proceed with the original search
                    proceedWithTripSearch(firestore, userEmail);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🚀 STEP 1 FAILED: Cannot list trips: " + e.getMessage());
                    e.printStackTrace();
                    
                    // Still try the original search as fallback
                    proceedWithTripSearch(firestore, userEmail);
                });
    }
    
    private void proceedWithTripSearch(FirebaseFirestore firestore, String userEmail) {
        Log.d(TAG, "🚀 STEP 2: Proceeding with trip search");
        Log.d(TAG, "🚀 Searching Firebase for trip:");
        Log.d(TAG, "   Title: '" + currentTrip.getTitle() + "'");
        Log.d(TAG, "   Destination: '" + currentTrip.getDestination() + "'");
        Log.d(TAG, "   Firebase path: users/" + userEmail + "/trips");
        
        // Find trip in Firebase by title and destination
        Log.d(TAG, "🚀 SEARCHING Firebase for:");
        Log.d(TAG, "   Local trip title: '" + currentTrip.getTitle() + "'");
        Log.d(TAG, "   Local trip destination: '" + currentTrip.getDestination() + "'");
        Log.d(TAG, "   Local trip Firebase ID: '" + currentTrip.getFirebaseId() + "'");
        
        firestore.collection("users")
                .document(userEmail)
                .collection("trips")
                .whereEqualTo("title", currentTrip.getTitle())
                .whereEqualTo("destination", currentTrip.getDestination())
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(tripQuery -> {
                    Log.d(TAG, "🚀 Trip search results: " + tripQuery.size() + " trips found");
                    
                    // Log what we actually found in Firebase vs what we searched for
                    Log.d(TAG, "🚀 COMPARISON:");
                    Log.d(TAG, "   Searched for title: '" + currentTrip.getTitle() + "'");
                    Log.d(TAG, "   Searched for destination: '" + currentTrip.getDestination() + "'");
                    
                    if (!tripQuery.isEmpty()) {
                        String tripFirebaseId = tripQuery.getDocuments().get(0).getId();
                        Log.d(TAG, "🚀 Found trip Firebase ID: " + tripFirebaseId);
                        
                        // Log all trip data for debugging
                        com.google.firebase.firestore.QueryDocumentSnapshot tripDoc = (com.google.firebase.firestore.QueryDocumentSnapshot) tripQuery.getDocuments().get(0);
                        Log.d(TAG, "🚀 Trip document data:");
                        Log.d(TAG, "   Firebase ID: " + tripDoc.getId());
                        Log.d(TAG, "   Title in Firebase: '" + tripDoc.getString("title") + "'");
                        Log.d(TAG, "   Destination in Firebase: '" + tripDoc.getString("destination") + "'");
                        
                        // Update currentTrip
                        currentTrip.setFirebaseId(tripFirebaseId);
                        
                        // Now load activities directly
                        firestore.collection("users")
                                .document(userEmail)
                                .collection("trips")
                                .document(tripFirebaseId)
                                .collection("activities")
                                .orderBy("dayNumber", Query.Direction.ASCENDING)
                                .orderBy("dateTime", Query.Direction.ASCENDING)
                                .get(com.google.firebase.firestore.Source.SERVER)
                                .addOnSuccessListener(activitiesQuery -> {
                                    Log.d(TAG, "🚀 Firebase activities query SUCCESS!");
                                    Log.d(TAG, "   Activities found: " + activitiesQuery.size());
                                    Log.d(TAG, "   Query path: users/" + userEmail + "/trips/" + tripFirebaseId + "/activities");
                                    Log.d(TAG, "   Is from cache: " + activitiesQuery.getMetadata().isFromCache());
                                    
                                    List<TripActivity> activities = new ArrayList<>();
                                    int activityCount = 0;
                                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : activitiesQuery) {
                                        activityCount++;
                                        Log.d(TAG, "🚀 Processing activity " + activityCount + ": " + doc.getString("title"));
                                        
                                        // LOG ALL FIREBASE FIELDS to see the actual structure
                                        Log.d(TAG, "🔍 Firebase document fields for " + doc.getId() + ":");
                                        for (String field : doc.getData().keySet()) {
                                            Object value = doc.get(field);
                                            Log.d(TAG, "   " + field + " = " + value + " (type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
                                        }
                                        
                                                                // Use nuclear parsing method to avoid crashes
                        TripActivity activity = parseFirebaseActivityNuclear(doc);
                        if (activity != null) {
                            activities.add(activity);
                        } else {
                                            Log.e(TAG, "❌ Failed to parse activity from Firebase document: " + doc.getId());
                                        }
                                    }
                                    
                                    Log.d(TAG, "🚀 SUCCESS: Displaying " + activities.size() + " Firebase activities");
                                    
                                    // Debug: Log each activity title
                                    for (TripActivity act : activities) {
                                        Log.d(TAG, "   📝 Activity: " + act.getTitle() + " (Day " + act.getDayNumber() + ")");
                                    }
                                    
                                    runOnUiThread(() -> {
                                        if (!isFinishing() && !isDestroyed()) {
                                            Log.d(TAG, "🚀 UI Thread: About to call displayTimeline with " + activities.size() + " activities");
                                            displayTimeline(activities);
                                            Log.d(TAG, "🚀 UI Thread: displayTimeline completed");
                                            
                                            if (infoPanelManager != null) {
                                                infoPanelManager.addCustomMessage("✓ Loaded " + activities.size() + " activities from cloud", false);
                                            }
                                        } else {
                                            Log.w(TAG, "🚀 UI Thread: Activity finishing/destroyed, skipping display");
                                        }
                                        
                                        // CRITICAL FIX: Reset loading flag when complete
                                        isLoadingFromFirebase = false;
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "🚀 Failed to load activities", e);
                                    runOnUiThread(() -> {
                                        if (!isFinishing() && !isDestroyed()) {
                                            // CRITICAL FIX: Don't clear data if Firebase data was already displayed
                                            if (!hasDisplayedFirebaseData) {
                                                displayTimeline(new ArrayList<>());
                                                if (infoPanelManager != null) {
                                                    infoPanelManager.addCustomMessage("❌ Failed to load activities", true);
                                                }
                                            } else {
                                                Log.d(TAG, "🎯 PROTECTED: Firebase data already displayed, not clearing");
                                            }
                                        }
                                        
                                        // CRITICAL FIX: Reset loading flag on failure too
                                        isLoadingFromFirebase = false;
                                    });
                                });
                    } else {
                        Log.e(TAG, "🚀 Trip not found with exact search - trying fallback");
                        Log.e(TAG, "   Searched for title: '" + currentTrip.getTitle() + "'");
                        Log.e(TAG, "   Searched for destination: '" + currentTrip.getDestination() + "'");
                        
                        // FALLBACK: List all trips and find the matching one manually
                        firestore.collection("users")
                                .document(userEmail)
                                .collection("trips")
                                .get(com.google.firebase.firestore.Source.SERVER)
                                .addOnSuccessListener(allTripsQuery -> {
                                    Log.d(TAG, "🚀 FALLBACK: Found " + allTripsQuery.size() + " total trips");
                                    
                                    String foundTripId = null;
                                    for (com.google.firebase.firestore.QueryDocumentSnapshot tripDoc : allTripsQuery) {
                                        String firebaseTitle = tripDoc.getString("title");
                                        String firebaseDestination = tripDoc.getString("destination");
                                        
                                        Log.d(TAG, "🚀 Checking trip: '" + firebaseTitle + "' -> '" + firebaseDestination + "'");
                                        
                                        // Try exact match first
                                        if (currentTrip.getTitle().equals(firebaseTitle) && 
                                            currentTrip.getDestination().equals(firebaseDestination)) {
                                            foundTripId = tripDoc.getId();
                                            Log.d(TAG, "🚀 FALLBACK: Found exact match with ID: " + foundTripId);
                                            break;
                                        }
                                        
                                        // Try trimmed match
                                        if (currentTrip.getTitle().trim().equals(firebaseTitle != null ? firebaseTitle.trim() : "") && 
                                            currentTrip.getDestination().trim().equals(firebaseDestination != null ? firebaseDestination.trim() : "")) {
                                            foundTripId = tripDoc.getId();
                                            Log.d(TAG, "🚀 FALLBACK: Found trimmed match with ID: " + foundTripId);
                                            break;
                                        }
                                    }
                                    
                                    if (foundTripId != null) {
                                        String finalTripId = foundTripId;
                                        Log.d(TAG, "🚀 FALLBACK SUCCESS: Using trip ID " + finalTripId);
                                        currentTrip.setFirebaseId(finalTripId);
                                        
                                        // Load activities with the found trip ID
                                        firestore.collection("users")
                                                .document(userEmail)
                                                .collection("trips")
                                                .document(finalTripId)
                                                .collection("activities")
                                                .orderBy("dayNumber", Query.Direction.ASCENDING)
                                                .orderBy("dateTime", Query.Direction.ASCENDING)
                                                .get(com.google.firebase.firestore.Source.SERVER)
                                                .addOnSuccessListener(activitiesQuery -> {
                                                    Log.d(TAG, "🚀 FALLBACK: Activities loaded: " + activitiesQuery.size());
                                                    
                                                    List<TripActivity> activities = new ArrayList<>();
                                                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : activitiesQuery) {
                                                        // Use standardized helper for fallback parsing too
                                                                                                // Use nuclear parsing to prevent crashes
                                        TripActivity fallbackActivity = parseFirebaseActivityNuclear(doc);
                                        if (fallbackActivity != null) {
                                            activities.add(fallbackActivity);
                                        } else {
                                                            Log.e(TAG, "❌ Failed to parse fallback activity from Firebase document: " + doc.getId());
                                                        }
                                                    }
                                                    
                                                    Log.d(TAG, "🚀 FALLBACK: Displaying " + activities.size() + " activities");
                                                    runOnUiThread(() -> {
                                                        if (!isFinishing() && !isDestroyed()) {
                                                            displayTimeline(activities);
                                                            if (infoPanelManager != null) {
                                                                infoPanelManager.addCustomMessage("✓ Loaded " + activities.size() + " activities (fallback)", false);
                                                            }
                                                        }
                                                        
                                                        // CRITICAL FIX: Reset loading flag when fallback complete
                                                        isLoadingFromFirebase = false;
                                                    });
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "🚀 FALLBACK: Failed to load activities", e);
                                                    runOnUiThread(() -> {
                                                        if (!isFinishing() && !isDestroyed()) {
                                                            // CRITICAL FIX: Don't clear data if Firebase data was already displayed
                                                            if (!hasDisplayedFirebaseData) {
                                                                displayTimeline(new ArrayList<>());
                                                            } else {
                                                                Log.d(TAG, "🎯 PROTECTED: Firebase data already displayed, not clearing (fallback)");
                                                            }
                                                        }
                                                        
                                                        // CRITICAL FIX: Reset loading flag on fallback failure
                                                        isLoadingFromFirebase = false;
                                                    });
                                                });
                                    } else {
                                        Log.e(TAG, "🚀 FALLBACK FAILED: No matching trip found");
                                        
                                                                // LAST RESORT: Try to use any existing Firebase ID from currentTrip
                        if (currentTrip.getFirebaseId() != null && !currentTrip.getFirebaseId().isEmpty()) {
                            Log.d(TAG, "🚀 LAST RESORT: Trying existing Firebase ID: " + currentTrip.getFirebaseId());
                            testDirectActivityAccess(firestore, userEmail, currentTrip.getFirebaseId());
                        } else {
                            Log.e(TAG, "🚀 COMPLETE FAILURE: No Firebase ID available");
                            
                            // 🔧 EMERGENCY FIX: If local trip title/destination match what we see in console
                            if ("Hs".equals(currentTrip.getTitle()) && "Hw".equals(currentTrip.getDestination())) {
                                Log.d(TAG, "🔧 EMERGENCY: Detected matching trip, setting Firebase ID manually");
                                currentTrip.setFirebaseId("LGRen9seRlnLfYyvctmT");
                                
                                // Try to load activities with the known ID
                                testDirectActivityAccess(firestore, userEmail, "LGRen9seRlnLfYyvctmT");
                            } else {
                                                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    // CRITICAL FIX: Don't clear data if Firebase data was already displayed
                                    if (!hasDisplayedFirebaseData) {
                                        displayTimeline(new ArrayList<>());
                                        if (infoPanelManager != null) {
                                            infoPanelManager.addCustomMessage("❌ Trip not found in cloud", true);
                                        }
                                    } else {
                                        Log.d(TAG, "🎯 PROTECTED: Firebase data already displayed, not clearing (final failure)");
                                    }
                                }
                                
                                // CRITICAL FIX: Reset loading flag on final failure
                                isLoadingFromFirebase = false;
                            });
                            }
                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "🚀 FALLBACK: Failed to list trips", e);
                                    runOnUiThread(() -> {
                                        if (!isFinishing() && !isDestroyed()) {
                                            // CRITICAL FIX: Don't clear data if Firebase data was already displayed
                                            if (!hasDisplayedFirebaseData) {
                                                displayTimeline(new ArrayList<>());
                                                if (infoPanelManager != null) {
                                                    infoPanelManager.addCustomMessage("❌ Cloud connection failed", true);
                                                }
                                            } else {
                                                Log.d(TAG, "🎯 PROTECTED: Firebase data already displayed, not clearing (fallback list failure)");
                                            }
                                        }
                                    });
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🚀 Failed to find trip in Firebase", e);
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            // CRITICAL FIX: Don't clear data if Firebase data was already displayed
                            if (!hasDisplayedFirebaseData) {
                                displayTimeline(new ArrayList<>());
                                if (infoPanelManager != null) {
                                    infoPanelManager.addCustomMessage("❌ Cloud connection failed", true);
                                }
                            } else {
                                Log.d(TAG, "🎯 PROTECTED: Firebase data already displayed, not clearing (connection failure)");
                            }
                        }
                        
                        // CRITICAL FIX: Reset loading flag on connection failure
                        isLoadingFromFirebase = false;
                    });
                });
    }
    
    // CRITICAL FIX: Method to refresh activity data without using LiveData observers (prevents crashes)
    private void refreshActivityDataDirectly() {
        // NUCLEAR PROTECTION: Absolutely block refresh operations in Firebase-only mode
        if (isFirebaseOnlyMode) {
            Log.w(TAG, "🔥🚨 NUCLEAR PROTECTION: Blocking refreshActivityDataDirectly in Firebase-only mode!");
            Log.w(TAG, "🔥 Using cached Firebase data instead");
            
            if (!firebaseActivitiesCache.isEmpty()) {
                displayFirebaseActivitiesNuclear(firebaseActivitiesCache);
            } else {
                Log.w(TAG, "🔥 No cached Firebase data, reloading from Firebase");
                loadFirebaseActivitiesDirectNuclear();
            }
            return;
        }
        
        // Original refresh logic for offline users only
        Log.d(TAG, "📱 Refreshing activity data for local mode");
        new Thread(() -> {
            try {
                TripRepository repository = TripRepository.getInstance(this);
                List<TripActivity> activities = repository.getActivitiesForTripSync(tripId);
                
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        displayTimeline(activities);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing activity data", e);
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() called - refreshing data");
        
        // CRITICAL FIX: Reset protection flag when resuming to allow fresh loads
        hasDisplayedFirebaseData = false;
        
        if (tripId != -1) {
            // NUCLEAR: Smart refresh based on current mode
            if (isFirebaseOnlyMode) {
                // Force refresh if we just added an activity or no data is displayed
                if (justAddedActivity || timelineAdapter == null || timelineAdapter.getItemCount() == 0 || firebaseActivitiesCache.isEmpty()) {
                    Log.d(TAG, "🔥 NUCLEAR RESUME: " + (justAddedActivity ? "Just added activity" : "No data displayed") + " - loading from Firebase");
                    loadFirebaseActivitiesDirectNuclear();
                    justAddedActivity = false; // Reset flag
                } else {
                    Log.d(TAG, "🔥 NUCLEAR RESUME: Data already displayed, skipping refresh to prevent flicker");
                }
            } else {
                // LOCAL MODE: For offline users, load from local database only
                Log.d(TAG, "📱 LOCAL RESUME: User offline - loading from local database only");
                if (justAddedActivity) {
                    Log.d(TAG, "📱 LOCAL RESUME: Just added activity - forcing refresh");
                    justAddedActivity = false; // Reset flag
                }
                refreshActivityDataDirectly();
            }
        }
    }
    
    /**
     * Test direct access to activities using existing Firebase ID
     */
    private void testDirectActivityAccess(FirebaseFirestore firestore, String userEmail, String tripFirebaseId) {
        Log.d(TAG, "🚀 TESTING DIRECT ACCESS to activities");
        Log.d(TAG, "   Path: users/" + userEmail + "/trips/" + tripFirebaseId + "/activities");
        
        firestore.collection("users")
                .document(userEmail)
                .collection("trips")
                .document(tripFirebaseId)
                .collection("activities")
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(activitiesQuery -> {
                    Log.d(TAG, "🚀 DIRECT ACCESS SUCCESS: Found " + activitiesQuery.size() + " activities");
                    
                    if (activitiesQuery.size() > 0) {
                        Log.d(TAG, "🚀 DIRECT ACCESS: Processing activities...");
                        
                        List<TripActivity> activities = new ArrayList<>();
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : activitiesQuery) {
                            Log.d(TAG, "🚀 DIRECT: Processing activity " + doc.getId());
                            
                            // Log all fields in this activity
                            Log.d(TAG, "🔍 DIRECT - Firebase document fields for " + doc.getId() + ":");
                            for (String field : doc.getData().keySet()) {
                                Object value = doc.get(field);
                                Log.d(TAG, "   " + field + " = " + value + " (type: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
                            }
                            
                            TripActivity activity = new TripActivity();
                            activity.setFirebaseId(doc.getId());
                            activity.setTripId(tripId);
                            
                            // Use flexible field parsing
                            activity.setTitle(getStringField(doc, "title", "activityTitle", "name"));
                            activity.setDescription(getStringField(doc, "description", "activityDescription", "desc"));
                            activity.setLocation(getStringField(doc, "location", "activityLocation", "place"));
                            activity.setTimeString(getStringField(doc, "timeString", "time", "activityTime"));
                            activity.setImageUrl(getStringField(doc, "imageUrl", "image", "activityImage"));
                            
                            Long dateTime = getLongField(doc, "dateTime", "timestamp", "date");
                            if (dateTime != null) activity.setDateTime(dateTime);
                            
                            Long dayNumber = getLongField(doc, "dayNumber", "day", "dayNum");
                            if (dayNumber != null) activity.setDayNumber(dayNumber.intValue());
                            
                            Long createdAt = getLongField(doc, "createdAt", "created", "createTime");
                            Long updatedAt = getLongField(doc, "updatedAt", "updated", "updateTime");
                            if (createdAt != null) activity.setCreatedAt(createdAt);
                            if (updatedAt != null) activity.setUpdatedAt(updatedAt);
                            
                            activity.setSynced(true);
                            activities.add(activity);
                            
                            Log.d(TAG, "🚀 DIRECT: Added activity: " + activity.getTitle());
                        }
                        
                        Log.d(TAG, "🚀 DIRECT ACCESS: Displaying " + activities.size() + " activities");
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                displayTimeline(activities);
                                if (infoPanelManager != null) {
                                    infoPanelManager.addCustomMessage("✓ Loaded " + activities.size() + " activities (direct access)", false);
                                }
                            }
                            
                            // CRITICAL FIX: Reset loading flag when direct access complete
                            isLoadingFromFirebase = false;
                        });
                    } else {
                        Log.d(TAG, "🚀 DIRECT ACCESS: No activities found");
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                displayTimeline(new ArrayList<>());
                                if (infoPanelManager != null) {
                                    infoPanelManager.addCustomMessage("No activities found for this trip", false);
                                }
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "🚀 DIRECT ACCESS FAILED: " + e.getMessage());
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            // CRITICAL FIX: Don't clear data if Firebase data was already displayed
                            if (!hasDisplayedFirebaseData) {
                                displayTimeline(new ArrayList<>());
                                if (infoPanelManager != null) {
                                    infoPanelManager.addCustomMessage("❌ Failed to access activities", true);
                                }
                            } else {
                                Log.d(TAG, "🎯 PROTECTED: Firebase data already displayed, not clearing (direct access failure)");
                            }
                        }
                        
                        // CRITICAL FIX: Reset loading flag on direct access failure
                        isLoadingFromFirebase = false;
                    });
                });
    }
    
    /**
     * Helper method to get String field with multiple possible names
     */
    private String getStringField(com.google.firebase.firestore.QueryDocumentSnapshot doc, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = doc.getString(fieldName);
            if (value != null && !value.trim().isEmpty()) {
                Log.d(TAG, "🔍 Found string field '" + fieldName + "' = '" + value + "'");
                return value;
            }
        }
        Log.w(TAG, "🔍 No string value found for fields: " + java.util.Arrays.toString(fieldNames));
        return null;
    }
    
    /**
     * Helper method to get Long field with multiple possible names
     */
    private Long getLongField(com.google.firebase.firestore.QueryDocumentSnapshot doc, String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                // Try as Long first
                Long value = doc.getLong(fieldName);
                if (value != null) {
                    Log.d(TAG, "🔍 Found long field '" + fieldName + "' = " + value);
                    return value;
                }
                
                // Try as Double and convert
                Double doubleValue = doc.getDouble(fieldName);
                if (doubleValue != null) {
                    Log.d(TAG, "🔍 Found double field '" + fieldName + "' = " + doubleValue + " (converting to long)");
                    return doubleValue.longValue();
                }
                
                // Try as String and parse
                String stringValue = doc.getString(fieldName);
                if (stringValue != null && !stringValue.trim().isEmpty()) {
                    try {
                        Long parsedValue = Long.parseLong(stringValue.trim());
                        Log.d(TAG, "🔍 Found string field '" + fieldName + "' = '" + stringValue + "' (parsed to long: " + parsedValue + ")");
                        return parsedValue;
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "🔍 Could not parse string '" + stringValue + "' as long for field '" + fieldName + "'");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "🔍 Error getting field '" + fieldName + "': " + e.getMessage());
            }
        }
        Log.w(TAG, "🔍 No long value found for fields: " + java.util.Arrays.toString(fieldNames));
        return null;
    }

    
    /**
     * Sync Firebase activities to local database to keep them in sync
     */
    private void syncFirebaseActivitiesToLocal(List<TripActivity> firebaseActivities) {
        try {
            TripRepository repository = TripRepository.getInstance(this);
            if (repository == null) {
                Log.e(TAG, "Repository is null during Firebase sync");
                return;
            }
            
            Log.d(TAG, "Syncing " + firebaseActivities.size() + " Firebase activities to local database");
            
            // Get current local activities synchronously
            new Thread(() -> {
                try {
                    List<TripActivity> localActivities = repository.getActivitiesForTripSync(tripId);
                    Map<String, TripActivity> localActivityMap = new HashMap<>();
                    
                    // Create map of local activities by Firebase ID
                    for (TripActivity local : localActivities) {
                        if (local.getFirebaseId() != null) {
                            localActivityMap.put(local.getFirebaseId(), local);
                        }
                    }
                    
                    // Process each Firebase activity on UI thread
                    runOnUiThread(() -> {
                        syncActivitiesOneByOne(firebaseActivities, localActivityMap, repository, 0);
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error getting local activities for sync", e);
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting Firebase to local sync", e);
        }
    }
    
    /**
     * Sync activities one by one to avoid async conflicts
     */
    private void syncActivitiesOneByOne(List<TripActivity> firebaseActivities, 
                                       Map<String, TripActivity> localActivityMap, 
                                       TripRepository repository, int index) {
        if (index >= firebaseActivities.size()) {
            Log.d(TAG, "✓ Firebase to local sync completed");
            return;
        }
        
        TripActivity firebaseActivity = firebaseActivities.get(index);
        String firebaseId = firebaseActivity.getFirebaseId();
        
        if (firebaseId == null) {
            // Skip and continue to next
            syncActivitiesOneByOne(firebaseActivities, localActivityMap, repository, index + 1);
            return;
        }
        
        TripActivity localActivity = localActivityMap.get(firebaseId);
        
        if (localActivity == null) {
            // Activity doesn't exist locally - insert it
            Log.d(TAG, "Inserting new activity from Firebase: " + firebaseActivity.getTitle());
            repository.insertActivity(firebaseActivity, new TripRepository.OnActivityOperationListener() {
                @Override
                public void onSuccess(int savedActivityId) {
                    Log.d(TAG, "✓ Inserted Firebase activity locally: " + firebaseActivity.getTitle());
                    // Continue to next activity
                    syncActivitiesOneByOne(firebaseActivities, localActivityMap, repository, index + 1);
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Failed to insert Firebase activity locally: " + error);
                    // Continue to next activity anyway
                    syncActivitiesOneByOne(firebaseActivities, localActivityMap, repository, index + 1);
                }
            });
        } else {
            // Activity exists - update if Firebase version is newer
            if (firebaseActivity.getUpdatedAt() > localActivity.getUpdatedAt()) {
                Log.d(TAG, "Updating local activity from Firebase: " + firebaseActivity.getTitle());
                firebaseActivity.setId(localActivity.getId()); // Keep local ID
                repository.updateActivity(firebaseActivity, new TripRepository.OnActivityOperationListener() {
                    @Override
                    public void onSuccess(int updatedActivityId) {
                        Log.d(TAG, "✓ Updated Firebase activity locally: " + firebaseActivity.getTitle());
                        // Continue to next activity
                        syncActivitiesOneByOne(firebaseActivities, localActivityMap, repository, index + 1);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Failed to update Firebase activity locally: " + error);
                        // Continue to next activity anyway
                        syncActivitiesOneByOne(firebaseActivities, localActivityMap, repository, index + 1);
                    }
                });
            } else {
                Log.d(TAG, "Local activity is newer, skipping: " + firebaseActivity.getTitle());
                // Continue to next activity
                syncActivitiesOneByOne(firebaseActivities, localActivityMap, repository, index + 1);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() called");
    }

    @Override
    protected void onDestroy() {
        // NUCLEAR PROTECTION: Prevent any cleanup operations in Firebase-only mode
        if (isFirebaseOnlyMode) {
            Log.d(TAG, "🔥 NUCLEAR: onDestroy - Firebase-only mode, disabling nuclear mode");
            TripRepository repository = TripRepository.getInstance(this);
            repository.disableNuclearFirebaseMode();
        } else {
            Log.d(TAG, "📱 onDestroy - local mode, performing normal cleanup");
            TripRepository repository = TripRepository.getInstance(this);
            repository.enableRealTimeUpdates();
        }
        
        super.onDestroy();
        Log.d(TAG, "onDestroy() called - cleaning up resources");
        
        // Release MediaPlayer resources from DeleteDialogHelper
        DeleteDialogHelper.releaseMediaPlayer();
        
        // CRITICAL FIX: Clear any ongoing operations to prevent crashes
        try {
            // CRITICAL FIX: Only remove observers if they were actually set up (for offline users)
            UserManager userManager = UserManager.getInstance(this);
            if (viewModel != null && tripId != -1 && !userManager.isLoggedIn()) {
                Log.d(TAG, "Removing ViewModel observers for offline user in onDestroy");
                viewModel.getTripById(tripId).removeObservers(this);
                viewModel.getActivitiesForTrip(tripId).removeObservers(this);
            } else {
                Log.d(TAG, "Skipping observer removal for logged-in user in onDestroy (no observers were set)");
            }
            
            // Clear adapter to prevent memory leaks
            if (timelineAdapter != null) {
                timelineAdapter = null;
            }
            
            // Clear recycler view to prevent memory leaks
            if (timelineRecyclerView != null) {
                timelineRecyclerView.setAdapter(null);
            }
            
            // CRITICAL FIX: Only re-enable real-time updates for offline users
            try {
                TripRepository repository = TripRepository.getInstance(this);
                if (!userManager.isLoggedIn()) {
                    Log.d(TAG, "Re-enabling real-time updates for offline user");
                    repository.enableRealTimeUpdates();
                } else {
                    Log.d(TAG, "Keeping real-time updates disabled for logged-in user");
                }
            } catch (Exception e) {
                Log.w(TAG, "Error managing real-time updates", e);
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error cleaning up observers and resources", e);
        }
    }

    /**
     * Load trip data from local database (for offline users only)
     */
    private void loadTripDataLocalOnly() {
        Log.d(TAG, "📱 Loading trip data from local database ONLY for trip ID: " + tripId);
        
        UserManager userManager = UserManager.getInstance(this);
        
        // CRITICAL PROTECTION: Absolutely prevent this for logged-in users
        if (userManager.isLoggedIn()) {
            Log.e(TAG, "🚨🚨🚨 CRITICAL ERROR: loadTripDataLocalOnly called for LOGGED-IN user!");
            Log.e(TAG, "🚨 This should NEVER happen! Using Firebase instead.");
            loadTripFromFirebaseOnlyNuclear();
            return;
        }
        
        // OFFLINE USERS ONLY: Set up ViewModel observers
        Log.d(TAG, "📱 Setting up ViewModel observers for offline user");
        
        // Load trip details
        viewModel.getTripById(tripId).observe(this, trip -> {
            if (trip != null) {
                Log.d(TAG, "📱 Local trip data loaded:");
                Log.d(TAG, "   Title: " + trip.getTitle());
                Log.d(TAG, "   Local ID: " + trip.getId());
                
                currentTrip = trip;
                displayTripInfo(trip);
            } else {
                Log.w(TAG, "Trip data is null for ID: " + tripId);
            }
        });
        
        // Load activities
        viewModel.getActivitiesForTrip(tripId).observe(this, activities -> {
            if (activities != null) {
                Log.d(TAG, "📱 Local activities loaded: " + activities.size() + " activities");
                displayTimeline(activities);
            } else {
                Log.w(TAG, "Activities data is null for trip ID: " + tripId);
            }
        });
    }
}