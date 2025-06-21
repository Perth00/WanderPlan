package com.example.mobiledegreefinalproject;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Manager for handling the expandable info panel that provides
 * non-intrusive tips and messages to users
 */
public class InfoPanelManager {
    
    private static final String TAG = "InfoPanelManager";
    
    private Context context;
    private ViewGroup infoPanelContainer;
    private LinearLayout infoStripHeader;
    private LinearLayout infoPanelContent;
    private ImageView infoArrow;
    private TextView infoStripText;
    
    // Content TextViews
    private TextView tripTips;
    private TextView activityTips;
    private TextView syncStatus;
    private TextView generalTips;
    
    private boolean isExpanded = false;
    
    public InfoPanelManager(Context context, ViewGroup infoPanelContainer) {
        this.context = context;
        this.infoPanelContainer = infoPanelContainer;
        initViews();
        setupClickListeners();
    }
    
    private void initViews() {
        try {
            infoStripHeader = infoPanelContainer.findViewById(R.id.info_strip_header);
            infoPanelContent = infoPanelContainer.findViewById(R.id.info_panel_content);
            infoArrow = infoPanelContainer.findViewById(R.id.iv_info_arrow);
            infoStripText = infoPanelContainer.findViewById(R.id.tv_info_strip_text);
            
            // Content views
            tripTips = infoPanelContainer.findViewById(R.id.tv_trip_tips);
            activityTips = infoPanelContainer.findViewById(R.id.tv_activity_tips);
            syncStatus = infoPanelContainer.findViewById(R.id.tv_sync_status);
            generalTips = infoPanelContainer.findViewById(R.id.tv_general_tips);
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error initializing info panel views", e);
        }
    }
    
    private void setupClickListeners() {
        if (infoStripHeader != null) {
            infoStripHeader.setOnClickListener(v -> toggleInfoPanel());
        }
    }
    
    private void toggleInfoPanel() {
        if (infoPanelContent == null || infoArrow == null) return;
        
        try {
            if (isExpanded) {
                // Collapse
                collapsePanel();
            } else {
                // Expand
                expandPanel();
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error toggling info panel", e);
        }
    }
    
    private void expandPanel() {
        if (infoPanelContent == null || infoArrow == null) return;
        
        infoPanelContent.setVisibility(View.VISIBLE);
        
        // Animate arrow rotation
        infoArrow.animate()
                .rotation(90f)
                .setDuration(200)
                .start();
        
        // Animate panel expansion
        try {
            Animation slideDown = AnimationUtils.loadAnimation(context, R.anim.slide_up);
            slideDown.setDuration(250);
            infoPanelContent.startAnimation(slideDown);
        } catch (Exception e) {
            android.util.Log.w(TAG, "Animation error during expand", e);
        }
        
        isExpanded = true;
        updateInfoStripText("👆 Tap to hide tips");
    }
    
    private void collapsePanel() {
        if (infoPanelContent == null || infoArrow == null) return;
        
        // Animate arrow rotation
        infoArrow.animate()
                .rotation(270f)
                .setDuration(200)
                .start();
        
        // Animate panel collapse
        try {
            Animation fadeOut = AnimationUtils.loadAnimation(context, R.anim.fade_in);
            fadeOut.setDuration(200);
            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}
                
                @Override
                public void onAnimationEnd(Animation animation) {
                    if (infoPanelContent != null) {
                        infoPanelContent.setVisibility(View.GONE);
                    }
                }
                
                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            infoPanelContent.startAnimation(fadeOut);
        } catch (Exception e) {
            android.util.Log.w(TAG, "Animation error during collapse", e);
            // Fallback without animation
            infoPanelContent.setVisibility(View.GONE);
        }
        
        isExpanded = false;
        updateInfoStripText("💡 Tap for tips and sync info");
    }
    
    private void updateInfoStripText(String text) {
        if (infoStripText != null) {
            infoStripText.setText(text);
        }
    }
    
    // Methods to show different types of content
    
    public void showTripManagementTips() {
        if (tripTips != null) {
            // Add emoji prefix and set proper color for trip tips
            String tipsText = "🗺️ Long press a trip in 'My Trips' to delete it\n" +
                             "🏠 Home shows upcoming trips (view-only)\n" +
                             "🔄 Sync requires a registered account";
            tripTips.setText(tipsText);
            
            // Set proper color for trip tips
            try {
                tripTips.setTextColor(context.getResources().getColor(R.color.primary, context.getTheme()));
            } catch (Exception e) {
                android.util.Log.w(TAG, "Error setting trip tips color", e);
                // Fallback to default
                tripTips.setTextColor(context.getResources().getColor(R.color.primary));
            }
        }
        
        showContent(tripTips, true);
        updateInfoStripText("💡 Trip management tips available");
    }
    
