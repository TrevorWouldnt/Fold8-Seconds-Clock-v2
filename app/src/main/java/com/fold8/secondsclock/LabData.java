package com.fold8.secondsclock;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Local-only backup and interchange. Nothing in this class calls privileged APIs. */
final class LabData {
    private static final String STORE = "fold8_lab_v15_workspace";
    private static final int VERSION = 1;
    private LabData() { }

    static String clockJson(ClockConfig c) {
        try {
            JSONObject j = new JSONObject();
            j.put("schema",VERSION); j.put("kind","fold8-clock");
            j.put("enabled",c.enabled); j.put("seconds",c.seconds);
            j.put("scale",c.scale); j.put("weight",c.weight);
            j.put("format",c.format); j.put("color",c.color);
            j.put("bubbleEnabled",c.bubbleEnabled); j.put("bubbleAuto",c.bubbleAuto);
            j.put("bubbleStyle",c.bubbleStyle); j.put("bubbleOpacity",c.bubbleOpacity);
            j.put("bubblePadding",c.bubblePadding); j.put("bubbleRadius",c.bubbleRadius);
            j.put("bubbleColor",c.bubbleColor); j.put("bubbleBorder",c.bubbleBorder);
            j.put("bubbleInset",c.bubbleInset); j.put("doubleTapBubble",c.doubleTapBubble);
            putClockExtras(j, c);
            return j.toString(2);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static ClockConfig parseClock(String text) throws Exception {
        if (text == null || text.length()>12000) throw new IllegalArgumentException("Invalid backup length");
        JSONObject j = new JSONObject(text);
        if (j.getInt("schema")!=VERSION || !"fold8-clock".equals(j.getString("kind")))
            throw new IllegalArgumentException("Not a supported Fold8 clock backup");
        String[] required={"enabled","seconds","scale","weight","format","color","bubbleEnabled",
            "bubbleAuto","bubbleStyle","bubbleOpacity","bubblePadding","bubbleRadius","bubbleColor",
            "bubbleBorder","bubbleInset","doubleTapBubble"};
        for (String field:required) if (!j.has(field) || j.isNull(field))
            throw new IllegalArgumentException("Missing field: " + field);
        ClockConfig result = new ClockConfig(j.getBoolean("enabled"),j.getBoolean("seconds"),
                inRange(j.getInt("scale"),85,135,"scale"),
                inRange(j.getInt("weight"),0,2,"weight"),
                inRange(j.getInt("format"),0,2,"format"),j.getInt("color"),
                j.getBoolean("bubbleEnabled"),inRange(j.getInt("bubbleStyle"),1,4,"bubble style"),
                inRange(j.getInt("bubbleOpacity"),15,95,"opacity"),
                inRange(j.getInt("bubblePadding"),2,12,"padding"),
                inRange(j.getInt("bubbleRadius"),6,28,"radius"),j.getInt("bubbleColor"),
                j.getBoolean("doubleTapBubble"),j.getBoolean("bubbleAuto"),
                inRange(j.getInt("bubbleBorder"),0,3,"border"),
                inRange(j.getInt("bubbleInset"),1,5,"inset"));
        return applyClockExtras(result, j);
    }

    /** New fields remain optional, so existing v1.5 backups/presets still load. */
    static ClockConfig applyClockExtras(ClockConfig c, JSONObject j) throws Exception {
        c.shadeSeconds = optionalBoolean(j, "shadeSeconds", c.shadeSeconds);
        c.doubleTapShade = optionalBoolean(j, "doubleTapShade", c.doubleTapShade);
        c.gestureHaptic = optionalBoolean(j, "gestureHaptic", c.gestureHaptic);
        c.showAmPm = optionalBoolean(j, "showAmPm", c.showAmPm);
        return c;
    }

    static void putClockExtras(JSONObject j, ClockConfig c) throws Exception {
        j.put("shadeSeconds", c.shadeSeconds);
        j.put("doubleTapShade", c.doubleTapShade);
        j.put("gestureHaptic", c.gestureHaptic);
        j.put("showAmPm", c.showAmPm);
    }

    private static boolean optionalBoolean(JSONObject j, String key, boolean fallback) throws Exception {
        if (!j.has(key)) return fallback;
        Object value = j.get(key);
        if (!(value instanceof Boolean)) throw new IllegalArgumentException(key + " must be true or false");
        return (Boolean) value;
    }
    private static int inRange(int value,int low,int high,String what){
        if(value<low || value>high)throw new IllegalArgumentException(what+" out of range");
        return value;
    }
    static boolean store(Context ctx,String name,String payload) {
        return prefs(ctx).edit().putString(name,payload).putLong(name+"_at",System.currentTimeMillis()).commit();
    }
    static String get(Context ctx,String name) {return prefs(ctx).getString(name,null);}
    static long time(Context ctx,String name) {return prefs(ctx).getLong(name+"_at",0);}
    static boolean erase(Context ctx,String name) {return prefs(ctx).edit().remove(name).remove(name+"_at").commit();}
    static SharedPreferences prefs(Context ctx) {return ctx.getSharedPreferences(STORE,Context.MODE_PRIVATE);}
    static String appearanceJson(AppearancePrefs a) {
        try {
            JSONObject j=new JSONObject();j.put("schema",1);j.put("kind","fold8-appearance");
            j.put("theme",a.theme);j.put("blur",a.blur);j.put("depth",a.depth);j.put("radius",a.radius);
            j.put("cardStyle",a.cardStyle);j.put("reduceMotion",a.reduceMotion);
            return j.toString(2);
        } catch(Exception e){throw new IllegalStateException(e);}
    }
    static AppearancePrefs parseAppearance(String raw) throws Exception {
        if(raw==null || raw.length()>10000)throw new IllegalArgumentException("Invalid theme backup length");
        JSONObject j=new JSONObject(raw);
        if(j.getInt("schema")!=1 || !"fold8-appearance".equals(j.getString("kind")))
            throw new IllegalArgumentException("Not a Fold8 appearance backup");
        AppearancePrefs a=new AppearancePrefs();
        a.theme=inRange(j.getInt("theme"),0,2,"theme");
        a.blur=inRange(j.getInt("blur"),0,40,"blur");
        a.depth=inRange(j.getInt("depth"),0,18,"elevation");
        a.radius=inRange(j.getInt("radius"),12,32,"corner radius");
        a.cardStyle=inRange(j.getInt("cardStyle"),0,2,"card style");
        a.reduceMotion=j.getBoolean("reduceMotion");return a;
    }
}
