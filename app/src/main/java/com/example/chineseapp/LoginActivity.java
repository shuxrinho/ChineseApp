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
import com.example.chineseapp.supabase.SupabaseAuthRepository;
import com.example.chineseapp.supabase.SupabaseUser;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG_LOGIN_UI = "CHAPP_LOGIN_UI";
    private SupabaseAuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authRepository = new SupabaseAuthRepository(this);

        EditText emailInput = findViewById(R.id.email_input);
        EditText passwordInput = findViewById(R.id.password_input);
        Button loginButton = findViewById(R.id.login_button);
        TextView signupLink = findViewById(R.id.signup_link);

        signupLink.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));

        // Pre-fill email if coming from signup confirmation flow
        String confirmationEmail = getIntent().getStringExtra("CONFIRMATION_EMAIL");
        if (confirmationEmail != null && !confirmationEmail.isEmpty()) {
            emailInput.setText(confirmationEmail);
            Toast.makeText(this, "Please log in with your email to continue", Toast.LENGTH_LONG).show();
        }

        loginButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            Log.d(TAG_LOGIN_UI, "Login button pressed. email=" + email);

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Log.d(TAG_LOGIN_UI, "Validation failed: missing email or password.");
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            loginButton.setEnabled(false);
            Log.d(TAG_LOGIN_UI, "Calling authRepository.signIn...");
            authRepository.signIn(email, password, new ResultCallback<>() {
                @Override
                public void onSuccess(SupabaseUser value) {
                    Log.d(TAG_LOGIN_UI, "Login success callback. userId=" + value.id);
                    loginButton.setEnabled(true);
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finishAffinity();
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG_LOGIN_UI, "Login error callback: " + message);
                    loginButton.setEnabled(true);
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
