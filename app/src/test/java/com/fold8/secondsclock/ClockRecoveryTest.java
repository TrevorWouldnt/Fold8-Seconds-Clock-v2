package com.fold8.secondsclock;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

/** Failure and lifecycle recovery; these tests do not establish Samsung device compatibility. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class ClockRecoveryTest {
    private ActivityController<Activity> controller;
    private ActivityController<Activity> foldedController;
    private Activity activity;
    private FrameLayout parent;
    private TimeZone originalZone;
    private final AtomicLong now = new AtomicLong(Instant.parse("2026-11-14T13:04:05Z").toEpochMilli());
    private final List<FailingStyleClock> failingStyleClocks = new ArrayList<>();

    @Before public void setUp() {
        SecondsHook.resetForTests();
        SecondsHook.setWallTimeForTests(now::get);
        SecondsHook.setConfigForTests(ClockConfig.safeFallback());
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        parent = new FrameLayout(activity);
        activity.setContentView(parent);
        Object root = ReflectionHelpers.callInstanceMethod(
                activity.getWindow().getDecorView(), "getViewRootImpl");
        ReflectionHelpers.callInstanceMethod(root, "dispatchAppVisibility",
                ClassParameter.from(boolean.class, true));
        shadowOf(Looper.getMainLooper()).idle();
        controller.windowFocusChanged(true);
        setDeviceAwake(true);
        Settings.System.putString(activity.getContentResolver(), Settings.System.TIME_12_24, "24");
    }

    @After public void tearDown() {
        for (FailingStyleClock clock : failingStyleClocks) {
            clock.throwOnStyle = false;
            clock.throwOnStyleRead = false;
        }
        SecondsHook.resetForTests();
        parent.removeAllViews();
        if (foldedController != null) foldedController.pause().stop().destroy();
        controller.pause().stop().destroy();
        TimeZone.setDefault(originalZone);
    }

    @Test public void stylingFailureCannotSuppressSecondsOrOtherClocks() {
        ClockConfig config = ClockConfig.safeFallback();
        config.scale = 125;
        SecondsHook.setConfigForTests(config);
        FailingStyleClock faulty = attach(new FailingStyleClock(activity));
        failingStyleClocks.add(faulty);
        faulty.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20);
        faulty.throwOnStyle = true;
        TextView healthy = attach(new TextView(activity));

        SecondsHook.start(faulty);
        SecondsHook.start(healthy);
        assertEquals("13:04:05", faulty.getText().toString());
        assertEquals("13:04:05", healthy.getText().toString());

        advanceSeconds(5);
        assertEquals("13:04:10", faulty.getText().toString());
        assertEquals("13:04:10", healthy.getText().toString());
        assertTrue("The failure must actually exercise the optional styling path", faulty.styleAttempts > 0);
        assertTrue("A broken optional style must not be retried on every second", faulty.styleAttempts <= 2);
    }

    @Test public void failureToCaptureOriginalStylingStillLeavesAWorkingPlainClock() {
        ClockConfig config = ClockConfig.safeFallback();
        config.scale = 125;
        SecondsHook.setConfigForTests(config);
        FailingStyleClock clock = attach(new FailingStyleClock(activity));
        failingStyleClocks.add(clock);
        clock.throwOnStyleRead = true;

        SecondsHook.start(clock);
        assertEquals("13:04:05", clock.getText().toString());
        advanceSeconds(5);
        assertEquals("13:04:10", clock.getText().toString());
        assertTrue("The original-style snapshot must have failed", clock.styleReads > 0);
        assertTrue("A failing style snapshot must also use the retry cooldown", clock.styleReads <= 2);
    }

    @Test public void transientFirstTextWriteFailureRecoversOnTheNextTickWithoutHotLoop() {
        TransientTextClock clock = attach(new TransientTextClock(activity));
        clock.setText("Samsung minute text");
        clock.textWrites = 0;
        clock.failuresRemaining = 1;

        SecondsHook.start(clock);
        assertTrue("A first failure must not spin in an immediate retry loop", clock.textWrites <= 2);
        advanceSeconds(1);
        assertEquals("13:04:06", clock.getText().toString());
        advanceSeconds(1);
        assertEquals("13:04:07", clock.getText().toString());
        assertTrue("Retries must stay bounded by the normal clock cadence", clock.textWrites <= 4);
    }

    @Test public void watchdogRestoresACancelledTickAndContinuesTicking() {
        TextView clock = attach(new TextView(activity));
        SecondsHook.start(clock);
        SecondsHook.cancelTickForTests();
        advanceSeconds(6);

        SecondsHook.recoverForTests();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("13:04:11", clock.getText().toString());
        advanceSeconds(1);
        assertEquals("13:04:12", clock.getText().toString());
    }

    @Test public void watchdogRepairsASamsungMinuteTextOverwriteBeforeTheNextTick() {
        TextView clock = attach(new TextView(activity));
        SecondsHook.start(clock);
        clock.setText("13:04");

        SecondsHook.recoverForTests();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("13:04:05", clock.getText().toString());
        advanceSeconds(1);
        assertEquals("13:04:06", clock.getText().toString());
    }

    @Test public void originalSizeSnapshotDoesNotCompoundWithSystemFontScaling() {
        Configuration configuration = new Configuration(activity.getResources().getConfiguration());
        configuration.fontScale = 1.30f;
        activity.getResources().updateConfiguration(configuration,
                activity.getResources().getDisplayMetrics());
        TextView clock = attach(new TextView(activity));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        float originalPx = clock.getTextSize();
        ClockConfig config = ClockConfig.safeFallback();
        config.scale = 130;
        SecondsHook.setConfigForTests(config);

        SecondsHook.start(clock);
        float desiredPx = originalPx * 1.30f;
        for (int i = 0; i < 5; i++) {
            advanceSeconds(1);
            SecondsHook.recoverForTests();
            assertEquals("Each tick must use the single original pixel-size snapshot",
                    desiredPx, clock.getTextSize(), .01f);
        }
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalPx);
        SecondsHook.paint(clock);
        assertEquals("A Samsung font-size reset must not replace the original snapshot",
                desiredPx, clock.getTextSize(), .01f);
        SecondsHook.stop(clock);
        assertEquals(originalPx, clock.getTextSize(), .01f);
    }

    @Test public void watchdogRecoversAMissedWakeNotificationFromActualDeviceState() {
        TextView clock = attach(new TextView(activity));
        SecondsHook.start(clock);
        SecondsHook.setScreenStateForTests(true);
        SecondsHook.cancelTickForTests();
        advanceSeconds(6);
        setDeviceAwake(true);

        SecondsHook.recoverForTests();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("13:04:11", clock.getText().toString());
        advanceSeconds(1);
        assertEquals("13:04:12", clock.getText().toString());
    }

    @Test public void recoveryDoesNotForceTicksWhileTheDeviceIsActuallyAsleep() {
        TextView clock = attach(new TextView(activity));
        SecondsHook.start(clock);
        setDeviceAwake(false);
        SecondsHook.setScreenStateForTests(true);
        SecondsHook.cancelTickForTests();
        advanceSeconds(6);

        SecondsHook.recoverForTests();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("13:04:05", clock.getText().toString());
    }

    @Test public void allHiddenClocksResumeAfterTheirFoldContainerBecomesVisible() {
        TextView clock = attach(new TextView(activity));
        SecondsHook.start(clock);
        parent.setAlpha(0f);
        SecondsHook.cancelTickForTests();
        advanceSeconds(6);
        SecondsHook.recoverForTests();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("13:04:05", clock.getText().toString());

        parent.setAlpha(1f);
        SecondsHook.recoverForTests();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("13:04:11", clock.getText().toString());
        advanceSeconds(1);
        assertEquals("13:04:12", clock.getText().toString());
    }

    @Test public void replacementAndReattachedClocksRecoverWithoutPaintingTheDetachedView() {
        TextView oldClock = attach(new TextView(activity));
        SecondsHook.start(oldClock);
        parent.removeView(oldClock);
        oldClock.setText("detached sentinel");
        TextView replacement = attach(new TextView(activity));
        SecondsHook.start(replacement);
        SecondsHook.cancelTickForTests();
        advanceSeconds(6);
        SecondsHook.recoverForTests();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("detached sentinel", oldClock.getText().toString());
        assertEquals("13:04:11", replacement.getText().toString());

        View firstRoot = replacement.getRootView();
        parent.removeView(replacement);
        foldedController = Robolectric.buildActivity(Activity.class).setup();
        Activity foldedActivity = foldedController.get();
        FrameLayout foldedRoot = new FrameLayout(foldedActivity);
        foldedActivity.setContentView(foldedRoot);
        Object viewRoot = ReflectionHelpers.callInstanceMethod(
                foldedActivity.getWindow().getDecorView(), "getViewRootImpl");
        ReflectionHelpers.callInstanceMethod(viewRoot, "dispatchAppVisibility",
                ClassParameter.from(boolean.class, true));
        foldedController.windowFocusChanged(true);
        foldedRoot.addView(replacement, new FrameLayout.LayoutParams(350, 80));
        assertTrue(replacement.isAttachedToWindow());
        assertNotSame("A fold can reattach the clock under a different window root",
                firstRoot, replacement.getRootView());
        advanceSeconds(1);
        SecondsHook.recoverForTests();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("13:04:12", replacement.getText().toString());
        assertEquals("detached sentinel", oldClock.getText().toString());
    }

    @Test public void watchdogRespectsDeliberatelyDisabledStatusAndShadeSeconds() {
        ClockConfig config = ClockConfig.safeFallback();
        config.seconds = false;
        config.shadeSeconds = false;
        SecondsHook.setConfigForTests(config);
        TextView status = attach(new TextView(activity));
        TextView shade = attach(new TextView(activity));
        SecondsHook.start(status);
        SecondsHook.start(shade, true);
        SecondsHook.cancelTickForTests();
        advanceSeconds(6);
        SecondsHook.recoverForTests();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("13:04", status.getText().toString());
        assertEquals("13:04", shade.getText().toString());

        config.enabled = false;
        config.seconds = true;
        config.shadeSeconds = true;
        SecondsHook.setConfigForTests(config);
        SecondsHook.recoverForTests();
        SecondsHook.paint(status);
        SecondsHook.paint(shade);
        assertEquals("13:04", status.getText().toString());
        assertEquals("13:04", shade.getText().toString());
    }

    @Test public void minuteOnlyModeAvoidsASecondBySecondPaintLoop() {
        ClockConfig config = ClockConfig.safeFallback();
        config.seconds = false;
        config.shadeSeconds = false;
        SecondsHook.setConfigForTests(config);
        CountingClock clock = attach(new CountingClock(activity));
        SecondsHook.start(clock);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        clock.textReads = 0;

        advanceSeconds(20);
        int reads = clock.textReads;
        assertEquals("13:04", clock.getText().toString());
        assertTrue("Minute-only clocks should not be painted twenty times in twenty seconds; reads=" + reads,
                reads < 10);
    }

    private <T extends TextView> T attach(T clock) {
        parent.addView(clock, new FrameLayout.LayoutParams(350, 80));
        assertTrue(clock.isAttachedToWindow());
        return clock;
    }

    private void advanceSeconds(long seconds) {
        now.addAndGet(seconds * 1000);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds));
    }

    private void setDeviceAwake(boolean awake) {
        PowerManager power = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        shadowOf(power).setIsInteractive(awake);
        shadowOf(activity.getWindowManager().getDefaultDisplay())
                .setState(awake ? Display.STATE_ON : Display.STATE_OFF);
    }

    private static final class FailingStyleClock extends TextView {
        boolean throwOnStyle;
        boolean throwOnStyleRead;
        int styleAttempts;
        int styleReads;
        FailingStyleClock(Context context) { super(context); }
        @Override public float getTextSize() {
            if (throwOnStyleRead) {
                styleReads++;
                throw new IllegalStateException("simulated Samsung style snapshot failure");
            }
            return super.getTextSize();
        }
        @Override public void setTextSize(int unit, float size) {
            if (throwOnStyle) {
                styleAttempts++;
                throw new IllegalStateException("simulated Samsung styling failure");
            }
            super.setTextSize(unit, size);
        }
    }

    private static final class TransientTextClock extends TextView {
        int failuresRemaining;
        int textWrites;
        TransientTextClock(Context context) { super(context); }
        @Override public void setText(CharSequence text, BufferType type) {
            textWrites++;
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new IllegalStateException("simulated transient text write failure");
            }
            super.setText(text, type);
        }
    }

    private static final class CountingClock extends TextView {
        int textReads;
        CountingClock(Context context) { super(context); }
        @Override public CharSequence getText() {
            textReads++;
            return super.getText();
        }
    }
}
