package com.example.chineseapp.Helpers;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decomposes a hanzi into component radicals and infers layout relations.
 *
 * It combines shape matching (against RadicalDatabase) with dynamic programming
 * over stroke ranges and relation scoring (left-right, top-bottom, enclosing).
 */
public class HanziStructureDecomposer {

    public enum Relation {
        SINGLE,
        LEFT_RIGHT,
        TOP_BOTTOM,
        ENCLOSING,
        OVERLAY
    }

    public static class Node {
        public final int startStroke;
        public final int endStroke; // exclusive
        public final RectF bounds;
        public final Relation relation;
        public final List<Node> children;
        public final String radical;
        public final float score;

        Node(int startStroke, int endStroke, RectF bounds, Relation relation,
             List<Node> children, String radical, float score) {
            this.startStroke = startStroke;
            this.endStroke = endStroke;
            this.bounds = bounds;
            this.relation = relation;
            this.children = children;
            this.radical = radical;
            this.score = score;
        }

        public boolean isLeaf() {
            return children == null || children.isEmpty();
        }
    }

    public static class Result {
        public final Node root;
        public final List<Node> leaves;

        Result(Node root, List<Node> leaves) {
            this.root = root;
            this.leaves = leaves;
        }

        public Map<String, Integer> requiredRadicalCounts() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Node leaf : leaves) {
                if (leaf.radical == null || leaf.radical.isEmpty()) {
                    continue;
                }
                counts.put(leaf.radical, counts.getOrDefault(leaf.radical, 0) + 1);
            }
            return counts;
        }
    }

    private static class Candidate {
        final String radical;
        final float score;

        Candidate(String radical, float score) {
            this.radical = radical;
            this.score = score;
        }
    }

    private static class SolveResult {
        final Node node;
        final float utility;

        SolveResult(Node node, float utility) {
            this.node = node;
            this.utility = utility;
        }
    }

    private static class RelationGuess {
        final Relation relation;
        final float confidence;

        RelationGuess(Relation relation, float confidence) {
            this.relation = relation;
            this.confidence = confidence;
        }
    }

    private static final float MATCH_SOFT_MULTIPLIER = 1.15f;
    private static final float SPLIT_PENALTY = 0.34f;
    private static final float LEAF_UNKNOWN_PENALTY = 0.42f;

    private final RadicalDatabase database;
    private final Map<Long, Candidate> candidateCache = new HashMap<>();
    private final Map<Long, SolveResult> solveCache = new HashMap<>();

    private float[][][] allMedians;
    private RectF[] strokeBounds;
    private int strokeCount;

    public HanziStructureDecomposer(RadicalDatabase database) {
        this.database = database;
    }

    public Result decompose(CharacterDecomposer.CharacterEntry entry) {
        strokeCount = entry.medians.size();
        if (strokeCount == 0) {
            return new Result(null, Collections.emptyList());
        }

        allMedians = toFloatArray(entry.medians);
        strokeBounds = precomputeStrokeBounds(allMedians);
        candidateCache.clear();
        solveCache.clear();

        Node root = solve(0, strokeCount).node;
        if (root == null) {
            return new Result(null, Collections.emptyList());
        }

        List<Node> leaves = new ArrayList<>();
        collectLeaves(root, leaves);

        int recognized = 0;
        for (Node leaf : leaves) {
            if (leaf.radical != null) {
                recognized++;
            }
        }

        if (recognized == 0) {
            Node fallback = new Node(0, strokeCount, unionBounds(0, strokeCount),
                    Relation.SINGLE, Collections.emptyList(), entry.character, 0f);
            leaves.clear();
            leaves.add(fallback);
            return new Result(fallback, leaves);
        }

        return new Result(root, leaves);
    }

    private SolveResult solve(int start, int end) {
        long key = rangeKey(start, end);
        SolveResult cached = solveCache.get(key);
        if (cached != null) {
            return cached;
        }

        int len = end - start;
        RectF bounds = unionBounds(start, end);
        Candidate whole = bestCandidate(start, end);

        Node bestNode;
        float bestUtility;

        if (whole != null) {
            bestUtility = recognitionUtility(whole.score, len);
            bestNode = new Node(start, end, bounds, Relation.SINGLE,
                    Collections.emptyList(), whole.radical, whole.score);
        } else {
            bestUtility = -LEAF_UNKNOWN_PENALTY * len;
            bestNode = new Node(start, end, bounds, Relation.SINGLE,
                    Collections.emptyList(), null, Float.NaN);
        }

        if (len >= 2) {
            for (int split = start + 1; split < end; split++) {
                SolveResult left = solve(start, split);
                SolveResult right = solve(split, end);

                if (left.node == null || right.node == null) {
                    continue;
                }

                RelationGuess guess = inferRelation(left.node.bounds, right.node.bounds);
                float relationBonus = guess.confidence * relationBonus(guess.relation);
                float splitUtility = left.utility + right.utility + relationBonus - SPLIT_PENALTY;

                if (splitUtility > bestUtility + 0.06f) {
                    List<Node> children = new ArrayList<>(2);
                    children.add(left.node);
                    children.add(right.node);

                    bestUtility = splitUtility;
                    bestNode = new Node(start, end, bounds, guess.relation,
                            children, null, Float.NaN);
                }
            }
        }

        SolveResult result = new SolveResult(bestNode, bestUtility);
        solveCache.put(key, result);
        return result;
    }

    private Candidate bestCandidate(int start, int end) {
        long key = rangeKey(start, end);
        Candidate cached = candidateCache.get(key);
        if (cached != null || candidateCache.containsKey(key)) {
            return cached;
        }

        int len = end - start;
        if (len <= 0) {
            candidateCache.put(key, null);
            return null;
        }

        float[][][] slice = new float[len][][];
        for (int i = 0; i < len; i++) {
            slice[i] = allMedians[start + i];
        }
        float[][][] normalized = RadicalDatabase.normalizeAndResample(slice);

        float threshold = RadicalDatabase.MATCH_THRESHOLD * MATCH_SOFT_MULTIPLIER;
        float bestScore = threshold;
        String bestRadical = null;

        for (RadicalDatabase.RadicalEntry entry : database.getByStrokeCount(len)) {
            float score = RadicalDatabase.matchScore(entry.normalizedMedians, normalized);
            if (score < bestScore) {
                bestScore = score;
                bestRadical = entry.character;
            }
        }

        Candidate out = bestRadical == null ? null : new Candidate(bestRadical, bestScore);
        candidateCache.put(key, out);
        return out;
    }

    private float recognitionUtility(float score, int strokeLen) {
        float q = Math.max(0f, 1f - (score / RadicalDatabase.MATCH_THRESHOLD));
        return 1.25f + q * 2.35f + 0.08f * strokeLen;
    }

    private float relationBonus(Relation relation) {
        switch (relation) {
            case LEFT_RIGHT:
                return 0.62f;
            case TOP_BOTTOM:
                return 0.58f;
            case ENCLOSING:
                return 0.7f;
            case OVERLAY:
                return 0.18f;
            default:
                return 0f;
        }
    }

    private RelationGuess inferRelation(RectF left, RectF right) {
        float overlapX = overlapRatio(left.left, left.right, right.left, right.right);
        float overlapY = overlapRatio(left.top, left.bottom, right.top, right.bottom);

        boolean leftContainsRight = containsWithMargin(left, right, 0.06f);
        boolean rightContainsLeft = containsWithMargin(right, left, 0.06f);
        if (leftContainsRight || rightContainsLeft) {
            float confidence = 0.75f + 0.2f * Math.max(overlapX, overlapY);
            return new RelationGuess(Relation.ENCLOSING, clamp01(confidence));
        }

        float centerDx = Math.abs(left.centerX() - right.centerX());
        float centerDy = Math.abs(left.centerY() - right.centerY());

        if (overlapY > 0.55f && centerDx > centerDy) {
            float confidence = 0.55f + 0.45f * (1f - overlapX);
            return new RelationGuess(Relation.LEFT_RIGHT, clamp01(confidence));
        }

        if (overlapX > 0.55f && centerDy > centerDx) {
            float confidence = 0.55f + 0.45f * (1f - overlapY);
            return new RelationGuess(Relation.TOP_BOTTOM, clamp01(confidence));
        }

        return new RelationGuess(Relation.OVERLAY, 0.35f);
    }

    private void collectLeaves(Node node, List<Node> out) {
        if (node == null) {
            return;
        }
        if (node.isLeaf()) {
            out.add(node);
            return;
        }

        List<Node> sorted = new ArrayList<>(node.children);
        if (node.relation == Relation.LEFT_RIGHT) {
            sorted.sort(Comparator.comparingDouble(n -> n.bounds.left));
        } else if (node.relation == Relation.TOP_BOTTOM) {
            sorted.sort(Comparator.comparingDouble(n -> n.bounds.top));
        } else {
            sorted.sort(Comparator.comparingInt(n -> n.startStroke));
        }

        for (Node child : sorted) {
            collectLeaves(child, out);
        }
    }

    private RectF[] precomputeStrokeBounds(float[][][] medians) {
        RectF[] bounds = new RectF[medians.length];
        for (int i = 0; i < medians.length; i++) {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (float[] point : medians[i]) {
                minX = Math.min(minX, point[0]);
                minY = Math.min(minY, point[1]);
                maxX = Math.max(maxX, point[0]);
                maxY = Math.max(maxY, point[1]);
            }
            bounds[i] = new RectF(minX, minY, maxX, maxY);
        }
        return bounds;
    }

    private RectF unionBounds(int start, int end) {
        RectF out = new RectF(strokeBounds[start]);
        for (int i = start + 1; i < end; i++) {
            out.union(strokeBounds[i]);
        }
        return out;
    }

    private static float[][][] toFloatArray(List<List<int[]>> medians) {
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

    private long rangeKey(int start, int end) {
        return (((long) start) << 32) | (end & 0xffffffffL);
    }

    private float overlapRatio(float a0, float a1, float b0, float b1) {
        float overlap = Math.max(0f, Math.min(a1, b1) - Math.max(a0, b0));
        float denom = Math.min(Math.max(1f, a1 - a0), Math.max(1f, b1 - b0));
        return clamp01(overlap / denom);
    }

    private boolean containsWithMargin(RectF outer, RectF inner, float marginRatio) {
        float marginX = outer.width() * marginRatio;
        float marginY = outer.height() * marginRatio;
        return inner.left >= outer.left - marginX
                && inner.top >= outer.top - marginY
                && inner.right <= outer.right + marginX
                && inner.bottom <= outer.bottom + marginY;
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
