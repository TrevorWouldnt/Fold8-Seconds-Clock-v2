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
import android.os.PowerManager;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Display;
import android.widget.TextView;
import android.view.ViewTreeObserver;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.ArrayDeque;
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
 * Updates the existing Samsung clock, with bounded lifecycle/timer recovery.
 * All view access is on the main thread; preferences are read on the IO worker.
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
    private static final Map<View, WeakReference<ViewTreeObserver>> ROOTS = new WeakHashMap<>();
    private static final Map<Object, Boolean> MANAGERS = new WeakHashMap<>();
    private static final Map<TextView, OriginalStyle> ORIGINALS = new HashMap<>();
    private static final Map<TextView, ClockState> STATES = new HashMap<>();
    // Vector loads this entry before Android necessarily has a main Looper.
    // Handlers are acquired only from callbacks after the UI is ready.
    private static Handler main() { return MainThread.requireHandler(); }
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Fold8-ControlLab-Settings"); thread.setDaemon(true); return thread;
    });
    private static Context appContext;
    private static boolean initialized, testing, queued, watchdogQueued, asleep, reading, readDirty;
    private static long nextTickDue, nextVisibilityCheck, nextDiscovery, nextConfigCheck;
    private static long configRetry = 5000L;
    private static int recoveryLogs;
    private static LongSupplier wallTime = System::currentTimeMillis;
    private static final Runnable TICK = () -> { queued = false; tick(); };
    private static final Runnable CONFIG_RETRY = SecondsHook::requestConfig;
    private static final Runnable WATCHDOG = new Runnable() {
        @Override public void run() {
            watchdogQueued = false;
            try { recover(); }
            catch (Throwable error) { reportRecovery("watchdog error: " + error); }
            finally { armWatchdog(); }
        }
    };
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
    private static boolean loggedFirstPaint;
    private static int lastReportedScale = -1;
    private static int loggedCorrections;
    private static boolean gestureFailureLogged;
    private static boolean bubbleFailureLogged;
    private static boolean dispatchFailureLogged;

    private static final class ClockState {
        final ClockText formatter = new ClockText();
        String rendered;
        long textRetryAfter, styleRetryAfter, formatterRetryAfter;
        boolean textErrorLogged, styleErrorLogged;
    }

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
        XposedBridge.log(TAG + "v18 package callback: " + lp.processName);
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
                        postClock((TextView)param.thisObject, () -> {
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
        // Independent acquisition path verified in the supplied Samsung signatures.
        try {
            Class<?> manager = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.phone.PhoneStatusBarClockManager", lp.classLoader);
            XC_MethodHook hook = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    WeakReference<Object> ref = new WeakReference<>(param.thisObject);
                    TextView anchor = managerClock(param.thisObject);
                    try {
                        MainThread.post(anchor, () -> {
                            Object owner = ref.get();
                            if (owner != null) { MANAGERS.put(owner, true); discoverManager(owner); }
                        });
                    } catch (Throwable error) { reportDispatchFailure(error); }
                }
            };
            XposedBridge.hookAllConstructors(manager, hook);
            for (String method : new String[]{"addClockView", "updateResources"})
                XposedBridge.hookAllMethods(manager, method, hook);
        } catch (Throwable error) { XposedBridge.log(TAG + "optional manager hook unavailable: " + error); }
    }

    private static void postClock(TextView clock, Runnable task) {
        try { MainThread.post(clock, task); }
        catch (Throwable error) { reportDispatchFailure(error); }
    }

    private static void reportDispatchFailure(Throwable error) {
        if (!dispatchFailureLogged) {
            dispatchFailureLogged = true;
            XposedBridge.log(TAG + "clock dispatch will retry on attachment: " + error);
            XposedBridge.log(error);
        }
    }

    private static TextView managerClock(Object owner) {
        try {
            Object view = XposedHelpers.getObjectField(owner, "mClockView");
            return view instanceof TextView && Boolean.FALSE.equals(kind(view.getClass())) ? (TextView)view : null;
        } catch (Throwable ignored) { return null; }
    }

    private static void discoverManager(Object owner) {
        TextView clock = managerClock(owner);
        if (clock != null) register(clock, false);
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
            boolean alreadyActive = ACTIVE.containsKey(clock);
            if (!KINDS.containsKey(clock)) { KINDS.put(clock, shade); clock.addOnAttachStateChangeListener(LIFECYCLE); }
            ACTIVE.put(clock, shade);
            bindRoot(clock.getRootView());
            VISIBILITY.put(clock, visible(clock));
            if (!testing) initialize(clock.getContext());
            if (!alreadyActive) {
                ClockGestures.updateConfig(config);
                ClockGestures.watch(clock, shade);
            }
            if (alreadyActive && queued) {
                if (!asleep && visible(clock)) paint(clock);
            } else tick();
            armWatchdog();
        } catch (Throwable error) { XposedBridge.log(TAG + "clock start failed: " + error); }
    }

    static void stop(TextView clock) {
        ACTIVE.remove(clock);
        VISIBILITY.remove(clock);
        ClockGestures.forget(clock);
        STATES.remove(clock);
        OriginalStyle saved = ORIGINALS.remove(clock);
        if (saved != null) {
            try { clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, saved.sizePx); } catch (Throwable ignored) { }
            try { clock.setTypeface(saved.typeface); } catch (Throwable ignored) { }
            try { if (saved.colors != null) clock.setTextColor(saved.colors); } catch (Throwable ignored) { }
            try { clock.setBackground(saved.oldBackground); } catch (Throwable ignored) { }
            try { clock.setPadding(saved.left,saved.top,saved.right,saved.bottom); } catch (Throwable ignored) { }
        }
        if (ACTIVE.isEmpty()) { main().removeCallbacks(TICK); queued = false; nextTickDue = 0; }
    }

    private static void bindRoot(View root) {
        try {
            ViewTreeObserver observer = root.getViewTreeObserver();
            WeakReference<ViewTreeObserver> saved = ROOTS.get(root);
            ViewTreeObserver previous = saved == null ? null : saved.get();
            if (previous == observer && observer.isAlive()) return;
            if (previous != null && previous.isAlive()) {
                previous.removeOnGlobalLayoutListener(LAYOUT);
                previous.removeOnPreDrawListener(DRAW);
            }
            if (observer.isAlive()) {
                observer.addOnGlobalLayoutListener(LAYOUT);
                observer.addOnPreDrawListener(DRAW);
                ROOTS.put(root, new WeakReference<>(observer));
            }
        } catch (Throwable error) { reportRecovery("window listener retry: " + error); }
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
                    new ContentObserver(main()) { @Override public void onChange(boolean self) { requestConfig(); } });
        } catch (Throwable error) { XposedBridge.log(TAG + "settings observer unavailable: " + error); }
        IntentFilter filter = new IntentFilter();
        for (String action : new String[]{Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT, Intent.ACTION_CONFIGURATION_CHANGED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED}) filter.addAction(action);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                try {
                    String action = intent.getAction();
                    if (Intent.ACTION_SCREEN_OFF.equals(action)) asleep = true;
                    else if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) asleep = false;
                    if (asleep) { main().removeCallbacks(TICK); queued = false; nextTickDue = 0; }
                    else { recover(); tick(); }
                } catch (Throwable error) { reportRecovery("screen event retry: " + error); }
                finally {
                    main().removeCallbacks(WATCHDOG); watchdogQueued = false;
                    armWatchdog();
                }
            }
        };
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else appContext.registerReceiver(receiver, filter);
        } catch (Throwable error) { XposedBridge.log(TAG + "screen receiver unavailable: " + error); }
        requestConfig();
    }

    private static void requestConfig() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main().post(SecondsHook::requestConfig); return; }
        if (reading) { readDirty = true; return; }
        main().removeCallbacks(CONFIG_RETRY);
        reading = true; readDirty = false;
        IO.execute(() -> {
            ClockConfig fresh = null;
            try { fresh = ClockConfig.loadRemote(appContext); }
            catch (Throwable error) {
                if (!reportedConfigError) { reportedConfigError = true; XposedBridge.log(TAG + "settings unavailable: " + error); }
            }
            ClockConfig result = fresh;
            main().post(() -> {
                reading = false;
                if (!readDirty && result != null) {
                    config = result; ClockGestures.updateConfig(result);
                    reportedConfigError = false; configRetry = 5000L;
                    nextConfigCheck = SystemClock.uptimeMillis() + 60000L;
                    tick();
                }
                if (readDirty) requestConfig();
                else if (result == null) {
                    nextConfigCheck = SystemClock.uptimeMillis() + configRetry;
                    main().postDelayed(CONFIG_RETRY, configRetry);
                    configRetry = Math.min(60000L, configRetry * 2);
                }
            });
        });
    }

    private static void schedule(long interval) {
        if (queued) main().removeCallbacks(TICK);
        queued = !asleep && !ACTIVE.isEmpty() && interval > 0;
        nextTickDue = 0;
        if (queued) {
            long delay = interval - Math.floorMod(wallTime.getAsLong(), interval);
            nextTickDue = SystemClock.uptimeMillis() + delay;
            queued = main().postDelayed(TICK, delay);
        }
    }
    private static void tick() {
        if (queued) main().removeCallbacks(TICK);
        queued = false;
        boolean seconds = false, visible = false;
        try {
            if (asleep) return;
            for (TextView view : new ArrayList<>(ACTIVE.keySet())) {
                try {
                    if (!view.isAttachedToWindow()) { stop(view); continue; }
                    if (visible(view)) {
                        visible = true;
                        seconds |= usesSeconds(view);
                        paint(view);
                    }
                } catch (Throwable error) { reportRecovery("isolated clock failure: " + error); }
            }
        } finally { schedule(visible ? (seconds ? 1000 : 60000) : 0); }
    }

    private static boolean usesSeconds(TextView view) {
        return config.enabled && (Boolean.TRUE.equals(ACTIVE.get(view)) ? config.shadeSeconds : config.seconds);
    }

    private static void armWatchdog() {
        if (!watchdogQueued && (initialized || !KINDS.isEmpty()))
            watchdogQueued = main().postDelayed(WATCHDOG, asleep ? 30000L : 5000L);
    }

    private static boolean recoverWake() {
        if (!asleep) return false;
        try {
            Context context = appContext;
            if (context == null && !ACTIVE.isEmpty()) context = ACTIVE.keySet().iterator().next().getContext();
            PowerManager power = context == null ? null : (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            boolean awake = power != null && power.isInteractive();
            if (!awake) for (TextView clock : new ArrayList<>(ACTIVE.keySet())) {
                Display display = clock.getDisplay();
                if (visible(clock) && display != null && display.getState() == Display.STATE_ON) { awake = true; break; }
            }
            if (awake) { asleep = false; reportRecovery("recovered missed wake event"); return true; }
        } catch (Throwable ignored) { }
        return false;
    }

    private static void recover() {
        boolean woke = recoverWake();
        long now = SystemClock.uptimeMillis();
        for (TextView clock : new ArrayList<>(KINDS.keySet())) {
            try {
                if (clock.isAttachedToWindow()) {
                    if (!ACTIVE.containsKey(clock)) start(clock, Boolean.TRUE.equals(KINDS.get(clock)));
                    bindRoot(clock.getRootView());
                } else if (ACTIVE.containsKey(clock)) stop(clock);
            } catch (Throwable error) { reportRecovery("lifecycle retry: " + error); }
        }
        if (asleep) return;
        if (now >= nextDiscovery) {
            nextDiscovery = now + 15000L;
            for (Object owner : new ArrayList<>(MANAGERS.keySet())) discoverManager(owner);
            boolean anyVisible = false;
            for (TextView view : new ArrayList<>(ACTIVE.keySet())) {
                try { anyVisible |= visible(view); }
                catch (Throwable error) { reportRecovery("visibility discovery retry: " + error); }
            }
            if (!anyVisible) {
                try { discoverInKnownRoots(); }
                catch (Throwable error) { reportRecovery("clock discovery retry: " + error); }
            }
        }
        boolean needsTick = woke;
        for (TextView view : new ArrayList<>(ACTIVE.keySet())) {
            try {
                if (!visible(view)) continue;
                ClockState state = STATES.get(view);
                needsTick |= !queued || nextTickDue == 0 || now > nextTickDue + 1500L;
                needsTick |= usesSeconds(view) && state != null && state.rendered != null
                        && now >= state.textRetryAfter && !state.rendered.contentEquals(view.getText());
            } catch (Throwable error) { reportRecovery("clock inspection retry: " + error); }
        }
        if (needsTick) { reportRecovery("rearmed clock ticker"); tick(); }
        if (!testing && !reading && now >= nextConfigCheck) requestConfig();
    }

    private static void discoverInKnownRoots() {
        // Only scan tracked SystemUI windows when all known clocks are hidden/missing.
        // A strict total node budget prevents work scaling with the full shade hierarchy.
        ArrayDeque<View> pending = new ArrayDeque<>();
        pending.addAll(new ArrayList<>(ROOTS.keySet()));
        int budget = 4096;
        while (!pending.isEmpty() && budget-- > 0) {
            View view = pending.removeFirst();
            if (!view.isAttachedToWindow()) continue;
            Boolean shade = view instanceof TextView ? kind(view.getClass()) : null;
            if (shade != null && !ACTIVE.containsKey(view)) register((TextView)view, shade);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup)view;
                for (int i = 0; i < group.getChildCount() && pending.size() < budget; i++) pending.addLast(group.getChildAt(i));
            }
        }
    }

    private static void reportRecovery(String reason) {
        if (recoveryLogs++ < 20) XposedBridge.log(TAG + "v18 " + reason + "; active=" + ACTIVE.size());
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
        long now = SystemClock.uptimeMillis();
        if (now < nextVisibilityCheck) return;
        nextVisibilityCheck = now + 250L;
        boolean changed = recoverWake();
        if (asleep) return;
        for (TextView view : new ArrayList<>(ACTIVE.keySet())) {
            try {
                boolean shown = visible(view);
                changed |= !Boolean.valueOf(shown).equals(VISIBILITY.get(view));
                VISIBILITY.put(view, shown);
                ClockState state = STATES.get(view);
                // Repair Samsung overwrites before draw without hooking every TextView.setText.
                if (shown && usesSeconds(view) && state != null && state.rendered != null
                        && now >= state.textRetryAfter && !state.rendered.contentEquals(view.getText())) paint(view);
            } catch (Throwable error) { reportRecovery("visibility retry: " + error); }
        }
        if (changed) {
            main().removeCallbacks(TICK); queued = true; nextTickDue = now; main().post(TICK);
        }
    }

    static void paint(TextView clock) {
        ClockState state = STATES.get(clock);
        if (state == null) { state = new ClockState(); STATES.put(clock, state); }
        ClockConfig c = config;
        long now = SystemClock.uptimeMillis();
        boolean shade = Boolean.TRUE.equals(ACTIVE.get(clock));
        // Time writes do not depend on capturing/applying optional visual styles.
        try {
            if (now >= state.textRetryAfter) {
                boolean seconds = c.enabled && (shade ? c.shadeSeconds : c.seconds);
                CharSequence next = null;
                if (now >= state.formatterRetryAfter) {
                    try { next = state.formatter.format(clock.getContext(), wallTime.getAsLong(), c, seconds); }
                    catch (Throwable error) {
                        state.formatterRetryAfter = now + 30000L;
                        reportRecovery("using plain time formatter: " + error);
                    }
                }
                if (next == null) {
                    boolean use24 = c.format == 2 || (c.format == 0 && DateFormat.is24HourFormat(clock.getContext()));
                    String pattern = use24 ? "HH:mm" : "h:mm";
                    if (seconds) pattern += ":ss";
                    if (!use24 && c.showAmPm) pattern += " a";
                    next = DateFormat.format(pattern, wallTime.getAsLong());
                }
                if (!next.toString().contentEquals(clock.getText())) clock.setText(next);
                state.rendered = next.toString();
                state.textRetryAfter = 0;
                if (!loggedFirstPaint) {
                    loggedFirstPaint = true;
                    XposedBridge.log(TAG + "first v18 clock paint; seconds=" + seconds + "; shade=" + shade);
                }
            }
        } catch (Throwable error) {
            // Also bounds pre-draw repair attempts for a TextView that rejects writes.
            state.textRetryAfter = now + 1000L;
            if (!state.textErrorLogged) {
                state.textErrorLogged = true;
                XposedBridge.log(TAG + "time write will retry: " + error);
            }
        }
        if (!shade && now >= state.styleRetryAfter) {
            try {
                OriginalStyle base = ORIGINALS.get(clock);
                if (base == null) { base = new OriginalStyle(clock); ORIGINALS.put(clock, base); }
                styleClock(clock, base, c);
                styleBubble(clock, base, c);
            } catch (Throwable error) {
                state.styleRetryAfter = now + 30000L;
                if (!state.styleErrorLogged) {
                    state.styleErrorLogged = true;
                    XposedBridge.log(TAG + "optional styling paused for 30s; seconds continue: " + error);
                }
            }
        }
    }

    static void setConfigForTests(ClockConfig value) { testing = true; config = value; ClockGestures.updateConfig(value); }
    static void setWallTimeForTests(LongSupplier value) { wallTime = value; }
    static void cancelTickForTests() {
        main().removeCallbacks(TICK);
        // Model a lost callback with stale bookkeeping, not a clean intentional pause.
        queued = true; nextTickDue = SystemClock.uptimeMillis() - 2000L;
    }
    static void recoverForTests() { recover(); }
    static void setScreenStateForTests(boolean value) { asleep = value; }
    static void resetForTests() {
        for (TextView view : new ArrayList<>(ACTIVE.keySet())) stop(view);
        for (TextView view : new ArrayList<>(KINDS.keySet())) view.removeOnAttachStateChangeListener(LIFECYCLE);
        for (WeakReference<ViewTreeObserver> saved : new ArrayList<>(ROOTS.values())) {
            ViewTreeObserver observer = saved.get();
            if (observer != null && observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(LAYOUT);
                observer.removeOnPreDrawListener(DRAW);
            }
        }
        ROOTS.clear(); VISIBILITY.clear();
        KINDS.clear(); ORIGINALS.clear(); STATES.clear(); MANAGERS.clear();
        main().removeCallbacks(TICK); main().removeCallbacks(WATCHDOG); main().removeCallbacks(CONFIG_RETRY);
        queued = false; watchdogQueued = false; asleep = false;
        nextTickDue = nextVisibilityCheck = nextDiscovery = nextConfigCheck = 0; recoveryLogs = 0;
        config = ClockConfig.safeFallback(); wallTime = System::currentTimeMillis; testing = true;
        loggedFirstPaint = false; ClockGestures.resetForTests();
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
