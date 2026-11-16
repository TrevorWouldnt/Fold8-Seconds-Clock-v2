package com.fold8.secondsclock;

import static org.junit.Assert.*;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class ControlLabUiTest {
    @Test public void originalClockStudioPreviewBubbleAndPresetsRemainAvailable() {
        ActivityController<ControlLabActivity> controller =
                Robolectric.buildActivity(ControlLabActivity.class).setup();
        try {
            ControlLabActivity activity = controller.get();
            View content = activity.getWindow().getDecorView();
            assertNotNull(find(content, Switch.class, "Enable clock customization"));
            assertNotNull(find(content, Switch.class, "Show bubble behind clock"));
            assertNotNull(find(content, Switch.class, "Double-tap clock to toggle bubble"));
            TextView previewLabel = ReflectionHelpers.getField(activity, "previewLabel");
            assertNotNull(previewLabel);
            assertTrue(previewLabel.getText().toString().contains("PREVIEW"));
            assertNotNull(find(content, Button.class, "Save A"));
            assertNotNull(find(content, Button.class, "Load B"));
            Button hub = find(content, Button.class,
                    "Open Control Center  •  Diagnostics / Tools / Looks");
            assertNotNull(hub);
            hub.performClick();
            Intent launched = Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;
            assertEquals(LabHubActivity.class.getName(), launched.getComponent().getClassName());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void controlCenterKeepsAllTabsAndDoesNotExecuteRootAtStartup() {
        ActivityController<LabHubActivity> controller =
                Robolectric.buildActivity(LabHubActivity.class).setup();
        try {
            LabHubActivity activity = controller.get();
            View content = activity.getWindow().getDecorView();
            assertNotNull(find(content, Button.class, "Clock"));
            assertNotNull(find(content, Button.class, "Themes"));
            assertNotNull(find(content, Button.class, "Device"));
            Button root = find(content, Button.class, "Root");
            assertNotNull(root);
            assertNotNull(find(content, Button.class, "Inspect applied clock"));
            root.performClick();
            assertEquals("Opening the toolbox must not run any root command", 0L,
                    (long) ReflectionHelpers.<Long>getField(activity, "rootTaskId"));
        } finally { controller.pause().stop().destroy(); }
    }

    private static <T extends TextView> T find(View root, Class<T> type, String text) {
        if (type.isInstance(root) && text.contentEquals(((TextView) root).getText())) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = find(group.getChildAt(i), type, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
