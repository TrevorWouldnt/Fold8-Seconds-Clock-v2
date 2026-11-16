package com.fold8.secondsclock;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
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
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises root hit-testing and click preservation; Vector native hooks are not simulated. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class ClockGesturesTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private FrameLayout parent;
    private ClockConfig config;
    private AtomicInteger bubbleToggles;
    private AtomicInteger shadeToggles;
    private long downTime;

    @Before public void setUp() {
        ClockGestures.resetForTests();
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = controller.get();
        parent = new FrameLayout(activity);
        activity.setContentView(parent);
        Object root = ReflectionHelpers.callInstanceMethod(activity.getWindow().getDecorView(), "getViewRootImpl");
        ReflectionHelpers.callInstanceMethod(root, "dispatchAppVisibility", ClassParameter.from(boolean.class, true));
        shadowOf(Looper.getMainLooper()).idle();
        controller.windowFocusChanged(true);
        config = ClockConfig.defaults();
        config.doubleTapBubble = true;
        config.doubleTapShade = true;
        config.gestureHaptic = false;
        ClockGestures.updateConfig(config);
        bubbleToggles = new AtomicInteger();
        shadeToggles = new AtomicInteger();
        ClockGestures.initialize((view, shade) -> {
            if (shade) shadeToggles.incrementAndGet(); else bubbleToggles.incrementAndGet();
        });
    }

    @After public void tearDown() {
        ClockGestures.resetForTests();
        parent.removeAllViews();
        controller.pause().stop().destroy();
    }

    @Test public void nonClickableClockReceivesDoubleTapAtWindowWithoutReplacingItsListener() {
        TextView status = clock(0, false);
        AtomicInteger existingTouch = new AtomicInteger();
        status.setOnTouchListener((view, event) -> { existingTouch.incrementAndGet(); return false; });
        assertFalse(status.isClickable());
        pair(status);
        assertEquals(1, bubbleToggles.get());
        assertEquals(0, shadeToggles.get());
        assertFalse("Gesture observation must not make Samsung's clock clickable", status.isClickable());
        MotionEvent down = MotionEvent.obtain(1000, 1000, MotionEvent.ACTION_DOWN, 10, 10, 0);
        status.dispatchTouchEvent(down);
        down.recycle();
        assertEquals("Existing listener is still installed", 1, existingTouch.get());
    }

    @Test public void shadeAndStatusAreIndependentAndHaveTheirOriginalSemantics() {
        TextView status = clock(0, false);
        TextView shade = clock(120, true);
        pair(status);
        waitMillis(400);
        pair(shade);
        assertEquals(1, bubbleToggles.get());
        assertEquals(1, shadeToggles.get());
    }

    @Test public void singleTapIsDeliveredAfterWaitingForAnOptionalSecondTap() {
        TextView shade = clock(0, true);
        AtomicInteger clicks = new AtomicInteger();
        shade.setOnClickListener(view -> clicks.incrementAndGet());
        tap(shade);
        assertTrue(ClockGestures.deferClick(shade));
        assertTrue("Repeated click callbacks do not schedule duplicate replays", ClockGestures.deferClick(shade));
        assertEquals(0, clicks.get());
        waitMillis(400);
        assertEquals(1, clicks.get());
        assertEquals(0, shadeToggles.get());
    }

    @Test public void doubleTapCancelsTheDeferredSamsungClick() {
        TextView shade = clock(0, true);
        AtomicInteger clicks = new AtomicInteger();
        shade.setOnClickListener(view -> clicks.incrementAndGet());
        tap(shade);
        assertTrue(ClockGestures.deferClick(shade));
        waitMillis(100);
        tap(shade);
        assertTrue(ClockGestures.deferClick(shade));
        waitMillis(1000);
        assertEquals(1, shadeToggles.get());
        assertEquals(0, clicks.get());
    }

    @Test public void abandonedSecondTapCannotKeepReschedulingASingleClickForever() {
        TextView shade = clock(0, true);
        AtomicInteger clicks = new AtomicInteger();
        shade.setOnClickListener(view -> clicks.incrementAndGet());
        tap(shade);
        assertTrue(ClockGestures.deferClick(shade));
        waitMillis(100);
        point(shade, MotionEvent.ACTION_DOWN, 10, 10, false);
        waitMillis(1500); // Deliberately no second UP, as with an interrupted long hold.
        assertEquals(1, clicks.get());
        waitMillis(1500);
        assertEquals(1, clicks.get());
        assertEquals(0, shadeToggles.get());
    }

    @Test public void duplicateDispatchObservationsDoNotCreateOrRepeatADoubleTap() {
        TextView status = clock(0, false);
        point(status, MotionEvent.ACTION_DOWN, 10, 10, true);
        waitMillis(50);
        point(status, MotionEvent.ACTION_UP, 10, 10, true);
        waitMillis(100);
        point(status, MotionEvent.ACTION_DOWN, 10, 10, true);
        waitMillis(50);
        point(status, MotionEvent.ACTION_UP, 10, 10, true);
        assertEquals(1, bubbleToggles.get());
    }

    @Test public void dragAndCancelAreNotTaps() {
        TextView status = clock(0, false);
        tap(status);
        waitMillis(100);
        point(status, MotionEvent.ACTION_DOWN, 10, 10, false);
        point(status, MotionEvent.ACTION_MOVE, 100, 10, false);
        point(status, MotionEvent.ACTION_UP, 10, 10, false);
        assertEquals(0, bubbleToggles.get());
        waitMillis(400);
        tap(status);
        waitMillis(100);
        point(status, MotionEvent.ACTION_DOWN, 10, 10, false);
        point(status, MotionEvent.ACTION_CANCEL, 10, 10, false);
        point(status, MotionEvent.ACTION_UP, 10, 10, false);
        assertEquals(0, bubbleToggles.get());
    }

    @Test public void aTransparentShadeClockDoesNotStealTheStatusClockHit() {
        TextView status = clock(0, false);
        TextView shade = clock(0, true);
        shade.setAlpha(0);
        pair(status);
        assertEquals(1, bubbleToggles.get());
        assertEquals(0, shadeToggles.get());
    }

    @Test public void topmostDisabledShadeClockDoesNotToggleAStatusClockBehindIt() {
        TextView status = clock(0, false);
        clock(0, true);
        config.doubleTapShade = false;
        ClockGestures.updateConfig(config);
        pair(status);
        assertEquals(0, bubbleToggles.get());
        assertEquals(0, shadeToggles.get());
    }

    @Test public void aClockInAnotherWindowOrInnerContainerCannotCompleteThePair() {
        TextView status = clock(0, false);
        tap(status);
        waitMillis(100);
        int[] position = new int[2]; status.getLocationOnScreen(position);
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, position[0] + 10, position[1] + 10, 0);
        ClockGestures.onTouch(parent, down); // Only the watched outer window is eligible.
        down.recycle();
        waitMillis(50);
        now = SystemClock.uptimeMillis();
        MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, position[0] + 10, position[1] + 10, 0);
        ClockGestures.onTouch(parent, up);
        up.recycle();
        assertEquals(0, bubbleToggles.get());
    }

    @Test public void forgettingOrDetachingCancelsPendingClickAndGesture() {
        TextView status = clock(0, false);
        AtomicInteger clicks = new AtomicInteger();
        status.setOnClickListener(view -> clicks.incrementAndGet());
        tap(status);
        assertTrue(ClockGestures.deferClick(status));
        parent.removeView(status);
        ClockGestures.forget(status);
        waitMillis(400);
        parent.addView(status);
        ClockGestures.watch(status, false);
        waitMillis(32);
        tap(status);
        assertEquals(0, bubbleToggles.get());
        assertEquals(0, clicks.get());
    }

    @Test public void gesturesAreOptInAndProgrammaticClicksOutsideTapWindowRemainNormal() {
        TextView status = clock(0, false);
        config.doubleTapBubble = false;
        ClockGestures.updateConfig(config);
        pair(status);
        assertEquals(0, bubbleToggles.get());
        assertFalse(ClockGestures.deferClick(status));
        config.enabled = false;
        config.doubleTapBubble = true;
        ClockGestures.updateConfig(config);
        pair(status);
        assertEquals(0, bubbleToggles.get());
        assertFalse(ClockGestures.deferClick(status));
    }

    private TextView clock(int top, boolean shade) {
        TextView view = new TextView(activity);
        view.setText("13:04:05");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(250, 90);
        params.topMargin = top;
        parent.addView(view, params);
        ClockGestures.watch(view, shade);
        waitMillis(32);
        assertTrue(view.isAttachedToWindow());
        assertEquals(View.VISIBLE, view.getWindowVisibility());
        return view;
    }

    private void pair(TextView clock) { tap(clock); waitMillis(100); tap(clock); }
    private void tap(TextView clock) {
        point(clock, MotionEvent.ACTION_DOWN, 10, 10, false);
        waitMillis(50);
        point(clock, MotionEvent.ACTION_UP, 10, 10, false);
    }
    private void point(TextView clock, int action, int x, int y, boolean twice) {
        int[] position = new int[2]; clock.getLocationOnScreen(position);
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = now;
        MotionEvent event = MotionEvent.obtain(downTime, now, action, position[0] + x, position[1] + y, 0);
        try {
            ClockGestures.onTouch(clock.getRootView(), event);
            if (twice) ClockGestures.onTouch(clock.getRootView(), event);
        } finally { event.recycle(); }
    }
    private static void waitMillis(long milliseconds) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds));
    }
}
