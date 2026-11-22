package com.glidepad.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Asks Google Play whether a newer version is out, and installs it with Play's own update
 * screen. Builds that didn't come from Play (test APKs) get "unknown", and Update then opens
 * the app's Play Store page instead.
 */
final class Updates {

    static final String UNKNOWN = "unknown", NONE = "none", AVAILABLE = "available";
    private static final String PACKAGE = "com.neerajreddy.glidepad"; // the Play listing, also for test builds
    private static final int REQUEST = 4207;

    interface Listener { void onState(String state); }

    private final Activity activity;
    private final AppUpdateManager manager;
    private AppUpdateInfo info;
    private String state = UNKNOWN;

    Updates(Activity activity) {
        this.activity = activity;
        manager = AppUpdateManagerFactory.create(activity);
    }

    String state() { return state; }

    void check(Listener listener) {
        manager.getAppUpdateInfo()
                .addOnSuccessListener(i -> {
                    info = i;
                    int a = i.updateAvailability();
                    state = a == UpdateAvailability.UPDATE_AVAILABLE && i.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) ? AVAILABLE
                            : a == UpdateAvailability.UPDATE_NOT_AVAILABLE ? NONE : UNKNOWN;
                    listener.onState(state);
                })
                .addOnFailureListener(e -> {
                    state = UNKNOWN;
                    listener.onState(state);
                });
    }

    /** Play's full-screen update if one is ready, otherwise the Play Store page. */
    void start() {
        if (AVAILABLE.equals(state) && info != null) {
            try {
                manager.startUpdateFlowForResult(info, AppUpdateType.IMMEDIATE, activity, REQUEST);
                return;
            } catch (Exception ignored) {
                // Fall back to the store page.
            }
        }
        openStore();
    }

    /** An update the user started finishes even if the app was sent to the background. */
    void resume() {
        manager.getAppUpdateInfo().addOnSuccessListener(i -> {
            if (i.updateAvailability() != UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) return;
            try {
                manager.startUpdateFlowForResult(i, AppUpdateType.IMMEDIATE, activity, REQUEST);
            } catch (Exception ignored) {
                // Nothing to resume.
            }
        });
    }

    private void openStore() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PACKAGE)));
        } catch (ActivityNotFoundException e) {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + PACKAGE)));
        }
    }

    static String version(Activity activity) {
        try {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
