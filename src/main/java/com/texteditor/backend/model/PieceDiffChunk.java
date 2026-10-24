package com.texteditor.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a changed interval / diff in the Piece Directory.
 * Describes that starting at `offset` in the base pieceDirectory,
 * `deleteCount` pieces were replaced by `insertedPieces`.
 */
public class PieceDiffChunk {

    private final int offset;
    private final int deleteCount;
    private final List<Piece> insertedPieces;

    @JsonCreator
    public PieceDiffChunk(
            @JsonProperty("offset") int offset,
            @JsonProperty("deleteCount") int deleteCount,
            @JsonProperty("insertedPieces") List<Piece> insertedPieces) {
        this.offset = offset;
        this.deleteCount = deleteCount;
        this.insertedPieces = insertedPieces != null ? new ArrayList<>(insertedPieces) : new ArrayList<>();
    }

    public int getOffset() {
        return offset;
    }

    public int getDeleteCount() {
        return deleteCount;
    }

    public List<Piece> getInsertedPieces() {
        return new ArrayList<>(insertedPieces);
    }

    /**
     * Applies a list of diff chunks onto a base piece directory.
     * Chunks are sorted in descending order of offset so that changes
     * at higher indices do not disrupt lower indices.
     */
    public static List<Piece> applyDiff(List<Piece> base, List<PieceDiffChunk> chunks) {
        if (base == null) base = Collections.emptyList();
        List<Piece> result = new ArrayList<>(base);
        if (chunks == null || chunks.isEmpty()) {
            return result;
        }

        // Sort descending by offset
        List<PieceDiffChunk> sorted = new ArrayList<>(chunks);
        sorted.sort((a, b) -> Integer.compare(b.getOffset(), a.getOffset()));

        for (PieceDiffChunk chunk : sorted) {
            int off = Math.max(0, Math.min(chunk.getOffset(), result.size()));
            int del = Math.max(0, Math.min(chunk.getDeleteCount(), result.size() - off));

            // Remove deleted pieces
            for (int i = 0; i < del; i++) {
                result.remove(off);
            }
            // Insert new pieces
            List<Piece> inserted = chunk.getInsertedPieces();
            if (inserted != null && !inserted.isEmpty()) {
                result.addAll(off, inserted);
            }
        }
        return result;
    }

    /**
     * Computes the list of diff chunks needed to transform base into target.
     */
    public static List<PieceDiffChunk> computeDiff(List<Piece> base, List<Piece> target) {
        if (base == null) base = Collections.emptyList();
        if (target == null) target = Collections.emptyList();

        int n = base.size();
        int m = target.size();

        // 1. Common prefix
        int prefix = 0;
        while (prefix < n && prefix < m && piecesEqual(base.get(prefix), target.get(prefix))) {
            prefix++;
        }

        // 2. Common suffix
        int suffix = 0;
        while (suffix < (n - prefix) && suffix < (m - prefix) &&
                piecesEqual(base.get(n - 1 - suffix), target.get(m - 1 - suffix))) {
            suffix++;
        }

        // If prefix + suffix cover everything and lengths are equal -> identical
        if (prefix + suffix == n && prefix + suffix == m) {
            return Collections.emptyList();
        }

        List<Piece> midBase = base.subList(prefix, n - suffix);
        List<Piece> midTarget = target.subList(prefix, m - suffix);

        // If midBase is empty, simple insertion at prefix
        if (midBase.isEmpty()) {
            return List.of(new PieceDiffChunk(prefix, 0, midTarget));
        }
        // If midTarget is empty, simple deletion at prefix
        if (midTarget.isEmpty()) {
            return List.of(new PieceDiffChunk(prefix, midBase.size(), Collections.emptyList()));
        }

        // General case: LCS on midBase and midTarget
        int bn = midBase.size();
        int tm = midTarget.size();
        int[][] dp = new int[bn + 1][tm + 1];
        for (int i = 0; i < bn; i++) {
            for (int j = 0; j < tm; j++) {
                if (piecesEqual(midBase.get(i), midTarget.get(j))) {
                    dp[i + 1][j + 1] = dp[i][j] + 1;
                } else {
                    dp[i + 1][j + 1] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        // Backtrack to find matching pairs (i, j)
        List<int[]> matches = new ArrayList<>();
        int bi = bn, tj = tm;
        while (bi > 0 && tj > 0) {
            if (piecesEqual(midBase.get(bi - 1), midTarget.get(tj - 1))) {
                matches.add(new int[]{bi - 1, tj - 1});
                bi--;
                tj--;
            } else if (dp[bi - 1][tj] >= dp[bi][tj - 1]) {
                bi--;
            } else {
                tj--;
            }
        }
        Collections.reverse(matches);

        // Generate chunks between matches
        List<PieceDiffChunk> chunks = new ArrayList<>();
        int lastBase = 0;
        int lastTarget = 0;

        for (int[] match : matches) {
            int matchBase = match[0];
            int matchTarget = match[1];

            if (matchBase > lastBase || matchTarget > lastTarget) {
                int delCount = matchBase - lastBase;
                List<Piece> ins = new ArrayList<>(midTarget.subList(lastTarget, matchTarget));
                chunks.add(new PieceDiffChunk(prefix + lastBase, delCount, ins));
            }
            lastBase = matchBase + 1;
            lastTarget = matchTarget + 1;
        }

        if (lastBase < bn || lastTarget < tm) {
            int delCount = bn - lastBase;
            List<Piece> ins = new ArrayList<>(midTarget.subList(lastTarget, tm));
            chunks.add(new PieceDiffChunk(prefix + lastBase, delCount, ins));
        }

        return chunks;
    }

    private static boolean piecesEqual(Piece a, Piece b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    @Override
    public String toString() {
        return "PieceDiffChunk{" +
                "offset=" + offset +
                ", deleteCount=" + deleteCount +
                ", insertedCount=" + (insertedPieces != null ? insertedPieces.size() : 0) +
                '}';
    }
}
