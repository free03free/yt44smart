package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Switch between the ExoPlayer engine and the YouTube web player (embedded in a WebView).
 * The player UI stays the same, only the video source changes.
 */
public class WebPlayerSettings {
    private static final String PREFS = "web_player";
    private static final String KEY_ENABLED = "enabled";
    private static final int MAX_FALLBACK_IDS = 200;
    // Videos the web player can't play (embedding disabled, etc). They are played by ExoPlayer.
    private static final Set<String> sFallbackIds = new HashSet<>();
    private static boolean sExoForced;

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static synchronized boolean shouldUseWeb(Context context, String videoId) {
        return videoId != null && isEnabled(context) && !sFallbackIds.contains(videoId);
    }

    public static synchronized void markFallback(String videoId) {
        if (videoId == null) {
            return;
        }

        if (sFallbackIds.size() > MAX_FALLBACK_IDS) {
            sFallbackIds.clear();
        }

        sFallbackIds.add(videoId);
    }

    /** When true, the player UI is built with the ExoPlayer adapter even if the web player is enabled. */
    public static synchronized boolean isExoForced() {
        return sExoForced;
    }

    public static synchronized void setExoForced(boolean forced) {
        sExoForced = forced;
    }
}
