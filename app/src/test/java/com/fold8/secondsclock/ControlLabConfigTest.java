package com.fold8.secondsclock;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ContentProviderController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowBinder;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class ControlLabConfigTest {
    private Context context;
    private ContentProviderController<ClockSettingsProvider> controller;
    private ClockSettingsProvider provider;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.createDeviceProtectedStorageContext()
                .getSharedPreferences("fold8_control_lab", 0).edit().clear().commit();
        controller = Robolectric.buildContentProvider(ClockSettingsProvider.class)
                .create(ClockConfig.AUTHORITY);
        provider = controller.get();
        ShadowBinder.setCallingUid(Process.myUid());
        assertTrue(ClockConfig.save(context, ClockConfig.defaults()));
    }

    @After public void tearDown() {
        ShadowBinder.reset();
        controller.shutdown();
    }

    @Test public void deviceProtectedSettingsAndProviderPreserveEveryClockOption() {
        ClockConfig c = detailedConfig();
        assertSameConfig(c, c.copy());
        assertTrue(ClockConfig.save(context, c));
        assertSameConfig(c, ClockConfig.loadLocal(context));
        assertSameConfig(c, ClockConfig.loadRemote(context));
        assertEquals(129, context.createDeviceProtectedStorageContext()
                .getSharedPreferences("fold8_control_lab", 0).getInt("scale", 0));
        try (Cursor cursor = provider.query(ClockConfig.CONTENT_URI, null, null, null, null)) {
            assertEquals(ClockConfig.CONTENT_URI, cursor.getNotificationUri());
        }
    }

    @Test public void bubbleGestureTogglesBubbleAndDisablesAutoWithoutChangingSeconds() {
        ClockConfig c = detailedConfig();
        c.bubbleEnabled = false;
        c.bubbleAuto = true;
        c.doubleTapBubble = true;
        c.seconds = false;
        c.shadeSeconds = true;
        ClockConfig.save(context, c);
        installSystemUi(12_999, true);
        ShadowBinder.setCallingUid(12_999);
        assertTrue(ClockConfig.toggleBubbleRemote(context));
        ClockConfig result = ClockConfig.loadLocal(context);
        assertTrue(result.bubbleEnabled);
        assertFalse(result.bubbleAuto);
        assertFalse(result.seconds);
        assertTrue(result.shadeSeconds);
        assertEquals(c.bubblePadding, result.bubblePadding);
        assertFalse(ClockConfig.toggleBubbleRemote(context));
    }

    @Test public void shadeGestureHasIndependentOptInAndDoesNotChangeStatusOrBubble() {
        installSystemUi(12_999, true);
        ShadowBinder.setCallingUid(12_999);
        try {
            ClockConfig.toggleShadeRemote(context);
            fail("Shade gesture requires explicit opt-in");
        } catch (SecurityException expected) { }
        ClockConfig c = detailedConfig();
        c.doubleTapShade = true;
        c.shadeSeconds = true;
        c.bubbleEnabled = true;
        ClockConfig.save(context, c);
        assertFalse(ClockConfig.toggleShadeRemote(context));
        ClockConfig result = ClockConfig.loadLocal(context);
        assertFalse(result.shadeSeconds);
        assertEquals(c.seconds, result.seconds);
        assertTrue(result.bubbleEnabled);
        assertEquals(c.doubleTapBubble, result.doubleTapBubble);
    }

    @Test public void userAppsCannotToggleAndImpersonatingPackageIsRejected() {
        ClockConfig c = detailedConfig();
        c.doubleTapBubble = true;
        ClockConfig.save(context, c);
        ShadowBinder.setCallingUid(23_456);
        try {
            provider.call(ClockConfig.METHOD_TOGGLE_BUBBLE, null, null);
            fail("Arbitrary applications must not modify SystemUI clock preferences");
        } catch (SecurityException expected) { }
        installSystemUi(12_999, false);
        ShadowBinder.setCallingUid(12_999);
        try {
            provider.call(ClockConfig.METHOD_TOGGLE_BUBBLE, null, null);
            fail("A user-installed package cannot impersonate system-installed SystemUI");
        } catch (SecurityException expected) { }
        assertSameConfig(c, ClockConfig.loadLocal(context));
    }

    @Test public void savingAndGestureWritesNotifyObservers() {
        AtomicInteger notifications = new AtomicInteger();
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) { notifications.incrementAndGet(); }
        };
        context.getContentResolver().registerContentObserver(ClockConfig.CONTENT_URI, false, observer);
        ClockConfig c = detailedConfig();
        c.doubleTapBubble = true;
        ClockConfig.save(context, c);
        installSystemUi(12_999, true);
        ShadowBinder.setCallingUid(12_999);
        ClockConfig.toggleBubbleRemote(context);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, notifications.get());
        context.getContentResolver().unregisterContentObserver(observer);
    }

    @Test public void clockJsonRoundTripAndV15MigrationKeepExistingStyleAndTools() throws Exception {
        ClockConfig c = detailedConfig();
        assertSameConfig(c, LabData.parseClock(LabData.clockJson(c)));
        JSONObject legacy = new JSONObject(LabData.clockJson(c));
        legacy.remove("shadeSeconds");
        legacy.remove("doubleTapShade");
        legacy.remove("gestureHaptic");
        legacy.remove("showAmPm");
        ClockConfig migrated = LabData.parseClock(legacy.toString());
        assertEquals(c.bubblePadding, migrated.bubblePadding);
        assertEquals(c.bubbleColor, migrated.bubbleColor);
        assertEquals(c.scale, migrated.scale);
        assertEquals(c.seconds, migrated.seconds);
        assertTrue(migrated.shadeSeconds);
        assertFalse(migrated.doubleTapShade);
        assertTrue(migrated.gestureHaptic);
        assertFalse(migrated.showAmPm);
    }

    @Test public void malformedClockImportFailsInsteadOfClampingAwayAnError() throws Exception {
        JSONObject malformed = new JSONObject(LabData.clockJson(detailedConfig()));
        malformed.put("bubblePadding", 99);
        try {
            LabData.parseClock(malformed.toString());
            fail("Imports must report invalid ranges");
        } catch (IllegalArgumentException expected) { }
        malformed.put("bubblePadding", 7);
        malformed.put("shadeSeconds", "yes");
        try {
            LabData.parseClock(malformed.toString());
            fail("New boolean values must be type checked");
        } catch (Exception expected) { }
    }

    @Test public void savedLooksRoundTripNewFieldsAndLoadLegacyLooks() throws Exception {
        ClockConfig c = detailedConfig();
        assertTrue(LocalPresets.save(context, 1, c));
        assertSameConfig(c, LocalPresets.load(context, 1));
        JSONObject old = new JSONObject(context.getSharedPreferences("fold8_saved_looks_v14", 0)
                .getString("look_1", ""));
        old.remove("shadeSeconds"); old.remove("doubleTapShade"); old.remove("gestureHaptic"); old.remove("showAmPm");
        context.getSharedPreferences("fold8_saved_looks_v14", 0).edit()
                .putString("look_2", old.toString()).commit();
        ClockConfig migrated = LocalPresets.load(context, 2);
        assertNotNull(migrated);
        assertEquals(c.bubbleRadius, migrated.bubbleRadius);
        assertTrue(migrated.shadeSeconds);
        assertFalse(migrated.doubleTapShade);
        assertTrue(migrated.gestureHaptic);
    }

    private void installSystemUi(int uid, boolean system) {
        PackageInfo info = new PackageInfo();
        info.packageName = "com.android.systemui";
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = info.packageName;
        info.applicationInfo.uid = uid;
        info.applicationInfo.flags = system ? ApplicationInfo.FLAG_SYSTEM : 0;
        Shadows.shadowOf(context.getPackageManager()).installPackage(info);
    }

    private static ClockConfig detailedConfig() {
        ClockConfig c = new ClockConfig(true, false, 129, 2, 2, 0xFF123ABC,
                true, 4, 83, 11, 26, 0xFF224466, true, true, 3, 5);
        c.shadeSeconds = false;
        c.doubleTapShade = true;
        c.gestureHaptic = false;
        c.showAmPm = true;
        return c;
    }

    private static void assertSameConfig(ClockConfig expected, ClockConfig actual) {
        assertEquals(LabData.clockJson(expected), LabData.clockJson(actual));
    }
}
