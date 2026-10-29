package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings of the in-app mini player (floating video window shown over the browse screens).
 */
public class MiniPlayerData {
    public static final int POSITION_BOTTOM_RIGHT = 0;
    public static final int POSITION_BOTTOM_LEFT = 1;
    public static final int POSITION_TOP_RIGHT = 2;
    public static final int POSITION_TOP_LEFT = 3;

    // Top bar = logo/clock + search + account buttons
    public static final int TOP_BAR_SHOW = 0;
    public static final int TOP_BAR_HIDE_WITH_MINI = 1; // hidden only while the mini window is visible
    public static final int TOP_BAR_HIDE_ALWAYS = 2;

    public static final int MIN_SIZE_PERCENT = 10;
    public static final int MAX_SIZE_PERCENT = 60;
    public static final int SIZE_STEP_PERCENT = 2;
    public static final int DEFAULT_SIZE_PERCENT = 30;
    public static final int[] QUALITIES = {144, 240, 360, 480, 720, 1080}; // video height
    public static final int DEFAULT_QUALITY = 360;

    private static final String PREFS_NAME = "mini_player_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_POSITION = "position";
    private static final String KEY_SIZE = "size"; // legacy: small/medium/large
    private static final String KEY_SIZE_PERCENT = "size_percent";
    private static final String KEY_QUALITY = "quality";
    private static final String KEY_PLAY_IN_MINI = "play_in_mini";
    private static final String KEY_TOP_BAR_MODE = "top_bar_mode";
    private static final String KEY_SEAMLESS = "seamless_switch";

    private static MiniPlayerData sInstance;
    private final SharedPreferences mPrefs;

    private MiniPlayerData(Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized MiniPlayerData instance(Context context) {
        if (sInstance == null) {
            sInstance = new MiniPlayerData(context);
        }

        return sInstance;
    }

    public boolean isEnabled() {
        return mPrefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /**
     * When the mini player is visible, a video chosen in the app plays inside the mini window instead of fullscreen.
     */
    public boolean isPlayInMiniEnabled() {
        return mPrefs.getBoolean(KEY_PLAY_IN_MINI, false);
    }

    public void setPlayInMiniEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_PLAY_IN_MINI, enabled).apply();
    }

    /**
     * Full mode: the same running player moves between the mini window and fullscreen (no reload).
     * Off: the "current mode" - the video is reopened quickly from saved stream data.
     */
    public boolean isSeamlessEnabled() {
        return mPrefs.getBoolean(KEY_SEAMLESS, false);
    }

    public void setSeamlessEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_SEAMLESS, enabled).apply();
    }

    public int getTopBarMode() {
        return mPrefs.getInt(KEY_TOP_BAR_MODE, TOP_BAR_SHOW);
    }

    public void setTopBarMode(int mode) {
        mPrefs.edit().putInt(KEY_TOP_BAR_MODE, mode).apply();
    }

    /**
     * @param miniWindowVisible is the mini window currently shown over the browse screen
     */
    public boolean isTopBarHidden(boolean miniWindowVisible) {
        int mode = getTopBarMode();
        return mode == TOP_BAR_HIDE_ALWAYS || (mode == TOP_BAR_HIDE_WITH_MINI && miniWindowVisible);
    }

    public int getPosition() {
        return mPrefs.getInt(KEY_POSITION, POSITION_BOTTOM_RIGHT);
    }

    public void setPosition(int position) {
        mPrefs.edit().putInt(KEY_POSITION, position).apply();
    }

    /**
     * Window width in percents of the screen width.
     */
    public int getSizePercent() {
        int percent;

        if (mPrefs.contains(KEY_SIZE_PERCENT)) {
            percent = mPrefs.getInt(KEY_SIZE_PERCENT, DEFAULT_SIZE_PERCENT);
        } else {
            // Migrate from the old three presets
            switch (mPrefs.getInt(KEY_SIZE, 1)) {
                case 0:
                    percent = 22;
                    break;
                case 2:
                    percent = 40;
                    break;
                default:
                    percent = DEFAULT_SIZE_PERCENT;
            }
        }

        return Math.max(MIN_SIZE_PERCENT, Math.min(MAX_SIZE_PERCENT, percent));
    }

    public void setSizePercent(int percent) {
        mPrefs.edit().putInt(KEY_SIZE_PERCENT, Math.max(MIN_SIZE_PERCENT, Math.min(MAX_SIZE_PERCENT, percent))).apply();
    }

    /**
     * Max video height (in pixels) for the mini player.
     */
    public int getQuality() {
        return mPrefs.getInt(KEY_QUALITY, DEFAULT_QUALITY);
    }

    public void setQuality(int height) {
        mPrefs.edit().putInt(KEY_QUALITY, height).apply();
    }
}
