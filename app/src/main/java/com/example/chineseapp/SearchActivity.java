package com.example.chineseapp;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chineseapp.Dictionary.DictionaryEntry;
import com.example.chineseapp.Helpers.CustomButton;
import com.example.chineseapp.WordLists.ListRepository;
import com.example.chineseapp.WordLists.WordItem;
import com.example.chineseapp.supabase.ResultCallback;
import com.example.chineseapp.supabase.SupabaseDataRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private SupabaseDataRepository repository;
    private LinearLayout container;
    private TextView statusText;
    private View operationOverlay;
    private TextView operationOverlayText;
    private EditText searchBar;
    private CustomButton goButton;

    private int activeSearchToken = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageView back = findViewById(R.id.back_img);
        back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        repository = new SupabaseDataRepository(this);
        container = findViewById(R.id.words_container);
        statusText = findViewById(R.id.status_text);
        operationOverlay = findViewById(R.id.operation_overlay);
        operationOverlayText = findViewById(R.id.operation_overlay_text);
        searchBar = findViewById(R.id.search_bar);
        goButton = findViewById(R.id.go_icon);

        statusText.setVisibility(View.GONE);
        goButton.setVisibility(View.INVISIBLE);

        searchBar.requestFocus();
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT);

        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                findWords(searchBar.getText().toString());
                return true;
            }
            return false;
        });

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                goButton.setVisibility(s.length() == 0 ? View.INVISIBLE : View.VISIBLE);
            }
        });

        goButton.setOnClickListener(v -> findWords(searchBar.getText().toString()));
    }

    private void findWords(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            container.removeAllViews();
            statusText.setVisibility(View.GONE);
            return;
        }

        int requestToken = ++activeSearchToken;
        setOverlayLoading(true, "searching...");
        statusText.setVisibility(View.GONE);

        repository.searchWords(trimmed, new ResultCallback<>() {
            @Override
            public void onSuccess(List<WordItem> value) {
                if (requestToken != activeSearchToken) return;

                List<DictionaryEntry> entries = new ArrayList<>();
                for (WordItem item : value) {
                    DictionaryEntry entry = new DictionaryEntry();
                    entry.id = item.id;
                    entry.simplified = item.hanzi;
                    entry.pinyin = item.pinyin;
                    entry.english = item.meaning;
                    entries.add(entry);
                }

                sortByHanziLength(entries);
                populateWords(entries);
                setOverlayLoading(false, null);
            }

            @Override
            public void onError(String message) {
                if (requestToken != activeSearchToken) return;
                setOverlayLoading(false, null);
                statusText.setText(message);
                statusText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setOverlayLoading(boolean show, String message) {
        operationOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && message != null) {
            operationOverlayText.setText(message);
        }
        goButton.setEnabled(!show);
        searchBar.setEnabled(!show);
    }

    private void populateWords(List<DictionaryEntry> words) {
        container.removeAllViews();

        if (words.isEmpty()) {
            statusText.setText("No results found");
            statusText.setVisibility(View.VISIBLE);
            return;
        }

        final int maxToRender = 80;
        int count = Math.min(words.size(), maxToRender);
        if (words.size() > maxToRender) {
            statusText.setText("Showing top " + maxToRender + " matches");
            statusText.setVisibility(View.VISIBLE);
        } else {
            statusText.setVisibility(View.GONE);
        }

        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < count; i++) {
            DictionaryEntry entry = words.get(i);

            View item = inflater.inflate(R.layout.word_item, container, false);

            ((TextView) item.findViewById(R.id.hanzi)).setText(entry.simplified);
            ((TextView) item.findViewById(R.id.pinyin)).setText(cleanPinyin(entry.pinyin));
            ((TextView) item.findViewById(R.id.translation)).setText(cleanEnglish(entry.english));

            item.findViewById(R.id.add_button).setOnClickListener(v -> add_word(entry, inflater));
            item.findViewById(R.id.practice_button).setVisibility(View.GONE);

            container.addView(item);
        }
    }

    private void add_word(DictionaryEntry entry, LayoutInflater inflater) {
        if (ListRepository.getListCount(this) == 0) {
            Toast.makeText(this, "You do not have any lists!", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.add_wordtolist_bottom_dialog, null);

        dialog.setContentView(sheetView);
        dialog.show();

        TextView cancel = sheetView.findViewById(R.id.cancel_button);
        TextView confirm = sheetView.findViewById(R.id.confirm_button);
        LinearLayout listsParent = sheetView.findViewById(R.id.lists_container);

        cancel.setOnClickListener(v -> dialog.dismiss());

        int listCount = ListRepository.getListCount(this);
        for (int i = 0; i < listCount; ++i) {
            View list = inflater.inflate(R.layout.add_list_view, listsParent, false);

            TextView title = list.findViewById(R.id.list_name);
            RadioButton check = list.findViewById(R.id.check_button);

            check.setChecked(ListRepository.containsWord(this, i + 1, entry.id));
            title.setText(ListRepository.getListTitle(this, i + 1));

            RadioButton finalCheck = check;
            list.setOnClickListener(v -> finalCheck.setChecked(!finalCheck.isChecked()));

            listsParent.addView(list);
        }

        confirm.setOnClickListener(v -> {
            for (int i = 0; i < listCount; ++i) {
                boolean shouldHaveWord = ((RadioButton) listsParent.getChildAt(i)
                        .findViewById(R.id.check_button)).isChecked();

                if (shouldHaveWord) {
                    WordItem toAdd = new WordItem(entry.id, entry.simplified, entry.pinyin, entry.english);
                    if (!ListRepository.addWordToList(this, i + 1, toAdd)) {
                        Toast.makeText(this, "Word already exists in list!", Toast.LENGTH_SHORT).show();
                    }
                } else if (ListRepository.containsWord(this, i + 1, entry.id)) {
                    ListRepository.removeWordFromList(this, i + 1, entry.id);
                }
            }
            dialog.dismiss();
        });
    }

    private String cleanEnglish(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim();
        if (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        if (cleaned.endsWith("/")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned;
    }

    private String cleanPinyin(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[1-5]", "");
    }

    private void sortByHanziLength(List<DictionaryEntry> list) {
        Collections.sort(list, (a, b) -> {
            int lenA = a.simplified == null ? 0 : a.simplified.length();
            int lenB = b.simplified == null ? 0 : b.simplified.length();
            return Integer.compare(lenA, lenB);
        });
    }
}
