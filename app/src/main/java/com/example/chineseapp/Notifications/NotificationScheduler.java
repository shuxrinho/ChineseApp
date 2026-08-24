package com.example.chineseapp.Notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.chineseapp.R;
import com.example.chineseapp.StreakActivity;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class NotificationScheduler {
    public static final String UNIQUE_STREAK_WORK = "STREAK_NOTIFICATIONS";
    public static final String PREFS_NAME = "notification_settings";
    public static final String KEY_ENABLED = "notifications_enabled";
    public static final String KEY_STREAK_ENABLED = "streak_notifications_enabled";
    public static final String KEY_HOUR = "reminder_hour";
    public static final String KEY_MINUTE = "reminder_minute";
    public static final String KEY_COMPLETED_DATE = "streak_completed_date";
    public static final String KEY_MESSAGE_DATE = "daily_message_date";
    public static final String KEY_MESSAGE_INDEX = "daily_message_index";
    public static final String KEY_LAST_MESSAGE_INDEX = "last_message_index";
    public static final int DEFAULT_HOUR = 20;
    public static final int DEFAULT_MINUTE = 0;

    private static final String CHANNEL_ID = "daily_streak_reminders";
    private static final int NOTIFICATION_ID = 1205;

    private static final String[] REMINDER_TEXTS = {
            "Your Chinese lessons miss you. Even your streak is checking the door every 5 minutes.",
            "Learn 5 Chinese words today or accidentally order 17 dumplings tomorrow.",
            "Your future self speaking fluent Chinese is currently disappointed. Fix that.",
            "The characters aren\u2019t that scary. One of them is literally just three lines pretending to be important.",
            "Study Chinese now. Xi\u00e8xi\u00e8 later.",
            "Your brain: \u201cOne quick lesson.\u201d\n   Your phone: \u201cLet\u2019s scroll for 4 hours.\u201d\n   Choose wisely.",
            "A panda somewhere believes in your Chinese progress. Don\u2019t let the panda down.",
            "Chinese has tones. Your grades also have tones. Right now they sound worried.",
            "Missed your lesson today? Even Google Translate sighed.",
            "One lesson a day keeps the \u201chow do I say this?\u201d panic away."
    };

    public static void scheduleDaily(Context context, int hour, int minute) {
        SharedPreferences prefs = prefs(context);
        prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putBoolean(KEY_STREAK_ENABLED, true)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();

        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (target.getTimeInMillis() <= now.getTimeInMillis()) {
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        long initialDelay = target.getTimeInMillis() - now.getTimeInMillis();

        PeriodicWorkRequest dailyWork =
                new PeriodicWorkRequest.Builder(NotificationWorker.class, 24, TimeUnit.HOURS)
                        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_STREAK_WORK,
                ExistingPeriodicWorkPolicy.REPLACE,
                dailyWork
        );
    }

    public static void scheduleFromPreferences(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.getBoolean(KEY_ENABLED, false) || !prefs.getBoolean(KEY_STREAK_ENABLED, true)) {
            cancelDaily(context);
            return;
        }
        scheduleDaily(
                context,
                prefs.getInt(KEY_HOUR, DEFAULT_HOUR),
                prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        );
    }

    public static void cancelDaily(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_ENABLED, false)
                .apply();
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_STREAK_WORK);
    }

    public static void setStreakEnabled(Context context, boolean enabled) {
        prefs(context).edit()
                .putBoolean(KEY_STREAK_ENABLED, enabled)
                .apply();
        if (enabled && prefs(context).getBoolean(KEY_ENABLED, false)) {
            scheduleFromPreferences(context);
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_STREAK_WORK);
        }
    }

    public static void saveReminderTime(Context context, int hour, int minute) {
        prefs(context).edit()
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();
        scheduleFromPreferences(context);
    }

    public static boolean areNotificationsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static boolean areStreakNotificationsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_STREAK_ENABLED, true);
    }

    public static int getReminderHour(Context context) {
        return prefs(context).getInt(KEY_HOUR, DEFAULT_HOUR);
    }

    public static int getReminderMinute(Context context) {
        return prefs(context).getInt(KEY_MINUTE, DEFAULT_MINUTE);
    }

    public static String getReminderTimeText(Context context) {
        return String.format(Locale.US, "%02d:%02d", getReminderHour(context), getReminderMinute(context));
    }

    public static void markStreakCompleted(Context context) {
        String today = todayKey();
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("streak_done", true)
                .apply();
        prefs(context).edit()
                .putString(KEY_COMPLETED_DATE, today)
                .apply();
    }

    public static boolean isStreakCompletedToday(Context context) {
        return todayKey().equals(prefs(context).getString(KEY_COMPLETED_DATE, ""));
    }

    public static void showDailyReminderIfNeeded(Context context) {
        if (!areNotificationsEnabled(context) || !areStreakNotificationsEnabled(context)) {
            return;
        }
        if (isStreakCompletedToday(context)) {
            return;
        }
        showReminder(context, getDailyReminderText(context));
    }

    public static void showTestReminder(Context context) {
        showReminder(context, getDailyReminderText(context));
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Daily streak reminders",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Reminders to study Chinese and extend your streak.");
        manager.createNotificationChannel(channel);
    }

    private static void showReminder(Context context, String body) {
        if (!hasNotificationPermission(context)) {
            return;
        }

        createChannel(context);

        Intent intent = new Intent(context, StreakActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                flags
        );

        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notifications)
                        .setContentTitle("Daily Streak")
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setColor(ContextCompat.getColor(context, R.color.primary));

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification.build());
        }
    }

    private static boolean hasNotificationPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static String getDailyReminderText(Context context) {
        SharedPreferences prefs = prefs(context);
        String today = todayKey();
        if (today.equals(prefs.getString(KEY_MESSAGE_DATE, ""))) {
            int savedIndex = prefs.getInt(KEY_MESSAGE_INDEX, 0);
            if (savedIndex >= 0 && savedIndex < REMINDER_TEXTS.length) {
                return REMINDER_TEXTS[savedIndex];
            }
        }

        int lastIndex = prefs.getInt(KEY_LAST_MESSAGE_INDEX, -1);
        int nextIndex = new Random().nextInt(REMINDER_TEXTS.length);
        if (REMINDER_TEXTS.length > 1 && nextIndex == lastIndex) {
            nextIndex = (nextIndex + 1 + new Random().nextInt(REMINDER_TEXTS.length - 1)) % REMINDER_TEXTS.length;
        }

        prefs.edit()
                .putString(KEY_MESSAGE_DATE, today)
                .putInt(KEY_MESSAGE_INDEX, nextIndex)
                .putInt(KEY_LAST_MESSAGE_INDEX, nextIndex)
                .apply();
        return REMINDER_TEXTS[nextIndex];
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String todayKey() {
        Calendar calendar = Calendar.getInstance();
        return String.format(
                Locale.US,
                "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
        );
    }
}
