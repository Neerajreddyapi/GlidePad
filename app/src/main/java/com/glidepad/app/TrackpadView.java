package com.glidepad.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

/**
 * The touch surface. Gestures:
 *  1 finger move → pointer · tap → click · long-press then move → drag
 *  2 finger move → scroll (with momentum) · tap → right click · pinch → ⌘+ / ⌘−
 *  3 finger swipe ← → desktops, ↑ Mission Control, ↓ App Exposé · tap → middle click
 */
public class TrackpadView extends View {

    private static final int NONE = 0, POINT = 1, SCROLL = 2, PINCH = 3, SWIPE = 4, DRAG = 5;
    private static final long TAP_MS = 260, LONG_PRESS_MS = 450, LABEL_MS = 1100, RIPPLE_MS = 380;
    private static final float TAP_TRAVEL_DP = 10, SCROLL_START_DP = 6, PINCH_START_DP = 30,
            SWIPE_DP = 40, SPREAD_DP = 45, ZOOM_STEP = 1.28f, POINTER_BASE = 1.4f, WHEEL_PER_DP = 0.12f;

    private HidManager hid;
    private Settings settings;
    private Runnable gestureListener;
    private final float density, slopDp;

    private int mode = NONE, maxPointers;
    private long downTime, lastMoveTime, lastScrollTime;
    private float lastCx, lastCy, travel, gestureTravel, startSpan, stepSpan, swipeDx, swipeDy;
    private boolean swipeFired;
    private VelocityTracker velocity;
    private float flingVx, flingVy;
    private long momentumLast;

