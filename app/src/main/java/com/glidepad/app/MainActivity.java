package com.glidepad.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity implements HidManager.Listener {

    private static final int REQ_PERMISSIONS = 1;
    private static final int PANEL_NONE = 0, PANEL_KEYS = 1, PANEL_SHORTCUTS = 2, PANEL_MEDIA = 3;

    private interface FloatSetter { void set(float v); }

    private interface BoolSetter { void set(boolean v); }

    private HidManager hid;
    private Settings settings;
    private SharedPreferences prefs;
    private float dp;

    private View content, connectPanel, settingsOverlay, settingsSheet, clickBar, statusDot, firstTimeCard, pairedCard, pairHint;
    private TextView statusText, connectStatus, enableBtn, settingsConnLabel, settingsConnBtn;
    private LinearLayout steps, pairedList, keyRowContent, shortcutPanel, mediaPanel, settingsContent;
    private View keyRow, workArea;
    private LinearLayout bottomDock, sideDock;
    private MacKeyboardView macKeyboard;
    private ImageButton btnRotate, btnKeyboard, btnShortcuts, btnMedia, btnSettings;
    private TrackpadView pad;
    private KeyInputView keyInput;

    private int panel = PANEL_NONE, stickyMods;
    private boolean imeWasVisible, settingsOpen, connected;
    private final List<TextView> modifierChips = new ArrayList<>();
    private OnBackInvokedCallback backCallback;

    // ------------------------------------------------------------ lifecycle

    /** Applies the Light/Dark choice from settings before any resources are loaded. */
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        int theme = base.getSharedPreferences("settings", MODE_PRIVATE).getInt("theme", Settings.THEME_SYSTEM);
        if (theme == Settings.THEME_SYSTEM) return;
        Configuration c = new Configuration();
        c.fontScale = 0; // leave the user's font size alone; only override night mode
        int current = base.getResources().getConfiguration().uiMode;
        c.uiMode = (current & ~Configuration.UI_MODE_NIGHT_MASK)
                | (theme == Settings.THEME_DARK ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        applyOverrideConfiguration(c);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        settings = Settings.load(prefs);
        hid = HidManager.get(this);

        content = findViewById(R.id.content);
        connectPanel = findViewById(R.id.connectPanel);
        settingsOverlay = findViewById(R.id.settingsOverlay);
        settingsSheet = findViewById(R.id.settingsSheet);
        clickBar = findViewById(R.id.clickBar);
        statusDot = findViewById(R.id.statusDot);
        statusText = findViewById(R.id.statusText);
        connectStatus = findViewById(R.id.connectStatus);
        enableBtn = findViewById(R.id.enableBtn);
        firstTimeCard = findViewById(R.id.firstTimeCard);
        pairedCard = findViewById(R.id.pairedCard);
        pairHint = findViewById(R.id.pairHint);
        steps = findViewById(R.id.steps);
        pairedList = findViewById(R.id.pairedList);
        keyRow = findViewById(R.id.keyRow);
        keyRowContent = findViewById(R.id.keyRowContent);
        shortcutPanel = findViewById(R.id.shortcutPanel);
        mediaPanel = findViewById(R.id.mediaPanel);
        settingsContent = findViewById(R.id.settingsContent);
        btnRotate = findViewById(R.id.btnRotate);
        btnKeyboard = findViewById(R.id.btnKeyboard);
        btnShortcuts = findViewById(R.id.btnShortcuts);
        btnMedia = findViewById(R.id.btnMedia);
        btnSettings = findViewById(R.id.btnSettings);
        pad = findViewById(R.id.pad);
        keyInput = findViewById(R.id.keyInput);
        workArea = findViewById(R.id.workArea);
        bottomDock = findViewById(R.id.bottomDock);
        sideDock = findViewById(R.id.sideDock);
        macKeyboard = findViewById(R.id.macKeyboard);

        setupInsets();
        applyOrientation();
        pad.setup(hid, settings);
        ((SheetView) settingsSheet).setOnDismiss(() -> setSettingsOpen(false));
        btnRotate.setOnClickListener(v -> {
            settings.landscape = !settings.landscape;
            settings.save(prefs);
            applyOrientation();
            haptic(v);
        });
        KeyInputView.Listener typing = new KeyInputView.Listener() {
            @Override public void onChar(char c) {
                int k = Keys.forChar(c);
                if (k >= 0) hid.key((k >> 8) | takeSticky(), k & 0xFF);
            }

            @Override public void onKey(int code) { hid.key(takeSticky(), code); }
        };
        keyInput.setListener(typing);    // phone keyboard (portrait)
        macKeyboard.setListener(typing); // built-in keyboard (landscape)

        btnKeyboard.setOnClickListener(v -> showPanel(panel == PANEL_KEYS ? PANEL_NONE : PANEL_KEYS));
        btnShortcuts.setOnClickListener(v -> showPanel(panel == PANEL_SHORTCUTS ? PANEL_NONE : PANEL_SHORTCUTS));
        btnMedia.setOnClickListener(v -> showPanel(panel == PANEL_MEDIA ? PANEL_NONE : PANEL_MEDIA));
        btnSettings.setOnClickListener(v -> setSettingsOpen(true));
        findViewById(R.id.statusPill).setOnClickListener(v -> setSettingsOpen(true));
        settingsOverlay.setOnClickListener(v -> setSettingsOpen(false));
        findViewById(R.id.makeVisibleBtn).setOnClickListener(v -> makeVisible());
        enableBtn.setOnClickListener(v -> onEnableClicked());
        setupClickButton(findViewById(R.id.leftBtn), HidManager.BTN_LEFT);
        setupClickButton(findViewById(R.id.rightBtn), HidManager.BTN_RIGHT);

        buildKeyRow();
        buildShortcuts();
        buildMedia();
        buildSettings();
        dockPanels();
        layoutSettingsSheet();
        clickBar.setVisibility(settings.clickButtons ? View.VISIBLE : View.GONE);

        // Theme changes recreate the activity; keep the settings sheet where it was.
        if (b != null && b.getBoolean("settingsOpen")) {
            settingsOpen = true;
            settingsOverlay.setVisibility(View.VISIBLE);
        }
        List<String> ask = new ArrayList<>();
        for (String p : requiredPermissions())
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) ask.add(p);
        // Optional: lets the "Connected to …" notification show. Asked once only.
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("askedNotifications", false)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ask.add(Manifest.permission.POST_NOTIFICATIONS);
            prefs.edit().putBoolean("askedNotifications", true).apply();
        }
        if (!ask.isEmpty()) requestPermissions(ask.toArray(new String[0]), REQ_PERMISSIONS);
    }

    @Override protected void onStart() {
        super.onStart();
        hid.addListener(this);
        if (hasPermissions()) { // also covers permission granted later from system settings
            hid.start();
            hid.ensureRegistered(); // Android drops the registration if we were in the background
            hid.reconnectLast();
        }
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("settingsOpen", settingsOpen);
    }

    @Override protected void onStop() {
        super.onStop();
        hid.removeListener(this);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        // Leaving the app (not just rotating/backgrounding) releases the Mac.
        if (isFinishing()) hid.stop();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (hasPermissions()) hid.start();
        render();
    }

    @Override public void onHidStateChanged() { render(); }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        showPanel(PANEL_NONE);
        dockPanels();
        layoutSettingsSheet();
        applyOrientation();
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /** Width of the right-half cards used in landscape. */
    private int halfWidth() {
        return getResources().getDisplayMetrics().widthPixels / 2 - px(12);
    }

    /**
     * Portrait: panels sit under the pad. Landscape: they live in a card on the right half
     * that slides up from the bottom, so the left half of the pad stays usable.
     */
    private void dockPanels() {
        boolean land = isLandscape();
        LinearLayout target = land ? sideDock : bottomDock;
        View[] views = {keyRow, macKeyboard, shortcutPanel, mediaPanel};
        for (View v : views) ((ViewGroup) v.getParent()).removeView(v);
        for (View v : views) target.addView(v);
        sideDock.animate().cancel();
        sideDock.setVisibility(View.GONE);
        sideDock.setTranslationY(0);
        if (land) {
            ViewGroup.LayoutParams lp = sideDock.getLayoutParams();
            lp.width = halfWidth();
            sideDock.setLayoutParams(lp);
        }
    }

    private void slideDock(boolean show) {
        sideDock.animate().cancel();
        if (show) {
            if (sideDock.getVisibility() != View.VISIBLE) {
                sideDock.setVisibility(View.VISIBLE);
                sideDock.setTranslationY(workArea.getHeight());
            }
            sideDock.animate().translationY(0).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(2.2f)).start();
        } else {
            sideDock.animate().translationY(sideDock.getHeight() + px(24)).setDuration(200)
                    .withEndAction(() -> { if (panel == PANEL_NONE) sideDock.setVisibility(View.GONE); }).start();
        }
    }

    /** Full-width bottom sheet upright; a right-half sheet sideways. */
    private void layoutSettingsSheet() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) settingsSheet.getLayoutParams();
        if (isLandscape()) {
            lp.width = halfWidth();
            lp.gravity = Gravity.BOTTOM | Gravity.END;
            lp.topMargin = px(16);
            lp.setMarginEnd(px(12));
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.gravity = Gravity.BOTTOM;
            lp.topMargin = px(72);
            lp.setMarginEnd(0);
        }
        settingsSheet.setLayoutParams(lp);
    }

    /** Portrait, or landscape (either way up) with the system bars tucked away for a wider pad. */
    private void applyOrientation() {
        setRequestedOrientation(settings.landscape
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        btnRotate.setSelected(settings.landscape);
        if (Build.VERSION.SDK_INT < 30) return;
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null) return;
        if (settings.landscape) {
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            c.hide(WindowInsets.Type.systemBars());
        } else {
            c.show(WindowInsets.Type.systemBars());
        }
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= 31)
            return new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE};
        return new String[0];
    }

    private boolean hasPermissions() {
        for (String p : requiredPermissions())
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    // -------------------------------------------------------------- render

    private void render() {
        boolean perms = hasPermissions();
        HidManager.State st = perms ? hid.state() : null;
        connected = st == HidManager.State.CONNECTED;

        connectPanel.setVisibility(connected ? View.GONE : View.VISIBLE);
        for (View v : new View[]{btnKeyboard, btnShortcuts, btnMedia}) {
            v.setEnabled(connected);
            v.setAlpha(connected ? 1f : 0.35f);
        }
        if (!connected && panel != PANEL_NONE) showPanel(PANEL_NONE);
        clickBar.setVisibility(connected && settings.clickButtons ? View.VISIBLE : View.GONE);
        if (connected) HidService.ensureRunning(this); // keeps the Mac connected while switching apps
        if (connected) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int dot;
        String status;
        if (connected) { dot = R.color.green; status = hid.hostName(); }
        else if (st == HidManager.State.CONNECTING) { dot = R.color.amber; status = "Connecting…"; }
        else if (st == HidManager.State.READY) { dot = R.color.text3; status = "Not connected"; }
        else { dot = R.color.red; status = "Unavailable"; }
        statusDot.setBackgroundTintList(ColorStateList.valueOf(getColor(dot)));
        statusText.setText(status);

        boolean pairing = st == HidManager.State.READY || st == HidManager.State.CONNECTING;
        firstTimeCard.setVisibility(pairing ? View.VISIBLE : View.GONE);
        pairedCard.setVisibility(pairing ? View.VISIBLE : View.GONE);
        pairHint.setVisibility(pairing ? View.VISIBLE : View.GONE);
        enableBtn.setVisibility(View.GONE);

        if (st == null) {
            connectStatus.setText("Glide Pad needs Bluetooth permission to act as a trackpad and keyboard for your Mac.");
            showEnable("Allow Bluetooth");
        } else switch (st) {
            case NO_BLUETOOTH:
                connectStatus.setText("This device doesn't have Bluetooth.");
                break;
            case BLUETOOTH_OFF:
                connectStatus.setText("Bluetooth is turned off.");
                showEnable("Turn on Bluetooth");
                break;
            case UNSUPPORTED:
                connectStatus.setText("Couldn't start trackpad mode. Your phone may not support acting as a "
                        + "Bluetooth keyboard and mouse, or another app is already using it. Close that app and try again.");
                showEnable("Try again");
                break;
            case STARTING:
                connectStatus.setText("Starting Bluetooth trackpad…");
                break;
            case CONNECTING:
                connectStatus.setText("Connecting to " + hid.connectingName() + "…\nMake sure the Mac is awake and nearby.");
                break;
            case READY:
                connectStatus.setText("Pair from your Mac, or pick a computer you've paired before.");
                break;
            default:
                break;
        }
        if (pairing) {
            buildSteps();
            buildPairedList();
        }
        renderSettingsConnection(connected);
        updateBackHandling();
    }

    private void showEnable(String text) {
        enableBtn.setText(text);
        enableBtn.setVisibility(View.VISIBLE);
    }

    private void onEnableClicked() {
        if (!hasPermissions()) {
            requestPermissions(requiredPermissions(), REQ_PERMISSIONS);
        } else if (hid.state() == HidManager.State.BLUETOOTH_OFF) {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            hid.retry();
        }
    }

    private void makeVisible() {
        Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        startActivity(i);
    }

    private void buildSteps() {
        steps.removeAllViews();
        String phone = Html.escapeHtml(hid.phoneName());
        steps.addView(step(1, "Tap <b>Make phone visible</b> below."));
        steps.addView(step(2, "On the Mac, open <b>System Settings → Bluetooth</b>."));
        steps.addView(step(3, "Next to <b>" + phone + "</b>, click <b>Connect</b> and accept the code on both screens."));
    }

    private View step(int n, String html) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, px(14), 0, 0);
        TextView num = text(String.valueOf(n), 13, R.color.text);
        num.setGravity(Gravity.CENTER);
        num.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        num.setBackgroundResource(R.drawable.step_num);
        row.addView(num, new LinearLayout.LayoutParams(px(26), px(26)));
        TextView t = text("", 15, R.color.text2);
        t.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT));
        t.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = px(14);
        lp.topMargin = px(2);
        row.addView(t, lp);
        return row;
    }

    private void buildPairedList() {
        pairedList.removeAllViews();
        List<BluetoothDevice> devices = hid.pairedComputers();
        if (devices.isEmpty()) {
            TextView none = text("None yet. Pair your Mac using the steps above.", 14, R.color.text3);
            none.setPadding(0, px(12), 0, px(10));
            pairedList.addView(none);
            return;
        }
        BluetoothDevice connecting = hid.connectingDevice();
        for (BluetoothDevice d : devices) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(10), 0, px(10));
            TextView name = text(HidManager.nameOf(d), 16, R.color.text);
            name.setSingleLine(true);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            boolean busy = d.equals(connecting);
            TextView btn = smallButton(busy ? "Connecting…" : "Connect");
            btn.setEnabled(!busy);
            btn.setOnClickListener(v -> hid.connect(d));
            row.addView(btn);
            pairedList.addView(row);
        }
    }

    // -------------------------------------------------------------- panels

    private void showPanel(int p) {
        if (panel == PANEL_KEYS && p != PANEL_KEYS) {
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(keyInput.getWindowToken(), 0);
            keyInput.setActive(false);
            setSticky(0);
        }
        boolean land = isLandscape();
        int old = panel;
        panel = p;
        keyRow.setVisibility(p == PANEL_KEYS ? View.VISIBLE : View.GONE);
        macKeyboard.setVisibility(p == PANEL_KEYS && land ? View.VISIBLE : View.GONE);
        shortcutPanel.setVisibility(p == PANEL_SHORTCUTS ? View.VISIBLE : View.GONE);
        mediaPanel.setVisibility(p == PANEL_MEDIA ? View.VISIBLE : View.GONE);
        btnKeyboard.setSelected(p == PANEL_KEYS);
        btnShortcuts.setSelected(p == PANEL_SHORTCUTS);
        btnMedia.setSelected(p == PANEL_MEDIA);
        if (p == PANEL_KEYS && land) {
            macKeyboard.reset();
            macKeyboard.setHaptics(settings.haptics);
        } else if (p == PANEL_KEYS) {
            imeWasVisible = false;
            keyInput.setActive(true);
            keyInput.post(() -> getSystemService(InputMethodManager.class).showSoftInput(keyInput, 0));
        }
        if (land) {
            sideDock.setBackgroundTintList(ColorStateList.valueOf(getColor(p == PANEL_KEYS ? R.color.kb_bg : R.color.surface)));
            if (p != PANEL_NONE && (old == PANEL_NONE || sideDock.getVisibility() != View.VISIBLE)) slideDock(true);
            else if (p == PANEL_NONE && old != PANEL_NONE) slideDock(false);
        }
        updateBackHandling();
    }

    private void buildKeyRow() {
        addKeyChip("esc", 0, Keys.ESC);
        addKeyChip("tab", 0, Keys.TAB);
        addKeyChip("⌘", Keys.CMD, 0);
        addKeyChip("⌥", Keys.ALT, 0);
        addKeyChip("⌃", Keys.CTRL, 0);
        addKeyChip("⇧", Keys.SHIFT, 0);
        addKeyChip("←", 0, Keys.LEFT);
        addKeyChip("↑", 0, Keys.UP);
        addKeyChip("↓", 0, Keys.DOWN);
        addKeyChip("→", 0, Keys.RIGHT);
        addKeyChip("⌦", 0, Keys.DELETE);
    }

    /** modifier != 0 makes a sticky modifier toggle; otherwise it sends {@code code}. */
    private void addKeyChip(String label, int modifier, int code) {
        TextView chip = text(label, 17, R.color.text);
        chip.setGravity(Gravity.CENTER);
        chip.setBackgroundResource(R.drawable.chip);
        chip.setPadding(px(12), 0, px(12), 0);
        chip.setMinWidth(px(50));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(44));
        lp.rightMargin = px(6);
        keyRowContent.addView(chip, lp);
        if (modifier != 0) {
            chip.setTag(modifier);
            modifierChips.add(chip);
            chip.setOnClickListener(v -> { setSticky(stickyMods ^ modifier); haptic(v); });
        } else {
            chip.setOnClickListener(v -> { hid.key(takeSticky(), code); haptic(v); });
        }
    }

    /** Modifiers tapped in the key row apply to the next key only, like a phone's shift key. */
    private int takeSticky() {
        int m = stickyMods;
        if (m != 0) runOnUiThread(() -> setSticky(0));
        return m;
    }

    private void setSticky(int mods) {
        stickyMods = mods;
        for (TextView c : modifierChips) {
            boolean on = (mods & (Integer) c.getTag()) != 0;
            c.setSelected(on);
            c.setTextColor(getColor(on ? android.R.color.white : R.color.text));
        }
    }

    private void buildShortcuts() {
        Object[][] items = {
                {"Spotlight", "⌘ Space", Keys.CMD, Keys.SPACE},
                {"Mission Control", "⌃ ↑", Keys.CTRL, Keys.UP},
                {"Switch app", "⌘ Tab", Keys.CMD, Keys.TAB},
                {"Copy", "⌘ C", Keys.CMD, Keys.KEY_C},
                {"Paste", "⌘ V", Keys.CMD, Keys.KEY_V},
                {"Undo", "⌘ Z", Keys.CMD, Keys.KEY_Z},
                {"Desktop ←", "⌃ ←", Keys.CTRL, Keys.LEFT},
                {"Desktop →", "⌃ →", Keys.CTRL, Keys.RIGHT},
                {"App Exposé", "⌃ ↓", Keys.CTRL, Keys.DOWN},
                {"Screenshot", "⌘ ⇧ 4", Keys.CMD | Keys.SHIFT, Keys.KEY_4},
                {"Close window", "⌘ W", Keys.CMD, Keys.KEY_W},
                {"Lock screen", "⌃ ⌘ Q", Keys.CTRL | Keys.CMD, Keys.KEY_Q},
        };
        shortcutPanel.removeAllViews();
        int cols = 3;
        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rlp.topMargin = px(6);
                shortcutPanel.addView(row, rlp);
            }
            Object[] it = items[i];
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setBackgroundResource(R.drawable.chip);
            chip.setPadding(px(6), px(9), px(6), px(9));
            TextView title = text((String) it[0], 14, R.color.text);
            title.setSingleLine(true);
            title.setGravity(Gravity.CENTER);
            chip.addView(title);
            TextView keys = text((String) it[1], 12, R.color.text3);
            keys.setGravity(Gravity.CENTER);
            chip.addView(keys);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i % cols != 0) lp.leftMargin = px(6);
            row.addView(chip, lp);
            int mods = (Integer) it[2], code = (Integer) it[3];
            chip.setOnClickListener(v -> { hid.key(mods, code); haptic(v); });
        }
    }

    private void buildMedia() {
        int[][] items = {
                {R.drawable.ic_prev, Keys.PREV_TRACK}, {R.drawable.ic_play_pause, Keys.PLAY_PAUSE},
                {R.drawable.ic_next, Keys.NEXT_TRACK}, {R.drawable.ic_vol_down, Keys.VOLUME_DOWN},
                {R.drawable.ic_mute, Keys.MUTE}, {R.drawable.ic_vol_up, Keys.VOLUME_UP},
        };
        String[] names = {"Previous track", "Play or pause", "Next track", "Volume down", "Mute", "Volume up"};
        for (int i = 0; i < items.length; i++) {
            ImageButton b = new ImageButton(this);
            b.setImageResource(items[i][0]);
            b.setBackgroundResource(R.drawable.chip);
            b.setContentDescription(names[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(56), 1);
            lp.rightMargin = px(6);
            if (i == 3) lp.leftMargin = px(8);
            mediaPanel.addView(b, lp);
            int usage = items[i][1];
            b.setOnClickListener(v -> { hid.media(usage); haptic(v); });
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupClickButton(View v, int button) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    hid.press(button);
                    view.setPressed(true);
                    haptic(view);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    hid.release(button);
                    view.setPressed(false);
                    return true;
            }
            return true;
        });
    }

    // ------------------------------------------------------------ settings

    private void setSettingsOpen(boolean open) {
        if (open == settingsOpen) return;
        settingsOpen = open;
        settingsOverlay.animate().cancel();
        settingsSheet.animate().cancel();
        if (open) {
            if (panel == PANEL_KEYS || isLandscape()) showPanel(PANEL_NONE); // sideways, the sheet takes the panel's spot
            if (settingsOverlay.getVisibility() != View.VISIBLE) {
                settingsOverlay.setAlpha(0f);
                settingsSheet.setTranslationY(settingsSheet.getHeight() > 0 ? settingsSheet.getHeight() : px(160));
            }
            settingsOverlay.setVisibility(View.VISIBLE);
            settingsOverlay.animate().alpha(1f).setDuration(180).start();
            settingsSheet.animate().translationY(0).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        } else {
            // Slide the sheet away (continuing from wherever a drag left it) and fade the backdrop.
            settingsSheet.animate().translationY(settingsSheet.getHeight()).setDuration(200).start();
            settingsOverlay.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> { if (!settingsOpen) settingsOverlay.setVisibility(View.GONE); }).start();
        }
        updateBackHandling();
    }

    /** How many lines the Mac scrolls for each scroll step, like a mouse's "lines per notch". */
    private View linesPicker() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, px(8), 0, px(6));
        box.addView(text("Lines per scroll step", 16, R.color.text));
        TextView sub = text("How far the Mac scrolls for each step. Your Mac's own Mouse → Scrolling speed setting adds to this.",
                13, R.color.text3);
        sub.setPadding(0, px(2), 0, px(10));
        box.addView(sub);
        LinearLayout row = new LinearLayout(this);
        int[] options = {1, 2, 3, 5, 8};
        List<TextView> chips = new ArrayList<>();
        for (int i = 0; i < options.length; i++) {
            int lines = options[i];
            TextView t = text(String.valueOf(lines), 15, R.color.text);
            t.setGravity(Gravity.CENTER);
            t.setBackgroundResource(R.drawable.chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(42), 1);
            if (i > 0) lp.leftMargin = px(8);
            row.addView(t, lp);
            chips.add(t);
            t.setOnClickListener(v -> {
                settings.scrollLines = lines;
                settings.save(prefs);
                haptic(v);
                markSelected(chips, options, lines);
            });
        }
        markSelected(chips, options, settings.scrollLines);
        box.addView(row);
        return box;
    }

    private void markSelected(List<TextView> chips, int[] values, int selected) {
        for (int i = 0; i < chips.size(); i++) {
            boolean on = values[i] == selected;
            chips.get(i).setSelected(on);
            chips.get(i).setTextColor(getColor(on ? android.R.color.white : R.color.text));
        }
    }

    private View themePicker() {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, px(8), 0, px(4));
        String[] names = {"System", "Light", "Dark"};
        for (int i = 0; i < names.length; i++) {
            TextView t = text(names[i], 15, R.color.text);
            t.setGravity(Gravity.CENTER);
            t.setBackgroundResource(R.drawable.chip);
            boolean selected = settings.theme == i;
            t.setSelected(selected);
            if (selected) t.setTextColor(getColor(android.R.color.white));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(44), 1);
            if (i > 0) lp.leftMargin = px(8);
            row.addView(t, lp);
            int theme = i;
            t.setOnClickListener(v -> {
                if (settings.theme == theme) return;
                settings.theme = theme;
                settings.save(prefs);
                recreate();
            });
        }
        return row;
    }

    private void buildSettings() {
        View handle = new View(this);
        handle.setBackgroundResource(R.drawable.handle);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(px(40), px(5));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = px(14);
        settingsContent.addView(handle, hlp);

        TextView title = text("Settings", 22, R.color.text);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        settingsContent.addView(title);

        // Connection row
        LinearLayout conn = new LinearLayout(this);
        conn.setGravity(Gravity.CENTER_VERTICAL);
        conn.setPadding(0, px(14), 0, px(4));
        settingsConnLabel = text("", 15, R.color.text2);
        conn.addView(settingsConnLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        settingsConnBtn = smallButton("Disconnect");
        settingsConnBtn.setOnClickListener(v -> { hid.disconnect(); setSettingsOpen(false); });
        conn.addView(settingsConnBtn);
        settingsContent.addView(conn);

        settingsContent.addView(caption("APPEARANCE"));
        settingsContent.addView(themePicker());

        settingsContent.addView(caption("POINTER"));
        settingsContent.addView(slider("Pointer speed", settings.pointerSpeed, v -> settings.pointerSpeed = v));

        settingsContent.addView(caption("SCROLLING"));
        settingsContent.addView(slider("Scroll speed", settings.scrollSpeed, v -> settings.scrollSpeed = v));
        settingsContent.addView(linesPicker());
        settingsContent.addView(toggle("Reverse scroll direction", "Flip if scrolling feels backwards on your Mac.",
                settings.reverseScroll, v -> settings.reverseScroll = v));
        settingsContent.addView(toggle("Scroll momentum", "Keep scrolling briefly after a flick.",
                settings.momentum, v -> settings.momentum = v));

        settingsContent.addView(caption("BEHAVIOUR"));
        settingsContent.addView(toggle("Tap to click", "Tap the pad to click. Off: use the click buttons.",
                settings.tapToClick, v -> settings.tapToClick = v));
        settingsContent.addView(toggle("Click buttons", "Show left and right buttons under the pad.",
                settings.clickButtons, v -> {
                    settings.clickButtons = v;
                    render();
                }));
        settingsContent.addView(toggle("Haptic feedback", "Vibrate on clicks and gestures.",
                settings.haptics, v -> settings.haptics = v));

        settingsContent.addView(caption("GESTURES"));
        String[][] gestures = {
                {"Tap", "Click"},
                {"Two-finger tap", "Right click"},
                {"Press and hold, then move", "Drag"},
                {"Two-finger slide", "Scroll"},
                {"Pinch", "Zoom (⌘+ / ⌘−)"},
                {"Four-finger swipe up", "Mission Control"},
                {"Four-finger swipe down", "App Exposé"},
                {"Four-finger swipe ← →", "Switch desktop"},
                {"Four-finger spread / pinch", "Show Desktop"},
        };
        for (String[] g : gestures) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, px(7), 0, px(7));
            row.addView(text(g[0], 14, R.color.text2), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(text(g[1], 14, R.color.text));
            settingsContent.addView(row);
        }
        TextView note = text("", 13, R.color.text2);
        note.setText(Html.fromHtml("<b>Note:</b> 3 and 4-finger gestures may not work on every phone. "
                + "Many phones (OnePlus and others) use multi-finger swipes for their own features, like "
                + "screenshots and Mind Space, and Android doesn't let apps override that. "
                + "Glide Pad leaves 3 fingers to your phone. If 4-finger swipes don't reach your Mac, "
                + "use the ⌘ shortcuts panel instead.", Html.FROM_HTML_MODE_COMPACT));
        note.setLineSpacing(0, 1.2f);
        note.setBackgroundResource(R.drawable.note_card);
        note.setPadding(px(14), px(12), px(14), px(12));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = px(12);
        nlp.bottomMargin = px(6);
        settingsContent.addView(note, nlp);

        // Footer: links + credit (same as Chroma Flow)
        LinearLayout links = new LinearLayout(this);
        links.setGravity(Gravity.CENTER);
        links.setPadding(0, px(22), 0, 0);
        links.addView(link("Privacy policy", "https://neerajreddyapi.github.io/GlidePad/privacy-policy.html"));
        links.addView(text("  ·  ", 13, R.color.text3));
        links.addView(link("Source code", "https://github.com/Neerajreddyapi/GlidePad"));
        settingsContent.addView(links);
        TextView crafted = text("Crafted by Neeraj Reddy", 13, R.color.text3);
        crafted.setGravity(Gravity.CENTER);
        crafted.setLetterSpacing(0.04f);
        crafted.setPadding(0, px(6), 0, px(8));
        settingsContent.addView(crafted);
    }

    private TextView link(String label, String url) {
        TextView t = text(label, 13, R.color.text3);
        t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        t.setPadding(px(2), px(8), px(2), px(8));
        t.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
            } catch (android.content.ActivityNotFoundException ignored) { }
        });
        return t;
    }

    private void renderSettingsConnection(boolean connected) {
        if (settingsConnLabel == null) return;
        settingsConnLabel.setText(connected ? "Connected to " + hid.hostName() : "Not connected");
        settingsConnBtn.setVisibility(connected ? View.VISIBLE : View.GONE);
    }

    private View slider(String label, float value, FloatSetter setter) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, px(8), 0, px(4));
        LinearLayout head = new LinearLayout(this);
        head.addView(text(label, 16, R.color.text), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView val = text(String.format("%.1f×", value), 15, R.color.text2);
        head.addView(val);
        box.addView(head);
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(Settings.toProgress(value));
        bar.setPadding(px(4), px(10), px(4), px(10));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float v = Settings.fromProgress(p);
                setter.set(v);
                val.setText(String.format("%.1f×", v));
            }

            @Override public void onStartTrackingTouch(SeekBar s) { }

            @Override public void onStopTrackingTouch(SeekBar s) { settings.save(prefs); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = -px(16);
        lp.rightMargin = -px(16);
        box.addView(bar, lp);
        return box;
    }

    private View toggle(String label, String sub, boolean value, BoolSetter setter) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, px(10), 0, px(10));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 16, R.color.text));
        TextView s = text(sub, 13, R.color.text3);
        s.setPadding(0, px(2), px(12), 0);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(this);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((b, checked) -> {
            setter.set(checked);
            settings.save(prefs);
        });
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }

    private TextView caption(String s) {
        TextView t = text(s, 12, R.color.text3);
        t.setLetterSpacing(0.08f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(0, px(22), 0, px(4));
        return t;
    }

    // ---------------------------------------------------------- back, insets

    private boolean handleBack() {
        if (settingsOpen) { setSettingsOpen(false); return true; }
        if (panel != PANEL_NONE) { showPanel(PANEL_NONE); return true; }
        // While connected, Back just hides the app so the Mac stays connected.
        // Use Settings → Disconnect to let go of the Mac.
        if (connected) { moveTaskToBack(true); return true; }
        return false;
    }

    private void updateBackHandling() {
        if (Build.VERSION.SDK_INT < 33) return; // onBackPressed covers older versions
        boolean need = settingsOpen || panel != PANEL_NONE || connected;
        OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
        if (need && backCallback == null) {
            backCallback = this::handleBack;
            d.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        } else if (!need && backCallback != null) {
            d.unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (!handleBack()) super.onBackPressed();
    }

    private void setupInsets() {
        if (Build.VERSION.SDK_INT < 30) return; // older versions resize for the keyboard by themselves
        getWindow().setDecorFitsSystemWindows(false);
        findViewById(R.id.root).setOnApplyWindowInsetsListener((v, in) -> {
            Insets bars = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = in.getInsets(WindowInsets.Type.ime());
            content.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            settingsSheet.setPadding(bars.left, 0, bars.right, bars.bottom);
            boolean imeVisible = in.isVisible(WindowInsets.Type.ime());
            // Keyboard dismissed with the system back gesture: close the key row too.
            if (panel == PANEL_KEYS && imeWasVisible && !imeVisible) showPanel(PANEL_NONE);
            imeWasVisible = imeVisible;
            return WindowInsets.CONSUMED;
        });
    }

    // -------------------------------------------------------------- helpers

    private TextView text(String s, int sp, int colorRes) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(getColor(colorRes));
        return t;
    }

    private TextView smallButton(String label) {
        TextView b = text(label, 14, R.color.text);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setBackgroundResource(R.drawable.btn_secondary);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(16), px(9), px(16), px(9));
        return b;
    }

    private void haptic(View v) {
        if (settings.haptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private int px(float dps) { return Math.round(dps * dp); }
}
