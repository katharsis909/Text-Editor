package com.texteditor.backend.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an immutable snapshot in the document's version history.
 * Per requirement:
 * - Simply stores all piece directory descriptors for that version.
 * - Ineffective but simple for now, as there likely won't be many versions and not much changes in directories.
 */
public class VersionSnapshot {

    private final int versionId;
    private final String timestamp;
    private final String description;
    private final int totalLength;
    private final int piecesCount;
    private final List<Piece> pieceDirectory;

    public VersionSnapshot(int versionId, String description, int totalLength, List<Piece> pieceDirectory) {
        this.versionId = versionId;
        this.timestamp = Instant.now().toString();
        this.description = description;
        this.totalLength = totalLength;
        this.pieceDirectory = new ArrayList<>(pieceDirectory != null ? pieceDirectory : List.of());
        this.piecesCount = this.pieceDirectory.size();
    }

    public int getVersionId() {
        return versionId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }

    public int getTotalLength() {
        return totalLength;
    }

    public int getPiecesCount() {
        return piecesCount;
    }

    public List<Piece> getPieceDirectory() {
        return new ArrayList<>(pieceDirectory);
    }
}
