package com.example.chineseapp.Helpers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.graphics.RectF;
import android.graphics.DashPathEffect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draws a dynamic decomposition panel from HanziStructureDecomposer output. */
public class StructurePanelView extends View {

    private final Paint panelFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slotFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private HanziStructureDecomposer.Node root;
    private final List<HanziStructureDecomposer.Node> leaves = new ArrayList<>();
    private final Map<String, Integer> filledCounts = new HashMap<>();

    private final RectF tempRect = new RectF();
    private final PathEffect dashPanel = new DashPathEffect(new float[]{18f, 14f}, 0f);
    private final PathEffect dashSlot = new DashPathEffect(new float[]{14f, 12f}, 0f);

    public StructurePanelView(Context context) {
        super(context);
        init();
    }

    public StructurePanelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StructurePanelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        panelFillPaint.setStyle(Paint.Style.FILL);
        panelFillPaint.setColor(Color.parseColor("#122A1A"));

        panelStrokePaint.setStyle(Paint.Style.STROKE);
        panelStrokePaint.setStrokeWidth(dp(2.2f));
        panelStrokePaint.setColor(Color.parseColor("#5F8F74"));
        panelStrokePaint.setPathEffect(dashPanel);

        slotStrokePaint.setStyle(Paint.Style.STROKE);
        slotStrokePaint.setStrokeWidth(dp(1.8f));
        slotStrokePaint.setColor(Color.parseColor("#3F5867"));
        slotStrokePaint.setPathEffect(dashSlot);

        slotFillPaint.setStyle(Paint.Style.FILL);
        slotFillPaint.setColor(Color.parseColor("#2E4C3A"));
    }

    public void setStructure(@Nullable HanziStructureDecomposer.Node rootNode,
                             @Nullable List<HanziStructureDecomposer.Node> leafNodes) {
        root = rootNode;
        leaves.clear();
        if (leafNodes != null) {
            leaves.addAll(leafNodes);
        }
        invalidate();
    }

    public void setFilledCounts(Map<String, Integer> selectedCounts) {
        filledCounts.clear();
        if (selectedCounts != null) {
            filledCounts.putAll(selectedCounts);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float inset = dp(2);
        float left = getPaddingLeft() + inset;
        float top = getPaddingTop() + inset;
        float right = getWidth() - getPaddingRight() - inset;
        float bottom = getHeight() - getPaddingBottom() - inset;
        float radius = dp(18);

        if (right <= left || bottom <= top) {
            return;
        }

        tempRect.set(left, top, right, bottom);
        canvas.drawRoundRect(tempRect, radius, radius, panelFillPaint);
        canvas.drawRoundRect(tempRect, radius, radius, panelStrokePaint);

        if (root == null || leaves.isEmpty()) {
            return;
        }

        RectF rootBounds = root.bounds;
        float rootW = Math.max(1f, rootBounds.width());
        float rootH = Math.max(1f, rootBounds.height());

        Map<String, Integer> drawBudget = new HashMap<>(filledCounts);

        for (HanziStructureDecomposer.Node leaf : leaves) {
            RectF b = leaf.bounds;
            float nx0 = (b.left - rootBounds.left) / rootW;
            float nx1 = (b.right - rootBounds.left) / rootW;
            float ny0 = (b.top - rootBounds.top) / rootH;
            float ny1 = (b.bottom - rootBounds.top) / rootH;

            float slotLeft = left + nx0 * (right - left);
            float slotRight = left + nx1 * (right - left);
            float slotTop = top + ny0 * (bottom - top);
            float slotBottom = top + ny1 * (bottom - top);

            float minW = dp(56);
            float minH = dp(44);
            if (slotRight - slotLeft < minW) {
                float cx = (slotLeft + slotRight) / 2f;
                slotLeft = cx - minW / 2f;
                slotRight = cx + minW / 2f;
            }
            if (slotBottom - slotTop < minH) {
                float cy = (slotTop + slotBottom) / 2f;
                slotTop = cy - minH / 2f;
                slotBottom = cy + minH / 2f;
            }

            slotLeft = clamp(slotLeft, left + dp(6), right - dp(26));
            slotTop = clamp(slotTop, top + dp(6), bottom - dp(26));
            slotRight = clamp(slotRight, slotLeft + dp(20), right - dp(6));
            slotBottom = clamp(slotBottom, slotTop + dp(20), bottom - dp(6));

            tempRect.set(slotLeft, slotTop, slotRight, slotBottom);

            boolean fill = false;
            if (leaf.radical != null) {
                int remaining = drawBudget.getOrDefault(leaf.radical, 0);
                if (remaining > 0) {
                    fill = true;
                    drawBudget.put(leaf.radical, remaining - 1);
                }
            }

            if (fill) {
                canvas.drawRoundRect(tempRect, dp(10), dp(10), slotFillPaint);
            }
            canvas.drawRoundRect(tempRect, dp(10), dp(10), slotStrokePaint);
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
