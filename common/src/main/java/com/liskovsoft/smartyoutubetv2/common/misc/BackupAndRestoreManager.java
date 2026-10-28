package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Environment;
import android.os.Handler;

import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.HiddenPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.disposables.Disposable;

public class BackupAndRestoreManager implements MotherActivity.OnPermissions {
    private static final String TAG = BackupAndRestoreManager.class.getSimpleName();
    private static final String BACKUP_DIR_NAME = "Backup";
    private final Context mContext;
    private static final String SHARED_PREFS_SUBDIR = "shared_prefs";
    private static final String FILES_SUBDIR = "files";
    /** Backup sections. Each section is stored in its own files, so it can be saved/restored alone. */
    public static final int CAT_SETTINGS = 1; // app settings + local data (history, sidebar...)
    public static final int CAT_ACCOUNT = 2;  // sign-in / YouTube service data
    public static final int CAT_LIBRARY = 4;  // saved playlists / collections of the library
    public static final int CAT_ALL = CAT_SETTINGS | CAT_ACCOUNT | CAT_LIBRARY;
    private final File mSharedPrefsDir;
    private final File mFilesDir;
    private final List<File> mBackupDirs;
    private final BackupAndRestoreHelper mHelper;
    private final boolean mForceApi30;
    private Runnable mPendingHandler;
    private Disposable mZipAction;

    public interface OnBackupNames {
        void onBackupNames(List<String> backupNames);
    }

    public BackupAndRestoreManager(Context context) {
        this(context, false);
    }

    public BackupAndRestoreManager(Context context, boolean forceApi30) {
        mContext = context;
        mForceApi30 = forceApi30;

        mHelper = new BackupAndRestoreHelper(context);

        mSharedPrefsDir = new File(mContext.getApplicationInfo().dataDir, SHARED_PREFS_SUBDIR);
        mFilesDir = FileHelpers.getFilesDir(context);

        mBackupDirs = new ArrayList<>();
    }

    private void initBackupDirs() {
        if (!mBackupDirs.isEmpty()) {
            return;
        }

        File externalDir = getExternalStorageDirectory();
        // Main backup dir
        mBackupDirs.add(createBackupDir(new File(externalDir, String.format("data/%s", mContext.getPackageName()))));

        File dataDir = new File(externalDir, "data");

        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        File[] appDirs = dataDir.listFiles();

        if (appDirs == null) {
            return;
        }

        // Fallback dirs: in case multiple app flavors installed
        for (File appDir : appDirs) {
            File backupDir = createBackupDir(appDir);
            if (!mBackupDirs.contains(backupDir)) {
                mBackupDirs.add(backupDir);
            }
        }
    }

    private File createBackupDir(File appDir) {
        return new File(appDir, BACKUP_DIR_NAME);
    }

    private File getExternalStorageDirectory() {
        File result;

        if (hasAccessOnlyToAppFolders()) {
            result = FileHelpers.getExternalMediaDirectory(mContext);
        } else {
            result = Environment.getExternalStorageDirectory();
        }

        return result;
    }

    public void checkPermAndRestore() {
        List<String> backupNames = getBackupNames();

        if (!backupNames.isEmpty()) {
            checkPermAndRestore(backupNames.get(0));
        }
    }

    public void checkPermAndRestore(String backupName) {
        checkPermAndRestore(backupName, CAT_ALL);
    }

    public void checkPermAndRestore(String backupName, int categories) {
        if (backupName == null) {
            return;
        }

        if (FileHelpers.isExternalStorageReadable()) {
            if (hasStoragePermissions(mContext)) {
                restoreData(backupName, categories);
            } else {
                mPendingHandler = () -> restoreData(backupName, categories);
                verifyStoragePermissionsAndReturn();
            }
        }
    }

    public void checkPermAndBackup() {
        checkPermAndBackup(CAT_ALL);
    }

    public void checkPermAndBackup(int categories) {
        if (FileHelpers.isExternalStorageWritable()) {
            if (hasStoragePermissions(mContext)) {
                backupData(categories);
            } else {
                mPendingHandler = () -> backupData(categories);
                verifyStoragePermissionsAndReturn();
            }
        }
    }

    public void backupData() {
        backupData(CAT_ALL);
    }

