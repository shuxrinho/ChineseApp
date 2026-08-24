package com.example.chineseapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chineseapp.Helpers.CustomButton;
import com.example.chineseapp.WordLists.ListRepository;
import com.example.chineseapp.WordLists.WordItem;

import java.util.ArrayList;
import java.util.List;

public class HandwritingActivity extends AppCompatActivity {
    ImageView backImg;
    CustomButton radicals, hanzi, structures, lists;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_handwriting);

        backImg = findViewById(R.id.back_img);
        radicals = findViewById(R.id.radicals_practice);
        hanzi = findViewById(R.id.hanzi_practice);
        structures = findViewById(R.id.structures_practice);
        lists = findViewById(R.id.word_list_practice);

        backImg.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        radicals.setOnClickListener(v -> {
            Intent intent = new Intent(this, ListActivity.class);
            intent.putExtra("list_index", 108);
            startActivity(intent);
        });

        hanzi.setOnClickListener(v -> {
            Intent intent = new Intent(this, ListActivity.class);
            intent.putExtra("list_index", 109);
            startActivity(intent);
        });

        structures.setOnClickListener(v -> {
            Intent intent = new Intent(this, ListActivity.class);
            intent.putExtra("list_index", 110);
            startActivity(intent);
        });

        lists.setOnClickListener(v -> openWordListPracticePicker());
    }

    private void openWordListPracticePicker() {
        int count = ListRepository.getListCount(this);
        if (count == 0) {
            Toast.makeText(this, "You do not have any lists yet", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Integer> indexes = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            List<WordItem> words = ListRepository.loadList(this, i);
            if (words == null || words.isEmpty()) {
                continue;
            }
            indexes.add(i);
            labels.add(ListRepository.getListTitle(this, i) + " (" + words.size() + " words)");
        }

        if (indexes.isEmpty()) {
            Toast.makeText(this, "All your lists are empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Choose a list to practice")
                .setItems(items, (dialog, which) -> {
                    int listIndex = indexes.get(which);
                    Intent intent = new Intent(this, WordListPracticeActivity.class);
                    intent.putExtra("list_index", listIndex);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
