package com.fold8.secondsclock;

import android.content.Context;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** A per-view formatter; used only from the main thread. */
final class ClockText {
    private Locale previousLocale;
    private TimeZone previousZone;
    private boolean previous24Hour;
    private boolean previousSeconds, previousAmPm;
    private SimpleDateFormat formatter;
    private final Date date = new Date();

    String format(Context context, long now) {
        return format(context, now, ClockConfig.safeFallback(), true);
    }
    String format(Context context, long now, ClockConfig config, boolean seconds) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        TimeZone zone = TimeZone.getDefault();
        boolean use24Hour = config.format == 2 || (config.format == 0 && DateFormat.is24HourFormat(context));
        if (formatter == null || !locale.equals(previousLocale)
                || !zone.equals(previousZone) || use24Hour != previous24Hour
                || seconds != previousSeconds || config.showAmPm != previousAmPm) {
            // Samsung's stock status-bar clock omits AM/PM; use the locale's time
            // separators/order while keeping that compact style in 12-hour mode.
            String pattern = DateFormat.getBestDateTimePattern(locale,
                    use24Hour ? (seconds ? "Hms" : "Hm") : (seconds ? "hms" : "hm"));
            if (!use24Hour && !config.showAmPm) pattern = withoutDayPeriod(pattern);
            formatter = new SimpleDateFormat(pattern, locale);
            formatter.setTimeZone(zone);
            previousLocale = locale;
            previousZone = zone;
            previous24Hour = use24Hour;
            previousSeconds = seconds; previousAmPm = config.showAmPm;
        }
        date.setTime(now);
        return formatter.format(date);
    }

    static long delayUntilNextSecond(long now) {
        return 1000L - Math.floorMod(now, 1000L);
    }

    private static String withoutDayPeriod(String pattern) {
        StringBuilder result = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '\'') quoted = !quoted;
            if (quoted || (ch != 'a' && ch != 'b' && ch != 'B')) result.append(ch);
        }
        // ICU uses both regular and narrow/nonbreaking spaces around day periods.
        return result.toString().replaceAll("^[\\s\\u00a0\\u202f]+|[\\s\\u00a0\\u202f]+$", "");
    }
}
