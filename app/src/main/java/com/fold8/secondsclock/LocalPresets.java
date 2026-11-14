package com.fold8.secondsclock;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** App-private clock look snapshots. Never writes into the active SystemUI config. */
final class LocalPresets {
    private static final String NAME = "fold8_saved_looks_v14";
    private static String key(int slot) {
        if(slot!=1 && slot!=2)throw new IllegalArgumentException("Invalid look slot");
        return "look_"+slot;
    }
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME,Context.MODE_PRIVATE);
    }
    static boolean exists(Context context,int slot) {
        return prefs(context).contains(key(slot));
    }
    static boolean save(Context context,int slot,ClockConfig c) {
        try {
            JSONObject j=new JSONObject();
            j.put("enabled",c.enabled); j.put("seconds",c.seconds);
            j.put("scale",c.scale); j.put("weight",c.weight);
            j.put("format",c.format); j.put("color",c.color);
            j.put("bubbleEnabled",c.bubbleEnabled); j.put("bubbleStyle",c.bubbleStyle);
            j.put("bubbleOpacity",c.bubbleOpacity); j.put("bubblePadding",c.bubblePadding);
            j.put("bubbleRadius",c.bubbleRadius); j.put("bubbleColor",c.bubbleColor);
            j.put("doubleTapBubble",c.doubleTapBubble); j.put("bubbleAuto",c.bubbleAuto);
            j.put("bubbleBorder",c.bubbleBorder); j.put("bubbleInset",c.bubbleInset);
            LabData.putClockExtras(j, c);
            return prefs(context).edit().putString(key(slot),j.toString()).commit();
        } catch(Exception e) {
            return false;
        }
    }
    static ClockConfig load(Context context,int slot) {
        String raw=prefs(context).getString(key(slot),null);
        if(raw==null)return null;
        try {
            JSONObject j=new JSONObject(raw);
            ClockConfig d=ClockConfig.defaults();
            ClockConfig result = new ClockConfig(
                j.optBoolean("enabled",d.enabled), j.optBoolean("seconds",d.seconds),
                j.optInt("scale",d.scale), j.optInt("weight",d.weight),
                j.optInt("format",d.format), j.optInt("color",d.color),
                j.optBoolean("bubbleEnabled",d.bubbleEnabled),
                j.optInt("bubbleStyle",d.bubbleStyle),
                j.optInt("bubbleOpacity",d.bubbleOpacity),
                j.optInt("bubblePadding",d.bubblePadding),
                j.optInt("bubbleRadius",d.bubbleRadius),
                j.optInt("bubbleColor",d.bubbleColor),
                j.optBoolean("doubleTapBubble",d.doubleTapBubble),
                j.optBoolean("bubbleAuto",d.bubbleAuto),
                j.optInt("bubbleBorder",d.bubbleBorder),
                j.optInt("bubbleInset",d.bubbleInset));
            return LabData.applyClockExtras(result, j);
        } catch(Exception malformed) {
            return null;
        }
    }
}
