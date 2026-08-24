package com.example.chineseapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.chineseapp.Helpers.CustomButton;
import com.example.chineseapp.Helpers.PullSyncContainer;
import com.example.chineseapp.offline.DatasetPreloadCoordinator;
import com.example.chineseapp.supabase.SessionManager;
import com.example.chineseapp.supabase.UserDataSyncManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    ImageView profileImage, notifImage, settingsImage, fireEmoji;
    CustomButton dictionaryButton, sentencesButton, handwritingButton, practiceButton, gamesBtn;
    TextView streakCounter, datasetStatusBanner;

    private PullSyncContainer pullSyncContainer;
    private UserDataSyncManager syncManager;
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private final DatasetPreloadCoordinator.Listener datasetListener = this::renderDatasetStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        notifImage = findViewById(R.id.notifications_icon);
        settingsImage = findViewById(R.id.settings_icon);
        profileImage = findViewById(R.id.profileImage);
        dictionaryButton = findViewById(R.id.btn_dictionary);
        sentencesButton = findViewById(R.id.btn_sentences);
        handwritingButton = findViewById(R.id.btn_handwriting);
        practiceButton = findViewById(R.id.btn_practice);
        streakCounter = findViewById(R.id.streak_counter);
        datasetStatusBanner = findViewById(R.id.dataset_status_banner);
        fireEmoji = findViewById(R.id.fire_emoji);
        gamesBtn = findViewById(R.id.btn_games);
        pullSyncContainer = findViewById(R.id.pull_sync);
        LinearLayout topBar = findViewById(R.id.topAppBar);
        syncManager = new UserDataSyncManager(this);

        notifImage.setOnClickListener(v -> goToActivity(NotifActivity.class));
        settingsImage.setOnClickListener(v -> goToActivity(SettingsActivity.class));
        profileImage.setOnClickListener(v -> goToActivity(ProfileActivity.class));
        practiceButton.setOnClickListener(v -> goToActivity(HandwritingActivity.class));
        dictionaryButton.setOnClickListener(v -> goToActivity(DictionaryActivity.class));
        sentencesButton.setOnClickListener(v -> goToActivity(SentencesActivity.class));
        handwritingButton.setOnClickListener(v -> goToActivity(HandwritingActivity.class));
        streakCounter.setOnClickListener(v -> goToActivity(StreakActivity.class));
        fireEmoji.setOnClickListener(v -> goToActivity(StreakActivity.class));
        gamesBtn.setOnClickListener(v -> goToActivity(GamesActivity.class));

        pullSyncContainer.setPullPermissionProvider(() -> true);
        topBar.post(() -> {
            pullSyncContainer.setIndicatorTopOffsetPx(topBar.getBottom() + dpInt(10f));
            pullSyncContainer.setPullStartMaxYpx(topBar.getBottom() + dpInt(24f));
        });
        pullSyncContainer.setSyncRequestListener(completion ->
                syncExecutor.execute(() -> {
                    UserDataSyncManager.SyncResult result = syncManager.syncNow();
                    runOnUiThread(() -> completion.finish(result.success, result.message));
                })
        );

        DatasetPreloadCoordinator.addListener(datasetListener);
        DatasetPreloadCoordinator.startIfNeeded(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileAvatar();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DatasetPreloadCoordinator.removeListener(datasetListener);
        syncExecutor.shutdownNow();
    }

    private void goToActivity(Class<?> targetActivity) {
        Intent intent = new Intent(this, targetActivity);
        startActivity(intent);
    }

    private int dpInt(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void loadProfileAvatar() {
        String avatarUrl = SessionManager.getAvatarUrl(this);
        if (avatarUrl.isEmpty()) {
            profileImage.setImageResource(R.drawable.ic_default_profile);
            return;
        }

        profileImage.setImageTintList(null);
        Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_default_profile)
                .error(R.drawable.ic_default_profile)
                .circleCrop()
                .into(profileImage);
    }

    private void renderDatasetStatus(boolean running, String message) {
        if (datasetStatusBanner == null) return;
        String safeMessage = message == null ? "" : message.trim();
        boolean show = running || safeMessage.toLowerCase().contains("failed");
        datasetStatusBanner.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        if (!safeMessage.isEmpty()) {
            datasetStatusBanner.setText(safeMessage);
        }
    }
}
