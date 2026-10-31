package com.glidepad.app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Makes the phone a Bluetooth HID "combo" device: a mouse with wheel + horizontal pan,
 * a keyboard and media keys. macOS talks to it with its built-in drivers.
 */
@SuppressLint("MissingPermission")
public final class HidManager {

    public enum State { NO_BLUETOOTH, BLUETOOTH_OFF, STARTING, UNSUPPORTED, READY, CONNECTING, CONNECTED }

    public interface Listener { void onHidStateChanged(); }

    public static final int BTN_LEFT = 1, BTN_RIGHT = 2, BTN_MIDDLE = 4;

    private static final int ID_MOUSE = 1, ID_KEYBOARD = 2, ID_CONSUMER = 3;
    private static final long MIN_INTERVAL_MS = 8; // don't flood the radio; movement is coalesced

    private static final int[] DESCRIPTOR = {
            // ---- Mouse (report 1): 5 buttons, 16-bit X/Y, wheel, horizontal pan ----
            0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x85, ID_MOUSE,
            0x09, 0x01, 0xA1, 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x05, 0x15, 0x00, 0x25, 0x01, 0x95, 0x05, 0x75, 0x01, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x03, 0x81, 0x01,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x16, 0x01, 0x80, 0x26, 0xFF, 0x7F, 0x75, 0x10, 0x95, 0x02, 0x81, 0x06,
            0x09, 0x38, 0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x01, 0x81, 0x06,
            0x05, 0x0C, 0x0A, 0x38, 0x02, 0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x01, 0x81, 0x06,
            0xC0, 0xC0,
            // ---- Keyboard (report 2): modifiers, reserved, 6 keys; LED output ----
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, ID_KEYBOARD,
            0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
            0x95, 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02,
            0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
            0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
            0xC0,
            // ---- Consumer / media keys (report 3): one 16-bit usage ----
            0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, ID_CONSUMER,
            0x15, 0x00, 0x26, 0xFF, 0x03, 0x19, 0x00, 0x2A, 0xFF, 0x03, 0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
            0xC0
    };

    private static HidManager instance;

