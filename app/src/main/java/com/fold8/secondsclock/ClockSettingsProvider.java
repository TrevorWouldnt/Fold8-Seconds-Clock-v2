package com.fold8.secondsclock;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** Configuration reads are public; gesture writes require the real installed SystemUI UID. */
public final class ClockSettingsProvider extends ContentProvider {
    @Override public boolean onCreate() {return true;}

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        if (!ClockConfig.CONTENT_URI.equals(uri)) throw new IllegalArgumentException("Unknown URI");
        ClockConfig c = ClockConfig.loadLocal(getContext());
        MatrixCursor out = new MatrixCursor(new String[] {
                "enabled", "seconds", "scale", "weight", "format", "color",
                "bubbleEnabled", "bubbleStyle", "bubbleOpacity", "bubblePadding",
                "bubbleRadius", "bubbleColor", "doubleTapBubble",
                "bubbleAuto", "bubbleBorder", "bubbleInset",
                "shadeSeconds", "doubleTapShade", "gestureHaptic", "showAmPm"});
        out.addRow(new Object[] {c.enabled?1:0,c.seconds?1:0,c.scale,c.weight,c.format,c.color,
                c.bubbleEnabled?1:0,c.bubbleStyle,c.bubbleOpacity,c.bubblePadding,
                c.bubbleRadius,c.bubbleColor,c.doubleTapBubble?1:0,
                c.bubbleAuto?1:0,c.bubbleBorder,c.bubbleInset,
                c.shadeSeconds?1:0,c.doubleTapShade?1:0,c.gestureHaptic?1:0,c.showAmPm?1:0});
        out.setNotificationUri(getContext().getContentResolver(), ClockConfig.CONTENT_URI);
        return out;
    }

    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        if (!ClockConfig.METHOD_TOGGLE_BUBBLE.equals(method)
                && !ClockConfig.METHOD_TOGGLE_SHADE.equals(method))
            throw new IllegalArgumentException("Unknown provider method");
        final int uid = Binder.getCallingUid();
        if (!isSystemUi(uid)) throw new SecurityException("Only Samsung SystemUI can change gesture settings");
        Bundle result = new Bundle();
        if (ClockConfig.METHOD_TOGGLE_BUBBLE.equals(method)) {
            result.putBoolean(ClockConfig.RESULT_BUBBLE, ClockConfig.toggleBubbleFromSystemUi(getContext()));
        } else {
            result.putBoolean(ClockConfig.RESULT_SHADE, ClockConfig.toggleShadeFromSystemUi(getContext()));
        }
        return result;
    }

    private boolean isSystemUi(int uid) {
        PackageManager manager = getContext().getPackageManager();
        String[] packages = manager.getPackagesForUid(uid);
        if (packages == null) return false;
        for (String name : packages) {
            if (!"com.android.systemui".equals(name)) continue;
            try {
                ApplicationInfo info = manager.getApplicationInfo(name, 0);
                return info.uid == uid && (info.flags
                        & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            } catch (PackageManager.NameNotFoundException ignored) { return false; }
        }
        return false;
    }

    @Override public String getType(Uri uri) {
        return ClockConfig.CONTENT_URI.equals(uri)?
                "vnd.android.cursor.item/vnd.fold8.control.lab.config":null;
    }
    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }
    @Override public int delete(Uri uri,String selection,String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }
}
