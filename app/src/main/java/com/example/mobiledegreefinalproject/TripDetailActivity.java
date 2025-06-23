package com.example.mobiledegreefinalproject;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.mobiledegreefinalproject.repository.TripRepository;

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
    
    // Info Panel Manager for non-intrusive messages
    private InfoPanelManager infoPanelManager;
    
    // Modern replacement for startActivityForResult
    private ActivityResultLauncher<Intent> addActivityLauncher;

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
        loadTripData();
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
                        Log.d(TAG, "Activity creation successful - refreshing data directly");
                        
                        // CRITICAL FIX: Add delay before refresh to allow Firebase sync to complete
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                refreshActivityDataDirectly();
                                
                                // Show success message via info panel instead of toast
                                if (infoPanelManager != null) {
                                    infoPanelManager.addCustomMessage("Activity added successfully! Timeline updated.", false);
                                }
                            }
                        }, 1000); // 1 second delay
                        
                        // Also do immediate refresh for local data
                        refreshActivityDataDirectly();
                        
                    } else {
                        Log.d(TAG, "Activity creation cancelled or failed");
                        // Just reload the current data to ensure consistency
                        refreshActivityDataDirectly();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling activity result", e);
                    // Fallback to basic refresh
                    if (!isFinishing() && !isDestroyed()) {
                        refreshActivityDataDirectly();
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
                    // Clear any ongoing operations before finishing
                    if (viewModel != null && tripId != -1) {
                        try {
                            viewModel.getTripById(tripId).removeObservers(TripDetailActivity.this);
                            viewModel.getActivitiesForTrip(tripId).removeObservers(TripDetailActivity.this);
                        } catch (Exception e) {
                            Log.w(TAG, "Error removing observers on back press", e);
                        }
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

    private void loadTripData() {
        Log.d(TAG, "Loading trip data for trip ID: " + tripId);
        
        // Load trip details
        viewModel.getTripById(tripId).observe(this, trip -> {
            if (trip != null) {
                Log.d(TAG, "Trip data loaded: " + trip.getTitle());
                currentTrip = trip;
                displayTripInfo(trip);
            } else {
                Log.w(TAG, "Trip data is null for ID: " + tripId);
            }
        });
        
        // Load activities
        viewModel.getActivitiesForTrip(tripId).observe(this, activities -> {
            if (activities != null) {
                Log.d(TAG, "Activities loaded: " + activities.size() + " activities");
                displayTimeline(activities);
            } else {
                Log.w(TAG, "Activities data is null for trip ID: " + tripId);
            }
        });
    }

    private void displayTripInfo(Trip trip) {
        tripTitle.setText(trip.getTitle());
        tripDestination.setText(trip.getDestination());
        tripDateRange.setText(trip.getDateRange());
        
        // TODO: Load map image when available
        mapPreview.setImageResource(R.drawable.ic_trips);
    }

    private void displayTimeline(List<TripActivity> activities) {
        Log.d(TAG, "Displaying timeline with " + activities.size() + " activities");
        
        if (activities.isEmpty()) {
            Log.d(TAG, "No activities - showing empty state");
            timelineRecyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            emptyStateText.setText("No activities planned yet.\nTap + to add your first activity!");
        } else {
            Log.d(TAG, "Showing activities timeline");
            timelineRecyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
            
            // Group activities by day
            Map<Integer, List<TripActivity>> groupedActivities = groupActivitiesByDay(activities);
            timelineAdapter.submitData(groupedActivities);
        }
    }

    private Map<Integer, List<TripActivity>> groupActivitiesByDay(List<TripActivity> activities) {
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
        
        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete Trip")
                .setMessage("Are you sure you want to delete \"" + currentTrip.getTitle() + "\"?\n\nThis action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteTrip())
                .setNegativeButton("Cancel", null)
                .show();
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
        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete Activity")
                .setMessage("Are you sure you want to delete \"" + activity.getTitle() + "\"?\n\nThis action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteActivity(activity))
                .setNegativeButton("Cancel", null)
                .show();
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
        
        // Use repository directly for immediate deletion
        com.example.mobiledegreefinalproject.repository.TripRepository repository = 
            com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
        
        repository.deleteActivity(activity, new com.example.mobiledegreefinalproject.repository.TripRepository.OnActivityOperationListener() {
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
                
                // Immediate refresh without delays
                refreshActivityDataDirectly();
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
                
                // Refresh to show current state
                refreshActivityDataDirectly();
            }
        });
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
                        loadTripData();
                    });
                }
            });
        }
    }
    
    // CRITICAL FIX: Method to refresh activity data without using LiveData observers (prevents crashes)
    private void refreshActivityDataDirectly() {
        Log.d(TAG, "=== DIRECT ACTIVITY DATA REFRESH ===");
        
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity finishing/destroyed, skipping refresh");
            return;
        }
        
        try {
            new Thread(() -> {
                try {
                    TripRepository repository = TripRepository.getInstance(this);
                    
                    // CRITICAL FIX: Get activities directly and clean up any duplicates
                    List<TripActivity> activities = repository.getActivitiesForTripSync(tripId);
                    
                    // Remove duplicates based on title and time (within 2 minutes)
                    Map<String, TripActivity> uniqueActivities = new LinkedHashMap<>();
                    for (TripActivity activity : activities) {
                        String key = activity.getTitle() + "_" + (activity.getDateTime() / 120000); // Group by 2-minute windows
                        
                        if (!uniqueActivities.containsKey(key)) {
                            uniqueActivities.put(key, activity);
                        } else {
                            // Keep the one with Firebase ID if available, or the newer one
                            TripActivity existing = uniqueActivities.get(key);
                            if (activity.getFirebaseId() != null && existing.getFirebaseId() == null) {
                                uniqueActivities.put(key, activity);
                            } else if (activity.getUpdatedAt() > existing.getUpdatedAt()) {
                                uniqueActivities.put(key, activity);
                            }
                        }
                    }
                    
                    List<TripActivity> cleanedActivities = new ArrayList<>(uniqueActivities.values());
                    
                    // Sort activities by date/time
                    cleanedActivities.sort((a1, a2) -> Long.compare(a1.getDateTime(), a2.getDateTime()));
                    
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Log.d(TAG, "Updating UI with " + cleanedActivities.size() + " activities");
                            displayTimeline(cleanedActivities);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error in direct activity refresh thread", e);
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed() && infoPanelManager != null) {
                            infoPanelManager.addCustomMessage("Failed to refresh activities", true);
                        }
                    });
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting direct activity refresh", e);
            if (infoPanelManager != null) {
                infoPanelManager.addCustomMessage("Failed to refresh activities", true);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() called - refreshing data");
        
        // CRITICAL FIX: Refresh data when returning to activity using direct method to prevent crashes
        if (tripId != -1) {
            refreshActivityDataDirectly();
            
            // Also do a quiet sync to ensure data consistency
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    forceSyncActivitiesQuiet();
                }
            }, 1000); // Small delay to let the direct refresh complete first
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() called - cleaning up resources");
        
        // CRITICAL FIX: Clear any ongoing operations to prevent crashes
        try {
            // Remove any observers that might still be attached
            if (viewModel != null && tripId != -1) {
                viewModel.getTripById(tripId).removeObservers(this);
                viewModel.getActivitiesForTrip(tripId).removeObservers(this);
            }
            
            // Clear adapter to prevent memory leaks
            if (timelineAdapter != null) {
                timelineAdapter = null;
            }
            
            // Clear recycler view to prevent memory leaks
            if (timelineRecyclerView != null) {
                timelineRecyclerView.setAdapter(null);
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Error cleaning up observers and resources", e);
        }
    }
}