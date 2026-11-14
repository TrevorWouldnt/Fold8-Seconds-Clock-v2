package com.fold8.secondsclock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Nonsecret reads are public; only this app and SystemUI can write preferences. */
public final class SettingsProvider extends ContentProvider {
    private static final String PREFERENCES = "clock_options";
    private SharedPreferences preferences;

    @Override public boolean onCreate() {
        // SystemUI starts before first unlock; these nonsecret preferences must
        // remain available during Direct Boot as well as after a normal reboot.
        preferences = getContext().createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFERENCES, 0);
        return true;
    }

    @Override public synchronized Bundle call(String method, String argument, Bundle extras) {
        if ("get".equals(method)) return readOptions().toBundle();
        if (!"update".equals(method)) throw new IllegalArgumentException("Unknown settings method");
        if (!canWrite(Binder.getCallingUid(), Process.myUid()) && !isSystemUi(Binder.getCallingUid())) {
            throw new SecurityException("Only Fold8 Seconds Clock and System UI may change settings");
        }
        ClockOptions options = readOptions().withUpdates(extras);
        preferences.edit()
                .putBoolean(ClockOptions.STATUS_SECONDS, options.statusSeconds)
                .putBoolean(ClockOptions.SHADE_SECONDS, options.shadeSeconds)
                .putBoolean(ClockOptions.DOUBLE_TAP, options.doubleTap)
                .putBoolean(ClockOptions.HAPTIC, options.haptic)
                .putBoolean(ClockOptions.SHOW_AM_PM, options.showAmPm)
                .putInt(ClockOptions.TIME_FORMAT, options.timeFormat)
                .apply();
        getContext().getContentResolver().notifyChange(ClockOptions.URI, null);
        return options.toBundle();
    }

    static boolean canWrite(int callerUid, int moduleUid) {
        return callerUid >= 0 && (callerUid == moduleUid || callerUid % 100_000 == Process.SYSTEM_UID);
    }

    private boolean isSystemUi(int callerUid) {
        // SystemUI may have a dedicated UID rather than the platform's UID 1000.
        PackageManager manager = getContext().getPackageManager();
        String[] packages = manager.getPackagesForUid(callerUid);
        if (packages == null) return false;
        for (String name : packages) {
            if (!"com.android.systemui".equals(name)) continue;
            try {
                ApplicationInfo info = manager.getApplicationInfo(name, 0);
                return info.uid == callerUid && (info.flags
                        & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            } catch (PackageManager.NameNotFoundException ignored) { return false; }
        }
        return false;
    }

    private ClockOptions readOptions() {
        // Build from the stored map, so a corrupt or old value's type is ignored safely.
        Bundle values = new Bundle();
        for (java.util.Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) values.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) values.putInt(entry.getKey(), (Integer) value);
        }
        return ClockOptions.fromBundle(values);
    }

    @Override public String getType(Uri uri) {
        requireOptionsUri(uri);
        return "vnd.android.cursor.item/vnd.com.fold8.secondsclock.options";
    }

    @Override public synchronized Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArguments, String sortOrder) {
        requireOptionsUri(uri);
        ClockOptions options = readOptions();
        MatrixCursor result = new MatrixCursor(new String[] {ClockOptions.STATUS_SECONDS,
                ClockOptions.SHADE_SECONDS, ClockOptions.DOUBLE_TAP, ClockOptions.HAPTIC,
                ClockOptions.SHOW_AM_PM, ClockOptions.TIME_FORMAT});
        result.addRow(new Object[] {options.statusSeconds ? 1 : 0, options.shadeSeconds ? 1 : 0,
                options.doubleTap ? 1 : 0, options.haptic ? 1 : 0, options.showAmPm ? 1 : 0,
                options.timeFormat});
        result.setNotificationUri(getContext().getContentResolver(), ClockOptions.URI);
        return result;
    }

    private static void requireOptionsUri(Uri uri) {
        if (!ClockOptions.URI.equals(uri)) throw new IllegalArgumentException("Unknown settings URI");
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Use the update call");
    }

    @Override public int update(Uri uri, ContentValues values, String where, String[] arguments) {
        throw new UnsupportedOperationException("Use the update call");
    }

    @Override public int delete(Uri uri, String where, String[] arguments) {
        throw new UnsupportedOperationException("Settings cannot be deleted");
    }
}
