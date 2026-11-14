package com.fold8.secondsclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Fold8 Control Lab's companion Control Center. All actions are real; risky ones require confirmation. */
public final class LabHubActivity extends Activity {
    private static final int FG=0xFFF7F9FF, MUTED=0xFFBECDE4, BG=0xFF0A101E;
    private static final int CARD=0xFF1A2840,ACCENT=0xFFA6C9FF;
    private LinearLayout page, content;
    private TextView result;
    private List<LabDiagnostics.Item> latest=new ArrayList<>();
    private LabUiPrefs ui;
    private int tab=0;
    private long rootTaskId=0;
    private final List<Button> navigation=new ArrayList<>();

    @Override protected void onCreate(Bundle b){super.onCreate(b);ui=LabUiPrefs.load(this);buildShell();showTab(0);}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(String str,int sp,int color,boolean bold){
        TextView v=new TextView(this);v.setText(str);
        int fg = ui.highContrast && color==MUTED ? Color.WHITE : color;
        v.setTextColor(fg);v.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp*ui.textScale/100f);
        if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);v.setPadding(dp(4),dp(5),dp(4),dp(5));return v;
    }
    private GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();
        int result=(color&0x00FFFFFF)|((Math.min(Color.alpha(color),Math.round(255f*ui.cardOpacity/100f)))<<24);
        d.setColor(result);d.setCornerRadius(dp(ui.buttonRoundness));
        d.setStroke(dp(1),ui.highContrast?0xFFD8E8FF:0xFF384B64);return d;}
    private LinearLayout section(String label,String hint){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        int spacing=ui.compact?9:13;c.setPadding(dp(spacing),dp(spacing),dp(spacing),dp(spacing));
        c.setBackground(rounded(CARD,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(13);content.addView(c,p);
        c.addView(text(label,17,FG,true));if(hint!=null)c.addView(text(hint,12,MUTED,false));return c;
    }
    private void button(LinearLayout parent,String title,String description,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(FG);
        b.setBackground(rounded(0xFF2D3B55,12));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(47));p.topMargin=dp(6);
        parent.addView(b,p);b.setOnClickListener(v->{if(ui.haptics)v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);action.run();});
        if(description!=null && !description.isEmpty())parent.addView(text(description,11,MUTED,false));
    }
    private void line(LinearLayout parent,String k,String v){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=text(k,13,MUTED,false);row.addView(label,new LinearLayout.LayoutParams(0,-2,1f));
        TextView value=text(v,13,FG,true);value.setGravity(Gravity.END);value.setMaxWidth(dp(240));
        row.addView(value);parent.addView(row);
    }
    private void buildShell(){
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        navigation.clear();
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(ui.oledBlack?Color.BLACK:BG);
        page.setPadding(dp(12),0,dp(12),0);page.setFitsSystemWindows(true);setContentView(page);
        page.addView(text("FOLD8 CONTROL LAB  /  v1.5",12,ui.accent!=0?ui.accent:ACCENT,true));
        page.addView(text("Control Center",28,FG,true));
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
        String[] tabs={"Clock","Themes","Device","Root"};
        for(int i=0;i<tabs.length;i++){
            final int selected=i;Button tabButton=new Button(this);tabButton.setText(tabs[i]);tabButton.setAllCaps(false);tabButton.setTextSize(11);
            tabButton.setPadding(0,0,0,0);tabButton.setTextColor(FG);tabButton.setBackground(rounded(0xFF293850,10));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1f);p.setMargins(dp(2),dp(3),dp(2),dp(6));nav.addView(tabButton,p);
            navigation.add(tabButton);
            tabButton.setOnClickListener(v->showTab(selected));
        }
        page.addView(nav);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(3),dp(8),dp(3),dp(16));scroll.addView(content);
        result=text("Select a tool. Diagnostics stay on your phone unless you copy or share them.",12,FG,false);
        result.setTextIsSelectable(true);result.setBackground(rounded(0xFF1B2741,12));result.setPadding(dp(12),dp(11),dp(12),dp(11));
        page.addView(result,new LinearLayout.LayoutParams(-1,dp(100)));
    }
    private void say(String value){result.setText(value);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void copy(String value){ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("Control Lab",value));toast("Copied to clipboard");}}
    private String clipboard(){ClipboardManager mgr=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(mgr==null||!mgr.hasPrimaryClip())return null;
        ClipData data=mgr.getPrimaryClip();if(data==null||data.getItemCount()==0)return null;
        CharSequence text=data.getItemAt(0).coerceToText(this);return text==null?null:text.toString();}
    private void share(String title,String body){Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT,title);send.putExtra(Intent.EXTRA_TEXT,body);
        startActivity(Intent.createChooser(send,"Share from Control Lab"));}
    private void confirm(String title,String message,Runnable action){new AlertDialog.Builder(this).setTitle(title)
        .setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->action.run()).show();}
    private void showTab(int selected){tab=selected;content.removeAllViews();
        for(int i=0;i<navigation.size();i++){
            boolean active=i==selected;Button b=navigation.get(i);
            b.setBackground(rounded(active?(ui.accent==0?ACCENT:ui.accent):0xFF293850,10));
            b.setTextColor(active?0xFF0A1527:FG);
        }
        if(selected==0)clockTools();else if(selected==1)themeTools();else if(selected==2)deviceTools();else rootTools();
    }
    private void clockTools(){
        LinearLayout status=section("Clock workflow","Controls below operate on app settings; they do not restart System UI automatically.");
        button(status,"Open Clock Studio","Return to the live pinned preview and status-bar controls",()->finish());
        button(status,"Inspect applied clock","Read the configuration currently saved for System UI",()->say(LabData.clockJson(ClockConfig.loadLocal(this))));
        button(status,"Compare with safe defaults","Quickly find every clock value different from factory settings",()->{
            ClockConfig current=ClockConfig.loadLocal(this);String a=LabData.clockJson(current),b=LabData.clockJson(ClockConfig.defaults());
            try{org.json.JSONObject x=new org.json.JSONObject(a),y=new org.json.JSONObject(b);StringBuilder diff=new StringBuilder();
                java.util.Iterator<String> keys=x.keys();while(keys.hasNext()){String k=keys.next();if(!x.get(k).equals(y.opt(k)))
                    diff.append(k).append(": ").append(x.get(k)).append(" (default ").append(y.opt(k)).append(")\n");}
                say(diff.length()==0?"All settings match defaults.":diff.toString());}catch(Exception e){say(e.toString());}});
        button(status,"Check settings provider","Read through the same IPC route used by the clock hook",()->{
            try{ClockConfig c=ClockConfig.loadRemote(this);say("Provider reachable.\n"+LabData.clockJson(c));}
            catch(Exception e){say("Provider unavailable: "+e.getMessage());}});
        button(status,"Measure clock/bubble contrast","Compute actual WCAG contrast for chosen colors",()->{
            ClockConfig c=ClockConfig.loadLocal(this);int t=c.color==0?Color.WHITE:c.color;
            int bg=c.bubbleColor==0?Color.rgb(15,23,42):c.bubbleColor;
            double contrast=(Math.max(lum(t),lum(bg))+.05)/(Math.min(lum(t),lum(bg))+.05);
            say(String.format(Locale.US,"Text/bubble contrast: %.2f:1\nAA normal text target: 4.5:1\nThis compares opaque colors, not translucent wallpaper blending.",contrast));});
        button(status,"Recommend readable text color","Pick black or white by contrast against the selected pill",()->{
            ClockConfig c=ClockConfig.loadLocal(this);int bg=c.bubbleColor==0?0xFF172033:c.bubbleColor;
            say("For the selected bubble color, the clearer text is " +(lum(bg)>.179?"BLACK #000000":"WHITE #FFFFFF")+
                ". Copy to Clock Studio's color field if desired.");});
        button(status,"Check clock width estimate","Approximate size needed for digits before notification icons",()->{
            ClockConfig c=ClockConfig.loadLocal(this);float dp=getResources().getDisplayMetrics().density;
            android.graphics.Paint p=new android.graphics.Paint();p.setTextSize(16f*dp*c.scale/100f);
            String sample=c.seconds?"12:59:59":"12:59";
            float width=p.measureText(sample)/dp+2*c.bubblePadding;
            say(String.format(Locale.US,"Estimated width: %.0f dp for %s\nRough estimate only; Samsung's actual typeface may differ.",width,sample));});

        LinearLayout portability=section("Backups and portability","Every import is validated and requires confirmation. No backup silently overwrites System UI settings.");
        button(portability,"Copy full clock JSON", "Includes all clock, bubble, and gesture settings",()->copy(LabData.clockJson(ClockConfig.loadLocal(this))));
        button(portability,"Share full clock JSON", "Send through Android's standard share sheet",()->share("Fold8 clock settings",LabData.clockJson(ClockConfig.loadLocal(this))));
        button(portability,"Save private clock backup", "Stores a timestamped backup inside app-private storage",()->{
            String body=LabData.clockJson(ClockConfig.loadLocal(this));boolean ok=LabData.store(this,"backup_clock",body);
            say(ok?"Private clock backup saved.":"Backup write failed.");});
        button(portability,"Inspect private clock backup", "View backup contents without applying them",()->{
            String raw=LabData.get(this,"backup_clock");say(raw==null?"No backup saved.":raw);});
        button(portability,"Compare backup to applied clock", "Shows whether a saved backup differs",()->{
            String old=LabData.get(this,"backup_clock");say(old==null?"No backup saved.":
                old.equals(LabData.clockJson(ClockConfig.loadLocal(this)))?"Identical to active clock settings.":"Backup differs from currently saved settings. Inspect before restoring.");});
        button(portability,"Restore clock backup", "Explicit confirmation required before applying",()->{
            try{String raw=LabData.get(this,"backup_clock");if(raw==null)throw new Exception("No backup saved");
                ClockConfig imported=LabData.parseClock(raw);confirm("Restore clock backup?","This replaces saved clock preferences. System UI may need a manual restart.",()->{
                    say(ClockConfig.save(this,imported)?"Backup restored. Restart System UI to apply if needed.":"Restore failed");});
            }catch(Exception e){say(e.getMessage());}});
        button(portability,"Import clock JSON from clipboard", "Validates type and ranges; asks before writing",()->{
            try{ClockConfig imported=LabData.parseClock(clipboard());say("Validated clipboard backup.\n"+LabData.clockJson(imported));
                confirm("Import clipboard configuration?","The active clock configuration will be replaced. This won't restart System UI.",()->{
                    say(ClockConfig.save(this,imported)?"Imported. Return to Clock Studio and restart System UI if needed.":"Import failed");});}
            catch(Exception e){say("Import rejected: "+e.getMessage());}});
        button(portability,"Copy backup creation time", "Timestamp of last private backup",()->{
            long t=LabData.time(this,"backup_clock");say(t==0?"No backup timestamp.":new java.util.Date(t).toString());});
        button(portability,"Delete private backup", "Only deletes the app-private copy, never the active clock",()->confirm("Delete backup?","This cannot be undone.",()->{
            say(LabData.erase(this,"backup_clock")?"Backup deleted":"Could not delete backup");}));
        LinearLayout slots=section("Expanded Looks: C through H","Six new locally stored look slots. Each can be saved, inspected, and applied individually.");
        for(char c='C';c<='H';c++){
            final String name="look_"+c;
            button(slots,"Save Look "+c,"Snapshots the active clock settings into this named slot",()->{
                String old=LabData.get(this,name);Runnable save=()->say(LabData.store(this,name,LabData.clockJson(ClockConfig.loadLocal(this)))?name+" saved":"Save failed");
                if(old!=null)confirm("Overwrite "+name+"?","Replaces the saved slot without changing the running clock.",save);else save.run();});
            button(slots,"Load Look "+c,"Confirmation required before changing active configuration",()->{
                try{String raw=LabData.get(this,name);ClockConfig saved=LabData.parseClock(raw);
                    confirm("Load "+name+"?","This replaces saved clock settings. Restart System UI manually to apply.",()->say(
                        ClockConfig.save(this,saved)?name+" loaded":"Load failed"));}
                catch(Exception e){say(name+": "+e.getMessage());}});
        }
    }
    private static double channel(int n){double v=n/255d;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
    private static double lum(int color){return .2126*channel(Color.red(color))+.7152*channel(Color.green(color))+.0722*channel(Color.blue(color));}

    private void themeTools(){
        ui=LabUiPrefs.load(this);
        LinearLayout visual=section("Appearance Studio+","App-only controls: none are read by System UI. Changes take effect when you return to Clock Studio.");
        button(visual,"Choose custom accent hex","Set button, slider and heading accents across Clock Studio",()->input("Accent color (#RRGGBB)",ui.accent==0?"#A6C9FF":String.format("#%06X",ui.accent&0xFFFFFF),value->{
            try{int v=Color.parseColor(value);ui.accent=v;saveUi("Custom accent saved");}
            catch(Exception e){say("Use #RRGGBB");}}));
        String[] names={"Ice blue","Emerald","Amber","Violet","Rose","Slate","OEM default"};
        int[] accents={0xFFA6C9FF,0xFF70DDB8,0xFFF6BA5B,0xFFC8B3FF,0xFFF4A4BF,0xFFBCC8D8,0};
        for(int i=0;i<names.length;i++){final int col=accents[i];final String name=names[i];button(visual,"Accent: "+name,"Immediate app-only color preset",()->{ui.accent=col;saveUi(name+" saved");});}
        toggle(visual,"High-contrast app text",ui.highContrast,v->{ui.highContrast=v;saveUi("High contrast "+(v?"on":"off"));});
        toggle(visual,"AMOLED black app backdrop",ui.oledBlack,v->{ui.oledBlack=v;saveUi("Background updated");});
        toggle(visual,"Compact card layout",ui.compact,v->{ui.compact=v;saveUi("Density updated");});
        toggle(visual,"Pin preview while scrolling",ui.stickyPreview,v->{ui.stickyPreview=v;saveUi("Preview layout updated");});
        toggle(visual,"Haptic feedback in Control Center",ui.haptics,v->{ui.haptics=v;saveUi("Haptics preference updated");});
        inputNumber(visual,"App typography",ui.textScale,85,125,"%",v->{ui.textScale=v;saveUi("Text size saved");});
        inputNumber(visual,"Card opacity",ui.cardOpacity,40,100,"%",v->{ui.cardOpacity=v;saveUi("Card opacity saved");});
        inputNumber(visual,"Decorative glow",ui.backgroundStrength,0,100,"%",v->{ui.backgroundStrength=v;saveUi("Glow strength saved");});
        inputNumber(visual,"Action-button rounding",ui.buttonRoundness,2,32,"dp",v->{ui.buttonRoundness=v;saveUi("Button rounding saved");});
        LinearLayout transfer=section("Theme backups","These work independently of clock settings and preserve your preferred card style.");
        button(transfer,"Export UI options as JSON",null,()->copy(ui.json()));
        button(transfer,"Share UI options",null,()->share("Fold8 app look",ui.json()));
        button(transfer,"Save UI options backup",null,()->say(LabData.store(this,"backup_ui",ui.json())?"Appearance backup saved":"Backup failed"));
        button(transfer,"Inspect appearance backup",null,()->{
            String x=LabData.get(this,"backup_ui");say(x==null?"No appearance backup.":x);});
        button(transfer,"Restore appearance backup",null,()->{
            try{LabUiPrefs restored=LabUiPrefs.parse(LabData.get(this,"backup_ui"));
                confirm("Restore appearance?","Only app appearance will change; your clock remains untouched.",()->{ui=restored;saveUi("Appearance restored");});}
            catch(Exception e){say("Restore rejected: "+e.getMessage());}});
        button(transfer,"Import appearance from clipboard",null,()->{
            try{LabUiPrefs imported=LabUiPrefs.parse(clipboard());
                confirm("Import app appearance?","Validated. This changes only Control Lab's UI.",()->{ui=imported;saveUi("Imported appearance");});}
            catch(Exception e){say("Import rejected: "+e.getMessage());}});
        button(transfer,"Reset added UI options",null,()->confirm("Reset Control Center styling?","Restores new v1.5 UI options, without touching existing Material/Glass/3D presets.",()->{
            ui=new LabUiPrefs();saveUi("New UI options reset");}));
        button(transfer,"Copy current appearance report",null,()->copy(LabData.appearanceJson(AppearancePrefs.load(this))+"\n"+ui.json()));
    }
    private interface BoolAction{void accept(boolean value);}
    private interface IntAction{void accept(int value);}
    private interface StringAction{void accept(String value);}
    private void toggle(LinearLayout parent,String name,boolean start,BoolAction f){Switch sw=new Switch(this);sw.setText(name);
        sw.setTextColor(FG);sw.setTextSize(14);sw.setChecked(start);parent.addView(sw);
        sw.setOnCheckedChangeListener((b,v)->f.accept(v));}
    private void inputNumber(LinearLayout parent,String title,int current,int low,int high,String unit,IntAction handler){
        button(parent,title+"  •  "+current+unit,"Range "+low+"–"+high+unit,()->input(title,""+current,value->{
            try{int num=Integer.parseInt(value.trim());if(num<low||num>high)throw new NumberFormatException();
                handler.accept(num);}catch(Exception e){say("Enter a number between "+low+" and "+high);}}));
    }
    private void input(String title,String initial,StringAction onSave){
        EditText edit=new EditText(this);edit.setSingleLine(true);edit.setText(initial);edit.setTextColor(FG);
        new AlertDialog.Builder(this).setTitle(title).setView(edit).setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(d,w)->onSave.accept(edit.getText().toString())).show();
    }
    private void saveUi(String message){
        ui.save(this);
        buildShell();showTab(1);
        say(message+". Control Center refreshed. Return to Clock Studio to see the same style.");
    }

    private void deviceTools(){
        LinearLayout head=section("Live device diagnostics","Read-only snapshot. Some Samsung sensors are not exposed to third-party apps.");
        button(head,"Refresh all hardware readings","Reads public Android APIs, no root required",()->{renderMetrics();say("Diagnostics refreshed.");});
        button(head,"Copy diagnostic report",null,()->{if(latest.isEmpty())latest=LabDiagnostics.collect(this);copy(LabDiagnostics.report(latest));});
        button(head,"Share diagnostic report",null,()->{if(latest.isEmpty())latest=LabDiagnostics.collect(this);share("Fold8 device snapshot",LabDiagnostics.report(latest));});
        button(head,"Save local snapshot",null,()->{
            if(latest.isEmpty())latest=LabDiagnostics.collect(this);
            say(LabData.store(this,"diag",LabDiagnostics.report(latest))?"Local snapshot saved":"Could not save snapshot");});
        button(head,"Compare to prior snapshot",null,()->{
            String old=LabData.get(this,"diag");if(old==null){say("Save a snapshot first.");return;}
            if(latest.isEmpty())latest=LabDiagnostics.collect(this);
            String current=LabDiagnostics.report(latest);
            java.util.LinkedHashMap<String,String> before=reportMap(old),after=reportMap(current);
            StringBuilder dif=new StringBuilder("Changes since saved snapshot:\n");
            for(String key:after.keySet()){String was=before.get(key),now=after.get(key);if(was!=null&&!was.equals(now))
                dif.append(key).append(": ").append(was).append(" → ").append(now).append("\n");}
            say(dif.length()<35?"No changes detected.":dif.toString());});
        renderMetrics();
    }
    private java.util.LinkedHashMap<String,String> reportMap(String report){
        java.util.LinkedHashMap<String,String> m=new java.util.LinkedHashMap<>();for(String line:report.split("\\n")){
            int i=line.indexOf(": ");if(i>0)m.put(line.substring(0,i),line.substring(i+2));}return m;
    }
    private void renderMetrics(){
        latest=LabDiagnostics.collect(this);
        // Refresh without destroying the action controls above.
        for(int n=content.getChildCount()-1;n>=1;n--)content.removeViewAt(n);
        LinearLayout battery=section("Power and thermals","Measured battery values, not advertised charger wattage.");
        LinearLayout system=section("Memory, storage and screen","Useful for assessing Fold8 performance and UI layout.");
        LinearLayout firmware=section("Firmware and network","Current OS, hardware, app display and transport state.");
        for(int i=0;i<latest.size();i++){
            LabDiagnostics.Item item=latest.get(i);
            LinearLayout target=i<11?battery:(i<23?system:firmware);
            line(target,item.title,item.value);
            if(i<11 || item.value.equals("Not reported by this firmware")){
                // Short explanation where sensor readings need interpretation.
                target.addView(text(item.description,10,MUTED,false));
            }
        }
    }
    private void rootTools(){
        LinearLayout root=section("Root and Vector troubleshooting","Every privileged probe runs only after you tap its button. No background root polling.");
        button(root,"Test root permission","Runs the harmless `id` command via su",()->root("id","Root identity"));
        button(root,"Check System UI process","Read the actual process ID",()->root("/system/bin/pidof com.android.systemui","System UI PID"));
        button(root,"Check System UI UID","Confirm process owner without changing it",()->root("/system/bin/ps -A -o USER,PID,NAME | grep com.android.systemui | head -n 5","System UI owner"));
        button(root,"Read System UI package path","Find the Samsung SystemUI package without replacing it",()->root("/system/bin/pm path com.android.systemui","Samsung package path"));
        button(root,"Read installed Control Lab APK path","Find your module's APK",()->say(getPackageCodePath()));
        button(root,"Check module build version","Android package manager's installed version",()->{
            try{PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);say("Installed version: "+info.versionName+" ("+info.versionCode+")");}
            catch(Exception e){say(e.toString());}});
        button(root,"Calculate module APK fingerprint","SHA-256 identifies the exact installed APK",()->hashSelf());
        button(root,"Find last clock-hook messages","Latest Vector/Xposed clock lines",()->root(
            "grep -hi 'Fold8ControlLab\\|Fold8Seconds' /data/adb/lspd/log/verbose_*.log 2>/dev/null | tail -n 20", "Recent clock messages"));
        button(root,"Find last clock-hook errors","Exceptions involving this module",()->root(
            "grep -hiE 'Fold8ControlLab.*(failed|error)|SecondsHook.*(Exception|Error)' /data/adb/lspd/log/verbose_*.log 2>/dev/null | tail -n 20", "Clock errors"));
        button(root,"Find Vector loading events","Check module load and scope logs",()->root(
            "grep -hi 'Loading legacy module com.fold8.secondsclock' /data/adb/lspd/log/verbose_*.log 2>/dev/null | tail -n 12", "Vector module loads"));
        button(root,"Recent System UI crashes","Recent relevant Android logcat lines",()->root(
            "/system/bin/logcat -d -t 1500 2>/dev/null | grep -iE 'FATAL EXCEPTION|systemui.*(crash|died)' | tail -n 20", "Recent System UI crashes"));
        button(root,"KernelSU module directory list","Read module directory names without editing them",()->root(
            "ls -1 /data/adb/modules 2>/dev/null | head -n 60", "Root module list"));
        button(root,"SELinux enforcement state","Read only",()->root("/system/bin/getenforce","SELinux mode"));
        button(root,"Copy safe module-disable command","For Termux only; does not execute automatically",()->copy("su -c 'pm disable-user --user 0 com.fold8.secondsclock'"));
        button(root,"Open Control Lab app info","Android's own Disable/Uninstall controls",()->openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS));
        button(root,"Open Android Developer Options","Android settings page",()->openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        button(root,"Open Android Display Settings","Android settings page",()->openSettings(Settings.ACTION_DISPLAY_SETTINGS));
        button(root,"Open Android Battery Settings","Android settings page",()->openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        button(root,"Open accessibility settings","Android settings page",()->openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        button(root,"Open notification settings","Android settings page",()->openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS));
        button(root,"Share last root result","Android share sheet, after checking for sensitive paths",()->share("Fold8 troubleshooting",result.getText().toString()));
        button(root,"Copy last root result",null,()->copy(result.getText().toString()));
    }
    private void openSettings(String action){
        Intent i=new Intent(action);
        if(Settings.ACTION_APPLICATION_DETAILS_SETTINGS.equals(action))i.setData(Uri.parse("package:"+getPackageName()));
        if(Settings.ACTION_APP_NOTIFICATION_SETTINGS.equals(action))i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
        try{startActivity(i);}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
            catch(Exception ignored){say("Settings page unavailable on this firmware.");}}
    }
    private void root(String command,String title){
        final long id=++rootTaskId;
        say(title+": checking… Root permission may prompt you.");
        new Thread(()->{
            String output="";Process process=null;
            try{
                process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
                if(!process.waitFor(9, TimeUnit.SECONDS)){process.destroyForcibly();output="Timed out after 9 seconds";}
                else {
                    BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream()));
                    StringBuilder b=new StringBuilder();String line;while((line=reader.readLine())!=null&&b.length()<8500)b.append(line).append('\n');
                    output=b.length()==0?"No matching data available (exit "+process.exitValue()+")":b.toString();
                }
            }catch(Exception e){output="Probe failed: "+e.getMessage();}
            final String report=title+"\n"+output;
            runOnUiThread(()->{if(id==rootTaskId)say(report);});
        },"Fold8-root-probe").start();
    }
    private void hashSelf(){
        new Thread(()->{
            try{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] buf=new byte[8192];
                try(FileInputStream stream=new FileInputStream(getPackageCodePath())){int n;while((n=stream.read(buf))!=-1)md.update(buf,0,n);}
                StringBuilder hex=new StringBuilder();for(byte b:md.digest())hex.append(String.format(Locale.US,"%02x",b&0xFF));
                runOnUiThread(()->say("Installed APK SHA-256:\n"+hex));
            }catch(Exception e){runOnUiThread(()->say("Cannot hash APK: "+e.getMessage()));}
        }).start();
    }
}
