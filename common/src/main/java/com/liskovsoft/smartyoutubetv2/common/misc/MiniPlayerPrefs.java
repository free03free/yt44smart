package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the "mini player" option. Kept separate from PlayerData to not touch its serialized format.
 */
public final class MiniPlayerPrefs {
    private static final String PREFS = "mini_player_prefs";
    private static final String KEY_ENABLED = "enabled";

    private MiniPlayerPrefs() {
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
