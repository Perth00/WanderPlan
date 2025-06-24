package com.example.mobiledegreefinalproject;

import android.app.AlertDialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;

public class DeleteDialogHelper {
    
    private static final String TAG = "DeleteDialogHelper";
    private static MediaPlayer deleteSound;
    
    /**
     * Show delete confirmation dialog with animation
     */
    public static void showDeleteDialog(Context context, String title, String message, 
                                      Runnable onConfirmDelete, Runnable onCancel) {
        if (context == null) return;
        
        try {
            // Create the delete confirmation dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_delete_confirmation, null);
            builder.setView(dialogView);
            
            AlertDialog dialog = builder.create();
            dialog.setCancelable(false); // Prevent dismissing during decision
            dialog.setCanceledOnTouchOutside(false); // Prevent dismissing by tapping outside
            
            // Set transparent background
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            
            // Find views in the dialog
            LottieAnimationView animationView = dialogView.findViewById(R.id.animation_delete);
            TextView titleView = dialogView.findViewById(R.id.tv_delete_title);
            TextView messageView = dialogView.findViewById(R.id.tv_delete_message);
            Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
            Button deleteButton = dialogView.findViewById(R.id.btn_delete);
            
            // Set custom title and message
            if (titleView != null && title != null) {
                titleView.setText(title);
            }
            if (messageView != null && message != null) {
                messageView.setText(message);
            }
            
            // Set up button click listeners
            if (cancelButton != null) {
                cancelButton.setOnClickListener(v -> {
                    try {
                        dialog.dismiss();
                        if (onCancel != null) {
                            onCancel.run();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in cancel button click", e);
                    }
                });
            }
            
            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    try {
                        // Play delete sound before confirming
                        playDeleteSound(context);
                        
                        // Disable buttons to prevent double-click
                        deleteButton.setEnabled(false);
                        cancelButton.setEnabled(false);
                        
                        // Show deleting state
                        deleteButton.setText("Deleting...");
                        
                        // Dismiss dialog and execute delete action
                        dialog.dismiss();
                        
                        if (onConfirmDelete != null) {
                            onConfirmDelete.run();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in delete button click", e);
                        dialog.dismiss();
                        if (onConfirmDelete != null) {
                            onConfirmDelete.run();
                        }
                    }
                });
            }
            
            dialog.show();
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing delete confirmation dialog", e);
            // Fallback to simple confirmation dialog
            showSimpleFallbackDialog(context, title, message, onConfirmDelete, onCancel);
        }
    }
    
    /**
     * Fallback simple delete dialog if main dialog fails
     */
    private static void showSimpleFallbackDialog(Context context, String title, String message, 
                                               Runnable onConfirmDelete, Runnable onCancel) {
        try {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(title != null ? title : "Delete Item")
                .setMessage(message != null ? message : "Are you sure you want to delete this item?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    playDeleteSound(context);
                    if (onConfirmDelete != null) {
                        onConfirmDelete.run();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    if (onCancel != null) {
                        onCancel.run();
                    }
                })
                .setIcon(R.drawable.ic_delete)
                .setCancelable(false)
                .show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing fallback dialog", e);
            // Final fallback - just execute the delete
            Toast.makeText(context, "⚠️ " + (message != null ? message : "Deleting item..."), Toast.LENGTH_SHORT).show();
            if (onConfirmDelete != null) {
                onConfirmDelete.run();
            }
        }
    }
    
    /**
     * Play delete sound using system notification sound
     */
    public static void playDeleteSound(Context context) {
        try {
            if (context == null) return;
            
            if (deleteSound != null) {
                deleteSound.release();
            }
            
            // Use system notification sound for delete (more subtle than success sound)
            deleteSound = MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
            if (deleteSound != null) {
                deleteSound.setOnCompletionListener(mediaPlayer -> {
                    try {
                        mediaPlayer.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing MediaPlayer", e);
                    }
                });
                // Play at lower volume for delete action
                deleteSound.setVolume(0.3f, 0.3f);
                deleteSound.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing delete sound", e);
            // Handle sound playback error silently
        }
    }
    
    /**
     * Release any active MediaPlayer instances
     */
    public static void releaseMediaPlayer() {
        try {
            if (deleteSound != null) {
                deleteSound.release();
                deleteSound = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing MediaPlayer", e);
        }
    }
} 