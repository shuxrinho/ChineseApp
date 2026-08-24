package com.example.chineseapp.Helpers;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import java.util.List;

public final class CustomContextMenu {

    private static final float PRESS_TRANSLATION = 8f;
    private static final float PRESS_SCALE = 0.97f;
    private static final long ANIM_DURATION = 80L;

    private CustomContextMenu() {
    }

    public static void show(View anchor, List<Option> options) {
        if (anchor == null || options == null || options.isEmpty()) return;

        Context context = anchor.getContext();
        LinearLayout menu = new LinearLayout(context);
        menu.setOrientation(LinearLayout.VERTICAL);
        int menuPadding = dp(context, 6f);
        menu.setPadding(menuPadding, menuPadding, menuPadding, menuPadding);
        menu.setBackground(createMenuBackground(context));
        menu.setClipToOutline(true);

        PopupWindow popupWindow = new PopupWindow(
                menu,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(dp(context, 10f));

        for (Option option : options) {
            menu.addView(createItemView(context, popupWindow, option));
        }

        popupWindow.showAsDropDown(anchor, 0, dp(context, 6f), Gravity.NO_GRAVITY);
    }

    public static int resolveIcon(Context context, String drawableName, @DrawableRes int fallbackRes) {
        int found = context.getResources().getIdentifier(drawableName, "drawable", context.getPackageName());
        return found != 0 ? found : fallbackRes;
    }

    private static View createItemView(Context context, PopupWindow popupWindow, Option option) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumWidth(dp(context, 210f));
        row.setPadding(dp(context, 12f), dp(context, 10f), dp(context, 14f), dp(context, 10f));
        row.setBackground(createItemBackground(context));
        row.setClickable(true);
        row.setFocusable(true);

        ImageView icon = new ImageView(context);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(context, 22f), dp(context, 22f));
        iconParams.setMarginEnd(dp(context, 12f));
        icon.setLayoutParams(iconParams);
        if (option.iconRes != 0) {
            icon.setImageResource(option.iconRes);
            icon.setColorFilter(Color.parseColor("#172B22"));
        } else {
            icon.setVisibility(View.INVISIBLE);
        }
        row.addView(icon);

        TextView label = new TextView(context);
        label.setText(option.title);
        label.setTextColor(Color.parseColor("#172B22"));
        label.setTextSize(16f);
        label.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        row.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animatePress(v, true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animatePress(v, false);
                    break;
            }
            return false;
        });

        row.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (option.action != null) {
                option.action.run();
            }
        });

        return row;
    }

    private static void animatePress(View view, boolean pressed) {
        view.animate()
                .translationY(pressed ? PRESS_TRANSLATION : 0f)
                .scaleX(pressed ? PRESS_SCALE : 1f)
                .scaleY(pressed ? PRESS_SCALE : 1f)
                .alpha(pressed ? 0.88f : 1f)
                .setDuration(ANIM_DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private static GradientDrawable createMenuBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(context, 18f));
        drawable.setStroke(dp(context, 1f), Color.parseColor("#1F000000"));
        return drawable;
    }

    private static GradientDrawable createItemBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(ContextCompat.getColor(context, android.R.color.transparent));
        drawable.setCornerRadius(dp(context, 14f));
        return drawable;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static class Option {
        public final String title;
        @DrawableRes
        public final int iconRes;
        public final Runnable action;

        public Option(String title, @DrawableRes int iconRes, Runnable action) {
            this.title = title;
            this.iconRes = iconRes;
            this.action = action;
        }
    }
}
