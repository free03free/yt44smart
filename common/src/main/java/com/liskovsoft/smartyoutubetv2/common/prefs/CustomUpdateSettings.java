package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/** Address of the user's own update site (manifest json, same format as the official smarttube_*.json). */
public class CustomUpdateSettings {
    private static final String PREFS = "custom_update";
    private final SharedPreferences mPrefs;

    private CustomUpdateSettings(Context context) {
        mPrefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static CustomUpdateSettings instance(Context context) {
        return new CustomUpdateSettings(context);
    }

    /** Used when nothing is saved: always points to the newest release of the user's repository. */
    public static final String DEFAULT_URL = "https://github.com/free03free/yt22smart/releases/latest/download/update.json";

    public String getUrl() {
        String url = mPrefs.getString("url", "");

        // Empty, or an old wrong value (a GitHub Actions page): use the default address
        if (url == null || url.trim().isEmpty() || url.contains("/actions/") || url.contains("free03free/ytsmart/")) {
            return DEFAULT_URL;
        }

        return url;
    }
    public void setUrl(String url) { mPrefs.edit().putString("url", url == null ? "" : url.trim()).apply(); }
}
