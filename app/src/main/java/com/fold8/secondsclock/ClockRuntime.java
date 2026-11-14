package com.fold8.secondsclock;

import android.content.*;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** One main-thread scheduler and cached settings snapshot for all tracked clocks. */
final class ClockRuntime {
    enum Kind { STATUS, SHADE }
    private final Context context;
    private final Handler main;
    private final LongSupplier wallTime;
    private final ExecutorService io;
    private final Map<TextView, Entry> clocks = new WeakHashMap<>();
    private final Map<View, Window> windows = new WeakHashMap<>();
    private final Set<Method> dispatchHooks = new HashSet<>();
    private ClockOptions options;
    private boolean awake = true, queued, replayingClick, readQueued, readDirty;
    private long configRevision;
    private final Runnable tick = () -> { queued = false; paintAndSchedule(); };

    ClockRuntime(Context context) {
        this(context, new Handler(Looper.getMainLooper()), System::currentTimeMillis, ClockOptions.defaults(), true);
    }
    ClockRuntime(Context context, Handler main, LongSupplier wallTime, ClockOptions options, boolean connected) {
        Context app = context.getApplicationContext();
        this.context = app == null ? context : app;
        this.main = main;
        this.wallTime = wallTime;
        this.options = options;
        io = connected ? Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Fold8ClockSettings"); thread.setDaemon(true); return thread;
        }) : null;
        if (connected) connect();
    }

    private void connect() {
        try { context.getContentResolver().registerContentObserver(ClockOptions.URI, false, new ContentObserver(main) {
            @Override public void onChange(boolean selfChange) { readOptions(); }
        }); } catch (Throwable error) { SecondsHook.log("settings observer unavailable: " + error); }
        try { context.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24),
                false, new ContentObserver(main) {
                    @Override public void onChange(boolean selfChange) { requestPaint(); }
                }); } catch (Throwable error) { SecondsHook.log("time-format observer unavailable: " + error); }
        IntentFilter filter = new IntentFilter();
        for (String action : new String[]{Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED}) filter.addAction(action);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) setAwake(false);
                else { if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) awake = true; requestPaint(); }
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(receiver, filter);
        } catch (Throwable error) { SecondsHook.log("screen/time receiver unavailable: " + error); }
        try {
            XposedBridge.hookMethod(View.class.getDeclaredMethod("performClick"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof TextView && deferClick((TextView) param.thisObject)) param.setResult(true);
                }
            });
        } catch (Throwable error) { SecondsHook.log("single-click deferral unavailable: " + error); }
        readOptions();
    }

    private void readOptions() {
        if (readQueued) { readDirty = true; return; }
        readDirty = false;
        readQueued = true;
        long revision = configRevision;
        io.execute(() -> {
            ClockOptions loaded = null;
            try { loaded = ClockOptions.read(context); }
            catch (Throwable error) { SecondsHook.log("settings read failed: " + error); }
            ClockOptions result = loaded;
            main.post(() -> {
                readQueued = false;
                if (result != null && revision == configRevision && !readDirty) applyOptions(result);
                if (readDirty || revision != configRevision) readOptions();
            });
        });
    }
    void applyOptions(ClockOptions value) {
        if (value.equals(options)) return;
        options = value;
        for (Window window : windows.values()) window.taps.reset();
        requestPaint();
    }
    ClockOptions options() { return options; }

    void watch(TextView clock, Kind kind) {
        try {
            if (!clocks.containsKey(clock)) {
                Entry entry = new Entry(clock, kind);
                clocks.put(clock, entry);
                clock.addOnAttachStateChangeListener(entry);
                SecondsHook.log("observed " + kind + " clock: " + clock.getClass().getName());
            }
            if (clock.isAttachedToWindow()) bindWindow(clock.getRootView());
            requestPaint();
        } catch (Throwable error) { SecondsHook.log("clock acquisition failed: " + error); }
    }
    private void bindWindow(View root) {
        if (windows.containsKey(root)) return;
        Window window = new Window(root);
        windows.put(root, window);
        root.getViewTreeObserver().addOnGlobalLayoutListener(window);
        root.getViewTreeObserver().addOnPreDrawListener(window);
        if (io == null) return;
        try {
            Method dispatch = null;
            for (Class<?> cursor = root.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
                try { dispatch = cursor.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class); break; }
                catch (NoSuchMethodException ignored) { /* find OEM override */ }
            }
            if (dispatch == null) throw new NoSuchMethodException("dispatchTouchEvent");
            if (!dispatchHooks.contains(dispatch)) {
                XC_MethodHook.Unhook hook = XposedBridge.hookMethod(dispatch, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View && param.args[0] instanceof MotionEvent)
                            onTouch((View) param.thisObject, (MotionEvent) param.args[0]);
                    }
                });
                if (hook == null) throw new IllegalStateException("no hook handle");
                dispatchHooks.add(dispatch);
                SecondsHook.log("touch hook: " + dispatch.getDeclaringClass().getName());
            }
        } catch (Throwable error) { SecondsHook.log("clock-window touch hook failed: " + error); }
    }

    void onTouch(View root, MotionEvent event) {
        Window window = windows.get(root);
        if (window == null || !options.doubleTap || !awake) return;
        try {
            if (window.lastTime == event.getEventTime() && window.lastDown == event.getDownTime()
                    && window.lastAction == event.getAction()) return;
            window.lastTime = event.getEventTime(); window.lastDown = event.getDownTime(); window.lastAction = event.getAction();
            Entry target = hit(root, event.getRawX(), event.getRawY());
            if (window.taps.onEvent(event.getActionMasked(), event.getRawX(), event.getRawY(),
                    event.getEventTime(), target, event.getPointerCount()) && target != null) {
                target.suppressClickUntil = SystemClock.uptimeMillis() + ViewConfiguration.getDoubleTapTimeout();
                if (target.pendingClick != null) main.removeCallbacks(target.pendingClick);
                target.pendingClick = null;
                toggle(target.kind);
                TextView clock = target.view.get();
                if (clock != null && options.haptic) clock.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                SecondsHook.log("double tap toggled " + target.kind);
            }
        } catch (Throwable error) { SecondsHook.log("gesture error: " + error); }
    }
    private Entry hit(View root, float x, float y) {
        Rect bounds = new Rect(); int[] offset = new int[2]; root.getLocationOnScreen(offset);
        Entry best = null;
        for (Entry entry : clocks.values()) {
            TextView clock = entry.view.get();
            if (clock == null || clock.getRootView() != root || !visible(clock)) continue;
            if (clock.getGlobalVisibleRect(bounds)) {
                bounds.offset(offset[0], offset[1]);
                if (bounds.contains((int) x, (int) y)
                        && (best == null || drawnAbove(clock, best.view.get()))) best = entry;
            }
        }
        return best;
    }
    private static boolean drawnAbove(View candidate, View other) {
        if (other == null) return true;
        List<View> a = new ArrayList<>(), b = new ArrayList<>();
        for (View v = candidate; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) a.add(0, v);
        for (View v = other; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) b.add(0, v);
        int i = 0;
        while (i < Math.min(a.size(), b.size()) && a.get(i) == b.get(i)) i++;
        if (i == 0 || i == a.size() || i == b.size()) return a.size() > b.size();
        View left = a.get(i), right = b.get(i);
        if (left.getZ() != right.getZ()) return left.getZ() > right.getZ();
        return ((ViewGroup) a.get(i - 1)).indexOfChild(left) > ((ViewGroup) a.get(i - 1)).indexOfChild(right);
    }
    boolean deferClick(TextView clock) {
        if (replayingClick || !options.doubleTap) return false;
        Entry entry = clocks.get(clock); Window window = windows.get(clock.getRootView());
        if (entry == null || window == null) return false;
        long now = SystemClock.uptimeMillis();
        if (entry.suppressClickUntil > now) return true;
        if (!window.taps.isPending(entry, now) && !window.taps.isSecondTapInProgress(entry)) return false;
        if (entry.pendingClick == null) {
            entry.pendingClick = new Runnable() {
                @Override public void run() {
                    long now = SystemClock.uptimeMillis();
                    window.taps.isPending(entry, now); // Expire abandoned second taps.
                    if (window.taps.isSecondTapInProgress(entry)
                            && now <= window.taps.getPendingFirstTapUpTime()
                            + ViewConfiguration.getDoubleTapTimeout() + ViewConfiguration.getLongPressTimeout()) {
                        main.postDelayed(this, ViewConfiguration.getLongPressTimeout()); return;
                    }
                    entry.pendingClick = null;
                    TextView view = entry.view.get();
                    if (view == null || !visible(view) || entry.suppressClickUntil > SystemClock.uptimeMillis()) return;
                    replayingClick = true;
                    try { view.performClick(); } finally { replayingClick = false; }
                }
            };
            main.postDelayed(entry.pendingClick, ViewConfiguration.getDoubleTapTimeout());
        }
        return true;
    }
    private void toggle(Kind kind) {
        Bundle update = new Bundle();
        update.putBoolean(kind == Kind.STATUS ? ClockOptions.STATUS_SECONDS : ClockOptions.SHADE_SECONDS,
                !(kind == Kind.STATUS ? options.statusSeconds : options.shadeSeconds));
        options = options.withUpdates(update); configRevision++; requestPaint();
        if (io != null) io.execute(() -> {
            try { ClockOptions.update(context, update); }
            catch (Throwable error) { SecondsHook.log("could not persist toggle: " + error); }
        });
    }
    void setAwake(boolean value) {
        awake = value;
        if (!value) {
            for (Window window : windows.values()) window.taps.reset();
            for (Entry entry : clocks.values()) {
                if (entry.pendingClick != null) main.removeCallbacks(entry.pendingClick);
                entry.pendingClick = null;
            }
        }
        requestPaint();
    }
    private void requestPaint() {
        if (queued) main.removeCallbacks(tick);
        queued = awake;
        if (queued) main.post(tick);
    }
    private static boolean visible(TextView view) {
        if (!view.isAttachedToWindow() || !view.isShown() || view.getWindowVisibility() != View.VISIBLE) return false;
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v.getAlpha() <= 0) return false;
        }
        return true;
    }
    private void paintAndSchedule() {
        if (!awake) return;
        long now = wallTime.getAsLong(); boolean active = false, secondsActive = false;
        for (Entry entry : clocks.values()) {
            TextView clock = entry.view.get();
            if (clock == null || entry.failed || !visible(clock)) continue;
            try {
                boolean seconds = entry.kind == Kind.STATUS ? options.statusSeconds : options.shadeSeconds;
                String text = entry.format.format(clock.getContext(), now, options, seconds);
                if (!text.contentEquals(clock.getText())) clock.setText(text);
                if (clock.getContentDescription() == null || !text.contentEquals(clock.getContentDescription())) {
                    clock.setContentDescription(text);
                }
                if (!entry.logged) { entry.logged = true; SecondsHook.log("first paint " + entry.kind + ": " + text); }
                active = true; secondsActive |= seconds;
            } catch (Throwable error) { entry.failed = true; SecondsHook.log("disabled one " + entry.kind + " clock: " + error); }
        }
        if (active) {
            long interval = secondsActive ? 1000 : 60000;
            queued = true; main.postDelayed(tick, interval - Math.floorMod(wallTime.getAsLong(), interval));
        }
    }
    void dispose() {
        main.removeCallbacks(tick); queued = false;
        for (Entry entry : clocks.values()) {
            TextView clock = entry.view.get();
            if (clock != null) clock.removeOnAttachStateChangeListener(entry);
            if (entry.pendingClick != null) main.removeCallbacks(entry.pendingClick);
        }
        for (Map.Entry<View, Window> pair : windows.entrySet()) {
            if (pair.getKey().getViewTreeObserver().isAlive()) pair.getKey().getViewTreeObserver().removeOnGlobalLayoutListener(pair.getValue());
            if (pair.getKey().getViewTreeObserver().isAlive()) pair.getKey().getViewTreeObserver().removeOnPreDrawListener(pair.getValue());
        }
        clocks.clear(); windows.clear(); if (io != null) io.shutdown();
    }
    private final class Entry implements View.OnAttachStateChangeListener {
        final WeakReference<TextView> view; final Kind kind; final ClockText format = new ClockText();
        boolean wasVisible, logged, failed; long suppressClickUntil; Runnable pendingClick;
        Entry(TextView view, Kind kind) { this.view = new WeakReference<>(view); this.kind = kind; }
        @Override public void onViewAttachedToWindow(View view) { bindWindow(view.getRootView()); requestPaint(); }
        @Override public void onViewDetachedFromWindow(View view) {
            if (pendingClick != null) main.removeCallbacks(pendingClick);
            pendingClick = null; requestPaint();
        }
    }
    private final class Window implements ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnPreDrawListener {
        final ClockTapTracker taps; long lastTime = -1, lastDown = -1; int lastAction = -1;
        Window(View root) {
            ViewConfiguration config = ViewConfiguration.get(root.getContext());
            taps = new ClockTapTracker(config.getScaledTouchSlop(), config.getScaledDoubleTapSlop(),
                    ViewConfiguration.getLongPressTimeout(), ViewConfiguration.getDoubleTapTimeout(), 40);
        }
        @Override public void onGlobalLayout() {
            boolean changed = false;
            for (Entry entry : clocks.values()) {
                TextView view = entry.view.get(); boolean isVisible = view != null && visible(view);
                changed |= isVisible != entry.wasVisible; entry.wasVisible = isVisible;
            }
            if (changed) requestPaint();
        }
        @Override public boolean onPreDraw() { onGlobalLayout(); return true; }
    }
}
