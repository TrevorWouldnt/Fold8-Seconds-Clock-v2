package com.fold8.secondsclock;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.Color;
import android.os.Bundle;

/** A small configuration contract shared with the clock hook through a provider. */
public final class ClockConfig {
    public static final String AUTHORITY = "com.fold8.secondsclock.settings";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/config");
    public static final String METHOD_TOGGLE_BUBBLE = "toggleBubble";
    public static final String RESULT_BUBBLE = "bubbleEnabled";
    public static final String METHOD_TOGGLE_SHADE = "toggleShadeSeconds";
    public static final String RESULT_SHADE = "shadeSeconds";
    private static final String PREFS = "fold8_control_lab";

    public boolean enabled;
    public boolean seconds;
    // Separate shade controls preserve the existing status-bar seconds setting.
    public boolean shadeSeconds = true;
    public boolean doubleTapShade = false;
    public boolean gestureHaptic = true;
    public boolean showAmPm = false;
    public int scale;
    public int weight; // 0 OEM, 1 medium, 2 bold
    public int format; // 0 system, 1 12h, 2 24h
    public int color;  // 0 OEM, otherwise ARGB

    // Bubble defaults to OFF, so an update cannot alter a working clock.
    public boolean bubbleEnabled;
    public int bubbleStyle; // 1 tinted glass, 2 opaque, 3 automatic text contrast
    public int bubbleOpacity; // 15-95 percent for translucent styles
    public int bubblePadding; // extra horizontal padding, dp
    public int bubbleRadius; // corners, dp
    public int bubbleColor; // 0 = automatic dark/light, else ARGB
    public boolean doubleTapBubble;
    public boolean bubbleAuto; // wallpaper contrast estimate; may be inaccurate behind third-party apps
    public int bubbleBorder; // stroke width 0..3dp
    public int bubbleInset; // vertical gap inside TextView, 1..5dp

    public ClockConfig(boolean enabled, boolean seconds, int scale, int weight,
                       int format, int color, boolean bubbleEnabled, int bubbleStyle,
                       int bubbleOpacity, int bubblePadding, int bubbleRadius,
                       int bubbleColor, boolean doubleTapBubble, boolean bubbleAuto, int bubbleBorder, int bubbleInset) {
        this.enabled = enabled;
        this.seconds = seconds;
        this.scale = clamp(scale, 85, 135);
        this.weight = clamp(weight, 0, 2);
        this.format = clamp(format, 0, 2);
        this.color = color;
        this.bubbleEnabled = bubbleEnabled;
        this.bubbleStyle = clamp(bubbleStyle, 1, 4);
        this.bubbleOpacity = clamp(bubbleOpacity, 15, 95);
        this.bubblePadding = clamp(bubblePadding, 2, 12);
        this.bubbleRadius = clamp(bubbleRadius, 6, 28);
        this.bubbleColor = bubbleColor;
        this.doubleTapBubble = doubleTapBubble;
        this.bubbleAuto = bubbleAuto;
        this.bubbleBorder = clamp(bubbleBorder, 0, 3);
        this.bubbleInset = clamp(bubbleInset, 1, 5);
    }

    public static ClockConfig defaults() {
        return new ClockConfig(true, true, 110, 0, 0, 0,
                false, 1, 45, 4, 18, 0, false, false, 1, 3);
    }
    /** Keep proven v6 clock behavior when preferences are unavailable. */
    public static ClockConfig safeFallback() {
        return new ClockConfig(true, true, 100, 0, 0, 0,
                false, 1, 45, 4, 18, 0, false, false, 1, 3);
    }

    private static SharedPreferences prefs(Context context) {
        Context deviceContext = context.createDeviceProtectedStorageContext();
        return deviceContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static ClockConfig loadLocal(Context context) {
        SharedPreferences p = prefs(context);
        ClockConfig d = defaults();
        ClockConfig result = new ClockConfig(
                p.getBoolean("enabled", d.enabled),
                p.getBoolean("seconds", d.seconds),
                p.getInt("scale", d.scale),
                p.getInt("weight", d.weight),
                p.getInt("format", d.format),
                p.getInt("color", d.color),
                p.getBoolean("bubbleEnabled", d.bubbleEnabled),
                p.getInt("bubbleStyle", d.bubbleStyle),
                p.getInt("bubbleOpacity", d.bubbleOpacity),
                p.getInt("bubblePadding", d.bubblePadding),
                p.getInt("bubbleRadius", d.bubbleRadius),
                p.getInt("bubbleColor", d.bubbleColor),
                p.getBoolean("doubleTapBubble", d.doubleTapBubble),
                p.getBoolean("bubbleAuto", d.bubbleAuto),
                p.getInt("bubbleBorder", d.bubbleBorder),
                p.getInt("bubbleInset", d.bubbleInset)
        );
        result.shadeSeconds = p.getBoolean("shadeSeconds", d.shadeSeconds);
        result.doubleTapShade = p.getBoolean("doubleTapShade", d.doubleTapShade);
        result.gestureHaptic = p.getBoolean("gestureHaptic", d.gestureHaptic);
        result.showAmPm = p.getBoolean("showAmPm", d.showAmPm);
        return result;
    }

    public static synchronized boolean save(Context context, ClockConfig c) {
        boolean saved = prefs(context).edit()
                .putBoolean("enabled", c.enabled)
                .putBoolean("seconds", c.seconds)
                .putBoolean("shadeSeconds", c.shadeSeconds)
                .putBoolean("doubleTapShade", c.doubleTapShade)
                .putBoolean("gestureHaptic", c.gestureHaptic)
                .putBoolean("showAmPm", c.showAmPm)
                .putInt("scale", c.scale)
                .putInt("weight", c.weight)
                .putInt("format", c.format)
                .putInt("color", c.color)
                .putBoolean("bubbleEnabled", c.bubbleEnabled)
                .putInt("bubbleStyle", c.bubbleStyle)
                .putInt("bubbleOpacity", c.bubbleOpacity)
                .putInt("bubblePadding", c.bubblePadding)
                .putInt("bubbleRadius", c.bubbleRadius)
                .putInt("bubbleColor", c.bubbleColor)
                .putBoolean("doubleTapBubble", c.doubleTapBubble)
                .putBoolean("bubbleAuto", c.bubbleAuto)
                .putInt("bubbleBorder", c.bubbleBorder)
                .putInt("bubbleInset", c.bubbleInset)
                .commit();
        if (saved) notifyChanged(context);
        return saved;
    }

    /** SystemUI only. Persists the state toggled by the optional double-tap gesture. */
    static synchronized boolean toggleBubbleFromSystemUi(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean("doubleTapBubble", false)) {
            throw new SecurityException("Clock bubble gesture is disabled");
        }
        boolean next = !p.getBoolean("bubbleEnabled", false);
        // A manual double tap overrides Auto, making the toggle visible and predictable.
        if (!p.edit().putBoolean("bubbleEnabled", next).putBoolean("bubbleAuto", false).commit()) {
            throw new IllegalStateException("Unable to persist bubble state");
        }
        notifyChanged(context);
        return next;
    }

