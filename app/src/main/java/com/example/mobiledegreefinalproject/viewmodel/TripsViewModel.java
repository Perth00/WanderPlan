package com.example.mobiledegreefinalproject.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.mobiledegreefinalproject.database.Trip;
import com.example.mobiledegreefinalproject.database.TripActivity;
import com.example.mobiledegreefinalproject.repository.TripRepository;

import java.util.List;

public class TripsViewModel extends AndroidViewModel {
    
    private final TripRepository repository;
    
    public TripsViewModel(@NonNull Application application) {
        super(application);
        repository = TripRepository.getInstance(application);
    }
    
    // Trip operations
    public LiveData<List<Trip>> getAllTrips() {
        return repository.getAllTrips();
    }
    
    public LiveData<Trip> getTripById(int tripId) {
        return repository.getTripById(tripId);
    }
    
    public void insertTrip(Trip trip, TripRepository.OnTripOperationListener listener) {
        repository.insertTrip(trip, listener);
    }
    
    public void updateTrip(Trip trip, TripRepository.OnTripOperationListener listener) {
        repository.updateTrip(trip, listener);
    }
    
    public void deleteTrip(Trip trip, TripRepository.OnTripOperationListener listener) {
        repository.deleteTrip(trip, listener);
    }
    
    // Activity operations
    public LiveData<List<TripActivity>> getActivitiesForTrip(int tripId) {
        return repository.getActivitiesForTrip(tripId);
    }
    
    public LiveData<List<TripActivity>> getActivitiesForDay(int tripId, int dayNumber) {
        return repository.getActivitiesForDay(tripId, dayNumber);
    }
    
    public void insertActivity(TripActivity activity, TripRepository.OnActivityOperationListener listener) {
        repository.insertActivity(activity, listener);
    }
    
    public void updateActivity(TripActivity activity, TripRepository.OnActivityOperationListener listener) {
        repository.updateActivity(activity, listener);
    }
    
    public void deleteActivity(TripActivity activity, TripRepository.OnActivityOperationListener listener) {
        repository.deleteActivity(activity, listener);
    }
} 