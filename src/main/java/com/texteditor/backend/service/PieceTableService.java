package com.texteditor.backend.service;

import com.texteditor.backend.model.BufferType;
import com.texteditor.backend.model.EditDescription;
import com.texteditor.backend.model.Piece;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class PieceTableService {

    // Immutable Original Buffer
    private StringBuilder originalBuffer = new StringBuilder();

    // Append-Only Add Buffer
    private StringBuilder addBuffer = new StringBuilder();

    // Piece Directory: Ordered list of piece descriptors defining read sequence
    private List<Piece> pieceDirectory = new ArrayList<>();

    // Log of changes made
    private List<EditDescription> history = new ArrayList<>();

    public PieceTableService() {}

    /**
     * First time add original string.
     */
    public synchronized EditDescription initialize(String originalText) {
        this.originalBuffer = new StringBuilder(originalText != null ? originalText : "");
        this.addBuffer = new StringBuilder();
        this.pieceDirectory = new ArrayList<>();
        this.history = new ArrayList<>();

        if (this.originalBuffer.length() > 0) {
            this.pieceDirectory.add(new Piece(BufferType.ORIGINAL, 0, this.originalBuffer.length()));
        }

        EditDescription desc = new EditDescription(
                "INITIALIZE",
                0,
                this.originalBuffer.length(),
                getPreview(this.originalBuffer.toString()),
                "ORIGINAL",
                0,
                this.pieceDirectory.size(),
                "Loaded initial text into immutable original buffer"
        );
        this.history.add(desc);
        return desc;
    }

    /**
     * Get total document length by summing lengths of all pieces in directory.
     */
    public synchronized int getTotalLength() {
        int sum = 0;
        for (Piece p : pieceDirectory) {
            sum += p.getLength();
        }
        return sum;
    }

    /**
     * Resolve character slice from specified buffer without modifying either buffer.
     */
    private String resolvePieceText(Piece piece) {
        StringBuilder buf = (piece.getBuffer() == BufferType.ORIGINAL) ? originalBuffer : addBuffer;
        return buf.substring(piece.getStart(), piece.getStart() + piece.getLength());
    }

    /**
     * Read the full text in order according to the piece directory.
     */
    public synchronized String getText() {
        StringBuilder sb = new StringBuilder();
        for (Piece p : pieceDirectory) {
            sb.append(resolvePieceText(p));
        }
        return sb.toString();
    }

    /**
     * Extracts a logical substring slice [start, start + length)
     * by traversing only the relevant pieces without allocating the entire document.
     */
    public synchronized String getSlice(int start, int length) {
        int total = getTotalLength();
        start = Math.max(0, Math.min(total, start));
        length = Math.max(0, Math.min(total - start, length));
        if (length == 0 || pieceDirectory.isEmpty()) {
            return "";
        }

        int end = start + length;
        StringBuilder result = new StringBuilder(length);
        int[] offsets = computeCumulativeOffsets();
        int startPieceIdx = binarySearchPieceIndex(offsets, start);

        for (int i = startPieceIdx; i < pieceDirectory.size(); i++) {
            int pStart = offsets[i];
            Piece p = pieceDirectory.get(i);
            int pEnd = pStart + p.getLength();

            if (pEnd > start && pStart < end) {
                int overlapStart = Math.max(0, start - pStart);
                int overlapEnd = Math.min(p.getLength(), end - pStart);
                StringBuilder buf = (p.getBuffer() == BufferType.ORIGINAL) ? originalBuffer : addBuffer;
                result.append(buf.substring(p.getStart() + overlapStart, p.getStart() + overlapEnd));
            }

            if (pStart >= end) {
                break;
            }
        }
        return result.toString();
    }

    /**
     * Compute cumulative offsets array for binary search.
     * offsets[i] is the starting global character index of pieceDirectory[i].
     */
    private int[] computeCumulativeOffsets() {
        int n = pieceDirectory.size();
        int[] offsets = new int[n];
        int current = 0;
        for (int i = 0; i < n; i++) {
            offsets[i] = current;
            current += pieceDirectory.get(i).getLength();
        }
        return offsets;
    }

    /**
     * Binary search to find the index of the piece containing global character position.
     */
    private int binarySearchPieceIndex(int[] offsets, int globalPos) {
        int low = 0;
        int high = offsets.length - 1;
        int result = -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int pieceStart = offsets[mid];
            int pieceLen = pieceDirectory.get(mid).getLength();
            int pieceEnd = pieceStart + pieceLen;

            if (globalPos >= pieceStart && globalPos < pieceEnd) {
                return mid;
            } else if (globalPos < pieceStart) {
                high = mid - 1;
            } else {
                result = mid; // fallback for boundary edge
                low = mid + 1;
            }
        }
        return result != -1 ? result : Math.max(0, offsets.length - 1);
    }

    /**
     * INSERTION: Keep adding pieces on additions.
     * Appends text to Add Buffer and rewrites the piece directory to direct what part of that
     * piece to read in what order.
     */
    public synchronized EditDescription insert(int position, String text) {
        if (text == null || text.isEmpty()) {
            return new EditDescription("INSERT", position, 0, "", "ADD", pieceDirectory.size(), pieceDirectory.size(), "No-op empty string");
        }

        int totalLen = getTotalLength();
        position = Math.max(0, Math.min(totalLen, position));

        // 1. Append to Add Buffer (Append-Only)
        int addStart = addBuffer.length();
        addBuffer.append(text);
        Piece newPiece = new Piece(BufferType.ADD, addStart, text.length());

        int piecesBefore = pieceDirectory.size();
        String details;

        if (pieceDirectory.isEmpty()) {
            pieceDirectory.add(newPiece);
            details = "Inserted as the only piece";
        } else if (position == 0) {
            pieceDirectory.add(0, newPiece);
            details = "Prepended to start of piece directory";
        } else if (position == totalLen) {
            pieceDirectory.add(newPiece);
            details = "Appended to end of piece directory";
        } else {
            // Middle addition: Use binary search to locate target piece to split
            int[] offsets = computeCumulativeOffsets();
            int targetIdx = binarySearchPieceIndex(offsets, position);
            Piece targetPiece = pieceDirectory.get(targetIdx);
            int pieceStartGlobal = offsets[targetIdx];
            int offsetInPiece = position - pieceStartGlobal;

            List<Piece> splitPieces = new ArrayList<>();
            // Left portion
            if (offsetInPiece > 0) {
                splitPieces.add(new Piece(targetPiece.getBuffer(), targetPiece.getStart(), offsetInPiece));
            }
            // Newly added piece in middle
            splitPieces.add(newPiece);
            // Right portion
            int remainingLen = targetPiece.getLength() - offsetInPiece;
            if (remainingLen > 0) {
                splitPieces.add(new Piece(targetPiece.getBuffer(), targetPiece.getStart() + offsetInPiece, remainingLen));
            }

            // Replace target piece with split subpieces
            pieceDirectory.remove(targetIdx);
            pieceDirectory.addAll(targetIdx, splitPieces);

            details = "Split piece #" + targetIdx + " (" + targetPiece.getBuffer() + ") at offset " + offsetInPiece;
        }

        EditDescription desc = new EditDescription(
                "INSERT",
                position,
                text.length(),
                getPreview(text),
                "ADD",
                piecesBefore,
                pieceDirectory.size(),
                details
        );
        history.add(desc);
        return desc;
    }

    /**
     * DELETION:
     * - The deleted piece still remains in the piece buffer as it is (buffers are NEVER deleted or shrunk).
     * - The piece directory gets rewritten directing what part of that piece to read in what order:
     *     - deleting a part of piece
     *     - deleting a complete piece
     *     - deleting multiple pieces half half
     * - Uses binary search to travel to the leftmost piece inside directory to delete.
     * - All handled in a single unified code block.
     */
    public synchronized EditDescription delete(int position, int length) {
        int totalLen = getTotalLength();
        position = Math.max(0, Math.min(totalLen, position));
        length = Math.max(0, Math.min(totalLen - position, length));

        if (length == 0 || pieceDirectory.isEmpty()) {
            return new EditDescription("DELETE", position, 0, "", "NONE", pieceDirectory.size(), pieceDirectory.size(), "No-op delete");
        }

        int delStart = position;
        int delEnd = position + length;
        int piecesBefore = pieceDirectory.size();

        // Binary search to find leftmost piece overlapping with delStart
        int[] offsets = computeCumulativeOffsets();
        int leftPieceIdx = binarySearchPieceIndex(offsets, delStart);

        // Binary search to find rightmost piece overlapping with delEnd - 1
        int rightPieceIdx = binarySearchPieceIndex(offsets, delEnd - 1);

        // Collect deleted text preview before updating piece directory
        String fullText = getText();
        String deletedPreview = fullText.substring(delStart, delEnd);

        // Single unified code block rewriting the piece directory
        List<Piece> newDirectory = new ArrayList<>();

        // 1. Retain all pieces strictly before the leftmost affected piece
        for (int i = 0; i < leftPieceIdx; i++) {
            newDirectory.add(pieceDirectory.get(i));
        }

        // 2. Handle affected pieces (leftmost through rightmost)
        if (leftPieceIdx == rightPieceIdx) {
            // Case A: Deletion falls entirely within a single piece (deleting part of a piece or the whole piece)
            Piece p = pieceDirectory.get(leftPieceIdx);
            int pStartGlobal = offsets[leftPieceIdx];
            int pEndGlobal = pStartGlobal + p.getLength();

            // Left remainder before delStart
            if (delStart > pStartGlobal) {
                int keepLen = delStart - pStartGlobal;
                newDirectory.add(new Piece(p.getBuffer(), p.getStart(), keepLen));
            }
            // Right remainder after delEnd
            if (delEnd < pEndGlobal) {
                int skipLen = delEnd - pStartGlobal;
                int keepLen = pEndGlobal - delEnd;
                newDirectory.add(new Piece(p.getBuffer(), p.getStart() + skipLen, keepLen));
            }
            // If delStart <= pStartGlobal and delEnd >= pEndGlobal, the piece is completely dropped
        } else {
            // Case B: Deletion spans across multiple pieces (deleting multiple pieces half-half or whole)
            Piece leftP = pieceDirectory.get(leftPieceIdx);
            int leftPStartGlobal = offsets[leftPieceIdx];
            if (delStart > leftPStartGlobal) {
                int keepLen = delStart - leftPStartGlobal;
                newDirectory.add(new Piece(leftP.getBuffer(), leftP.getStart(), keepLen));
            }

            // All intermediate pieces (leftPieceIdx + 1 .. rightPieceIdx - 1) are completely omitted!

            Piece rightP = pieceDirectory.get(rightPieceIdx);
            int rightPStartGlobal = offsets[rightPieceIdx];
            int rightPEndGlobal = rightPStartGlobal + rightP.getLength();
            if (delEnd < rightPEndGlobal) {
                int skipLen = delEnd - rightPStartGlobal;
                int keepLen = rightPEndGlobal - delEnd;
                newDirectory.add(new Piece(rightP.getBuffer(), rightP.getStart() + skipLen, keepLen));
            }
        }

        // 3. Retain all pieces strictly after the rightmost affected piece
        for (int i = rightPieceIdx + 1; i < pieceDirectory.size(); i++) {
            newDirectory.add(pieceDirectory.get(i));
        }

        this.pieceDirectory = newDirectory;

        String details = "Binary search located left piece #" + leftPieceIdx + " and right piece #" + rightPieceIdx +
                ". Rewrote piece directory (buffers untouched in RAM).";

        EditDescription desc = new EditDescription(
                "DELETE",
                position,
                length,
                getPreview(deletedPreview),
                "PIECE_DIRECTORY",
                piecesBefore,
                pieceDirectory.size(),
                details
        );
        history.add(desc);
        return desc;
    }

    /**
     * Plan 1 Persistence:
     * For changing it in the middle, load the whole piece directory as one and rewrite it whole to disk.
     */
    public synchronized String saveToDiskPlan1(String filePath) throws IOException {
        String consolidated = getText();
        Path path = Path.of(filePath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, consolidated, StandardCharsets.UTF_8);
        return "Saved " + consolidated.length() + " characters (" + pieceDirectory.size() + " pieces) to " + filePath;
    }

    public synchronized String loadFromDiskPlan1(String filePath) throws IOException {
        String content = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        initialize(content);
        return "Loaded " + content.length() + " characters from " + filePath + " into piece table.";
    }

    public synchronized List<Piece> getPieceDirectory() {
        return new ArrayList<>(pieceDirectory);
    }

    public synchronized List<EditDescription> getHistory() {
        return new ArrayList<>(history);
    }

    public synchronized int getOriginalBufferSize() {
        return originalBuffer.length();
    }

    public synchronized int getAddBufferSize() {
        return addBuffer.length();
    }

    private String getPreview(String s) {
        if (s == null) return "";
        return s.length() > 30 ? s.substring(0, 30) + "..." : s;
    }
}
