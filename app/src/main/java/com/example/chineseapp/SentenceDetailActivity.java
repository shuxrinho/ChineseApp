package com.example.chineseapp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SentenceDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sentence_detail);

        String mandarin = getIntent().getStringExtra("mandarin");
        String pinyin = getIntent().getStringExtra("pinyin");
        String english = getIntent().getStringExtra("english");
        int hsk = getIntent().getIntExtra("hsk", 0);

        ((TextView) findViewById(R.id.mandarin)).setText(mandarin);
        ((TextView) findViewById(R.id.pinyin)).setText(pinyin);
        ((TextView) findViewById(R.id.english)).setText(english);
        ((TextView) findViewById(R.id.hsk)).setText("HSK Level: " + hsk);
    }
}