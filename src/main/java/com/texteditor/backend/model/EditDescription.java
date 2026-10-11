package com.texteditor.backend.model;

import java.time.Instant;

/**
 * Describes changes made to the text and piece directory (where it was made, buffer, and piece count).
 */
public class EditDescription {
    private String opType;
    private int position;
    private int length;
    private String textPreview;
    private String affectedBuffer;
    private int piecesBeforeCount;
    private int piecesAfterCount;
    private String details;
    private Instant timestamp;

    public EditDescription() {
        this.timestamp = Instant.now();
    }

    public EditDescription(String opType, int position, int length, String textPreview,
                           String affectedBuffer, int piecesBeforeCount, int piecesAfterCount, String details) {
        this.opType = opType;
        this.position = position;
        this.length = length;
        this.textPreview = textPreview;
        this.affectedBuffer = affectedBuffer;
        this.piecesBeforeCount = piecesBeforeCount;
        this.piecesAfterCount = piecesAfterCount;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public String getTextPreview() {
        return textPreview;
    }

    public void setTextPreview(String textPreview) {
        this.textPreview = textPreview;
    }

    public String getAffectedBuffer() {
        return affectedBuffer;
    }

    public void setAffectedBuffer(String affectedBuffer) {
        this.affectedBuffer = affectedBuffer;
    }

    public int getPiecesBeforeCount() {
        return piecesBeforeCount;
    }

    public void setPiecesBeforeCount(int piecesBeforeCount) {
        this.piecesBeforeCount = piecesBeforeCount;
    }

    public int getPiecesAfterCount() {
        return piecesAfterCount;
    }

    public void setPiecesAfterCount(int piecesAfterCount) {
        this.piecesAfterCount = piecesAfterCount;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "[" + opType + " @ pos " + position + "] " +
                "Length: " + length + " | " +
                "Buffer: " + affectedBuffer + " | " +
                "Pieces: " + piecesBeforeCount + " -> " + piecesAfterCount + " | " +
                details + " | Preview: '" + textPreview + "'";
    }
}
