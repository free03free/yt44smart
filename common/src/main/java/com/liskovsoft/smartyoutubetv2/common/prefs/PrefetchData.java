package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Background prefetch of the rows/grids: the videos are loaded in advance (silently) so the user
 * doesn't wait with a spinner when the end of a row is reached.
 */
public class PrefetchData {
    public static final int MODE_OFF = 0;
    public static final int MODE_NORMAL = 1;
    public static final int MODE_MORE = 2;

    private static final String PREFS_NAME = "prefetch_prefs";
    private static final String KEY_MODE = "mode";
    private static final String KEY_CACHE_MINUTES = "section_cache_minutes";
    private static final String KEY_CACHE_SECTIONS = "section_cache_sections";
    private static final String KEY_REFRESH_ON_ENTER = "section_refresh_on_enter";
    /** Upper limits of the custom values (7 days / 200 sections) */
    public static final int MAX_CACHE_MINUTES = 10080;
    public static final int MAX_CACHE_SECTIONS = 200;
    /** 0 = off, -1 = never expires (until the app is closed) */
    public static final int[] CACHE_MINUTES = {0, 5, 10, 15, 30, 60, 120, 240, 480, 1440, -1};
    /** 0 = unlimited */
    public static final int[] CACHE_SECTIONS = {2, 4, 8, 12, 16, 24, 32, 48, 0};

    private static PrefetchData sInstance;
    private final SharedPreferences mPrefs;

    private PrefetchData(Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized PrefetchData instance(Context context) {
        if (sInstance == null) {
            sInstance = new PrefetchData(context);
        }

        return sInstance;
    }

    public int getMode() {
        return mPrefs.getInt(KEY_MODE, MODE_NORMAL);
    }

    public void setMode(int mode) {
        mPrefs.edit().putInt(KEY_MODE, mode).apply();
    }

    /**
     * How long the loaded section stays in memory. Returning to it within this time doesn't reload it. 0 = off.
     */
    public int getSectionCacheMinutes() {
        int minutes = mPrefs.getInt(KEY_CACHE_MINUTES, 30);
        return minutes > MAX_CACHE_MINUTES ? MAX_CACHE_MINUTES : minutes;
    }

    public void setSectionCacheMinutes(int minutes) {
        // 0 = off, negative = never expires, otherwise 1..MAX
        int safe = minutes < 0 ? -1 : Math.min(minutes, MAX_CACHE_MINUTES);
        mPrefs.edit().putInt(KEY_CACHE_MINUTES, safe).apply();
    }

    /**
     * How many sections are kept in memory. 0 = unlimited.
     */
    public int getSectionCacheSections() {
        int count = mPrefs.getInt(KEY_CACHE_SECTIONS, 8);
        return count > MAX_CACHE_SECTIONS ? MAX_CACHE_SECTIONS : Math.max(count, 0);
    }

    public void setSectionCacheSections(int count) {
        // 0 = unlimited, otherwise 1..MAX
        int safe = count <= 0 ? 0 : Math.min(count, MAX_CACHE_SECTIONS);
        mPrefs.edit().putInt(KEY_CACHE_SECTIONS, safe).apply();
    }

    /**
     * Refresh the sections when the user comes back to the app (like YouTube does), so the newest videos are shown.
     */
    public boolean isRefreshOnEnterEnabled() {
        return mPrefs.getBoolean(KEY_REFRESH_ON_ENTER, true);
    }

    public void setRefreshOnEnterEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_REFRESH_ON_ENTER, enabled).apply();
    }

    /**
     * Hard memory budget of the cache (total videos of all kept sections). Applied even when the number of sections
     * is unlimited and the time never expires, so heavy use can't make the app run out of memory.
     * Scales with the heap size of the device.
     */
    public int getMaxCachedVideos() {
        long heapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        return (int) Math.max(500, Math.min(3000, heapMb * 12));
    }

    public boolean isEnabled() {
        return getMode() != MODE_OFF;
    }

    /**
     * Wanted amount of videos in a row before the loading stops.
     */
    public int getRowTarget() {
        switch (getMode()) {
            case MODE_NORMAL:
                return 30;
            case MODE_MORE:
                return 50;
            default:
                return 0;
        }
    }

    /**
     * Wanted amount of videos in a grid before the loading stops.
     */
    public int getGridTarget() {
        switch (getMode()) {
            case MODE_NORMAL:
                return 50;
            case MODE_MORE:
                return 90;
            default:
                return 0;
        }
    }

    /**
     * How many rows before the end of a row the next page should be requested.
     */
    public int getRowScrollNum(int defaultNum) {
        return isEnabled() ? Math.max(defaultNum, 12) : defaultNum;
    }
}