    public static synchronized HidManager get(Context c) {
        if (instance == null) instance = new HidManager(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final BluetoothAdapter adapter;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler io;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile BluetoothHidDevice hid;
    private volatile BluetoothDevice host, connecting;
    private volatile boolean registered, unsupported, proxyRequested, started;

    private HidManager(Context app) {
        this.app = app;
        BluetoothManager bm = app.getSystemService(BluetoothManager.class);
        adapter = bm == null ? null : bm.getAdapter();
        prefs = app.getSharedPreferences("hid", Context.MODE_PRIVATE);
        HandlerThread t = new HandlerThread("hid-io", Process.THREAD_PRIORITY_URGENT_DISPLAY);
        t.start();
        io = new Handler(t.getLooper());
    }

    // ------------------------------------------------------------------ state

    public State state() {
        if (adapter == null) return State.NO_BLUETOOTH;
        if (!adapter.isEnabled()) return State.BLUETOOTH_OFF;
        if (unsupported) return State.UNSUPPORTED;
        if (host != null) return State.CONNECTED;
        if (connecting != null) return State.CONNECTING;
        if (registered) return State.READY;
        return State.STARTING;
    }

    public String hostName() { return nameOf(host); }

    public String connectingName() { return nameOf(connecting); }

    public BluetoothDevice connectingDevice() { return connecting; }

    public String phoneName() {
        String n = adapter == null ? null : adapter.getName();
        return n == null ? "this phone" : n;
    }

    public static String nameOf(BluetoothDevice d) {
        if (d == null) return null;
        String n = d.getName();
        return n == null || n.isEmpty() ? d.getAddress() : n;
    }

    /** Paired devices that could be a computer, last-used first. */
    public List<BluetoothDevice> pairedComputers() {
        List<BluetoothDevice> out = new ArrayList<>();
        if (adapter == null || !adapter.isEnabled()) return out;
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null) return out;
        String last = prefs.getString("last", null);
        for (BluetoothDevice d : bonded) {
            int major = d.getBluetoothClass() == null ? 0x1F00 : d.getBluetoothClass().getMajorDeviceClass();
            boolean computerLike = major == android.bluetooth.BluetoothClass.Device.Major.COMPUTER
                    || major == android.bluetooth.BluetoothClass.Device.Major.MISC
                    || major == android.bluetooth.BluetoothClass.Device.Major.UNCATEGORIZED;
            if (!computerLike) continue;
            if (d.getAddress().equals(last)) out.add(0, d); else out.add(d);
        }
        return out;
    }

    public void addListener(Listener l) { listeners.addIfAbsent(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() { main.post(() -> { for (Listener l : listeners) l.onHidStateChanged(); }); }

    // -------------------------------------------------------------- lifecycle

    public void start() {
        if (started) return;
        started = true;
        IntentFilter f = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(btReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(btReceiver, f);
        openProxy();
    }

    public void stop() {
        if (!started) return;
        started = false;
        try { app.unregisterReceiver(btReceiver); } catch (IllegalArgumentException ignored) { }
        BluetoothHidDevice h = hid;
        if (h != null) {
            if (registered) h.unregisterApp();
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, h);
        }
        resetConnection();
        hid = null;
        proxyRequested = false;
    }

    /** After an "unsupported" error, e.g. once another keyboard/mouse app has been closed. */
    public void retry() {
        unsupported = false;
        if (hid != null) register(); else openProxy();
        changed();
    }

    public void connect(BluetoothDevice d) {
        BluetoothHidDevice h = hid;
        if (h == null || d == null) return;
        if (host != null && !host.equals(d)) h.disconnect(host);
        connecting = d;
        changed();
        if (!h.connect(d)) { connecting = null; changed(); }
    }

    public void disconnect() {
        BluetoothHidDevice h = hid;
        BluetoothDevice d = host;
        if (h != null && d != null) h.disconnect(d);
    }

    private void openProxy() {
        if (adapter == null || !adapter.isEnabled() || hid != null || proxyRequested) { changed(); return; }
        proxyRequested = true;
        if (!adapter.getProfileProxy(app, profileListener, BluetoothProfile.HID_DEVICE)) {
            proxyRequested = false;
            unsupported = true;
        }
        changed();
    }

    private void register() {
        BluetoothHidDevice h = hid;
        if (h == null) return;
        byte[] desc = new byte[DESCRIPTOR.length];
        for (int i = 0; i < desc.length; i++) desc[i] = (byte) DESCRIPTOR[i];
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "Glide Pad", "Trackpad and keyboard", "Glide Pad",
                BluetoothHidDevice.SUBCLASS1_COMBO, desc);
        BluetoothHidDeviceAppQosSettings qos = new BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT, 800, 9, 0, 11250,
                BluetoothHidDeviceAppQosSettings.MAX);
        // Fails when the phone lacks HID-device support or another app already holds it.
        if (!h.registerApp(sdp, null, qos, io::post, callback)) unsupported = true;
        changed();
    }

    private void resetConnection() {
        registered = false;
        host = null;
        connecting = null;
    }

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
            proxyRequested = false;
            if (!started) { adapter.closeProfileProxy(profile, proxy); return; }
            hid = (BluetoothHidDevice) proxy;
            register();
        }

