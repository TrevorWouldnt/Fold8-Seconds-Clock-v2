package com.fold8.secondsclock;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
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
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class ControlLabHookTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private FrameLayout parent;
    private TimeZone originalZone;
    private final AtomicLong now = new AtomicLong(Instant.parse("2026-11-14T13:04:05Z").toEpochMilli());

    @Before public void setUp() {
        SecondsHook.resetForTests();
        SecondsHook.setWallTimeForTests(now::get);
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
        Settings.System.putString(activity.getContentResolver(), Settings.System.TIME_12_24, "24");
    }

    @After public void tearDown() {
        SecondsHook.resetForTests();
        parent.removeAllViews();
        controller.pause().stop().destroy();
        TimeZone.setDefault(originalZone);
    }

    @Test public void statusSecondsDoNotControlShadeAndShadeRetainsOemStyle() {
        ClockConfig config = ClockConfig.safeFallback();
        config.seconds = false;
        config.shadeSeconds = true;
        config.scale = 130;
        config.color = Color.RED;
        config.bubbleEnabled = true;
        SecondsHook.setConfigForTests(config);
        TextView status = addClock();
        TextView shade = addClock();
        shade.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32);
        shade.setTextColor(Color.GREEN);
        SecondsHook.start(status);
        SecondsHook.start(shade, true);
        assertEquals("13:04", status.getText().toString());
        assertEquals("13:04:05", shade.getText().toString());
        assertEquals(32f, shade.getTextSize(), .01f);
        assertEquals(Color.GREEN, shade.getCurrentTextColor());
        assertNull(shade.getBackground());
    }

    @Test public void bubbleUsesFullPaddingAndRestoresOriginalOnStop() {
        TextView clock = addClock();
        Drawable original = new ColorDrawable(Color.BLUE);
        clock.setBackground(original);
        clock.setPadding(3, 2, 4, 2);
        ClockConfig config = ClockConfig.safeFallback();
        config.bubbleEnabled = true;
        config.bubblePadding = 12;
        SecondsHook.setConfigForTests(config);
        SecondsHook.start(clock);
        int side = Math.round(12 * activity.getResources().getDisplayMetrics().density);
        assertEquals(3 + side, clock.getPaddingLeft());
        assertEquals(4 + side, clock.getPaddingRight());
        assertEquals(2, clock.getPaddingTop());
        Drawable styled = clock.getBackground();
        assertNotSame(original, styled);
        SecondsHook.paint(clock);
        assertSame("Unchanged styling should reuse its drawable", styled, clock.getBackground());
        SecondsHook.stop(clock);
        assertSame(original, clock.getBackground());
        assertEquals(3, clock.getPaddingLeft());
        assertEquals(4, clock.getPaddingRight());
    }

    @Test public void fontScalingNeverCompoundsAcrossTicksOrSamsungCorrections() {
        TextView clock = addClock();
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20);
        ClockConfig config = ClockConfig.safeFallback();
        config.scale = 125;
        SecondsHook.setConfigForTests(config);
        SecondsHook.start(clock);
        for (int i = 0; i < 5; i++) SecondsHook.paint(clock);
        assertEquals(25f, clock.getTextSize(), .01f);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18);
        SecondsHook.paint(clock);
        assertEquals(25f, clock.getTextSize(), .01f);
    }

    @Test public void tickerUpdatesAfterClockChangesAndStopsWhenDetached() {
        SecondsHook.setConfigForTests(ClockConfig.safeFallback());
        TextView clock = addClock();
        SecondsHook.start(clock);
        assertEquals("13:04:05", clock.getText().toString());
        clock.setText("Samsung minute text");
        SecondsHook.paint(clock);
        assertEquals("13:04:05", clock.getText().toString());
        now.addAndGet(2000);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals("13:04:07", clock.getText().toString());
        parent.removeView(clock);
        clock.setText("detached sentinel");
        now.addAndGet(3000);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertEquals("detached sentinel", clock.getText().toString());
    }

    @Test public void enablingAndDisablingBubbleDoesNotAccumulatePadding() {
        TextView clock = addClock();
        clock.setPadding(2, 1, 3, 1);
        ClockConfig config = ClockConfig.safeFallback();
        config.bubbleEnabled = true;
        config.bubblePadding = 8;
        SecondsHook.setConfigForTests(config);
        SecondsHook.start(clock);
        int styledLeft = clock.getPaddingLeft();
        config.bubbleEnabled = false;
        SecondsHook.paint(clock);
        assertEquals(2, clock.getPaddingLeft());
        config.bubbleEnabled = true;
        SecondsHook.paint(clock);
        assertEquals(styledLeft, clock.getPaddingLeft());
    }

    private TextView addClock() {
        TextView clock = new TextView(activity);
        parent.addView(clock, new FrameLayout.LayoutParams(350, 80));
        assertTrue(clock.isAttachedToWindow());
        return clock;
    }
}
