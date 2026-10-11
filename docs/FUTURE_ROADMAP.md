# Future Roadmap & Effectiveness Optimizations

This document outlines architectural enhancements, algorithmic optimizations, and planned features for scaling the Text Editor and Piece Table engine to massive documents ($5 \times 10^7$ characters).

---

## 1. Improving Piece Table Search from $O(P)$ to $O(\log P)$

### Current Limitation: Linear Piece List
Currently, finding which piece contains global position $K$ requires iterating through pieces:
$$\text{Time Complexity} = O(P)$$
Where $P$ is the number of pieces. For a document with thousands of small edits, $P$ can grow to several thousand, making linear search slower.

### Optimization: The Piece Tree (Balanced Red-Black Tree or B-Tree)
Instead of storing pieces in a flat array, organize them into a **Piece Tree** (the approach used by VS Code):
- **Node Structure**:
  - Each node represents a `Piece`.
  - Each node maintains `size_left`: the sum of lengths of all pieces in its left subtree.
- **Search Algorithm**:
  - If $K < \text{node.size\_left}$, traverse to left child.
  - If $K \ge \text{node.size\_left}$ and $K < \text{node.size\_left} + \text{node.length}$, the target piece is found!
  - Otherwise, subtract $(\text{node.size\_left} + \text{node.length})$ from $K$ and traverse to right child.
- **Result**: Insertion, deletion, and index lookup become **$O(\log P)$**.

```
                [Piece B: len=40, left_size=500]
                     /                       \
        [Piece A: len=500]             [Piece C: len=1000]
```

---

## 2. Dynamic Virtual Paging over Non-Contiguous Pieces

### The Challenge
When edits happen in the middle, pieces fragment. How does the frontend fetch a virtual 1024-character "page" when that page spans multiple discontinuous pieces?

### Solution: Logical View Slices
The backend treats pages as **logical index windows** rather than physical files:
$$\text{Page } P = \text{slice}(P \times 1024, (P + 1) \times 1024)$$

Using `get_slice(start, end)`:
1. Locate the piece containing `start` in $O(\log P)$.
2. Collect characters across subsequent pieces until reaching `end`.
3. The frontend receives an exact 1,024-character contiguous payload without caring how many fragmented pieces exist under the hood.

---

## 3. Piece Compaction & Defragmentation

### The Problem: Single-Character Typing Fragmentation
If a user types 100 characters consecutively (`"a"`, `"b"`, `"c"`, ...), naive insertion creates 100 individual pieces:
`Piece(ADD, 0, 1)`, `Piece(ADD, 1, 1)`, `Piece(ADD, 2, 1)`, ...

### Optimization: Consecutive Insertion Merging
Before creating a new piece, check if the insertion is contiguous with the previous piece:
1. Is the previous piece in the `ADD` buffer?
2. Does the previous piece end exactly where the new insertion in `add_buffer` begins (`p.start + p.length == new_add_start`)?
3. If yes, **simply increment `p.length += len(new_text)`** instead of splitting or creating a new piece!
4. This keeps $P = 1$ during normal typing sessions.

### Periodic Compaction
When piece fragmentation exceeds a threshold (e.g., $P > 5,000$):
- Run a background compaction worker.
- Re-write the active piece chain into a single consolidated `original_buffer` and reset `add_buffer = ""`.

---

## 4. Zero-Cost Undo / Redo

Because neither `original_buffer` nor `add_buffer` are ever mutated or truncated:
- Every piece descriptor is an immutable value object.
- **Undo Stack**: Simply store the previous list of `pieces` descriptors (or tree root pointer).
- To **Undo**: Swap the active pieces list with the previous snapshot:
  $$\text{Undo Time Complexity} = O(1)$$
- Memory cost of an undo state is only a few dozen integers (the descriptor pointers), not a duplicate copy of the text.

---

## 5. Comparative Evaluation: Data Structure Selection

| Feature | Flat Array | Gap Buffer | Rope (Balanced Tree) | Piece Table | Piece Tree |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Middle Insertion** | $O(N)$ (slow) | $O(1)$ at gap, $O(N)$ on move | $O(\log N)$ | $O(P)$ list split | **$O(\log P)$** |
| **Middle Deletion** | $O(N)$ | $O(1)$ at gap, $O(N)$ on move | $O(\log N)$ | $O(P)$ list trim | **$O(\log P)$** |
| **Random Index Lookup** | $O(1)$ | $O(1)$ | $O(\log N)$ | $O(P)$ | **$O(\log P)$** |
| **Undo / Redo** | High memory | High memory | Moderate (tree sharing) | **Near zero memory** | **Near zero memory** |
| **Buffer Appends** | Mutates buffer | Shifts gap buffer | Tree node creation | **Append-only** | **Append-only** |

---

## 6. Persistence Evolution: Plan 1 vs. Plan 2

### Plan 1 (Current Implementation)
- Consolidates the full text from the piece sequence in memory.
- Overwrites the target file on disk as a single complete block.
- Best for simplicity and moderate document sizes ($\le 10\text{MB}$).

### Plan 2 (For $50\text{M}$ Chars / Scaled Systems)
- **Write-Ahead Log (WAL)**: Append edit descriptors to an append-only transaction journal on disk.
- **Chunked File Blocks**: Store document on disk in fixed 64KB block files.
- **Dirty Block Flusher**: When a piece within a 64KB block changes, mark that block dirty and asynchronously flush only that modified block to disk, rather than rewriting 500MB on every save.

---

## 7. Scaled Persistence: Separate Piece Files & On-Demand Loading

### 7.1 The Scenario: User Connects & Disconnects
When a user connects and requests a text file:
1. **Load Piece Directory (Descriptors)**:
   - Piece additions and deletions are relatively infrequent compared to read requests.
   - The entire `pieceDirectory` (metadata containing piece numbering, piece file name/ID, start offset, and length) is small (e.g. 10,000 pieces $\times$ 16 bytes $\approx 160\text{KB}$) and is loaded into memory in a numbered HashMap / Index Table.
2. **Pieces Stored as Separate Files on Disk**:
   - Each piece or chunk is persisted as an independent file: `piece_<ID>.bin` or `piece_<ID>.txt`.
   - The piece name in the directory is the exact name of the file on disk.
3. **On-Demand Loading (Zero Unnecessary Disk I/O)**:
   - Pieces are **not** loaded in memory upon user connection.
   - When the user requests a virtual page or slice (e.g. chars 1,024 to 2,048), only the specific piece file(s) covering that slice are read from disk.
   - Inactive pieces remain on disk.
   - This provides $O(1)$ connection time regardless of document size ($10\text{MB}$ or $1\text{GB}$).

