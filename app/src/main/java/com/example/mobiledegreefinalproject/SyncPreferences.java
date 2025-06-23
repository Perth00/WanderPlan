package com.example.mobiledegreefinalproject;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Utility class to manage sync preferences and settings
 */
public class SyncPreferences {
    private static final String PREFS_NAME = "SyncPreferences";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";
    private static final String KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled";
    private static final String KEY_SYNC_ON_LOGIN = "sync_on_login";
    private static final String KEY_JSON_SYNC_VERSION = "json_sync_version";
    private static final String KEY_LAST_SYNC_STATUS = "last_sync_status";
    private static final String KEY_TRIPS_SYNCED_COUNT = "trips_synced_count";
    private static final String KEY_ACTIVITIES_SYNCED_COUNT = "activities_synced_count";
    
    private final SharedPreferences prefs;
    
    public SyncPreferences(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    // Last sync time
    public void setLastSyncTime(long timestamp) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, timestamp).apply();
    }
    
    public long getLastSyncTime() {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0);
    }
    
    public String getLastSyncTimeFormatted() {
        long timestamp = getLastSyncTime();
        if (timestamp == 0) {
            return "Never";
        }
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
            "MMM dd, yyyy 'at' HH:mm", 
            java.util.Locale.getDefault()
        );
        return sdf.format(new java.util.Date(timestamp));
    }
    
    // Auto sync settings
    public void setAutoSyncEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply();
    }
    
    public boolean isAutoSyncEnabled() {
        return prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true); // Default to enabled
    }
    
    // Sync on login setting
    public void setSyncOnLogin(boolean enabled) {
        prefs.edit().putBoolean(KEY_SYNC_ON_LOGIN, enabled).apply();
    }
    
    public boolean shouldSyncOnLogin() {
        return prefs.getBoolean(KEY_SYNC_ON_LOGIN, true); // Default to enabled
    }
    
    // JSON sync version
    public void setJsonSyncVersion(String version) {
        prefs.edit().putString(KEY_JSON_SYNC_VERSION, version).apply();
    }
    
    public String getJsonSyncVersion() {
        return prefs.getString(KEY_JSON_SYNC_VERSION, "1.0");
    }
    
    // Last sync status
    public void setLastSyncStatus(SyncStatus status) {
        prefs.edit().putString(KEY_LAST_SYNC_STATUS, status.name()).apply();
    }
    
    public SyncStatus getLastSyncStatus() {
        String statusStr = prefs.getString(KEY_LAST_SYNC_STATUS, SyncStatus.NEVER.name());
        try {
            return SyncStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return SyncStatus.NEVER;
        }
    }
    
    // Sync counts
    public void setLastSyncCounts(int trips, int activities) {
        prefs.edit()
            .putInt(KEY_TRIPS_SYNCED_COUNT, trips)
            .putInt(KEY_ACTIVITIES_SYNCED_COUNT, activities)
            .apply();
    }
    
    public int getLastTripsCount() {
        return prefs.getInt(KEY_TRIPS_SYNCED_COUNT, 0);
    }
    
    public int getLastActivitiesCount() {
        return prefs.getInt(KEY_ACTIVITIES_SYNCED_COUNT, 0);
    }
    
    // Complete sync success record
    public void recordSuccessfulSync(int trips, int activities) {
        setLastSyncTime(System.currentTimeMillis());
        setLastSyncStatus(SyncStatus.SUCCESS);
        setLastSyncCounts(trips, activities);
    }
    
    // Record sync failure
    public void recordFailedSync() {
        setLastSyncTime(System.currentTimeMillis());
        setLastSyncStatus(SyncStatus.FAILED);
    }
    
    // Check if sync is needed (based on time threshold)
    public boolean isSyncNeeded() {
        if (!isAutoSyncEnabled()) {
            return false;
        }
        
        long lastSync = getLastSyncTime();
        long now = System.currentTimeMillis();
        long hoursSinceLastSync = (now - lastSync) / (1000 * 60 * 60);
        
        // Sync if more than 24 hours since last sync, or if last sync failed
        return hoursSinceLastSync > 24 || getLastSyncStatus() == SyncStatus.FAILED;
    }
    
    // Get sync summary for UI display
    public String getSyncSummary() {
        SyncStatus status = getLastSyncStatus();
        
        switch (status) {
            case SUCCESS:
                return String.format("✅ Last sync: %s\n🧳 Trips: %d | 📝 Activities: %d", 
                    getLastSyncTimeFormatted(), 
                    getLastTripsCount(), 
                    getLastActivitiesCount());
                    
            case FAILED:
                return String.format("❌ Last sync failed: %s", getLastSyncTimeFormatted());
                
            case IN_PROGRESS:
                return "🔄 Sync in progress...";
                
            case NEVER:
            default:
                return "ℹ️ No sync performed yet";
        }
    }
    
    // Clear all sync data (for logout)
    public void clearSyncData() {
        prefs.edit()
            .remove(KEY_LAST_SYNC_TIME)
            .remove(KEY_LAST_SYNC_STATUS)
            .remove(KEY_TRIPS_SYNCED_COUNT)
            .remove(KEY_ACTIVITIES_SYNCED_COUNT)
            .apply();
    }
    
    public enum SyncStatus {
        NEVER,
        IN_PROGRESS,
        SUCCESS,
        FAILED
    }
} 