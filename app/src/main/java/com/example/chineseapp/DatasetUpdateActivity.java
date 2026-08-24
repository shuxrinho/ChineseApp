package com.example.chineseapp;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

public class DatasetUpdateActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dataset_update);

        ImageView back = findViewById(R.id.back_img);
        back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }
}