    static synchronized boolean toggleShadeFromSystemUi(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean("doubleTapShade", false)) {
            throw new SecurityException("Quick Settings clock gesture is disabled");
        }
        boolean next = !p.getBoolean("shadeSeconds", true);
        if (!p.edit().putBoolean("shadeSeconds", next).commit()) {
            throw new IllegalStateException("Unable to persist Quick Settings seconds");
        }
        notifyChanged(context);
        return next;
    }

    private static void notifyChanged(Context context) {
        context.getContentResolver().notifyChange(CONTENT_URI, null);
    }

    public static boolean toggleBubbleRemote(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Bundle result = resolver.call(CONTENT_URI, METHOD_TOGGLE_BUBBLE, null, null);
        if (result == null || !result.containsKey(RESULT_BUBBLE))
            throw new IllegalStateException("Bubble provider did not return a value");
        return result.getBoolean(RESULT_BUBBLE);
    }

    public static boolean toggleShadeRemote(Context context) {
        Bundle result = context.getContentResolver().call(CONTENT_URI, METHOD_TOGGLE_SHADE, null, null);
        if (result == null || !result.containsKey(RESULT_SHADE)) {
            throw new IllegalStateException("Shade provider did not return a value");
        }
        return result.getBoolean(RESULT_SHADE);
    }

    public ClockConfig copy() {
        ClockConfig result = new ClockConfig(enabled, seconds, scale, weight, format, color,
                bubbleEnabled, bubbleStyle, bubbleOpacity, bubblePadding, bubbleRadius,
                bubbleColor, doubleTapBubble, bubbleAuto, bubbleBorder, bubbleInset);
        result.shadeSeconds = shadeSeconds;
        result.doubleTapShade = doubleTapShade;
        result.gestureHaptic = gestureHaptic;
        result.showAmPm = showAmPm;
        return result;
    }

    public static ClockConfig loadRemote(Context context) {
        Cursor cursor = context.getContentResolver().query(CONTENT_URI, null, null, null, null);
        if (cursor == null) throw new IllegalStateException("Settings provider unavailable");
        try {
            if (!cursor.moveToFirst()) throw new IllegalStateException("No settings row");
            ClockConfig result = new ClockConfig(
                cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("seconds")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("scale")),
                cursor.getInt(cursor.getColumnIndexOrThrow("weight")),
                cursor.getInt(cursor.getColumnIndexOrThrow("format")),
                cursor.getInt(cursor.getColumnIndexOrThrow("color")),
                cursor.getInt(cursor.getColumnIndexOrThrow("bubbleEnabled")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("bubbleStyle")),
                cursor.getInt(cursor.getColumnIndexOrThrow("bubbleOpacity")),
                cursor.getInt(cursor.getColumnIndexOrThrow("bubblePadding")),
                cursor.getInt(cursor.getColumnIndexOrThrow("bubbleRadius")),
                cursor.getInt(cursor.getColumnIndexOrThrow("bubbleColor")),
                cursor.getInt(cursor.getColumnIndexOrThrow("doubleTapBubble")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("bubbleAuto")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("bubbleBorder")),
                cursor.getInt(cursor.getColumnIndexOrThrow("bubbleInset"))
            );
            result.shadeSeconds = optionalBoolean(cursor, "shadeSeconds", true);
            result.doubleTapShade = optionalBoolean(cursor, "doubleTapShade", false);
            result.gestureHaptic = optionalBoolean(cursor, "gestureHaptic", true);
            result.showAmPm = optionalBoolean(cursor, "showAmPm", false);
            return result;
        } finally {
            cursor.close();
        }
    }

    private static boolean optionalBoolean(Cursor cursor, String name, boolean fallback) {
        int column = cursor.getColumnIndex(name);
        return column < 0 ? fallback : cursor.getInt(column) == 1;
    }

    public static int clamp(int n,int low,int high) {return Math.max(low,Math.min(high,n));}
    public static int parseColor(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        String cleaned=text.trim();
        if (!cleaned.matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Use #RRGGBB or leave blank for automatic color");
        return Color.parseColor(cleaned);
    }
    public static String printColor(int color) {
        return color == 0 ? "" : String.format(java.util.Locale.US,"#%06X",color & 0xFFFFFF);
    }
}
