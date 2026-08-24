package com.example.chineseapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chineseapp.Sentences.*;
import com.example.chineseapp.supabase.ResultCallback;
import com.example.chineseapp.supabase.SupabaseDataRepository;
import java.util.List;
import java.util.Set;

public class SentencesActivity extends AppCompatActivity {

    private SupabaseDataRepository sentenceRepo;
    private LinearLayout container;
    private EditText searchEdit;
    private Handler handler = new Handler();
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sentences);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sentenceRepo = new SupabaseDataRepository(this);
        container = findViewById(R.id.sentences_container);
        searchEdit = findViewById(R.id.search_edit);

        findViewById(R.id.back_img).setOnClickListener(v -> finish());

        // Top-right favourites button
        findViewById(R.id.favourites_btn).setOnClickListener(v ->
                startActivity(new Intent(this, FavouriteSentencesActivity.class))
        );

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (searchRunnable != null) handler.removeCallbacks(searchRunnable);
                searchRunnable = () -> performSearch(s.toString());
                handler.postDelayed(searchRunnable, 300);
            }
        });

        performSearch("");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!searchEdit.getText().toString().isEmpty())
            performSearch(searchEdit.getText().toString());
    }

    private void performSearch(String query) {
        sentenceRepo.searchSentences(query, new ResultCallback<>() {
            @Override
            public void onSuccess(List<SentenceEntry> results) {
                sentenceRepo.getFavouriteSentenceIds(new ResultCallback<>() {
                    @Override
                    public void onSuccess(Set<Long> favouriteIds) {
                        container.removeAllViews();
                        LayoutInflater inflater = LayoutInflater.from(SentencesActivity.this);

                        for (SentenceEntry entry : results) {
                            View itemView = inflater.inflate(R.layout.sentence_item, container, false);

                            ((TextView) itemView.findViewById(R.id.mandarin)).setText(entry.mandarin);
                            ((TextView) itemView.findViewById(R.id.pinyin)).setText(entry.pinyin);
                            ((TextView) itemView.findViewById(R.id.english)).setText(entry.english);

                            ImageView star = itemView.findViewById(R.id.star_button);
                            boolean isFav = favouriteIds.contains((long) entry.id);
                            star.setImageResource(isFav ? R.drawable.ic_star_filled : R.drawable.ic_star_empty);

                            star.setOnClickListener(v -> {
                                boolean newValue = !favouriteIds.contains((long) entry.id);
                                sentenceRepo.setSentenceFavourite(entry.id, newValue, new ResultCallback<>() {
                                    @Override
                                    public void onSuccess(Boolean value) {
                                        if (newValue) {
                                            favouriteIds.add((long) entry.id);
                                            star.setImageResource(R.drawable.ic_star_filled);
                                        } else {
                                            favouriteIds.remove((long) entry.id);
                                            star.setImageResource(R.drawable.ic_star_empty);
                                        }
                                    }

                                    @Override
                                    public void onError(String message) {
                                        // keep current icon
                                    }
                                });
                            });

                            container.addView(itemView);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        container.removeAllViews();
                    }
                });
            }

            @Override
            public void onError(String message) {
                container.removeAllViews();
            }
        });
    }
}
