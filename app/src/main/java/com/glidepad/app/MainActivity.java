package com.glidepad.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
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
    private View keyRow;
    private ImageButton btnKeyboard, btnShortcuts, btnMedia, btnSettings;
    private TrackpadView pad;
    private KeyInputView keyInput;

    private int panel = PANEL_NONE, stickyMods;
    private boolean imeWasVisible;
    private final List<TextView> modifierChips = new ArrayList<>();
    private OnBackInvokedCallback backCallback;

    // ------------------------------------------------------------ lifecycle

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
        btnKeyboard = findViewById(R.id.btnKeyboard);
        btnShortcuts = findViewById(R.id.btnShortcuts);
        btnMedia = findViewById(R.id.btnMedia);
        btnSettings = findViewById(R.id.btnSettings);
        pad = findViewById(R.id.pad);
        keyInput = findViewById(R.id.keyInput);

        setupInsets();
        pad.setup(hid, settings);
        keyInput.setListener(new KeyInputView.Listener() {
            @Override public void onChar(char c) {
                int k = Keys.forChar(c);
                if (k >= 0) hid.key((k >> 8) | takeSticky(), k & 0xFF);
            }

            @Override public void onKey(int code) { hid.key(takeSticky(), code); }
        });

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
        clickBar.setVisibility(settings.clickButtons ? View.VISIBLE : View.GONE);

        if (hasPermissions()) hid.start();
        else requestPermissions(requiredPermissions(), REQ_PERMISSIONS);
    }

    @Override protected void onStart() {
        super.onStart();
        hid.addListener(this);
        render();
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
        boolean connected = st == HidManager.State.CONNECTED;

        connectPanel.setVisibility(connected ? View.GONE : View.VISIBLE);
        for (View v : new View[]{btnKeyboard, btnShortcuts, btnMedia}) {
            v.setEnabled(connected);
            v.setAlpha(connected ? 1f : 0.35f);
        }
        if (!connected && panel != PANEL_NONE) showPanel(PANEL_NONE);
        clickBar.setVisibility(connected && settings.clickButtons ? View.VISIBLE : View.GONE);
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
            setSticky(0);
        }
        panel = p;
        keyRow.setVisibility(p == PANEL_KEYS ? View.VISIBLE : View.GONE);
        shortcutPanel.setVisibility(p == PANEL_SHORTCUTS ? View.VISIBLE : View.GONE);
        mediaPanel.setVisibility(p == PANEL_MEDIA ? View.VISIBLE : View.GONE);
        btnKeyboard.setSelected(p == PANEL_KEYS);
        btnShortcuts.setSelected(p == PANEL_SHORTCUTS);
        btnMedia.setSelected(p == PANEL_MEDIA);
        if (p == PANEL_KEYS) {
            imeWasVisible = false;
            keyInput.requestFocus();
            keyInput.post(() -> getSystemService(InputMethodManager.class).showSoftInput(keyInput, 0));
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
        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            if (i % 3 == 0) {
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
            chip.addView(title);
            chip.addView(text((String) it[1], 12, R.color.text3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i % 3 != 0) lp.leftMargin = px(6);
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
        if (open == (settingsOverlay.getVisibility() == View.VISIBLE)) return;
        if (open) {
            if (panel == PANEL_KEYS) showPanel(PANEL_NONE);
            settingsOverlay.setVisibility(View.VISIBLE);
            settingsOverlay.setAlpha(0f);
            settingsOverlay.animate().alpha(1f).setDuration(160).start();
            settingsSheet.setTranslationY(px(80));
            settingsSheet.animate().translationY(0).setDuration(220).start();
        } else {
            settingsOverlay.animate().alpha(0f).setDuration(140)
                    .withEndAction(() -> settingsOverlay.setVisibility(View.GONE)).start();
        }
        updateBackHandling(open);
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

        settingsContent.addView(caption("POINTER"));
        settingsContent.addView(slider("Pointer speed", settings.pointerSpeed, v -> settings.pointerSpeed = v));
        settingsContent.addView(slider("Scroll speed", settings.scrollSpeed, v -> settings.scrollSpeed = v));

        settingsContent.addView(caption("BEHAVIOUR"));
        settingsContent.addView(toggle("Tap to click", "Tap the pad to click. Off: use the click buttons.",
                settings.tapToClick, v -> settings.tapToClick = v));
        settingsContent.addView(toggle("Reverse scroll direction", "Flip if scrolling feels backwards on your Mac.",
                settings.reverseScroll, v -> settings.reverseScroll = v));
        settingsContent.addView(toggle("Scroll momentum", "Keep scrolling briefly after a flick.",
                settings.momentum, v -> settings.momentum = v));
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
                {"Three-finger swipe up", "Mission Control"},
                {"Three-finger swipe down", "App Exposé"},
                {"Three-finger swipe ← →", "Switch desktop"},
                {"Three-finger tap", "Middle click"},
        };
        for (String[] g : gestures) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, px(7), 0, px(7));
            row.addView(text(g[0], 14, R.color.text2), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(text(g[1], 14, R.color.text));
            settingsContent.addView(row);
        }
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
        if (settingsOverlay.getVisibility() == View.VISIBLE) { setSettingsOpen(false); return true; }
        if (panel != PANEL_NONE) { showPanel(PANEL_NONE); return true; }
        return false;
    }

    private void updateBackHandling() { updateBackHandling(settingsOverlay.getVisibility() == View.VISIBLE); }

    private void updateBackHandling(boolean settingsOpen) {
        if (Build.VERSION.SDK_INT < 33) return; // onBackPressed covers older versions
        boolean need = settingsOpen || panel != PANEL_NONE;
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
