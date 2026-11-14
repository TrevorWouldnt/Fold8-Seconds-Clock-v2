package com.fold8.secondsclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import org.json.JSONObject;

/** App-only styling; never exposed to Samsung SystemUI. */
final class LabUiPrefs {
    static final String FILE="fold8_lab_ui_v15";
    int accent=0; // 0: current theme accent
    boolean highContrast=false;
    boolean oledBlack=false;
    boolean compact=false;
    boolean stickyPreview=true;
    boolean haptics=false;
    int textScale=100;
    int cardOpacity=100;
    int backgroundStrength=100;
    int buttonRoundness=15;
    static LabUiPrefs load(Context c){
        SharedPreferences p=c.getSharedPreferences(FILE,Context.MODE_PRIVATE);
        LabUiPrefs v=new LabUiPrefs();
        v.accent=p.getInt("accent",0);
        v.highContrast=p.getBoolean("highContrast",false);
        v.oledBlack=p.getBoolean("oledBlack",false);
        v.compact=p.getBoolean("compact",false);
        v.stickyPreview=p.getBoolean("stickyPreview",true);
        v.haptics=p.getBoolean("haptics",false);
        v.textScale=clamp(p.getInt("textScale",100),85,125);
        v.cardOpacity=clamp(p.getInt("cardOpacity",100),40,100);
        v.backgroundStrength=clamp(p.getInt("backgroundStrength",100),0,100);
        v.buttonRoundness=clamp(p.getInt("buttonRoundness",15),2,32);
        return v;
    }
    void save(Context c){
        c.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit()
            .putInt("accent",accent).putBoolean("highContrast",highContrast)
            .putBoolean("oledBlack",oledBlack).putBoolean("compact",compact)
            .putBoolean("stickyPreview",stickyPreview).putBoolean("haptics",haptics)
            .putInt("textScale",clamp(textScale,85,125))
            .putInt("cardOpacity",clamp(cardOpacity,40,100))
            .putInt("backgroundStrength",clamp(backgroundStrength,0,100))
            .putInt("buttonRoundness",clamp(buttonRoundness,2,32)).apply();
    }
    static int clamp(int v,int lo,int hi){return Math.min(hi,Math.max(lo,v));}
    String json(){
        try {
            JSONObject j=new JSONObject();j.put("schema",1);j.put("kind","fold8-ui");
            j.put("accent",accent);j.put("highContrast",highContrast);j.put("oledBlack",oledBlack);
            j.put("compact",compact);j.put("stickyPreview",stickyPreview);j.put("haptics",haptics);
            j.put("textScale",textScale);j.put("cardOpacity",cardOpacity);
            j.put("backgroundStrength",backgroundStrength);j.put("buttonRoundness",buttonRoundness);
            return j.toString(2);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    static LabUiPrefs parse(String raw) throws Exception {
        if(raw==null||raw.length()>12000)throw new IllegalArgumentException("Invalid backup length");
        JSONObject j=new JSONObject(raw);
        if(j.getInt("schema")!=1||!"fold8-ui".equals(j.getString("kind")))throw new IllegalArgumentException("Not an app appearance backup");
        LabUiPrefs a=new LabUiPrefs();
        a.accent=j.getInt("accent");if(a.accent!=0) a.accent=0xFF000000 | (a.accent&0x00FFFFFF);
        a.highContrast=j.getBoolean("highContrast");a.oledBlack=j.getBoolean("oledBlack");
        a.compact=j.getBoolean("compact");a.stickyPreview=j.getBoolean("stickyPreview");
        a.haptics=j.getBoolean("haptics");
        a.textScale=inRange(j.getInt("textScale"),85,125);
        a.cardOpacity=inRange(j.getInt("cardOpacity"),40,100);
        a.backgroundStrength=inRange(j.getInt("backgroundStrength"),0,100);
        a.buttonRoundness=inRange(j.getInt("buttonRoundness"),2,32);
        return a;
    }
    private static int inRange(int i,int lo,int hi){if(i<lo||i>hi)throw new IllegalArgumentException("Out of range: "+i);return i;}
}
