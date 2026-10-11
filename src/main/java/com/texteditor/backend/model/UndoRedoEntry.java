package com.texteditor.backend.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an entry in the Undo/Redo stack.
 * In a Piece Table, buffers (Original and Add) are strictly immutable/append-only,
 * so an edit state is completely captured by:
 * 1. The snapshot of the piece directory descriptors before the edit.
 * 2. The affected character range [position, position + length).
 * 3. The operation type ("INSERT", "DELETE").
 * 4. Human-readable description.
 */
public class UndoRedoEntry {

    private final String operationType; // "INSERT", "DELETE"
    private final int position;
    private final int length;
    private final List<Piece> pieceDirectorySnapshot;
    private final String description;

    public UndoRedoEntry(String operationType, int position, int length, List<Piece> pieceDirectorySnapshot, String description) {
        this.operationType = operationType;
        this.position = position;
        this.length = length;
        // Defensive copy of the piece descriptors
        this.pieceDirectorySnapshot = new ArrayList<>(pieceDirectorySnapshot != null ? pieceDirectorySnapshot : List.of());
        this.description = description;
    }

    public String operationType() {
        return operationType;
    }

    public int position() {
        return position;
    }

    public int length() {
        return length;
    }

    public List<Piece> pieceDirectorySnapshot() {
        return new ArrayList<>(pieceDirectorySnapshot);
    }

    public String description() {
        return description;
    }
}