    public void showActivityManagementTips() {
        if (activityTips != null) {
            // Add emoji prefix and set proper color for activity tips
            String tipsText = "🎯 Long press an activity to delete it\n" +
                             "🔄 New activities may need a refresh to appear\n" +
                             "☁️ All changes sync automatically with your account";
            activityTips.setText(tipsText);
            
            // Set proper color for activity tips
            try {
                activityTips.setTextColor(context.getResources().getColor(R.color.primary, context.getTheme()));
            } catch (Exception e) {
                android.util.Log.w(TAG, "Error setting activity tips color", e);
                // Fallback to default
                activityTips.setTextColor(context.getResources().getColor(R.color.primary));
            }
        }
        
        showContent(activityTips, true);
        updateInfoStripText("💡 Activity management tips available");
    }
    
    public void updateSyncStatus(String status, boolean isSuccess) {
        if (syncStatus != null) {
            String prefix = isSuccess ? "📡 " : "⚠️ ";
            syncStatus.setText(prefix + "Sync Status: " + status);
            
            // Set color based on status
            try {
                int colorRes = isSuccess ? R.color.success : R.color.warning;
                syncStatus.setTextColor(context.getResources().getColor(colorRes, context.getTheme()));
            } catch (Exception e) {
                android.util.Log.w(TAG, "Error setting sync status color", e);
            }
            
            showContent(syncStatus, true);
            updateInfoStripText("Sync status updated");
        }
    }
    
    public void showGeneralTip(String tip) {
        if (generalTips != null) {
            generalTips.setText("💡 " + tip);
            
            // Set proper color for tips - using primary blue like other panels
            try {
                generalTips.setTextColor(context.getResources().getColor(R.color.primary, context.getTheme()));
            } catch (Exception e) {
                android.util.Log.w(TAG, "Error setting general tip color", e);
                // Fallback to default
                generalTips.setTextColor(context.getResources().getColor(R.color.primary));
            }
            
            showContent(generalTips, true);
            updateInfoStripText("💡 New tip available");
        }
    }
    
    public void addCustomMessage(String message, boolean isImportant) {
        // For custom messages, we'll use the general tips area
        if (generalTips != null) {
            String prefix = isImportant ? "⚠️ " : "ℹ️ ";
            generalTips.setText(prefix + message);
            
            // Set color based on importance - use primary blue for consistency, except warnings
            try {
                int colorRes = isImportant ? R.color.warning : R.color.primary;
                generalTips.setTextColor(context.getResources().getColor(colorRes, context.getTheme()));
            } catch (Exception e) {
                android.util.Log.w(TAG, "Error setting custom message color", e);
                // Fallback to default
                generalTips.setTextColor(context.getResources().getColor(R.color.primary));
            }
            
            showContent(generalTips, true);
            updateInfoStripText(isImportant ? "⚠️ Important info available" : "ℹ️ Info available");
        }
    }
    
    private void showContent(TextView contentView, boolean show) {
        if (contentView != null) {
            contentView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
    
    // Convenience methods for common scenarios
    
    public void showSyncSuccess() {
        updateSyncStatus("All data up to date", true);
    }
    
    public void showSyncInProgress() {
        updateSyncStatus("Syncing data...", true);
    }
    
    public void showSyncError(String error) {
        updateSyncStatus("Sync error: " + error, false);
    }
    
    public void showActivityDeletedMessage() {
        addCustomMessage("Activity deleted. Data refreshed automatically.", false);
    }
    
    public void showTripDeletedMessage() {
        addCustomMessage("Trip deleted successfully. Returning to trip list.", false);
    }
    
    public void showAccountRequiredForSync() {
        showGeneralTip("Create an account to sync your trips across devices");
    }
    
    public void showActivityRefreshTip() {
        addCustomMessage("New activities may need a refresh to appear", false);
    }
    
    public void showDeleteInstructionForTrip() {
        addCustomMessage("To delete a trip, go to 'My Trips' and long press the trip", false);
    }
    
    // Reset and cleanup methods
    
    public void hideAllContent() {
        showContent(tripTips, false);
        showContent(activityTips, false);
        showContent(syncStatus, false);
        showContent(generalTips, false);
        updateInfoStripText("💡 Tap for tips and sync info");
    }
    
    public void reset() {
        hideAllContent();
        if (isExpanded) {
            collapsePanel();
        }
    }
    
    public boolean isExpanded() {
        return isExpanded;
    }
    
    public void setVisible(boolean visible) {
        if (infoPanelContainer != null) {
            infoPanelContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
} 