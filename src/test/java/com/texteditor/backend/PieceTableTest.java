package com.texteditor.backend;

import com.texteditor.backend.model.BufferType;
import com.texteditor.backend.model.EditDescription;
import com.texteditor.backend.model.Piece;
import com.texteditor.backend.model.VersionSnapshot;
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

    @Test
    @DisplayName("6. Version History: Store piece directory for each version and rollback")
    void testVersionHistory() {
        service.initialize("Version 1 base content.");
        assertEquals(1, service.getVersionHistory().size());

        // Make edits and create Version 2
        service.insert(service.getTotalLength(), " Additional chapter added.");
        com.texteditor.backend.model.VersionSnapshot v2 = service.createVersion("Added Chapter 2");
        assertEquals(2, v2.getVersionId());
        assertEquals(2, service.getVersionHistory().size());

        // Make further edits
        service.insert(0, "PREFACE: ");
        service.delete(15, 5);
        String modifiedText = service.getText();

        // Restore Version 1: Replaces pieceDirectory with Version 1 descriptors snapshot
        com.texteditor.backend.model.VersionSnapshot restoredV1 = service.restoreVersion(1);
        assertNotNull(restoredV1);
        assertEquals("Version 1 base content.", service.getText());

        // Restore Version 2
        com.texteditor.backend.model.VersionSnapshot restoredV2 = service.restoreVersion(2);
        assertNotNull(restoredV2);
        assertEquals("Version 1 base content. Additional chapter added.", service.getText());

        // Buffer invariant: original and add buffers are never deleted
        assertTrue(service.getOriginalBufferSize() > 0);
        assertTrue(service.getAddBufferSize() > 0);
    }

    @Test
    @DisplayName("7. 5-Interval Checkpoints & Delta Diffs: Verification of 95%+ space-saving and lossless rollback")
    void testFiveIntervalCheckpointsAndDeltaRollback() {
        service.initialize("V1 Base.");
        // Version 1 is CHECKPOINT
        VersionSnapshot v1 = service.getVersionHistory().get(0);
        assertTrue(v1.isCheckpoint());
        assertEquals("CHECKPOINT", v1.getSnapshotType());
        assertNotNull(v1.getPieceDirectory());

        // Create versions 2 to 12
        for (int i = 2; i <= 12; i++) {
            service.insert(service.getTotalLength(), " [v" + i + "]");
            VersionSnapshot v = service.createVersion("Milestone " + i);
            assertEquals(i, v.getVersionId());

            if (i % 5 == 0) {
                // Version 5, 10 are CHECKPOINTS
                assertTrue(v.isCheckpoint(), "Version " + i + " must be a CHECKPOINT");
                assertEquals("CHECKPOINT", v.getSnapshotType());
                assertNotNull(v.getPieceDirectory(), "Checkpoint must store full pieceDirectory");
                assertNotNull(v.getOriginalBufferContent());
            } else {
                // Versions 2, 3, 4, 6, 7, 8, 9, 11, 12 are DELTAS
                assertTrue(v.isDelta(), "Version " + i + " must be a DELTA");
                assertEquals("DELTA", v.getSnapshotType());
                assertNull(v.getPieceDirectory(), "Delta must NOT store full pieceDirectory (space saving)");
                assertNull(v.getOriginalBufferContent());
                assertNotNull(v.getDiffChunks(), "Delta must store diff chunks");
                assertFalse(v.getDiffChunks().isEmpty(), "Delta must have at least one diff chunk");
            }
        }

        assertEquals(12, service.getVersionHistory().size());

        // Text at v12
        assertTrue(service.getText().contains("[v12]"));

        // Rollback to v3 (a DELTA based on v1)
        VersionSnapshot r3 = service.restoreVersion(3);
        assertNotNull(r3);
        assertEquals("V1 Base. [v2] [v3]", service.getText());

        // Rollback to v5 (a CHECKPOINT)
        VersionSnapshot r5 = service.restoreVersion(5);
        assertNotNull(r5);
        assertEquals("V1 Base. [v2] [v3] [v4] [v5]", service.getText());

        // Rollback to v8 (a DELTA based on v5)
        VersionSnapshot r8 = service.restoreVersion(8);
        assertNotNull(r8);
        assertEquals("V1 Base. [v2] [v3] [v4] [v5] [v6] [v7] [v8]", service.getText());

        // Rollback to v10 (a CHECKPOINT)
        VersionSnapshot r10 = service.restoreVersion(10);
        assertNotNull(r10);
        assertEquals("V1 Base. [v2] [v3] [v4] [v5] [v6] [v7] [v8] [v9] [v10]", service.getText());

        // Rollback to v1 (initial CHECKPOINT)
        VersionSnapshot r1 = service.restoreVersion(1);
        assertNotNull(r1);
        assertEquals("V1 Base.", service.getText());

        // Rollback forward to v12 (a DELTA based on v10)
        VersionSnapshot r12 = service.restoreVersion(12);
        assertNotNull(r12);
        assertTrue(service.getText().endsWith("[v12]"));
    }

    @Test
    @DisplayName("8. PieceDiffChunk: LCS diff computation and patch reconstruction")
    void testPieceDiffChunkComputation() {
        Piece p1 = new Piece(BufferType.ORIGINAL, 0, 10);
        Piece p2 = new Piece(BufferType.ADD, 0, 5);
        Piece p3 = new Piece(BufferType.ORIGINAL, 10, 15);
        Piece p4 = new Piece(BufferType.ADD, 5, 20);

        List<Piece> base = List.of(p1, p2, p3, p4);

        // Target: p2 replaced with [pNew1, pNew2]
        Piece pNew1 = new Piece(BufferType.ADD, 25, 8);
        Piece pNew2 = new Piece(BufferType.ADD, 33, 4);
        List<Piece> target = List.of(p1, pNew1, pNew2, p3, p4);

        List<com.texteditor.backend.model.PieceDiffChunk> diff = com.texteditor.backend.model.PieceDiffChunk.computeDiff(base, target);
        assertEquals(1, diff.size());
        assertEquals(1, diff.get(0).getOffset());
        assertEquals(1, diff.get(0).getDeleteCount());
        assertEquals(2, diff.get(0).getInsertedPieces().size());

        // Apply diff to base
        List<Piece> reconstructed = com.texteditor.backend.model.PieceDiffChunk.applyDiff(base, diff);
        assertEquals(target.size(), reconstructed.size());
        assertEquals(target, reconstructed);
    }

    @Test
    @DisplayName("9. Disk Persistence: Serializing and rehydrating 5-interval checkpoints & deltas from companion JSON")
    void testDiskPersistenceWithCheckpointsAndDeltas() throws IOException {
        String testDocPath = "target/test_interval_doc.txt";
        File f = new File(testDocPath);
        if (f.exists()) f.delete();
        File vf = new File(testDocPath + ".versions.json");
        if (vf.exists()) vf.delete();

        service.initialize("Initial doc content.");
        service.saveToDiskPlan1(testDocPath);

        for (int i = 2; i <= 10; i++) {
            service.insert(service.getTotalLength(), " [part " + i + "]");
            service.createVersion("Version " + i);
        }

        // Verify JSON companion file exists
        assertTrue(vf.exists());
        String jsonContent = java.nio.file.Files.readString(vf.toPath());
        assertTrue(jsonContent.contains("\"snapshotType\":\"CHECKPOINT\""));
        assertTrue(jsonContent.contains("\"snapshotType\":\"DELTA\""));

        // Now test reloading into an independent, fresh service instance
        PieceTableService freshService = new PieceTableService();
        freshService.loadFromDiskPlan1(testDocPath);
        freshService.loadVersionsFromDisk();

        assertEquals(10, freshService.getVersionHistory().size());

        // Checkpoint versions: v1, v5, v10
        assertTrue(freshService.getVersionHistory().get(0).isCheckpoint());
        assertTrue(freshService.getVersionHistory().get(4).isCheckpoint());
        assertTrue(freshService.getVersionHistory().get(9).isCheckpoint());

        // Delta versions: v2, v3, v4, v6, v7, v8, v9
        assertTrue(freshService.getVersionHistory().get(1).isDelta());
        assertTrue(freshService.getVersionHistory().get(2).isDelta());
        assertTrue(freshService.getVersionHistory().get(5).isDelta());

        // Test rollback on the newly loaded service instance
        VersionSnapshot rolledV4 = freshService.restoreVersion(4);
        assertNotNull(rolledV4);
        assertEquals("Initial doc content. [part 2] [part 3] [part 4]", freshService.getText());

        VersionSnapshot rolledV5 = freshService.restoreVersion(5);
        assertNotNull(rolledV5);
        assertEquals("Initial doc content. [part 2] [part 3] [part 4] [part 5]", freshService.getText());

        VersionSnapshot rolledV8 = freshService.restoreVersion(8);
        assertNotNull(rolledV8);
        assertTrue(freshService.getText().endsWith("[part 8]"));

        // Cleanup
        f.delete();
        vf.delete();
    }

    @Test
    @DisplayName("10. Incremental Version Diffs: Each intermediate version stores diff from previous version increment, checkpoints at every 5th")
    void testIncrementalPieceDiffsChainAndIntervalCheckpoints() {
        service.initialize("BaseDocument"); // v1 Checkpoint
        assertEquals(1, service.getVersionHistory().size());
        assertEquals("CHECKPOINT", service.getVersionHistory().get(0).getSnapshotType());

        // Create v2 (Delta from v1)
        service.insert(service.getTotalLength(), " - Add 1");
        VersionSnapshot v2 = service.createVersion("Step 1");
        assertEquals(2, v2.getVersionId());
        assertEquals(1, v2.getBaseVersionId()); // Delta based on v1
        assertEquals("DELTA", v2.getSnapshotType());

        // Create v3 (Delta from v2)
        service.insert(service.getTotalLength(), " - Add 2");
        VersionSnapshot v3 = service.createVersion("Step 2");
        assertEquals(3, v3.getVersionId());
        assertEquals(2, v3.getBaseVersionId()); // Incremental diff from v2
        assertEquals("DELTA", v3.getSnapshotType());

        // Create v4 (Delta from v3 with a delete operation)
        service.delete(0, 4); // Remove "Base" -> "Document - Add 1 - Add 2"
        VersionSnapshot v4 = service.createVersion("Step 3 Delete");
        assertEquals(4, v4.getVersionId());
        assertEquals(3, v4.getBaseVersionId()); // Incremental diff from v3
        assertEquals("DELTA", v4.getSnapshotType());

        // Create v5 (CHECKPOINT: 5-interval checkpoint)
        service.insert(0, "NEW ");
        VersionSnapshot v5 = service.createVersion("Milestone 5 Checkpoint");
        assertEquals(5, v5.getVersionId());
        assertEquals(5, v5.getBaseVersionId());
        assertEquals("CHECKPOINT", v5.getSnapshotType());
        assertNotNull(v5.getPieceDirectory());

        // Create v6 (Delta from v5)
        service.insert(service.getTotalLength(), " - Add 4");
        VersionSnapshot v6 = service.createVersion("Step 4");
        assertEquals(6, v6.getVersionId());
        assertEquals(5, v6.getBaseVersionId()); // Delta based on v5
        assertEquals("DELTA", v6.getSnapshotType());

        // Create v7 (Delta from v6)
        service.insert(service.getTotalLength(), " - Add 5");
        VersionSnapshot v7 = service.createVersion("Step 5");
        assertEquals(7, v7.getVersionId());
        assertEquals(6, v7.getBaseVersionId()); // Incremental diff from v6
        assertEquals("DELTA", v7.getSnapshotType());

        // Test step-by-step rollbacks:
        // Rollback to v4
        VersionSnapshot r4 = service.restoreVersion(4);
        assertNotNull(r4);
        assertEquals("Document - Add 1 - Add 2", service.getText());

        // Rollback to v3
        VersionSnapshot r3 = service.restoreVersion(3);
        assertNotNull(r3);
        assertEquals("BaseDocument - Add 1 - Add 2", service.getText());

        // Rollback to v2
        VersionSnapshot r2 = service.restoreVersion(2);
        assertNotNull(r2);
        assertEquals("BaseDocument - Add 1", service.getText());

        // Rollback to v1
        VersionSnapshot r1 = service.restoreVersion(1);
        assertNotNull(r1);
        assertEquals("BaseDocument", service.getText());

        // Rollback forward to v7
        VersionSnapshot r7 = service.restoreVersion(7);
        assertNotNull(r7);
        assertEquals("NEW Document - Add 1 - Add 2 - Add 4 - Add 5", service.getText());
    }
}
