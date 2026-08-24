package com.example.chineseapp.Helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CharacterDecomposer {

    // ── Public data model ─────────────────────────────────────────────────────

    public static class CharacterEntry {
        public final String character;
        public final List<String> strokes;
        public final List<List<int[]>> medians;

        public CharacterEntry(String ch, List<String> strokes, List<List<int[]>> medians) {
            this.character = ch;
            this.strokes   = Collections.unmodifiableList(strokes);
            this.medians   = Collections.unmodifiableList(medians);
        }
    }

    public static class Component {
        public final List<Integer>     strokeIndices;
        public final List<String>      strokes;
        public final List<List<int[]>> medians;
        public final String            matchedRadical; // null = unrecognised
        public final float             matchScore;

        Component(List<Integer> idx, List<String> st, List<List<int[]>> med,
                  String radical, float score) {
            strokeIndices  = Collections.unmodifiableList(idx);
            strokes        = Collections.unmodifiableList(st);
            medians        = Collections.unmodifiableList(med);
            matchedRadical = radical;
            matchScore     = score;
        }

        public boolean isRecognised() { return matchedRadical != null; }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static class Match {
        final int    start;
        final int    end;   // exclusive
        final String radical;
        final float  score;
        Match(int s, int e, String r, float sc) { start=s; end=e; radical=r; score=sc; }
    }

    // ── Config ────────────────────────────────────────────────────────────────

    /**
     * Maximum window size in strokes.
     * Raised to 20 so large components like 囊 (22 strokes) can still be
     * partially recognised when they appear as a sub-component.
     */
    private static final int MAX_WINDOW = 20;

    /**
     * Minimum fraction of total strokes that must be covered by recognised
     * components before we accept the decomposition.
     * If coverage is below this we return the whole character as one component.
     */
    private static final float MIN_COVERAGE = 0.5f;

    private final RadicalDatabase db;

    public CharacterDecomposer(RadicalDatabase db) { this.db = db; }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<Component> decompose(CharacterEntry entry) {
        int n = entry.medians.size();
        if (n == 0) return Collections.emptyList();

        float[][][] medians = toFloatArray(entry.medians);

        // Step 1: find all candidate matches (windows of size 1..MAX_WINDOW)
        List<Match> candidates = findCandidateMatches(medians, n);

        // Step 2: greedy non-overlapping selection
        List<Match> selected = selectNonOverlapping(candidates, n);

        // Step 3: coverage check — if too few strokes are explained, give up
        int coveredStrokes = 0;
        for (Match m : selected) coveredStrokes += (m.end - m.start);
        if ((float) coveredStrokes / n < MIN_COVERAGE) {
            // Return entire character as a single unrecognised component
            return Collections.singletonList(makeComponent(entry, 0, n, null, Float.NaN));
        }

        // Step 4: fill gaps and build final component list
        return buildComponents(entry, selected, n);
    }

    // ── Step 1: candidate matching ────────────────────────────────────────────

    private List<Match> findCandidateMatches(float[][][] medians, int n) {
        List<Match> results = new ArrayList<>();

        for (int start = 0; start < n; start++) {
            // Try single-stroke windows too (size = 1)
            int maxSize = Math.min(MAX_WINDOW, n - start);

            for (int size = 1; size <= maxSize; size++) {
                int end = start + size;

                float[][][] window   = Arrays.copyOfRange(medians, start, end);
                float[][][] normCand = RadicalDatabase.normalizeAndResample(window);

                float  bestScore   = RadicalDatabase.MATCH_THRESHOLD;
                String bestRadical = null;

                for (RadicalDatabase.RadicalEntry radical : db.getByStrokeCount(size)) {
                    float score = RadicalDatabase.matchScore(
                            radical.normalizedMedians, normCand);
                    if (score < bestScore) {
                        bestScore   = score;
                        bestRadical = radical.character;
                    }
                }

                if (bestRadical != null) {
                    results.add(new Match(start, end, bestRadical, bestScore));
                }
            }
        }
        return results;
    }

    // ── Step 2: greedy non-overlapping selection ──────────────────────────────

    private List<Match> selectNonOverlapping(List<Match> candidates, int n) {
        // Sort: best score first; on tie prefer larger window
        Collections.sort(candidates, new Comparator<Match>() {
            @Override public int compare(Match a, Match b) {
                int cmp = Float.compare(a.score, b.score);
                if (cmp != 0) return cmp;
                return Integer.compare(b.end - b.start, a.end - a.start);
            }
        });

        boolean[]  used   = new boolean[n];
        List<Match> picked = new ArrayList<>();

        for (Match m : candidates) {
            boolean conflict = false;
            for (int k = m.start; k < m.end; k++) {
                if (used[k]) { conflict = true; break; }
            }
            if (conflict) continue;
            for (int k = m.start; k < m.end; k++) used[k] = true;
            picked.add(m);
        }

        // Re-sort by stroke position for gap-filling
        Collections.sort(picked, new Comparator<Match>() {
            @Override public int compare(Match a, Match b) {
                return Integer.compare(a.start, b.start);
            }
        });

        return picked;
    }

    // ── Step 3: build component list, filling gaps ────────────────────────────

    private List<Component> buildComponents(CharacterEntry entry,
                                            List<Match> selected, int n) {
        List<Component> out    = new ArrayList<>();
        int             cursor = 0;

        for (Match m : selected) {
            // Gap before this match → one unified "other" component
            if (m.start > cursor) {
                out.add(makeComponent(entry, cursor, m.start, null, Float.NaN));
            }
            out.add(makeComponent(entry, m.start, m.end, m.radical, m.score));
            cursor = m.end;
        }

        // Trailing gap
        if (cursor < n) {
            out.add(makeComponent(entry, cursor, n, null, Float.NaN));
        }

        if (out.isEmpty()) {
            out.add(makeComponent(entry, 0, n, null, Float.NaN));
        }

        return out;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Component makeComponent(CharacterEntry entry,
                                    int startInclusive, int endExclusive,
                                    String radical, float score) {
        List<Integer>     indices = new ArrayList<>();
        List<String>      strokes = new ArrayList<>();
        List<List<int[]>> medians = new ArrayList<>();
        for (int k = startInclusive; k < endExclusive; k++) {
            indices.add(k);
            strokes.add(entry.strokes.get(k));
            medians.add(entry.medians.get(k));
        }
        return new Component(indices, strokes, medians, radical, score);
    }

    private float[][][] toFloatArray(List<List<int[]>> medians) {
        float[][][] out = new float[medians.size()][][];
        for (int i = 0; i < medians.size(); i++) {
            List<int[]> stroke = medians.get(i);
            out[i] = new float[stroke.size()][2];
            for (int j = 0; j < stroke.size(); j++) {
                out[i][j][0] = stroke.get(j)[0];
                out[i][j][1] = stroke.get(j)[1];
            }
        }
        return out;
    }
}