    /**
     * @param categories sections to save (CAT_*). A partial backup replaces only the files of the chosen sections,
     *                   the other sections already kept in the backup folder stay untouched.
     */
    public void backupData(int categories) {
        Log.d(TAG, "App has been updated or installed. Doing data backup...");

        File currentBackup = getBackup();

        if (currentBackup == null || !hasStoragePermissions(mContext)) {
            Log.d(TAG, "Oops. Backup location not writable.");
            return;
        }

        //if (hasAccessOnlyToAppFolders()) {
        //    File mediaDir = FileHelpers.getExternalMediaDirectory(mContext);
        //    File dataDir = new File(mediaDir, "data");
        //    FileHelpers.delete(dataDir);
        //} else if (currentBackup.isDirectory()) { // plain sdcard storage
        //    // remove old backup <app_id>/Backup
        //    FileHelpers.delete(currentBackup);
        //}
        //
        //if (mSharedPrefsDir.isDirectory() && !FileHelpers.isEmpty(mSharedPrefsDir)) {
        //    File destination = new File(currentBackup, mSharedPrefsDir.getName());
        //    FileHelpers.copy(mSharedPrefsDir, destination, fileName -> Helpers.endsWithAny(fileName.toString(), Utils.BACKUP_PATTERNS), null);
        //
        //    // Don't store unique id
        //    FileHelpers.delete(new File(destination, HiddenPrefs.SHARED_PREFERENCES_NAME + ".xml"));
        //}
        //
        //if (mFilesDir.isDirectory() && !FileHelpers.isEmpty(mFilesDir)) {
        //    File destination = new File(currentBackup, mFilesDir.getName());
        //    FileHelpers.copy(mFilesDir, destination, null, fileName -> Helpers.endsWithAny(fileName.toString(), Utils.BACKUP_DIR_PATTERNS));
        //}

        backupToDir(currentBackup, categories);

        if (hasAccessOnlyToAppFolders()) {
            mHelper.exportAppMediaFolder();
        } else {
            RxHelper.disposeActions(mZipAction);
            mZipAction = RxHelper.runAsync(this::saveDataToZip);
        }
    }

    public void backupToDir(File target) {
        backupToDir(target, CAT_ALL);
    }

    public void backupToDir(File target, int categories) {
        categories &= CAT_ALL;

        if (categories == 0) {
            return;
        }

        if (categories != CAT_ALL) {
            backupToDirPartial(target, categories);
            return;
        }

        if (target.isDirectory()) { // plain sdcard storage
            // remove old backup <app_id>/Backup
            FileHelpers.delete(target);
        }

        if (mSharedPrefsDir.isDirectory() && !FileHelpers.isEmpty(mSharedPrefsDir)) {
            File destination = new File(target, mSharedPrefsDir.getName());
            FileHelpers.copy(mSharedPrefsDir, destination, file -> Helpers.endsWithAny(file.getName(), Utils.BACKUP_PREFS), null);

            // Don't store unique id
            FileHelpers.delete(new File(destination, HiddenPrefs.SHARED_PREFERENCES_NAME + ".xml"));
        }

        if (mFilesDir.isDirectory() && !FileHelpers.isEmpty(mFilesDir)) {
            File destination = new File(target, mFilesDir.getName());
            FileHelpers.copy(mFilesDir, destination, null, dir -> Helpers.equalsAny(dir.getName(), Utils.BACKUP_DIRS));
        }
    }

    private static boolean matchesPrefs(String name, int categories) {
        if (Helpers.equalsAny(name, "yt_service_prefs.xml")) {
            return (categories & CAT_ACCOUNT) != 0;
        }

        if (Helpers.equalsAny(name, "playlist_library.xml")) {
            return (categories & CAT_LIBRARY) != 0;
        }

        return (categories & CAT_SETTINGS) != 0 && Helpers.endsWithAny(name, Utils.BACKUP_PREFS);
    }

    private static boolean matchesDir(String name, int categories) {
        if (Helpers.equalsAny(name, "yt_service_prefs")) {
            return (categories & CAT_ACCOUNT) != 0;
        }

        return (categories & CAT_SETTINGS) != 0 && Helpers.equalsAny(name, "app_prefs", "global_prefs");
    }

    private static void deleteMatching(File dir, int categories, boolean dirs) {
        File[] list = dir.listFiles();

        if (list == null) {
            return;
        }

        for (File file : list) {
            boolean match = dirs ? (file.isDirectory() && matchesDir(file.getName(), categories))
                    : (file.isFile() && matchesPrefs(file.getName(), categories));

            if (match) {
                FileHelpers.delete(file);
            }
        }
    }

