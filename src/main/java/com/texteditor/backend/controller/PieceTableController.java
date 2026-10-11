package com.texteditor.backend.controller;

import com.texteditor.backend.model.EditDescription;
import com.texteditor.backend.model.Piece;
import com.texteditor.backend.service.PieceTableService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/piecetable")
public class PieceTableController {

    private final PieceTableService service;

    public PieceTableController(PieceTableService service) {
        this.service = service;
    }

    @PostMapping("/init")
    public ResponseEntity<EditDescription> initialize(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        EditDescription desc = service.initialize(text);
        return ResponseEntity.ok(desc);
    }

    @PostMapping("/insert")
    public ResponseEntity<EditDescription> insert(@RequestBody Map<String, Object> body) {
        int position = (int) body.getOrDefault("position", 0);
        String text = (String) body.getOrDefault("text", "");
        EditDescription desc = service.insert(position, text);
        return ResponseEntity.ok(desc);
    }

    @PostMapping("/delete")
    public ResponseEntity<EditDescription> delete(@RequestBody Map<String, Object> body) {
        int position = (int) body.getOrDefault("position", 0);
        int length = (int) body.getOrDefault("length", 0);
        EditDescription desc = service.delete(position, length);
        return ResponseEntity.ok(desc);
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
}
