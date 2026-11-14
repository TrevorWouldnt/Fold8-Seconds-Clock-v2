package com.fold8.secondsclock;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.Set;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Legacy Vector entry point; edits existing Samsung clock views only. */
public final class SecondsHook implements IXposedHookLoadPackage {
    static final String PREFIX = "com.android.systemui.statusbar.policy.";
    private static final String[] CLOCKS = {"QSClock", "QSClockIndicatorView", "QSClockHeaderView", "QSClockPanelView"};
    private static ClockRuntime runtime;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if (!"com.android.systemui".equals(load.packageName)) return;
        log("v0.3 package callback, process=" + load.processName);
        if (load.processName != null && !"com.android.systemui".equals(load.processName)) return;
        for (String name : CLOCKS) {
            try {
                Class<?> type = XposedHelpers.findClass(PREFIX + name, load.classLoader);
                XC_MethodHook acquire = new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof TextView)) return;
                        WeakReference<TextView> ref = new WeakReference<>((TextView) param.thisObject);
                        MAIN.post(() -> {
                            TextView view = ref.get();
                            if (view == null) return;
                            ClockRuntime.Kind kind = classify(view.getClass());
                            if (kind != null) safeWatch(view, kind);
                        }); // Defer until the OEM finishes the complete constructor.
                    }
                };
                install(type, name, acquire);
            } catch (Throwable error) { log("optional clock unavailable: " + name + " (" + error + ")"); }
        }
        try {
            Class<?> manager = XposedHelpers.findClass("com.android.systemui.statusbar.phone.PhoneStatusBarClockManager", load.classLoader);
            XC_MethodHook acquire = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object value = XposedHelpers.getObjectField(param.thisObject, "mClockView");
                        if (value instanceof TextView) {
                            WeakReference<TextView> ref = new WeakReference<>((TextView) value);
                            MAIN.post(() -> {
                                TextView view = ref.get();
                                if (view != null) safeWatch(view, ClockRuntime.Kind.STATUS);
                            });
                        }
                    } catch (Throwable error) { log("manager clock lookup: " + error); }
                }
            };
            log("manager constructors=" + count(XposedBridge.hookAllConstructors(manager, acquire)));
            log("manager addClockView=" + count(XposedBridge.hookAllMethods(manager, "addClockView", acquire)));
        } catch (Throwable error) { log("optional manager hook unavailable: " + error); }
    }

    private static void install(Class<?> type, String name, XC_MethodHook acquire) {
        try { log(name + " constructors=" + count(XposedBridge.hookAllConstructors(type, acquire))); }
        catch (Throwable error) { log(name + " constructors: " + error); }
        for (String method : new String[]{"notifyTimeChanged", "onAttachedToWindow"}) {
            try { log(name + "." + method + "=" + count(XposedBridge.hookAllMethods(type, method, acquire))); }
            catch (Throwable error) { log(name + "." + method + ": " + error); }
        }
    }

    static ClockRuntime.Kind classify(Class<?> type) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            String name = cursor.getName();
            if (name.equals(PREFIX + "QSClockIndicatorView")) return ClockRuntime.Kind.STATUS;
            if (name.equals(PREFIX + "QSClockHeaderView") || name.equals(PREFIX + "QSClockPanelView")) return ClockRuntime.Kind.SHADE;
        }
        return null; // No guessing at date, lock-screen, or always-on-display views.
    }

    private static ClockRuntime getRuntime(TextView view) {
        if (runtime == null) runtime = new ClockRuntime(view.getContext());
        return runtime;
    }
    private static void safeWatch(TextView view, ClockRuntime.Kind kind) {
        try { getRuntime(view).watch(view, kind); }
        catch (Throwable error) { log("clock startup failed: " + error); }
    }
    private static int count(Set<XC_MethodHook.Unhook> hooks) {
        int count = 0;
        for (XC_MethodHook.Unhook hook : hooks) if (hook != null) count++;
        return count;
    }
    static void log(String message) { XposedBridge.log("[Fold8Seconds] " + message); }
}
