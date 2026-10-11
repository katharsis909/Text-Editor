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
