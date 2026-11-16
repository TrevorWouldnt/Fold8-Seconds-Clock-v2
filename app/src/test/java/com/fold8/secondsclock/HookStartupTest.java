package com.fold8.secondsclock;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.provider.Settings;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Reproduces Vector loading the module before Android has published its main looper. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class HookStartupTest {
    @Test public void freshEntryAndGesturesLoadBeforeMainLooperThenTheClockCanStart() throws Throwable {
        Looper originalMain = Looper.getMainLooper();
        assertNotNull(originalMain);
        FreshModuleLoader loader = new FreshModuleLoader(getClass().getClassLoader());
        Class<?> hook;
        try {
            // Robolectric normally prepares this before any test/application code.
            // Remove it before loading a fresh copy of the actual module classes.
            ReflectionHelpers.callStaticMethod(Looper.class, "clearMainLooperForTest");
            assertNull("The regression requires a genuinely absent main looper", Looper.getMainLooper());
            hook = Class.forName("com.fold8.secondsclock.SecondsHook", true, loader);
            assertSame(loader, hook.getClassLoader());
            Object entry = hook.getDeclaredConstructor().newInstance();
            assertTrue(entry instanceof IXposedHookLoadPackage);
            Class<?> gestures = Class.forName("com.fold8.secondsclock.ClockGestures", true, loader);
            assertSame(loader, gestures.getClassLoader());

            XC_LoadPackage.LoadPackageParam packageLoad = new XC_LoadPackage.LoadPackageParam();
            packageLoad.packageName = "com.android.systemui";
            packageLoad.processName = "com.android.systemui";
            packageLoad.classLoader = loader;
            // The test API declines native hook installation; registration itself
            // still executes the real entry point while no main looper exists.
            ((IXposedHookLoadPackage) entry).handleLoadPackage(packageLoad);
            assertNull(Looper.getMainLooper());
        } finally {
            ReflectionHelpers.callStaticMethod(Looper.class, "setMainLooperForTest",
                    ClassParameter.from(Looper.class, originalMain));
        }
        assertSame(originalMain, Looper.getMainLooper());

        TimeZone originalZone = TimeZone.getDefault();
        ActivityController<Activity> controller = null;
        FrameLayout container = null;
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            invoke(hook, "resetForTests", new Class<?>[0]);
            AtomicLong now = new AtomicLong(Instant.parse("2026-11-14T13:04:05Z").toEpochMilli());
            invoke(hook, "setWallTimeForTests", new Class<?>[]{LongSupplier.class}, (LongSupplier) now::get);
            controller = Robolectric.buildActivity(Activity.class).setup();
            Activity activity = controller.get();
            container = new FrameLayout(activity);
            activity.setContentView(container);
            Object root = ReflectionHelpers.callInstanceMethod(
                    activity.getWindow().getDecorView(), "getViewRootImpl");
            ReflectionHelpers.callInstanceMethod(root, "dispatchAppVisibility",
                    ClassParameter.from(boolean.class, true));
            shadowOf(originalMain).idle();
            controller.windowFocusChanged(true);
            Settings.System.putString(activity.getContentResolver(), Settings.System.TIME_12_24, "24");
            TextView clock = new TextView(activity);
            container.addView(clock, new FrameLayout.LayoutParams(350, 80));
            assertTrue(clock.isAttachedToWindow());

            invoke(hook, "start", new Class<?>[]{TextView.class}, clock);
            assertEquals("13:04:05", clock.getText().toString());
            now.addAndGet(1000);
            shadowOf(originalMain).idleFor(Duration.ofSeconds(1));
            assertEquals("The same module must remain usable after main-looper publication",
                    "13:04:06", clock.getText().toString());
        } finally {
            try { invoke(hook, "resetForTests", new Class<?>[0]); }
            finally {
                if (container != null) container.removeAllViews();
                if (controller != null) controller.pause().stop().destroy();
                TimeZone.setDefault(originalZone);
            }
        }
    }

    private static Object invoke(Class<?> type, String name, Class<?>[] parameters, Object... arguments)
            throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }

    /**
     * Android/Xposed stay in Robolectric's sandbox; every app class gets a fresh
     * initialization using the real compiled bytecode. Earlier UI tests cannot
     * hide an eager static Handler by having already initialized the module.
     */
    private static final class FreshModuleLoader extends ClassLoader {
        FreshModuleLoader(ClassLoader parent) { super(parent); }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("com.fold8.secondsclock.")) return super.loadClass(name, resolve);
            synchronized (this) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    String resource = name.replace('.', '/') + ".class";
                    try (InputStream input = getParent().getResourceAsStream(resource)) {
                        if (input == null) throw new ClassNotFoundException(name + " (missing real class resource)");
                        byte[] bytes = input.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException error) {
                        throw new ClassNotFoundException(name, error);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
}
