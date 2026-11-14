package com.fold8.secondsclock;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.app.WallpaperManager;
import android.app.WallpaperColors;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.view.ViewTreeObserver;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import java.lang.ref.WeakReference;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Fold8 Control Lab v13. Preserves the onAttached/notifyTimeChanged/detach hooks
 * from the user's verified-working v6. New code only reads harmless visual
 * settings and styles the existing Samsung QSClockIndicatorView TextView.
 * No APK overlays, root commands, or SystemUI file replacement.
 */
public final class SecondsHook implements IXposedHookLoadPackage {
    private static final String TAG = "[Fold8ControlLab] ";
    private static final String SYSUI = "com.android.systemui";
    private static final String VIEW_CLASS = "com.android.systemui.statusbar.policy.QSClockIndicatorView";
    private static final String SOUND_CLASS = "com.android.systemui.statusbar.policy.QSClockBellSound";
    private static final String BASE_CLASS = "com.android.systemui.statusbar.policy.QSClock";

    private static final Map<TextView, Boolean> ACTIVE = new HashMap<>();
    private static final Map<TextView, Boolean> KINDS = new WeakHashMap<>();
    private static final Map<TextView, Boolean> VISIBILITY = new WeakHashMap<>();
    private static final Map<View, Boolean> ROOTS = new WeakHashMap<>();
    private static final Map<TextView, OriginalStyle> ORIGINALS = new HashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Fold8-ControlLab-Settings"); thread.setDaemon(true); return thread;
    });
    private static Context appContext;
    private static boolean initialized, testing, queued, asleep, reading, readDirty;
    private static LongSupplier wallTime = System::currentTimeMillis;
    private static final Runnable TICK = () -> { queued = false; tick(); };
    private static final ViewTreeObserver.OnGlobalLayoutListener LAYOUT = SecondsHook::checkVisibility;
    private static final ViewTreeObserver.OnPreDrawListener DRAW = () -> { checkVisibility(); return true; };
    private static final View.OnAttachStateChangeListener LIFECYCLE = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {
            start((TextView)view, Boolean.TRUE.equals(KINDS.get(view)));
        }
        @Override public void onViewDetachedFromWindow(View view) { stop((TextView)view); }
    };
    private static ClockConfig config = ClockConfig.safeFallback();
    private static boolean reportedConfigError;
    private static boolean reportedPaintError;
    private static boolean loggedFirstPaint;
    private static int lastReportedScale = -1;
    private static int loggedCorrections;
    private static boolean gestureFailureLogged;
    private static boolean bubbleFailureLogged;

    private static final class OriginalStyle {
        final float sizePx;
        final Typeface typeface;
        final ColorStateList colors;
        int lastScale = -1;
        int lastWeight = -1;
        int lastColor = Integer.MIN_VALUE;
        final Drawable oldBackground;
        final int left, top, right, bottom;
        Drawable bubbleBackground;
        String appliedBubbleKey = "";
        final ClockText formatter = new ClockText();
        long lastAutoCheck;
        boolean autoIsNeeded = true;
        boolean autoErrorLogged;

        OriginalStyle(TextView view) {
            sizePx = view.getTextSize();
            typeface = view.getTypeface();
            colors = view.getTextColors();
            oldBackground = view.getBackground();
            left=view.getPaddingLeft(); top=view.getPaddingTop();
            right=view.getPaddingRight(); bottom=view.getPaddingBottom();
        }
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!SYSUI.equals(lp.packageName)) return;
        XposedBridge.log(TAG + "v16 package callback: " + lp.processName);
        if (lp.processName != null && !SYSUI.equals(lp.processName)) return;
        for (String name : new String[]{BASE_CLASS, VIEW_CLASS,
                "com.android.systemui.statusbar.policy.QSClockHeaderView",
                "com.android.systemui.statusbar.policy.QSClockPanelView"}) {
            try {
                Class<?> type = XposedHelpers.findClass(name, lp.classLoader);
                XC_MethodHook hook = new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof TextView)) return;
                        WeakReference<TextView> ref = new WeakReference<>((TextView)param.thisObject);
                        MAIN.post(() -> {
                            TextView view = ref.get();
                            if (view == null) return;
                            Boolean shade = kind(view.getClass());
                            if (shade != null) register(view, shade);
                        });
                    }
                };
                try { XposedBridge.hookAllConstructors(type, hook); }
                catch (Throwable error) { XposedBridge.log(TAG + name + " constructors unavailable: " + error); }
                for (String method : new String[]{"onAttachedToWindow", "notifyTimeChanged"}) {
                    try { XposedBridge.hookAllMethods(type, method, hook); }
                    catch (Throwable error) { XposedBridge.log(TAG + name + "." + method + " unavailable: " + error); }
                }
                XposedBridge.log(TAG + "registered optional clock hooks: " + name);
            } catch (Throwable error) { XposedBridge.log(TAG + "optional clock unavailable: " + name); }
        }
    }

    private static Boolean kind(Class<?> type) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            if (VIEW_CLASS.equals(cursor.getName())) return false;
            if (cursor.getName().equals("com.android.systemui.statusbar.policy.QSClockHeaderView")
                    || cursor.getName().equals("com.android.systemui.statusbar.policy.QSClockPanelView")) return true;
        }
        return null;
    }

    private static void register(TextView clock, boolean shade) {
        try {
            if (!KINDS.containsKey(clock)) { KINDS.put(clock, shade); clock.addOnAttachStateChangeListener(LIFECYCLE); }
            if (clock.isAttachedToWindow()) start(clock, shade);
        } catch (Throwable error) { XposedBridge.log(TAG + "clock registration failed: " + error); }
    }

    static void start(TextView clock) { start(clock, false); }
    static void start(TextView clock, boolean shade) {
        if (!clock.isAttachedToWindow()) return;
        try {
            if (!KINDS.containsKey(clock)) { KINDS.put(clock, shade); clock.addOnAttachStateChangeListener(LIFECYCLE); }
            if (!ORIGINALS.containsKey(clock)) ORIGINALS.put(clock, new OriginalStyle(clock));
            ACTIVE.put(clock, shade);
            View root = clock.getRootView();
            if (!ROOTS.containsKey(root)) {
                ROOTS.put(root, true);
                root.getViewTreeObserver().addOnGlobalLayoutListener(LAYOUT);
                root.getViewTreeObserver().addOnPreDrawListener(DRAW);
            }
            VISIBILITY.put(clock, visible(clock));
            if (!testing) initialize(clock.getContext());
            ClockGestures.updateConfig(config);
            ClockGestures.watch(clock, shade);
            paint(clock);
            schedule(1000);
        } catch (Throwable error) { XposedBridge.log(TAG + "clock start failed: " + error); }
    }

    static void stop(TextView clock) {
        ACTIVE.remove(clock);
        VISIBILITY.remove(clock);
        ClockGestures.forget(clock);
        OriginalStyle saved = ORIGINALS.remove(clock);
        if (saved != null) {
            clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, saved.sizePx);
            clock.setTypeface(saved.typeface);
            if (saved.colors != null) clock.setTextColor(saved.colors);
            clock.setBackground(saved.oldBackground);
            clock.setPadding(saved.left,saved.top,saved.right,saved.bottom);
        }
        if (ACTIVE.isEmpty()) { MAIN.removeCallbacks(TICK); queued = false; }
    }

    private static void initialize(Context context) {
        if (initialized) return;
        initialized = true;
        Context app = context.getApplicationContext(); appContext = app == null ? context : app;
        ClockGestures.initialize((view, shade) -> IO.execute(() -> {
            try {
                if (shade) ClockConfig.toggleShadeRemote(appContext);
                else ClockConfig.toggleBubbleRemote(appContext);
                requestConfig();
            } catch (Throwable error) { XposedBridge.log(TAG + "gesture persistence failed: " + error); }
        }));
        try {
            appContext.getContentResolver().registerContentObserver(ClockConfig.CONTENT_URI, false,
                    new ContentObserver(MAIN) { @Override public void onChange(boolean self) { requestConfig(); } });
        } catch (Throwable error) { XposedBridge.log(TAG + "settings observer unavailable: " + error); }
        IntentFilter filter = new IntentFilter();
        for (String action : new String[]{Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED}) filter.addAction(action);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                asleep = Intent.ACTION_SCREEN_OFF.equals(intent.getAction());
                if (asleep) { MAIN.removeCallbacks(TICK); queued = false; }
                else tick();
            }
        };
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else appContext.registerReceiver(receiver, filter);
        } catch (Throwable error) { XposedBridge.log(TAG + "screen receiver unavailable: " + error); }
        requestConfig();
    }

    private static void requestConfig() {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(SecondsHook::requestConfig); return; }
        if (reading) { readDirty = true; return; }
        reading = true; readDirty = false;
        IO.execute(() -> {
            ClockConfig fresh = null;
            try { fresh = ClockConfig.loadRemote(appContext); }
            catch (Throwable error) {
                if (!reportedConfigError) { reportedConfigError = true; XposedBridge.log(TAG + "settings unavailable: " + error); }
            }
            ClockConfig result = fresh;
            MAIN.post(() -> {
                reading = false;
                if (!readDirty && result != null) { config = result; ClockGestures.updateConfig(result); tick(); }
                if (readDirty) requestConfig();
            });
        });
    }

    private static void schedule(long interval) {
        if (queued) MAIN.removeCallbacks(TICK);
        queued = !asleep && !ACTIVE.isEmpty() && interval > 0;
        if (queued) MAIN.postDelayed(TICK, interval - Math.floorMod(wallTime.getAsLong(), interval));
    }
    private static void tick() {
        if (queued) MAIN.removeCallbacks(TICK);
        queued = false;
        if (asleep) return;
        boolean seconds = false, visible = false;
        for (TextView view : new ArrayList<>(ACTIVE.keySet())) {
            if (!view.isAttachedToWindow()) { stop(view); continue; }
            if (visible(view)) {
                paint(view);
                visible = true;
                seconds |= config.enabled && (Boolean.TRUE.equals(ACTIVE.get(view)) ? config.shadeSeconds : config.seconds);
            }
        }
        schedule(visible ? (seconds ? 1000 : 60000) : 0);
    }

    private static boolean visible(TextView view) {
        if (!view.isAttachedToWindow() || !view.isShown() || view.getWindowVisibility() != View.VISIBLE) return false;
        for (View cursor = view; cursor != null;
                cursor = cursor.getParent() instanceof View ? (View)cursor.getParent() : null) {
            if (cursor.getAlpha() <= 0) return false;
        }
        return true;
    }
    private static void checkVisibility() {
        if (asleep) return;
        boolean changed = false;
        for (TextView view : ACTIVE.keySet()) {
            boolean now = visible(view);
            changed |= !Boolean.valueOf(now).equals(VISIBILITY.get(view));
            VISIBILITY.put(view, now);
        }
        if (changed) {
            MAIN.removeCallbacks(TICK); queued = true; MAIN.post(TICK);
        }
    }

    static void paint(TextView clock) {
        try {
            ClockConfig c = config;
            OriginalStyle base = ORIGINALS.get(clock);
            if (base == null) {
                base = new OriginalStyle(clock);
                ORIGINALS.put(clock, base);
            }
            boolean shade = Boolean.TRUE.equals(ACTIVE.get(clock));
            if (!shade) { styleClock(clock, base, c); styleBubble(clock, base, c); }
            CharSequence next = base.formatter.format(clock.getContext(), wallTime.getAsLong(), c,
                    c.enabled && (shade ? c.shadeSeconds : c.seconds));
            if (!next.toString().contentEquals(clock.getText())) clock.setText(next);
            if (!loggedFirstPaint) {
                loggedFirstPaint = true;
                XposedBridge.log(TAG + "first v9 clock paint: " + next);
            }
        } catch (Throwable error) {
            if (!reportedPaintError) {
                reportedPaintError = true;
                XposedBridge.log(TAG + "paint failed: " + error);
                XposedBridge.log(error);
            }
        }
    }

    static void setConfigForTests(ClockConfig value) { testing = true; config = value; ClockGestures.updateConfig(value); }
    static void setWallTimeForTests(LongSupplier value) { wallTime = value; }
    static void resetForTests() {
        for (TextView view : new ArrayList<>(ACTIVE.keySet())) stop(view);
        for (TextView view : new ArrayList<>(KINDS.keySet())) view.removeOnAttachStateChangeListener(LIFECYCLE);
        for (View root : new ArrayList<>(ROOTS.keySet())) {
            if (root.getViewTreeObserver().isAlive()) {
                root.getViewTreeObserver().removeOnGlobalLayoutListener(LAYOUT);
                root.getViewTreeObserver().removeOnPreDrawListener(DRAW);
            }
        }
        ROOTS.clear(); VISIBILITY.clear();
        KINDS.clear(); ORIGINALS.clear(); MAIN.removeCallbacks(TICK); queued = false; asleep = false;
        config = ClockConfig.safeFallback(); wallTime = System::currentTimeMillis; testing = true;
        reportedPaintError = false; loggedFirstPaint = false; ClockGestures.resetForTests();
    }

    private static void styleClock(TextView view, OriginalStyle base, ClockConfig c) {
        int scale = c.enabled ? c.scale : 100;
        int weight = c.enabled ? c.weight : 0;
        int color = c.enabled ? c.color : 0;

        // Samsung may restore its own font size after our first modification.
        // Compare the *actual* TextView size, not merely the last value we sent.
        // Do not multiply the current size or it would grow on every second tick.
        final float desiredSizePx = base.sizePx * scale / 100f;
        final float actualSizePx = view.getTextSize();
        if (base.sizePx > 1f && Math.abs(actualSizePx - desiredSizePx) > 0.5f) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, desiredSizePx);
            if (loggedCorrections < 5) {
                loggedCorrections++;
                XposedBridge.log(TAG + "v9 size correction #" + loggedCorrections
                        + ": originalPx=" + base.sizePx
                        + ", actualPx=" + actualSizePx
                        + ", targetPx=" + desiredSizePx
                        + ", scale=" + scale);
            }
        }
        base.lastScale = scale;
        if (base.lastWeight != weight) {
            Typeface typeface = base.typeface;
            if (weight == 1) typeface = Typeface.create(base.typeface, 500, false);
            if (weight == 2) typeface = Typeface.create(base.typeface, 700, false);
            view.setTypeface(typeface);
            base.lastWeight = weight;
        }
        if (base.lastColor != color) {
            if (color == 0) {
                if (base.colors != null) view.setTextColor(base.colors);
            } else {
                view.setTextColor(color);
            }
            base.lastColor = color;
        }
    }

    /** Contrast ratio from sRGB luminance (WCAG-style); wallpaper colors are estimates only. */
    private static double luminance(int color) {
        double r = channel(Color.red(color)/255.0);
        double g = channel(Color.green(color)/255.0);
        double b = channel(Color.blue(color)/255.0);
        return .2126*r + .7152*g + .0722*b;
    }
    private static double channel(double n) {
        return n <= .04045 ? n / 12.92 : Math.pow((n + .055) / 1.055, 2.4);
    }
    private static boolean needsWallpaperContrast(TextView clock, OriginalStyle base) {
        long now=SystemClock.elapsedRealtime();
        if (now-base.lastAutoCheck < 1500L && base.lastAutoCheck != 0) return base.autoIsNeeded;
        base.lastAutoCheck=now;
        try {
            WallpaperManager wm=(WallpaperManager)clock.getContext().getSystemService(Context.WALLPAPER_SERVICE);
            WallpaperColors wc=wm==null?null:wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if(wc==null || wc.getPrimaryColor()==null) return base.autoIsNeeded; // uncertain: keep bubble visible
            double textLum=luminance(clock.getCurrentTextColor());
            double wallLum=luminance(wc.getPrimaryColor().toArgb());
            double contrast=(Math.max(textLum,wallLum)+.05)/(Math.min(textLum,wallLum)+.05);
            base.autoIsNeeded=contrast<4.5; // hide when wallpaper and clock have high contrast
        } catch (Throwable t) {
            if (!base.autoErrorLogged) {
                base.autoErrorLogged=true;
                XposedBridge.log(TAG+"auto wallpaper contrast unavailable; keeping bubble visible: "+t);
            }
        }
        return base.autoIsNeeded;
    }

    // A lightly tinted pill is deliberately NOT a blur of the wallpaper.
    // True cross-process background blur would require a separate renderer/overlay.
    private static void styleBubble(TextView clock, OriginalStyle base, ClockConfig c) {
        final boolean show = c.enabled && c.bubbleEnabled &&
                (!c.bubbleAuto || needsWallpaperContrast(clock, base));
        if (!show) {
            if (base.bubbleBackground != null) {
                clock.setBackground(base.oldBackground);
                clock.setPadding(base.left, base.top, base.right, base.bottom);
                base.bubbleBackground = null;
                base.appliedBubbleKey = "";
            }
            return;
        }
        try {
            final float density=clock.getResources().getDisplayMetrics().density;
            final int textColor=clock.getCurrentTextColor();
            final int gray=(Color.red(textColor)*299+Color.green(textColor)*587
                    +Color.blue(textColor)*114)/1000;
            final int defaultRgb=gray>=130?0xFF0A1425:0xFFF5F8FF;
            final int rgb=c.bubbleColor!=0?c.bubbleColor:defaultRgb;
            final int alpha=c.bubbleStyle==2?255:Math.round(255f*c.bubbleOpacity/100f);
            final int fill=(rgb&0x00FFFFFF)|(alpha<<24);
            final String key=c.bubbleStyle+":"+c.bubbleOpacity+":"+c.bubblePadding+":"
                    +c.bubbleRadius+":"+c.bubbleColor+":"+c.bubbleBorder+":"+c.bubbleInset
                    +":"+textColor+":"+c.scale;
            // The old implementation silently reduced 12 dp to 3-6 dp.
            // Honor the stored slider value and reapply it when Samsung resets padding.
            final int sidePx = dp(clock, c.bubblePadding);
            final int expectedLeft = base.left + sidePx;
            final int expectedRight = base.right + sidePx;
            final boolean backgroundMatches = key.equals(base.appliedBubbleKey)
                    && clock.getBackground() == base.bubbleBackground;
            if (backgroundMatches) {
                if (clock.getPaddingLeft() != expectedLeft || clock.getPaddingRight() != expectedRight
                        || clock.getPaddingTop() != base.top || clock.getPaddingBottom() != base.bottom) {
                    clock.setPadding(expectedLeft, base.top, expectedRight, base.bottom);
                    XposedBridge.log(TAG + "v13 restored padding: sideDp=" + c.bubblePadding
                            + " leftPx=" + clock.getPaddingLeft() + " rightPx=" + clock.getPaddingRight());
                }
                return;
            }
            int second=fill;
            if(c.bubbleStyle==1 || c.bubbleStyle==4) {
                second=Color.argb(alpha,Math.min(255,Color.red(rgb)+18),
                        Math.min(255,Color.green(rgb)+18),Math.min(255,Color.blue(rgb)+18));
            }
            GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{fill,second});
            shape.setCornerRadius(Math.max(dp(clock,c.bubbleRadius),dp(clock,14)));
            if(c.bubbleBorder>0) {
                int stroke=gray>=130?0x66FFFFFF:0x55475970;
                shape.setStroke(Math.max(1,dp(clock,c.bubbleBorder)),stroke);
            }
            // Inset the drawable vertically INSTEAD of growing the whole TextView.
            // Prevents the top stroke being clipped by Samsung's fixed-height status bar.
            Drawable bubble=new InsetDrawable(shape,0,dp(clock,c.bubbleInset),0,dp(clock,c.bubbleInset));
            // Apply background BEFORE explicit padding. View.setBackground() may
            // otherwise overwrite paddings with the drawable's own insets.
            clock.setBackground(bubble);
            clock.setPadding(expectedLeft, base.top, expectedRight, base.bottom);
            base.bubbleBackground=bubble;
            base.appliedBubbleKey=key;
            XposedBridge.log(TAG + "v13 bubble padding applied: requestedSideDp=" + c.bubblePadding
                    + " leftPx=" + clock.getPaddingLeft()
                    + " rightPx=" + clock.getPaddingRight()
                    + " widthPx=" + clock.getWidth());
        } catch(Throwable problem) {
            if(!bubbleFailureLogged) {
                bubbleFailureLogged=true;
                XposedBridge.log(TAG+"bubble styling skipped: "+problem);
            }
        }
    }

    private static int dp(View v,float n) {
        return Math.round(n*v.getResources().getDisplayMetrics().density);
    }

}
