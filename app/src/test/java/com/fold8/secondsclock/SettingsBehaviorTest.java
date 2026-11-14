package com.fold8.secondsclock;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ContentProviderController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowBinder;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class SettingsBehaviorTest {
    private Context context;
    private ContentProviderController<SettingsProvider> controller;
    private SettingsProvider provider;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        controller = Robolectric.buildContentProvider(SettingsProvider.class)
                .create(ClockOptions.AUTHORITY);
        provider = controller.get();
        ShadowBinder.setCallingUid(Process.myUid());
        provider.call("update", null, ClockOptions.defaults().toBundle());
    }

    @After public void tearDown() {
        ShadowBinder.reset();
        controller.shutdown();
    }

    @Test public void malformedUpdatesCannotReplaceValidValues() {
        Bundle initial = new Bundle();
        initial.putBoolean(ClockOptions.STATUS_SECONDS, false);
        initial.putInt(ClockOptions.TIME_FORMAT, 12);
        ClockOptions options = ClockOptions.defaults().withUpdates(initial);
        Bundle malformed = new Bundle();
        malformed.putString(ClockOptions.STATUS_SECONDS, "true");
        malformed.putInt(ClockOptions.TIME_FORMAT, 99);
        malformed.putBoolean(ClockOptions.SHADE_SECONDS, false);
        malformed.putString("unrecognized", "ignored");
        options = options.withUpdates(malformed);
        assertFalse(options.statusSeconds);
        assertFalse(options.shadeSeconds);
        assertEquals(12, options.timeFormat);
        assertEquals(options, ClockOptions.fromBundle(options.toBundle()));
    }

    @Test public void systemUiPartialWritePersistsWithoutChangingOtherOptions() {
        ShadowBinder.setCallingUid(Process.SYSTEM_UID);
        Bundle update = new Bundle();
        update.putBoolean(ClockOptions.STATUS_SECONDS, false);
        ClockOptions changed = ClockOptions.fromBundle(provider.call("update", null, update));
        assertFalse(changed.statusSeconds);
        assertTrue(changed.shadeSeconds);
        assertTrue(changed.doubleTap);
        assertEquals(changed, ClockOptions.read(context));
        Context directBootContext = context.createDeviceProtectedStorageContext();
        assertFalse(directBootContext.getSharedPreferences("clock_options", 0)
                .getBoolean(ClockOptions.STATUS_SECONDS, true));
    }

    @Test public void arbitraryAppsCanReadButCannotModifySettings() {
        ShadowBinder.setCallingUid(23_456);
        assertTrue(ClockOptions.fromBundle(provider.call("get", null, null)).statusSeconds);
        Bundle update = new Bundle();
        update.putBoolean(ClockOptions.STATUS_SECONDS, false);
        try {
            provider.call("update", null, update);
            fail("Untrusted caller must not write preferences");
        } catch (SecurityException expected) {
            assertTrue(ClockOptions.fromBundle(provider.call("get", null, null)).statusSeconds);
        }
        assertTrue(SettingsProvider.canWrite(101_000, 10_001));
        assertFalse(SettingsProvider.canWrite(110_001, 10_001));
        assertFalse(SettingsProvider.canWrite(-1, -1));
    }

    @Test public void writingOptionsNotifiesRuntimeObservers() {
        AtomicInteger notifications = new AtomicInteger();
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) { notifications.incrementAndGet(); }
        };
        context.getContentResolver().registerContentObserver(ClockOptions.URI, false, observer);
        Bundle update = new Bundle();
        update.putBoolean(ClockOptions.SHADE_SECONDS, false);
        ClockOptions.update(context, update);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, notifications.get());
        assertFalse(ClockOptions.read(context).shadeSeconds);
        context.getContentResolver().unregisterContentObserver(observer);
    }

    @Test public void realSystemUiPackageCanPersistFromItsDedicatedUid() {
        PackageInfo systemUi = new PackageInfo();
        systemUi.packageName = "com.android.systemui";
        systemUi.applicationInfo = new ApplicationInfo();
        systemUi.applicationInfo.packageName = systemUi.packageName;
        systemUi.applicationInfo.uid = 12_999;
        systemUi.applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        Shadows.shadowOf(context.getPackageManager()).installPackage(systemUi);
        ShadowBinder.setCallingUid(12_999);
        Bundle update = new Bundle();
        update.putBoolean(ClockOptions.SHADE_SECONDS, false);
        assertFalse(ClockOptions.fromBundle(provider.call("update", null, update)).shadeSeconds);
        // A user-installed app with that name must never gain the same authority.
        systemUi.applicationInfo.flags = 0;
        Shadows.shadowOf(context.getPackageManager()).installPackage(systemUi);
        try {
            provider.call("update", null, update);
            fail("A non-system package cannot impersonate SystemUI");
        } catch (SecurityException expected) { /* expected */ }
    }

    @Test public void settingsScreenSavesControlsAndRefreshesAfterExternalToggle() {
        ActivityController<SettingsActivity> activityController =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        SettingsActivity activity = activityController.get();
        Switch status = findSwitch(activity.getWindow().getDecorView(), "Seconds in the status bar");
        assertNotNull(status);
        assertTrue(status.isChecked());
        status.performClick();
        assertFalse(ClockOptions.read(context).statusSeconds);
        Bundle update = new Bundle();
        update.putBoolean(ClockOptions.STATUS_SECONDS, true);
        ShadowBinder.setCallingUid(Process.SYSTEM_UID);
        provider.call("update", null, update);
        ShadowBinder.setCallingUid(Process.myUid());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue("An external gesture refreshes controls while this screen remains visible",
                status.isChecked());
        activityController.pause().stop();
        update.putBoolean(ClockOptions.STATUS_SECONDS, false);
        ShadowBinder.setCallingUid(Process.SYSTEM_UID);
        provider.call("update", null, update);
        ShadowBinder.setCallingUid(Process.myUid());
        activityController.start().resume();
        assertFalse(status.isChecked());
        activityController.pause().stop().destroy();
    }

    private static Switch findSwitch(View root, String text) {
        if (root instanceof Switch && text.contentEquals(((Switch) root).getText())) {
            return (Switch) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                Switch found = findSwitch(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
