package com.example.chineseapp.Helpers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawingCanvas extends View {

    private final List<Path> userPaths = new ArrayList<>();
    private Path             currentPath = new Path();

    /** The strokes the user is expected to reproduce in the current stage. */
    private List<Path> targetStrokes = new ArrayList<>();

    private Paint  userPaint, gridPaint;
    private Matrix matrix, inverseMatrix;
    private static final float SOURCE_SIZE = 1000f;

    public DrawingCanvas(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        userPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        userPaint.setColor(Color.BLACK);
        userPaint.setStyle(Paint.Style.STROKE);
        userPaint.setStrokeWidth(20f);
        userPaint.setStrokeCap(Paint.Cap.ROUND);
        userPaint.setStrokeJoin(Paint.Join.ROUND);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0x22000000);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Set the exact strokes the user should draw this stage.
     * Replaces the old setReferenceStrokes(all, hintMode) approach — the
     * caller is responsible for passing only the relevant subset.
     */
    public void setTargetStrokes(List<Path> strokes) {
        this.targetStrokes = new ArrayList<>(strokes);
    }

    public void clearDrawing() {
        userPaths.clear();
        currentPath.reset();
        invalidate();
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        buildMatrix(w, h);
    }

    private void buildMatrix(int w, int h) {
        if (w == 0 || h == 0) return;
        float scale = Math.min(w, h) / SOURCE_SIZE * 0.85f;
        float dx = (w - SOURCE_SIZE * scale) / 2f;
        float dy = (h - SOURCE_SIZE * scale) / 2f;
        matrix = new Matrix();
        matrix.setScale(scale, -scale);
        matrix.postTranslate(dx, h - dy);
        inverseMatrix = new Matrix();
        matrix.invert(inverseMatrix);
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (inverseMatrix == null) return true;
        float[] pt = {event.getX(), event.getY()};
        inverseMatrix.mapPoints(pt);
        float x = pt[0], y = pt[1];
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPath = new Path();
                currentPath.moveTo(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                currentPath.lineTo(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                currentPath.lineTo(x, y);
                userPaths.add(currentPath);
                currentPath = new Path();
                invalidate();
                break;
        }
        return true;
    }

    // ── Drawing ───────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        // Guide cross-hair
        canvas.drawLine(w / 2f, 0,       w / 2f, h,       gridPaint);
        canvas.drawLine(0,      h / 2f,  w,      h / 2f,  gridPaint);

        if (matrix == null) return;
        for (Path p : userPaths) {
            Path t = new Path();
            p.transform(matrix, t);
            canvas.drawPath(t, userPaint);
        }
        if (!currentPath.isEmpty()) {
            Path t = new Path();
            currentPath.transform(matrix, t);
            canvas.drawPath(t, userPaint);
        }
    }

    // ── Scoring ───────────────────────────────────────────────────────────────
    /**
     * Compare the user's drawn paths against {@link #targetStrokes}.
     * Returns 0–100.
     */
    public int scoreDrawing() {
        if (targetStrokes == null || targetStrokes.isEmpty()) return 50;
        if (userPaths.isEmpty()) return 0;

        float tolerance = SOURCE_SIZE * 0.08f;
        List<float[]> userPoints = samplePaths(userPaths, 8);

        int totalSamples = 0, hits = 0;
        for (Path ref : targetStrokes) {
            List<float[]> refPoints = samplePath(ref, 20);
            for (float[] rp : refPoints) {
                totalSamples++;
                for (float[] up : userPoints) {
                    float dx = rp[0] - up[0];
                    float dy = rp[1] - up[1];
                    if (dx * dx + dy * dy < tolerance * tolerance) {
                        hits++;
                        break;
                    }
                }
            }
        }
        if (totalSamples == 0) return 50;
        return Math.min(100, (int) (100f * hits / totalSamples));
    }

    // ── Path sampling helpers ─────────────────────────────────────────────────
    private List<float[]> samplePaths(List<Path> paths, int samplesPerPath) {
        List<float[]> result = new ArrayList<>();
        for (Path p : paths) result.addAll(samplePath(p, samplesPerPath));
        return result;
    }

    private List<float[]> samplePath(Path path, int n) {
        List<float[]> pts = new ArrayList<>();
        PathMeasure pm = new PathMeasure(path, false);
        float len = pm.getLength();
        if (len == 0) return pts;
        float[] pos = new float[2], tan = new float[2];
        for (int i = 0; i <= n; i++) {
            pm.getPosTan(len * i / n, pos, tan);
            pts.add(new float[]{pos[0], pos[1]});
        }
        return pts;
    }
}