    // drawing
    private Bitmap surface;
    private final Paint touchFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint touchRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] tx = new float[10], ty = new float[10];
    private int touchCount;
    private String label;
    private long labelAt = -LABEL_MS, rippleAt = -RIPPLE_MS;
    private float rippleX, rippleY;
    private final RectF tmp = new RectF();
    private final int accent, dragAccent; // RGB only; alpha is added when drawing

    public TrackpadView(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        slopDp = android.view.ViewConfiguration.get(c).getScaledTouchSlop() / density;
        accent = c.getColor(R.color.accent) & 0xFFFFFF;
        dragAccent = c.getColor(R.color.green) & 0xFFFFFF;
        touchRing.setStyle(Paint.Style.STROKE);
        touchRing.setStrokeWidth(1.5f * density);
        labelBg.setColor(c.getColor(R.color.label_bg));
        labelText.setColor(c.getColor(R.color.label_text));
        labelText.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
        labelText.setTextAlign(Paint.Align.CENTER);
        labelText.setFakeBoldText(true);
        hintText.setColor(c.getColor(R.color.pad_hint));
        hintText.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
        hintText.setTextAlign(Paint.Align.CENTER);
    }

    void setup(HidManager hid, Settings settings) {
        this.hid = hid;
        this.settings = settings;
    }

    /** Called when the system takes over a multi-finger gesture before we could use it. */
    void setOnGestureStolen(Runnable r) { gestureListener = r; }

    // ---------------------------------------------------------------- input

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (hid == null) return false;
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopMomentum();
                mode = NONE;
                maxPointers = 1;
                downTime = lastMoveTime = e.getEventTime();
                travel = gestureTravel = 0;
                resetBaseline(e, -1);
                postDelayed(longPress, LONG_PRESS_MS);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                maxPointers = Math.max(maxPointers, e.getPointerCount());
                removeCallbacks(longPress);
                resetBaseline(e, -1);
                if (mode != DRAG) {
                    mode = NONE;
                    travel = swipeDx = swipeDy = 0;
                    swipeFired = false;
                    startSpan = stepSpan = span(e, -1);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                handleMove(e);
                break;

            case MotionEvent.ACTION_POINTER_UP:
                if (mode == SCROLL && e.getPointerCount() == 2) captureFling(e);
                resetBaseline(e, e.getActionIndex());
                break;

            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                finishGesture(e);
                recycleVelocity();
                break;

            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                if (mode == DRAG) hid.release(HidManager.BTN_LEFT);
                // The phone's own 3-finger gestures (screenshot, Mind Space…) cancel our touch.
                if (maxPointers >= 3 && !swipeFired && gestureListener != null) gestureListener.run();
                mode = NONE;
                recycleVelocity();
                break;
        }
        recordTouches(e);
        invalidate();
        return true;
    }

    private void handleMove(MotionEvent e) {
        int n = e.getPointerCount();
        float cx = centroid(e, -1, true), cy = centroid(e, -1, false);
        float dx = (cx - lastCx) / density, dy = (cy - lastCy) / density;
        lastCx = cx;
        lastCy = cy;
        long dt = Math.max(1, e.getEventTime() - lastMoveTime);
        lastMoveTime = e.getEventTime();
        float dist = (float) Math.hypot(dx, dy);
        travel += dist;
        gestureTravel += dist;

        if (mode == DRAG) { pointer(dx, dy, dist, dt); return; }

        if (maxPointers == 1) {
            if (mode == NONE && travel > slopDp) {
                mode = POINT;
                removeCallbacks(longPress);
            }
            if (mode == POINT) pointer(dx, dy, dist, dt);
            return;
        }
        if (n < maxPointers) return; // fingers lifting one by one: ignore the leftovers

        if (n == 2) {
            float sp = span(e, -1);
            if (mode == NONE) {
                float spanDelta = Math.abs(sp - startSpan) / density;
                if (spanDelta > PINCH_START_DP && spanDelta > travel) { mode = PINCH; stepSpan = sp; }
                else if (travel > SCROLL_START_DP) mode = SCROLL;
            }
            if (mode == SCROLL) {
                scroll(dx, dy);
                lastScrollTime = e.getEventTime();
            } else if (mode == PINCH) {
                if (sp > stepSpan * ZOOM_STEP) {
                    stepSpan = sp;
                    hid.key(Keys.CMD, Keys.EQUAL);
                    feedback("Zoom in", HapticFeedbackConstants.CLOCK_TICK);
                } else if (sp < stepSpan / ZOOM_STEP) {
                    stepSpan = sp;
                    hid.key(Keys.CMD, Keys.MINUS);
                    feedback("Zoom out", HapticFeedbackConstants.CLOCK_TICK);
                }
            }
            return;
        }

        // three or four fingers: Mac-style swipes; four-finger spread/pinch shows the desktop
        if (travel > slopDp) mode = SWIPE;
        swipeDx += dx;
        swipeDy += dy;
        if (swipeFired) return;
        if (n >= 4) {
            float spread = (span(e, -1) - startSpan) / density;
            if (Math.abs(spread) > SPREAD_DP && Math.abs(spread) > Math.hypot(swipeDx, swipeDy)) {
                swipeFired = true;
                mode = SWIPE;
                hid.key(0, Keys.F11); // macOS "Show Desktop" (pressing again brings the windows back)
                feedback(spread > 0 ? "Show Desktop" : "Show windows", HapticFeedbackConstants.CONTEXT_CLICK);
                return;
            }
        }
        if (Math.abs(swipeDx) < SWIPE_DP && Math.abs(swipeDy) < SWIPE_DP) return;
        swipeFired = true;
        if (Math.abs(swipeDx) > Math.abs(swipeDy)) {
            // Like a Mac trackpad: swiping left reveals the desktop on the right.
            if (swipeDx < 0) { hid.key(Keys.CTRL, Keys.RIGHT); feedback("Next desktop →", HapticFeedbackConstants.CONTEXT_CLICK); }
            else { hid.key(Keys.CTRL, Keys.LEFT); feedback("← Previous desktop", HapticFeedbackConstants.CONTEXT_CLICK); }
        } else if (swipeDy < 0) {
            hid.key(Keys.CTRL, Keys.UP);
            feedback("Mission Control", HapticFeedbackConstants.CONTEXT_CLICK);
        } else {
            hid.key(Keys.CTRL, Keys.DOWN);
            feedback("App Exposé", HapticFeedbackConstants.CONTEXT_CLICK);
        }
    }

    private void finishGesture(MotionEvent e) {
        long duration = e.getEventTime() - downTime;
        if (mode == DRAG) {
            hid.release(HidManager.BTN_LEFT);
        } else if (mode == SCROLL) {
            boolean stillMoving = e.getEventTime() - lastScrollTime < 80;
            if (settings.momentum && stillMoving && Math.hypot(flingVx, flingVy) > 0.35f) startMomentum();
        } else if (mode == NONE && duration < TAP_MS && gestureTravel < TAP_TRAVEL_DP) {
            rippleX = e.getX();
            rippleY = e.getY();
            if (maxPointers == 1) {
                if (!settings.tapToClick) return;
                hid.click(HidManager.BTN_LEFT);
                rippleAt = SystemClock.uptimeMillis();
                haptic(HapticFeedbackConstants.KEYBOARD_TAP);
            } else if (maxPointers == 2) {
                hid.click(HidManager.BTN_RIGHT);
                rippleAt = SystemClock.uptimeMillis();
                feedback("Right click", HapticFeedbackConstants.KEYBOARD_TAP);
            } else {
                hid.click(HidManager.BTN_MIDDLE);
                rippleAt = SystemClock.uptimeMillis();
                feedback("Middle click", HapticFeedbackConstants.KEYBOARD_TAP);
            }
        }
        mode = NONE;
    }

    private final Runnable longPress = () -> {
        if (mode != NONE || maxPointers != 1) return;
        mode = DRAG;
        hid.press(HidManager.BTN_LEFT);
        feedback("Drag", HapticFeedbackConstants.LONG_PRESS);
        invalidate();
    };

    private void pointer(float dx, float dy, float dist, long dt) {
        float speed = dist / dt; // dp per ms
        float gain = POINTER_BASE * settings.pointerSpeed * (1f + 0.8f * Math.min(speed, 2.5f));
        hid.move(dx * gain, dy * gain);
    }

    private void scroll(float dx, float dy) {
        // With macOS "natural scrolling" (the default) this matches a real trackpad.
        float k = WHEEL_PER_DP * settings.scrollSpeed * (settings.reverseScroll ? -1 : 1);
        hid.scroll(-dy * k, dx * k);
    }

    // ------------------------------------------------------------- momentum

    private void captureFling(MotionEvent e) {
        velocity.computeCurrentVelocity(1);
        float vx = 0, vy = 0;
        int n = e.getPointerCount();
        for (int i = 0; i < n; i++) {
            vx += velocity.getXVelocity(e.getPointerId(i));
            vy += velocity.getYVelocity(e.getPointerId(i));
        }
        flingVx = vx / n / density;
        flingVy = vy / n / density;
    }

    private void startMomentum() {
        momentumLast = SystemClock.uptimeMillis();
        postOnAnimation(momentum);
    }

    private void stopMomentum() {
        removeCallbacks(momentum);
        flingVx = flingVy = 0;
    }

    private final Runnable momentum = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            long dt = Math.min(32, now - momentumLast);
            momentumLast = now;
            float decay = (float) Math.pow(0.996, dt);
            flingVx *= decay;
            flingVy *= decay;
            if (Math.hypot(flingVx, flingVy) < 0.03f) return;
            scroll(flingVx * dt, flingVy * dt);
            postOnAnimation(this);
        }
    };

    // -------------------------------------------------------------- helpers

    private void resetBaseline(MotionEvent e, int skip) {
        lastCx = centroid(e, skip, true);
        lastCy = centroid(e, skip, false);
    }

    private static float centroid(MotionEvent e, int skip, boolean x) {
        float sum = 0;
        int count = 0;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skip) continue;
            sum += x ? e.getX(i) : e.getY(i);
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private static float span(MotionEvent e, int skip) {
        float cx = centroid(e, skip, true), cy = centroid(e, skip, false), sum = 0;
        int count = 0;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skip) continue;
            sum += (float) Math.hypot(e.getX(i) - cx, e.getY(i) - cy);
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private void feedback(String text, int hapticType) {
        label = text;
        labelAt = SystemClock.uptimeMillis();
        haptic(hapticType);
        invalidate();
    }

    private void haptic(int type) {
        if (settings.haptics) performHapticFeedback(type);
    }

    private void recordTouches(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { touchCount = 0; return; }
        int lifted = action == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1;
        touchCount = 0;
        for (int i = 0; i < e.getPointerCount() && touchCount < tx.length; i++) {
            if (i == lifted) continue;
            tx[touchCount] = e.getX(i);
            ty[touchCount] = e.getY(i);
            touchCount++;
        }
    }

    private void recycleVelocity() {
        if (velocity != null) { velocity.recycle(); velocity = null; }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopMomentum();
        removeCallbacks(longPress);
        recycleVelocity();
    }

    // -------------------------------------------------------------- drawing

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        // Keep edge swipes on the pad from triggering the system Back gesture
        // (Android honours up to 200dp of this per screen edge).
        if (android.os.Build.VERSION.SDK_INT >= 29)
            setSystemGestureExclusionRects(java.util.Collections.singletonList(new android.graphics.Rect(0, 0, w, h)));
        Context ctx = getContext();
        surface = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(surface);
        float r = 28 * density, inset = density / 2;
        RectF rect = new RectF(inset, inset, w - inset, h - inset);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, 0, 0, h, ctx.getColor(R.color.pad_top),
                ctx.getColor(R.color.pad_bottom), Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, fill);

        // Fine dot texture, like brushed glass.
        Path clip = new Path();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(ctx.getColor(R.color.pad_dot));
        float step = 22 * density;
        for (float y = step / 2; y < h; y += step)
            for (float x = step / 2; x < w; x += step) c.drawCircle(x, y, 1.1f * density, dot);
        c.restore();

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(density);
        border.setShader(new LinearGradient(0, 0, 0, h, ctx.getColor(R.color.pad_border_top),
                ctx.getColor(R.color.pad_border_bottom), Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, border);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (surface != null) canvas.drawBitmap(surface, 0, 0, null);
        long now = SystemClock.uptimeMillis();
        boolean animating = false;
        float w = getWidth(), h = getHeight();

        if (touchCount == 0) {
            canvas.drawText("Tap to click · two fingers to scroll", w / 2, h - 28 * density, hintText);
        }

        int rgb = mode == DRAG ? dragAccent : accent;
        touchFill.setColor(0x33000000 | rgb);
        touchRing.setColor(0xAA000000 | rgb);
        float tr = (mode == DRAG ? 30 : 26) * density;
        for (int i = 0; i < touchCount; i++) {
            canvas.drawCircle(tx[i], ty[i], tr, touchFill);
            canvas.drawCircle(tx[i], ty[i], tr, touchRing);
        }

        long rt = now - rippleAt;
        if (rt < RIPPLE_MS) {
            float t = rt / (float) RIPPLE_MS;
            touchRing.setColor(((int) ((1 - t) * 200) << 24) | accent);
            canvas.drawCircle(rippleX, rippleY, (16 + 34 * t) * density, touchRing);
            animating = true;
        }

        long lt = now - labelAt;
        if (label != null && lt < LABEL_MS) {
            float alpha = lt > LABEL_MS - 300 ? (LABEL_MS - lt) / 300f : 1f;
            float tw = labelText.measureText(label);
            float ph = 34 * density, top = 18 * density;
            tmp.set(w / 2 - tw / 2 - 16 * density, top, w / 2 + tw / 2 + 16 * density, top + ph);
            labelBg.setAlpha((int) (240 * alpha));
            labelText.setAlpha((int) (255 * alpha));
            canvas.drawRoundRect(tmp, ph / 2, ph / 2, labelBg);
            canvas.drawText(label, w / 2, top + ph / 2 - (labelText.descent() + labelText.ascent()) / 2, labelText);
            animating = true;
        }

        if (animating) postInvalidateOnAnimation();
    }
}
