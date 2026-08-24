package com.example.chineseapp.Helpers;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

public class StrokeView extends View {

    // ── Static display state ──────────────────────────────────────────────────
    private List<Path>      strokesToDraw = new ArrayList<>();
    private List<float[][]> mediansToDraw = new ArrayList<>();

    // ── Animation state ───────────────────────────────────────────────────────
    private final List<Path>      completedStrokes = new ArrayList<>();
    private final List<float[][]> completedMedians = new ArrayList<>();
    private Path      animatingPath   = null;
    private float[][] animatingMedian = null;
    private float     animProgress    = 0f;
    private boolean   animMode        = false;
    private boolean   animActive      = false;
    private ValueAnimator strokeAnimator;

    // ── Paints ────────────────────────────────────────────────────────────────
    private Paint strokePaint, shaftPaint, arrowPaint, animPaint;
    private Matrix matrix;
    private static final float SOURCE_SIZE    = 1000f;
    private static final int   STROKE_DURATION = 250; // ms per stroke

    public StrokeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(0xFFCCCCCC);
        strokePaint.setStyle(Paint.Style.FILL);

        shaftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shaftPaint.setColor(0xFFE67E22);
        shaftPaint.setStyle(Paint.Style.STROKE);
        shaftPaint.setStrokeWidth(5f);
        shaftPaint.setStrokeCap(Paint.Cap.ROUND);

        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(0xFFE67E22);
        arrowPaint.setStyle(Paint.Style.FILL);

        // Brush-stroke paint used while a stroke is being animated
        animPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        animPaint.setColor(0xFF444444);
        animPaint.setStyle(Paint.Style.STROKE);
        animPaint.setStrokeWidth(36f);
        animPaint.setStrokeCap(Paint.Cap.ROUND);
        animPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Animate all strokes in sequence, each drawn progressively along its
     * median path. Calls {@code onAllDone} on the main thread when finished.
     */
    public void animateStrokes(List<Path>      strokes,
                               List<float[][]> medians,
                               Runnable        onAllDone) {
        cancelAnimator();
        completedStrokes.clear();
        completedMedians.clear();
        animatingPath   = null;
        animatingMedian = null;
        animProgress    = 0f;
        animMode        = true;
        animActive      = true;
        invalidate();
        animateSequentially(strokes, medians, 0, onAllDone);
    }

    /** Cancel any running animation; keeps whatever strokes were completed. */
    public void stopAnimation() {
        animActive = false;
        cancelAnimator();
        animatingPath   = null;
        animatingMedian = null;
        animMode        = false;
        invalidate();
    }

    /**
     * Show the strokes of a single component with direction arrows.
     * Also stops any running animation.
     */
    public void showComponent(List<Path> strokes, List<float[][]> medians) {
        stopAnimation();
        strokesToDraw = new ArrayList<>(strokes);
        mediansToDraw = new ArrayList<>(medians);
        buildMatrix();
        invalidate();
    }

    /** Show all strokes of the character. */
    public void showAll(List<Path> all, List<float[][]> medians) {
        showComponent(all, medians);
    }

    /** Hide everything (free-draw / no-hint stage). */
    public void showNothing() {
        stopAnimation();
        strokesToDraw.clear();
        mediansToDraw.clear();
        invalidate();
    }

    // ── Legacy helpers ────────────────────────────────────────────────────────
    public void showTopHalf(List<Path> all, List<float[][]> medians) {
        int half = Math.max(1, all.size() / 2);
        showComponent(
                new ArrayList<>(all.subList(0, half)),
                new ArrayList<>(medians.subList(0, half)));
    }

    public void showBottomHalf(List<Path> all, List<float[][]> medians) {
        int half = Math.max(1, all.size() / 2);
        showComponent(
                new ArrayList<>(all.subList(half, all.size())),
                new ArrayList<>(medians.subList(half, medians.size())));
    }

    // ── Internal animation helpers ────────────────────────────────────────────

    private void animateSequentially(List<Path>      strokes,
                                     List<float[][]> medians,
                                     int             index,
                                     Runnable        onAllDone) {
        if (!animActive) return;
        if (index >= strokes.size()) {
            animMode = false;
            if (onAllDone != null) onAllDone.run();
            return;
        }
        animateSingleStroke(
                strokes.get(index),
                medians.get(index),
                () -> animateSequentially(strokes, medians, index + 1, onAllDone));
    }

