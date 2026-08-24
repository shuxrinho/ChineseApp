package com.example.chineseapp;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.chineseapp.Helpers.CustomButton;
import com.example.chineseapp.Notifications.NotificationScheduler;

public class NotifActivity extends AppCompatActivity {
    private static final int REQ_POST_NOTIFICATIONS = 100;

    private SwitchCompat masterSwitch;
    private SwitchCompat streakSwitch;
    private TextView statusTitle;
    private TextView statusBody;
    private TextView reminderTime;
    private TextView todayStatus;
    private LinearLayout timeRow;
    private CustomButton saveButton, testButton;

    private int selectedHour;
    private int selectedMinute;
    private boolean applyingState = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notif);
        getWindow().setStatusBarColor(0xFF15151B);
        getWindow().setNavigationBarColor(0xFF15151B);

        bindViews();
        selectedHour = NotificationScheduler.getReminderHour(this);
        selectedMinute = NotificationScheduler.getReminderMinute(this);

        ImageView back = findViewById(R.id.back_img);
        back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        timeRow.setOnClickListener(v -> openTimePicker());
        saveButton.setOnClickListener(v -> saveSettings(true));
        testButton.setOnClickListener(v -> sendTestReminder());

        masterSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applyingState) return;
            if (isChecked) {
                ensurePermissionThen(() -> {
                    NotificationScheduler.scheduleDaily(this, selectedHour, selectedMinute);
                    refreshUi();
                    Toast.makeText(this, "Daily reminders turned on.", Toast.LENGTH_SHORT).show();
                });
            } else {
                NotificationScheduler.cancelDaily(this);
                refreshUi();
                Toast.makeText(this, "Daily reminders turned off.", Toast.LENGTH_SHORT).show();
            }
        });

        streakSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applyingState) return;
            NotificationScheduler.setStreakEnabled(this, isChecked);
            refreshUi();
        });

        applyPressAnimation(back, timeRow, saveButton, testButton);
        refreshUi();
    }

    private void bindViews() {
        masterSwitch = findViewById(R.id.master_switch);
        streakSwitch = findViewById(R.id.streak_switch);
        statusTitle = findViewById(R.id.notification_status_title);
        statusBody = findViewById(R.id.notification_status_body);
        reminderTime = findViewById(R.id.reminder_time);
        todayStatus = findViewById(R.id.today_status);
        timeRow = findViewById(R.id.time_row);
        saveButton = findViewById(R.id.save_btn);
        testButton = findViewById(R.id.test_btn);
    }

    private void refreshUi() {
        boolean enabled = NotificationScheduler.areNotificationsEnabled(this);
        boolean streakEnabled = NotificationScheduler.areStreakNotificationsEnabled(this);

        applyingState = true;
        masterSwitch.setChecked(enabled);
        streakSwitch.setChecked(streakEnabled);
        applyingState = false;

        reminderTime.setText(String.format(java.util.Locale.US, "%02d:%02d", selectedHour, selectedMinute));
        streakSwitch.setEnabled(enabled);
        timeRow.setEnabled(enabled);
        saveButton.setEnabled(enabled);
        testButton.setEnabled(hasNotificationPermission());

        statusTitle.setText(enabled ? "Daily reminders are on" : "Daily reminders are off");
        if (enabled && streakEnabled) {
            statusBody.setText("A random streak reminder will arrive every day at "
                    + NotificationScheduler.getReminderTimeText(this) + ".");
        } else if (enabled) {
            statusBody.setText("Notifications are enabled, but streak reminders are paused.");
        } else {
            statusBody.setText("Turn them on to get one streak reminder every day.");
        }

        todayStatus.setText(NotificationScheduler.isStreakCompletedToday(this)
                ? "Today's practice is complete, so the next reminder will wait until tomorrow."
                : "Today's lesson is not completed yet.");
    }

    private void openTimePicker() {
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    reminderTime.setText(String.format(java.util.Locale.US, "%02d:%02d", selectedHour, selectedMinute));
                },
                selectedHour,
                selectedMinute,
                true
        );
        dialog.setTitle("Choose reminder time");
        dialog.show();
    }

    private void saveSettings(boolean showToast) {
        ensurePermissionThen(() -> {
            if (!masterSwitch.isChecked()) {
                NotificationScheduler.cancelDaily(this);
            } else {
                NotificationScheduler.saveReminderTime(this, selectedHour, selectedMinute);
                NotificationScheduler.setStreakEnabled(this, streakSwitch.isChecked());
            }
            refreshUi();
            if (showToast) {
                Toast.makeText(this, "Reminder settings saved.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendTestReminder() {
        ensurePermissionThen(() -> {
            NotificationScheduler.showTestReminder(this);
            Toast.makeText(this, "Test reminder sent.", Toast.LENGTH_SHORT).show();
        });
    }

    private void ensurePermissionThen(Runnable action) {
        if (hasNotificationPermission()) {
            action.run();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_POST_NOTIFICATIONS
            );
        }
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_POST_NOTIFICATIONS) {
            return;
        }

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            NotificationScheduler.scheduleDaily(this, selectedHour, selectedMinute);
            NotificationScheduler.setStreakEnabled(this, streakSwitch.isChecked());
            Toast.makeText(this, "Daily reminders turned on.", Toast.LENGTH_SHORT).show();
        } else {
            NotificationScheduler.cancelDaily(this);
            Toast.makeText(this, "Notification permission was denied.", Toast.LENGTH_SHORT).show();
        }
        refreshUi();
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
}
