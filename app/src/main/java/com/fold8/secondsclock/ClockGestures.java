package com.fold8.secondsclock;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Observes touches at each clock's actual window before Samsung can intercept
 * child touches. It never consumes touch events or changes OEM touch listeners.
 * All mutable state is confined to the main looper.
 */
final class ClockGestures {
    interface ToggleCallback { void onToggle(TextView clock, boolean shade); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<TextView, Target> TARGETS = new WeakHashMap<>();
    private static final Map<View, Window> WINDOWS = new WeakHashMap<>();
    private static final Set<Method> DISPATCH_HOOKS = new HashSet<>();
    private static final Set<Method> CLICK_HOOKS = new HashSet<>();
    private static ClockConfig config = ClockConfig.safeFallback();
    private static ToggleCallback callback;
    private static boolean replayingClick;
    private static boolean nativeHooksEnabled = true;
    private static boolean reportedDispatchFailure;
    private static boolean reportedClickFailure;

    private ClockGestures() { }

    static void initialize(ToggleCallback value) {
        if (!onMain()) { MAIN.post(() -> initialize(value)); return; }
        callback = value;
    }

    static void updateConfig(ClockConfig value) {
        if (value == null) return;
        if (!onMain()) { MAIN.post(() -> updateConfig(value)); return; }
        boolean changed = config.enabled != value.enabled
                || config.doubleTapBubble != value.doubleTapBubble
                || config.doubleTapShade != value.doubleTapShade;
        config = value.copy(); // ClockConfig is mutable; retain a stable gesture snapshot.
        if (changed) for (Window window : WINDOWS.values()) window.taps.reset();
    }

    static void watch(TextView clock, boolean shade) {
        if (!onMain()) {
            WeakReference<TextView> reference = new WeakReference<>(clock);
            MAIN.post(() -> { TextView view = reference.get(); if (view != null) watch(view, shade); });
            return;
        }
        Target target = TARGETS.get(clock);
        if (target == null) {
            target = new Target(clock, shade);
            TARGETS.put(clock, target);
            clock.addOnAttachStateChangeListener(target);
        }
        if (clock.isAttachedToWindow()) bind(clock, target);
    }

    static void forget(TextView clock) {
        if (!onMain()) {
            WeakReference<TextView> reference = new WeakReference<>(clock);
            MAIN.post(() -> { TextView view = reference.get(); if (view != null) forget(view); });
            return;
        }
        Target target = TARGETS.remove(clock);
        if (target == null) return;
        clock.removeOnAttachStateChangeListener(target);
        cancelPendingClick(target);
        View root = target.root == null ? null : target.root.get();
        Window window = root == null ? null : WINDOWS.get(root);
        if (window != null) window.taps.reset();
        prune(root);
    }

