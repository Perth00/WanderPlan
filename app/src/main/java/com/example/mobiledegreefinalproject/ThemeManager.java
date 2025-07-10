package com.example.mobiledegreefinalproject;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.view.Window;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

public class ThemeManager {
    private static final String PREFS_NAME = "ThemePrefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_COLOR_THEME = "color_theme";

    public static final String THEME_LIGHT = "Light";
    public static final String THEME_DARK = "Dark";
    public static final String THEME_SYSTEM = "System";

    public static final String COLOR_PINK = "PastelPink";
    public static final String COLOR_GREEN = "ForestGreen";
    public static final String COLOR_RED = "Red";
    public static final String COLOR_BLUE = "Blue";
    public static final String COLOR_LIGHT_BLUE = "LightBlue";

    public static final String THEME_PASTEL_PINK = "PastelPink";
    public static final String THEME_FOREST_GREEN = "ForestGreen";
    public static final String THEME_RED = "Red";
    public static final String THEME_BLUE = "Blue";
    public static final String THEME_LIGHT_BLUE = "LightBlue";
    public static final String THEME_SUNSET_ORANGE = "SunsetOrange";
    public static final String THEME_DEEP_PURPLE = "DeepPurple";
    public static final String THEME_MODERN_TEAL = "ModernTeal";

    public static void applyTheme(Context context) {
        String theme = getTheme(context);
        switch (theme) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
        applyColorTheme(context);
        
        // Apply navigation bar styling if context is an Activity
        if (context instanceof Activity) {
            updateNavigationBar((Activity) context);
        }
    }

    public static void applyColorTheme(Context context) {
        String colorTheme = getColorTheme(context);
        switch (colorTheme) {
            case THEME_FOREST_GREEN:
                context.setTheme(R.style.Theme_WanderPlan_ForestGreen);
                break;
            case THEME_RED:
                context.setTheme(R.style.Theme_WanderPlan_Red);
                break;
            case THEME_BLUE:
                context.setTheme(R.style.Theme_WanderPlan_Blue);
                break;
            case THEME_LIGHT_BLUE:
                context.setTheme(R.style.Theme_WanderPlan_LightBlue);
                break;
            case THEME_SUNSET_ORANGE:
                context.setTheme(R.style.Theme_WanderPlan_SunsetOrange);
                break;
            case THEME_DEEP_PURPLE:
                context.setTheme(R.style.Theme_WanderPlan_DeepPurple);
                break;
            case THEME_MODERN_TEAL:
                context.setTheme(R.style.Theme_WanderPlan_ModernTeal);
                break;
            case THEME_PASTEL_PINK:
            default:
                context.setTheme(R.style.Theme_WanderPlan_PastelPink);
                break;
        }
    }

    public static void updateNavigationBar(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = activity.getWindow();
            boolean isDarkTheme = isDarkTheme(activity);
            
            if (isDarkTheme) {
                // Dark theme - dark navigation bar
                window.setNavigationBarColor(ContextCompat.getColor(activity, R.color.dark_surface));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    View decorView = window.getDecorView();
                    int flags = decorView.getSystemUiVisibility();
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; // Remove light navigation bar flag
                    decorView.setSystemUiVisibility(flags);
                }
            } else {
                // Light theme - light navigation bar
                window.setNavigationBarColor(ContextCompat.getColor(activity, R.color.white));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    View decorView = window.getDecorView();
                    int flags = decorView.getSystemUiVisibility();
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; // Add light navigation bar flag
                    decorView.setSystemUiVisibility(flags);
                }
            }
        }
    }

    public static void setTheme(Context context, String theme) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_THEME, theme);
        editor.apply();
        applyTheme(context);
    }

    public static void setColorTheme(Context context, String colorTheme) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_COLOR_THEME, colorTheme);
        editor.apply();
    }

    public static String getTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_THEME, THEME_SYSTEM);
    }

    public static String getColorTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_COLOR_THEME, THEME_PASTEL_PINK);
    }
    
    /**
     * Check if the current theme is dark mode
     */
    public static boolean isDarkTheme(Context context) {
        String theme = getTheme(context);
        if (THEME_DARK.equals(theme)) {
            return true;
        } else if (THEME_LIGHT.equals(theme)) {
            return false;
        } else {
            // System theme - check system setting
            int nightModeFlags = context.getResources().getConfiguration().uiMode & 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
    }
    
    /**
     * Get the current primary color based on theme
     */
    public static int getCurrentPrimaryColor(Context context) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        return typedValue.data;
    }
    
    /**
     * Get the current secondary color based on theme
     */
    public static int getCurrentSecondaryColor(Context context) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSecondary, typedValue, true);
        return typedValue.data;
    }
} 