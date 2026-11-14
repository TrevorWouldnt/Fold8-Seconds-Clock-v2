package com.fold8.secondsclock;

import android.app.Activity;
import android.database.ContentObserver;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Small native settings screen, usable on both the cover and inner display. */
public final class SettingsActivity extends Activity {
    private Switch statusSeconds;
    private Switch shadeSeconds;
    private Switch doubleTap;
    private Switch haptic;
    private Switch showAmPm;
    private Spinner timeFormat;
    private TextView summary;
    private boolean loading;
    private boolean observing;
    private ClockOptions options = ClockOptions.defaults();
    private final ContentObserver optionsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { reloadOptions(); }
    };

    @Override protected void onCreate(Bundle state) {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        setTitle("Fold8 Seconds Clock");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        TextView title = text(content, "Your clocks, with seconds", 24);
        title.setAccessibilityHeading(true);
        text(content, "Use your existing Samsung clocks in the status bar and Quick Settings.", 16);
        summary = text(content, "", 15);

        statusSeconds = toggle(content, "Seconds in the status bar", ClockOptions.STATUS_SECONDS);
        shadeSeconds = toggle(content, "Seconds in Quick Settings", ClockOptions.SHADE_SECONDS);
        doubleTap = toggle(content, "Double-tap a clock to toggle its seconds", ClockOptions.DOUBLE_TAP);
        haptic = toggle(content, "Vibrate after a double-tap toggle", ClockOptions.HAPTIC);
        showAmPm = toggle(content, "Show AM/PM in 12-hour time", ClockOptions.SHOW_AM_PM);

        TextView formatLabel = text(content, "Time format", 17);
        timeFormat = new Spinner(this);
        timeFormat.setId(View.generateViewId());
        timeFormat.setContentDescription("Time format");
        formatLabel.setLabelFor(timeFormat.getId());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[] {"Follow phone setting", "12-hour", "24-hour"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeFormat.setAdapter(adapter);
        content.addView(timeFormat, new LinearLayout.LayoutParams(-1, dp(56)));
        timeFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selected = position == 1 ? 12 : position == 2 ? 24 : 0;
                if (loading || position != timeFormat.getSelectedItemPosition()
                        || selected == options.timeFormat) return;
                Bundle update = new Bundle();
                update.putInt(ClockOptions.TIME_FORMAT, selected);
                save(update);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        Button reset = new Button(this);
        reset.setText("Restore default settings");
        content.addView(reset, new LinearLayout.LayoutParams(-1, -2));
        reset.setOnClickListener(view -> save(ClockOptions.defaults().toBundle()));

        TextView activation = text(content, "Activate in Vector", 20);
        activation.setAccessibilityHeading(true);
        text(content, "In Vector → Modules, enable Fold8 Seconds Clock and select only System UI "
                + "(com.android.systemui). Reboot after first activation. Settings above apply "
                + "immediately once the module is active.", 16);
        text(content, "This experimental module still needs verification on your phone. If the "
                + "clock does not change, collect Vector log lines containing [Fold8Seconds]. "
                + "To undo the module, disable it in Vector and reboot.", 14);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!observing) {
            getContentResolver().registerContentObserver(ClockOptions.URI, false, optionsObserver);
            observing = true;
        }
        reloadOptions();
    }

    @Override protected void onPause() {
        if (observing) {
            getContentResolver().unregisterContentObserver(optionsObserver);
            observing = false;
        }
        super.onPause();
    }

    private void reloadOptions() {
        try {
            options = ClockOptions.read(this);
            showOptions();
        } catch (RuntimeException error) {
            summary.setText("Settings are unavailable. Close and reopen the app.");
        }
    }

    private Switch toggle(LinearLayout content, String label, String key) {
        Switch control = new Switch(this);
        control.setText(label);
        control.setTextSize(16);
        control.setMinHeight(dp(64));
        control.setPadding(0, dp(8), 0, dp(8));
        content.addView(control, new LinearLayout.LayoutParams(-1, -2));
        control.setOnCheckedChangeListener((button, checked) -> {
            if (loading) return;
            Bundle update = new Bundle();
            update.putBoolean(key, checked);
            save(update);
        });
        return control;
    }

    private void save(Bundle update) {
        try {
            options = ClockOptions.update(this, update);
            showOptions();
        } catch (RuntimeException error) {
            Toast.makeText(this, "Could not save settings. Try reopening the app.", Toast.LENGTH_LONG).show();
            showOptions();
        }
    }

    private void showOptions() {
        loading = true;
        statusSeconds.setChecked(options.statusSeconds);
        shadeSeconds.setChecked(options.shadeSeconds);
        doubleTap.setChecked(options.doubleTap);
        haptic.setChecked(options.haptic);
        haptic.setEnabled(options.doubleTap);
        showAmPm.setChecked(options.showAmPm);
        showAmPm.setEnabled(options.timeFormat != 24);
        timeFormat.setSelection(options.timeFormat == 12 ? 1 : options.timeFormat == 24 ? 2 : 0);
        summary.setText("Status bar: seconds " + (options.statusSeconds ? "on" : "off")
                + "\nQuick Settings: seconds " + (options.shadeSeconds ? "on" : "off"));
        loading = false;
    }

    private TextView text(LinearLayout content, String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setPadding(0, dp(8), 0, dp(12));
        content.addView(text, new LinearLayout.LayoutParams(-1, -2));
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
