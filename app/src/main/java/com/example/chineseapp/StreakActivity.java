package com.example.chineseapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class StreakActivity extends AppCompatActivity {
    private static final int DAYS_IN_MONTH = 31;
    private static final int COMPLETED_DAYS = 12;
    private static final int FIRST_DAY_OFFSET = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_streak);
        getWindow().setStatusBarColor(Color.parseColor("#15151B"));
        getWindow().setNavigationBarColor(Color.parseColor("#15151B"));

        findViewById(R.id.back_btn).setOnClickListener(v -> finish());
        findViewById(R.id.help_btn).setOnClickListener(v ->
                Toast.makeText(this, "Complete your daily practice to extend your streak.", Toast.LENGTH_SHORT).show());
        findViewById(R.id.configure_practice_btn).setOnClickListener(v ->
                Toast.makeText(this, "Practice amount settings coming soon.", Toast.LENGTH_SHORT).show());
        findViewById(R.id.invite_friend_btn).setOnClickListener(v ->
                Toast.makeText(this, "Friend invites coming soon.", Toast.LENGTH_SHORT).show());
        findViewById(R.id.shared_mei).setOnClickListener(v ->
                Toast.makeText(this, "Opening Mei Lin's shared streak soon.", Toast.LENGTH_SHORT).show());
        findViewById(R.id.shared_david).setOnClickListener(v ->
                Toast.makeText(this, "Opening David's shared streak soon.", Toast.LENGTH_SHORT).show());

        renderWeekHeader();
        renderCalendar();
        applyPressAnimation(
                findViewById(R.id.back_btn),
                findViewById(R.id.help_btn),
                findViewById(R.id.configure_practice_btn),
                findViewById(R.id.shared_mei),
                findViewById(R.id.shared_david),
                findViewById(R.id.invite_friend_btn)
        );
    }

    private void renderWeekHeader() {
        GridLayout headerGrid = findViewById(R.id.week_header_grid);
        String[] days = {"S", "M", "T", "W", "T", "F", "S"};
        for (String day : days) {
            TextView textView = new TextView(this);
            textView.setText(day);
            textView.setTextColor(Color.parseColor("#B7D9BE"));
            textView.setTextSize(18f);
            textView.setGravity(android.view.Gravity.CENTER);
            textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
            headerGrid.addView(textView, weightedGridParams(dpInt(28f)));
        }
    }

    private void renderCalendar() {
        GridLayout calendarGrid = findViewById(R.id.calendar_grid);
        int totalCells = FIRST_DAY_OFFSET + DAYS_IN_MONTH;
        int rows = (int) Math.ceil(totalCells / 7f);
        int paddedCells = rows * 7;

        for (int index = 0; index < paddedCells; index++) {
            FrameLayout cell = new FrameLayout(this);
            GridLayout.LayoutParams cellParams = weightedGridParams(dpInt(66f));
            cell.setLayoutParams(cellParams);

            int dayNumber = index - FIRST_DAY_OFFSET + 1;
            if (dayNumber >= 1 && dayNumber <= DAYS_IN_MONTH) {
                TextView day = new TextView(this);
                day.setText(String.valueOf(dayNumber));
                day.setGravity(android.view.Gravity.CENTER);
                day.setTextSize(20f);
                day.setTypeface(day.getTypeface(), android.graphics.Typeface.BOLD);

                FrameLayout.LayoutParams dayParams = new FrameLayout.LayoutParams(dpInt(51f), dpInt(51f));
                dayParams.gravity = android.view.Gravity.CENTER;
                if (dayNumber <= COMPLETED_DAYS) {
                    day.setTextColor(Color.WHITE);
                    day.setBackgroundResource(R.drawable.bg_streak_completed_day);
                    day.setElevation(dpInt(8f));
                } else {
                    day.setTextColor(Color.parseColor("#B7D9BE"));
                }
                cell.addView(day, dayParams);
            }
            calendarGrid.addView(cell);
        }
    }

    private GridLayout.LayoutParams weightedGridParams(int heightPx) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
        );
        params.width = 0;
        params.height = heightPx;
        return params;
    }

    private void applyPressAnimation(View... views) {
        for (View view : views) {
            if (view == null) continue;
            view.setOnTouchListener((v, event) -> {
                if (!v.isEnabled()) return false;
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.animate()
                            .scaleX(0.97f)
                            .scaleY(0.97f)
                            .alpha(0.88f)
                            .setDuration(90L)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                } else if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(160L)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
                return false;
            });
        }
    }

    private int dpInt(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