    private void backupToDirPartial(File target, final int categories) {
        File prefsDest = new File(target, SHARED_PREFS_SUBDIR);
        File filesDest = new File(target, FILES_SUBDIR);

        // replace only the chosen sections, keep the others
        deleteMatching(prefsDest, categories, false);
        deleteMatching(filesDest, categories, true);

        if (mSharedPrefsDir.isDirectory() && !FileHelpers.isEmpty(mSharedPrefsDir)) {
            FileHelpers.copy(mSharedPrefsDir, prefsDest, file -> matchesPrefs(file.getName(), categories), null);

            // Don't store unique id
            FileHelpers.delete(new File(prefsDest, HiddenPrefs.SHARED_PREFERENCES_NAME + ".xml"));
        }

        if (mFilesDir.isDirectory() && !FileHelpers.isEmpty(mFilesDir)) {
            FileHelpers.copy(mFilesDir, filesDest, null, dir -> matchesDir(dir.getName(), categories));
        }
    }

    /**
     * Sections that really exist inside the backup (CAT_* flags).
     */
    public int getAvailableCategories(String backupName) {
        File backup = getBackupCheck(backupName);

        if (backup == null) {
            return 0;
        }

        int[] all = {CAT_SETTINGS, CAT_ACCOUNT, CAT_LIBRARY};
        int result = 0;

        File[] prefs = new File(backup, SHARED_PREFS_SUBDIR).listFiles();

        if (prefs != null) {
            for (File file : prefs) {
                for (int category : all) {
                    if (file.isFile() && matchesPrefs(file.getName(), category)) {
                        result |= category;
                    }
                }
            }
        }

        File[] dirs = new File(backup, FILES_SUBDIR).listFiles();

        if (dirs != null) {
            for (File dir : dirs) {
                for (int category : all) {
                    if (dir.isDirectory() && matchesDir(dir.getName(), category)) {
                        result |= category;
                    }
                }
            }
        }

        return result;
    }

    private void restoreData(String backupName, final int categories) {
        if ((categories & CAT_ALL) == CAT_ALL) {
            restoreData(backupName);
            return;
        }

        Log.d(TAG, "Restoring chosen sections: " + categories);

        File currentBackup = getBackupCheck(backupName);

        if (currentBackup == null) {
            MessageHelpers.showLongMessage(mContext, "Oops. Backup folder is empty.");
            return;
        }

        // only what the backup really contains: a section missing there must not wipe the current data
        final int use = categories & getAvailableCategories(backupName);

        if (use == 0) {
            MessageHelpers.showLongMessage(mContext, R.string.nothing_found);
            return;
        }

        File prefsBackup = new File(currentBackup, SHARED_PREFS_SUBDIR);
        File filesBackup = new File(currentBackup, FILES_SUBDIR);

        deleteMatching(mSharedPrefsDir, use, false);
        deleteMatching(mFilesDir, use, true);

        FileHelpers.copy(prefsBackup, mSharedPrefsDir, file -> matchesPrefs(file.getName(), use), null);
        FileHelpers.copy(filesBackup, mFilesDir, null, dir -> matchesDir(dir.getName(), use));

        if ((use & CAT_SETTINGS) != 0) {
            fixFileNames(mSharedPrefsDir);
        }

        MessageHelpers.showMessage(mContext, R.string.msg_done);

        // Same as the full restore: the app is killed to apply the data
        new Handler(mContext.getMainLooper()).postDelayed(() -> Runtime.getRuntime().exit(0), 1_000);
    }

    private void restoreData(String backupName) {
        Log.d(TAG, "App just updated. Restoring data...");

        File currentBackup = getBackupCheck(backupName);
        File sharedPrefsBackupDir = new File(currentBackup, SHARED_PREFS_SUBDIR);
        File filesBackupDir = new File(currentBackup, FILES_SUBDIR);

        if (FileHelpers.isEmpty(sharedPrefsBackupDir)) {
            Log.d(TAG, "Oops. Backup folder is empty.");
            MessageHelpers.showLongMessage(mContext, "Oops. Backup folder is empty.");
            return;
        }

        if (mSharedPrefsDir.isDirectory()) {
            // remove old data
            FileHelpers.delete(mSharedPrefsDir);
        }

        FileHelpers.copy(sharedPrefsBackupDir, mSharedPrefsDir);
        FileHelpers.copy(filesBackupDir, mFilesDir);
        fixFileNames(mSharedPrefsDir);

        MessageHelpers.showMessage(mContext, R.string.msg_done);

        // NOTE: Don't restart the app, just kill. The reboot will broke the files.
        // To apply settings we need to kill the app
        new Handler(mContext.getMainLooper()).postDelayed(() -> Runtime.getRuntime().exit(0), 1_000);
    }

    /**
     * Fix file names from other app versions
     */
    private void fixFileNames(File dataDir) {
        Collection<File> files = FileHelpers.listFileTree(dataDir);

        String suffix = "_preferences.xml";
        String targetName = mContext.getPackageName() + suffix;

        for (File file : files) {
            if (file.getName().endsWith(suffix) && !file.getName().endsWith(targetName)) {
                FileHelpers.copy(file, new File(file.getParentFile(), targetName));
                FileHelpers.delete(file);
            }
        }
    }

