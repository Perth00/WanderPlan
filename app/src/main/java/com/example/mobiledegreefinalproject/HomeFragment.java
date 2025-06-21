package com.example.mobiledegreefinalproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobiledegreefinalproject.adapter.TripsAdapter;
import com.example.mobiledegreefinalproject.viewmodel.TripsViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private TextView welcomeText;
    private RecyclerView tripsRecyclerView;
    private FloatingActionButton fabAddTrip;
    private LinearLayout emptyStateLayout;
    private Button btnAddTrip;

    private TripsAdapter tripsAdapter;
    private TripsViewModel viewModel;
    
    // Info Panel Manager for non-intrusive messages
    private InfoPanelManager infoPanelManager;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupViewModel();
        setupRecyclerView();
        setupClickListeners();
        updateWelcomeMessage();
        observeTrips();
    }

    private void initViews(View view) {
        welcomeText = view.findViewById(R.id.tv_welcome);
        tripsRecyclerView = view.findViewById(R.id.rv_trips);
        fabAddTrip = view.findViewById(R.id.fab_add_trip);
        emptyStateLayout = view.findViewById(R.id.layout_empty_state);

        // Find the button in the empty state layout
        btnAddTrip = view.findViewById(R.id.btn_add_trip_empty);
        
        // Initialize Info Panel Manager
        ViewGroup infoPanelContainer = view.findViewById(R.id.info_panel_container);
        if (infoPanelContainer != null) {
            infoPanelManager = new InfoPanelManager(getContext(), infoPanelContainer);
            
            // Show appropriate tips based on user status
            if (getActivity() instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) getActivity();
                UserManager userManager = mainActivity.getUserManager();
                if (userManager != null && !userManager.isLoggedIn()) {
                    infoPanelManager.showAccountRequiredForSync();
                } else {
                    infoPanelManager.showGeneralTip("This page shows your upcoming trips. Manage trips in 'My Trips' tab.");
                }
            }
        } else {
            android.util.Log.w("HomeFragment", "Info panel container not found");
        }
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(TripsViewModel.class);
    }

    private void setupRecyclerView() {
        // Create a read-only adapter for home page (no delete functionality)
        tripsAdapter = new TripsAdapter(trip -> {
            // Navigate to trip detail
            Intent intent = new Intent(getContext(), TripDetailActivity.class);
            intent.putExtra("trip_id", trip.getId());
            startActivity(intent);
        });
        
        // Disable delete mode for home page
        tripsAdapter.setDeleteMode(false);
        
        // No long press listener for home page (read-only)
        // tripsAdapter.setOnTripLongClickListener(...); // Intentionally commented out

        tripsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        tripsRecyclerView.setAdapter(tripsAdapter);
    }

    private void setupClickListeners() {
        fabAddTrip.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), AddTripActivity.class);
            startActivity(intent);
        });

        // Set click listener for empty state button
        if (btnAddTrip != null) {
            btnAddTrip.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), AddTripActivity.class);
                startActivity(intent);
            });
        }
    }

    private void updateWelcomeMessage() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            String userName = mainActivity.getUserName();
            welcomeText.setText(getString(R.string.welcome_message, userName));
        }
    }

    private void observeTrips() {
        if (viewModel == null) return;
        
        viewModel.getAllTrips().observe(getViewLifecycleOwner(), trips -> {
            // Check if fragment is still in valid state
            if (!isAdded() || getActivity() == null || getActivity().isFinishing() || getContext() == null) {
                android.util.Log.w("HomeFragment", "Fragment not in valid state for trips update");
                return;
            }
            
            if (trips != null && tripsAdapter != null) {
                try {
                    // Filter for upcoming trips only
                    List<com.example.mobiledegreefinalproject.database.Trip> upcomingTrips = new ArrayList<>();
                    long currentTime = System.currentTimeMillis();

                    for (com.example.mobiledegreefinalproject.database.Trip trip : trips) {
                        // Show trip if end date is in the future
                        if (trip.getEndDate() >= currentTime) {
                            upcomingTrips.add(trip);
                        }
                    }

                    tripsAdapter.submitList(upcomingTrips);

                    // Show/hide empty state
                    if (tripsRecyclerView != null && emptyStateLayout != null) {
                        if (upcomingTrips.isEmpty()) {
                            tripsRecyclerView.setVisibility(View.GONE);
                            emptyStateLayout.setVisibility(View.VISIBLE);
                        } else {
                            tripsRecyclerView.setVisibility(View.VISIBLE);
                            emptyStateLayout.setVisibility(View.GONE);
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("HomeFragment", "Error updating trips list", e);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        android.util.Log.d("HomeFragment", "onResume called - running duplicate cleanup");
        
        // Check if fragment is in valid state
        if (!isAdded() || getActivity() == null || getActivity().isFinishing()) {
            android.util.Log.w("HomeFragment", "Fragment not in valid state for cleanup");
            return;
        }
        
        // Clean up any duplicates when the fragment becomes visible
        if (viewModel != null) {
            viewModel.cleanupDuplicateTrips(new com.example.mobiledegreefinalproject.repository.TripRepository.OnTripSyncListener() {
                @Override
                public void onSuccess() {
                    android.util.Log.d("HomeFragment", "Duplicate cleanup completed successfully");
                }
                
                @Override
                public void onError(String error) {
                    android.util.Log.w("HomeFragment", "Duplicate cleanup failed: " + error);
                }
            });
            
            // Refresh data when returning to home fragment
            observeTrips();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        android.util.Log.d("HomeFragment", "onDestroy called - cleaning up");
        // Clear references to prevent memory leaks
        tripsAdapter = null;
        viewModel = null;
    }
}