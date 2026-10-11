# 📝 High-Performance Piece Table Multi-Line Text Editor

A full-stack, production-grade text editor built with an authoritative **Piece Table data structure** on a **Spring Boot 3 (Java 17)** backend, paired with an ultra-responsive **HTML5 Monospace Virtual Canvas** frontend.

Designed from the ground up to support large multi-line documents (100,000+ characters) with zero string reallocation on edits, multi-level Undo/Redo, persistent Version History, multi-user folders, and complete clipboard integration (`Ctrl+C`, `Ctrl+V`, `Ctrl+X`, `Ctrl+A`).

---

## 🚀 Key Features

### 1. ⚡ Authoritative Piece Table Architecture
- **Immutable Buffers**: The `originalBuffer` (initial document text) and `addBuffer` (appended keystrokes) are strictly immutable and append-only.
- **Piece Directory**: Text is structured as an ordered list of lightweight descriptors (`Piece`):
  - `buffer`: `ORIGINAL` | `ADD`
  - `start`: Buffer character offset
  - `length`: Slice character count
- **Zero Reallocation Deletes & Edits**: Deletions split pieces and adjust directory pointers without mutating RAM buffer strings.
- **Synchronous Edit Queue (No Optimistic UI)**: All edits, backspaces, and clipboard pastes are processed sequentially by the server with authoritative auto-saving.

### 2. 🕒 Persistent Version History & Rollback
- **Companion Disk Persistence**: Version snapshots are automatically stored on disk alongside the file as `<fileName>.versions.json`.
- **Complete Directory & Buffer Snapshots**: Preserves piece descriptors alongside buffer state, allowing exact, lossless rollback to any previous version.
- **Rollback Without Data Loss**: Reverts the active piece directory and immediately triggers disk synchronization.

### 3. 📂 Multi-User & Multi-Folder Workspaces
- Pre-configured user workspaces (`megha`, `john`, `alice`) with isolated directories:
  - `projects/` (e.g. `main.txt`, `algorithm.txt`)
  - `notes/` (e.g. `ideas.txt`, `meeting.txt`)
  - `logs/` (e.g. `audit.txt`)
- Auto-seeded with 10,000+ characters of content for high-throughput stress testing.
- Automatic exclusion of internal `.versions.json` companion files from the sidebar file tree.

### 4. 🖥️ Two Purpose-Built Frontends
1. **User Frontend (`index.html`)**:
   - Clean, modern IDE experience with dark mode UI.
   - Monospace virtual canvas with live gutter line numbering.
   - Continuous vertical edge drag auto-scroll when selecting across long pages.
   - Clipboard support (`Ctrl+C` / `Cmd+C` copy, `Ctrl+V` / `Cmd+V` paste, `Ctrl+X` / `Cmd+X` cut, `Ctrl+A` / `Cmd+A` select all).
   - Version History modal dialog with 1-click snapshot creation and rollback.
2. **Developer & Memory Monitor (`dev.html`)**:
   - Low-level Piece Table inspector.
   - **Sliding RAM Cache**: Visualizes 10,240-char virtual pages loaded in memory.
   - Live Piece Directory table with buffer addresses, offsets, and lengths.
   - Version history inspect & rollback telemetry.

---

## 🏗️ System Architecture

```
┌────────────────────────────────────────────────────────┐
│             Frontend UI Layer (Port 8080)              │
│   • index.html (Clean User Multi-line Canvas Editor)   │
│   • dev.html   (RAM Sliding Cache & Piece Inspector)   │
└───────────────────────────┬────────────────────────────┘
                            │ REST / JSON (Authoritative Sync)
┌───────────────────────────▼────────────────────────────┐
│          Spring Boot Backend Layer (Port 8081)         │
│  com.texteditor.backend.controller.PieceTableController │
│  com.texteditor.backend.service.PieceTableService       │
├────────────────────────────────────────────────────────┤
│  • Piece Directory: [ Piece(0), Piece(1), ... ]        │
│  • Original Buffer: Immutable raw file content         │
│  • Add Buffer:      Append-only typing buffer          │
│  • Undo / Redo:     Dual Stacks (Piece Directory Diffs)│
│  • Version Manager: Snapshot persistence to disk JSON  │
└───────────────────────────┬────────────────────────────┘
                            │ Auto-save on every edit
┌───────────────────────────▼────────────────────────────┐
│                      Disk Storage                      │
│  data/users/{username}/{folder}/{file}.txt             │
│  data/users/{username}/{folder}/{file}.txt.versions.json│
└────────────────────────────────────────────────────────┘
```

