package com.example.chineseapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FavouriteSentencesActivity extends AppCompatActivity {

    private SupabaseDataRepository sentenceRepo;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_favourite_sentences);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sentenceRepo = new SupabaseDataRepository(this);
        container = findViewById(R.id.favourites_container);

        findViewById(R.id.back_img).setOnClickListener(v -> finish());

        loadFavourites();
    }

    private void loadFavourites() {
        sentenceRepo.getFavouriteSentenceIds(new ResultCallback<>() {
            @Override
            public void onSuccess(Set<Long> favIds) {
                if (favIds.isEmpty()) {
                    showEmpty();
                    return;
                }

                sentenceRepo.getSentencesByIds(favIds, new ResultCallback<>() {
                    @Override
                    public void onSuccess(List<SentenceEntry> finalFavourites) {
                        container.removeAllViews();
                        LayoutInflater inflater = LayoutInflater.from(FavouriteSentencesActivity.this);

                        if (finalFavourites.isEmpty()) {
                            showEmpty();
                            return;
                        }

                        for (SentenceEntry entry : finalFavourites) {
                            View itemView = inflater.inflate(R.layout.sentence_item, container, false);

                            ((TextView) itemView.findViewById(R.id.mandarin)).setText(entry.mandarin);
                            ((TextView) itemView.findViewById(R.id.pinyin)).setText(entry.pinyin);
                            ((TextView) itemView.findViewById(R.id.english)).setText(entry.english);

                            ImageView star = itemView.findViewById(R.id.star_button);
                            star.setImageResource(R.drawable.ic_star_filled);

                            star.setOnClickListener(v -> {
                                sentenceRepo.setSentenceFavourite(entry.id, false, new ResultCallback<>() {
                                    @Override
                                    public void onSuccess(Boolean value) {
                                        container.removeView(itemView);
                                        if (container.getChildCount() == 0) {
                                            showEmpty();
                                        }
                                    }

                                    @Override
                                    public void onError(String message) {
                                        // no-op
                                    }
                                });
                            });

                            container.addView(itemView);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        showEmpty();
                    }
                });
            }

            @Override
            public void onError(String message) {
                showEmpty();
            }
        });
    }

    private void showEmpty() {
        container.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText("No favourite sentences yet");
        empty.setTextSize(18f);
        empty.setPadding(0, 60, 0, 60);
        empty.setGravity(android.view.Gravity.CENTER);
        container.addView(empty);
    }
}
