package com.example.chineseapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chineseapp.Helpers.CharacterDecomposer;
import com.example.chineseapp.Helpers.DrawingCanvas;
import com.example.chineseapp.Helpers.RadicalDatabase;
import com.example.chineseapp.Helpers.StrokeView;
import com.example.chineseapp.Helpers.TextSanitizer;
import com.example.chineseapp.Notifications.NotificationScheduler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PracticeWordActivity extends AppCompatActivity {

    private TextView tvHanzi;
    private TextView tvPinyin;
    private TextView tvTranslation;
    private TextView tvInstruction;
    private TextView tvScore;
    private TextView tvCharacterProgress;
    private Button btnNext;
    private StrokeView strokeView;
    private DrawingCanvas drawingCanvas;
    private LinearLayout resultLayout;

    private String fullHanzi;
    private String pinyin;
    private String translation;
    private int sourceListIndex = -1;
    private String[] characters;

    private final List<CharData> charDataList = new ArrayList<>();

    private int charIndex = 0;
    private int stageIndex = 0;
    private int totalScore = 0;
    private int scoreCount = 0;
    private boolean isAnimating = false;

    private static class CharData {
        final String character;
        final List<android.graphics.Path> allStrokes = new ArrayList<>();
        final List<float[][]> allMedians = new ArrayList<>();
        final List<List<android.graphics.Path>> componentStrokes = new ArrayList<>();
        final List<List<float[][]>> componentMedians = new ArrayList<>();
        final List<String> componentLabels = new ArrayList<>();

        CharData(String ch) {
            character = ch;
        }

        boolean hasComponents() {
            return !componentStrokes.isEmpty();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice_word);

        fullHanzi = getIntent().getStringExtra("hanzi");
        if (fullHanzi == null || fullHanzi.isEmpty()) {
            fullHanzi = "?";
        }
        pinyin = getIntent().getStringExtra("pinyin");
        translation = getIntent().getStringExtra("english");
        sourceListIndex = getIntent().getIntExtra("list_index", -1);

        characters = new String[fullHanzi.codePointCount(0, fullHanzi.length())];
        int idx = 0;
        for (int cp, i = 0; i < fullHanzi.length(); i += Character.charCount(cp)) {
            cp = fullHanzi.codePointAt(i);
            characters[idx++] = new String(Character.toChars(cp));
        }

        bindViews();
        displayWordInfo();

        tvInstruction.setText("Loading...");
        btnNext.setEnabled(false);

        new Thread(this::loadAllCharacters).start();
        btnNext.setOnClickListener(v -> advanceStage());
    }

    private void bindViews() {
        tvHanzi = findViewById(R.id.tv_hanzi);
        tvPinyin = findViewById(R.id.tv_pinyin);
        tvTranslation = findViewById(R.id.tv_translation);
        tvInstruction = findViewById(R.id.tv_instruction);
        tvScore = findViewById(R.id.tv_score);
        tvCharacterProgress = findViewById(R.id.tv_character_progress);
        btnNext = findViewById(R.id.btn_next);
        strokeView = findViewById(R.id.stroke_view);
        drawingCanvas = findViewById(R.id.drawing_canvas);
        resultLayout = findViewById(R.id.result_layout);
    }

    private void displayWordInfo() {
        tvHanzi.setText(fullHanzi);
        tvPinyin.setText(TextSanitizer.cleanPinyin(pinyin));
        tvTranslation.setText(TextSanitizer.cleanMeaning(translation));
    }

    private void loadAllCharacters() {
        RadicalDatabase db = RadicalDatabase.getInstance(this);

        for (String ch : characters) {
            CharData data = new CharData(ch);
            loadCharacterStrokes(ch, data);
            decomposeCharacter(data, db);
            charDataList.add(data);
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            btnNext.setEnabled(true);
            setupStage();
        });
    }

    private void loadCharacterStrokes(String ch, CharData data) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open("graphics.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject obj = new JSONObject(line.trim());
                if (!obj.getString("character").equals(ch)) {
                    continue;
                }

                JSONArray strokesArr = obj.getJSONArray("strokes");
                JSONArray mediansArr = obj.getJSONArray("medians");

                for (int i = 0; i < strokesArr.length(); i++) {
                    data.allStrokes.add(parseSvgPath(strokesArr.getString(i)));

                    JSONArray medStroke = mediansArr.getJSONArray(i);
                    float[][] pts = new float[medStroke.length()][2];
                    for (int j = 0; j < medStroke.length(); j++) {
                        JSONArray pt = medStroke.getJSONArray(j);
                        pts[j][0] = (float) pt.getDouble(0);
                        pts[j][1] = (float) pt.getDouble(1);
                    }
                    data.allMedians.add(pts);
                }
                break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void decomposeCharacter(CharData data, RadicalDatabase db) {
        List<String> rawSvg = new ArrayList<>();
        List<List<int[]>> rawMedians = new ArrayList<>();

        for (android.graphics.Path ignored : data.allStrokes) {
            rawSvg.add("");
        }
        for (float[][] med : data.allMedians) {
            List<int[]> ml = new ArrayList<>();
            for (float[] pt : med) {
                ml.add(new int[]{(int) pt[0], (int) pt[1]});
            }
            rawMedians.add(ml);
        }

        CharacterDecomposer decomposer = new CharacterDecomposer(db);
        CharacterDecomposer.CharacterEntry entry =
                new CharacterDecomposer.CharacterEntry(data.character, rawSvg, rawMedians);
        List<CharacterDecomposer.Component> components = decomposer.decompose(entry);

        if (components.size() > 1) {
            for (CharacterDecomposer.Component comp : components) {
                List<android.graphics.Path> cStrokes = new ArrayList<>();
                List<float[][]> cMedians = new ArrayList<>();
                for (int i : comp.strokeIndices) {
                    cStrokes.add(data.allStrokes.get(i));
                    cMedians.add(data.allMedians.get(i));
                }
                data.componentStrokes.add(cStrokes);
                data.componentMedians.add(cMedians);
                data.componentLabels.add(comp.matchedRadical);
            }
        }
    }

    private void setupStage() {
        if (charIndex >= charDataList.size()) {
            showResult();
            return;
        }

        stopAnimation();
        drawingCanvas.clearDrawing();
        resultLayout.setVisibility(View.GONE);
        strokeView.setVisibility(View.VISIBLE);
        drawingCanvas.setVisibility(View.VISIBLE);
        tvScore.setVisibility(View.GONE);

        CharData data = charDataList.get(charIndex);
        updateCharacterProgress();
        tvHanzi.setText(buildHighlightedWord());

        if (data.hasComponents()) {
            setupComponentStage(data);
        } else {
            setupNoComponentStage(data);
        }
    }

    private void setupComponentStage(CharData data) {
        int total = data.componentStrokes.size();
        int freeDrawStage = total * 2;

        if (stageIndex == freeDrawStage) {
            tvInstruction.setText("Now draw " + data.character + " from memory!");
            btnNext.setText(isLastCharacter() ? "See result ->" : "Next character ->");
            btnNext.setEnabled(true);
            strokeView.showNothing();
            drawingCanvas.setTargetStrokes(data.allStrokes);
            return;
        }

        int compIdx = stageIndex / 2;
        boolean isAnimPhase = (stageIndex % 2 == 0);
        String label = data.componentLabels.get(compIdx);
        String labelPart = (label != null) ? " (" + label + ")" : "";

        if (isAnimPhase) {
            tvInstruction.setText("Watch component " + (compIdx + 1) + " of " + total + labelPart);
            btnNext.setText("Skip");
            btnNext.setEnabled(true);
            drawingCanvas.setTargetStrokes(data.componentStrokes.get(compIdx));

            isAnimating = true;
            strokeView.animateStrokes(data.componentStrokes.get(compIdx), data.componentMedians.get(compIdx), () -> {
                isAnimating = false;
                stageIndex++;
                setupStage();
            });
        } else {
            tvInstruction.setText("Draw component " + (compIdx + 1) + " of " + total + labelPart);
            btnNext.setText("Next ->");
            btnNext.setEnabled(true);
            strokeView.showComponent(data.componentStrokes.get(compIdx), data.componentMedians.get(compIdx));
            drawingCanvas.setTargetStrokes(data.componentStrokes.get(compIdx));
        }
    }

    private void setupNoComponentStage(CharData data) {
        if (stageIndex == 0) {
            tvInstruction.setText("Watch the stroke order for " + data.character);
            btnNext.setText("Skip");
            btnNext.setEnabled(true);
            drawingCanvas.setTargetStrokes(data.allStrokes);

            isAnimating = true;
            strokeView.animateStrokes(data.allStrokes, data.allMedians, () -> {
                isAnimating = false;
                stageIndex++;
                setupStage();
            });
            return;
        }

        tvInstruction.setText("Now draw " + data.character + " from memory!");
        btnNext.setText(isLastCharacter() ? "See result ->" : "Next character ->");
        btnNext.setEnabled(true);
        strokeView.showNothing();
        drawingCanvas.setTargetStrokes(data.allStrokes);
    }

    private void stopAnimation() {
        isAnimating = false;
        strokeView.stopAnimation();
    }

    private void advanceStage() {
        CharData data = charDataList.get(charIndex);

        if (data.hasComponents()) {
            int freeDrawStage = data.componentStrokes.size() * 2;

            if (stageIndex == freeDrawStage) {
                totalScore += drawingCanvas.scoreDrawing();
                scoreCount++;
                charIndex++;
                stageIndex = 0;
                setupStage();
            } else if (stageIndex % 2 == 0) {
                stopAnimation();
                stageIndex++;
                setupStage();
            } else {
                totalScore += drawingCanvas.scoreDrawing();
                scoreCount++;
                stageIndex++;
                setupStage();
            }
            return;
        }

        if (stageIndex == 0) {
            stopAnimation();
            stageIndex++;
            setupStage();
        } else {
            totalScore += drawingCanvas.scoreDrawing();
            scoreCount++;
            charIndex++;
            stageIndex = 0;
            setupStage();
        }
    }

    private void showResult() {
        stopAnimation();
        strokeView.setVisibility(View.GONE);
        drawingCanvas.setVisibility(View.GONE);
        resultLayout.setVisibility(View.VISIBLE);
        tvCharacterProgress.setVisibility(View.GONE);
        btnNext.setText("Done");
        btnNext.setOnClickListener(v -> onDonePressed());

        int avg = scoreCount > 0 ? totalScore / scoreCount : 0;
        String grade;
        if (avg >= 90) {
            grade = "Excellent!";
        } else if (avg >= 70) {
            grade = "Good!";
        } else if (avg >= 50) {
            grade = "Keep Practicing";
        } else {
            grade = "Try Again";
        }

        tvHanzi.setText(fullHanzi);
        tvInstruction.setText(grade);
        tvScore.setVisibility(View.VISIBLE);
        tvScore.setText(avg + " / 100");

        final int[] cur = {0};
        Handler h = new Handler(Looper.getMainLooper());
        int steps = 40;
        long delay = 1200L / steps;
        Runnable ticker = new Runnable() {
            @Override
            public void run() {
                cur[0] += Math.max(1, avg / steps);
                if (cur[0] >= avg) {
                    tvScore.setText(avg + " / 100");
                } else {
                    tvScore.setText(cur[0] + " / 100");
                    h.postDelayed(this, delay);
                }
            }
        };
        h.postDelayed(ticker, delay);
    }

    private void onDonePressed() {
        NotificationScheduler.markStreakCompleted(this);
        if (sourceListIndex >= 0) {
            Intent intent = new Intent(this, ListActivity.class);
            intent.putExtra("list_index", sourceListIndex);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
    }

    private boolean isLastCharacter() {
        return charIndex == charDataList.size() - 1;
    }

    private void updateCharacterProgress() {
        if (characters.length > 1) {
            tvCharacterProgress.setVisibility(View.VISIBLE);
            tvCharacterProgress.setText("Character " + (charIndex + 1) + " of " + characters.length);
        } else {
            tvCharacterProgress.setVisibility(View.GONE);
        }
    }

    private String buildHighlightedWord() {
        return charDataList.get(charIndex).character;
    }

    public static android.graphics.Path parseSvgPath(String d) {
        android.graphics.Path path = new android.graphics.Path();
        String[] tokens = d.trim().split("(?=[MmLlQqCcZz])|[,\\s]+");
        int i = 0;
        while (i < tokens.length) {
            String t = tokens[i].trim();
            if (t.isEmpty()) {
                i++;
                continue;
            }
            char cmd = t.charAt(0);
            switch (cmd) {
                case 'M': {
                    float x = Float.parseFloat(tokens[++i]);
                    float y = Float.parseFloat(tokens[++i]);
                    path.moveTo(x, y);
                    break;
                }
                case 'L': {
                    float x = Float.parseFloat(tokens[++i]);
                    float y = Float.parseFloat(tokens[++i]);
                    path.lineTo(x, y);
                    break;
                }
                case 'Q': {
                    float x1 = Float.parseFloat(tokens[++i]);
                    float y1 = Float.parseFloat(tokens[++i]);
                    float x = Float.parseFloat(tokens[++i]);
                    float y = Float.parseFloat(tokens[++i]);
                    path.quadTo(x1, y1, x, y);
                    break;
                }
                case 'C': {
                    float x1 = Float.parseFloat(tokens[++i]);
                    float y1 = Float.parseFloat(tokens[++i]);
                    float x2 = Float.parseFloat(tokens[++i]);
                    float y2 = Float.parseFloat(tokens[++i]);
                    float x = Float.parseFloat(tokens[++i]);
                    float y = Float.parseFloat(tokens[++i]);
                    path.cubicTo(x1, y1, x2, y2, x, y);
                    break;
                }
                case 'Z':
                case 'z':
                    path.close();
                    break;
                default:
                    break;
            }
            i++;
        }
        return path;
    }
}
