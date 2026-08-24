package com.example.chineseapp.Helpers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public class CircularBorderProgressView extends View {

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float progress = 0f;
    private float strokeWidth;

    public CircularBorderProgressView(Context context) {
        this(context, null);
    }

    public CircularBorderProgressView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircularBorderProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        strokeWidth = dp(3f);

        basePaint.setStyle(Paint.Style.STROKE);
        basePaint.setStrokeWidth(strokeWidth);
        basePaint.setColor(Color.parseColor("#66FFFFFF"));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Color.WHITE);
    }

    public void setProgress(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - progress) < 0.001f) return;
        progress = clamped;
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float halfStroke = strokeWidth / 2f;
        arcRect.set(halfStroke, halfStroke, getWidth() - halfStroke, getHeight() - halfStroke);

        canvas.drawOval(arcRect, basePaint);
        canvas.drawArc(arcRect, -90f, 360f * progress, false, progressPaint);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
