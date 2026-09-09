package com.example.chineseapp.supabase;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SupabaseStreakRepository {
    private static final String TAG_STREAK_REPO = "CHAPP_SB_STREAK";
    private final Context context;
    private final SupabaseClient client;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public interface StreakDataCallback {
        void onSuccess(StreakData data);
        void onError(String error);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }

    public static class StreakData {
        public int currentStreak;
        public int longestStreak;
        public String lastActivityDate;
        public List<String> completedDates;

        public StreakData(int currentStreak, int longestStreak, String lastActivityDate, List<String> completedDates) {
            this.currentStreak = currentStreak;
            this.longestStreak = longestStreak;
            this.lastActivityDate = lastActivityDate;
            this.completedDates = completedDates;
        }
    }

    public SupabaseStreakRepository(Context context) {
        this.context = context.getApplicationContext();
        this.client = new SupabaseClient(this.context);
    }

    public void getStreakData(StreakDataCallback callback) {
        Log.d(TAG_STREAK_REPO, "Queueing streak data fetch");
        io.execute(() -> {
            try {
                if (!SupabaseClient.hasConfig()) {
                    throw new IllegalStateException("Supabase is not configured.");
                }
                if (!SessionManager.isLoggedIn(context)) {
                    throw new IllegalStateException("Please log in to view streaks.");
                }

                // Fetch user_streaks
                JSONObject streakResponse = client.getRest("user_streaks?user_id=eq." + SessionManager.getUserId(context), true);
                JSONArray streakRows = new JSONArray(streakResponse.getString("raw"));
                
                int currentStreak = 0;
                int longestStreak = 0;
                String lastActivityDate = null;
                
                if (streakRows.length() > 0) {
                    JSONObject row = streakRows.optJSONObject(0);
                    if (row != null) {
                        currentStreak = row.optInt("current_streak", 0);
                        longestStreak = row.optInt("longest_streak", 0);
                        lastActivityDate = row.optString("last_activity_date", null);
                    }
                }

                // Fetch daily_goals for the current year
                Calendar cal = Calendar.getInstance();
                int year = cal.get(Calendar.YEAR);
                String startDate = String.format(Locale.US, "%d-01-01", year);
                String endDate = String.format(Locale.US, "%d-12-31", year);
                
                JSONObject goalsResponse = client.getRest(
                    "daily_goals?user_id=eq." + SessionManager.getUserId(context) + 
                    "&date=gte." + startDate + "&date=lte." + endDate + 
                    "&is_completed=eq.true", 
                    true
                );
                JSONArray goalsRows = new JSONArray(goalsResponse.getString("raw"));
                
                List<String> completedDates = new ArrayList<>();
                for (int i = 0; i < goalsRows.length(); i++) {
                    JSONObject goal = goalsRows.optJSONObject(i);
                    if (goal != null) {
                        String date = goal.optString("date", null);
                        if (date != null) {
                            completedDates.add(date);
                        }
                    }
                }

                StreakData data = new StreakData(currentStreak, longestStreak, lastActivityDate, completedDates);
                postSuccess(callback, data);
            } catch (Exception e) {
                Log.e(TAG_STREAK_REPO, "Streak data fetch failed: " + e.getMessage(), e);
                postError(callback, e.getMessage() == null ? "Failed to load streak data." : e.getMessage());
            }
        });
    }

    public void markGoalCompleted(SimpleCallback callback) {
        Log.d(TAG_STREAK_REPO, "Queueing daily goal completion");
        io.execute(() -> {
            try {
                if (!SupabaseClient.hasConfig()) {
                    throw new IllegalStateException("Supabase is not configured.");
                }
                if (!SessionManager.isLoggedIn(context)) {
                    throw new IllegalStateException("Please log in to complete daily goals.");
                }

                // Call the RPC function to complete daily goal and update streak
                JSONObject response = client.postRpcWithoutBody("complete_daily_goal", true);
                Log.d(TAG_STREAK_REPO, "Daily goal completion response: " + response.toString());
                
                postSuccess(callback);
            } catch (Exception e) {
                Log.e(TAG_STREAK_REPO, "Daily goal completion failed: " + e.getMessage(), e);
                postError(callback, e.getMessage() == null ? "Failed to complete daily goal." : e.getMessage());
            }
        });
    }

    public boolean isTodayCompletedLocally() {
        String today = getTodayKey();
        return context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .getBoolean("streak_done_" + today, false);
    }

    public void markGoalCompletedLocally() {
        String today = getTodayKey();
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("streak_done_" + today, true)
                .apply();
    }

    private String getTodayKey() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date());
    }

    private <T> void postSuccess(StreakDataCallback callback, T value) {
        main.post(() -> callback.onSuccess((StreakData) value));
    }

    private void postSuccess(SimpleCallback callback) {
        main.post(callback::onSuccess);
    }

    private <T> void postError(StreakDataCallback callback, String error) {
        main.post(() -> callback.onError(error));
    }

    private void postError(SimpleCallback callback, String error) {
        main.post(() -> callback.onError(error));
    }
}
