package com.example.mobiledegreefinalproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public class TripsFragment extends Fragment {

    private RecyclerView tripsRecyclerView;
    private FloatingActionButton fabAddTrip;
    private TextView emptyStateText;
    private TripsAdapter tripsAdapter;
    private TripsViewModel viewModel;

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
        tripsAdapter = new TripsAdapter(trip -> {
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

    private void observeTrips() {
        viewModel.getAllTrips().observe(getViewLifecycleOwner(), trips -> {
            if (trips != null) {
                tripsAdapter.submitList(trips);
                
                // Show/hide empty state
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
        });
    }
}