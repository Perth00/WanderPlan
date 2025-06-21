package com.example.mobiledegreefinalproject.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "users")
public class User {
    
    @PrimaryKey
    @NonNull
    private String userId;
    
    private String name;
    private String email;
    private String profileImageUrl;
    private String profileImageLocalPath;
    private long lastUpdated;
    private boolean isCurrentUser;
    
    public User(@NonNull String userId) {
        this.userId = userId;
        this.lastUpdated = System.currentTimeMillis();
        this.isCurrentUser = false;
    }
    
    // Getters and Setters
    @NonNull
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public String getProfileImageUrl() {
        return profileImageUrl;
    }
    
    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public String getProfileImageLocalPath() {
        return profileImageLocalPath;
    }
    
    public void setProfileImageLocalPath(String profileImageLocalPath) {
        this.profileImageLocalPath = profileImageLocalPath;
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public boolean isCurrentUser() {
        return isCurrentUser;
    }
    
    public void setCurrentUser(boolean currentUser) {
        isCurrentUser = currentUser;
        this.lastUpdated = System.currentTimeMillis();
    }
} 