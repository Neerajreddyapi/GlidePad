package com.glidepad.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/**
 * A bottom-sheet ScrollView: when its content is scrolled to the top, pulling down
 * anywhere on it drags the whole sheet down, and letting go far or fast enough dismisses it.
 */
public class SheetView extends ScrollView {

    private final int slop;
    private final float density;
    private Runnable onDismiss;
    private float startY;
    private boolean dragging;
    private VelocityTracker velocity;

    public SheetView(Context c, AttributeSet a) {
        super(c, a);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        density = c.getResources().getDisplayMetrics().density;
    }

    void setOnDismiss(Runnable r) { onDismiss = r; }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            startY = e.getRawY();
            dragging = false;
            animate().cancel();
        }
        track(e);
        return super.dispatchTouchEvent(e);
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        // Steal the gesture from switches, sliders and rows once it is clearly a pull-down.
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE && shouldDrag(e)) return true;
        return super.onInterceptTouchEvent(e);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (!dragging && !shouldDrag(e)) break;
                setTranslationY(Math.max(0, e.getRawY() - startY));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) break;
                dragging = false;
                velocity.computeCurrentVelocity(1000);
                float v = velocity.getYVelocity(), t = getTranslationY();
                if (onDismiss != null && (t > getHeight() * 0.25f || (v > 900 * density && t > 0))) {
                    onDismiss.run();
                } else {
                    animate().translationY(0).setDuration(180).start();
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    /** True once the finger has moved down past the touch slop while the content is at the top. */
    private boolean shouldDrag(MotionEvent e) {
        if (dragging) return true;
        if (getScrollY() > 0) {
            startY = e.getRawY(); // measure the pull from the moment the content reaches the top
            return false;
        }
        if (e.getRawY() - startY <= slop) return false;
        dragging = true;
        startY = e.getRawY();
        // Tell ScrollView its own scroll gesture is over.
        MotionEvent cancel = MotionEvent.obtain(e);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.onTouchEvent(cancel);
        cancel.recycle();
        return true;
    }

    private void track(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (velocity != null) velocity.recycle();
            velocity = VelocityTracker.obtain();
        }
        if (velocity == null) return;
        // Raw coordinates: the view itself moves while dragging.
        MotionEvent raw = MotionEvent.obtain(e);
        raw.setLocation(e.getRawX(), e.getRawY());
        velocity.addMovement(raw);
        raw.recycle();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (velocity != null) { velocity.recycle(); velocity = null; }
    }
}
