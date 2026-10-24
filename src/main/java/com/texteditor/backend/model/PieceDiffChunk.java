package com.texteditor.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a changed interval / piece diff in the Piece Directory.
 * Describes that starting at `offset` in the base pieceDirectory,
 * `deleteCount` pieces were replaced by `insertedPieces`.
 *
 * Implemented WITHOUT expensive string/list LCS algorithms:
 * Directly identifies the changed piece interval (or parts of pieces)
 * in O(pieces) linear time with zero matrix allocations.
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
     * Applies diff chunks onto a base piece directory.
     * Chunks are sorted in descending order of offset so that changes
     * at higher indices do not disrupt lower indices.
     */
    public static List<Piece> applyDiff(List<Piece> base, List<PieceDiffChunk> chunks) {
        if (base == null) base = Collections.emptyList();
        List<Piece> result = new ArrayList<>(base);
        if (chunks == null || chunks.isEmpty()) {
            return result;
        }

        List<PieceDiffChunk> sorted = new ArrayList<>(chunks);
        sorted.sort((a, b) -> Integer.compare(b.getOffset(), a.getOffset()));

        for (PieceDiffChunk chunk : sorted) {
            int off = Math.max(0, Math.min(chunk.getOffset(), result.size()));
            int del = Math.max(0, Math.min(chunk.getDeleteCount(), result.size() - off));

            for (int i = 0; i < del; i++) {
                result.remove(off);
            }
            List<Piece> inserted = chunk.getInsertedPieces();
            if (inserted != null && !inserted.isEmpty()) {
                result.addAll(off, inserted);
            }
        }
        return result;
    }

    /**
     * Directly identifies the changed piece interval between base and target in O(pieces).
     * Zero LCS, zero DP table: directly detects which pieces or parts of pieces were replaced.
     */
    public static List<PieceDiffChunk> computeDiff(List<Piece> base, List<Piece> target) {
        if (base == null) base = Collections.emptyList();
        if (target == null) target = Collections.emptyList();

        int n = base.size();
        int m = target.size();

        // 1. Common prefix of pieces from left
        int start = 0;
        while (start < n && start < m && piecesEqual(base.get(start), target.get(start))) {
            start++;
        }

        // 2. Common suffix of pieces from right
        int endBase = n - 1;
        int endTarget = m - 1;
        while (endBase >= start && endTarget >= start && piecesEqual(base.get(endBase), target.get(endTarget))) {
            endBase--;
            endTarget--;
        }

        // If no differences found, return empty list
        if (start > endBase && start > endTarget) {
            return Collections.emptyList();
        }

        // The changed interval in base: pieces from start to endBase
        int deleteCount = Math.max(0, endBase - start + 1);

        // The replacement pieces in target: pieces or parts of pieces from start to endTarget
        List<Piece> replacementPieces = new ArrayList<>();
        if (start <= endTarget) {
            replacementPieces.addAll(target.subList(start, endTarget + 1));
        }

        return List.of(new PieceDiffChunk(start, deleteCount, replacementPieces));
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
