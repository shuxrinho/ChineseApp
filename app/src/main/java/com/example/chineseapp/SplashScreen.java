package com.example.chineseapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chineseapp.supabase.SessionManager;
import com.example.chineseapp.supabase.SupabaseClient;

public class SplashScreen extends AppCompatActivity {

    private boolean launched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash_screen);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (!SupabaseClient.hasConfig()) {
            launchOnce(LoginActivity.class);
            return;
        }

        if (!SessionManager.isLoggedIn(this)) {
            launchOnce(LoginActivity.class);
            return;
        }

        launchOnce(MainActivity.class);
    }

    private void launchOnce(Class<?> target) {
        if (launched) return;
        launched = true;
        startActivity(new Intent(this, target));
        finish();
    }
}
