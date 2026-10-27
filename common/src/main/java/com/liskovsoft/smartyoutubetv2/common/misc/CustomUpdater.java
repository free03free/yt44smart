package com.liskovsoft.smartyoutubetv2.common.misc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Update from the user's own GitHub repository (latest GitHub Release).
 * No manifest file is needed: the address is the repository link, e.g. https://github.com/owner/repo
 */
public final class CustomUpdater {
    private static final String TAG = CustomUpdater.class.getSimpleName();
    private static final Pattern REPO = Pattern.compile("github\\.com/([^/\\s]+)/([^/?#\\s]+)");
    private static final Pattern API_REPO = Pattern.compile("repos/([^/\\s]+)/([^/?#\\s]+)");
    private static final Pattern NIGHTLY = Pattern.compile("nightly-(\\d+)");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static class Release {
        public String name;
        public String tag;
        public String apkUrl;
        public String apkName;
        public List<String> changes = new ArrayList<>();
    }

    public interface Callback {
        /** release == null and error == null: the app is up to date */
        void onDone(Release release, Exception error);
    }

    private CustomUpdater() {
    }

    public static void check(Context context, String url, Callback callback) {
        final Context app = context.getApplicationContext();

        new Thread(() -> {
            Release release = null;
            Exception error = null;

            try {
                release = fetchNewer(app, url);
            } catch (Exception e) {
                Log.e(TAG, "Update check failed: " + e);
                error = e;
            }

            final Release r = release;
            final Exception err = error;
            MAIN.post(() -> callback.onDone(r, err));
        }).start();
    }

