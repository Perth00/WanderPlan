package com.example.mobiledegreefinalproject.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TripDao {
    
    @Insert
    long insertTrip(Trip trip);
    
    @Update
    void updateTrip(Trip trip);
    
    @Delete
    void deleteTrip(Trip trip);
    
    @Query("SELECT * FROM trips ORDER BY startDate ASC")
    LiveData<List<Trip>> getAllTrips();
    
    @Query("SELECT * FROM trips ORDER BY startDate ASC")
    List<Trip> getAllTripsSync();
    
    @Query("SELECT * FROM trips WHERE id = :tripId")
    LiveData<Trip> getTripById(int tripId);
    
    @Query("SELECT * FROM trips WHERE id = :tripId")
    Trip getTripByIdSync(int tripId);
    
    @Query("SELECT * FROM trips WHERE firebaseId = :firebaseId")
    Trip getTripByFirebaseId(String firebaseId);
    
    @Query("SELECT * FROM trips WHERE synced = 0")
    List<Trip> getUnsyncedTrips();
    
    @Query("UPDATE trips SET synced = 1 WHERE id = :tripId")
    void markTripAsSynced(int tripId);
    
    @Query("UPDATE trips SET firebaseId = :firebaseId, synced = 1 WHERE id = :tripId")
    void updateTripFirebaseId(int tripId, String firebaseId);
    
    @Query("DELETE FROM trips WHERE id = :tripId")
    void deleteTripById(int tripId);
    
    @Query("SELECT COUNT(*) FROM trips")
    int getTripCount();
} 