package com.example.chineseapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chineseapp.Helpers.CharacterDecomposer;
import com.example.chineseapp.Helpers.HanziStructureDecomposer;
import com.example.chineseapp.Helpers.RadicalDatabase;
import com.example.chineseapp.Helpers.StructurePanelView;
import com.example.chineseapp.Helpers.TextSanitizer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StructurePracticeActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_HANZI = "hanzi";
    public static final String EXTRA_PINYIN = "pinyin";
    public static final String EXTRA_ENGLISH = "english";
    public static final String EXTRA_LIST_INDEX = "list_index";

    private ImageView btnBack;
    private TextView tvBuildTitle;
    private TextView tvSubTitle;
    private TextView tvHint;
    private StructurePanelView structurePanel;
    private GridLayout radicalsGrid;
    private LinearLayout selectedComponentsContainer;
    private Button btnConfirmConstruction;

    private String wordId;
    private String fullHanzi;
    private String pinyin;
    private String english;
    private int sourceListIndex = -1;

    private String[] hanziChars;
    private int structureCharIndex = 0;
    private String targetChar;

    private final List<RadicalOption> options = new ArrayList<>();
    private final Set<Integer> selectedIndices = new LinkedHashSet<>();
    private final Map<String, Integer> requiredCounts = new LinkedHashMap<>();
    private final Map<Integer, OptionView> optionViews = new HashMap<>();
    private final Map<Integer, TextView> selectedTokens = new HashMap<>();

    private int loadVersion = 0;

    private static final String[] DECOY_POOL = {
            "\u706b", "\u571f", "\u91d1", "\u5c71", "\u53e3", "\u624b", "\u5fc3", "\u76ee", "\u8033", "\u8db3",
            "\u95e8", "\u9a6c", "\u9c7c", "\u7530", "\u529b", "\u738b", "\u5927", "\u77f3", "\u767d", "\u7af9",
            "\u4eba", "\u5b50", "\u5973", "\u8a00", "\u6708", "\u65e5", "\u6728", "\u6c34"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_structure_practice);

        wordId = getIntent().getStringExtra(EXTRA_ID);
        fullHanzi = getIntent().getStringExtra(EXTRA_HANZI);
        pinyin = getIntent().getStringExtra(EXTRA_PINYIN);
        english = getIntent().getStringExtra(EXTRA_ENGLISH);
        sourceListIndex = getIntent().getIntExtra(EXTRA_LIST_INDEX, -1);

        bindViews();
        parseCharacters();

        btnBack.setOnClickListener(v -> finish());
        btnConfirmConstruction.setOnClickListener(v -> onConfirmPressed());

        loadCurrentCharacter();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        tvBuildTitle = findViewById(R.id.tvBuildTitle);
        tvSubTitle = findViewById(R.id.tvSubTitle);
        tvHint = findViewById(R.id.tvHint);
        structurePanel = findViewById(R.id.structurePanel);
        radicalsGrid = findViewById(R.id.radicalsGrid);
        selectedComponentsContainer = findViewById(R.id.selectedComponentsContainer);
        btnConfirmConstruction = findViewById(R.id.btnConfirmConstruction);
    }

    private void parseCharacters() {
        if (fullHanzi == null || fullHanzi.isEmpty()) {
            hanziChars = new String[]{"?"};
            targetChar = "?";
            return;
        }

        hanziChars = new String[fullHanzi.codePointCount(0, fullHanzi.length())];
        int idx = 0;
        for (int cp, i = 0; i < fullHanzi.length(); i += Character.charCount(cp)) {
            cp = fullHanzi.codePointAt(i);
            hanziChars[idx++] = new String(Character.toChars(cp));
        }
        targetChar = hanziChars[0];
    }

    private void loadCurrentCharacter() {
        targetChar = hanziChars[Math.max(0, Math.min(structureCharIndex, hanziChars.length - 1))];

        populateHeader();
        clearSelectionUI();
        structurePanel.setStructure(null, Collections.emptyList());
        structurePanel.setFilledCounts(Collections.emptyMap());
        setConfirmEnabled(false);

        int currentVersion = ++loadVersion;
        String charForLoad = targetChar;

        new Thread(() -> {
            List<String> rawSvg = new ArrayList<>();
            List<List<int[]>> rawMedians = new ArrayList<>();
            loadStrokeData(charForLoad, rawSvg, rawMedians);

            Map<String, Integer> localRequired = new LinkedHashMap<>();
            HanziStructureDecomposer.Result result = null;

            if (!rawMedians.isEmpty()) {
                RadicalDatabase db = RadicalDatabase.getInstance(this);
                CharacterDecomposer.CharacterEntry entry =
                        new CharacterDecomposer.CharacterEntry(charForLoad, rawSvg, rawMedians);
                HanziStructureDecomposer decomposer = new HanziStructureDecomposer(db);
                result = decomposer.decompose(entry);
                localRequired.putAll(result.requiredRadicalCounts());
            }

            if (localRequired.isEmpty()) {
                localRequired.put(charForLoad, 1);
            }

            HanziStructureDecomposer.Result finalResult = result;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (currentVersion != loadVersion) {
                    return;
                }

                requiredCounts.clear();
                requiredCounts.putAll(localRequired);

                if (finalResult != null) {
                    structurePanel.setStructure(finalResult.root, finalResult.leaves);
                } else {
                    structurePanel.setStructure(null, Collections.emptyList());
                }

                buildOptions();
                renderOptions();
                updatePanelHighlights();
            });
        }).start();
    }

    private void populateHeader() {
        String cleanPinyin = TextSanitizer.cleanPinyin(pinyin);
        String cleanMeaning = TextSanitizer.cleanMeaning(english);

        tvBuildTitle.setText("Build the hanzi");
        tvSubTitle.setText(targetChar + "   " + cleanPinyin + "   " + cleanMeaning);

        if (hanziChars.length > 1) {
            tvHint.setText("Structure " + (structureCharIndex + 1) + " of " + hanziChars.length);
        } else {
            tvHint.setText("Select components and place them on the panel");
        }
    }

    private void loadStrokeData(String target, List<String> rawSvg, List<List<int[]>> rawMedians) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open("graphics.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject obj = new JSONObject(line.trim());
                if (!obj.getString("character").equals(target)) {
                    continue;
                }

                JSONArray strokesArr = obj.getJSONArray("strokes");
                JSONArray mediansArr = obj.getJSONArray("medians");

                for (int i = 0; i < strokesArr.length(); i++) {
                    rawSvg.add(strokesArr.getString(i));
                    JSONArray medianStroke = mediansArr.getJSONArray(i);
                    List<int[]> points = new ArrayList<>();
                    for (int j = 0; j < medianStroke.length(); j++) {
                        JSONArray point = medianStroke.getJSONArray(j);
                        points.add(new int[]{(int) point.getDouble(0), (int) point.getDouble(1)});
                    }
                    rawMedians.add(points);
                }
                return;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void buildOptions() {
        options.clear();
        selectedIndices.clear();
        optionViews.clear();
        selectedTokens.clear();

        int seq = 0;
        for (Map.Entry<String, Integer> entry : requiredCounts.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                options.add(new RadicalOption(seq++, entry.getKey(), true));
            }
        }

        int targetOptionCount = Math.max(3, Math.min(9, options.size() + 3));
        List<String> decoys = new ArrayList<>(Arrays.asList(DECOY_POOL));
        Collections.shuffle(decoys);

        Map<String, Integer> alreadyPresent = new HashMap<>(requiredCounts);
        for (String d : decoys) {
            if (options.size() >= targetOptionCount) {
                break;
            }
            if (alreadyPresent.containsKey(d)) {
                continue;
            }
            options.add(new RadicalOption(seq++, d, false));
            alreadyPresent.put(d, 1);
        }

        Collections.shuffle(options);
    }

    private void renderOptions() {
        radicalsGrid.removeAllViews();
        selectedComponentsContainer.removeAllViews();
        optionViews.clear();
        selectedTokens.clear();

        int columns = options.size() <= 4 ? 2 : 3;
        radicalsGrid.setColumnCount(columns);

        for (int i = 0; i < options.size(); i++) {
            RadicalOption option = options.get(i);
            OptionView optionView = createOptionView(option.radical, i);
            optionViews.put(i, optionView);
            radicalsGrid.addView(optionView.card);
        }

        setConfirmEnabled(false);
    }

    private OptionView createOptionView(String radical, int index) {
        FrameLayout card = new FrameLayout(this);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(86);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        card.setLayoutParams(lp);
        card.setBackgroundResource(R.drawable.bg_radical_card);
        card.setClickable(true);
        card.setFocusable(true);

        TextView radicalText = new TextView(this);
        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        radicalText.setLayoutParams(textLp);
        radicalText.setText(radical);
        radicalText.setTextSize(42f);
        radicalText.setTextColor(0xFF81C784);
        radicalText.setTypeface(android.graphics.Typeface.SERIF);
        card.addView(radicalText);

        card.setOnClickListener(v -> toggleOption(index));
        return new OptionView(card, radicalText);
    }

    private void toggleOption(int index) {
        if (selectedIndices.contains(index)) {
            deselectOption(index);
        } else {
            selectOption(index);
        }
        updatePanelHighlights();
        setConfirmEnabled(isSelectionCorrect());
    }

    private void selectOption(int index) {
        OptionView optionView = optionViews.get(index);
        if (optionView == null) {
            return;
        }

        selectedIndices.add(index);
        optionView.card.setVisibility(View.GONE);

        TextView token = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        token.setLayoutParams(lp);
        token.setBackgroundResource(R.drawable.bg_radical_card_selected);
        token.setPadding(dp(14), dp(8), dp(14), dp(8));
        token.setText(options.get(index).radical);
        token.setTextSize(34f);
        token.setTypeface(android.graphics.Typeface.SERIF);
        token.setTextColor(0xFF0A1A0F);
        token.setClickable(true);
        token.setFocusable(true);
        token.setOnClickListener(v -> {
            deselectOption(index);
            updatePanelHighlights();
            setConfirmEnabled(isSelectionCorrect());
        });

        selectedTokens.put(index, token);
        selectedComponentsContainer.addView(token);
    }

    private void deselectOption(int index) {
        OptionView optionView = optionViews.get(index);
        if (optionView == null) {
            return;
        }

        selectedIndices.remove(index);
        optionView.card.setVisibility(View.VISIBLE);

        TextView token = selectedTokens.remove(index);
        if (token != null) {
            selectedComponentsContainer.removeView(token);
        }
    }

    private void clearSelectionUI() {
        selectedIndices.clear();
        selectedTokens.clear();
        if (selectedComponentsContainer != null) {
            selectedComponentsContainer.removeAllViews();
        }
    }

    private void updatePanelHighlights() {
        structurePanel.setFilledCounts(selectedCounts());
    }

    private Map<String, Integer> selectedCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int idx : selectedIndices) {
            String radical = options.get(idx).radical;
            counts.put(radical, counts.getOrDefault(radical, 0) + 1);
        }
        return counts;
    }

    private boolean isSelectionCorrect() {
        return selectedCounts().equals(requiredCounts);
    }

    private void setConfirmEnabled(boolean enabled) {
        btnConfirmConstruction.setEnabled(enabled);
        btnConfirmConstruction.setAlpha(enabled ? 1f : 0.45f);
    }

    private void onConfirmPressed() {
        if (!isSelectionCorrect()) {
            Toast.makeText(this, "Select and place the exact components", Toast.LENGTH_SHORT).show();
            return;
        }

        if (structureCharIndex < hanziChars.length - 1) {
            structureCharIndex++;
            loadCurrentCharacter();
            return;
        }

        proceedToPractice();
    }

    private void proceedToPractice() {
        Intent intent = new Intent(this, PracticeWordActivity.class);
        intent.putExtra(EXTRA_ID, wordId);
        intent.putExtra(EXTRA_HANZI, fullHanzi);
        intent.putExtra(EXTRA_PINYIN, pinyin);
        intent.putExtra(EXTRA_ENGLISH, english);
        intent.putExtra(EXTRA_LIST_INDEX, sourceListIndex);
        startActivity(intent);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static class RadicalOption {
        final int id;
        final String radical;
        final boolean correct;

        RadicalOption(int id, String radical, boolean correct) {
            this.id = id;
            this.radical = radical;
            this.correct = correct;
        }
    }

    private static class OptionView {
        final FrameLayout card;
        final TextView text;

        OptionView(FrameLayout card, TextView text) {
            this.card = card;
            this.text = text;
        }
    }
}
