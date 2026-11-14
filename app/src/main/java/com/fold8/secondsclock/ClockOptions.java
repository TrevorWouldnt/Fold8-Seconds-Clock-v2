package com.fold8.secondsclock;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.util.Objects;

/** Immutable settings shared by the app and the injected SystemUI module. */
public final class ClockOptions {
    public static final String AUTHORITY = "com.fold8.secondsclock.settings";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/options");
    public static final String STATUS_SECONDS = "status_seconds";
    public static final String SHADE_SECONDS = "shade_seconds";
    public static final String DOUBLE_TAP = "double_tap";
    public static final String HAPTIC = "haptic";
    public static final String SHOW_AM_PM = "show_am_pm";
    public static final String TIME_FORMAT = "time_format";

    public final boolean statusSeconds;
    public final boolean shadeSeconds;
    public final boolean doubleTap;
    public final boolean haptic;
    public final boolean showAmPm;
    /** 0 follows the phone setting; otherwise 12 or 24. */
    public final int timeFormat;

    public ClockOptions(boolean statusSeconds, boolean shadeSeconds, boolean doubleTap,
            boolean haptic, boolean showAmPm, int timeFormat) {
        this.statusSeconds = statusSeconds;
        this.shadeSeconds = shadeSeconds;
        this.doubleTap = doubleTap;
        this.haptic = haptic;
        this.showAmPm = showAmPm;
        this.timeFormat = validFormat(timeFormat) ? timeFormat : 0;
    }

    public static ClockOptions defaults() {
        return new ClockOptions(true, true, true, true, false, 0);
    }

    public static ClockOptions fromBundle(Bundle bundle) {
        return defaults().withUpdates(bundle);
    }

    /** Unknown keys and wrongly typed values never replace a valid existing setting. */
    public ClockOptions withUpdates(Bundle updates) {
        if (updates == null) return this;
        return new ClockOptions(booleanValue(updates, STATUS_SECONDS, statusSeconds),
                booleanValue(updates, SHADE_SECONDS, shadeSeconds),
                booleanValue(updates, DOUBLE_TAP, doubleTap),
                booleanValue(updates, HAPTIC, haptic),
                booleanValue(updates, SHOW_AM_PM, showAmPm),
                formatValue(updates, timeFormat));
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(STATUS_SECONDS, statusSeconds);
        bundle.putBoolean(SHADE_SECONDS, shadeSeconds);
        bundle.putBoolean(DOUBLE_TAP, doubleTap);
        bundle.putBoolean(HAPTIC, haptic);
        bundle.putBoolean(SHOW_AM_PM, showAmPm);
        bundle.putInt(TIME_FORMAT, timeFormat);
        return bundle;
    }

    /** Read once and cache in SystemUI; observe URI for changes rather than polling. */
    public static ClockOptions read(Context context) {
        Bundle result = context.getContentResolver().call(URI, "get", null, null);
        if (result == null) throw new IllegalStateException("Clock settings provider did not respond");
        return fromBundle(result);
    }

    public static ClockOptions update(Context context, Bundle updates) {
        Bundle result = context.getContentResolver().call(URI, "update", null, updates);
        if (result == null) throw new IllegalStateException("Clock settings provider did not respond");
        return fromBundle(result);
    }

    private static boolean validFormat(int value) {
        return value == 0 || value == 12 || value == 24;
    }

    @SuppressWarnings("deprecation")
    private static boolean booleanValue(Bundle bundle, String key, boolean fallback) {
        Object value = bundle.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    @SuppressWarnings("deprecation")
    private static int formatValue(Bundle bundle, int fallback) {
        Object value = bundle.get(TIME_FORMAT);
        return value instanceof Integer && validFormat((Integer) value) ? (Integer) value : fallback;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ClockOptions)) return false;
        ClockOptions options = (ClockOptions) other;
        return statusSeconds == options.statusSeconds && shadeSeconds == options.shadeSeconds
                && doubleTap == options.doubleTap && haptic == options.haptic
                && showAmPm == options.showAmPm && timeFormat == options.timeFormat;
    }

    @Override public int hashCode() {
        return Objects.hash(statusSeconds, shadeSeconds, doubleTap, haptic, showAmPm, timeFormat);
    }
}