        @Override public void onServiceDisconnected(int profile) {
            hid = null;
            resetConnection();
            changed();
        }
    };

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int s = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (s == BluetoothAdapter.STATE_ON) {
                openProxy();
            } else if (s == BluetoothAdapter.STATE_TURNING_OFF || s == BluetoothAdapter.STATE_OFF) {
                BluetoothHidDevice h = hid;
                if (h != null) adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, h);
                hid = null;
                proxyRequested = false;
                resetConnection();
                changed();
            }
        }
    };

    private final BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice plugged, boolean isRegistered) {
            registered = isRegistered;
            if (!isRegistered) {
                host = null;
                connecting = null;
            } else if (host == null) {
                // Reconnect to the Mac we used last time (or the one the stack says is plugged).
                BluetoothDevice target = plugged != null ? plugged : lastHost();
                BluetoothHidDevice h = hid;
                if (target != null && h != null && h.connect(target)) connecting = target;
            }
            changed();
        }

        @Override public void onConnectionStateChanged(BluetoothDevice d, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                host = d;
                connecting = null;
                prefs.edit().putString("last", d.getAddress()).apply();
                synchronized (lock) { accX = accY = accWheel = accPan = 0; buttons = 0; }
            } else if (state == BluetoothProfile.STATE_CONNECTING) {
                connecting = d;
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (d.equals(host)) host = null;
                if (d.equals(connecting)) connecting = null;
            }
            changed();
        }

        @Override public void onGetReport(BluetoothDevice d, byte type, byte id, int bufferSize) {
            BluetoothHidDevice h = hid;
            if (h == null) return;
            int size = id == ID_MOUSE ? 7 : id == ID_KEYBOARD ? 8 : id == ID_CONSUMER ? 2 : 0;
            if (size == 0) h.reportError(d, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);
            else h.replyReport(d, type, id, new byte[size]);
        }

        @Override public void onSetReport(BluetoothDevice d, byte type, byte id, byte[] data) {
            BluetoothHidDevice h = hid;
            if (h != null) h.reportError(d, BluetoothHidDevice.ERROR_RSP_SUCCESS);
        }

        @Override public void onVirtualCableUnplug(BluetoothDevice d) {
            if (d.equals(host)) host = null;
            changed();
        }
    };

    private BluetoothDevice lastHost() {
        String last = prefs.getString("last", null);
        if (last == null || adapter == null) return null;
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null) return null;
        for (BluetoothDevice d : bonded) if (d.getAddress().equals(last)) return d;
        return null;
    }

    // ---------------------------------------------------------------- reports

    private final Object lock = new Object();
    private float accX, accY, accWheel, accPan;
    private int buttons, sentButtons;
    private boolean flushPosted;
    private volatile long lastSend;
    private final Runnable flushTask = () -> {
        synchronized (lock) { flushPosted = false; }
        flushMouse();
    };

    /** Relative pointer movement in mouse counts (fractions are carried over). */
    public void move(float dx, float dy) {
        if (host == null) return;
        synchronized (lock) { accX += dx; accY += dy; }
        scheduleFlush();
    }

    /** Wheel units; positive vertical scrolls up, positive horizontal pans right. */
    public void scroll(float vertical, float horizontal) {
        if (host == null) return;
        synchronized (lock) { accWheel += vertical; accPan += horizontal; }
        scheduleFlush();
    }

    public void press(int button) {
        io.post(() -> { synchronized (lock) { buttons |= button; } flushMouse(); });
    }

    public void release(int button) {
        io.post(() -> { synchronized (lock) { buttons &= ~button; } flushMouse(); });
    }

    public void click(int button) {
        press(button);
        io.postDelayed(() -> { synchronized (lock) { buttons &= ~button; } flushMouse(); }, 15);
    }

    /** Presses and releases one key, with modifiers held around it (see {@link Keys}). */
    public void key(int mods, int code) {
        io.post(() -> {
            if (mods != 0) sendKeys(mods, 0);
            sendKeys(mods, code);
            if (mods != 0) sendKeys(mods, 0);
            sendKeys(0, 0);
        });
    }

    /** Consumer-page usage, e.g. {@link Keys#VOLUME_UP}. */
    public void media(int usage) {
        io.post(() -> {
            send(ID_CONSUMER, new byte[]{(byte) usage, (byte) (usage >> 8)});
            send(ID_CONSUMER, new byte[]{0, 0});
        });
    }

    private void scheduleFlush() {
        synchronized (lock) {
            if (flushPosted) return;
            flushPosted = true;
        }
        long wait = Math.max(0, lastSend + MIN_INTERVAL_MS - SystemClock.uptimeMillis());
        io.postDelayed(flushTask, wait);
    }

    private void flushMouse() {
        int dx, dy, w, p, b;
        boolean more;
        synchronized (lock) {
            dx = clamp((int) accX, 32767); accX -= dx;
            dy = clamp((int) accY, 32767); accY -= dy;
            w = clamp((int) accWheel, 127); accWheel -= w;
            p = clamp((int) accPan, 127); accPan -= p;
            b = buttons;
            more = Math.abs(accX) >= 1 || Math.abs(accY) >= 1 || Math.abs(accWheel) >= 1 || Math.abs(accPan) >= 1;
        }
        if (dx == 0 && dy == 0 && w == 0 && p == 0 && b == sentButtons) return;
        send(ID_MOUSE, new byte[]{(byte) b, (byte) dx, (byte) (dx >> 8), (byte) dy, (byte) (dy >> 8), (byte) w, (byte) p});
        sentButtons = b;
        if (more) scheduleFlush();
    }

    private void sendKeys(int mods, int code) {
        send(ID_KEYBOARD, new byte[]{(byte) mods, 0, (byte) code, 0, 0, 0, 0, 0});
    }

    private void send(int id, byte[] data) {
        BluetoothHidDevice h = hid;
        BluetoothDevice d = host;
        if (h == null || d == null) return;
        h.sendReport(d, id, data);
        lastSend = SystemClock.uptimeMillis();
    }

    private static int clamp(int v, int max) { return Math.max(-max, Math.min(max, v)); }
}
