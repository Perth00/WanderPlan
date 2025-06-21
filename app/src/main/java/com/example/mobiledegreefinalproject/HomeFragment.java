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
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(TripsViewModel.class);
    }

    private void setupRecyclerView() {
        tripsAdapter = new TripsAdapter(trip -> {
            // Navigate to trip detail
            Intent intent = new Intent(getContext(), TripDetailActivity.class);
            intent.putExtra("trip_id", trip.getId());
            startActivity(intent);
        });

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
        viewModel.getAllTrips().observe(getViewLifecycleOwner(), trips -> {
            if (trips != null) {
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
                if (upcomingTrips.isEmpty()) {
                    tripsRecyclerView.setVisibility(View.GONE);
                    emptyStateLayout.setVisibility(View.VISIBLE);
                } else {
                    tripsRecyclerView.setVisibility(View.VISIBLE);
                    emptyStateLayout.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh data when returning to home fragment
        if (viewModel != null) {
            observeTrips();
        }
    }
}