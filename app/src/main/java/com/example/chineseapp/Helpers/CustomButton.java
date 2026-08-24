package com.example.chineseapp.Helpers;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CustomButton extends FrameLayout{

    private static final float PRESS_TRANSLATION = 8f;
    private static final float PRESS_SCALE = 0.97f;
    private static final long ANIM_DURATION = 80;
    public CustomButton(@NonNull Context context) {
        super(context);
        init();
    }

    // REQUIRED for XML inflation (THIS ONE MATTERS MOST)
    public CustomButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // REQUIRED for styles/themes
    public CustomButton(@NonNull Context context,
                                @Nullable AttributeSet attrs,
                                int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);

        setOnTouchListener((v, event) -> {
            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    animatePress(true);
                    break;

                case MotionEvent.ACTION_UP:
                    animatePress(false);
                    performClick();
                    break;

                case MotionEvent.ACTION_CANCEL:
                    animatePress(false);
                    break;
            }
            return true;
        });
    }

    private void animatePress(boolean pressed) {
        animate()
                .translationY(pressed ? PRESS_TRANSLATION : 0f)
                .scaleX(pressed ? PRESS_SCALE : 1f)
                .scaleY(pressed ? PRESS_SCALE : 1f)
                .setDuration(ANIM_DURATION)
                .start();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
