package com.fold8.secondsclock;

import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Display;
import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Updates Samsung's existing status-bar TextView. No overlay or SystemUI APK patch. */
public final class SecondsHook implements IXposedHookLoadPackage {
    private static final String TAG = "[Fold8Seconds] ";
    private static final String SYSUI = "com.android.systemui";
    private static final String VIEW_CLASS =
            "com.android.systemui.statusbar.policy.QSClockIndicatorView";
    private static final String SOUND_CLASS =
            "com.android.systemui.statusbar.policy.QSClockBellSound";
    // Accessed only on the main looper. Values must not strongly reference keys.
    private static final Map<TextView, Ticker> TASKS = new WeakHashMap<>();
    private static final AtomicBoolean FAILED = new AtomicBoolean();
    private static Handler mainHandler;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if (!SYSUI.equals(load.packageName) || !SYSUI.equals(load.processName)) return;
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            Class<?> view = XposedHelpers.findClass(VIEW_CLASS, load.classLoader);
            Class<?> sound = XposedHelpers.findClass(SOUND_CLASS, load.classLoader);
            if (!TextView.class.isAssignableFrom(view)) {
                throw new IllegalStateException(VIEW_CLASS + " is not a TextView");
            }
            mainHandler = new Handler(Looper.getMainLooper());
            XC_MethodHook refresh = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof TextView) {
                        refreshOnMain((TextView) param.thisObject);
                    }
                }
            };
            hooks.add(XposedHelpers.findAndHookMethod(view, "onAttachedToWindow", refresh));
            hooks.add(XposedHelpers.findAndHookMethod(view, "notifyTimeChanged", sound, refresh));
            XposedBridge.log(TAG + "installed hooks for " + VIEW_CLASS);
        } catch (Throwable error) {
            for (XC_MethodHook.Unhook hook : hooks) {
                try { hook.unhook(); } catch (Throwable ignored) { /* best effort rollback */ }
            }
            fail(error);
        }
    }

    private static void refreshOnMain(TextView clock) {
        if (FAILED.get()) return;
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                refresh(clock);
            } else {
                // Some OEM clock notifications may originate from another thread.
                WeakReference<TextView> reference = new WeakReference<>(clock);
                mainHandler.post(() -> {
                    TextView attached = reference.get();
                    if (attached != null) refresh(attached);
                });
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    // Package-private so lifecycle behavior can be exercised with Android tests.
    static void refresh(TextView clock) {
        refresh(clock, System::currentTimeMillis);
    }

    static void refresh(TextView clock, LongSupplier wallTime) {
        if (FAILED.get()) return;
        try {
            if (!clock.isAttachedToWindow()) return;
            Ticker ticker = TASKS.get(clock);
            if (ticker == null) {
                ticker = new Ticker(clock, wallTime);
                TASKS.put(clock, ticker);
                clock.addOnAttachStateChangeListener(ticker);
                XposedBridge.log(TAG + "attached status-bar clock ticker");
            }
            // Cancel the previous callback: Samsung updates must not create extra tickers.
            ticker.run();
        } catch (Throwable error) {
            stop(clock);
            fail(error);
        }
    }

    private static void stop(TextView clock) {
        Ticker ticker = TASKS.remove(clock);
        if (ticker != null) {
            clock.removeCallbacks(ticker);
            clock.removeOnAttachStateChangeListener(ticker);
        }
    }

    private static void fail(Throwable error) {
        // Disable future writes after the first failure, and avoid per-second log spam.
        if (FAILED.compareAndSet(false, true)) {
            XposedBridge.log(TAG + "disabled for this SystemUI process after error");
            XposedBridge.log(error);
        }
    }

    private static final class Ticker implements Runnable, View.OnAttachStateChangeListener {
        private final WeakReference<TextView> reference;
        private final ClockText formatter = new ClockText();
        private final LongSupplier wallTime;

        Ticker(TextView clock, LongSupplier wallTime) {
            reference = new WeakReference<>(clock);
            this.wallTime = wallTime;
        }

        @Override
        public void run() {
            TextView clock = reference.get();
            if (clock == null) return;
            try {
                clock.removeCallbacks(this);
                if (FAILED.get() || !clock.isAttachedToWindow()) {
                    stop(clock);
                    return;
                }
                Display display = clock.getDisplay();
                PowerManager power = clock.getContext().getSystemService(PowerManager.class);
                // No wake lock: do not repaint during sleep or on the always-on display.
                if (clock.isShown() && clock.getWindowVisibility() == View.VISIBLE
                        && display != null && display.getState() == Display.STATE_ON
                        && power != null && power.isInteractive()) {
                    long now = wallTime.getAsLong();
                    String text = formatter.format(clock.getContext(), now);
                    if (!text.contentEquals(clock.getText())) clock.setText(text);
                    clock.setContentDescription(text);
                }
                // Wall time can change independently of uptime. Recompute every time.
                clock.postDelayed(this, ClockText.delayUntilNextSecond(wallTime.getAsLong()));
            } catch (Throwable error) {
                stop(clock);
                fail(error);
            }
        }

        @Override public void onViewAttachedToWindow(View view) { /* hook handles attach */ }
        @Override public void onViewDetachedFromWindow(View view) { stop((TextView) view); }
    }
}
