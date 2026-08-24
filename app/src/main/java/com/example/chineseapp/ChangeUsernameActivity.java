package com.example.chineseapp;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chineseapp.supabase.ResultCallback;
import com.example.chineseapp.supabase.SessionManager;
import com.example.chineseapp.supabase.SupabaseUser;

public class ChangeUsernameActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_change_username);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        private void updateUsername(String username, AlertDialog dialog, EditText input, TextView
        saveButton) {
            if (usernameUpdateInProgress) return;
            Log.d(TAG_PROFILE, "Starting username update. usernameLength=" + username.length());
            usernameUpdateInProgress = true;
            changeUsername.setEnabled(false);
            input.setEnabled(false);
            saveButton.setEnabled(false);
            saveButton.setText("Saving...");

            profileRepository.updateUsername(username, new ResultCallback<>() {
                @Override
                public void onSuccess(SupabaseUser user) {
                    Log.d(TAG_PROFILE, "Username update succeeded. userId=" + (user == null ? "" : user.id));
                    usernameUpdateInProgress = false;
                    changeUsername.setEnabled(true);
                    dialog.dismiss();
                    renderUser(user);
                    Toast.makeText(ProfileActivity.this, "Username updated.", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG_PROFILE, "Username update failed: " + message);
                    usernameUpdateInProgress = false;
                    changeUsername.setEnabled(true);
                    input.setEnabled(true);
                    saveButton.setEnabled(true);
                    saveButton.setText("Save");
                    input.setError(message);
                    Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void showUsernameDialog() {
        SupabaseUser current = SessionManager.getUser(this);
        String currentUsername = current == null ? "" : safe(current.username);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        input.setText(currentUsername);
        input.setSelectAllOnFocus(true);
        input.setHint("username");
        input.setTextColor(0xFF102118);
        input.setHintTextColor(0xFF7E9689);
        input.setBackgroundTintList(ColorStateList.valueOf(0xFF1FA76D));

        FrameLayout wrapper = new FrameLayout(this);
        int horizontalPadding = dpInt(22f);
        int topPadding = dpInt(8f);
        wrapper.setPadding(horizontalPadding, topPadding, horizontalPadding, 0);
        wrapper.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Change username")
                .setMessage("Use 3-24 letters, numbers, or underscores.")
                .setView(wrapper)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(d -> {
            TextView saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setTextColor(0xFF0B8F46);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF607568);

            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String value = safe(s == null ? "" : s.toString());
                    saveButton.setEnabled(isUsernameValid(value) && !value.equals(currentUsername));
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };
            input.addTextChangedListener(watcher);
            watcher.onTextChanged(input.getText(), 0, 0, input.length());

            saveButton.setOnClickListener(v -> {
                String nextUsername = safe(input.getText().toString());
                if (!isUsernameValid(nextUsername)) {
                    input.setError("Use 3-24 letters, numbers, or underscores.");
                    return;
                }
                if (nextUsername.equals(currentUsername)) {
                    dialog.dismiss();
                    return;
                }
                updateUsername(nextUsername, dialog, input, saveButton);
            });
        });

        dialog.setOnDismissListener(d -> {
            if (!usernameUpdateInProgress) {
                changeUsername.setEnabled(true);
            }
        });
        dialog.show();
        input.requestFocus();
    }
}