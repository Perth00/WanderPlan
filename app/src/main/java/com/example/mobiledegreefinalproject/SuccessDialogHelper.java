package com.example.mobiledegreefinalproject;

import android.app.AlertDialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;

public class SuccessDialogHelper {
    
    private static final String TAG = "SuccessDialogHelper";
    private static MediaPlayer successSound;
    
    /**
     * Show success animation dialog with custom message
     */
    public static void showSuccessDialog(Context context, String message) {
        showSuccessDialog(context, message, null);
    }
    
    /**
     * Show success animation dialog with custom message and callback
     */
    public static void showSuccessDialog(Context context, String message, Runnable onDismiss) {
        if (context == null) return;
        
        try {
            // Create the success animation dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_success_animation, null);
            builder.setView(dialogView);
            
            AlertDialog dialog = builder.create();
            dialog.setCancelable(true);
            
            // Set transparent background
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            
            // Find views in the dialog
            LottieAnimationView animationView = dialogView.findViewById(R.id.animation_success);
            TextView messageView = dialogView.findViewById(R.id.tv_success_message);
            
            // Set custom message
            if (messageView != null && message != null) {
                messageView.setText(message);
            }
            
            // Auto dismiss after animation completes
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (dialog != null && dialog.isShowing()) {
                        dialog.dismiss();
                        if (onDismiss != null) {
                            onDismiss.run();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error dismissing animation dialog", e);
                }
            }, 2500); // 2.5 seconds
            
            dialog.show();
            playSuccessSound(context);
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing success animation", e);
            // Fallback to simple toast
            Toast.makeText(context, "✅ " + message, Toast.LENGTH_SHORT).show();
            playSuccessSound(context);
        }
    }
    
    /**
     * Play success sound using the custom sound file
     */
    public static void playSuccessSound(Context context) {
        try {
            if (context == null) return;
            
            if (successSound != null) {
                successSound.release();
            }
            
            // Use the custom success sound from raw resources
            successSound = MediaPlayer.create(context, R.raw.success_sound);
            if (successSound != null) {
                successSound.setOnCompletionListener(mediaPlayer -> {
                    try {
                        mediaPlayer.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing MediaPlayer", e);
                    }
                });
                successSound.start();
            } else {
                Log.w(TAG, "Custom success sound not available, using default notification sound");
                // Fallback to default notification sound
                successSound = MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
                if (successSound != null) {
                    successSound.start();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing success sound", e);
            // Handle sound playback error silently
        }
    }
    
    /**
     * Release any active MediaPlayer instances
     */
    public static void releaseMediaPlayer() {
        try {
            if (successSound != null) {
                successSound.release();
                successSound = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing MediaPlayer", e);
        }
    }
} 