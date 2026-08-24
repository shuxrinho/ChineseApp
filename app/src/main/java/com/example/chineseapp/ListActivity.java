package com.example.chineseapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chineseapp.Helpers.WordAdapter;
import com.example.chineseapp.WordLists.ListRepository;
import com.example.chineseapp.WordLists.WordItem;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private TextView listName;
    private RecyclerView recyclerView;
    private TextView emptyText;
    private EditText searchBar;
    private View searchContainer;
    private View operationOverlay;
    private TextView operationOverlayText;

    private int listIndex;
    private final List<WordItem> allItems = new ArrayList<>();
    private final List<WordItem> filteredItems = new ArrayList<>();
    private WordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        listIndex = getIntent().getIntExtra("list_index", 0);
        listName = findViewById(R.id.list_name);
        recyclerView = findViewById(R.id.recyclerView);
        emptyText = findViewById(R.id.empty_message);
        searchBar = findViewById(R.id.search_bar);
        searchContainer = findViewById(R.id.search_container);
        operationOverlay = findViewById(R.id.operation_overlay);
        operationOverlayText = findViewById(R.id.operation_overlay_text);

        ImageView back = findViewById(R.id.back_img);
        back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WordAdapter(this, filteredItems, listIndex);
        recyclerView.setAdapter(adapter);

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterWords(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        loadListData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void loadListData() {
        io.execute(() -> {
            List<WordItem> loaded;
            String title;

            if (listIndex == ListRepository.LIST_ALL_WORDS) {
                loaded = ListRepository.loadAllCustomWords(this);
                title = ListRepository.getPopularListName(this, listIndex);
            } else if (listIndex >= 100) {
                loaded = ListRepository.loadPopularList(this, listIndex);
                title = ListRepository.getPopularListName(this, listIndex);
            } else {
                loaded = ListRepository.loadList(this, listIndex);
                title = ListRepository.getListTitle(this, listIndex);
            }

            runOnUiThread(() -> {
                listName.setText(title);

                allItems.clear();
                filteredItems.clear();
                allItems.addAll(loaded);
                filteredItems.addAll(loaded);
                adapter.notifyDataSetChanged();

                if (allItems.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    searchContainer.setVisibility(View.GONE);
                } else {
                    emptyText.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    searchContainer.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void filterWords(String queryRaw) {
        String query = queryRaw.toLowerCase(Locale.US).trim();
        String normalizedPinyinQuery = normalizePinyinForSearch(query);

        filteredItems.clear();
        if (query.isEmpty()) {
            filteredItems.addAll(allItems);
        } else {
            for (WordItem item : allItems) {
                String hanzi = item.hanzi == null ? "" : item.hanzi;
                String pinyin = item.pinyin == null ? "" : item.pinyin.toLowerCase(Locale.US);
                String meaning = item.meaning == null ? "" : item.meaning.toLowerCase(Locale.US);

                if (hanzi.contains(query)
                        || pinyin.contains(query)
                        || meaning.contains(query)
                        || (!normalizedPinyinQuery.isEmpty()
                        && normalizePinyinForSearch(item.pinyin).contains(normalizedPinyinQuery))) {
                    filteredItems.add(item);
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    private String normalizePinyinForSearch(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.US)
                .replaceAll("[1-5]", "")
                .replaceAll("[^a-z]", "");
    }

    private void setOverlay(boolean show, String text) {
        operationOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && text != null) {
            operationOverlayText.setText(text);
        }
    }

    public void add_word(WordItem entry, LayoutInflater inflater) {
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
            dialog.dismiss();

            io.execute(() -> {
                for (int i = 0; i < listCount; ++i) {
                    boolean checked = ((RadioButton) listsParent.getChildAt(i).findViewById(R.id.check_button)).isChecked();
                    if (checked) {
                        WordItem toAdd = new WordItem(entry.id, entry.hanzi, entry.pinyin, entry.meaning);
                        ListRepository.addWordToList(this, i + 1, toAdd);
                    } else if (ListRepository.containsWord(this, i + 1, entry.id)) {
                        ListRepository.removeWordFromList(this, i + 1, entry.id);
                    }
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                    loadListData();
                });
            });
        });
    }
}
