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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private TextView welcomeText;
    private RecyclerView tripsRecyclerView;
    private FloatingActionButton fabAddTrip;
    
    // TODO: Replace with actual trip adapter
    // private TripAdapter tripAdapter;
    // private List<Trip> trips;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupRecyclerView();
        setupClickListeners();
        updateWelcomeMessage();
    }

    private void initViews(View view) {
        welcomeText = view.findViewById(R.id.tv_welcome);
        tripsRecyclerView = view.findViewById(R.id.rv_trips);
        fabAddTrip = view.findViewById(R.id.fab_add_trip);
    }

    private void setupRecyclerView() {
        tripsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        // TODO: Initialize with actual trip data
        // trips = new ArrayList<>();
        // tripAdapter = new TripAdapter(trips, this::onTripClick);
        // tripsRecyclerView.setAdapter(tripAdapter);
        
        // For now, hide the RecyclerView since we don't have data yet
        tripsRecyclerView.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        fabAddTrip.setOnClickListener(v -> {
            // TODO: Navigate to AddTripActivity
            // Intent intent = new Intent(getActivity(), AddTripActivity.class);
            // startActivity(intent);
            
            // For now, show a simple message
            if (getContext() != null) {
                android.widget.Toast.makeText(getContext(), 
                    "Add Trip functionality coming soon!", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateWelcomeMessage() {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            String userName = mainActivity.getUserName();
            welcomeText.setText(getString(R.string.welcome_message, userName));
        }
    }

    private void onTripClick(/* Trip trip */) {
        // TODO: Navigate to trip details
        // Intent intent = new Intent(getActivity(), TripDetailsActivity.class);
        // intent.putExtra("trip_id", trip.getId());
        // startActivity(intent);
    }
} 