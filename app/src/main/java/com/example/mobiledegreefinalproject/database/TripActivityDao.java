package com.example.mobiledegreefinalproject.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TripActivityDao {
    
    @Insert
    long insertActivity(TripActivity activity);
    
    @Update
    void updateActivity(TripActivity activity);
    
    @Delete
    void deleteActivity(TripActivity activity);
    
    @Query("SELECT * FROM trip_activities WHERE tripId = :tripId ORDER BY dayNumber ASC, dateTime ASC")
    LiveData<List<TripActivity>> getActivitiesForTrip(int tripId);
    
    @Query("SELECT * FROM trip_activities WHERE tripId = :tripId ORDER BY dayNumber ASC, dateTime ASC")
    List<TripActivity> getActivitiesForTripSync(int tripId);
    
    @Query("SELECT * FROM trip_activities WHERE tripId = :tripId AND dayNumber = :dayNumber ORDER BY dateTime ASC")
    LiveData<List<TripActivity>> getActivitiesForDay(int tripId, int dayNumber);
    
    @Query("SELECT * FROM trip_activities WHERE tripId = :tripId AND dayNumber = :dayNumber ORDER BY dateTime ASC")
    List<TripActivity> getActivitiesForDaySync(int tripId, int dayNumber);
    
    @Query("SELECT * FROM trip_activities WHERE id = :activityId")
    LiveData<TripActivity> getActivityById(int activityId);
    
    @Query("SELECT * FROM trip_activities WHERE id = :activityId")
    TripActivity getActivityByIdSync(int activityId);
    
    @Query("SELECT * FROM trip_activities WHERE firebaseId = :firebaseId")
    TripActivity getActivityByFirebaseId(String firebaseId);
    
    @Query("SELECT * FROM trip_activities WHERE synced = 0")
    List<TripActivity> getUnsyncedActivities();
    
    @Query("UPDATE trip_activities SET synced = 1 WHERE id = :activityId")
    void markActivityAsSynced(int activityId);
    
    @Query("UPDATE trip_activities SET firebaseId = :firebaseId, synced = 1 WHERE id = :activityId")
    void updateActivityFirebaseId(int activityId, String firebaseId);
    
    @Query("DELETE FROM trip_activities WHERE id = :activityId")
    void deleteActivityById(int activityId);
    
    @Query("DELETE FROM trip_activities WHERE tripId = :tripId")
    void deleteAllActivitiesForTrip(int tripId);
    
    @Query("SELECT COUNT(*) FROM trip_activities WHERE tripId = :tripId")
    int getActivityCountForTrip(int tripId);
    
    @Query("SELECT DISTINCT dayNumber FROM trip_activities WHERE tripId = :tripId ORDER BY dayNumber ASC")
    List<Integer> getDaysWithActivities(int tripId);

    @Query("SELECT * FROM trip_activities ORDER BY tripId ASC, dayNumber ASC, dateTime ASC")
    List<TripActivity> getAllActivitiesSync();
} 