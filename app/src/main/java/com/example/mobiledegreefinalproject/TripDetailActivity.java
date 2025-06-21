package com.example.mobiledegreefinalproject;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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
                        
                        // CRITICAL FIX: Use direct data refresh without LiveData observers to prevent crashes
                        refreshActivityDataDirectly();
                        
                        // Show success message
                        Toast.makeText(this, "Activity added successfully!", Toast.LENGTH_SHORT).show();
                        
                    } else {
                        Log.d(TAG, "Activity creation cancelled or failed");
                        // Just reload the current data
                        refreshActivityDataDirectly();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling activity result", e);
                    // Fallback to basic refresh
                    refreshActivityDataDirectly();
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
                Log.d(TAG, "Back button pressed");
                finish();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
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
            forceSyncActivities();
            return true;
        } else if (id == R.id.action_delete_trip) {
            showDeleteTripDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void forceSyncActivities() {
        Log.d(TAG, "Force syncing activities for trip: " + tripId);
        
        // Show loading indicator
        Toast.makeText(this, "Syncing activities...", Toast.LENGTH_SHORT).show();
        
        com.example.mobiledegreefinalproject.repository.TripRepository repository = 
            com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
        
        repository.forceSyncAllActivities(new com.example.mobiledegreefinalproject.repository.TripRepository.OnTripSyncListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Force sync completed successfully");
                runOnUiThread(() -> {
                    Toast.makeText(TripDetailActivity.this, "Activities synced successfully!", Toast.LENGTH_SHORT).show();
                    // Refresh data directly to update UI
                    refreshActivityDataDirectly();
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Force sync failed: " + error);
                runOnUiThread(() -> new android.app.AlertDialog.Builder(TripDetailActivity.this)
                        .setTitle("Sync Failed")
                        .setMessage("Failed to sync activities: " + error + "\n\nPlease check your internet connection and try again.")
                        .setPositiveButton("OK", null)
                        .show());
            }
        });
    }
    
    // CRITICAL FIX: Silent sync method for automatic data consistency without UI notifications
    private void forceSyncActivitiesQuiet() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        Log.d(TAG, "Quietly syncing activities for trip: " + tripId);
        
        com.example.mobiledegreefinalproject.repository.TripRepository repository = 
            com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
        
        repository.forceSyncTripActivities(tripId, new com.example.mobiledegreefinalproject.repository.TripRepository.OnTripSyncListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Quiet sync completed successfully");
                // Don't show toast, just ensure data is consistent
                if (!isFinishing() && !isDestroyed()) {
                    runOnUiThread(() -> refreshActivityDataDirectly());
                }
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "Quiet sync failed (this is normal): " + error);
                // Don't show error to user for quiet sync
            }
        });
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
            // Try to open Google Maps with the destination
            String uri = "geo:0,0?q=" + android.net.Uri.encode(destination);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri));
            mapIntent.setPackage("com.google.android.apps.maps");
            
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                // Fallback to web browser if Google Maps not available
                String webUri = "https://www.google.com/maps/search/" + android.net.Uri.encode(destination);
                Intent webIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(webUri));
                if (webIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(webIntent);
                } else {
                    Toast.makeText(this, "No map application available", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening map", e);
            Toast.makeText(this, "Error opening map: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        if (currentTrip == null) {
            return;
        }
        
        // Show loading dialog using modern AlertDialog
        android.app.AlertDialog progressDialog = new android.app.AlertDialog.Builder(this)
            .setMessage("Deleting trip...")
            .setCancelable(false)
            .create();
        progressDialog.show();
        
        viewModel.deleteTrip(currentTrip, new com.example.mobiledegreefinalproject.repository.TripRepository.OnTripOperationListener() {
            @Override
            public void onSuccess(int tripId) {
                progressDialog.dismiss();
                Toast.makeText(TripDetailActivity.this, "Trip deleted successfully", Toast.LENGTH_SHORT).show();
                
                // Return to trips list
                finish();
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
        // CRITICAL FIX: Check activity state first
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity is finishing/destroyed, skipping deletion");
            return;
        }
        
        Log.d(TAG, "Starting activity deletion for: " + activity.getTitle() + " (ID: " + activity.getId() + ", Firebase ID: " + activity.getFirebaseId() + ")");
        
        // CRITICAL FIX: If activity doesn't have Firebase ID, get fresh data from repository directly (not LiveData)
        if (activity.getFirebaseId() == null || activity.getFirebaseId().isEmpty()) {
            Log.w(TAG, "Activity missing Firebase ID, attempting to get fresh data from database");
            
            // Use executor to avoid LiveData observer leaks
            com.example.mobiledegreefinalproject.repository.TripRepository repository = 
                com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
            
            // Get fresh data from repository without LiveData
            new Thread(() -> {
                try {
                    List<TripActivity> activities = repository.getActivitiesForTripSync(tripId);
                    TripActivity updatedActivity = null;
                    
                    for (TripActivity act : activities) {
                        if (act.getTitle().equals(activity.getTitle()) && 
                            Math.abs(act.getDateTime() - activity.getDateTime()) < 60000) { // Within 1 minute
                            updatedActivity = act;
                            break;
                        }
                    }
                    
                    final TripActivity finalActivity = (updatedActivity != null && updatedActivity.getFirebaseId() != null) 
                        ? updatedActivity : activity;
                    
                    // Switch back to UI thread for deletion
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            if (finalActivity.getFirebaseId() != null && !finalActivity.getFirebaseId().equals(activity.getFirebaseId())) {
                                Log.d(TAG, "Found updated activity with Firebase ID: " + finalActivity.getFirebaseId());
                            } else {
                                Log.w(TAG, "Could not find activity with Firebase ID, proceeding with original");
                            }
                            proceedWithDeletion(finalActivity);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error getting fresh activity data", e);
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            proceedWithDeletion(activity);
                        }
                    });
                }
            }).start();
        } else {
            proceedWithDeletion(activity);
        }
    }
    
    private void proceedWithDeletion(TripActivity activity) {
        // CRITICAL FIX: Check activity state before proceeding
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity is finishing/destroyed, aborting deletion");
            return;
        }
        
        Log.d(TAG, "Proceeding with deletion for: " + activity.getTitle() + " (Firebase ID: " + activity.getFirebaseId() + ")");
        
        // Show loading dialog using modern AlertDialog
        android.app.AlertDialog progressDialog = new android.app.AlertDialog.Builder(this)
            .setMessage("Deleting activity...")
            .setCancelable(false)
            .create();
        
        // CRITICAL FIX: Only show dialog if activity is still alive
        if (!isFinishing() && !isDestroyed()) {
            progressDialog.show();
        } else {
            Log.w(TAG, "Activity destroyed, skipping dialog and deletion");
            return;
        }
        
        try {
            // CRITICAL FIX: Use repository directly instead of ViewModel to avoid LiveData observer leaks
            com.example.mobiledegreefinalproject.repository.TripRepository repository = 
                com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
            
            repository.deleteActivity(activity, new com.example.mobiledegreefinalproject.repository.TripRepository.OnActivityOperationListener() {
                @Override
                public void onSuccess(int activityId) {
                    Log.d(TAG, "Activity deletion successful: " + activityId);
                    
                    // CRITICAL FIX: Check activity state before UI operations
                    if (!isFinishing() && !isDestroyed()) {
                        try {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error dismissing progress dialog", e);
                        }
                        
                        Toast.makeText(TripDetailActivity.this, "Activity deleted successfully", Toast.LENGTH_SHORT).show();
                        
                        // CRITICAL FIX: Immediate UI refresh without using LiveData observers to prevent crashes
                        refreshActivityDataDirectly();
                        
                        // Also force a sync to ensure data consistency
                        forceSyncActivitiesQuiet();
                    } else {
                        Log.w(TAG, "Activity destroyed during success callback");
                        // Still try to dismiss dialog safely
                        try {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        } catch (Exception ignored) {}
                    }
                }
                
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Activity deletion failed: " + error);
                    
                    // CRITICAL FIX: Check activity state before UI operations
                    if (!isFinishing() && !isDestroyed()) {
                        try {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error dismissing progress dialog", e);
                        }
                        
                        // Show user-friendly error message
                        String userMessage = "Failed to delete activity";
                        if (error.contains("network") || error.contains("timeout")) {
                            userMessage = "Network error - activity may have been deleted locally";
                        }
                        
                        new android.app.AlertDialog.Builder(TripDetailActivity.this)
                                .setTitle("Delete Failed")
                                .setMessage(userMessage + "\n\nTechnical details: " + error)
                                .setPositiveButton("OK", null)
                                .show();
                        
                        // CRITICAL FIX: Refresh data even on error to show current state without using LiveData observers
                        refreshActivityDataDirectly();
                        
                        // Force sync to ensure we have correct state
                        forceSyncActivitiesQuiet();
                    } else {
                        Log.w(TAG, "Activity destroyed during error callback");
                        // Still try to dismiss dialog safely
                        try {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        } catch (Exception ignored) {}
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception during activity deletion", e);
            
            // CRITICAL FIX: Safe cleanup on exception
            if (!isFinishing() && !isDestroyed()) {
                try {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                } catch (Exception dialogError) {
                    Log.w(TAG, "Error dismissing progress dialog on exception", dialogError);
                }
                
                Toast.makeText(this, "Error deleting activity: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                loadTripData(); // Refresh to show current state
            }
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
                        loadTripData();
                    });
                }
            });
        }
    }
    
    // CRITICAL FIX: Method to refresh activity data without using LiveData observers (prevents crashes)
    private void refreshActivityDataDirectly() {
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "Activity destroyed, skipping data refresh");
            return;
        }
        
        Log.d(TAG, "Refreshing activity data directly from repository");
        
        // Use background thread to avoid blocking UI
        new Thread(() -> {
            try {
                com.example.mobiledegreefinalproject.repository.TripRepository repository = 
                    com.example.mobiledegreefinalproject.repository.TripRepository.getInstance(this);
                
                // Get fresh data from database directly
                List<TripActivity> activities = repository.getActivitiesForTripSync(tripId);
                
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Log.d(TAG, "Updating UI with " + activities.size() + " activities");
                        displayTimeline(activities);
                        
                        // Also update trip info to ensure consistency
                        if (currentTrip != null) {
                            displayTripInfo(currentTrip);
                        }
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error refreshing activity data directly", e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        // Show error to user but don't crash
                        Toast.makeText(TripDetailActivity.this, "Error refreshing data", Toast.LENGTH_SHORT).show();
                        
                        // Try fallback refresh after short delay
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                loadTripData();
                            }
                        }, 2000);
                    }
                });
            }
        }).start();
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