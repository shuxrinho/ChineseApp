package com.example.chineseapp;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.PatternsCompat;

import com.example.chineseapp.Helpers.CustomButton;
import com.example.chineseapp.supabase.ResultCallback;
import com.example.chineseapp.supabase.SessionManager;
import com.example.chineseapp.supabase.SupabaseAuthRepository;
import com.example.chineseapp.supabase.SupabaseUser;

/** Password-gated Supabase email change with an in-app six-digit email-change OTP. */
public class ChangeEmailActivity extends AppCompatActivity {
    private EditText passwordInput;
    private EditText emailInput;
    private EditText codeInput;
    private TextView currentEmail;
    private TextView heading;
    private TextView description;
    private TextView codeDestination;
    private TextView buttonText;
    private View emailSection;
    private View codeSection;
    private CustomButton continueButton;
    private SupabaseAuthRepository authRepository;
    private String requestedEmail = "";
    private boolean requestInFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_email);
        authRepository = new SupabaseAuthRepository(this);

        passwordInput = findViewById(R.id.current_password_input);
        emailInput = findViewById(R.id.new_email_input);
        codeInput = findViewById(R.id.confirmation_code_input);
        currentEmail = findViewById(R.id.current_email);
        heading = findViewById(R.id.change_email_heading);
        description = findViewById(R.id.change_email_description);
        codeDestination = findViewById(R.id.code_destination);
        buttonText = findViewById(R.id.change_email_button_text);
        emailSection = findViewById(R.id.new_email_section);
        codeSection = findViewById(R.id.code_section);
        continueButton = findViewById(R.id.change_email_button);

        currentEmail.setText(SessionManager.getEmail(this));
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        continueButton.setOnClickListener(v -> onPrimaryAction());
        animateIn(findViewById(R.id.change_email_card), 80L);
        animateIn(continueButton, 170L);
    }

    private void onPrimaryAction() {
        if (requestInFlight) return;
        if (requestedEmail.isEmpty()) {
            requestCode();
        } else {
            confirmCode();
        }
    }

    private void requestCode() {
        String password = passwordInput.getText().toString();
        String email = emailInput.getText().toString().trim().toLowerCase();
        if (password.isEmpty()) {
            passwordInput.setError("Enter your current password");
            passwordInput.requestFocus();
            return;
        }
        if (!PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email address");
            emailInput.requestFocus();
            return;
        }
        if (email.equalsIgnoreCase(SessionManager.getEmail(this))) {
            emailInput.setError("This is already your current email");
            return;
        }

        setLoading(true, "Sending code...");
        authRepository.requestEmailChange(password, email, new ResultCallback<>() {
            @Override
            public void onSuccess(Boolean value) {
                requestedEmail = email;
                passwordInput.setText("");
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordInput.setVisibility(View.GONE);
                emailSection.setVisibility(View.GONE);
                codeSection.setVisibility(View.VISIBLE);
                codeDestination.setText("We sent a 6-digit code to " + requestedEmail);
                heading.setText("Confirm your new email");
                description.setText("Enter the code to securely finish this change.");
                buttonText.setText("Confirm email");
                setLoading(false, null);
                animateIn(codeSection, 0L);
                codeInput.requestFocus();
            }

            @Override
            public void onError(String message) {
                setLoading(false, null);
                Toast.makeText(ChangeEmailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmCode() {
        String code = codeInput.getText().toString().trim();
        if (!code.matches("\\d{6}")) {
            codeInput.setError("Enter the 6-digit code");
            codeInput.requestFocus();
            return;
        }
        setLoading(true, "Confirming...");
        authRepository.confirmEmailChange(requestedEmail, code, new ResultCallback<>() {
            @Override
            public void onSuccess(SupabaseUser user) {
                setLoading(false, null);
                Toast.makeText(ChangeEmailActivity.this, "Email address updated.", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false, null);
                Toast.makeText(ChangeEmailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading, String label) {
        requestInFlight = loading;
        continueButton.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        emailInput.setEnabled(!loading);
        codeInput.setEnabled(!loading);
        if (loading) buttonText.setText(label);
        else if (requestedEmail.isEmpty()) buttonText.setText("Send confirmation code");
        else buttonText.setText("Confirm email");
    }

    private void animateIn(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(getResources().getDisplayMetrics().density * 18f);
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(320L)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
}
