package com.fold8.secondsclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Fold8 Control Lab v1.5. New Control Center features affect the app only.
 * The Samsung status bar hook, provider, and clock preference contract are unchanged from v0.7.
 * Built with native Android widgets; no Compose/AndroidX or network permissions required.
 */
public final class ControlLabActivity extends Activity {
    private static final int WHITE = Color.rgb(247, 249, 255);
    private static final int MUTED = Color.rgb(177, 189, 214);
    private static final int BLUE = Color.rgb(148, 186, 255);
    private static final int MINT = Color.rgb(117, 224, 206);

    private AppearancePrefs appearance;
    private LabUiPrefs extraUi;
    private AmbientView ambient;
    private FrameLayout root;
    private LinearLayout main;
    private final List<LinearLayout> cards = new ArrayList<>();
    private final List<Button> themeButtons = new ArrayList<>();
    private final List<Button> cardButtons = new ArrayList<>();
    private TextView heading, subheading, overline, previewLabel, preview, previewRight;
    private SeekBar blurSeek, depthSeek, cornerSeek;
    private TextView blurValue, depthValue, cornerValue;
    private Switch motionSwitch;

    private Switch enabledSwitch, secondsSwitch, shadeSecondsSwitch, shadeGestureSwitch,
            gestureHapticSwitch, amPmSwitch;
    private SeekBar scaleSeek;
    private TextView scaleValue;
    private Spinner weightSpinner, formatSpinner;
    private EditText colorEdit;
    private Switch bubbleSwitch, gestureSwitch;
    private Spinner bubbleStyleSpinner;
    private SeekBar bubbleOpacitySeek,bubblePaddingSeek,bubbleRadiusSeek;
    private TextView bubbleOpacityValue,bubblePaddingValue,bubbleRadiusValue;
    private EditText bubbleColorEdit;
    private Switch bubbleAutoSwitch;
    private SeekBar bubbleBorderSeek,bubbleInsetSeek;
    private TextView bubbleBorderValue,bubbleInsetValue;
    private LinearLayout pinnedPreview;
    private Button demoBackgroundButton;
    private boolean previewLightBackground=false;
    // Only used by Control Lab's preview. No timers are created inside SystemUI.
    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private final Runnable previewTick = new Runnable() {
        @Override public void run() {
            // Update only the text once a second, not the pill drawables or padding.
            refreshPreviewTime();
            long remaining = 1000L - (System.currentTimeMillis() % 1000L);
            previewHandler.postDelayed(this, Math.max(50L, remaining));
        }
    };


    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        appearance = AppearancePrefs.load(this);
        extraUi = LabUiPrefs.load(this);
        getWindow().setStatusBarColor(Color.rgb(9, 13, 24));
        getWindow().setNavigationBarColor(Color.rgb(9, 13, 24));
        setTitle("Fold8 Control Lab");
        createScreen();
        showConfig(ClockConfig.loadLocal(this));
        styleScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        previewHandler.removeCallbacks(previewTick);
        previewHandler.post(previewTick);
        if (bubbleSwitch != null && !bubbleSwitch.isPressed()) {
            // Only refresh the gesture's persisted visibility; do not erase unsaved style edits.
            ClockConfig stored=ClockConfig.loadLocal(this);
            bubbleSwitch.setChecked(stored.bubbleEnabled);
            if(bubbleAutoSwitch!=null)bubbleAutoSwitch.setChecked(stored.bubbleAuto);
            if(shadeSecondsSwitch!=null && !shadeSecondsSwitch.isPressed())
                shadeSecondsSwitch.setChecked(stored.shadeSeconds);
        }
    }

    @Override protected void onPause() {
        previewHandler.removeCallbacks(previewTick);
        super.onPause();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        // The Control Center edits app-only styling. Recreate once on return so
        // the pinned/scrolling preview layout and card density stay consistent.
        if (requestCode==1500) recreate();
    }

    private void createScreen() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(10, 14, 25));
        ambient = new AmbientView();
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));

        // Android 15+ draws activities edge-to-edge by default.
        // Reserve real system-bar insets so our header isn't under the clock.
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view,insets) -> {
                Insets bars=insets.getInsets(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                root.setPadding(0,bars.top,0,bars.bottom);
                return insets;
            });
        } else root.setFitsSystemWindows(true);

        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        pinnedPreview=new LinearLayout(this);
        pinnedPreview.setOrientation(LinearLayout.VERTICAL);
        pinnedPreview.setPadding(dp(18),dp(8),dp(18),dp(0));
        page.addView(pinnedPreview,new LinearLayout.LayoutParams(-1,-2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(18), dp(22), dp(18), dp(30));
        scroll.addView(main);
        setContentView(root);

        overline = label("FOLD8  /  CONTROL LAB", 11, MINT, true);
        overline.setLetterSpacing(.16f);
        main.addView(overline);
        heading = label("Make it yours.", 31, WHITE, true);
        heading.setPadding(0, dp(8), 0, 0);
        main.addView(heading);
        subheading = label("APPEARANCE STUDIO  •  v1.5", 12, MUTED, false);
        subheading.setPadding(0, dp(4), 0, dp(10));
        main.addView(subheading);
        Button openHub = actionButton("Open Control Center  •  Diagnostics / Tools / Looks", true);
        main.addView(openHub,buttonParams(1));
        openHub.setOnClickListener(v -> startActivityForResult(
            new Intent(this, LabHubActivity.class), 1500));

        LinearLayout themes = card();
        themes.addView(label("INTERFACE STYLE", 12, MUTED, true));
        TextView styleIntro = label("These styles affect Control Lab only, never your status-bar clock.", 13, MUTED, false);
        styleIntro.setPadding(0, dp(5), 0, dp(14));
        themes.addView(styleIntro);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setWeightSum(3f);
        for (int i=0; i<3; i++) {
            final int selected = i;
            Button chip = new Button(this);
            chip.setAllCaps(false);
            chip.setText(i==0 ? "Material" : i==1 ? "Glass" : "3D Cards");
            chip.setTextSize(13);
            chip.setMinHeight(0);
            chip.setMinimumHeight(0);
            chip.setPadding(dp(1), dp(2), dp(1), dp(2));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            if (i>0) cp.leftMargin = dp(7);
            chipRow.addView(chip, cp);
            themeButtons.add(chip);
            chip.setOnClickListener(v -> {
                appearance.theme = selected;
                appearance.save(this);
                styleScreen();
            });
        }
        themes.addView(chipRow);
        TextView cardStyleLabel = label("CARD STYLING", 11, MUTED, true);
        cardStyleLabel.setPadding(0, dp(16), 0, dp(10));
        themes.addView(cardStyleLabel);
        LinearLayout cardRow = new LinearLayout(this);
        cardRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i=0; i<3; i++) {
            final int selectedStyle = i;
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(i==0?"Floating":i==1?"Outlined":"Solid");
            b.setTextSize(12);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            if(i>0) cp.leftMargin=dp(7);
            cardRow.addView(b,cp);
            cardButtons.add(b);
            b.setOnClickListener(v -> {
                appearance.cardStyle=selectedStyle;
                appearance.save(this);
                styleScreen();
            });
        }
        themes.addView(cardRow);

        LinearLayout previewCard = card();
        if(extraUi.stickyPreview) {
            main.removeView(previewCard);
            pinnedPreview.addView(previewCard,new LinearLayout.LayoutParams(-1,-2));
        }
        previewCard.setPadding(dp(12),dp(9),dp(12),dp(10));
        previewLabel = label("PINNED CLOCK PREVIEW", 11, MUTED, true);
        previewLabel.setLetterSpacing(.11f);
        previewCard.addView(previewLabel);
        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        previewRow.setPadding(0, dp(7), 0, dp(3));
        preview = label("--:--:--", 22, WHITE, true);
        preview.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        previewRow.addView(preview, new LinearLayout.LayoutParams(-2, -2));
        previewRow.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));
        previewRight = label("●  5G  ▰", 13, MUTED, false);
        previewRow.addView(previewRight);
        previewCard.addView(previewRow);
        demoBackgroundButton = actionButton("Preview bright wallpaper",false);
        demoBackgroundButton.setTextSize(11);
        demoBackgroundButton.setMinHeight(0);
        previewCard.addView(demoBackgroundButton,new LinearLayout.LayoutParams(-1,dp(32)));
        demoBackgroundButton.setOnClickListener(v -> {
            previewLightBackground=!previewLightBackground;
            demoBackgroundButton.setText(previewLightBackground?"Preview dark wallpaper":"Preview bright wallpaper");
            updatePreview();
        });

        LinearLayout lookCard = card();
        lookCard.addView(label("ATMOSPHERE", 12, MUTED, true));
        blurValue = addSlider(lookCard, "Backdrop blur", 0, 40, appearance.blur, v -> {
            appearance.blur=v; appearance.save(this); styleScreen();
        });
        blurSeek = lastSlider;
        depthValue = addSlider(lookCard, "Card elevation", 0, 18, appearance.depth, v -> {
            appearance.depth=v; appearance.save(this); styleScreen();
        });
        depthSeek = lastSlider;
        cornerValue = addSlider(lookCard, "Corner radius", 12, 32, appearance.radius, v -> {
            appearance.radius=v; appearance.save(this); styleScreen();
        });
        cornerSeek = lastSlider;
        motionSwitch = new Switch(this);
        motionSwitch.setText("Reduce animations");
        motionSwitch.setTextColor(WHITE);
        motionSwitch.setTextSize(14);
        motionSwitch.setPadding(0, dp(12), 0, dp(2));
        motionSwitch.setChecked(appearance.reduceMotion);
        lookCard.addView(motionSwitch);
        motionSwitch.setOnCheckedChangeListener((b,checked) -> {
            appearance.reduceMotion=checked;
            appearance.save(this);
            styleScreen();
        });
        TextView hint = label("Blur affects the decorative background inside this app. It never blurs Samsung's status bar or other apps.", 11, MUTED, false);
        hint.setPadding(0, dp(9), 0, 0);
        lookCard.addView(hint);

        TextView clockHeading = label("CLOCK STUDIO", 12, MINT, true);
        clockHeading.setLetterSpacing(.13f);
        clockHeading.setPadding(dp(4), dp(12), 0, dp(13));
        main.addView(clockHeading);

        LinearLayout toggles = card();
        enabledSwitch = createSwitch("Enable clock customization");
        secondsSwitch = createSwitch("Show seconds in the status bar");
        secondsSwitch.setPadding(0, dp(12), 0, 0);
        toggles.addView(enabledSwitch);
        toggles.addView(secondsSwitch);
        shadeSecondsSwitch = createSwitch("Show seconds in Quick Settings");
        shadeSecondsSwitch.setPadding(0, dp(12), 0, 0);
        toggles.addView(shadeSecondsSwitch);
        TextView shadeHelp=label("Also updates the clock in the notification / Quick Settings drop-down. "
                + "Your status-bar size, color and bubble stay independent.",11,MUTED,false);
        shadeHelp.setPadding(0,dp(7),0,0);
        toggles.addView(shadeHelp);

        LinearLayout size = card();
        size.addView(label("Clock size", 16, WHITE, true));
        scaleValue = label("110%", 15, BLUE, true);
        size.addView(scaleValue);
        scaleSeek = new SeekBar(this);
        scaleSeek.setMax(50);
        scaleSeek.setProgressTintList(ColorStateList.valueOf(BLUE));
        scaleSeek.setThumbTintList(ColorStateList.valueOf(BLUE));
        size.addView(scaleSeek);
        size.addView(label("85%                                       135%", 11, MUTED, false));

        LinearLayout textStyle = card();
        textStyle.addView(label("Clock weight", 14, MUTED, false));
        weightSpinner = spinner(new String[] {"Samsung default", "Medium", "Bold"});
        textStyle.addView(weightSpinner);
        TextView format = label("Time format", 14, MUTED, false);
        format.setPadding(0, dp(14), 0, 0);
        textStyle.addView(format);
        formatSpinner = spinner(new String[] {"Follow system", "12-hour", "24-hour"});
        textStyle.addView(formatSpinner);
        amPmSwitch = createSwitch("Show AM/PM in 12-hour time");
        amPmSwitch.setPadding(0, dp(12), 0, 0);
        textStyle.addView(amPmSwitch);

        LinearLayout colors = card();
        colors.addView(label("Clock color", 15, WHITE, true));
        TextView colorHelp = label("Optional #RRGGBB. Blank uses Samsung's original color.", 12, MUTED, false);
        colorHelp.setPadding(0, dp(6), 0, dp(9));
        colors.addView(colorHelp);
        colorEdit = new EditText(this);
        colorEdit.setTextColor(WHITE);
        colorEdit.setHintTextColor(MUTED);
        colorEdit.setHint("Samsung default");
        colorEdit.setSingleLine(true);
        colorEdit.setTextSize(15);
        colorEdit.setPadding(dp(12), dp(9), dp(12), dp(9));
        colorEdit.setBackground(surface(Color.rgb(35,44,59),Color.rgb(55,70,89),12));
        colors.addView(colorEdit);

        TextView bubbleHeading=label("CLOCK BACKGROUND",12,MINT,true);
        bubbleHeading.setLetterSpacing(.13f);
        bubbleHeading.setPadding(dp(4),dp(12),0,dp(13));
        main.addView(bubbleHeading);
        LinearLayout bubbleCard=card();
        bubbleSwitch=createSwitch("Show bubble behind clock");
        bubbleCard.addView(bubbleSwitch);
        bubbleCard.addView(label("Pill appearance",14,MUTED,false));
        bubbleStyleSpinner=spinner(new String[]{"Tinted glass", "Opaque", "Auto tint", "Soft gradient"});
        bubbleCard.addView(bubbleStyleSpinner);
        TextView help=label("Styles control the pill's look. Auto visibility below estimates contrast from wallpaper colors; it cannot see every app behind the status bar.",11,MUTED,false);
        help.setPadding(0,dp(5),0,dp(9));
        bubbleCard.addView(help);
        bubbleAutoSwitch=createSwitch("Auto show only when contrast is low");
        bubbleCard.addView(bubbleAutoSwitch);
        TextView autoHelp=label("Works best on the home screen. Uses a wallpaper color estimate, NOT live screenshots. Double-tap switches back to manual control.",11,MUTED,false);
        autoHelp.setPadding(0,dp(4),0,dp(8));
        bubbleCard.addView(autoHelp);
        bubbleOpacityValue=addSlider(bubbleCard,"Opacity",15,95,45, v->updatePreview());
        bubbleOpacitySeek=lastSlider;
        bubblePaddingValue=addSlider(bubbleCard,"Side padding",2,12,4,v->updatePreview());
        bubblePaddingSeek=lastSlider;
        bubbleRadiusValue=addSlider(bubbleCard,"Corner radius",6,28,18,v->updatePreview());
        bubbleRadiusSeek=lastSlider;
        bubbleBorderValue=addSlider(bubbleCard,"Outline thickness",0,3,1,v->updatePreview());
        bubbleBorderSeek=lastSlider;
        bubbleInsetValue=addSlider(bubbleCard,"Top/bottom inset",1,5,3,v->updatePreview());
        bubbleInsetSeek=lastSlider;
        TextView bubbleColorLabel=label("Bubble color (optional #RRGGBB)",13,MUTED,false);
        bubbleColorLabel.setPadding(0,dp(10),0,dp(4));
        bubbleCard.addView(bubbleColorLabel);
        bubbleColorEdit=new EditText(this);
        bubbleColorEdit.setTextColor(WHITE);
        bubbleColorEdit.setHintTextColor(MUTED);
        bubbleColorEdit.setHint("Automatic dark/light");
        bubbleColorEdit.setSingleLine(true);
        bubbleColorEdit.setTextSize(14);
        bubbleColorEdit.setPadding(dp(12),dp(8),dp(12),dp(8));
        bubbleColorEdit.setBackground(surface(0xFF232C3B,0xFF374659,12));
        bubbleCard.addView(bubbleColorEdit);
        gestureSwitch=createSwitch("Double-tap clock to toggle bubble");
        gestureSwitch.setPadding(0,dp(17),0,0);
        bubbleCard.addView(gestureSwitch);
        TextView gestureHelp=label("Optional. Double-tap the status-bar clock to show or hide its bubble. "
                + "A single tap keeps Samsung's action after a short delay; drags do not toggle it.",11,MUTED,false);
        gestureHelp.setPadding(0,dp(7),0,0);
        bubbleCard.addView(gestureHelp);
        shadeGestureSwitch=createSwitch("Double-tap Quick Settings clock to toggle seconds");
        shadeGestureSwitch.setPadding(0,dp(17),0,0);
        bubbleCard.addView(shadeGestureSwitch);
        gestureHapticSwitch=createSwitch("Vibrate after a clock double-tap");
        gestureHapticSwitch.setPadding(0,dp(12),0,0);
        bubbleCard.addView(gestureHapticSwitch);

        TextView presetsTitle=label("SAVED CLOCK LOOKS",12,MINT,true);
        presetsTitle.setLetterSpacing(.13f);
        presetsTitle.setPadding(dp(4),dp(13),0,dp(12));
        main.addView(presetsTitle);
        LinearLayout presetsCard=card();
        presetsCard.addView(label("Compare styles without changing System UI",14,WHITE,true));
        TextView presetHint=label("Save your current slider settings into either slot. Load only changes the controls and preview. Tap Apply when you're satisfied.",11,MUTED,false);
        presetHint.setPadding(0,dp(5),0,dp(9));
        presetsCard.addView(presetHint);
        addPresetRow(presetsCard,1);
        addPresetRow(presetsCard,2);
        Button exportSettings=actionButton("Copy settings summary",false);
        exportSettings.setTextSize(13);
        LinearLayout.LayoutParams exportParams=new LinearLayout.LayoutParams(-1,dp(43));
        exportParams.topMargin=dp(10);
        presetsCard.addView(exportSettings,exportParams);
        exportSettings.setOnClickListener(v -> copyClockSummary());

        Button apply = actionButton("Apply clock settings", false);
        main.addView(apply, buttonParams(18));
        apply.setOnClickListener(v -> {
            ClockConfig proposed = readClockConfig();
            if (proposed == null) return;
            if (ClockConfig.save(this, proposed)) {
                Toast.makeText(this, "Saved. Use Apply + restart System UI if changes haven't appeared.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Clock settings could not be saved", Toast.LENGTH_LONG).show();
            }
        });
        Button applyRefresh = actionButton("Apply + restart System UI", true);
        main.addView(applyRefresh, buttonParams(7));
        applyRefresh.setOnClickListener(v -> confirmApplyAndRestart());
        Button restore = actionButton("Restore clock defaults", false);
        main.addView(restore, buttonParams(7));
        restore.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Restore clock defaults?")
            .setMessage("Returns the clock to 110%, seconds on, default color, and bubble off. App themes are not reset.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore", (dialog, which) -> {
                ClockConfig original = ClockConfig.defaults();
                showConfig(original);
                ClockConfig.save(this, original);
            }).show());

        TextView footer = label("CONTROL LAB v1.6  •  CONTROL CENTER  •  KEEP YOUR v1.5 BACKUP", 10, MUTED, true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(5), dp(22), dp(5), dp(8));
        main.addView(footer);

        enabledSwitch.setOnCheckedChangeListener((b,checked)-> updatePreview());
        secondsSwitch.setOnCheckedChangeListener((b,checked)-> updatePreview());
        shadeSecondsSwitch.setOnCheckedChangeListener((b,checked)-> updatePreview());
        shadeGestureSwitch.setOnCheckedChangeListener((b,checked)-> updatePreview());
        gestureHapticSwitch.setOnCheckedChangeListener((b,checked)-> updatePreview());
        amPmSwitch.setOnCheckedChangeListener((b,checked)-> updatePreview());
        scaleSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar,int v,boolean fromUser) {
                scaleValue.setText((v+85)+"%");updatePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        AdapterView.OnItemSelectedListener l = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a,View v,int pos,long id) {updatePreview();}
            @Override public void onNothingSelected(AdapterView<?> a) {}
        };
        weightSpinner.setOnItemSelectedListener(l);
        formatSpinner.setOnItemSelectedListener(l);
        colorEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int st,int co,int after) {}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count) {updatePreview();}
            @Override public void afterTextChanged(Editable e) {}
        });
        bubbleSwitch.setOnCheckedChangeListener((b,checked)->updatePreview());
        bubbleAutoSwitch.setOnCheckedChangeListener((b,checked)->updatePreview());
        gestureSwitch.setOnCheckedChangeListener((b,checked)->updatePreview());
        bubbleStyleSpinner.setOnItemSelectedListener(l);
        bubbleColorEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int st,int co,int after) {}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count) {updatePreview();}
            @Override public void afterTextChanged(Editable e) {}
        });
    }

    private interface ValueAction {void accept(int value);}
    private SeekBar lastSlider;
    private TextView addSlider(LinearLayout parent, String name, int min, int max, int current, ValueAction callback) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(11), 0, 0);
        TextView caption = label(name, 14, WHITE, false);
        line.addView(caption,new LinearLayout.LayoutParams(0,-2,1f));
        final String unit = "Opacity".equals(name) ? "%" : " dp";
        TextView amount = label(current+unit, 13, BLUE, true);
        line.addView(amount);
        parent.addView(line);
        SeekBar slider = new SeekBar(this);
        slider.setMax(max-min);
        slider.setProgress(current-min);
        slider.setProgressTintList(ColorStateList.valueOf(BLUE));
        slider.setThumbTintList(ColorStateList.valueOf(BLUE));
        parent.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s,int v,boolean fromUser) {
                int now=v+min;
                amount.setText(now+unit);
                if(fromUser) callback.accept(now);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        lastSlider=slider;
        return amount;
    }

    private void styleScreen() {
        boolean glass = appearance.theme==AppearancePrefs.GLASS;
        boolean depth = appearance.theme==AppearancePrefs.DEPTH;
        int bg = glass ? 0xFF0C1428 : depth ? 0xFF101018 : 0xFF0F151C;
        int accent = materialAccent();
        if (glass) accent = 0xFFA6CBFF;
        if (depth) accent = 0xFFAEAAF6;
        if (extraUi.oledBlack) bg = Color.BLACK;
        if (extraUi.accent != 0) accent=extraUi.accent;
        if (extraUi.highContrast) accent=0xFFFFFFFF;
        root.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        ambient.setScheme(appearance.theme);
        ambient.setAlpha(extraUi.oledBlack?0f:extraUi.backgroundStrength/100f);
        if(Build.VERSION.SDK_INT>=31 && glass && appearance.blur>0) {
            float r=dp(appearance.blur);
            ambient.setRenderEffect(RenderEffect.createBlurEffect(r,r,Shader.TileMode.CLAMP));
        } else if (Build.VERSION.SDK_INT >= 31) {
            ambient.setRenderEffect(null);
        }
        for (int i=0;i<cards.size();i++) {
            LinearLayout card = cards.get(i);
            int alpha = glass ? 0xA0 : 0xF2;
            alpha = Math.round(alpha * extraUi.cardOpacity / 100f);
            int top = glass ? 0xFF27364C : depth ? 0xFF333144 : 0xFF262F35;
            int bottom= glass ? 0xFF1D263D : depth ? 0xFF191C2B : 0xFF1E292E;
            if (glass) { top=(top&0x00FFFFFF)|(alpha<<24);bottom=(bottom&0x00FFFFFF)|((alpha-12)<<24); }
            boolean outline = appearance.cardStyle == AppearancePrefs.OUTLINED;
            boolean solid = appearance.cardStyle == AppearancePrefs.SOLID;
            if (solid) { top=0xFF293241; bottom=0xFF222B3A; }
            if (outline) {
                top = (top & 0x00FFFFFF) | 0x78000000;
                bottom = (bottom & 0x00FFFFFF) | 0x70000000;
            }
            if(extraUi.cardOpacity<100) {
                top = (top & 0x00FFFFFF) | (Math.min(Color.alpha(top),Math.round(255*extraUi.cardOpacity/100f))<<24);
                bottom = (bottom & 0x00FFFFFF) | (Math.min(Color.alpha(bottom),Math.round(255*extraUi.cardOpacity/100f))<<24);
            }
            GradientDrawable plate = surface(top,bottom,appearance.radius);
            plate.setStroke(outline ? dp(2) : dp(1), solid ? Color.TRANSPARENT : (outline ? 0x92BDDCFF : 0x44D6E2FF));
            card.setBackground(plate);
            float elevation = solid || outline ? 0 : (appearance.theme==AppearancePrefs.MATERIAL ? 1f : appearance.depth)*(getResources().getDisplayMetrics().density);
            card.setElevation(elevation);
            card.setTranslationZ(depth && !solid && !outline ? dp(2) : 0f);
        }
        for(int i=0;i<themeButtons.size();i++) {
            Button b=themeButtons.get(i);
            boolean selected=appearance.theme==i;
            b.setTextColor(selected?0xFF102136:WHITE);
            b.setTypeface(null,selected?Typeface.BOLD:Typeface.NORMAL);
            b.setBackground(surface(selected?accent:0xFF30384B,selected?accent:0xFF30384B,14));
            b.setElevation(selected ? dp(5) : 0);
            b.animate().cancel();
            if (appearance.reduceMotion) {
                b.setScaleX(1f); b.setScaleY(1f);
            } else {
                b.animate().scaleX(selected ? 1.015f : 1f).scaleY(selected ? 1.015f : 1f).setDuration(160).start();
            }
        }
        for (int i=0; i<cardButtons.size(); i++) {
            Button b=cardButtons.get(i);
            boolean selected=appearance.cardStyle==i;
            b.setTextColor(selected ? 0xFF102136 : WHITE);
            b.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            b.setBackground(surface(selected?accent:0xFF30384B,selected?accent:0xFF30384B,12));
            b.setElevation(selected ? dp(4) : 0);
        }
        ColorStateList tint = ColorStateList.valueOf(accent);
        for(SeekBar seek : new SeekBar[]{blurSeek,depthSeek,cornerSeek,scaleSeek,bubbleOpacitySeek,bubblePaddingSeek,bubbleRadiusSeek,bubbleBorderSeek,bubbleInsetSeek}) {
            if(seek != null) { seek.setProgressTintList(tint); seek.setThumbTintList(tint); }
        }
        for(Switch sw : new Switch[]{motionSwitch,enabledSwitch,secondsSwitch,shadeSecondsSwitch,
                shadeGestureSwitch,gestureHapticSwitch,amPmSwitch,bubbleSwitch,bubbleAutoSwitch,gestureSwitch}) {
            if(sw != null) sw.setThumbTintList(tint);
        }
        if(previewLabel!=null) previewLabel.setText(glass ? "GLASS STUDIO / PREVIEW" : depth ? "3D DEPTH / PREVIEW" : "MATERIAL YOU / PREVIEW");
        if(previewRight!=null) previewRight.setTextColor(accent);
        overline.setTextColor(accent);
        if(blurSeek!=null) {
            blurSeek.setEnabled(glass);
            blurValue.setText(appearance.blur+" dp"+(glass?"":" · glass only"));
        }
        if(depthSeek!=null)depthValue.setText(appearance.depth+" dp");
        if(cornerSeek!=null)cornerValue.setText(appearance.radius+" dp");
        if(bubbleStyleSpinner!=null && bubbleOpacitySeek!=null)
            bubbleOpacitySeek.setEnabled(bubbleStyleSpinner.getSelectedItemPosition()!=1);
        if(!appearance.reduceMotion) {
            // Only animate in-app decorative artwork. Never animate the clock in SystemUI.
            if (ambient.getAlpha()!=1f) ambient.animate().alpha(1f).setDuration(180).start();
        } else ambient.animate().cancel();
        updatePreview();
    }

    private int materialAccent() {
        if(Build.VERSION.SDK_INT>=31) {
            try {return getColor(android.R.color.system_accent1_200);}catch(Exception ignored){}
        }
        return MINT;
    }

    private void showConfig(ClockConfig c) {
        enabledSwitch.setChecked(c.enabled);
        secondsSwitch.setChecked(c.seconds);
        shadeSecondsSwitch.setChecked(c.shadeSeconds);
        shadeGestureSwitch.setChecked(c.doubleTapShade);
        gestureHapticSwitch.setChecked(c.gestureHaptic);
        amPmSwitch.setChecked(c.showAmPm);
        scaleSeek.setProgress(c.scale-85);
        scaleValue.setText(c.scale+"%");
        weightSpinner.setSelection(c.weight);
        formatSpinner.setSelection(c.format);
        colorEdit.setText(ClockConfig.printColor(c.color));
        bubbleSwitch.setChecked(c.bubbleEnabled);
        bubbleAutoSwitch.setChecked(c.bubbleAuto);
        bubbleStyleSpinner.setSelection(c.bubbleStyle-1);
        bubbleOpacitySeek.setProgress(c.bubbleOpacity-15);
        bubblePaddingSeek.setProgress(c.bubblePadding-2);
        bubbleRadiusSeek.setProgress(c.bubbleRadius-6);
        bubbleColorEdit.setText(ClockConfig.printColor(c.bubbleColor));
        bubbleBorderSeek.setProgress(c.bubbleBorder);
        bubbleInsetSeek.setProgress(c.bubbleInset-1);
        gestureSwitch.setChecked(c.doubleTapBubble);
        updatePreview();
    }

    private ClockConfig readClockConfig() {
        int color;
        try {color=ClockConfig.parseColor(colorEdit.getText().toString());}
        catch (IllegalArgumentException e) {colorEdit.setError(e.getMessage());return null;}
        int bubbleColor;
        try {bubbleColor=ClockConfig.parseColor(bubbleColorEdit.getText().toString());}
        catch(IllegalArgumentException e){bubbleColorEdit.setError(e.getMessage());return null;}
        ClockConfig result = new ClockConfig(enabledSwitch.isChecked(),secondsSwitch.isChecked(),
            scaleSeek.getProgress()+85,weightSpinner.getSelectedItemPosition(),
            formatSpinner.getSelectedItemPosition(),color,
            bubbleSwitch.isChecked(),bubbleStyleSpinner.getSelectedItemPosition()+1,
            bubbleOpacitySeek.getProgress()+15,bubblePaddingSeek.getProgress()+2,
            bubbleRadiusSeek.getProgress()+6,bubbleColor,gestureSwitch.isChecked(),
            bubbleAutoSwitch.isChecked(),bubbleBorderSeek.getProgress(),bubbleInsetSeek.getProgress()+1);
        result.shadeSeconds = shadeSecondsSwitch.isChecked();
        result.doubleTapShade = shadeGestureSwitch.isChecked();
        result.gestureHaptic = gestureHapticSwitch.isChecked();
        result.showAmPm = amPmSwitch.isChecked();
        return result;
    }

    /** A lightweight 1 Hz text refresh while the Activity is visible. */
    private void refreshPreviewTime() {
        if (preview==null || enabledSwitch==null || secondsSwitch==null) return;
        boolean seconds=enabledSwitch.isChecked() && secondsSwitch.isChecked();
        int fmt=formatSpinner==null?0:formatSpinner.getSelectedItemPosition();
        boolean use24=fmt==2 || (fmt==0 && DateFormat.is24HourFormat(this));
        String pattern=use24 ? (seconds?"HH:mm:ss":"HH:mm") : (seconds?"h:mm:ss":"h:mm");
        if (!use24 && amPmSwitch != null && amPmSwitch.isChecked()) pattern += " a";
        preview.setText(DateFormat.format(pattern,System.currentTimeMillis()));
    }

    private void updatePreview() {
        if(preview==null || enabledSwitch==null || scaleSeek==null) return;
        boolean enabled=enabledSwitch.isChecked();
        refreshPreviewTime();
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP,
            22f*(enabled?scaleSeek.getProgress()+85:100)/100f);
        int w=weightSpinner==null?0:weightSpinner.getSelectedItemPosition();
        preview.setTypeface(Typeface.create("sans-serif-medium",enabled&&w==2?Typeface.BOLD:Typeface.NORMAL));
        int tint=WHITE;
        if(enabled&&colorEdit!=null) {
            try {int c=ClockConfig.parseColor(colorEdit.getText().toString());if(c!=0)tint=c;}
            catch(IllegalArgumentException ignored){}
        }
        preview.setTextColor(tint);
        // Preview low/high contrast without accessing phone screenshots.
        int simulatedWallpaper=previewLightBackground?0xFFF0F4FF:0xFF172033;
        boolean lowContrast=Math.abs(brightness(tint)-brightness(simulatedWallpaper))<120;
        boolean showBubble=bubbleSwitch!=null && bubbleSwitch.isChecked() && enabled
                && (!bubbleAutoSwitch.isChecked() || lowContrast);
        if (showBubble) {
            int style=bubbleStyleSpinner.getSelectedItemPosition()+1;
            boolean lightText=brightness(tint)>130;
            int rgb=lightText?0xFF0A1425:0xFFF5F8FF;
            try {int chosen=ClockConfig.parseColor(bubbleColorEdit.getText().toString());
                 if(chosen!=0)rgb=chosen;}catch(IllegalArgumentException ignored){}
            int alpha=style==2?255:Math.round(255f*(bubbleOpacitySeek.getProgress()+15)/100f);
            int first=(rgb&0x00FFFFFF)|(alpha<<24);
            int second=(style==1||style==4)?Color.argb(alpha,Math.min(255,Color.red(rgb)+18),
                Math.min(255,Color.green(rgb)+18),Math.min(255,Color.blue(rgb)+18)):first;
            GradientDrawable pill=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,second});
            pill.setCornerRadius(dp(bubbleRadiusSeek.getProgress()+6));
            if(bubbleBorderSeek.getProgress()>0) pill.setStroke(dp(bubbleBorderSeek.getProgress()),0x66FFFFFF);
            int inset=dp(bubbleInsetSeek.getProgress()+1);
            preview.setBackground(new android.graphics.drawable.InsetDrawable(pill,0,inset,0,inset));
            int pad=dp(bubblePaddingSeek.getProgress()+2);
            preview.setPadding(pad,0,pad,0);
        } else {
            preview.setBackground(null);
            preview.setPadding(0,0,0,0);
        }
        if (bubbleOpacitySeek!=null && bubbleStyleSpinner!=null)
            bubbleOpacitySeek.setEnabled(bubbleStyleSpinner.getSelectedItemPosition()!=1);
    }

    private void addPresetRow(LinearLayout parent, int slot) {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,dp(50));
        rowParams.topMargin=dp(7);
        parent.addView(row,rowParams);

        Button save=actionButton("Save " + (slot==1?"A":"B"),false);
        Button load=actionButton("Load " + (slot==1?"A":"B"),false);
        save.setTextSize(13);
        load.setTextSize(13);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1f);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1f);
        right.leftMargin=dp(8);
        row.addView(save,left);
        row.addView(load,right);
        save.setOnClickListener(v -> {
            ClockConfig config=readClockConfig();
            if(config==null)return;
            Runnable persist=() -> {
                boolean ok=LocalPresets.save(this,slot,config);
                Toast.makeText(this,ok?"Saved look " + (slot==1?"A":"B") : "Could not save look",Toast.LENGTH_SHORT).show();
            };
            if(LocalPresets.exists(this,slot)) {
                new AlertDialog.Builder(this)
                    .setTitle("Overwrite look " + (slot==1?"A":"B") + "?")
                    .setMessage("The previous saved look in this slot will be replaced. Your active status-bar clock will not change.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Overwrite",(d,w)->persist.run())
                    .show();
            } else persist.run();
        });
        load.setOnClickListener(v -> {
            ClockConfig stored=LocalPresets.load(this,slot);
            if(stored==null) {
                Toast.makeText(this,"No saved look in slot " + (slot==1?"A":"B"),Toast.LENGTH_SHORT).show();
                return;
            }
            showConfig(stored);
            Toast.makeText(this,"Loaded into preview. Tap Apply to save to the real clock.",Toast.LENGTH_LONG).show();
        });
    }

    private void copyClockSummary() {
        ClockConfig c=readClockConfig();
        if(c==null)return;
        String summary="Fold8 Control Lab 1.6\n" +
            "Clock: enabled=" + c.enabled + ", seconds=" + c.seconds +
            ", size=" + c.scale + "%, weight=" + c.weight + ", format=" + c.format +
            ", color=" + ClockConfig.printColor(c.color) + "\n" +
            "Bubble: visible=" + c.bubbleEnabled + ", auto=" + c.bubbleAuto +
            ", style=" + c.bubbleStyle + ", opacity=" + c.bubbleOpacity + "%" +
            ", side=" + c.bubblePadding + "dp, radius=" + c.bubbleRadius +
            "dp, inset=" + c.bubbleInset + "dp, border=" + c.bubbleBorder +
            "dp, doubleTap=" + c.doubleTapBubble + "\n" +
            "Quick Settings: seconds=" + c.shadeSeconds + ", doubleTapSeconds=" + c.doubleTapShade
                + ", gestureHaptic=" + c.gestureHaptic + ", AM/PM=" + c.showAmPm + "\n" +
            "Theme: " + appearance.theme + ", cards=" + appearance.cardStyle +
            ", blur=" + appearance.blur + ", elevation=" + appearance.depth;
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Fold8 Control Lab settings",summary));
            Toast.makeText(this,"Settings copied. No passwords or root data included.",Toast.LENGTH_LONG).show();
        }
    }

    private static int brightness(int c) {
        return (Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)/1000;
    }

    // The only privileged behavior in Control Lab: refresh SystemUI on explicit consent.
    // Existing hook, provider, clock size algorithm and Appearance Studio are unchanged.
    private static final String RESTART_COMMAND =
        "pid=$(/system/bin/pidof -s com.android.systemui); "
        + "[ -n \"$pid\" ] || exit 4; /system/bin/kill -TERM \"$pid\"";

    private void confirmApplyAndRestart() {
        ClockConfig updated = readClockConfig();
        if (updated == null) return;
        if (!ClockConfig.save(this, updated)) {
            Toast.makeText(this, "Clock settings could not be saved", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Restart System UI?")
            .setMessage("Settings saved. Restart Samsung System UI to apply them without rebooting your phone. "
                + "Your status bar and screen may briefly disappear. "
                + "If the System UI hook causes a crash loop, use your verified KernelSU recovery route.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restart", (dialog, which) -> restartSystemUi())
            .show();
    }

    private void restartSystemUi() {
        new Thread(() -> {
            int exit = -1;
            String failure = null;
            Process child = null;
            try {
                // Request root only after explicit confirmation. No shell expansion of user input.
                child = new ProcessBuilder("su", "-c", RESTART_COMMAND)
                        .redirectErrorStream(true)
                        .start();
                if (!child.waitFor(45, java.util.concurrent.TimeUnit.SECONDS)) {
                    child.destroy();
                    failure = "Root request timed out";
                } else {
                    exit = child.exitValue();
                    if (exit != 0) failure = "Root command exited with code " + exit;
                }
            } catch (Throwable t) {
                failure = t.getClass().getSimpleName();
            } finally {
                if (child != null) child.destroy();
            }
            final boolean success = (exit == 0 && failure == null);
            final String reason = failure;
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, "System UI restart requested", Toast.LENGTH_LONG).show();
                } else {
                    showManualRestart(reason);
                }
            });
        }, "Fold8-SystemUI-Refresh").start();
    }

    private void showManualRestart(String reason) {
        final String terminalCommand = "su -c '" + RESTART_COMMAND + "'";
        String message = "Couldn't restart System UI directly" +
                (reason == null ? "." : " (" + reason + ").") +
                "\nYou can copy the command and run it in Termux if needed.";
        new AlertDialog.Builder(this)
            .setTitle("System UI was not restarted")
            .setMessage(message)
            .setNegativeButton("Close", null)
            .setPositiveButton("Copy Termux command", (dialog, which) -> {
                ClipboardManager clipboard =
                    (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("System UI restart", terminalCommand));
                    Toast.makeText(this, "Command copied", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        int pad=extraUi!=null && extraUi.compact?12:18;
        v.setPadding(dp(pad),dp(pad),dp(pad),dp(pad));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(13);
        main.addView(v,p);
        cards.add(v);
        return v;
    }

    private Switch createSwitch(String text) {
        Switch s=new Switch(this);
        s.setText(text);
        s.setTextColor(WHITE);
        s.setTextSize(15);
        return s;
    }

    private Spinner spinner(String[] choices) {
        Spinner s=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,choices) {
            @Override public View getView(int position, View reuse, ViewGroup parent) {
                TextView v=(TextView)super.getView(position,reuse,parent);
                v.setTextColor(WHITE);v.setTextSize(15);
                v.setPadding(dp(7),dp(13),dp(7),dp(9));return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        return s;
    }

    private Button actionButton(String text, boolean accent) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(accent?0xFF0E1D34:WHITE);
        int selected=extraUi!=null && extraUi.accent!=0?extraUi.accent:BLUE;
        b.setBackground(surface(accent?selected:0xFF333C50,accent?selected:0xFF333C50,
            extraUi==null?15:extraUi.buttonRoundness));
        return b;
    }
    private LinearLayout.LayoutParams buttonParams(int margin) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(53));
        p.topMargin=dp(margin);
        return p;
    }
    private GradientDrawable surface(int top,int bottom,int radius) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1),0x44D6E2FF);
        return d;
    }
    private TextView label(String text,int size,int color,boolean bold) {
        TextView v=new TextView(this);
        v.setText(text);v.setTextColor(extraUi!=null&&extraUi.highContrast&&color==MUTED?WHITE:color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP,size*(extraUi==null?1f:extraUi.textScale/100f));
        if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        return v;
    }
    private int dp(float v) {return Math.round(v*getResources().getDisplayMetrics().density);}

    private final class AmbientView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int theme=AppearancePrefs.GLASS;
        AmbientView() {super(ControlLabActivity.this);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
        void setScheme(int value) {theme=value;invalidate();}
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w=getWidth(),h=getHeight();
            if(w==0||h==0)return;
            paint.setShader(new LinearGradient(0,0,w,h,
                theme==AppearancePrefs.MATERIAL?0xFF123039:theme==AppearancePrefs.DEPTH?0xFF151527:0xFF111B3E,
                0xFF0B101C,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,paint);
            paint.setShader(new RadialGradient(w*.2f,h*.12f,w*.75f,
                new int[]{theme==AppearancePrefs.DEPTH?0x805E43D1:0x88709EE8,0x00466BB3},
                null,Shader.TileMode.CLAMP));
            c.drawCircle(w*.2f,h*.12f,w*.75f,paint);
            paint.setShader(new RadialGradient(w*1.08f,h*.45f,w*.65f,
                new int[]{0x6651D7C5,0x001D7FA9},null,Shader.TileMode.CLAMP));
            c.drawCircle(w*1.08f,h*.45f,w*.65f,paint);
            paint.setShader(null);
        }
    }
}
