package com.texteditor.backend.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple balanced-by-construction binary tree for Piece Directory.
 * Used when piece count exceeds TREE_THRESHOLD to provide O(log P)
 * character offset seeks and subtree length aggregations without linear array scans.
 */
public class PieceBinaryTree {

    public static class PieceTreeNode {
        public Piece piece;
        public int subtreeLength;
        public int subtreeCount;
        public PieceTreeNode left;
        public PieceTreeNode right;
        public PieceTreeNode parent;

        public PieceTreeNode(Piece piece) {
            this.piece = piece;
            this.subtreeLength = (piece != null) ? piece.getLength() : 0;
            this.subtreeCount = 1;
        }

        public void updateMetrics() {
            this.subtreeLength = ((piece != null) ? piece.getLength() : 0)
                    + ((left != null) ? left.subtreeLength : 0)
                    + ((right != null) ? right.subtreeLength : 0);
            this.subtreeCount = 1
                    + ((left != null) ? left.subtreeCount : 0)
                    + ((right != null) ? right.subtreeCount : 0);
        }
    }

    public static class SeekResult {
        public final PieceTreeNode node;
        public final Piece piece;
        public final int globalPieceStart;
        public final int offsetInPiece;
        public final int pieceIndex;

        public SeekResult(PieceTreeNode node, Piece piece, int globalPieceStart, int offsetInPiece, int pieceIndex) {
            this.node = node;
            this.piece = piece;
            this.globalPieceStart = globalPieceStart;
            this.offsetInPiece = offsetInPiece;
            this.pieceIndex = pieceIndex;
        }
    }

    private PieceTreeNode root;

    public PieceBinaryTree() {
        this.root = null;
    }

    public PieceTreeNode getRoot() {
        return root;
    }

    /**
     * Builds a balanced binary tree from an ordered list of pieces in O(P) time.
     */
    public static PieceBinaryTree buildFromList(List<Piece> pieces) {
        PieceBinaryTree tree = new PieceBinaryTree();
        if (pieces == null || pieces.isEmpty()) {
            return tree;
        }
        tree.root = buildRecursive(pieces, 0, pieces.size() - 1, null);
        return tree;
    }

    private static PieceTreeNode buildRecursive(List<Piece> pieces, int low, int high, PieceTreeNode parent) {
        if (low > high) return null;
        int mid = (low + high) >>> 1;
        PieceTreeNode node = new PieceTreeNode(pieces.get(mid));
        node.parent = parent;
        node.left = buildRecursive(pieces, low, mid - 1, node);
        node.right = buildRecursive(pieces, mid + 1, high, node);
        node.updateMetrics();
        return node;
    }

    /**
     * Rebuilds / balances the tree from a list of pieces.
     */
    public void rebuild(List<Piece> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            this.root = null;
        } else {
            this.root = buildRecursive(pieces, 0, pieces.size() - 1, null);
        }
    }

    public int getTotalLength() {
        return (root != null) ? root.subtreeLength : 0;
    }

    public int size() {
        return (root != null) ? root.subtreeCount : 0;
    }

    public int getDepth() {
        return computeDepth(root);
    }

    private int computeDepth(PieceTreeNode node) {
        if (node == null) return 0;
        return 1 + Math.max(computeDepth(node.left), computeDepth(node.right));
    }

    /**
     * Finds the piece containing the global character offset in O(log P) average time.
     */
    public SeekResult findAtOffset(int globalCharOffset) {
        if (root == null) return null;
        int total = root.subtreeLength;
        int clamped = Math.max(0, Math.min(total, globalCharOffset));

        PieceTreeNode curr = root;
        int accumulatedOffset = 0;
        int accumulatedIndex = 0;
        SeekResult lastValid = null;

        while (curr != null) {
            int leftLen = (curr.left != null) ? curr.left.subtreeLength : 0;
            int leftCount = (curr.left != null) ? curr.left.subtreeCount : 0;
            int pieceStart = accumulatedOffset + leftLen;
            int pieceLen = (curr.piece != null) ? curr.piece.getLength() : 0;
            int pieceEnd = pieceStart + pieceLen;
            int pieceIdx = accumulatedIndex + leftCount;

            lastValid = new SeekResult(curr, curr.piece, pieceStart, clamped - pieceStart, pieceIdx);

            if (clamped < pieceStart) {
                curr = curr.left;
            } else if (clamped < pieceEnd || (clamped == pieceEnd && clamped == total)) {
                return new SeekResult(curr, curr.piece, pieceStart, clamped - pieceStart, pieceIdx);
            } else {
                accumulatedOffset += leftLen + pieceLen;
                accumulatedIndex += leftCount + 1;
                curr = curr.right;
            }
        }
        return lastValid;
    }

    /**
     * In-order traversal to collect all pieces sequentially.
     */
    public List<Piece> toList() {
        List<Piece> result = new ArrayList<>();
        inorderCollect(root, result);
        return result;
    }

    private void inorderCollect(PieceTreeNode node, List<Piece> result) {
        if (node == null) return;
        inorderCollect(node.left, result);
        if (node.piece != null) {
            result.add(node.piece);
        }
        inorderCollect(node.right, result);
    }
}