    private static void bind(TextView clock, Target target) {
        View root = clock.getRootView();
        target.root = new WeakReference<>(root);
        Window window = WINDOWS.get(root);
        if (window == null) {
            window = new Window(root);
            WINDOWS.put(root, window);
        }
        if (!nativeHooksEnabled || SystemClock.uptimeMillis() < window.nextHookAttempt) return;
        // A failed registration is not cached as a successful hook. A later
        // acquisition/attach retries, with bounded log volume.
        window.nextHookAttempt = SystemClock.uptimeMillis() + 5000;
        try {
            Method dispatch = declaration(root.getClass(), "dispatchTouchEvent", MotionEvent.class);
            if (!DISPATCH_HOOKS.contains(dispatch)) {
                XC_MethodHook.Unhook handle = XposedBridge.hookMethod(dispatch, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (param.thisObject instanceof View && param.args[0] instanceof MotionEvent) {
                                onTouch((View) param.thisObject, (MotionEvent) param.args[0]);
                            }
                        } catch (Throwable error) {
                            if (!reportedDispatchFailure) {
                                reportedDispatchFailure = true;
                                log("window observation failed; OEM dispatch continues: " + error);
                            }
                        }
                    }
                });
                if (handle == null) throw new IllegalStateException("No touch-hook handle");
                DISPATCH_HOOKS.add(dispatch);
                log("window gesture hook: " + dispatch.getDeclaringClass().getName());
            }
            Method click = declaration(clock.getClass(), "performClick");
            if (!CLICK_HOOKS.contains(click)) {
                XC_MethodHook.Unhook handle = XposedBridge.hookMethod(click, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (param.thisObject instanceof TextView && deferClick((TextView) param.thisObject)) {
                                param.setResult(Boolean.TRUE);
                            }
                        } catch (Throwable error) {
                            if (!reportedClickFailure) {
                                reportedClickFailure = true;
                                log("clock click observation failed; OEM click continues: " + error);
                            }
                        }
                    }
                });
                if (handle == null) throw new IllegalStateException("No click-hook handle");
                CLICK_HOOKS.add(click);
            }
        } catch (Throwable error) { log("gesture hook unavailable; will retry: " + error); }
    }

    private static Method declaration(Class<?> type, String name, Class<?>... args)
            throws NoSuchMethodException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try { return cursor.getDeclaredMethod(name, args); }
            catch (NoSuchMethodException ignored) { /* use the real inherited implementation */ }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    static void onTouch(View root, MotionEvent event) {
        if (!onMain() || callback == null || !config.enabled) return;
        Window window = WINDOWS.get(root);
        if (window == null) return;
        try {
            if (window.duplicate(event)) return;
            Target target = hit(root, event.getRawX(), event.getRawY());
            if (target != null && !armed(target)) target = null;
            if (target != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                target.suppressClickUntil = 0; // Do not swallow an unrelated third tap.
            }
            boolean doubleTap = window.taps.onEvent(event.getActionMasked(), event.getRawX(),
                    event.getRawY(), event.getEventTime(), target, event.getPointerCount());
            if (!doubleTap || target == null) return;
            TextView clock = target.view.get();
            if (clock == null) return;
            cancelPendingClick(target);
            target.suppressClickUntil = SystemClock.uptimeMillis() + ViewConfiguration.getDoubleTapTimeout();
            callback.onToggle(clock, target.shade);
            if (config.gestureHaptic) clock.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            log(target.shade ? "shade seconds double tap" : "clock bubble double tap");
        } catch (Throwable error) { log("gesture event ignored after error: " + error); }
    }

    static boolean deferClick(TextView clock) {
        if (!onMain() || replayingClick || callback == null) return false;
        Target target = TARGETS.get(clock);
        if (target == null || !armed(target)) return false;
        View root = target.root == null ? null : target.root.get();
        Window window = root == null ? null : WINDOWS.get(root);
        if (window == null) return false;
        long now = SystemClock.uptimeMillis();
        if (target.suppressClickUntil > now) return true;
        if (!window.taps.isPending(target, now) && !window.taps.isSecondTapInProgress(target)) return false;
        if (target.pendingClick == null) {
            long deadline = window.taps.getPendingFirstTapUpTime()
                    + ViewConfiguration.getDoubleTapTimeout() + ViewConfiguration.getLongPressTimeout();
            target.pendingClick = new Runnable() {
                @Override public void run() {
                    long time = SystemClock.uptimeMillis();
                    window.taps.isPending(target, time); // Also expires abandoned taps.
                    if (window.taps.isSecondTapInProgress(target) && time < deadline) {
                        MAIN.postDelayed(this, Math.min(50, deadline - time));
                        return;
                    }
                    target.pendingClick = null;
                    TextView view = target.view.get();
                    if (view == null || !visible(view) || target.suppressClickUntil > time) return;
                    replayingClick = true;
                    try { view.performClick(); }
                    catch (Throwable error) { log("deferred clock click failed: " + error); }
                    finally { replayingClick = false; }
                }
            };
            MAIN.postDelayed(target.pendingClick, ViewConfiguration.getDoubleTapTimeout());
        }
        return true;
    }

    private static boolean armed(Target target) {
        return config.enabled && (target.shade ? config.doubleTapShade : config.doubleTapBubble);
    }

    private static Target hit(View root, float x, float y) {
        Rect bounds = new Rect();
        int[] offset = new int[2];
        root.getLocationOnScreen(offset);
        Target best = null;
        for (Target candidate : TARGETS.values()) {
            TextView view = candidate.view.get();
            if (view == null || view.getRootView() != root || !visible(view)) continue;
            if (view.getGlobalVisibleRect(bounds)) {
                bounds.offset(offset[0], offset[1]);
                if (bounds.contains((int) x, (int) y)
                        && (best == null || drawnAbove(view, best.view.get()))) best = candidate;
            }
        }
        return best;
    }

    private static boolean visible(View view) {
        if (!view.isAttachedToWindow() || !view.isShown() || view.getWindowVisibility() != View.VISIBLE) return false;
        for (View cursor = view; cursor != null;
                cursor = cursor.getParent() instanceof View ? (View) cursor.getParent() : null) {
            if (cursor.getAlpha() <= 0) return false;
        }
        return true;
    }

    private static boolean drawnAbove(View candidate, View other) {
        if (other == null) return true;
        List<View> a = ancestry(candidate), b = ancestry(other);
        int common = 0;
        while (common < Math.min(a.size(), b.size()) && a.get(common) == b.get(common)) common++;
        if (common == 0 || common == a.size() || common == b.size()) return a.size() > b.size();
        View left = a.get(common), right = b.get(common);
        if (left.getZ() != right.getZ()) return left.getZ() > right.getZ();
        View parent = a.get(common - 1);
        return parent instanceof ViewGroup
                && ((ViewGroup) parent).indexOfChild(left) > ((ViewGroup) parent).indexOfChild(right);
    }

    private static List<View> ancestry(View view) {
        List<View> result = new ArrayList<>();
        for (View cursor = view; cursor != null;
                cursor = cursor.getParent() instanceof View ? (View) cursor.getParent() : null) result.add(0, cursor);
        return result;
    }

    private static void cancelPendingClick(Target target) {
        if (target.pendingClick != null) MAIN.removeCallbacks(target.pendingClick);
        target.pendingClick = null;
    }

    private static void prune(View root) {
        if (root == null) return;
        for (Target target : TARGETS.values()) {
            TextView view = target.view.get();
            if (view != null && view.isAttachedToWindow() && view.getRootView() == root) return;
        }
        WINDOWS.remove(root);
    }

    static void resetForTests() {
        for (Map.Entry<TextView, Target> entry : TARGETS.entrySet()) {
            entry.getKey().removeOnAttachStateChangeListener(entry.getValue());
            cancelPendingClick(entry.getValue());
        }
        TARGETS.clear(); WINDOWS.clear();
        callback = null; config = ClockConfig.safeFallback(); replayingClick = false;
        reportedDispatchFailure = false; reportedClickFailure = false;
        nativeHooksEnabled = false; // A Robolectric test must not invoke Vector's native hook engine.
    }

    private static boolean onMain() { return Looper.myLooper() == Looper.getMainLooper(); }
    private static void log(String message) { XposedBridge.log("[Fold8ControlLab] " + message); }

    private static final class Target implements View.OnAttachStateChangeListener {
        final WeakReference<TextView> view;
        final boolean shade;
        WeakReference<View> root;
        Runnable pendingClick;
        long suppressClickUntil;
        Target(TextView view, boolean shade) { this.view = new WeakReference<>(view); this.shade = shade; }
        @Override public void onViewAttachedToWindow(View view) { bind((TextView) view, this); }
        @Override public void onViewDetachedFromWindow(View view) {
            cancelPendingClick(this);
            View oldRoot = root == null ? null : root.get();
            Window window = oldRoot == null ? null : WINDOWS.get(oldRoot);
            if (window != null) window.taps.reset();
            prune(oldRoot);
        }
    }

    private static final class Window {
        final ClockTapTracker taps;
        long lastTime = -1, lastDown = -1, nextHookAttempt;
        int lastAction = -1, lastPointers, lastX, lastY;
        Window(View root) {
            ViewConfiguration configuration = ViewConfiguration.get(root.getContext());
            taps = new ClockTapTracker(configuration.getScaledTouchSlop(), configuration.getScaledDoubleTapSlop(),
                    ViewConfiguration.getLongPressTimeout(), ViewConfiguration.getDoubleTapTimeout(), 40);
        }
        boolean duplicate(MotionEvent event) {
            int x = Float.floatToIntBits(event.getRawX()), y = Float.floatToIntBits(event.getRawY());
            if (lastTime == event.getEventTime() && lastDown == event.getDownTime()
                    && lastAction == event.getAction() && lastPointers == event.getPointerCount()
                    && lastX == x && lastY == y) return true;
            lastTime = event.getEventTime(); lastDown = event.getDownTime(); lastAction = event.getAction();
            lastPointers = event.getPointerCount(); lastX = x; lastY = y;
            return false;
        }
    }
}
