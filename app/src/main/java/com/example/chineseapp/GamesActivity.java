package com.example.chineseapp;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chineseapp.Helpers.GameButtonModel;
import com.example.chineseapp.Helpers.GamesButtonAdapter;

import java.util.ArrayList;
import java.util.List;

public class GamesActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private GamesButtonAdapter adapter;
    private List<GameButtonModel> gameList;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_games);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        recyclerView = findViewById(R.id.games_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Create dummy data from string resources and refined descriptions
        gameList = new ArrayList<>();
        gameList.add(new GameButtonModel(
                getString(R.string.game_pinyin_title),
                getString(R.string.game_pinyin_description),
                getString(R.string.game_pinyin_start),
                true // Is Pinyin Game (for specific logic later if needed)
        ));
        // Add more games using your other string resources/refined descriptions
        gameList.add(new GameButtonModel(
                getString(R.string.game_radical_title),
                getString(R.string.game_radical_description),
                getString(R.string.game_radical_start),
                false
        ));
        gameList.add(new GameButtonModel(
                getString(R.string.game_tones_title),
                getString(R.string.game_tones_description),
                getString(R.string.game_tones_start),
                false
        ));
        // Add others as needed...

        adapter = new GamesButtonAdapter(this, gameList);
        recyclerView.setAdapter(adapter);
    }
}