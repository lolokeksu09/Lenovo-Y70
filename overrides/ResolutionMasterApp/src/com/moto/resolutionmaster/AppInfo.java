package com.moto.resolutionmaster;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String packageName;
    public final String label;
    public final Drawable icon;
    public final boolean isSystem;
    public boolean isEnabled;
    // null = package state unavailable, never count as disabled
    public Boolean enabledState;
    public final String description;
    public final int safetyLevel;
    public final String safetyLabel;
    public final int safetyColor;

    public AppInfo(String packageName, String label, Drawable icon, boolean isSystem, boolean isEnabled,
                   String description, int safetyLevel, String safetyLabel, int safetyColor) {
        this(packageName, label, icon, isSystem, Boolean.valueOf(isEnabled),
             description, safetyLevel, safetyLabel, safetyColor);
    }

    public AppInfo(String packageName, String label, Drawable icon, boolean isSystem, Boolean enabledState,
                   String description, int safetyLevel, String safetyLabel, int safetyColor) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
        this.isSystem = isSystem;
        setEnabledState(enabledState);
        this.description = description;
        this.safetyLevel = safetyLevel;
        this.safetyLabel = safetyLabel;
        this.safetyColor = safetyColor;
    }

    public void setEnabledState(Boolean state) {
        this.enabledState = state;
        this.isEnabled = Boolean.TRUE.equals(state);
    }
}
