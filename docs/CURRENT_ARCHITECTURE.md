# Current Architecture: Text Editor & Spring Boot Piece Table Engine

## 1. Overview

This document describes the pure Spring Boot backend implementation of the single-line Piece Table engine.

Core Architectural Tenets:
1. **Zero Text Mutation in Memory**: Text added to `originalBuffer` or `addBuffer` is never modified or truncated.
2. **Piece Directory as an Ordered Read Sequence**: We store the ordered sequence of piece descriptors `(BufferType, start, length)` dictating what part of which buffer to read and for how long.
3. **Unified Binary Search Deletion**: Uses binary search on cumulative piece offsets to locate the leftmost piece, handling single-piece partial deletion, complete piece drops, and multi-piece half-half deletions in a single code block.
4. **Plan 1 Disk Persistence**: Loads the whole piece directory as one and rewrites the unified text whole to disk.

---

## 2. Spring Boot Backend Structure

The backend is built with Spring Boot 3.2.x (Java 17) in `src/main/java/com/texteditor/backend`:

- **`model/BufferType.java`**: Enum distinguishing `ORIGINAL` (read-only initial text) from `ADD` (append-only insertions).
- **`model/Piece.java`**: Lightweight descriptor pointing to `(BufferType buffer, int start, int length)`.
- **`model/EditDescription.java`**: Audit log model describing what change was made, position, length, affected buffer, piece count before and after, and details.
- **`service/PieceTableService.java`**: Core business logic implementing initial load, additions, binary search deletions, and Plan 1 persistence.
- **`controller/PieceTableController.java`**: REST API endpoints (`/api/piecetable/init`, `/insert`, `/delete`, `/text`, `/inspect`, `/save`, `/load`).

---

## 3. Buffer Invariants & Additions

### 3.1 Two Buffers
1. **Original Buffer** (`originalBuffer`): Loaded on initialization. Never modified.
2. **Add Buffer** (`addBuffer`): Append-only. Every insertion appends its text to the end of `addBuffer`.

### 3.2 Additions Algorithm
When inserting text $S$ at global character offset $K$:
1. Append $S$ to `addBuffer` at index `addStart = addBuffer.length()`.
2. Construct `Piece(BufferType.ADD, addStart, S.length())`.
3. If $K = 0$, prepend to `pieceDirectory`.
4. If $K = \text{totalLength}$, append to `pieceDirectory`.
5. If $K$ is in the middle:
   - Use binary search over cumulative offsets to locate the piece spanning index $K$.
   - Split that piece into `leftPiece` and `rightPiece`.
   - Insert `newPiece` between them: `[leftPiece, newPiece, rightPiece]`.
6. Return `EditDescription` describing where the edit was made.

---

## 4. Deletions (Without Keyboard & Mouse)

When operating without physical keyboard and mouse inputs, the software is notified via API/service call which range to delete in the middle (`position`, `length`).

### 4.1 Invariant: Buffers Remain Untouched
> **"The deleted piece still remains in the piece as it is"**
Neither `originalBuffer` nor `addBuffer` are shortened or spliced. Deletion is purely a descriptor-level operation that rewrites the piece directory.

### 4.2 Binary Search for Leftmost Piece
Because piece lengths $L_0, L_1, \dots, L_{n-1}$ define strictly monotonically increasing start offsets:
$$C_0 = 0, \quad C_i = C_{i-1} + L_{i-1}$$
We perform a binary search on $C$ to find:
- **`leftPieceIdx`**: The leftmost piece overlapping with `delStart`.
- **`rightPieceIdx`**: The rightmost piece overlapping with `delEnd - 1`.

### 4.3 Unified Single-Code Handling
The piece directory is rewritten in a single pass:
1. **Pieces before `leftPieceIdx`**: Retained unmodified in `newDirectory`.
2. **Affected Pieces (`leftPieceIdx` .. `rightPieceIdx`)**:
   - **Case A: Single Piece (`leftPieceIdx == rightPieceIdx`)**:
     - *Deleting part of a piece*: If `delStart > pieceStart`, keep left slice. If `delEnd < pieceEnd`, keep right slice.
     - *Deleting a complete piece*: If `delStart <= pieceStart` and `delEnd >= pieceEnd`, the piece is dropped completely.
   - **Case B: Multiple Pieces (`leftPieceIdx < rightPieceIdx`)**:
     - *Deleting multiple pieces half-half*: Leftmost piece is truncated (`delStart > leftStart`). All intermediate pieces are completely skipped. Rightmost piece is truncated (`delEnd < rightEnd`).
3. **Pieces after `rightPieceIdx`**: Retained unmodified in `newDirectory`.
4. Replace `pieceDirectory` with `newDirectory`.

---

## 5. Plan 1: Disk Persistence

Under **Plan 1**:
- When saving: The engine iterates through the active `pieceDirectory` in order, resolves the slices from `originalBuffer` and `addBuffer`, concatenates the unified string, and rewrites the whole file to disk.
- When loading: Reads the whole file from disk and initializes a fresh Piece Table with that content as `originalBuffer`.

---

## 6. Frontend Integration: Read + Add with Server-Authoritative Consistency

### 6.1 Scope & Separation
- **Frontend Scope**: Strictly **Read + Add** (deletion concept has been completely removed from frontend controls).
- **Backend Scope**: Manages the authoritative Piece Table state, piece descriptors order, and persistence.

### 6.2 The Read + Add Flow
1. **Read (Logical Window Paging)**:
   - Frontend requests slices on demand: `GET /api/piecetable/slice?start=${P * 1024}&length=1024`.
   - Backend extracts the slice in $O(\text{pieces})$ without building the full document string.
   - Frontend maintains its 5-page sliding cache with prefetching near edges and farthest-page eviction.
2. **Add (Middle Insertions)**:
   - When a user types or clicks "Insert", frontend issues: `POST /api/piecetable/insert { position, text }`.
   - Backend appends to `addBuffer`, splits the target piece, and responds with the new total length and piece count.
   - Frontend receives the authoritative ACK:
     - Invalidates cached pages from the edit point onwards.
     - Re-fetches the authoritative slice for the active page.
     - Advances cursor selection and re-renders.

### 6.3 Design Decision: Why Optimistic UI Updates Were Deliberately Omitted
In single-user local applications, optimistic updates render characters immediately before server confirmation. However, this was deliberately avoided here for the following architectural reasons:

1. **Multi-User Collaboration Readiness**:
   - In future iterations supporting multiple concurrent users, client-side speculative mutations cause character indices to diverge when simultaneous edits occur.
   - Without Operational Transformation (OT) or Conflict-Free Replicated Data Types (CRDTs), optimistic edits lead to desynchronization and race conditions.
2. **Deterministic Source of Truth**:
   - By awaiting the server's authoritative acknowledgment, all connected clients always reflect the identical piece descriptor order and global character coordinates.