---

## ⌨️ Keyboard Shortcuts & Controls

| Shortcut | Action |
| :--- | :--- |
| **`Enter`** | Insert newline character (`\n`) |
| **`Backspace`** / **`Delete`** | Delete character before/after cursor, or delete selected range |
| **`Shift` + `Arrow Keys`** | Select text range |
| **`Mouse Drag`** | Continuous selection with edge drag auto-scroll |
| **`Ctrl+C` / `Cmd+C`** | Copy selected range to system clipboard |
| **`Ctrl+V` / `Cmd+V`** | Paste system clipboard at cursor (replaces selection if active) |
| **`Ctrl+X` / `Cmd+X`** | Cut selected range to system clipboard |
| **`Ctrl+A` / `Cmd+A`** | Select all text in active document |
| **`Ctrl+Z` / `Cmd+Z`** | Server-side Undo operation |
| **`Ctrl+Y` / `Cmd+Shift+Z`** | Server-side Redo operation |
| **`Ctrl+` / `Ctrl-`** | Zoom in / Zoom out editor font size |

---

## 🛠️ Getting Started

### Prerequisites
- **Java 17** or higher (`java -version`)
- **Apache Maven 3.8+** (`mvn -v`)
- **Python 3** (or any static HTTP server for frontend)

### 1. Clone the Repository
```bash
git clone https://github.com/katharsis909/Text-Editor.git
cd Text-Editor
```

### 2. Start Backend (Port 8081)
```bash
mvn spring-boot:run
```
*The Spring Boot server will initialize on `http://localhost:8081`.*

### 3. Start Frontend (Port 8080)
Open a new terminal tab in the project root:
```bash
python3 -m http.server 8080
```

### 4. Access the Applications
- **User Editor**: [http://localhost:8080/index.html](http://localhost:8080/index.html)
- **Developer Piece Table Monitor**: [http://localhost:8080/dev.html](http://localhost:8080/dev.html)

---

## 🧪 Running Unit Tests

The backend includes a comprehensive test suite covering Piece Table inserts, deletes across piece boundaries, undo/redo stacks, version snapshots, and disk persistence:
```bash
mvn test
```

Expected result:
```
[INFO] Running com.texteditor.backend.PieceTableTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 📂 Project Structure

```
Text Editor/
├── pom.xml                                      # Maven dependencies (Spring Boot 3.2.3, Jackson)
├── index.html                                   # User-facing Multi-line Canvas Editor
├── dev.html                                     # Developer RAM monitor & Piece Table inspector
├── README.md                                    # Project documentation
├── data/                                        # User file storage
│   └── users/
│       ├── megha/                              # User folders (projects, notes, logs)
│       ├── john/
│       └── alice/
└── src/
    ├── main/java/com/texteditor/backend/
    │   ├── TextEditorApplication.java          # Spring Boot Main Application
    │   ├── controller/
    │   │   └── PieceTableController.java       # REST Endpoints (files, slice, insert, delete, versions)
    │   ├── model/
    │   │   ├── BufferType.java                 # ORIGINAL vs ADD enum
    │   │   ├── EditDescription.java            # Operation telemetry record
    │   │   ├── Piece.java                      # Piece descriptor (buffer, start, length)
    │   │   └── VersionSnapshot.java            # Immutable version snapshot with buffers
    │   └── service/
    │       ├── PieceTableService.java          # Core Piece Table algorithmic engine
    │       └── UndoRedoEntry.java              # Undo/Redo stack history model
    └── test/java/com/texteditor/backend/
        └── PieceTableTest.java                 # Complete unit test suite (12 tests)
```

---

## 📄 License
This project is open-source and available under the MIT License.
