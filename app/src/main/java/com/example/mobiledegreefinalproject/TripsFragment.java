package com.example.mobiledegreefinalproject;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobiledegreefinalproject.adapter.TripsAdapter;
import com.example.mobiledegreefinalproject.database.Trip;
import com.example.mobiledegreefinalproject.repository.TripRepository;
import com.example.mobiledegreefinalproject.viewmodel.TripsViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class TripsFragment extends Fragment {

    private RecyclerView tripsRecyclerView;
    private FloatingActionButton fabAddTrip;
    private TextView emptyStateText;
    private TripsAdapter tripsAdapter;
    private TripsViewModel viewModel;
    
    // Info Panel Manager for non-intrusive messages
    private InfoPanelManager infoPanelManager;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trips, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        android.util.Log.d("TripsFragment", "onViewCreated called");
        
        try {
            initViews(view);
            setupViewModel();
            setupRecyclerView();
            setupClickListeners();
            observeTrips();
            android.util.Log.d("TripsFragment", "Setup completed successfully");
        } catch (Exception e) {
            android.util.Log.e("TripsFragment", "Error in onViewCreated", e);
        }
    }

    private void initViews(View view) {
        tripsRecyclerView = view.findViewById(R.id.rv_trips);
        fabAddTrip = view.findViewById(R.id.fab_add_trip);
        emptyStateText = view.findViewById(R.id.empty_state_text);
        
        // Initialize Info Panel Manager
        ViewGroup infoPanelContainer = view.findViewById(R.id.info_panel_container);
        if (infoPanelContainer != null) {
            infoPanelManager = new InfoPanelManager(getContext(), infoPanelContainer);
            infoPanelManager.showTripManagementTips();
        } else {
            android.util.Log.w("TripsFragment", "Info panel container not found");
        }
        
        // Debug: Log if views are null
        if (tripsRecyclerView == null) {
            android.util.Log.e("TripsFragment", "rv_trips not found");
        }
        if (fabAddTrip == null) {
            android.util.Log.e("TripsFragment", "fab_add_trip not found");
        }
        if (emptyStateText == null) {
            android.util.Log.e("TripsFragment", "empty_state_text not found");
        }
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(TripsViewModel.class);
    }

    private void setupRecyclerView() {
        tripsAdapter = new TripsAdapter(
            // Click listener for opening trip details
            trip -> {
                try {
                    // Navigate to trip detail
                    Intent intent = new Intent(getContext(), TripDetailActivity.class);
                    intent.putExtra("trip_id", trip.getId());
                    startActivity(intent);
                } catch (Exception e) {
                    android.util.Log.e("TripsFragment", "Error starting TripDetailActivity", e);
                    if (getContext() != null) {
                        android.widget.Toast.makeText(getContext(), 
                            "Error opening trip details", 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            },
            // Delete listener for deleting trips (when delete button is clicked)
            trip -> {
                showDeleteTripDialog(trip);
            }
        );
        
        // Add long click listener to directly show delete dialog
        tripsAdapter.setOnLongClickListener(trip -> {
            showDeleteTripDialog(trip);
            return true;
        });
        
        if (tripsRecyclerView != null) {
            tripsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            tripsRecyclerView.setAdapter(tripsAdapter);
        }
    }

    private void setupClickListeners() {
        if (fabAddTrip != null) {
            fabAddTrip.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(getContext(), AddTripActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    android.util.Log.e("TripsFragment", "Error starting AddTripActivity", e);
                    if (getContext() != null) {
                        android.widget.Toast.makeText(getContext(), 
                            "Error opening add trip screen", 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void showDeleteTripDialog(Trip trip) {
        if (getContext() == null) return;
        
        new AlertDialog.Builder(getContext())
                .setTitle("Delete Trip")
                .setMessage("Are you sure you want to delete \"" + trip.getTitle() + "\"?\n\nThis will also delete all activities for this trip. This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteTrip(trip))
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteTrip(Trip trip) {
        // Check if fragment is still in valid state
        if (getContext() == null || !isAdded() || getActivity() == null || getActivity().isFinishing()) {
            android.util.Log.w("TripsFragment", "Fragment not in valid state for delete operation");
            return;
        }
        
        // Show loading dialog
        AlertDialog progressDialog = new AlertDialog.Builder(getContext())
            .setMessage("Deleting trip...")
            .setCancelable(false)
            .create();
        progressDialog.show();
        
        viewModel.deleteTrip(trip, new TripRepository.OnTripOperationListener() {
            @Override
            public void onSuccess(int tripId) {
                // Check if fragment is still valid before updating UI
                if (!isAdded() || getActivity() == null || getActivity().isFinishing()) {
                    android.util.Log.w("TripsFragment", "Fragment destroyed during delete operation - skipping UI updates");
                    if (progressDialog != null && progressDialog.isShowing()) {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            android.util.Log.w("TripsFragment", "Error dismissing progress dialog", e);
                        }
                    }
                    return;
                }
                
                if (progressDialog != null && progressDialog.isShowing()) {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        android.util.Log.w("TripsFragment", "Error dismissing progress dialog", e);
                    }
                }
                
                if (getContext() != null) {
                    if (infoPanelManager != null) {
                        infoPanelManager.addCustomMessage("Trip \"" + trip.getTitle() + "\" deleted successfully", false);
                    }
                }
                
                // Notify MainActivity to inform other fragments about trip deletion
                android.util.Log.d("TripsFragment", "=== TRIPS FRAGMENT DELETION SUCCESS ===");
                android.util.Log.d("TripsFragment", "Trip deleted successfully, tripId: " + tripId);
                android.util.Log.d("TripsFragment", "Activity type: " + (getActivity() != null ? getActivity().getClass().getSimpleName() : "null"));
                
                if (getActivity() instanceof MainActivity) {
                    android.util.Log.d("TripsFragment", "Calling MainActivity.notifyTripDeleted(" + tripId + ")");
                    ((MainActivity) getActivity()).notifyTripDeleted(tripId);
                    android.util.Log.d("TripsFragment", "MainActivity notification sent");
                } else {
                    android.util.Log.w("TripsFragment", "Activity is not MainActivity! Cannot notify budget fragments.");
                }
                
                android.util.Log.d("TripsFragment", "Trip deleted successfully: " + trip.getTitle());
            }
            
            @Override
            public void onError(String error) {
                // Check if fragment is still valid before updating UI
                if (!isAdded() || getActivity() == null || getActivity().isFinishing()) {
                    android.util.Log.w("TripsFragment", "Fragment destroyed during delete operation - skipping error UI");
                    if (progressDialog != null && progressDialog.isShowing()) {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            android.util.Log.w("TripsFragment", "Error dismissing progress dialog", e);
                        }
                    }
                    return;
                }
                
                if (progressDialog != null && progressDialog.isShowing()) {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        android.util.Log.w("TripsFragment", "Error dismissing progress dialog", e);
                    }
                }
                
                if (getContext() != null) {
                    try {
                        new AlertDialog.Builder(getContext())
                                .setTitle("Delete Failed")
                                .setMessage("Failed to delete trip: " + error)
                                .setPositiveButton("OK", null)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show();
                    } catch (Exception e) {
                        android.util.Log.w("TripsFragment", "Error showing error dialog", e);
                    }
                }
                
                android.util.Log.e("TripsFragment", "Failed to delete trip: " + error);
            }
        });
    }

    private void observeTrips() {
        if (viewModel == null) return;
        
        viewModel.getAllTrips().observe(getViewLifecycleOwner(), trips -> {
            // Check if fragment is still in valid state
            if (!isAdded() || getActivity() == null || getActivity().isFinishing() || getContext() == null) {
                android.util.Log.w("TripsFragment", "Fragment not in valid state for trips update");
                return;
            }
            
            if (trips != null && tripsAdapter != null) {
                try {
                    tripsAdapter.submitList(trips);
                    
                    // Show/hide empty state
                    if (tripsRecyclerView != null) {
                        if (trips.isEmpty()) {
                            tripsRecyclerView.setVisibility(View.GONE);
                            if (emptyStateText != null) {
                                emptyStateText.setVisibility(View.VISIBLE);
                            }
                        } else {
                            tripsRecyclerView.setVisibility(View.VISIBLE);
                            if (emptyStateText != null) {
                                emptyStateText.setVisibility(View.GONE);
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("TripsFragment", "Error updating trips list", e);
                }
            }
        });
    }
    
    @Override
    public void onResume() {
        super.onResume();
        android.util.Log.d("TripsFragment", "onResume called - running duplicate cleanup");
        
        // Check if fragment is in valid state
        if (!isAdded() || getActivity() == null || getActivity().isFinishing()) {
            android.util.Log.w("TripsFragment", "Fragment not in valid state for cleanup");
            return;
        }
        
        // Clean up any duplicates when the fragment becomes visible
        if (viewModel != null) {
            viewModel.cleanupDuplicateTrips(new com.example.mobiledegreefinalproject.repository.TripRepository.OnTripSyncListener() {
                @Override
                public void onSuccess() {
                    android.util.Log.d("TripsFragment", "Duplicate cleanup completed successfully");
                }
                
                @Override
                public void onError(String error) {
                    android.util.Log.w("TripsFragment", "Duplicate cleanup failed: " + error);
                }
            });
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        android.util.Log.d("TripsFragment", "onDestroy called - cleaning up");
        // Clear references to prevent memory leaks
        tripsAdapter = null;
        viewModel = null;
    }
}