package com.example.chineseapp.Helpers;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RadicalDatabase {

    // ── Radical catalogue ─────────────────────────────────────────────────────
    /**
     * Every character here is treated as a potential component.
     * The list covers:
     *  - Pure radical forms that never appear as standalone words (氵, 扌, 灬 …)
     *  - Common characters that frequently appear as components (木, 日, 月 …)
     *  - HSK-level building-blocks (人, 口, 山, 土 …)
     *
     * If a character is not found in graphics.txt it is silently skipped.
     */
    public static final String[] RADICAL_CHARS = {
            // ── 2-stroke ──────────────────────────────────────────────────────────
            "人", "儿", "入", "八", "刀", "力", "又", "冫", "亻",
            // ── 3-stroke ──────────────────────────────────────────────────────────
            "口", "土", "士", "大", "女", "子", "寸", "小", "山", "工",
            "己", "弓", "彳", "氵", "扌", "纟", "讠", "饣", "忄", "马",
            "门", "飞", "干", "巾",
            // ── 4-stroke ──────────────────────────────────────────────────────────
            "心", "水", "火", "木", "日", "月", "手", "王", "牛", "斤",
            "犬", "父", "气", "毛", "贝", "见", "车", "风", "灬", "爪",
            "方", "文", "止", "比",
            // ── 5-stroke ──────────────────────────────────────────────────────────
            "目", "田", "白", "立", "石", "禾", "矢", "示", "皮", "龙",
            "玉", "生", "用", "疒", "甲", "申",
            // ── 6-stroke ──────────────────────────────────────────────────────────
            "竹", "羊", "虫", "米", "耳", "肉", "舌", "色", "艹", "西",
            // ── 7-stroke ──────────────────────────────────────────────────────────
            "足", "走", "言", "見", "角",
            // ── 8-stroke ──────────────────────────────────────────────────────────
            "金", "雨", "隹",
    };

    // ── Tuning constants ─────────────────────────────────────────────────────
    /** Number of points each stroke median is resampled to. */
    static final int RESAMPLE_N = 6;

    /**
     * Maximum average per-stroke distance (on the [0,1000] normalised scale)
     * for a window to be considered a match.
     *
     * Empirically: same-family characters tend to score 60–150;
     * unrelated strokes tend to score 250+.
     */
    public static final float MATCH_THRESHOLD = 185f;

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile RadicalDatabase instance;

    public static RadicalDatabase getInstance(Context ctx) {
        if (instance == null) {
            synchronized (RadicalDatabase.class) {
                if (instance == null) {
                    instance = load(ctx.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ── Data model ────────────────────────────────────────────────────────────

    /** One entry in the database. */
    public static class RadicalEntry {
        public final String character;
        public final int strokeCount;
        /**
         * Pre-normalised + pre-resampled medians.
         * Dimensions: [strokeIndex][samplePointIndex][0=x / 1=y]
         */
        public final float[][][] normalizedMedians;

        RadicalEntry(String ch, int sc, float[][][] nm) {
            character = ch;
            strokeCount = sc;
            normalizedMedians = nm;
        }

        @Override
        public String toString() {
            return "RadicalEntry{" + character + ", strokes=" + strokeCount + "}";
        }
    }

    /** Main index: strokeCount → list of radicals with that count. */
    private final Map<Integer, List<RadicalEntry>> byStrokeCount = new HashMap<>();

    // ── Loading ───────────────────────────────────────────────────────────────

    private static RadicalDatabase load(Context ctx) {
        RadicalDatabase db = new RadicalDatabase();
        Set<String> needed = new HashSet<>(Arrays.asList(RADICAL_CHARS));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ctx.getAssets().open("graphics.txt")))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (needed.isEmpty()) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                JSONObject obj = new JSONObject(line);
                String ch = obj.getString("character");
                if (!needed.remove(ch)) continue; // not in our list, or already loaded

                JSONArray mediansArr = obj.getJSONArray("medians");
                int strokeCount = mediansArr.length();

                // Parse raw medians
                float[][][] raw = new float[strokeCount][][];
                for (int i = 0; i < strokeCount; i++) {
                    JSONArray strokeArr = mediansArr.getJSONArray(i);
                    raw[i] = new float[strokeArr.length()][2];
                    for (int j = 0; j < strokeArr.length(); j++) {
                        JSONArray pt = strokeArr.getJSONArray(j);
                        raw[i][j][0] = (float) pt.getDouble(0);
                        raw[i][j][1] = (float) pt.getDouble(1);
                    }
                }

                // Normalize + resample once, store permanently
                float[][][] normalized = normalizeAndResample(raw);
                RadicalEntry entry = new RadicalEntry(ch, strokeCount, normalized);
                db.byStrokeCount
                        .computeIfAbsent(strokeCount, k -> new ArrayList<>())
                        .add(entry);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return db;
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** All radicals with the given stroke count (never null). */
    public List<RadicalEntry> getByStrokeCount(int count) {
        List<RadicalEntry> r = byStrokeCount.get(count);
        return r != null ? r : Collections.<RadicalEntry>emptyList();
    }

    /** Total number of loaded radicals. */
    public int size() {
        int n = 0;
        for (List<RadicalEntry> l : byStrokeCount.values()) n += l.size();
        return n;
    }

    // ── Normalisation & resampling ────────────────────────────────────────────

    /**
     * Normalise a group of strokes to a [0,1000]×[0,1000] box
     * (uniform scale preserving aspect ratio, centered),
     * then resample each stroke median to exactly {@link #RESAMPLE_N} points.
     *
     * This is called both when building the database (on reference radicals)
     * and at decomposition time (on candidate windows from the target character).
     * The same transformation ensures apples-to-apples comparison.
     */
    public static float[][][] normalizeAndResample(float[][][] strokes) {
        // ── 1. Bounding box ───────────────────────────────────────────────────
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[][] stroke : strokes) {
            for (float[] pt : stroke) {
                if (pt[0] < minX) minX = pt[0];
                if (pt[0] > maxX) maxX = pt[0];
                if (pt[1] < minY) minY = pt[1];
                if (pt[1] > maxY) maxY = pt[1];
            }
        }
        float w = maxX - minX;
        float h = maxY - minY;
        float maxDim = Math.max(w, h);
        if (maxDim < 1f) maxDim = 1f;

        float scale   = 1000f / maxDim;
        float offsetX = (1000f - w * scale) / 2f;
        float offsetY = (1000f - h * scale) / 2f;

        // ── 2. Normalize + resample each stroke ───────────────────────────────
        float[][][] result = new float[strokes.length][RESAMPLE_N][2];
        for (int i = 0; i < strokes.length; i++) {
            float[][] norm = new float[strokes[i].length][2];
            for (int j = 0; j < strokes[i].length; j++) {
                norm[j][0] = (strokes[i][j][0] - minX) * scale + offsetX;
                norm[j][1] = (strokes[i][j][1] - minY) * scale + offsetY;
            }
            result[i] = resample(norm, RESAMPLE_N);
        }
        return result;
    }

    /**
     * Resample a polyline (sequence of 2D points) to exactly {@code n}
     * evenly-arc-length-spaced points using linear interpolation.
     */
    static float[][] resample(float[][] pts, int n) {
        if (pts.length == 0) return new float[n][2];
        if (pts.length == 1) {
            float[][] out = new float[n][2];
            for (int i = 0; i < n; i++) { out[i][0] = pts[0][0]; out[i][1] = pts[0][1]; }
            return out;
        }

        // Cumulative arc lengths
        float[] cumLen = new float[pts.length];
        for (int i = 1; i < pts.length; i++) {
            float dx = pts[i][0] - pts[i - 1][0];
            float dy = pts[i][1] - pts[i - 1][1];
            cumLen[i] = cumLen[i - 1] + (float) Math.sqrt(dx * dx + dy * dy);
        }
        float total = cumLen[pts.length - 1];
        if (total < 1f) {
            // Degenerate: all points coincide
            float[][] out = new float[n][2];
            for (int i = 0; i < n; i++) { out[i][0] = pts[0][0]; out[i][1] = pts[0][1]; }
            return out;
        }

        float[][] out = new float[n][2];
        int seg = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / (n - 1) * total;
            // Advance segment pointer
            while (seg < pts.length - 2 && cumLen[seg + 1] < t) seg++;
            float segLen = cumLen[seg + 1] - cumLen[seg];
            float alpha  = segLen < 1e-6f ? 0f : (t - cumLen[seg]) / segLen;
            out[i][0] = pts[seg][0] + alpha * (pts[seg + 1][0] - pts[seg][0]);
            out[i][1] = pts[seg][1] + alpha * (pts[seg + 1][1] - pts[seg][1]);
        }
        return out;
    }

    // ── Matching ──────────────────────────────────────────────────────────────

    /**
     * Compute the match score between:
     *   {@code reference} — a pre-normalized, pre-resampled radical entry
     *   {@code candidate} — a window of strokes already passed through
     *                       {@link #normalizeAndResample}
     *
     * Score is the mean Euclidean distance per stroke per sample point,
     * on the [0,1000] scale.  Lower = better.
     *
     * Strokes are compared in order (index 0 vs 0, 1 vs 1, …).
     * This is correct because Chinese stroke order is highly conventional.
     */
    public static float matchScore(float[][][] reference, float[][][] candidate) {
        int nStrokes = reference.length; // == candidate.length guaranteed by caller
        float total = 0f;
        for (int i = 0; i < nStrokes; i++) {
            float strokeDist = 0f;
            for (int j = 0; j < RESAMPLE_N; j++) {
                float dx = reference[i][j][0] - candidate[i][j][0];
                float dy = reference[i][j][1] - candidate[i][j][1];
                strokeDist += (float) Math.sqrt(dx * dx + dy * dy);
            }
            total += strokeDist / RESAMPLE_N;
        }
        return total / nStrokes;
    }
}
