package com.example.mobiledegreefinalproject.database;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "trip_activities",
        foreignKeys = @ForeignKey(entity = Trip.class,
                                parentColumns = "id",
                                childColumns = "tripId",
                                onDelete = ForeignKey.CASCADE),
        indices = {@androidx.room.Index(value = "tripId")})
public class TripActivity {
    @PrimaryKey(autoGenerate = true)
    private int id;

    private int tripId; // Foreign key to Trip
    private String firebaseId; // For Firebase sync
    private String title;
    private String description;
    private String location;
    private long dateTime; // Timestamp for when activity occurs
    private int dayNumber; // Which day of the trip (1, 2, 3, etc.)
    private String timeString; // Human readable time like "09:00 AM"
    private String imageUrl; // Local or Firebase Storage URL
    private String imageLocalPath; // Local file path for offline images
    private double latitude;
    private double longitude;
    private long createdAt;
    private long updatedAt;
    private boolean synced;

    // Constructors
    public TripActivity() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.synced = false;
    }

    @androidx.room.Ignore
    public TripActivity(int tripId, String title, String description, long dateTime, int dayNumber) {
        this();
        this.tripId = tripId;
        this.title = title;
        this.description = description;
        this.dateTime = dateTime;
        this.dayNumber = dayNumber;
        this.timeString = formatTime(dateTime);
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTripId() {
        return tripId;
    }

    public void setTripId(int tripId) {
        this.tripId = tripId;
    }

    public String getFirebaseId() {
        return firebaseId;
    }

    public void setFirebaseId(String firebaseId) {
        this.firebaseId = firebaseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public long getDateTime() {
        return dateTime;
    }

    public void setDateTime(long dateTime) {
        this.dateTime = dateTime;
        this.timeString = formatTime(dateTime);
        this.updatedAt = System.currentTimeMillis();
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(int dayNumber) {
        this.dayNumber = dayNumber;
    }

    public String getTimeString() {
        return timeString;
    }

    public void setTimeString(String timeString) {
        this.timeString = timeString;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageLocalPath() {
        return imageLocalPath;
    }

    public void setImageLocalPath(String imageLocalPath) {
        this.imageLocalPath = imageLocalPath;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    // Helper methods
    private String formatTime(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }

    public String getFormattedDate() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(dateTime));
    }

    public boolean hasImage() {
        return (imageUrl != null && !imageUrl.isEmpty()) || (imageLocalPath != null && !imageLocalPath.isEmpty());
    }

    public String getDisplayImagePath() {
        if (imageUrl != null && !imageUrl.isEmpty()) {
            return imageUrl;
        }
        return imageLocalPath;
    }
}