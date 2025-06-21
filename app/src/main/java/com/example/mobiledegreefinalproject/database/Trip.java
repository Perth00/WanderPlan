package com.example.mobiledegreefinalproject.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trips")
public class Trip {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String firebaseId; // For Firebase sync
    private String title;
    private String destination;
    private long startDate; // Timestamp
    private long endDate; // Timestamp
    private String mapImageUrl; // Static map or location reference
    private double latitude;
    private double longitude;
    private long createdAt;
    private long updatedAt;
    private boolean synced; // For offline/online sync tracking

    // Constructors
    public Trip() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.synced = false;
    }

    @androidx.room.Ignore
    public Trip(String title, String destination, long startDate, long endDate) {
        this();
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Trip title cannot be null or empty");
        }
        if (destination == null || destination.trim().isEmpty()) {
            throw new IllegalArgumentException("Trip destination cannot be null or empty");
        }
        if (startDate <= 0) {
            throw new IllegalArgumentException("Start date must be a valid timestamp");
        }
        if (endDate <= 0) {
            throw new IllegalArgumentException("End date must be a valid timestamp");
        }
        if (endDate < startDate) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        
        this.title = title.trim();
        this.destination = destination.trim();
        this.startDate = startDate;
        this.endDate = endDate;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFirebaseId() { return firebaseId; }
    public void setFirebaseId(String firebaseId) { this.firebaseId = firebaseId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { 
        this.title = title; 
        this.updatedAt = System.currentTimeMillis();
    }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { 
        this.destination = destination; 
        this.updatedAt = System.currentTimeMillis();
    }

    public long getStartDate() { return startDate; }
    public void setStartDate(long startDate) { 
        this.startDate = startDate; 
        this.updatedAt = System.currentTimeMillis();
    }

    public long getEndDate() { return endDate; }
    public void setEndDate(long endDate) { 
        this.endDate = endDate; 
        this.updatedAt = System.currentTimeMillis();
    }

    public String getMapImageUrl() { return mapImageUrl; }
    public void setMapImageUrl(String mapImageUrl) { this.mapImageUrl = mapImageUrl; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public boolean isSynced() { return synced; }
    public void setSynced(boolean synced) { this.synced = synced; }

    // Helper methods
    public int getDurationDays() {
        return (int) ((endDate - startDate) / (24 * 60 * 60 * 1000)) + 1;
    }

    public String getDateRange() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(startDate)) + " - " + sdf.format(new java.util.Date(endDate));
    }
}