package com.example.chineseapp;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chineseapp.Helpers.CustomContextMenu;
import com.example.chineseapp.Helpers.CustomButton;
import com.example.chineseapp.Helpers.PullSyncContainer;
import com.example.chineseapp.WordLists.ListRepository;
import com.example.chineseapp.supabase.UserDataSyncManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DictionaryActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private GridLayout customGrid;
    private GridLayout popularListsGrid;
    private CustomButton addButton;
    private CustomButton refreshButton;
    private View operationOverlay;
    private TextView operationOverlayText;
    private PullSyncContainer pullSyncContainer;
    private UserDataSyncManager syncManager;

    private final List<CustomListItem> customLists = new ArrayList<>();

    private static class CustomListItem {
        final int position;
        final String title;
        final String description;

        CustomListItem(int position, String title, String description) {
            this.position = position;
            this.title = title;
            this.description = description;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dictionary);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        customGrid = findViewById(R.id.grid_container);
        popularListsGrid = findViewById(R.id.popular_lists_grid);
        addButton = findViewById(R.id.add_button);
        refreshButton = findViewById(R.id.refresh_button);
        operationOverlay = findViewById(R.id.operation_overlay);
        operationOverlayText = findViewById(R.id.operation_overlay_text);
        pullSyncContainer = findViewById(R.id.pull_sync);
        LinearLayout topBar = findViewById(R.id.dictionary_top_bar);
        syncManager = new UserDataSyncManager(this);

        ImageView back = findViewById(R.id.back_img);
        back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        ImageView search = findViewById(R.id.search_icon);
        search.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));

        inflatePopularLists();
        addButton.setOnClickListener(v -> openCreateListDialog());
        refreshButton.setOnClickListener(v -> syncCustomLists());
        pullSyncContainer.setPullPermissionProvider(() -> true);
        topBar.post(() -> {
            pullSyncContainer.setIndicatorTopOffsetPx(topBar.getBottom() + dpInt(10f));
            pullSyncContainer.setPullStartMaxYpx(topBar.getBottom() + dpInt(24f));
        });
        pullSyncContainer.setSyncRequestListener(completion ->
                io.execute(() -> {
                    UserDataSyncManager.SyncResult result = syncManager.syncNow();
                    runOnUiThread(() -> {
                        completion.finish(result.success, result.message);
                        if (result.success) {
                            ListRepository.refreshCustomCache(this);
                            loadCustomLists();
                        }
                    });
                })
        );

        loadCustomLists();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void loadCustomLists() {
        io.execute(() -> {
            List<CustomListItem> loaded = new ArrayList<>();
            int count = ListRepository.getListCount(this);
            int maxItems = Math.min(count, 6);
            for (int i = 1; i <= maxItems; i++) {
                loaded.add(new CustomListItem(
                        i,
                        ListRepository.getListTitle(this, i),
                        ListRepository.getListDescription(this, i)
                ));
            }

            runOnUiThread(() -> {
                customLists.clear();
                customLists.addAll(loaded);
                renderCustomLists();
            });
        });
    }

    private void renderCustomLists() {
        customGrid.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        int[] colors = {
                R.color.list1, R.color.list2, R.color.list3,
                R.color.list4, R.color.list5
        };

        for (int i = 0; i < customLists.size(); i++) {
            CustomListItem item = customLists.get(i);

            int row = i / 3;
            int col = i % 3;

            View view = inflater.inflate(R.layout.main_lists_view, customGrid, false);
            ((TextView) view.findViewById(R.id.list_name)).setText(item.title);
            ((TextView) view.findViewById(R.id.list_desc)).setText(item.description);
            ((TextView) view.findViewById(R.id.list_number)).setText(String.valueOf(item.position));

            if (i < colors.length) {
                view.setBackgroundTintList(ContextCompat.getColorStateList(this, colors[i]));
            }
            int listPosition = item.position;
            view.setOnLongClickListener(v -> {
                showListMenu(v, listPosition);
                return true;
            });

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(row, 1f),
                    GridLayout.spec(col, 1f)
            );
            params.width = 0;
            params.setMargins(8, 8, 8, 8);
            view.setLayoutParams(params);

            view.setOnClickListener(v -> {
                Intent intent = new Intent(this, ListActivity.class);
                intent.putExtra("list_index", listPosition);
                startActivity(intent);
            });

            customGrid.addView(view);
        }

        if (customLists.size() >= 6) return;

        int addRow = customLists.size() / 3;
        int addCol = customLists.size() % 3;

        GridLayout.LayoutParams addParams = new GridLayout.LayoutParams(
                GridLayout.spec(addRow, 1f),
                GridLayout.spec(addCol, 1f)
        );
        addParams.width = 0;
        addParams.setMargins(8, 8, 8, 8);
        addButton.setLayoutParams(addParams);

        customGrid.addView(addButton);
    }

    private void showListMenu(View anchor, int listPosition) {
        CustomContextMenu.show(anchor, Arrays.asList(
                new CustomContextMenu.Option(
                        "Rename",
                        R.drawable.ic_edit,
                        () -> openRenameListDialog(listPosition)
                ),
                new CustomContextMenu.Option(
                        "Delete",
                        R.drawable.ic_delete,
                        () -> deleteList(listPosition)
                )
        ));
    }

    private void openCreateListDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.add_list_bottom_dialog, null);
        dialog.setContentView(sheetView);
        dialog.show();

        EditText etTitle = sheetView.findViewById(R.id.list_title);
        EditText etDesc = sheetView.findViewById(R.id.list_desc);
        CustomButton btnSave = sheetView.findViewById(R.id.btn_save);

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter title", Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            createList(title, desc);
        });
    }

    private void createList(String title, String description) {
        setOverlay(true, "Creating list...");
        io.execute(() -> {
            int position = ListRepository.addList(this, title, description);
            runOnUiThread(() -> {
                setOverlay(false, null);
                if (position == -1) {
                    Toast.makeText(this, "Maximum number of lists reached", Toast.LENGTH_SHORT).show();
                }
                loadCustomLists();
            });
        });
    }

    private void openRenameListDialog(int listPosition) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.add_list_bottom_dialog, null);
        dialog.setContentView(sheetView);
        dialog.show();

        EditText etTitle = sheetView.findViewById(R.id.list_title);
        EditText etDesc = sheetView.findViewById(R.id.list_desc);
        CustomButton btnSave = sheetView.findViewById(R.id.btn_save);

        etTitle.setText(ListRepository.getListTitle(this, listPosition));
        etDesc.setText(ListRepository.getListDescription(this, listPosition));

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter title", Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            renameList(listPosition, title, desc);
        });
    }

    private void renameList(int listPosition, String title, String description) {
        setOverlay(true, "Updating list...");
        io.execute(() -> {
            ListRepository.renameList(this, listPosition, title, description);
            runOnUiThread(() -> {
                setOverlay(false, null);
                loadCustomLists();
            });
        });
    }

    private void deleteList(int listPosition) {
        setOverlay(true, "Deleting list...");
        io.execute(() -> {
            ListRepository.removeList(this, listPosition);
            runOnUiThread(() -> {
                setOverlay(false, null);
                loadCustomLists();
            });
        });
    }

    private void syncCustomLists() {
        setOverlay(true, "Syncing custom lists...");
        io.execute(() -> {
            UserDataSyncManager.SyncResult result = syncManager.syncNow();
            runOnUiThread(() -> {
                setOverlay(false, null);
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
                if (result.success) {
                    ListRepository.refreshCustomCache(this);
                    loadCustomLists();
                }
            });
        });
    }

    private void setOverlay(boolean show, String text) {
        operationOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && text != null) {
            operationOverlayText.setText(text);
        }
    }

    private void inflatePopularLists() {
        LayoutInflater inflater = LayoutInflater.from(this);

        addPopularList(inflater, "HSK 1 Essentials", "#4CAF50", ListRepository.LIST_HSK1, 0);
        addPopularList(inflater, "Greetings & Introductions", "#2196F3", ListRepository.LIST_GREETINGS, 1);
        addPopularList(inflater, "Food & Restaurant", "#FF9800", ListRepository.LIST_FOOD, 2);
        addPopularList(inflater, "Travel & Transport", "#9C27B0", ListRepository.LIST_TRAVEL, 3);
        addPopularList(inflater, "Numbers & Time", "#F44336", ListRepository.LIST_NUMBERS, 4);
        addPopularList(inflater, "Family & Daily Life", "#607D8B", ListRepository.LIST_FAMILY, 5);
        addPopularList(inflater, "Radicals", "#795548", ListRepository.LIST_RADICALS, 6);
        addPopularList(inflater, "All Words", "#455A64", ListRepository.LIST_ALL_WORDS, 7);
    }

    private void addPopularList(LayoutInflater inflater, String title, String color, int listId, int i) {
        int row = i / 3;
        int col = i % 3;

        View view = inflater.inflate(R.layout.main_lists_view, popularListsGrid, false);

        ((TextView) view.findViewById(R.id.list_name)).setText(title);
        ((TextView) view.findViewById(R.id.list_number)).setText(String.valueOf(i + 1));

        view.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(color)));

        view.findViewById(R.id.list_desc).setVisibility(View.GONE);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(row, 1f),
                GridLayout.spec(col, 1f)
        );

        params.width = 0;
        params.setMargins(8, 8, 8, 8);
        view.setLayoutParams(params);

        view.setOnClickListener(v -> {
            Intent intent = new Intent(this, ListActivity.class);
            intent.putExtra("list_index", listId);
            startActivity(intent);
        });

        popularListsGrid.addView(view);
    }

    private int dpInt(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
