package com.example.chineseapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chineseapp.Helpers.DrawingCanvas;
import com.example.chineseapp.Helpers.StrokeView;
import com.example.chineseapp.Helpers.TextSanitizer;
import com.example.chineseapp.Notifications.NotificationScheduler;
import com.example.chineseapp.WordLists.ListRepository;
import com.example.chineseapp.WordLists.WordItem;
import com.example.chineseapp.supabase.SupabaseStreakRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WordListPracticeActivity extends AppCompatActivity {

    private TextView tvListTitle;
    private TextView tvWord;
    private TextView tvMeta;
    private TextView tvProgress;
    private TextView tvInstruction;
    private Button btnHint;
    private Button btnStop;
    private Button btnNext;
    private StrokeView strokeView;
    private DrawingCanvas drawingCanvas;
    private ImageView btnBack;

    private int listIndex;
    private String listTitle;
    private final List<WordSessionItem> sessionItems = new ArrayList<>();

    private int wordIndex = 0;
    private int charIndex = 0;
    private boolean hintVisible = false;
    private boolean summaryShown = false;

    private static final int MISSED_THRESHOLD = 60;
    
    private SupabaseStreakRepository streakRepo;

    private static class CharStrokeData {
        final String character;
        final List<android.graphics.Path> strokes;
        final List<float[][]> medians;

        CharStrokeData(String character, List<android.graphics.Path> strokes, List<float[][]> medians) {
            this.character = character;
            this.strokes = strokes;
            this.medians = medians;
        }
    }

    private static class WordSessionItem {
        final WordItem word;
        final List<CharStrokeData> chars = new ArrayList<>();
        final List<Integer> scores = new ArrayList<>();

        WordSessionItem(WordItem word) {
            this.word = word;
        }

        float averageScore() {
            if (scores.isEmpty()) {
                return 0f;
            }
            int sum = 0;
            for (int s : scores) {
                sum += s;
            }
            return sum / (float) scores.size();
        }

        boolean attemptedAll() {
            return !chars.isEmpty() && scores.size() >= chars.size();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_list_practice);

        listIndex = getIntent().getIntExtra("list_index", -1);
        if (listIndex < 1) {
            Toast.makeText(this, "Invalid list", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        streakRepo = new SupabaseStreakRepository(this);
        listTitle = ListRepository.getListTitle(this, listIndex);
        tvListTitle.setText(listTitle);

        btnBack.setOnClickListener(v -> finish());
        btnHint.setOnClickListener(v -> toggleHint());
        btnStop.setOnClickListener(v -> stopPractice());
        btnNext.setOnClickListener(v -> submitCurrentCharacter());

        startLoadingSession();
    }

    private void bindViews() {
        tvListTitle = findViewById(R.id.tv_list_title);
        tvWord = findViewById(R.id.tv_word);
        tvMeta = findViewById(R.id.tv_meta);
        tvProgress = findViewById(R.id.tv_progress);
        tvInstruction = findViewById(R.id.tv_instruction);
        btnHint = findViewById(R.id.btn_hint);
        btnStop = findViewById(R.id.btn_stop);
        btnNext = findViewById(R.id.btn_next);
        strokeView = findViewById(R.id.stroke_view);
        drawingCanvas = findViewById(R.id.drawing_canvas);
        btnBack = findViewById(R.id.back_img);
    }

    private void startLoadingSession() {
        tvInstruction.setText("Loading list...");
        btnNext.setEnabled(false);

        new Thread(() -> {
            List<WordItem> words = ListRepository.loadList(this, listIndex);
            if (words == null || words.isEmpty()) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "This list has no words", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }

            Map<String, CharStrokeData> cache = new HashMap<>();
            for (WordItem word : words) {
                WordSessionItem item = new WordSessionItem(word);
                List<String> characters = splitCharacters(word.hanzi);

                for (String ch : characters) {
                    CharStrokeData data = cache.get(ch);
                    if (data == null) {
                        data = loadCharacterData(ch);
                        if (data != null) {
                            cache.put(ch, data);
                        }
                    }
                    if (data != null) {
                        item.chars.add(data);
                    }
                }

                if (!item.chars.isEmpty()) {
                    sessionItems.add(item);
                }
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (sessionItems.isEmpty()) {
                    Toast.makeText(this, "No drawable hanzi found in this list", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                btnNext.setEnabled(true);
                showCurrentCharacter();
            });
        }).start();
    }

    private void showCurrentCharacter() {
        if (wordIndex >= sessionItems.size()) {
            showSummary(false);
            return;
        }

        WordSessionItem item = sessionItems.get(wordIndex);
        if (charIndex >= item.chars.size()) {
            wordIndex++;
            charIndex = 0;
            showCurrentCharacter();
            return;
        }

        CharStrokeData current = item.chars.get(charIndex);

        tvWord.setText(current.character);
        String cleanPinyin = TextSanitizer.cleanPinyin(item.word.pinyin);
        String cleanMeaning = TextSanitizer.cleanMeaning(item.word.meaning);
        tvMeta.setText(cleanPinyin + "   " + cleanMeaning);
        tvProgress.setText("Word " + (wordIndex + 1) + "/" + sessionItems.size()
                + "  •  Hanzi " + (charIndex + 1) + "/" + item.chars.size());

        tvInstruction.setText("Write from memory, then tap Next");
        drawingCanvas.clearDrawing();
        drawingCanvas.setTargetStrokes(current.strokes);

        hintVisible = false;
        btnHint.setText("Hint");
        strokeView.showNothing();
    }

    private void submitCurrentCharacter() {
        if (wordIndex >= sessionItems.size()) {
            showSummary(false);
            return;
        }

        WordSessionItem item = sessionItems.get(wordIndex);
        if (charIndex >= item.chars.size()) {
            wordIndex++;
            charIndex = 0;
            showCurrentCharacter();
            return;
        }

        int score = drawingCanvas.scoreDrawing();
        item.scores.add(score);
        
        // Mark daily goal as completed on first character submission
        if (wordIndex == 0 && charIndex == 0) {
            markDailyGoalCompleted();
        }

        charIndex++;
        showCurrentCharacter();
    }
    
    private void markDailyGoalCompleted() {
        // Check if already marked today locally
        if (streakRepo != null && !streakRepo.isTodayCompletedLocally()) {
            // Mark locally immediately
            streakRepo.markGoalCompletedLocally();
            
            // Sync with Supabase in background
            streakRepo.markGoalCompleted(new SupabaseStreakRepository.SimpleCallback() {
                @Override
                public void onSuccess() {
                    // Successfully synced with server
                }
                
                @Override
                public void onError(String error) {
                    // Local mark succeeded, server sync failed - will retry next time
                    // User's streak is still counted locally
                }
            });
        }
    }

    private void toggleHint() {
        if (wordIndex >= sessionItems.size()) {
            return;
        }

        WordSessionItem item = sessionItems.get(wordIndex);
        if (charIndex >= item.chars.size()) {
            return;
        }

        CharStrokeData current = item.chars.get(charIndex);

        hintVisible = !hintVisible;
        if (hintVisible) {
            strokeView.showAll(current.strokes, current.medians);
            btnHint.setText("Hide Hint");
            tvInstruction.setText("Hint is on: follow the stroke direction");
        } else {
            strokeView.showNothing();
            btnHint.setText("Hint");
            tvInstruction.setText("Write from memory, then tap Next");
        }
    }

    private void stopPractice() {
        showSummary(true);
    }

    private void showSummary(boolean stoppedEarly) {
        if (summaryShown) {
            return;
        }
        summaryShown = true;

        List<WordSessionItem> attempted = new ArrayList<>();
        List<String> missed = new ArrayList<>();

        for (WordSessionItem item : sessionItems) {
            if (!item.scores.isEmpty()) {
                attempted.add(item);
            }

            boolean unfinished = !item.attemptedAll();
            boolean lowScore = item.attemptedAll() && item.averageScore() < MISSED_THRESHOLD;
            if (unfinished || lowScore) {
                missed.add(item.word.hanzi);
            }
        }

        attempted.sort(Comparator.comparingDouble(WordSessionItem::averageScore).reversed());

        StringBuilder message = new StringBuilder();
        if (stoppedEarly) {
            message.append("Practice stopped early.\n\n");
        } else {
            message.append("Practice complete.\n\n");
        }

        if (attempted.isEmpty()) {
            message.append("No words were completed yet.\n");
        } else {
            // Sync streak with Supabase (local already marked during practice)
            NotificationScheduler.markStreakCompleted(this);
            message.append("Best completed words: ");
            int bestCount = Math.min(3, attempted.size());
            for (int i = 0; i < bestCount; i++) {
                WordSessionItem best = attempted.get(i);
                if (i > 0) {
                    message.append(", ");
                }
                message.append(best.word.hanzi)
                        .append(" (")
                        .append(Math.round(best.averageScore()))
                        .append("%)");
            }
            message.append("\n");
        }

        if (missed.isEmpty()) {
            message.append("Missed words: none");
        } else {
            message.append("Missed words: ");
            for (int i = 0; i < missed.size(); i++) {
                if (i > 0) {
                    message.append(", ");
                }
                message.append(missed.get(i));
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Session Summary")
                .setMessage(message.toString())
                .setCancelable(false)
                .setPositiveButton("Back to list", (dialog, which) -> {
                    Intent intent = new Intent(this, ListActivity.class);
                    intent.putExtra("list_index", listIndex);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private CharStrokeData loadCharacterData(String character) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open("graphics.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject obj = new JSONObject(line.trim());
                if (!obj.getString("character").equals(character)) {
                    continue;
                }

                JSONArray strokesArr = obj.getJSONArray("strokes");
                JSONArray mediansArr = obj.getJSONArray("medians");

                List<android.graphics.Path> strokes = new ArrayList<>();
                List<float[][]> medians = new ArrayList<>();

                for (int i = 0; i < strokesArr.length(); i++) {
                    strokes.add(PracticeWordActivity.parseSvgPath(strokesArr.getString(i)));

                    JSONArray medStroke = mediansArr.getJSONArray(i);
                    float[][] pts = new float[medStroke.length()][2];
                    for (int j = 0; j < medStroke.length(); j++) {
                        JSONArray pt = medStroke.getJSONArray(j);
                        pts[j][0] = (float) pt.getDouble(0);
                        pts[j][1] = (float) pt.getDouble(1);
                    }
                    medians.add(pts);
                }

                return new CharStrokeData(character, strokes, medians);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private List<String> splitCharacters(String hanzi) {
        if (hanzi == null || hanzi.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (int cp, i = 0; i < hanzi.length(); i += Character.charCount(cp)) {
            cp = hanzi.codePointAt(i);
            out.add(new String(Character.toChars(cp)));
        }
        return out;
    }
}
