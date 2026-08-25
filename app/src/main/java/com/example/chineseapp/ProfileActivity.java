package com.example.chineseapp;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.chineseapp.Helpers.CustomContextMenu;
import com.example.chineseapp.Helpers.PullSyncContainer;
import com.example.chineseapp.supabase.ResultCallback;
import com.example.chineseapp.supabase.SessionManager;
import com.example.chineseapp.supabase.SupabaseAuthRepository;
import com.example.chineseapp.supabase.SupabaseProfileRepository;
import com.example.chineseapp.supabase.SupabaseStorageRepository;
import com.example.chineseapp.supabase.SupabaseUser;
import com.example.chineseapp.supabase.UserDataSyncManager;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {
    private static final String TAG_PROFILE = "CHAPP_PROFILE";

    View changeEmail;
    View changeUsername;
    View changePfp;
    View logOutBtn;
    View profileAvatarFrame;
    View rowEditProfile;
    View notificationBtn, goalsBtn;
    View rowFriends;
    View rowFollowers;
    View rowSecuritySettings;
    View checkDatasets;
    ImageView profileAvatar;
    SwitchCompat notificationsSwitch;
    SwitchCompat goalsSwitch;
    private PullSyncContainer pullSyncContainer;
    private ScrollView profileScroll;
    private SupabaseAuthRepository authRepository;
    private SupabaseStorageRepository storageRepository;
    private SupabaseProfileRepository profileRepository;
    private UserDataSyncManager syncManager;
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private TextView profileName;
    private TextView profileEmail;
    private TextView usernameSecondary;
    private TextView emailSecondary;
    private boolean avatarUploadInProgress = false;
    private boolean usernameUpdateInProgress = false;
    private Uri pendingCameraPhotoUri;

    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    Log.d(TAG_PROFILE, "Image picker returned uri=" + uri);
                    uploadProfilePhoto(uri);
                } else {
                    Log.d(TAG_PROFILE, "Image picker cancelled.");
                }
            });

    private final ActivityResultLauncher<Uri> cameraCapture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && pendingCameraPhotoUri != null) {
                    Log.d(TAG_PROFILE, "Camera returned uri=" + pendingCameraPhotoUri);
                    uploadProfilePhoto(pendingCameraPhotoUri);
                } else {
                    Log.d(TAG_PROFILE, "Camera capture cancelled or failed.");
                    Toast.makeText(this, "No photo captured.", Toast.LENGTH_SHORT).show();
                }
                pendingCameraPhotoUri = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Log.d(TAG_PROFILE, "ProfileActivity created. loggedIn=" + SessionManager.isLoggedIn(this)
                + ", userId=" + SessionManager.getUserId(this));
        authRepository = new SupabaseAuthRepository(this);
        storageRepository = new SupabaseStorageRepository(this);
        profileRepository = new SupabaseProfileRepository(this);

        changeEmail = findViewById(R.id.change_email_btn);
        changeUsername = findViewById(R.id.change_username_btn);
        changePfp = findViewById(R.id.change_pfp_btn);
        profileAvatarFrame = findViewById(R.id.profile_avatar_frame);
        notificationBtn = findViewById(R.id.change_notification_btn);
        rowFriends = findViewById(R.id.row_friends);
        rowFollowers = findViewById(R.id.row_followers);
        rowSecuritySettings = findViewById(R.id.row_security_settings);
        checkDatasets = findViewById(R.id.check_datasets_btn);
        profileAvatar = findViewById(R.id.profile_avatar);
        goalsBtn = findViewById(R.id.set_goals);
        logOutBtn = findViewById(R.id.log_out);

        notificationsSwitch = findViewById(R.id.switch_notifications);
        goalsSwitch = findViewById(R.id.switch_goals);
        pullSyncContainer = findViewById(R.id.pull_sync);
        profileScroll = findViewById(R.id.profile_scroll);
        LinearLayout profileHeader = findViewById(R.id.profile_header);
        syncManager = new UserDataSyncManager(this);

        profileName = findViewById(R.id.profile_name);
        profileEmail = findViewById(R.id.profile_email);
        usernameSecondary = findViewById(R.id.username_secondary);
        emailSecondary = findViewById(R.id.email_secondary);

        renderUser(SessionManager.getUser(this));

        pullSyncContainer.setPullPermissionProvider(() -> profileScroll != null && !profileScroll.canScrollVertically(-1));

        profileHeader.post(() -> {
            pullSyncContainer.setIndicatorTopOffsetPx(profileHeader.getBottom() + dpInt(10f));
            pullSyncContainer.setPullStartMaxYpx(profileHeader.getBottom() + dpInt(24f));
        });

        pullSyncContainer.setSyncRequestListener(completion ->
                syncExecutor.execute(() -> {
                    UserDataSyncManager.SyncResult result = syncManager.syncNow();
                    runOnUiThread(() -> completion.finish(result.success, result.message));
                })
        );

        View.OnClickListener pickPhotoClickListener = v -> {
            Log.d(TAG_PROFILE, "Change profile photo tapped. inProgress=" + avatarUploadInProgress
                    + ", loggedIn=" + SessionManager.isLoggedIn(this));
            if (avatarUploadInProgress) return;
            if (!SessionManager.isLoggedIn(this)) {
                Log.w(TAG_PROFILE, "Blocked avatar picker because user is not logged in.");
                Toast.makeText(this, "Please log in to update your profile photo.", Toast.LENGTH_SHORT).show();
                return;
            }
            showProfilePhotoMenu(v);
        };
        changePfp.setOnClickListener(pickPhotoClickListener);
        profileAvatarFrame.setOnClickListener(pickPhotoClickListener);

        logOutBtn.setOnClickListener(v -> {
            logOutBtn.setEnabled(false);
            authRepository.signOut(new ResultCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean value) {
                    logOutBtn.setEnabled(true);
                    startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
                    finishAffinity();
                }

                @Override
                public void onError(String message) {
                    logOutBtn.setEnabled(true);
                    Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        changeEmail.setOnClickListener(v -> gotoActivity(ChangeEmailActivity.class));
        changeUsername.setOnClickListener(v -> gotoActivity(ChangeUsernameActivity.class));
        goalsBtn.setOnClickListener(v -> {gotoActivity(StreakActivity.class);});
        checkDatasets.setOnClickListener(v -> gotoActivity(DatasetUpdateActivity.class));
        notificationBtn.setOnClickListener(v -> {gotoActivity(NotifActivity.class);});

        applyPressAnimation(
                profileAvatarFrame,
                rowEditProfile,
                changePfp,
                changeUsername,
                rowFriends,
                rowFollowers,
                goalsBtn,
                checkDatasets,
                changeEmail,
                rowSecuritySettings,
                logOutBtn
        );
        animateProfileActionsIn(
                changePfp,
                changeUsername,
                rowFriends,
                rowFollowers,
                goalsBtn,
                checkDatasets,
                changeEmail,
                rowSecuritySettings,
                logOutBtn
        );
    }

    private void gotoActivity(Class activity) {
        Intent intent = new Intent(this, activity);
        startActivity(intent);
    }

    private void showProfilePhotoMenu(View anchor) {
        CustomContextMenu.show(anchor, Arrays.asList(
                new CustomContextMenu.Option(
                        "Take a photo",
                        R.drawable.ic_camera_line,
                        this::launchCameraCapture
                ),
                new CustomContextMenu.Option(
                        "Choose from gallery",
                        R.drawable.ic_gallery,
                        () -> imagePicker.launch("image/*")
                )
        ));
    }

    private void launchCameraCapture() {
        try {
            pendingCameraPhotoUri = createCameraPhotoUri();
            cameraCapture.launch(pendingCameraPhotoUri);
        } catch (IOException e) {
            Log.e(TAG_PROFILE, "Unable to create camera photo uri.", e);
            Toast.makeText(this, "Could not open camera.", Toast.LENGTH_SHORT).show();
        }
    }

    private Uri createCameraPhotoUri() throws IOException {
        File photoDir = new File(getCacheDir(), "profile_photos");
        if (!photoDir.exists() && !photoDir.mkdirs()) {
            throw new IOException("Unable to create profile photo cache directory.");
        }
        File photoFile = File.createTempFile("profile_photo_", ".jpg", photoDir);
        return FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                photoFile
        );
    }

    private void renderUser(SupabaseUser user) {
        if (user == null) {
            Log.w(TAG_PROFILE, "renderUser called with null user.");
            loadAvatar("");
            return;
        }

        Log.d(TAG_PROFILE, "Rendering user. id=" + user.id
                + ", emailPresent=" + (user.email != null && !user.email.isEmpty())
                + ", usernamePresent=" + (user.username != null && !user.username.isEmpty())
                + ", avatarPresent=" + (user.avatarUrl != null && !user.avatarUrl.isEmpty()));
        if (user.username != null && !user.username.isEmpty()) {
            profileName.setText(user.username);
            usernameSecondary.setText(user.username);
        }
        if (user.email != null && !user.email.isEmpty()) {
            profileEmail.setText(user.email);
            emailSecondary.setText(user.email);
        }
        loadAvatar(user.avatarUrl);
    }

    private void uploadProfilePhoto(Uri uri) {
        Log.d(TAG_PROFILE, "Starting profile photo update for uri=" + uri);
        setAvatarUploadInProgress(true);
        Toast.makeText(this, "Uploading profile photo...", Toast.LENGTH_SHORT).show();

        storageRepository.uploadProfilePhoto(uri, new ResultCallback<>() {
            @Override
            public void onSuccess(String publicUrl) {
                Log.d(TAG_PROFILE, "Storage upload succeeded. publicUrlPresent="
                        + (publicUrl != null && !publicUrl.isEmpty()));
                profileRepository.updateAvatarUrl(publicUrl, new ResultCallback<>() {
                    @Override
                    public void onSuccess(SupabaseUser user) {
                        Log.d(TAG_PROFILE, "Profile avatar_url update succeeded. userId="
                                + (user == null ? "" : user.id));
                        setAvatarUploadInProgress(false);
                        renderUser(user);
                        Toast.makeText(ProfileActivity.this, "Profile photo updated.", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG_PROFILE, "Profile avatar_url update failed: " + message);
                        setAvatarUploadInProgress(false);
                        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG_PROFILE, "Storage upload failed: " + message);
                setAvatarUploadInProgress(false);
                Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }



    private boolean isUsernameValid(String value) {
        return value != null && value.matches("^[A-Za-z0-9_]{3,24}$");
    }

    private void loadAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            Log.d(TAG_PROFILE, "Loading default avatar.");
            profileAvatar.setImageResource(R.drawable.ic_default_profile);
            return;
        }

        Log.d(TAG_PROFILE, "Loading remote avatar. urlLength=" + avatarUrl.length());
        profileAvatar.setImageTintList(null);
        Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_default_profile)
                .error(R.drawable.ic_default_profile)
                .circleCrop()
                .into(profileAvatar);
    }

    private void setAvatarUploadInProgress(boolean inProgress) {
        avatarUploadInProgress = inProgress;
        changePfp.setEnabled(!inProgress);
        profileAvatarFrame.setEnabled(!inProgress);
        Log.d(TAG_PROFILE, "Avatar upload progress changed. inProgress=" + inProgress);
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

    private void animateProfileActionsIn(View... views) {
        for (int i = 0; i < views.length; i++) {
            View view = views[i];
            if (view == null) continue;
            view.setAlpha(0f);
            view.setTranslationY(dpInt(10f));
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(45L * i)
                    .setDuration(260L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        syncExecutor.shutdownNow();
    }

    private int dpInt(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
