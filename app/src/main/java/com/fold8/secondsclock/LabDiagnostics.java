package com.fold8.secondsclock;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Read-only diagnostics using public Android APIs. Data not exposed by a device is marked unavailable. */
final class LabDiagnostics {
    static final class Item {
        final String title,value,description;
        Item(String title, String value, String description){this.title=title;this.value=value;this.description=description;}
    }
    private static void add(List<Item> out,String name,Object value,String why){out.add(new Item(name, String.valueOf(value),why));}
    private static String unknown(){return "Not reported by this firmware";}
    private static String bytes(long n){if(n<0)return unknown(); return String.format(Locale.US,"%.2f GB",n/1073741824d);}
    static List<Item> collect(Activity a) {
        List<Item> out=new ArrayList<>();
        Context app=a.getApplicationContext();
        Intent battery=app.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
        int max=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
        add(out,"Battery percentage",level>=0&&max>0?String.format(Locale.US,"%.0f%%",100d*level/max):unknown(),"Charge currently reported by Android");
        int status=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
        String[] statusNames={"Unknown","Unknown","Charging","Discharging","Not charging","Full"};
        add(out,"Charging state",status>0&&status<statusNames.length?statusNames[status]:unknown(),"Whether the battery is currently charging");
        int plugged=battery==null?0:battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
        add(out,"Power source",(plugged&BatteryManager.BATTERY_PLUGGED_AC)!=0?"AC":
                (plugged&BatteryManager.BATTERY_PLUGGED_USB)!=0?"USB":
                (plugged&BatteryManager.BATTERY_PLUGGED_WIRELESS)!=0?"Wireless":
                (plugged&BatteryManager.BATTERY_PLUGGED_DOCK)!=0?"Dock":"On battery", "Charging transport reported by Android");
        int health=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_HEALTH,-1);
        String healthName=health==BatteryManager.BATTERY_HEALTH_GOOD?"Good":
                health==BatteryManager.BATTERY_HEALTH_OVERHEAT?"Overheating":
                health==BatteryManager.BATTERY_HEALTH_DEAD?"Dead":
                health==BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE?"Overvoltage":
                health==BatteryManager.BATTERY_HEALTH_COLD?"Cold":"Unknown";
        add(out,"Battery health flag",healthName,"Android's broad health status, not capacity degradation");
        int temp=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1);
        add(out,"Battery temperature",temp>=0?String.format(Locale.US,"%.1f °C",temp/10d):unknown(),"Battery sensor, not the CPU temperature");
        int voltage=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1);
        add(out,"Battery voltage",voltage>0?String.format(Locale.US,"%.3f V",voltage/1000d):unknown(),"Current battery terminal voltage");
        BatteryManager bm=(BatteryManager)app.getSystemService(Context.BATTERY_SERVICE);
        long ma=bm==null?Long.MIN_VALUE:bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        boolean hasCurrent=ma!=Long.MIN_VALUE && Math.abs(ma)<30000000L;
        add(out,"Battery current",hasCurrent?String.format(Locale.US,"%.0f mA",ma/1000d):unknown(),"Signed battery-side current, when supported");
        add(out,"Estimated battery power",hasCurrent&&voltage>0?
                String.format(Locale.US,"%.2f W",ma/1000000d*voltage/1000d):unknown(),
                "Voltage × battery current. Not charger input wattage");
        long charge=bm==null?Long.MIN_VALUE:bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        add(out,"Battery charge counter",charge>0&&charge<Long.MAX_VALUE/2?String.format(Locale.US,"%.0f mAh",charge/1000d):unknown(),"Remaining charge counter when supported");
        PowerManager pm=(PowerManager)app.getSystemService(Context.POWER_SERVICE);
        add(out,"Battery saver",pm!=null&&pm.isPowerSaveMode()?"Enabled":"Off","Android power-save state");
        if(Build.VERSION.SDK_INT>=29&&pm!=null){
            int thermal=pm.getCurrentThermalStatus();
            String[] levels={"None","Light","Moderate","Severe","Critical","Emergency","Shutdown"};
            add(out,"Thermal pressure",thermal>=0&&thermal<levels.length?levels[thermal]:unknown(),"OS thermal status, not a temperature in degrees");
        } else add(out,"Thermal pressure",unknown(),"Requires Android 10+");
        add(out,"Time since boot",String.format(Locale.US,"%.1f hours",SystemClock.elapsedRealtime()/3600000d),"Includes time spent sleeping");
        ActivityManager am=(ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();
        if(am!=null)am.getMemoryInfo(mem);
        add(out,"Total RAM",am!=null?bytes(mem.totalMem):unknown(),"Physical memory reported by ActivityManager");
        add(out,"Available RAM",am!=null?bytes(mem.availMem):unknown(),"Memory available to other apps");
        add(out,"Memory pressure",am!=null?(mem.lowMemory?"Low-memory condition":"Normal"):unknown(),"Low-memory flag from Android");
        StatFs disk=new StatFs(Environment.getDataDirectory().getAbsolutePath());
        add(out,"Internal storage total",bytes(disk.getTotalBytes()),"Total /data partition size");
        add(out,"Internal storage free",bytes(disk.getAvailableBytes()),"Free /data space usable by applications");
        Display display=a.getWindowManager().getDefaultDisplay();
        DisplayMetrics dm=new DisplayMetrics();display.getRealMetrics(dm);
        add(out,"Physical display pixels",dm.widthPixels+" × "+dm.heightPixels,"Current logical display including system bars");
        add(out,"Display density",dm.densityDpi+" dpi","Android display-density configuration");
        add(out,"Display refresh rate",String.format(Locale.US,"%.1f Hz",display.getRefreshRate()),"Current display mode refresh rate");
        int orient=a.getResources().getConfiguration().orientation;
        add(out,"Orientation",orient==Configuration.ORIENTATION_LANDSCAPE?"Landscape":"Portrait","Activity's current orientation");
        add(out,"Smallest width",a.getResources().getConfiguration().smallestScreenWidthDp+" dp","Useful for foldable layout decisions; not a hinge sensor");
        add(out,"Font scale",String.format(Locale.US,"%.2f×",a.getResources().getConfiguration().fontScale),"Android accessibility font-scaling factor");
        int night=a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        add(out,"System dark theme",night==Configuration.UI_MODE_NIGHT_YES?"On":"Off","Android's current dark-mode preference");
        add(out,"Android release",Build.VERSION.RELEASE,"Operating-system release");
        add(out,"Security patch",Build.VERSION.SECURITY_PATCH,"Android security patch level");
        add(out,"Firmware build",Build.DISPLAY,"Samsung build identifier");
        add(out,"Kernel release",System.getProperty("os.version","Unknown"),"Linux kernel version exposed to apps");
        add(out,"Device model",Build.MANUFACTURER+" "+Build.MODEL,"Manufacturer and model");
        add(out,"Processor ABIs",android.text.TextUtils.join(", ",Build.SUPPORTED_ABIS),"Supported Android native architectures");
        add(out,"CPU cores",Runtime.getRuntime().availableProcessors(),"Cores available to this app");
        add(out,"Locale",Locale.getDefault().toLanguageTag(),"Current app language locale");
        add(out,"Time zone",TimeZone.getDefault().getID(),"Current time zone identifier");
        int timeout=Settings.System.getInt(app.getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,-1);
        add(out,"Screen timeout",timeout>=0?timeout/1000+" seconds":unknown(),"Auto-sleep timeout");
        ConnectivityManager cm=(ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps=cm==null?null:cm.getNetworkCapabilities(cm.getActiveNetwork());
        add(out,"Wi-Fi transport",caps==null?unknown():(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Active":"Inactive"),"Active network's transport type");
        add(out,"Mobile data transport",caps==null?unknown():(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"Active":"Inactive"),"Active network's transport type");
        add(out,"VPN transport",caps==null?unknown():(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?"Active":"Inactive"),"VPN status of the active network");
        add(out,"Metered connection",cm==null?unknown():(cm.isActiveNetworkMetered()?"Metered":"Unmetered"),"Android's data-usage classification");
        return out;
    }
    static String report(List<Item> metrics){
        StringBuilder b=new StringBuilder("Fold8 Control Lab - diagnostic snapshot\n");
        for(Item i:metrics)b.append(i.title).append(": ").append(i.value).append("\n");
        return b.toString();
    }
}
