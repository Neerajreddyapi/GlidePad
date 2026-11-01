package com.glidepad.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps the Mac connected while Glide Pad is in the background.
 *
 * Android's Bluetooth HID service unregisters a keyboard/mouse app as soon as it is no longer
 * visible, unless it runs a foreground service. So while connected we show an ongoing
 * notification ("Connected to …") with a Disconnect button.
 */
public class HidService extends Service implements HidManager.Listener {

    private static final String CHANNEL = "connection";
    private static final String ACTION_DISCONNECT = "com.glidepad.app.DISCONNECT";
    private static final int ID = 1;
    private static boolean running;

    private HidManager hid;
    private String shownFor;

    /** Called from the activity (in the foreground) whenever the Mac is connected. */
    static void ensureRunning(Context c) {
        if (running) return;
        running = true;
        c.startForegroundService(new Intent(c, HidService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        hid = HidManager.get(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Mac connection", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Shown while your phone is connected to a Mac as a trackpad");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        // startForeground must be called promptly after startForegroundService, even if we then stop.
        String name = hid.hostName();
        startInForeground(name);
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            hid.disconnect();
            stop();
            return START_NOT_STICKY;
        }
        if (name == null) { stop(); return START_NOT_STICKY; }
        hid.addListener(this);
        return START_NOT_STICKY;
    }

    @Override public void onHidStateChanged() {
        String name = hid.hostName();
        if (name == null) stop();
        else if (!name.equals(shownFor)) startInForeground(name);
    }

    private void startInForeground(String name) {
        shownFor = name;
        Notification n = build(name == null ? "Mac" : name);
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(ID, n);
    }

    private Notification build(String name) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE);
        PendingIntent disconnect = PendingIntent.getService(this, 1,
                new Intent(this, HidService.class).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_pad)
                .setContentTitle("Connected to " + name)
                .setContentText("Tap to open the trackpad")
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "Disconnect", disconnect).build())
                .build();
    }

    private void stop() {
        hid.removeListener(this);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        running = false;
    }

    @Override public void onDestroy() {
        hid.removeListener(this);
        running = false;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
