package com.texteditor.backend;

import com.texteditor.backend.model.BufferType;
import com.texteditor.backend.model.EditDescription;
import com.texteditor.backend.model.Piece;
import com.texteditor.backend.service.PieceTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PieceTableTest {

    private PieceTableService service;

    @BeforeEach
    void setUp() {
        service = new PieceTableService();
    }

    @Test
    @DisplayName("1. First time add original string into original buffer")
    void testInitialization() {
        String initial = "The quick brown fox jumps over the lazy dog.";
        EditDescription desc = service.initialize(initial);

        assertEquals(initial.length(), service.getTotalLength());
        assertEquals(initial, service.getText());
        assertEquals(1, service.getPieceDirectory().size());
        assertEquals(BufferType.ORIGINAL, service.getPieceDirectory().get(0).getBuffer());
        assertEquals(0, service.getPieceDirectory().get(0).getStart());
        assertEquals(initial.length(), service.getPieceDirectory().get(0).getLength());
        assertEquals(initial.length(), service.getOriginalBufferSize());
        assertEquals(0, service.getAddBufferSize());
        assertNotNull(desc);
    }

    @Test
    @DisplayName("2. Middle additions: Add pieces without moving existing buffers")
    void testMiddleInsertions() {
        service.initialize("HelloWorld"); // length 10
        assertEquals(1, service.getPieceDirectory().size());

        // Insert in middle at position 5: "Beautiful" -> "HelloBeautifulWorld"
        EditDescription insert1 = service.insert(5, "Beautiful");
        assertEquals("HelloBeautifulWorld", service.getText());
        assertEquals(3, service.getPieceDirectory().size());
        assertEquals(9, service.getAddBufferSize());
        assertEquals(10, service.getOriginalBufferSize()); // Original untouched!

        // Insert at index 13 (between 'u' and 'l' in 'Beautiful')
        EditDescription insert2 = service.insert(13, "-VERY-");
        assertEquals("HelloBeautifu-VERY-lWorld", service.getText());
        assertEquals(5, service.getPieceDirectory().size());

        // Verify piece descriptors store order, start, and length
        List<Piece> pieces = service.getPieceDirectory();
        int runningLen = 0;
        for (Piece p : pieces) {
            assertTrue(p.getLength() > 0);
            runningLen += p.getLength();
        }
        assertEquals(service.getTotalLength(), runningLen);
    }

    @Test
    @DisplayName("2B. Logical slice extraction across fragmented pieces")
    void testGetSlice() {
        service.initialize("0123456789");
        service.insert(5, "ABCDE"); // "01234ABCDE56789"
        assertEquals("01234ABCDE56789", service.getText());

        // Extract slice spanning both pieces
        String slice = service.getSlice(3, 6); // from index 3 for 6 chars: "34" + "ABCD" = "34ABCD"
        assertEquals("34ABCD", slice);

        // Extract slice at boundary
        assertEquals("012", service.getSlice(0, 3));
        assertEquals("789", service.getSlice(12, 3));
    }

    @Test
    @DisplayName("3A. Deleting a part of a single piece in the middle")
    void testDeletePartOfPiece() {
        service.initialize("ABCDEFGHIJ"); // 10 chars
        // Delete "DEF" (pos 3, len 3)
        EditDescription delDesc = service.delete(3, 3);

        assertEquals("ABCGHIJ", service.getText());
        assertEquals(7, service.getTotalLength());
        // Piece #0 was split into 2 pieces: "ABC" (len 3) and "GHIJ" (len 4)
        assertEquals(2, service.getPieceDirectory().size());
        assertEquals(10, service.getOriginalBufferSize()); // Buffer still has 10 chars untouched!
        assertEquals("PIECE_DIRECTORY", delDesc.getAffectedBuffer());
    }

    @Test
    @DisplayName("3B. Deleting a complete piece entirely")
    void testDeleteCompletePiece() {
        service.initialize("AAA");
        service.insert(3, "BBB"); // Add buffer: "BBB"
        service.insert(6, "CCC"); // Pieces: AAA, BBB, CCC -> total 9 chars
        assertEquals("AAABBBCCC", service.getText());
        assertEquals(3, service.getPieceDirectory().size());

        // Delete "BBB" completely (pos 3, len 3)
        service.delete(3, 3);
        assertEquals("AAACCC", service.getText());
        assertEquals(2, service.getPieceDirectory().size());
        // The Add buffer still retains "BBB" and "CCC" untouched!
        assertEquals(6, service.getAddBufferSize());
    }

    @Test
    @DisplayName("3C. Deleting multiple pieces half-half (spanning across multiple pieces)")
    void testDeleteMultiplePiecesHalfHalf() {
        service.initialize("12345");          // Piece 0: "12345"
        service.insert(5, "ABCDE");          // Piece 1: "ABCDE"
        service.insert(10, "XYZWV");         // Piece 2: "XYZWV"
        assertEquals("12345ABCDEXYZWV", service.getText()); // 15 chars

        // Delete from index 3 (inside Piece 0) for 9 characters:
        // Spans: "45" (from Piece 0), all of "ABCDE" (Piece 1), and "XY" (from Piece 2)
        // Expected remainder: "123" + "ZWV" = "123ZWV"
        EditDescription delDesc = service.delete(3, 9);

        assertEquals("123ZWV", service.getText());
        assertEquals(6, service.getTotalLength());
        assertEquals(2, service.getPieceDirectory().size()); // Piece 0 left remainder + Piece 2 right remainder

        // Piece 0 has "123" (len 3)
        assertEquals(3, service.getPieceDirectory().get(0).getLength());
        // Piece 1 has "ZWV" (len 3, starts at 2 inside Piece 2's add buffer slice)
        assertEquals(3, service.getPieceDirectory().get(1).getLength());

        // Buffers remain completely intact
        assertEquals(5, service.getOriginalBufferSize());
        assertEquals(10, service.getAddBufferSize());
    }

    @Test
    @DisplayName("4. Plan 1 Persistence: Save and reload consolidated text whole to disk")
    void testPlan1DiskPersistence() throws IOException {
        service.initialize("InitialLineContent-");
        service.insert(19, "MiddleAddition-");
        service.insert(34, "EndContent");
        service.delete(7, 4); // delete "Line"

        String currentText = service.getText();
        String tempFile = "target/test_output.txt";

        // Save
        String saveMsg = service.saveToDiskPlan1(tempFile);
        assertNotNull(saveMsg);
        assertTrue(new File(tempFile).exists());

        // Reload into a new service
        PieceTableService newService = new PieceTableService();
        newService.loadFromDiskPlan1(tempFile);

        assertEquals(currentText, newService.getText());
        assertEquals(currentText.length(), newService.getTotalLength());
    }

    @Test
    @DisplayName("5A. Add Undo & Redo: Revert added piece from directory without mutating buffers")
    void testAddUndoRedo() {
        service.initialize("Hello World");
        assertEquals(1, service.getPieceDirectory().size());
        assertEquals(0, service.getUndoStackSize());

        // Insert "Beautiful " at pos 6
        service.insert(6, "Beautiful ");
        assertEquals("Hello Beautiful World", service.getText());
        assertEquals(3, service.getPieceDirectory().size());
        assertEquals(1, service.getUndoStackSize());
        assertTrue(service.canUndo());
        assertFalse(service.canRedo());

        // Undo the insert: piece table restores to original state without shrinking buffers!
        EditDescription undoDesc = service.undo();
        assertEquals("Hello World", service.getText());
        assertEquals(1, service.getPieceDirectory().size());
        assertEquals(0, service.getUndoStackSize());
        assertEquals(1, service.getRedoStackSize());
        assertTrue(service.canRedo());
        // Verify Add buffer still retains "Beautiful " (append-only integrity)
        assertEquals(11, service.getOriginalBufferSize());
        assertEquals(10, service.getAddBufferSize());

        // Redo the insert: re-applies the pieces to directory
        EditDescription redoDesc = service.redo();
        assertEquals("Hello Beautiful World", service.getText());
        assertEquals(3, service.getPieceDirectory().size());
        assertTrue(service.canUndo());
        assertFalse(service.canRedo());
    }

    @Test
    @DisplayName("5B. Delete Part of Piece Undo & Redo")
    void testDeletePartOfPieceUndoRedo() {
        service.initialize("ABCDEFGHIJ"); // 10 chars
        // Delete "DEF" at pos 3, len 3 -> "ABCGHIJ"
        service.delete(3, 3);
        assertEquals("ABCGHIJ", service.getText());
        assertEquals(2, service.getPieceDirectory().size());
        assertTrue(service.canUndo());

        // Undo Delete: Restores the original intact piece
        service.undo();
        assertEquals("ABCDEFGHIJ", service.getText());
        assertEquals(1, service.getPieceDirectory().size());

        // Redo Delete: Cuts "DEF" again
        service.redo();
        assertEquals("ABCGHIJ", service.getText());
        assertEquals(2, service.getPieceDirectory().size());
    }

    @Test
    @DisplayName("5C. Delete Complete Piece Undo & Redo")
    void testDeleteCompletePieceUndoRedo() {
        service.initialize("AAA");
        service.insert(3, "BBB");
        service.insert(6, "CCC");
        assertEquals("AAABBBCCC", service.getText());
        assertEquals(3, service.getPieceDirectory().size());

        // Delete "BBB" completely
        service.delete(3, 3);
        assertEquals("AAACCC", service.getText());
        assertEquals(2, service.getPieceDirectory().size());

        // Undo Delete complete piece: Restores the sequence [AAA, BBB, CCC]
        service.undo();
        assertEquals("AAABBBCCC", service.getText());
        assertEquals(3, service.getPieceDirectory().size());

        // Redo Delete complete piece
        service.redo();
        assertEquals("AAACCC", service.getText());
        assertEquals(2, service.getPieceDirectory().size());
    }

    @Test
    @DisplayName("5D. Delete Multiple Pieces Half-Half Undo & Redo (Sequence of pieces restoration)")
    void testDeleteMultiplePiecesHalfHalfUndoRedo() {
        service.initialize("12345");          // Piece 0: "12345"
        service.insert(5, "ABCDE");          // Piece 1: "ABCDE"
        service.insert(10, "XYZWV");         // Piece 2: "XYZWV"
        assertEquals("12345ABCDEXYZWV", service.getText());
        assertEquals(3, service.getPieceDirectory().size());

        // Delete from pos 3 for 9 chars: spans "45" + "ABCDE" + "XY" -> leaves "123ZWV"
        service.delete(3, 9);
        assertEquals("123ZWV", service.getText());
        assertEquals(2, service.getPieceDirectory().size());

        // Undo Delete: Restores the exact sequence of whole and half pieces originally present
        service.undo();
        assertEquals("12345ABCDEXYZWV", service.getText());
        assertEquals(3, service.getPieceDirectory().size());
        assertEquals("12345", service.getSlice(0, 5));
        assertEquals("ABCDE", service.getSlice(5, 5));
        assertEquals("XYZWV", service.getSlice(10, 5));

        // Redo Delete: Re-cuts the half-half range
        service.redo();
        assertEquals("123ZWV", service.getText());
        assertEquals(2, service.getPieceDirectory().size());
    }
}
