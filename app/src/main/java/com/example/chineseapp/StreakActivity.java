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

import com.example.chineseapp.supabase.SupabaseStreakRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class StreakActivity extends AppCompatActivity {
    private static final int DAYS_IN_MONTH = 31;
    
    private TextView streakCountText;
    private TextView streakMessageText;
    private TextView monthTitleText;
    private TextView daysCompletedText;
    private GridLayout calendarGrid;
    
    private SupabaseStreakRepository streakRepo;
    private int currentStreak = 0;
    private int longestStreak = 0;
    private Set<String> completedDates = new HashSet<>();

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

        bindViews();
        streakRepo = new SupabaseStreakRepository(this);
        
        renderWeekHeader();
        loadStreakData();
        
        applyPressAnimation(
                findViewById(R.id.back_btn),
                findViewById(R.id.help_btn),
                findViewById(R.id.configure_practice_btn),
                findViewById(R.id.shared_mei),
                findViewById(R.id.shared_david),
                findViewById(R.id.invite_friend_btn)
        );
    }
    
    private void bindViews() {
        streakCountText = findViewById(R.id.streak_count);
        streakMessageText = findViewById(R.id.streak_message);
        monthTitleText = findViewById(R.id.month_title);
        daysCompletedText = findViewById(R.id.days_completed);
        calendarGrid = findViewById(R.id.calendar_grid);
    }
    
    private void loadStreakData() {
        streakRepo.getStreakData(new SupabaseStreakRepository.StreakDataCallback() {
            @Override
            public void onSuccess(SupabaseStreakRepository.StreakData data) {
                currentStreak = data.currentStreak;
                longestStreak = data.longestStreak;
                completedDates = new HashSet<>(data.completedDates);
                
                runOnUiThread(() -> {
                    updateStreakDisplay();
                    renderCalendar();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(StreakActivity.this, error, Toast.LENGTH_SHORT).show();
                    // Show default state even on error
                    updateStreakDisplay();
                    renderCalendar();
                });
            }
        });
    }
    
    private void updateStreakDisplay() {
        streakCountText.setText(currentStreak + " Days");
        
        if (currentStreak == 0) {
            streakMessageText.setText("Start your streak today!");
        } else if (currentStreak < 7) {
            streakMessageText.setText("You're getting started! Keep it up!");
        } else if (currentStreak < 30) {
            streakMessageText.setText("Great progress! You're on fire!");
        } else {
            streakMessageText.setText("Amazing! " + currentStreak + " days strong!");
        }
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
        calendarGrid.removeAllViews();
        
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH); // 0-based
        
        // Set month title
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        monthTitleText.setText(monthFormat.format(cal.getTime()));
        
        // Calculate days in month
        Calendar monthCal = Calendar.getInstance();
        monthCal.set(year, month, 1);
        int daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDayOfWeek = monthCal.get(Calendar.DAY_OF_WEEK); // 1=Sunday
        
        int totalCells = (firstDayOfWeek - 1) + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7f);
        int paddedCells = rows * 7;
        
        // Count completed days this month
        int completedThisMonth = 0;
        Calendar checkCal = Calendar.getInstance();
        checkCal.set(year, month, 1);
        for (int day = 1; day <= daysInMonth; day++) {
            checkCal.set(Calendar.DAY_OF_MONTH, day);
            String dateKey = formatDateKey(checkCal.getTime());
            if (completedDates.contains(dateKey)) {
                completedThisMonth++;
            }
        }
        
        daysCompletedText.setText(completedThisMonth + "/" + daysInMonth + " Days");

        for (int index = 0; index < paddedCells; index++) {
            FrameLayout cell = new FrameLayout(this);
            GridLayout.LayoutParams cellParams = weightedGridParams(dpInt(66f));
            cell.setLayoutParams(cellParams);

            int dayNumber = index - (firstDayOfWeek - 1) + 1;
            if (dayNumber >= 1 && dayNumber <= daysInMonth) {
                TextView day = new TextView(this);
                day.setText(String.valueOf(dayNumber));
                day.setGravity(android.view.Gravity.CENTER);
                day.setTextSize(20f);
                day.setTypeface(day.getTypeface(), android.graphics.Typeface.BOLD);

                FrameLayout.LayoutParams dayParams = new FrameLayout.LayoutParams(dpInt(51f), dpInt(51f));
                dayParams.gravity = android.view.Gravity.CENTER;
                
                // Check if this day is completed
                Calendar dayCal = Calendar.getInstance();
                dayCal.set(year, month, dayNumber);
                String dateKey = formatDateKey(dayCal.getTime());
                boolean isCompleted = completedDates.contains(dateKey);
                
                // Check if it's today
                String todayKey = formatDateKey(new Date());
                boolean isToday = dateKey.equals(todayKey);
                
                if (isCompleted) {
                    day.setTextColor(Color.WHITE);
                    day.setBackgroundResource(R.drawable.bg_streak_completed_day);
                    day.setElevation(dpInt(8f));
                } else if (isToday) {
                    day.setTextColor(Color.parseColor("#FFD700"));
                    day.setBackgroundResource(R.drawable.bg_streak_today_day);
                } else {
                    day.setTextColor(Color.parseColor("#B7D9BE"));
                }
                cell.addView(day, dayParams);
            }
            calendarGrid.addView(cell);
        }
    }
    
    private String formatDateKey(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(date);
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
