package com.fold8.secondsclock;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.Looper;
import android.os.LocaleList;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class ClockBehaviorTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private FrameLayout parent;
    private TimeZone oldZone;

    @Before public void setUp() {
        oldZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = controller.get();
        parent = new FrameLayout(activity);
        activity.setContentView(parent);
        Display display = activity.getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
        shadowOf(display).setState(Display.STATE_ON);
        shadowOf(activity.getSystemService(PowerManager.class)).setIsInteractive(true);
        setLocale(Locale.US);
        Settings.System.putString(activity.getContentResolver(), Settings.System.TIME_12_24, "24");
        shadowOf(Looper.getMainLooper()).idle();
        controller.windowFocusChanged(true);
    }

    @After public void tearDown() {
        parent.removeAllViews();
        controller.pause().stop().destroy();
        TimeZone.setDefault(oldZone);
    }

    private void setLocale(Locale locale) {
        Configuration config = new Configuration(activity.getResources().getConfiguration());
        config.setLocales(new LocaleList(locale));
        activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
    }

    @Test public void formatsSecondsAndRespondsToHourSettingAndZone() {
        ClockText formatter = new ClockText();
        long now = Instant.parse("2026-11-12T13:04:05Z").toEpochMilli();
        assertEquals("13:04:05", formatter.format(activity, now));
        Settings.System.putString(activity.getContentResolver(), Settings.System.TIME_12_24, "12");
        assertEquals("1:04:05", formatter.format(activity, now));
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        assertEquals("5:04:05", formatter.format(activity, now));
    }

    @Test public void updatesAfterLocaleAndDateChange() {
        ClockText formatter = new ClockText();
        long now = Instant.parse("2026-11-12T23:59:59Z").toEpochMilli();
        assertEquals("23:59:59", formatter.format(activity, now));
        assertEquals("00:00:00", formatter.format(activity, now + 1000));
        setLocale(Locale.forLanguageTag("ar-EG"));
        String localized = formatter.format(activity, now);
        assertNotEquals("23:59:59", localized);
        assertTrue(localized.contains("٥٩"));
    }

    @Test public void alignsTicksEvenAfterWallClockChanges() {
        assertEquals(1000, ClockText.delayUntilNextSecond(0));
        assertEquals(1, ClockText.delayUntilNextSecond(999));
        assertEquals(877, ClockText.delayUntilNextSecond(1_234_123));
        assertEquals(1, ClockText.delayUntilNextSecond(-1));
    }

    @Test public void tickerUpdatesExistingViewAndStopsOnDetach() {
        TextView clock = new TextView(activity);
        parent.addView(clock);
        assertTrue(clock.isAttachedToWindow());
        SecondsHook.refresh(clock);
        String first = clock.getText().toString();
        assertTrue(diagnostics(clock), first.matches("\\d{2}:\\d{2}:\\d{2}"));
        // Samsung's own minute notification must be corrected immediately.
        clock.setText("Samsung minute text");
        SecondsHook.refresh(clock);
        assertNotEquals("Samsung minute text", clock.getText().toString());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertNotEquals(first, clock.getText().toString());
        parent.removeView(clock);
        clock.setText("detached sentinel");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertEquals("detached sentinel", clock.getText().toString());
        parent.addView(clock);
        SecondsHook.refresh(clock);
        assertNotEquals("detached sentinel", clock.getText().toString());
    }

    @Test public void hiddenClockIsNotRepaintedAndResumesWhenShown() {
        TextView clock = new TextView(activity);
        parent.addView(clock);
        clock.setVisibility(android.view.View.GONE);
        clock.setText("hidden sentinel");
        SecondsHook.refresh(clock);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals("hidden sentinel", clock.getText().toString());
        clock.setVisibility(android.view.View.VISIBLE);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertNotEquals(diagnostics(clock), "hidden sentinel", clock.getText().toString());
    }

    @Test public void sleepingPhoneIsNotRepaintedAndResumesOnWake() {
        TextView clock = new TextView(activity);
        parent.addView(clock);
        PowerManager power = activity.getSystemService(PowerManager.class);
        shadowOf(power).setIsInteractive(false);
        clock.setText("sleep sentinel");
        SecondsHook.refresh(clock);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals("sleep sentinel", clock.getText().toString());
        shadowOf(power).setIsInteractive(true);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertNotEquals(diagnostics(clock), "sleep sentinel", clock.getText().toString());
    }

    private String diagnostics(TextView clock) {
        Display display = clock.getDisplay();
        return "text=" + clock.getText() + ", attached=" + clock.isAttachedToWindow()
                + ", shown=" + clock.isShown() + ", windowVisibility=" + clock.getWindowVisibility()
                + ", displayState=" + (display == null ? "null" : display.getState())
                + ", interactive=" + activity.getSystemService(PowerManager.class).isInteractive();
    }
}
