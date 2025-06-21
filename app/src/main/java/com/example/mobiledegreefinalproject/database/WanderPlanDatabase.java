package com.example.mobiledegreefinalproject.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
    entities = {Trip.class, TripActivity.class, User.class},
    version = 3,
    exportSchema = false
)
public abstract class WanderPlanDatabase extends RoomDatabase {
    
    private static volatile WanderPlanDatabase INSTANCE;
    
    public abstract TripDao tripDao();
    public abstract TripActivityDao tripActivityDao();
    public abstract UserDao userDao();
    
    public static WanderPlanDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (WanderPlanDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        WanderPlanDatabase.class,
                        "wanderplan_database"
                    )
                    .fallbackToDestructiveMigration() // Allow destructive migration for development
                    .build();
                }
            }
        }
        return INSTANCE;
    }
} 