    private void animateSingleStroke(Path stroke, float[][] median, Runnable onDone) {
        animatingPath   = stroke;
        animatingMedian = median;
        animProgress    = 0f;

        strokeAnimator = ValueAnimator.ofFloat(0f, 1f);
        strokeAnimator.setDuration(STROKE_DURATION);
        strokeAnimator.setInterpolator(new LinearInterpolator());

        strokeAnimator.addUpdateListener(anim -> {
            animProgress = (float) anim.getAnimatedValue();
            invalidate();
        });

        strokeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!animActive) return;
                // Stroke is complete – add to finished list and show fully
                completedStrokes.add(stroke);
                completedMedians.add(median);
                animatingPath   = null;
                animatingMedian = null;
                animProgress    = 0f;
                invalidate();
                if (onDone != null) onDone.run();
            }
        });

        strokeAnimator.start();
    }

    private void cancelAnimator() {
        if (strokeAnimator != null && strokeAnimator.isRunning()) {
            strokeAnimator.cancel();
        }
        strokeAnimator = null;
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        buildMatrix();
    }

    private void buildMatrix() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float scale = Math.min(w, h) / SOURCE_SIZE * 0.85f;
        float dx = (w - SOURCE_SIZE * scale) / 2f;
        float dy = (h - SOURCE_SIZE * scale) / 2f;
        matrix = new Matrix();
        matrix.setScale(scale, -scale);
        matrix.postTranslate(dx, h - dy);
    }

    private float[] transformPoint(float x, float y) {
        float[] pt = {x, y};
        matrix.mapPoints(pt);
        return pt;
    }

    // ── Drawing ───────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        if (matrix == null) return;

        if (animMode) {
            drawAnimationFrame(canvas);
        } else {
            drawStatic(canvas);
        }
    }

    /** Normal static rendering: filled shapes + direction arrows. */
    private void drawStatic(Canvas canvas) {
        for (Path p : strokesToDraw) {
            Path t = new Path();
            p.transform(matrix, t);
            canvas.drawPath(t, strokePaint);
        }
        drawArrows(canvas, mediansToDraw);
    }

    /** Animation rendering: completed strokes fully drawn + current stroke traced. */
    private void drawAnimationFrame(Canvas canvas) {
        // 1. Already-completed strokes as filled shapes
        for (Path p : completedStrokes) {
            Path t = new Path();
            p.transform(matrix, t);
            canvas.drawPath(t, strokePaint);
        }
        // Draw arrows only for completed strokes
        drawArrows(canvas, completedMedians);

        // 2. Currently animating stroke – progressive trace along median
        if (animatingMedian != null && animatingMedian.length >= 2 && animProgress > 0f) {
            // Build median path in screen coordinates
            Path medianPath = new Path();
            float[] first = transformPoint(animatingMedian[0][0], animatingMedian[0][1]);
            medianPath.moveTo(first[0], first[1]);
            for (int j = 1; j < animatingMedian.length; j++) {
                float[] pt = transformPoint(animatingMedian[j][0], animatingMedian[j][1]);
                medianPath.lineTo(pt[0], pt[1]);
            }

            // Measure and extract partial segment
            PathMeasure pm = new PathMeasure(medianPath, false);
            float totalLen = pm.getLength();
            if (totalLen > 0f) {
                Path partial = new Path();
                pm.getSegment(0f, totalLen * animProgress, partial, true);
                canvas.drawPath(partial, animPaint);
            }
        }
    }

    /** Draw direction arrows for a given list of median point sets. */
    private void drawArrows(Canvas canvas, List<float[][]> medians) {
        for (float[][] pts : medians) {
            if (pts.length < 2) continue;

            float[][] tp = new float[pts.length][2];
            for (int j = 0; j < pts.length; j++) {
                float[] mapped = transformPoint(pts[j][0], pts[j][1]);
                tp[j][0] = mapped[0];
                tp[j][1] = mapped[1];
            }

            // Shaft
            Path shaft = new Path();
            shaft.moveTo(tp[0][0], tp[0][1]);
            for (int j = 1; j < tp.length - 1; j++) {
                shaft.lineTo(tp[j][0], tp[j][1]);
            }
            canvas.drawPath(shaft, shaftPaint);

            // Arrowhead
            float[] tip  = tp[tp.length - 1];
            float[] prev = tp[tp.length - 2];
            float angle = (float) Math.atan2(tip[1] - prev[1], tip[0] - prev[0]);

            float dx2 = tip[0] - prev[0], dy2 = tip[1] - prev[1];
            float segLen    = (float) Math.sqrt(dx2 * dx2 + dy2 * dy2);
            float arrowSize = Math.max(20f, Math.min(segLen * 1.2f, 55f));

            Path arrow = new Path();
            arrow.moveTo(
                    tip[0] + arrowSize * (float) Math.cos(angle),
                    tip[1] + arrowSize * (float) Math.sin(angle));
            arrow.lineTo(
                    tip[0] + arrowSize * 0.55f * (float) Math.cos(angle + Math.PI * 0.75),
                    tip[1] + arrowSize * 0.55f * (float) Math.sin(angle + Math.PI * 0.75));
            arrow.lineTo(
                    tip[0] + arrowSize * 0.55f * (float) Math.cos(angle - Math.PI * 0.75),
                    tip[1] + arrowSize * 0.55f * (float) Math.sin(angle - Math.PI * 0.75));
            arrow.close();
            canvas.drawPath(arrow, arrowPaint);
        }
    }
}