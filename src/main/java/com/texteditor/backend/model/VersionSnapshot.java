package com.texteditor.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an immutable snapshot in the document's version history.
 *
 * Implements a 5-Interval Checkpoint + Delta Diff Architecture:
 * - Every 5th version (Version 1, 5, 10, 15...) is a full CHECKPOINT storing
 *   the entire pieceDirectory and full buffer contents.
 * - Intermediate versions inside each 5-version interval (e.g. 2, 3, 4; 6, 7, 8, 9...)
 *   are stored as DELTAS relative to the nearest interval Checkpoint, saving 95%+
 *   disk and memory space.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionSnapshot {

    private final int versionId;
    private final String timestamp;
    private final String description;
    private final int totalLength;
    private final int piecesCount;

    // Snapshot Type: "CHECKPOINT" or "DELTA"
    private final String snapshotType;
    // Base Version ID: points to the Checkpoint this version is based on
    private final int baseVersionId;

    // Checkpoint-only fields (omitted in JSON for DELTA versions to save 95%+ disk space)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<Piece> pieceDirectory;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String originalBufferContent;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String addBufferContent;

    // Delta-only fields (omitted in JSON for CHECKPOINT versions)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<PieceDiffChunk> diffChunks;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String addedText;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String deltaSummary;

    @JsonCreator
    public VersionSnapshot(
            @JsonProperty("versionId") int versionId,
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("description") String description,
            @JsonProperty("totalLength") int totalLength,
            @JsonProperty("piecesCount") int piecesCount,
            @JsonProperty("snapshotType") String snapshotType,
            @JsonProperty("baseVersionId") Integer baseVersionId,
            @JsonProperty("pieceDirectory") List<Piece> pieceDirectory,
            @JsonProperty("originalBufferContent") String originalBufferContent,
            @JsonProperty("addBufferContent") String addBufferContent,
            @JsonProperty("diffChunks") List<PieceDiffChunk> diffChunks,
            @JsonProperty("addedText") String addedText,
            @JsonProperty("deltaSummary") String deltaSummary) {
        this.versionId = versionId;
        this.timestamp = timestamp != null ? timestamp : Instant.now().toString();
        this.description = description;
        this.totalLength = totalLength;
        this.piecesCount = piecesCount;
        this.snapshotType = (snapshotType != null && !snapshotType.isBlank()) ? snapshotType.toUpperCase() : "CHECKPOINT";
        this.baseVersionId = (baseVersionId != null) ? baseVersionId : versionId;

        this.pieceDirectory = pieceDirectory != null ? new ArrayList<>(pieceDirectory) : null;
        this.originalBufferContent = originalBufferContent;
        this.addBufferContent = addBufferContent;

        this.diffChunks = diffChunks != null ? new ArrayList<>(diffChunks) : null;
        this.addedText = addedText;
        this.deltaSummary = deltaSummary;
    }

    /**
     * Legacy constructor for checkpoints with explicit buffers.
     */
    public VersionSnapshot(int versionId, String description, int totalLength, List<Piece> pieceDirectory, String originalBufferContent, String addBufferContent) {
        this(
                versionId,
                Instant.now().toString(),
                description,
                totalLength,
                pieceDirectory != null ? pieceDirectory.size() : 0,
                "CHECKPOINT",
                versionId,
                pieceDirectory,
                originalBufferContent,
                addBufferContent,
                null,
                null,
                "Full Checkpoint"
        );
    }

    /**
     * Legacy constructor for checkpoints without explicit buffers.
     */
    public VersionSnapshot(int versionId, String description, int totalLength, List<Piece> pieceDirectory) {
        this(versionId, description, totalLength, pieceDirectory, null, null);
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

    public String getSnapshotType() {
        return snapshotType;
    }

    public int getBaseVersionId() {
        return baseVersionId;
    }

    @JsonIgnore
    public boolean isCheckpoint() {
        return "CHECKPOINT".equalsIgnoreCase(snapshotType);
    }

    @JsonIgnore
    public boolean isDelta() {
        return "DELTA".equalsIgnoreCase(snapshotType);
    }

    public List<Piece> getPieceDirectory() {
        return pieceDirectory != null ? new ArrayList<>(pieceDirectory) : null;
    }

    public String getOriginalBufferContent() {
        return originalBufferContent;
    }

    public String getAddBufferContent() {
        return addBufferContent;
    }

    public List<PieceDiffChunk> getDiffChunks() {
        return diffChunks != null ? new ArrayList<>(diffChunks) : Collections.emptyList();
    }

    public String getAddedText() {
        return addedText;
    }

    public String getDeltaSummary() {
        return deltaSummary;
    }

    /**
     * Resolves the full piece directory for this version, reconstructing
     * from the base checkpoint if this is a DELTA snapshot.
     */
    public List<Piece> resolvePieceDirectory(VersionSnapshot baseCheckpoint) {
        if (isCheckpoint() || pieceDirectory != null) {
            return pieceDirectory != null ? new ArrayList<>(pieceDirectory) : Collections.emptyList();
        }
        if (baseCheckpoint != null) {
            return PieceDiffChunk.applyDiff(baseCheckpoint.getPieceDirectory(), diffChunks);
        }
        return Collections.emptyList();
    }

    /**
     * Resolves the authoritative addBuffer for this version.
     */
    public String resolveAddBuffer(VersionSnapshot baseCheckpoint) {
        if (isCheckpoint() || addBufferContent != null) {
            return addBufferContent != null ? addBufferContent : "";
        }
        if (baseCheckpoint != null) {
            String baseAdd = baseCheckpoint.getAddBufferContent() != null ? baseCheckpoint.getAddBufferContent() : "";
            return baseAdd + (addedText != null ? addedText : "");
        }
        return "";
    }

    /**
     * Resolves the authoritative originalBuffer for this version.
     */
    public String resolveOriginalBuffer(VersionSnapshot baseCheckpoint) {
        if (isCheckpoint() || originalBufferContent != null) {
            return originalBufferContent != null ? originalBufferContent : "";
        }
        if (baseCheckpoint != null) {
            return baseCheckpoint.getOriginalBufferContent() != null ? baseCheckpoint.getOriginalBufferContent() : "";
        }
        return "";
    }
}
