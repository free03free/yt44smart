package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.misc.FontScaler.Category;

/**
 * Advanced appearance options: text color and bold per element category, plus the app background color.
 * Stored in its own SharedPreferences file (independent from the other app data formats).
 * Values are cached in memory because they are read after every layout pass.
 */
public final class AppearancePrefs {
    private static final String PREFS = "appearance_prefs";
    private static final int N = Category.values().length;
    private static int[] sColors;
    private static boolean[] sBold;
    private static int sBackground;
    private static int sAccent;
    private static boolean sLoaded;

    private AppearancePrefs() {
    }

    private static synchronized void load(Context context) {
        if (sLoaded) {
            return;
        }

        SharedPreferences p = prefs(context);
        sColors = new int[N];
        sBold = new boolean[N];
        for (Category c : Category.values()) {
            sColors[c.ordinal()] = p.getInt("color_" + c.name(), 0);
            sBold[c.ordinal()] = p.getBoolean("bold_" + c.name(), false);
        }
        sBackground = p.getInt("background", 0);
        sAccent = p.getInt("accent", 0);
        sLoaded = true;
    }

    /** 0 means "keep the original color" */
    public static int getTextColor(Context context, Category category) {
        load(context);
        return sColors[category.ordinal()];
    }

    public static void setTextColor(Context context, Category category, int color) {
        load(context);
        sColors[category.ordinal()] = color;
        prefs(context).edit().putInt("color_" + category.name(), color).apply();
    }

    public static boolean isBold(Context context, Category category) {
        load(context);
        return sBold[category.ordinal()];
    }

    public static void setBold(Context context, Category category, boolean bold) {
        load(context);
        sBold[category.ordinal()] = bold;
        prefs(context).edit().putBoolean("bold_" + category.name(), bold).apply();
    }

    /** 0 means "keep the theme background" */
    public static int getBackground(Context context) {
        load(context);
        return sBackground;
    }

    public static void setBackground(Context context, int color) {
        load(context);
        sBackground = color;
        prefs(context).edit().putInt("background", color).apply();
    }

    /** Color of focused/selected/pressed text. 0 = keep the original focus colors */
    public static int getAccent(Context context) {
        load(context);
        return sAccent;
    }

    public static void setAccent(Context context, int color) {
        load(context);
        sAccent = color;
        prefs(context).edit().putInt("accent", color).apply();
    }

    /** Resets colors, bold and background. Font sizes are reset by the caller (they live in MainUIData). */
    public static void resetAll(Context context) {
        load(context);
        java.util.Arrays.fill(sColors, 0);
        java.util.Arrays.fill(sBold, false);
        sBackground = 0;
        sAccent = 0;
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
