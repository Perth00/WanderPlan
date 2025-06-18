package com.example.mobiledegreefinalproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class TripsFragment extends Fragment {

    private RecyclerView tripsRecyclerView;
    private FloatingActionButton fabAddTrip;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trips, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupRecyclerView();
        setupClickListeners();
    }

    private void initViews(View view) {
        tripsRecyclerView = view.findViewById(R.id.rv_trips);
        fabAddTrip = view.findViewById(R.id.fab_add_trip);
    }

    private void setupRecyclerView() {
        // TODO: Setup trips adapter
        // tripsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // tripsRecyclerView.setAdapter(tripsAdapter);
    }

    private void setupClickListeners() {
        fabAddTrip.setOnClickListener(v -> {
            // TODO: Navigate to add trip activity
            if (getContext() != null) {
                android.widget.Toast.makeText(getContext(), 
                    "Add Trip functionality coming soon!", 
                    android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }
} 