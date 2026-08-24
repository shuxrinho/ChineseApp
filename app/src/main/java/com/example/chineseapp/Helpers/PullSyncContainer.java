package com.example.chineseapp.Helpers;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PullSyncContainer extends FrameLayout {

    public interface PullPermissionProvider {
        boolean canPull();
    }

    public interface SyncCompletion {
        void finish(boolean success, String message);
    }

    public interface SyncRequestListener {
        void onSyncRequested(SyncCompletion completion);
    }

    private final int touchSlop;
    private final float triggerDistancePx;
    private final float maxPullDistancePx;

    private PullPermissionProvider pullPermissionProvider;
    private SyncRequestListener syncRequestListener;

    private LinearLayout indicatorContainer;
    private CircularBorderProgressView circleProgress;
    private TextView indicatorText;

    private View contentView;
    private ValueAnimator resetAnimator;

    private int indicatorTopOffsetPx;
    private int pullStartMaxYpx = Integer.MAX_VALUE;
    private float startX;
    private float startY;
    private float pullDistance;
    private boolean pulling;
    private boolean syncing;
    private boolean ignoreNextEvent;

    public PullSyncContainer(Context context) {
        this(context, null);
    }

    public PullSyncContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PullSyncContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        triggerDistancePx = dp(128f);
        maxPullDistancePx = dp(170f);
        indicatorTopOffsetPx = dpInt(74f);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() > 0) {
            contentView = getChildAt(0);
        }
        ensureIndicator();
    }

    public void setPullPermissionProvider(PullPermissionProvider provider) {
        this.pullPermissionProvider = provider;
    }

    public void setSyncRequestListener(SyncRequestListener listener) {
        this.syncRequestListener = listener;
    }

    public void setIndicatorTopOffsetPx(int px) {
        indicatorTopOffsetPx = Math.max(0, px);
        if (indicatorContainer != null) {
            LayoutParams lp = (LayoutParams) indicatorContainer.getLayoutParams();
            lp.topMargin = indicatorTopOffsetPx;
            indicatorContainer.setLayoutParams(lp);
        }
    }

    public void setPullStartMaxYpx(int px) {
        pullStartMaxYpx = Math.max(0, px);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ignoreNextEvent) {
            if (ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                ignoreNextEvent = false;
            }
            return false;
        }

        if (syncing) return false;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                pulling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = ev.getY() - startY;
                float dx = Math.abs(ev.getX() - startX);

                if (dy > touchSlop && dy > dx && canStartPull()) {
                    stopResetAnimation();
                    pulling = true;
                    updatePullState(dy);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pulling = false;
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!pulling && !syncing) return super.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (!syncing) {
                    float dy = event.getY() - startY;
                    updatePullState(dy);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!syncing) {
                    boolean shouldSync = progress() >= 1f;
                    pulling = false;
                    if (shouldSync) {
                        beginSync();
                    } else {
                        animateBackAndHide();
                    }
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void beginSync() {
        syncing = true;
        animatePullTo(triggerDistancePx);
        circleProgress.setProgress(1f);
        indicatorText.setText("Syncing...");

        if (syncRequestListener == null) {
            finishSync(false, "Sync is unavailable.");
            return;
        }

        syncRequestListener.onSyncRequested((success, message) ->
                post(() -> finishSync(success, message))
        );
    }

    private void finishSync(boolean success, String message) {
        syncing = false;
        indicatorText.setText(message == null || message.trim().isEmpty()
                ? (success ? "Sync completed." : "Sync failed.")
                : message);
        postDelayed(this::animateBackAndHide, 550);
    }

    private void updatePullState(float rawDistance) {
        pullDistance = Math.max(0f, Math.min(maxPullDistancePx, rawDistance));
        if (pullDistance <= 0.5f) {
            hideIndicatorImmediate();
            return;
        }
        showIndicator();
        applyContentTranslation(pullDistance);

        float p = progress();
        circleProgress.setProgress(p);
        indicatorText.setText(p >= 1f ? "Release to sync" : "Pull to sync");
        float alpha = Math.max(0.2f, p);
        indicatorContainer.setAlpha(alpha);
    }

    private void applyContentTranslation(float distance) {
        if (contentView != null) {
            contentView.setTranslationY(distance);
        }
    }

    private float progress() {
        return Math.max(0f, Math.min(1f, pullDistance / triggerDistancePx));
    }

    private void animatePullTo(float targetDistance) {
        float start = pullDistance;
        float end = Math.max(0f, Math.min(maxPullDistancePx, targetDistance));
        stopResetAnimation();

        resetAnimator = ValueAnimator.ofFloat(start, end);
        resetAnimator.setDuration(170L);
        resetAnimator.addUpdateListener(a -> {
            pullDistance = (float) a.getAnimatedValue();
            updatePullState(pullDistance);
        });
        resetAnimator.start();
    }

    private void animateBackAndHide() {
        float start = pullDistance;
        stopResetAnimation();
        resetAnimator = ValueAnimator.ofFloat(start, 0f);
        resetAnimator.setDuration(220L);
        resetAnimator.addUpdateListener(a -> {
            pullDistance = (float) a.getAnimatedValue();
            if (pullDistance <= 0.5f) {
                hideIndicatorImmediate();
            } else {
                showIndicator();
                applyContentTranslation(pullDistance);
                circleProgress.setProgress(progress());
                indicatorContainer.setAlpha(Math.max(0.2f, progress()));
            }
        });
        resetAnimator.start();
    }

    private void stopResetAnimation() {
        if (resetAnimator != null) {
            resetAnimator.cancel();
            resetAnimator = null;
        }
    }

    private boolean canStartPull() {
        if (startY > pullStartMaxYpx) {
            return false;
        }
        if (pullPermissionProvider != null) {
            return pullPermissionProvider.canPull();
        }
        return true;
    }

    private void ensureIndicator() {
        if (indicatorContainer != null) return;

        indicatorContainer = new LinearLayout(getContext());
        indicatorContainer.setOrientation(LinearLayout.HORIZONTAL);
        indicatorContainer.setVisibility(GONE);
        indicatorContainer.setAlpha(0f);
        indicatorContainer.setPadding(dpInt(10f), dpInt(8f), dpInt(12f), dpInt(8f));
        indicatorContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16f));
        bg.setColor(Color.parseColor("#CC151A19"));
        bg.setStroke(dpInt(1f), Color.parseColor("#40FFFFFF"));
        indicatorContainer.setBackground(bg);

        circleProgress = new CircularBorderProgressView(getContext());
        int circleSize = dpInt(28f);
        LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(circleSize, circleSize);

        indicatorText = new TextView(getContext());
        indicatorText.setText("Pull to sync");
        indicatorText.setTextColor(Color.WHITE);
        indicatorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        textLp.leftMargin = dpInt(10f);

        indicatorContainer.addView(circleProgress, circleLp);
        indicatorContainer.addView(indicatorText, textLp);

        LayoutParams lp = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = indicatorTopOffsetPx;
        lp.leftMargin = dpInt(16f);

        addView(indicatorContainer, lp);
        indicatorContainer.bringToFront();
    }

    private void showIndicator() {
        ensureIndicator();
        if (indicatorContainer.getVisibility() != VISIBLE) {
            indicatorContainer.setVisibility(VISIBLE);
        }
    }

    private void hideIndicatorImmediate() {
        pullDistance = 0f;
        applyContentTranslation(0f);
        if (indicatorContainer != null) {
            indicatorContainer.setAlpha(0f);
            indicatorContainer.setVisibility(GONE);
        }
        if (circleProgress != null) {
            circleProgress.setProgress(0f);
        }
        if (indicatorText != null) {
            indicatorText.setText("Pull to sync");
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private int dpInt(float value) {
        return Math.round(dp(value));
    }
}
