package com.example.chineseapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chineseapp.supabase.ResultCallback;
import com.example.chineseapp.supabase.SessionManager;
import com.example.chineseapp.supabase.SupabaseAuthRepository;
import com.example.chineseapp.supabase.SupabaseUser;

public class SignupActivity extends AppCompatActivity {

    private static final String TAG_SIGNUP_UI = "CHAPP_SIGNUP_UI";
    private SupabaseAuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        authRepository = new SupabaseAuthRepository(this);

        EditText usernameInput = findViewById(R.id.username_input);
        EditText emailInput = findViewById(R.id.email_input);
        EditText passwordInput = findViewById(R.id.password_input);
        EditText confirmInput = findViewById(R.id.confirm_password_input);
        Button signupButton = findViewById(R.id.signup_button);
        TextView loginLink = findViewById(R.id.login_link);

        loginLink.setOnClickListener(v -> finish());

        signupButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            String confirm = confirmInput.getText().toString().trim();
            Log.d(TAG_SIGNUP_UI, "Signup button pressed. email=" + email + ", username=" + username);

            if (TextUtils.isEmpty(username) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Log.d(TAG_SIGNUP_UI, "Validation failed: empty fields.");
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirm)) {
                Log.d(TAG_SIGNUP_UI, "Validation failed: passwords mismatch.");
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Log.d(TAG_SIGNUP_UI, "Validation failed: password length < 6.");
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            signupButton.setEnabled(false);
            Log.d(TAG_SIGNUP_UI, "Calling authRepository.signUp...");
            authRepository.signUp(email, password, username, new ResultCallback<>() {
                @Override
                public void onSuccess(SupabaseUser value) {
                    Log.d(TAG_SIGNUP_UI, "Signup success callback. userId=" + value.id + ", email=" + value.email + ", emailConfirmed=" + value.emailConfirmed);
                    signupButton.setEnabled(true);
                    
                    // When email confirmation is enabled, Supabase returns emailConfirmed=false
                    // and no session tokens. User must confirm email before logging in.
                    if (!value.emailConfirmed) {
                        Toast.makeText(
                                SignupActivity.this,
                                "Account created. Please check your email inbox (and spam folder) for the confirmation link. Click the link to activate your account, then log in.",
                                Toast.LENGTH_LONG
                        ).show();
                        startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                        finishAffinity();
                    } else {
                        // Email already confirmed (rare case with certain configurations)
                        Toast.makeText(SignupActivity.this, "Account created. You are now signed in.", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(SignupActivity.this, MainActivity.class));
                        finishAffinity();
                    }
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG_SIGNUP_UI, "Signup error callback: " + message);
                    signupButton.setEnabled(true);
                    Toast.makeText(SignupActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