    public static void downloadAndInstall(Context context, Release release) {
        final Context app = context.getApplicationContext();
        final Activity activity = findActivity(context);
        final ProgressUi ui = new ProgressUi(activity, app);
        ui.show();

        new Thread(() -> {
            File apk = null;

            try {
                File dir = new File(app.getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException("Can't create " + dir);
                }

                apk = new File(dir, "update.apk");
                if (apk.exists()) {
                    apk.delete();
                }

                download(release.apkUrl, apk, ui);

                final File done = apk;
                MAIN.post(() -> {
                    ui.dismiss();
                    install(app, done, activity, release);
                });
            } catch (Exception e) {
                if (apk != null) {
                    apk.delete();
                }

                MAIN.post(() -> {
                    ui.dismiss();
                    if (!ui.cancelled) {
                        Log.e(TAG, "Update download failed: " + e);
                        MessageHelpers.showMessage(app, app.getString(R.string.update_error) + ": " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /** Download progress: a dialog with a progress bar (or periodic messages if there is no activity). */
    private static final class ProgressUi {
        private final Activity mActivity;
        private final Context mApp;
        private AlertDialog mDialog;
        private ProgressBar mBar;
        private TextView mText;
        private long mLastToast;
        volatile boolean cancelled;

        ProgressUi(Activity activity, Context app) {
            mActivity = activity;
            mApp = app;
        }

        void show() {
            if (mActivity == null || mActivity.isFinishing()) {
                MessageHelpers.showMessage(mApp, mApp.getString(R.string.custom_update_downloading));
                return;
            }

            float d = mActivity.getResources().getDisplayMetrics().density;
            int pad = (int) (20 * d);

            LinearLayout box = new LinearLayout(mActivity);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(pad, pad, pad, pad);

            mText = new TextView(mActivity);
            mText.setTextSize(16);
            mText.setText(R.string.custom_update_downloading);
            box.addView(mText);

            mBar = new ProgressBar(mActivity, null, android.R.attr.progressBarStyleHorizontal);
            mBar.setIndeterminate(true);
            mBar.setMax(100);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = pad / 2;
            box.addView(mBar, lp);

            try {
                mDialog = new AlertDialog.Builder(mActivity)
                        .setTitle(R.string.custom_update_downloading)
                        .setView(box)
                        .setCancelable(false)
                        .setNegativeButton(android.R.string.cancel, (dlg, which) -> cancelled = true)
                        .create();
                mDialog.show();
            } catch (Exception e) {
                mDialog = null;
            }
        }

        // Can be called from any thread
        void update(long done, long total) {
            MAIN.post(() -> {
                String size = total > 0
                        ? String.format(Locale.US, "%.1f / %.1f MB", done / 1048576f, total / 1048576f)
                        : String.format(Locale.US, "%.1f MB", done / 1048576f);

                if (mDialog != null && mBar != null && mText != null) {
                    if (total > 0) {
                        mBar.setIndeterminate(false);
                        mBar.setProgress((int) (done * 100 / total));
                        mText.setText(mApp.getString(R.string.custom_update_downloading) + "\n" + size + "  (" + (done * 100 / total) + "%)");
                    } else {
                        mText.setText(mApp.getString(R.string.custom_update_downloading) + "\n" + size);
                    }
                } else {
                    long now = SystemClock.uptimeMillis();
                    if (now - mLastToast > 3000) {
                        mLastToast = now;
                        MessageHelpers.showMessage(mApp, mApp.getString(R.string.custom_update_downloading) + " " + size);
                    }
                }
            });
        }

        void dismiss() {
            try {
                if (mDialog != null && mDialog.isShowing()) {
                    mDialog.dismiss();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static Release fetchNewer(Context context, String url) throws Exception {
        // A link to the update json file (generated by the build workflow)
        if (url != null && url.toLowerCase().contains(".json")) {
            return fetchFromManifest(context, url.trim());
        }

        Matcher m = REPO.matcher(url == null ? "" : url);
        if (!m.find()) {
            m = API_REPO.matcher(url == null ? "" : url);
            if (!m.find()) {
                throw new IllegalArgumentException(context.getString(R.string.custom_update_bad_url));
            }
        }

        String owner = m.group(1);
        String repo = m.group(2).replaceAll("\\.git$", "");

        String json = httpGet("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest");
        JSONObject obj = new JSONObject(json);

        String tag = obj.optString("tag_name", "");
        String name = obj.optString("name", "");
        if (name.isEmpty()) {
            name = tag;
        }

        if (!isNewer(installedVersionName(context), tag, name)) {
            return null;
        }

        Release release = new Release();
        release.tag = tag;
        release.name = name;

        JSONArray assets = obj.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String assetName = asset.optString("name", "");
                if (!assetName.toLowerCase().endsWith(".apk")) {
                    continue;
                }
                // The universal build works on any device
                if (release.apkUrl == null || assetName.toLowerCase().contains("universal")) {
                    release.apkUrl = asset.optString("browser_download_url", null);
                    release.apkName = assetName;
                }
            }

            // Better: the build for the same ABI as the installed app (smaller and identical variant)
            for (String abi : abiPreference(context)) {
                boolean found = false;
                for (int i = 0; i < assets.length() && !found; i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String assetName = asset.optString("name", "");
                    if (assetName.toLowerCase().endsWith("_" + abi + ".apk")) {
                        release.apkUrl = asset.optString("browser_download_url", release.apkUrl);
                        release.apkName = assetName;
                        found = true;
                    }
                }
                if (found) {
                    break;
                }
            }
        }

        if (release.apkUrl == null) {
            throw new IllegalStateException(context.getString(R.string.custom_update_no_apk));
        }

        for (String line : obj.optString("body", "").split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                release.changes.add(line.trim());
            }
        }
        if (release.changes.isEmpty()) {
            release.changes.add(name);
        }

        return release;
    }

    /**
     * Manifest format: {"version": "32.68-nightly-16", "changes": ["..."], "apk": "https://.../file.apk"}
     */
    private static Release fetchFromManifest(Context context, String url) throws Exception {
        JSONObject obj = new JSONObject(httpGet(url));

        String name = obj.optString("version", "");
        String apk = obj.optString("apk", "");

        if (name.isEmpty() || apk.isEmpty()) {
            throw new IllegalStateException(context.getString(R.string.custom_update_bad_manifest));
        }

        if (!isNewer(installedVersionName(context), name, name)) {
            return null;
        }

        // Optional "apks": {"armeabi-v7a": url, "arm64-v8a": url, ..., "universal": url}.
        // Take the build for the same ABI as the installed app; fall back to "apk" (universal).
        JSONObject apks = obj.optJSONObject("apks");
        if (apks != null) {
            for (String abi : abiPreference(context)) {
                String candidate = apks.optString(abi, "");
                if (!candidate.isEmpty()) {
                    apk = candidate;
                    break;
                }
            }
        }

        Release release = new Release();
        release.tag = name;
        release.name = name;
        release.apkUrl = apk;
        release.apkName = apk.substring(apk.lastIndexOf('/') + 1);

        JSONArray changes = obj.optJSONArray("changes");
        if (changes != null) {
            for (int i = 0; i < changes.length(); i++) {
                String change = changes.optString(i, "").trim();
                if (!change.isEmpty()) {
                    release.changes.add(change);
                }
            }
        }
        if (release.changes.isEmpty()) {
            release.changes.add(name);
        }

        return release;
    }

    /** The ABI the installed app actually runs with (from its native library folder), or null. */
    private static String installedAbi(Context context) {
        try {
            String dir = context.getApplicationInfo().nativeLibraryDir;
            if (dir != null) {
                String n = dir.substring(dir.lastIndexOf('/') + 1);
                if ("arm64".equals(n)) return "arm64-v8a";
                if ("arm".equals(n)) return "armeabi-v7a";
                if ("x86_64".equals(n)) return "x86_64";
                if ("x86".equals(n)) return "x86";
            }
        } catch (Exception e) {
            Log.e(TAG, "installedAbi failed: " + e);
        }
        return null;
    }

    /** Preferred ABIs, best first: the one the installed app uses, then what the device supports. */
    @SuppressWarnings("deprecation")
    private static List<String> abiPreference(Context context) {
        List<String> out = new ArrayList<>();
        String inst = installedAbi(context);
        if (inst != null) {
            out.add(inst);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if (!out.contains(abi)) {
                    out.add(abi);
                }
            }
        } else if (Build.CPU_ABI != null && !out.contains(Build.CPU_ABI)) {
            out.add(Build.CPU_ABI);
        }
        return out;
    }

    private static String installedVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName != null ? info.versionName : "";
        } catch (Exception e) {
            return "";
        }
    }

    // Versions look like "32.68-nightly-15": compare the build number
    private static boolean isNewer(String installed, String tag, String name) {
        int remote = nightly(tag + " " + name);
        int local = nightly(installed);

        if (remote >= 0 && local >= 0) {
            return remote > local;
        }

        return !installed.equals(name) && !installed.equals(tag);
    }

    private static int nightly(String text) {
        Matcher m = NIGHTLY.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    // Connection problems (SSL handshake timeout, DNS, etc.) are often temporary: try up to 3 times with longer timeouts
    private static String httpGet(String url) throws IOException {
        IOException last = null;

        for (int i = 1; i <= 3; i++) {
            try {
                return httpGetOnce(url, 15_000 * i);
            } catch (java.net.SocketTimeoutException | javax.net.ssl.SSLException
                     | java.net.ConnectException | java.net.UnknownHostException e) {
                last = e;
                try {
                    Thread.sleep(1500L * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        throw last != null ? last : new IOException("interrupted");
    }

    private static String httpGetOnce(String url, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "SmartTube-CustomUpdater");

        try {
            int code = c.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code + (code == 404 ? " (no releases)" : ""));
            }

            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    private static void download(String url, File target, ProgressUi ui) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(30_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", "SmartTube-CustomUpdater");

        try {
            int code = c.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }

            final long total = c.getContentLength(); // -1 if unknown

            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[16 * 1024];
                int n;
                long done = 0;
                long lastUi = 0;

                while ((n = in.read(buf)) > 0) {
                    if (ui.cancelled) {
                        throw new IOException("cancelled");
                    }

                    out.write(buf, 0, n);
                    done += n;

                    long now = SystemClock.uptimeMillis();
                    if (now - lastUi > 200) {
                        lastUi = now;
                        ui.update(done, total);
                    }
                }

                ui.update(done, total);
            }
        } finally {
            c.disconnect();
        }
    }

    /**
     * Android only says "App not installed". Find the real reason before calling the installer.
     * Returns a message resource id, or 0 if no problem was found.
     */
    @SuppressWarnings("deprecation")
    private static int findConflict(Context context, File apk) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo update = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
            if (update == null) {
                return 0;
            }
            if (update.packageName != null && !context.getPackageName().equals(update.packageName)) {
                // Not an update at all: Android would install it as a second app (or refuse it)
                return R.string.custom_update_other_app;
            }

            PackageInfo current = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);

            if (update.versionCode < current.versionCode) {
                return R.string.custom_update_downgrade;
            }

            if (update.signatures != null && current.signatures != null
                    && update.signatures.length > 0 && current.signatures.length > 0
                    && !java.util.Arrays.equals(update.signatures[0].toByteArray(), current.signatures[0].toByteArray())) {
                return R.string.custom_update_sig_mismatch;
            }
        } catch (Exception e) {
            Log.e(TAG, "Conflict check failed: " + e);
        }

        return 0;
    }

    private static void showConflict(Context context, Activity activity, Release release, int messageRes) {
        String message = context.getString(messageRes);

        if (activity == null || activity.isFinishing()) {
            MessageHelpers.showMessage(context, message);
            return;
        }

        try {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.update_error)
                    .setMessage(message)
                    .setPositiveButton(R.string.custom_update_open_browser, (d, w) -> {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(i);
                        } catch (Exception e) {
                            MessageHelpers.showMessage(context, e.getMessage());
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Exception e) {
            MessageHelpers.showMessage(context, message);
        }
    }

    private static void install(Context context, File apk, Activity activity, Release release) {
        int conflict = findConflict(context, apk);
        if (conflict != 0) {
            showConflict(context, activity, release, conflict);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
                MessageHelpers.showMessage(context, context.getString(R.string.custom_update_allow_install));
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.getPackageName()));
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(settings);
                return;
            }

            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".customupdate", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Install failed: " + e);
            MessageHelpers.showMessage(context, context.getString(R.string.update_error) + ": " + e.getMessage());
        }
    }
}
