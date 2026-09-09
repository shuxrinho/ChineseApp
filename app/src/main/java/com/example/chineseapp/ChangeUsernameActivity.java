package com.example.chineseapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.chineseapp.Helpers.CustomButton;
import com.example.chineseapp.supabase.ResultCallback;
import com.example.chineseapp.supabase.SessionManager;
import com.example.chineseapp.supabase.SupabaseProfileRepository;
import com.example.chineseapp.supabase.SupabaseUser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChangeUsernameActivity extends AppCompatActivity {

    private EditText usernameInput;
    private ImageView clearButton;
    private ImageView checkLowercase;
    private ImageView checkNoNumberStart;
    private ImageView checkLength;
    private ImageView checkSpecialChars;
    private ImageView checkAvailability;
    private ProgressBar availabilityProgress;
    private CustomButton saveButton;
    private TextView saveButtonText;
    private View backButton;

    private SupabaseProfileRepository profileRepository;
    private ExecutorService executor;
    private Handler mainHandler;

    private boolean isUsernameValid = false;
    private boolean isAvailable = false;
    private String currentUsername = "";
    private static final long AVAILABILITY_CHECK_DELAY_MS = 500;
    private Runnable availabilityCheckRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_username);

        profileRepository = new SupabaseProfileRepository(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Get current username from SessionManager
        SupabaseUser currentUser = SessionManager.getUser(this);
        if (currentUser != null && currentUser.username != null) {
            currentUsername = currentUser.username;
        }

        initViews();
        setupListeners();
    }

    private void initViews() {
        usernameInput = findViewById(R.id.username_input);
        clearButton = findViewById(R.id.clear_button);
        checkLowercase = findViewById(R.id.check_lowercase);
        checkNoNumberStart = findViewById(R.id.check_no_number_start);
        checkLength = findViewById(R.id.check_length);
        checkSpecialChars = findViewById(R.id.check_special_chars);
        checkAvailability = findViewById(R.id.check_availability);
        availabilityProgress = findViewById(R.id.availability_progress);
        saveButton = findViewById(R.id.save_button);
        saveButtonText = findViewById(R.id.save_button_text);
        backButton = findViewById(R.id.back_button);
    }

    private void setupListeners() {
        // Back button
        backButton.setOnClickListener(v -> finish());

        // Clear button
        clearButton.setOnClickListener(v -> {
            usernameInput.setText("");
            usernameInput.requestFocus();
        });

        // Text watcher for real-time validation
        usernameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String username = s.toString();
                
                // Show/hide clear button
                clearButton.setVisibility(username.isEmpty() ? View.GONE : View.VISIBLE);

                // Validate criteria
                boolean lowercaseOnly = validateLowercaseOnly(username);
                boolean noNumberStart = validateNoNumberStart(username);
                boolean validLength = validateLength(username);
                boolean noSpecialChars = validateNoSpecialChars(username);

                // Update check icons
                updateCheckIcon(checkLowercase, lowercaseOnly);
                updateCheckIcon(checkNoNumberStart, noNumberStart);
                updateCheckIcon(checkLength, validLength);
                updateCheckIcon(checkSpecialChars, noSpecialChars);

                // Check availability only if other criteria pass
                if (lowercaseOnly && noNumberStart && validLength && noSpecialChars) {
                    scheduleAvailabilityCheck(username);
                } else {
                    cancelAvailabilityCheck();
                    setAvailabilityState(false, false);
                }

                // Overall validity
                isUsernameValid = lowercaseOnly && noNumberStart && validLength && noSpecialChars && isAvailable;
                updateSaveButton();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Save button click
        saveButton.setOnClickListener(v -> {
            if (!saveButton.isEnabled()) {
                Toast.makeText(this, "Please enter a valid username", Toast.LENGTH_SHORT).show();
                return;
            }

            String newUsername = usernameInput.getText().toString().trim();
            if (newUsername.equals(currentUsername)) {
                Toast.makeText(this, "Username is the same as current", Toast.LENGTH_SHORT).show();
                return;
            }

            saveNewUsername(newUsername);
        });
    }

    private boolean validateLowercaseOnly(String username) {
        if (username.isEmpty()) return false;
        return username.matches("^[a-z_]+$");
    }

    private boolean validateNoNumberStart(String username) {
        if (username.isEmpty()) return true;
        char firstChar = username.charAt(0);
        return !Character.isDigit(firstChar);
    }

    private boolean validateLength(String username) {
        return username.length() >= 3 && username.length() <= 15;
    }

    private boolean validateNoSpecialChars(String username) {
        if (username.isEmpty()) return true;
        // Only lowercase letters, numbers, and underscores allowed
        return username.matches("^[a-z0-9_]+$");
    }

    private void updateCheckIcon(ImageView imageView, boolean isValid) {
        if (isValid) {
            imageView.setImageResource(R.drawable.ic_check_filled);
        } else {
            imageView.setImageResource(R.drawable.ic_check_empty);
        }
    }

    private void scheduleAvailabilityCheck(String username) {
        cancelAvailabilityCheck();
        
        availabilityCheckRunnable = () -> {
            checkUsernameAvailability(username);
        };
        mainHandler.postDelayed(availabilityCheckRunnable, AVAILABILITY_CHECK_DELAY_MS);
    }

    private void cancelAvailabilityCheck() {
        if (availabilityCheckRunnable != null) {
            mainHandler.removeCallbacks(availabilityCheckRunnable);
            availabilityCheckRunnable = null;
        }
    }

    private void checkUsernameAvailability(String username) {
        setAvailabilityState(true, true); // loading state

        profileRepository.isUsernameAvailable(username, new ResultCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean available) {
                mainHandler.post(() -> {
                    isAvailable = available;
                    setAvailabilityState(false, available);
                    updateSaveButton();
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    isAvailable = false;
                    setAvailabilityState(false, false);
                    Toast.makeText(ChangeUsernameActivity.this, message, Toast.LENGTH_SHORT).show();
                    updateSaveButton();
                });
            }
        });
    }

    private void setAvailabilityState(boolean loading, boolean available) {
        if (loading) {
            availabilityProgress.setVisibility(View.VISIBLE);
            checkAvailability.setVisibility(View.GONE);
        } else {
            availabilityProgress.setVisibility(View.GONE);
            checkAvailability.setVisibility(View.VISIBLE);
            updateCheckIcon(checkAvailability, available);
        }
    }

    private void updateSaveButton() {
        boolean allCriteriaMet = isUsernameValid && !usernameInput.getText().toString().trim().equals(currentUsername);
        
        if (allCriteriaMet) {
            saveButton.setEnabled(true);
            saveButton.setClickable(true);
            saveButton.setBackgroundResource(R.drawable.button_enabled_bg);
            saveButtonText.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            saveButton.setEnabled(false);
            saveButton.setClickable(false);
            saveButton.setBackgroundResource(R.drawable.button_disabled_bg);
            saveButtonText.setTextColor(ContextCompat.getColor(this, R.color.gray));
        }
    }

    private void saveNewUsername(String newUsername) {
        saveButton.setEnabled(false);
        saveButtonText.setText("Saving...");
        usernameInput.setEnabled(false);

        profileRepository.updateUsername(newUsername, new ResultCallback<SupabaseUser>() {
            @Override
            public void onSuccess(SupabaseUser user) {
                saveButton.setEnabled(true);
                saveButtonText.setText("Save");
                usernameInput.setEnabled(true);
                Toast.makeText(ChangeUsernameActivity.this, "Username updated successfully", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                saveButton.setEnabled(true);
                saveButtonText.setText("Save");
                usernameInput.setEnabled(true);
                
                if (message.toLowerCase().contains("unique") || message.toLowerCase().contains("already")) {
                    setAvailabilityState(false, false);
                    updateSaveButton();
                }
                
                Toast.makeText(ChangeUsernameActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelAvailabilityCheck();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
