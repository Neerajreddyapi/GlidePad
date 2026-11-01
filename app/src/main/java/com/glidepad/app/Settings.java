package com.glidepad.app;

import android.content.SharedPreferences;

final class Settings {
    private static final float MIN_SPEED = 0.3f, MAX_SPEED = 3f;

    static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2;

    int theme = THEME_SYSTEM;
    float pointerSpeed = 1f, scrollSpeed = 1f;
    boolean tapToClick = true, reverseScroll, momentum = true, clickButtons, haptics = true;
    boolean landscape, hideGestureTip;

    static Settings load(SharedPreferences p) {
        Settings s = new Settings();
        s.theme = p.getInt("theme", THEME_SYSTEM);
        s.pointerSpeed = p.getFloat("pointerSpeed", 1f);
        s.scrollSpeed = p.getFloat("scrollSpeed", 1f);
        s.tapToClick = p.getBoolean("tapToClick", true);
        s.reverseScroll = p.getBoolean("reverseScroll", false);
        s.momentum = p.getBoolean("momentum", true);
        s.clickButtons = p.getBoolean("clickButtons", false);
        s.haptics = p.getBoolean("haptics", true);
        s.landscape = p.getBoolean("landscape", false);
        s.hideGestureTip = p.getBoolean("hideGestureTip", false);
        return s;
    }

    void save(SharedPreferences p) {
        p.edit()
                .putInt("theme", theme)
                .putFloat("pointerSpeed", pointerSpeed)
                .putFloat("scrollSpeed", scrollSpeed)
                .putBoolean("tapToClick", tapToClick)
                .putBoolean("reverseScroll", reverseScroll)
                .putBoolean("momentum", momentum)
                .putBoolean("clickButtons", clickButtons)
                .putBoolean("haptics", haptics)
                .putBoolean("landscape", landscape)
                .putBoolean("hideGestureTip", hideGestureTip)
                .apply();
    }

    /** Sliders are logarithmic so 1× sits near the middle. */
    static int toProgress(float speed) {
        return Math.round((float) (Math.log(speed / MIN_SPEED) / Math.log(MAX_SPEED / MIN_SPEED) * 100));
    }

    static float fromProgress(int progress) {
        return (float) (MIN_SPEED * Math.pow(MAX_SPEED / MIN_SPEED, progress / 100.0));
    }
}
