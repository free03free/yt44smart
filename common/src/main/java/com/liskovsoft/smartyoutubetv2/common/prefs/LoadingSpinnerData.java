package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Style of the loading indicator that is shown while the video (or a screen) is being loaded.
 */
public class LoadingSpinnerData {
    public static final int STYLE_DEFAULT = 0;
    public static final int STYLE_BARS_12 = 1;
    public static final int STYLE_BARS_THICK = 2;
    public static final int STYLE_STARBURST = 3;
    public static final int STYLE_DOTS_FADE = 4;
    public static final int STYLE_DOTS_TAPER = 5;
    public static final int STYLE_DOTS_PULSE = 6;
    public static final int STYLE_ARC_GRADIENT = 7;
    public static final int STYLE_RING_ARC = 8;
    public static final int STYLE_DASHES = 9;
    public static final int STYLE_SEGMENTS = 10;
    public static final int STYLE_TWO_ARCS = 11;
    public static final int STYLE_COUNT = 12;

    /**
     * Palette. The first item (0) means "automatic": the color follows the app theme.
     */
    public static final int[] COLORS = {
            0, 0xFFFFFFFF, 0xFFFF3B30, 0xFF2196F3, 0xFF4CAF50,
            0xFFFFC107, 0xFFFF9800, 0xFF9C27B0, 0xFF00BCD4, 0xFFE91E63
    };
    public static final int MIN_SIZE_DP = 32;
    public static final int MAX_SIZE_DP = 128;
    public static final int SIZE_STEP_DP = 8;
    public static final int DEFAULT_SIZE_DP = 64;

    private static final String PREFS_NAME = "loading_spinner_prefs";
    private static final String KEY_STYLE = "style";
    private static final String KEY_COLOR_INDEX = "color_index";
    private static final String KEY_SIZE_DP = "size_dp";

    private static LoadingSpinnerData sInstance;
    private final SharedPreferences mPrefs;

    private LoadingSpinnerData(Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized LoadingSpinnerData instance(Context context) {
        if (sInstance == null) {
            sInstance = new LoadingSpinnerData(context);
        }

        return sInstance;
    }

    public int getStyle() {
        int style = mPrefs.getInt(KEY_STYLE, STYLE_DEFAULT);

        return style >= 0 && style < STYLE_COUNT ? style : STYLE_DEFAULT;
    }

    public void setStyle(int style) {
        mPrefs.edit().putInt(KEY_STYLE, style).apply();
    }

    public int getColorIndex() {
        int index = mPrefs.getInt(KEY_COLOR_INDEX, 0);

        return index >= 0 && index < COLORS.length ? index : 0;
    }

    public void setColorIndex(int index) {
        mPrefs.edit().putInt(KEY_COLOR_INDEX, index).apply();
    }

    /**
     * @return ARGB color or 0 when the color should follow the theme
     */
    public int getColor() {
        return COLORS[getColorIndex()];
    }

    public int getSizeDp() {
        int size = mPrefs.getInt(KEY_SIZE_DP, DEFAULT_SIZE_DP);

        return Math.max(MIN_SIZE_DP, Math.min(MAX_SIZE_DP, size));
    }

    public void setSizeDp(int sizeDp) {
        mPrefs.edit().putInt(KEY_SIZE_DP, sizeDp).apply();
    }
}
