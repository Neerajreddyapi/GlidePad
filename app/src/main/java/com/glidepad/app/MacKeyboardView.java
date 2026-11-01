package com.glidepad.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact on-screen keyboard used in landscape, so it fits beside the trackpad
 * (Android's own keyboard always spans the whole screen width).
 */
public class MacKeyboardView extends View {

    private static final int SHIFT = -1, BACK = -2, ENTER = -3, TO_NUMBERS = -4, TO_LETTERS = -5, TO_SYMBOLS = -6;
    private static final int LETTERS = 0, NUMBERS = 1, SYMBOLS = 2;
    private static final float ROW_DP = 46, GAP_DP = 5, PAD_DP = 6;

    private static final class Key {
        final int code;
        final String label;
        final float weight;
        final RectF rect = new RectF();

        Key(int code, String label, float weight) {
            this.code = code;
            this.label = label;
            this.weight = weight;
        }

        boolean special() { return code < 0; }
    }

    private final float density;
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF shadow = new RectF();
    private final int keyColor, specialColor, pressedColor, shadowColor;

    private final List<List<Key>> rows = new ArrayList<>();
    private final SparseArray<Key> pressed = new SparseArray<>();
    private int layer = LETTERS;
    private boolean shift, caps, haptics = true;
    private long lastShiftTap;
    private KeyInputView.Listener listener;

    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() {
            if (listener != null) listener.onKey(Keys.BACKSPACE);
            postDelayed(this, 55);
        }
    };

    public MacKeyboardView(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        keyColor = c.getColor(R.color.key);
        specialColor = c.getColor(R.color.key_special);
        pressedColor = c.getColor(R.color.key_pressed);
        shadowColor = c.getColor(R.color.key_shadow);
        int text = c.getColor(R.color.text);
        textPaint.setColor(text);
        textPaint.setTextSize(21 * sp);
        textPaint.setTextAlign(Paint.Align.CENTER);
        smallText.setColor(text);
        smallText.setTextSize(15 * sp);
        smallText.setTextAlign(Paint.Align.CENTER);
        smallText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        iconPaint.setColor(text);
        iconPaint.setStrokeWidth(1.7f * density);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        buildRows();
    }

    void setListener(KeyInputView.Listener l) { listener = l; }

    void setHaptics(boolean on) { haptics = on; }

    /** Back to lowercase letters each time the keyboard is opened. */
    void reset() {
        layer = LETTERS;
        shift = caps = false;
        buildRows();
    }

    // ---------------------------------------------------------------- layout

    private void buildRows() {
        rows.clear();
        if (layer == LETTERS) {
            rows.add(chars("qwertyuiop", 1));
            rows.add(chars("asdfghjkl", 1));
            List<Key> r3 = new ArrayList<>();
            r3.add(new Key(SHIFT, "", 1.5f));
            r3.addAll(chars("zxcvbnm", 1));
            r3.add(new Key(BACK, "", 1.5f));
            rows.add(r3);
            rows.add(bottomRow("123", TO_NUMBERS));
        } else {
            boolean numbers = layer == NUMBERS;
            rows.add(chars(numbers ? "1234567890" : "[]{}#%^*+=", 1));
            rows.add(chars(numbers ? "-/:;()$&@\"" : "_\\|~<>`@$&", 1));
            List<Key> r3 = new ArrayList<>();
            r3.add(new Key(numbers ? TO_SYMBOLS : TO_NUMBERS, numbers ? "#+=" : "123", 1.5f));
            r3.addAll(chars(".,?!'", 1.4f));
            r3.add(new Key(BACK, "", 1.5f));
            rows.add(r3);
            rows.add(bottomRow("ABC", TO_LETTERS));
        }
        layoutKeys();
        invalidate();
    }

    private static List<Key> chars(String s, float weight) {
        List<Key> out = new ArrayList<>();
        for (char c : s.toCharArray()) out.add(new Key(c, String.valueOf(c), weight));
        return out;
    }

    private static List<Key> bottomRow(String modeLabel, int modeCode) {
        List<Key> r = new ArrayList<>();
        r.add(new Key(modeCode, modeLabel, 1.5f));
        r.add(new Key(',', ",", 1));
        r.add(new Key(' ', "space", 5));
        r.add(new Key('.', ".", 1));
        r.add(new Key(ENTER, "", 1.5f));
        return r;
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int h = Math.round((ROW_DP * 4 + PAD_DP * 2) * density);
        setMeasuredDimension(MeasureSpec.getSize(wSpec), h);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { layoutKeys(); }

    private void layoutKeys() {
        int w = getWidth();
        if (w == 0) return;
        float pad = PAD_DP * density, gap = GAP_DP * density, rowH = ROW_DP * density;
        float unit = (w - pad * 2) / 10f;
        for (int r = 0; r < rows.size(); r++) {
            List<Key> row = rows.get(r);
            float total = 0;
            for (Key k : row) total += k.weight * unit;
            float x = (w - total) / 2, y = pad + r * rowH;
            for (Key k : row) {
                float kw = k.weight * unit;
                k.rect.set(x + gap / 2, y + gap / 2, x + kw - gap / 2, y + rowH - gap / 2);
                x += kw;
            }
        }
    }

    // ---------------------------------------------------------------- input

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int i = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                Key k = keyAt(e.getX(i), e.getY(i));
                if (k == null) break;
                pressed.put(e.getPointerId(i), k);
                if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                onKeyDown(k);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                Key k = pressed.get(e.getPointerId(i));
                pressed.remove(e.getPointerId(i));
                if (k != null) onKeyUp(k);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed.clear();
                removeCallbacks(repeatDelete);
                break;
        }
        invalidate();
        return true;
    }

    private void onKeyDown(Key k) {
        switch (k.code) {
            case SHIFT:
                long now = SystemClock.uptimeMillis();
                if (caps) { caps = false; shift = false; }
                else if (shift && now - lastShiftTap < 350) caps = true; // double-tap = caps lock
                else shift = !shift;
                lastShiftTap = now;
                break;
            case TO_NUMBERS: layer = NUMBERS; buildRows(); break;
            case TO_SYMBOLS: layer = SYMBOLS; buildRows(); break;
            case TO_LETTERS: layer = LETTERS; buildRows(); break;
            case BACK:
                if (listener != null) listener.onKey(Keys.BACKSPACE);
                postDelayed(repeatDelete, 420);
                break;
        }
    }

    /** Characters are sent when the finger lifts, like phone keyboards. */
    private void onKeyUp(Key k) {
        if (k.code == BACK) { removeCallbacks(repeatDelete); return; }
        if (listener == null) return;
        if (k.code == ENTER) { listener.onKey(Keys.ENTER); return; }
        if (k.special()) return;
        char c = (char) k.code;
        if (layer == LETTERS && (shift || caps)) c = Character.toUpperCase(c);
        listener.onChar(c);
        if (shift && !caps) shift = false;
    }

    private Key keyAt(float x, float y) {
        Key best = null;
        float bestDist = Float.MAX_VALUE;
        for (List<Key> row : rows)
            for (Key k : row) {
                // Nearest key, so the gaps between keys still register.
                float dx = Math.max(0, Math.max(k.rect.left - x, x - k.rect.right));
                float dy = Math.max(0, Math.max(k.rect.top - y, y - k.rect.bottom));
                float d = dx * dx + dy * dy;
                if (d < bestDist) { bestDist = d; best = k; }
            }
        return bestDist < (12 * density) * (12 * density) ? best : null;
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(repeatDelete);
    }

    // ---------------------------------------------------------------- drawing

    @Override protected void onDraw(Canvas canvas) {
        float r = 7 * density, off = 1.2f * density;
        for (List<Key> row : rows)
            for (Key k : row) {
                boolean down = pressed.indexOfValue(k) >= 0;
                boolean lit = k.code == SHIFT && (shift || caps);
                shadow.set(k.rect.left, k.rect.top + off, k.rect.right, k.rect.bottom + off);
                keyPaint.setColor(shadowColor);
                canvas.drawRoundRect(shadow, r, r, keyPaint);
                keyPaint.setColor(down ? pressedColor : lit ? keyColor : k.special() ? specialColor : keyColor);
                canvas.drawRoundRect(k.rect, r, r, keyPaint);
                drawLabel(canvas, k);
            }
    }

    private void drawLabel(Canvas c, Key k) {
        float cx = k.rect.centerX(), cy = k.rect.centerY(), d = density;
        switch (k.code) {
            case SHIFT: {
                path.reset();
                path.moveTo(cx, cy - 8 * d);
                path.lineTo(cx + 8 * d, cy + 1 * d);
                path.lineTo(cx + 4 * d, cy + 1 * d);
                path.lineTo(cx + 4 * d, cy + 7 * d);
                path.lineTo(cx - 4 * d, cy + 7 * d);
                path.lineTo(cx - 4 * d, cy + 1 * d);
                path.lineTo(cx - 8 * d, cy + 1 * d);
                path.close();
                iconPaint.setStyle(shift || caps ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
                c.drawPath(path, iconPaint);
                if (caps) {
                    iconPaint.setStyle(Paint.Style.STROKE);
                    c.drawLine(cx - 4 * d, cy + 10 * d, cx + 4 * d, cy + 10 * d, iconPaint);
                }
                return;
            }
            case BACK: {
                path.reset();
                path.moveTo(cx - 10 * d, cy);
                path.lineTo(cx - 5 * d, cy - 7 * d);
                path.lineTo(cx + 10 * d, cy - 7 * d);
                path.lineTo(cx + 10 * d, cy + 7 * d);
                path.lineTo(cx - 5 * d, cy + 7 * d);
                path.close();
                iconPaint.setStyle(Paint.Style.STROKE);
                c.drawPath(path, iconPaint);
                c.drawLine(cx - 0.5f * d, cy - 3 * d, cx + 5.5f * d, cy + 3 * d, iconPaint);
                c.drawLine(cx + 5.5f * d, cy - 3 * d, cx - 0.5f * d, cy + 3 * d, iconPaint);
                return;
            }
            case ENTER: {
                iconPaint.setStyle(Paint.Style.STROKE);
                path.reset();
                path.moveTo(cx + 8 * d, cy - 7 * d);
                path.lineTo(cx + 8 * d, cy + 2 * d);
                path.lineTo(cx - 8 * d, cy + 2 * d);
                path.moveTo(cx - 4 * d, cy - 2 * d);
                path.lineTo(cx - 8 * d, cy + 2 * d);
                path.lineTo(cx - 4 * d, cy + 6 * d);
                c.drawPath(path, iconPaint);
                return;
            }
        }
        Paint p = k.special() || k.code == ' ' ? smallText : textPaint;
        String label = k.label;
        if (layer == LETTERS && label.length() == 1 && (shift || caps)) label = label.toUpperCase();
        c.drawText(label, cx, cy - (p.descent() + p.ascent()) / 2, p);
    }
}
