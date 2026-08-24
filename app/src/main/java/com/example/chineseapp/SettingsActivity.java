package com.example.chineseapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.chineseapp.Helpers.CustomButton;
import com.example.chineseapp.supabase.SessionManager;

public class SettingsActivity extends AppCompatActivity {

    LinearLayout telegram_contact_tv, email_contact_tv;
    ImageView backButton, accountAvatar;
    CustomButton accountBtn, notifBtn, goalBtn;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        telegram_contact_tv = findViewById(R.id.telegram_contact_tv);
        email_contact_tv = findViewById(R.id.email_contact_tv);
        backButton = findViewById(R.id.back_img);
        accountAvatar = findViewById(R.id.account_avatar);
        accountBtn = findViewById(R.id.account_settings);
        notifBtn = findViewById(R.id.notifications_settings);
        goalBtn = findViewById(R.id.goal_settings);


        email_contact_tv.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:shuxrateshm10@gmail.com"));
            v.getContext().startActivity(intent);
        });

        telegram_contact_tv.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/EshmurodovShuxrat"));
            v.getContext().startActivity(intent);
        });

        backButton.setOnClickListener(v -> {
            finish();
        });

        accountBtn.setOnClickListener(v -> gotoActivity(ProfileActivity.class));
        notifBtn.setOnClickListener(v -> gotoActivity(NotifActivity.class));
//        goalBtn.setOnClickListener(v );  TEMP

    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAccountAvatar();
    }

    void gotoActivity(Class activity) {
        Intent intent = new Intent(this, activity);
        startActivity(intent);
    }

    private void loadAccountAvatar() {
        String avatarUrl = SessionManager.getAvatarUrl(this);
        if (avatarUrl.isEmpty()) {
            accountAvatar.setImageResource(R.drawable.ic_default_profile);
            return;
        }

        accountAvatar.setImageTintList(null);
        Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_default_profile)
                .error(R.drawable.ic_default_profile)
                .circleCrop()
                .into(accountAvatar);
    }
}
