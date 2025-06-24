package com.example.mobiledegreefinalproject;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

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
} 