package com.fold8.secondsclock;

import android.content.Context;
import android.content.SharedPreferences;

/** App-only appearance settings. NEVER read by SystemUI or exposed by ClockSettingsProvider. */
public final class AppearancePrefs {
    public static final int MATERIAL = 0;
    public static final int GLASS = 1;
    public static final int DEPTH = 2;
    public static final int FLOATING = 0, OUTLINED = 1, SOLID = 2;
    private static final String FILE = "fold8_lab_appearance_v8";

    public int theme = GLASS;
    public int blur = 24;          // backdrop decorative effect only (dp)
    public int depth = 9;          // elevation for cards (dp)
    public int radius = 22;
    public int cardStyle = FLOATING;        // rounded corner radius (dp)
    public boolean reduceMotion = false;

    public static AppearancePrefs load(Context context) {
        SharedPreferences p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        AppearancePrefs a = new AppearancePrefs();
        a.theme = clamp(p.getInt("theme", a.theme), MATERIAL, DEPTH);
        a.blur = clamp(p.getInt("blur", a.blur), 0, 40);
        a.depth = clamp(p.getInt("depth", a.depth), 0, 18);
        a.radius = clamp(p.getInt("radius", a.radius), 12, 32);
        a.cardStyle = clamp(p.getInt("cardStyle", a.cardStyle), FLOATING, SOLID);
        a.reduceMotion = p.getBoolean("reduceMotion", a.reduceMotion);
        return a;
    }

    public void save(Context context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("theme", clamp(theme, MATERIAL, DEPTH))
            .putInt("blur", clamp(blur, 0, 40))
            .putInt("depth", clamp(depth, 0, 18))
            .putInt("radius", clamp(radius, 12, 32))
            .putInt("cardStyle", clamp(cardStyle, FLOATING, SOLID))
            .putBoolean("reduceMotion", reduceMotion)
            .apply();
    }

    private static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }
}
