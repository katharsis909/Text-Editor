package com.texteditor.backend.controller;

import com.texteditor.backend.model.EditDescription;
import com.texteditor.backend.model.Piece;
import com.texteditor.backend.service.PieceTableService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/piecetable")
@CrossOrigin(origins = "*")
public class PieceTableController {

    private final PieceTableService service;

    public PieceTableController(PieceTableService service) {
        this.service = service;
    }

    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initialize(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        EditDescription desc = service.initialize(text);
        Map<String, Object> response = new HashMap<>();
        response.put("description", desc);
        response.put("totalLength", service.getTotalLength());
        response.put("piecesCount", service.getPieceDirectory().size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/slice")
    public ResponseEntity<Map<String, Object>> getSlice(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "1024") int length) {
        String slice = service.getSlice(start, length);
        Map<String, Object> response = new HashMap<>();
        response.put("start", start);
        response.put("length", slice.length());
        response.put("content", slice);
        response.put("totalLength", service.getTotalLength());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/insert")
    public ResponseEntity<Map<String, Object>> insert(@RequestBody Map<String, Object> body) {
        int position = (int) body.getOrDefault("position", 0);
        String text = (String) body.getOrDefault("text", "");
        EditDescription desc = service.insert(position, text);
        Map<String, Object> response = new HashMap<>();
        response.put("description", desc);
        response.put("newTotalLength", service.getTotalLength());
        response.put("piecesCount", service.getPieceDirectory().size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestBody Map<String, Object> body) {
        int position = (int) body.getOrDefault("position", 0);
        int length = (int) body.getOrDefault("length", 0);
        EditDescription desc = service.delete(position, length);
        Map<String, Object> response = new HashMap<>();
        response.put("description", desc);
        response.put("newTotalLength", service.getTotalLength());
        response.put("piecesCount", service.getPieceDirectory().size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/undo")
    public ResponseEntity<Map<String, Object>> undo() {
        EditDescription desc = service.undo();
        Map<String, Object> response = new HashMap<>();
        response.put("description", desc);
        response.put("newTotalLength", service.getTotalLength());
        response.put("piecesCount", service.getPieceDirectory().size());
        response.put("canUndo", service.canUndo());
        response.put("canRedo", service.canRedo());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/redo")
    public ResponseEntity<Map<String, Object>> redo() {
        EditDescription desc = service.redo();
        Map<String, Object> response = new HashMap<>();
        response.put("description", desc);
        response.put("newTotalLength", service.getTotalLength());
        response.put("piecesCount", service.getPieceDirectory().size());
        response.put("canUndo", service.canUndo());
        response.put("canRedo", service.canRedo());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/text")
    public ResponseEntity<Map<String, Object>> getText() {
        Map<String, Object> response = new HashMap<>();
        response.put("text", service.getText());
        response.put("totalLength", service.getTotalLength());
        response.put("piecesCount", service.getPieceDirectory().size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/inspect")
    public ResponseEntity<Map<String, Object>> inspect() {
        Map<String, Object> response = new HashMap<>();
        response.put("totalLength", service.getTotalLength());
        response.put("originalBufferSize", service.getOriginalBufferSize());
        response.put("addBufferSize", service.getAddBufferSize());
        response.put("pieceDirectory", service.getPieceDirectory());
        response.put("history", service.getHistory());
        response.put("undoStackSize", service.getUndoStackSize());
        response.put("redoStackSize", service.getRedoStackSize());
        response.put("canUndo", service.canUndo());
        response.put("canRedo", service.canRedo());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, String>> save(@RequestBody Map<String, String> body) throws IOException {
        String filePath = body.getOrDefault("filePath", "saved_document.txt");
        String result = service.saveToDiskPlan1(filePath);
        return ResponseEntity.ok(Map.of("message", result));
    }

    @PostMapping("/load")
    public ResponseEntity<Map<String, String>> load(@RequestBody Map<String, String> body) throws IOException {
        String filePath = body.getOrDefault("filePath", "saved_document.txt");
        String result = service.loadFromDiskPlan1(filePath);
        return ResponseEntity.ok(Map.of("message", result));
    }

    @GetMapping("/users/{username}/files")
    public ResponseEntity<Map<String, Object>> getUserFiles(@PathVariable String username) {
        String baseDir = "data/users/" + username;
        java.io.File dir = new java.io.File(baseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Check if user has predefined folders
        java.io.File projDir = new java.io.File(baseDir + "/projects");
        java.io.File docsDir = new java.io.File(baseDir + "/notes");
        java.io.File logsDir = new java.io.File(baseDir + "/logs");
        projDir.mkdirs();
        docsDir.mkdirs();
        logsDir.mkdirs();

        // Ensure 10k chars sample files exist in each folder
        seedUserFolderFiles(username);

        List<Map<String, String>> files = new ArrayList<>();
        scanDirectory(dir, dir.getAbsolutePath(), files);

        Map<String, Object> resp = new HashMap<>();
        resp.put("user", username);
        resp.put("activeFile", service.getActiveFilePath());
        resp.put("files", files);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/users/{username}/open")
    public ResponseEntity<Map<String, Object>> openUserFile(
            @PathVariable String username,
            @RequestBody Map<String, String> body) throws IOException {
        String relativePath = body.getOrDefault("path", "projects/main.txt");
        String fullPath = "data/users/" + username + "/" + relativePath;
        java.io.File file = new java.io.File(fullPath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), "// Empty file created for " + username + "\n", java.nio.charset.StandardCharsets.UTF_8);
        }
        service.loadFromDiskPlan1(fullPath);

        Map<String, Object> resp = new HashMap<>();
        resp.put("user", username);
        resp.put("path", relativePath);
        resp.put("fullPath", fullPath);
        resp.put("totalLength", service.getTotalLength());
        resp.put("text", service.getText());
        return ResponseEntity.ok(resp);
    }

    private void seedUserFolderFiles(String username) {
        String base = "data/users/" + username;
        seedFileIfMissing(base + "/projects/main.txt", "Projects / main.txt (Code Engine)", 10000);
        seedFileIfMissing(base + "/projects/algorithm.txt", "Projects / algorithm.txt (Piece Table Spec)", 10000);
        seedFileIfMissing(base + "/notes/ideas.txt", "Notes / ideas.txt (Product Thoughts)", 10000);
        seedFileIfMissing(base + "/notes/meeting.txt", "Notes / meeting.txt (Architecture Notes)", 10000);
        seedFileIfMissing(base + "/logs/audit.txt", "Logs / audit.txt (System Events)", 10000);
    }

    private void seedFileIfMissing(String path, String title, int targetChars) {
        java.io.File f = new java.io.File(path);
        if (!f.exists() || f.length() == 0) {
            try {
                f.getParentFile().mkdirs();
                StringBuilder sb = new StringBuilder(targetChars + 200);
                sb.append("// === ").append(title).append(" ===\n");
                sb.append("// User document initialized with 10,000+ characters for testing.\n\n");
                int line = 1;
                while (sb.length() < targetChars) {
                    sb.append(String.format("Line %04d: High-performance Piece Table buffer persistence. Authoritative zero-mutation streaming edit line.\n", line++));
                }
                java.nio.file.Files.writeString(f.toPath(), sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void scanDirectory(java.io.File node, String baseAbsPath, List<Map<String, String>> out) {
        if (node.isDirectory()) {
            java.io.File[] children = node.listFiles();
            if (children != null) {
                for (java.io.File c : children) {
                    scanDirectory(c, baseAbsPath, out);
                }
            }
        } else if (node.isFile()) {
            String rel = node.getAbsolutePath().substring(baseAbsPath.length());
            if (rel.startsWith("/")) rel = rel.substring(1);
            Map<String, String> m = new HashMap<>();
            m.put("relativePath", rel);
            m.put("name", node.getName());
            m.put("size", String.valueOf(node.length()));
            out.add(m);
        }
    }
}