    private void verifyStoragePermissionsAndReturn() {
        if (mContext instanceof MotherActivity) {
            ((MotherActivity) mContext).addOnPermissions(this);

            PermissionHelpers.verifyStoragePermissions(mContext);
        }
    }

    private File getBackup() {
        File currentBackup = null;

        for (File backupDir : getBackupDirs()) {
            currentBackup = backupDir;
            break;
        }

        return currentBackup;
    }

    private File getBackupCheck(String backupName) {
        File currentBackup = null;

        for (File backupDir : getBackupDirs()) {
            File parentFile = backupDir.getParentFile(); // backupDir: /data/<app_id>/Backup

            if (parentFile == null) {
                continue;
            }

            if (backupDir.exists() && Helpers.equals(parentFile.getName(), backupName)) {
                currentBackup = backupDir;
                break;
            }
        }

        return currentBackup;
    }

    private File getBackupCheck() {
        for (File backupDir : getBackupDirs()) {
            if (backupDir.exists()) {
                return backupDir.getParentFile();
            }
        }

        return null;
    }

    @Override
    public void onPermissions(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PermissionHelpers.REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "REQUEST_EXTERNAL_STORAGE permission has been granted");

                if (mPendingHandler != null) {
                    mPendingHandler.run();
                    mPendingHandler = null;
                }
            }
        }
    }

    public String getBackupRootPath() {
        // NOTE: Android 11+ only backup through the file manager (no shared dir)
        if (hasAccessOnlyToAppFolders() && VERSION.SDK_INT > 29) {
            String backupZipName = GeneralData.instance(mContext).getBackupZipName();
            backupZipName = backupZipName != null ? "/" + backupZipName : "";
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath()
                    + "/" + BackupAndRestoreHelper.BACKUP_FOLDER_NAME + backupZipName;
        }

        return getRestoreRootPath();
    }

    public String getRestoreRootPath() {
        // NOTE: Android 11+ only restore through the file manager (no shared dir)
        if (hasAccessOnlyToAppFolders()) {
            return getExternalStorageDirectory().toString();
        }

        return String.format("%s/data", getExternalStorageDirectory());
    }

    public void getBackupNames(OnBackupNames callback) {
        if (FileHelpers.isExternalStorageReadable()) {
            if (hasStoragePermissions(mContext)) {
                if (hasAccessOnlyToAppFolders()) {
                    // Try to restore externally copied backup zip (if any)
                    unpackBackupZip();
                }
                callback.onBackupNames(getBackupNames());
            } else {
                mPendingHandler = () -> callback.onBackupNames(getBackupNames());
                verifyStoragePermissionsAndReturn();
            }
        }
    }

    private List<String> getBackupNames() {
        List<String> names = new ArrayList<>();

        for (File backupDir : getBackupDirs()) {
            File parentFile = backupDir.getParentFile();

            if (parentFile == null) {
                continue;
            }

            if (backupDir.exists()) {
                names.add(parentFile.getName());
            }
        }

        return names;
    }

    private List<File> getBackupDirs() {
        initBackupDirs();

        return mBackupDirs;
    }

    private boolean hasStoragePermissions(Context context) {
        return hasAccessOnlyToAppFolders() || PermissionHelpers.hasStoragePermissions(context);
    }

    public boolean hasBackup() {
        return getBackupCheck() != null;
    }

    // Android 11+: only backup through the file manager (no shared dir)
    private boolean hasAccessOnlyToAppFolders() {
        return AppInfoHelpers.getRealSdkVersion(mContext) > 29 || mForceApi30;
    }

    private void saveDataToZip() {
        File mediaDir = getExternalStorageDirectory();
        File dataDir = new File(mediaDir, "data");
        if (dataDir.exists()) {
            File zipFile = new File(mediaDir,  "SmartTubeBackup.zip");
            ZipHelper2.zipDirectory(dataDir, zipFile);
        }
    }

    private void saveDataToZip(File currentBackup) { // /data/<app_id>/Backup
        if (!FileHelpers.isEmpty(currentBackup)) {
            File source = currentBackup.getParentFile();
            if (source != null) {
                File zipFile = new File(source.getParentFile(), source.getName() + ".zip");
                ZipHelper2.zipDirectory(source, zipFile);
            }
        }
    }

    private void unpackBackupZip() {
        File[] files = FileHelpers.getExternalMediaDirectory(mContext).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".zip")) {
                    mHelper.unpackTempZip(file);
                    // More than one zip file
                    //break;
                }
            }
        }
    }
}
