package com.glidepad.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** A ring that empties as the "phone is visible" time runs out, with the time left in the middle. */
public class CountdownRing extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulse = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;
    private long endsAt, totalMs = 1;

    public CountdownRing(Context c) {
        super(c);
        density = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        int accent = c.getColor(R.color.accent);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(8 * density);
        track.setColor(c.getColor(R.color.surface2));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(8 * density);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(accent);
        pulse.setColor(accent);
        time.setColor(c.getColor(R.color.text));
        time.setTextSize(34 * sp);
        time.setTextAlign(Paint.Align.CENTER);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        caption.setColor(c.getColor(R.color.text3));
        caption.setTextSize(13 * sp);
        caption.setTextAlign(Paint.Align.CENTER);
    }

    void start(long endsAtUptime, long totalMs) {
        this.endsAt = endsAtUptime;
        this.totalMs = Math.max(1, totalMs);
        postInvalidateOnAnimation();
    }

    @Override protected void onMeasure(int w, int h) {
        int size = Math.round(168 * density);
        setMeasuredDimension(size, size);
    }

    @Override protected void onDraw(Canvas c) {
        long left = Math.max(0, endsAt - SystemClock.uptimeMillis());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - 14 * density;

        // A soft pulse behind the ring while the phone is discoverable.
        if (left > 0) {
            float t = (SystemClock.uptimeMillis() % 1600) / 1600f;
            pulse.setAlpha((int) (40 * (1 - t)));
            c.drawCircle(cx, cy, r * (0.55f + 0.45f * t), pulse);
        }

        box.set(cx - r, cy - r, cx + r, cy + r);
        c.drawOval(box, track);
        c.drawArc(box, -90, 360f * left / totalMs, false, arc);

        long secs = (left + 999) / 1000;
        String label = (secs / 60) + ":" + String.format("%02d", secs % 60);
        c.drawText(label, cx, cy + time.getTextSize() * 0.3f, time);
        c.drawText(left > 0 ? "visible" : "ended", cx, cy + time.getTextSize() * 0.3f + 20 * density, caption);

        if (left > 0) postInvalidateOnAnimation();
    }
}
