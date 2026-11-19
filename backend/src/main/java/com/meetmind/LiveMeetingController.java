package com.meetmind;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/live")
public class LiveMeetingController {
    private final LiveMeetingService liveMeetingService;
    public LiveMeetingController(LiveMeetingService liveMeetingService) { this.liveMeetingService = liveMeetingService; }

    @PostMapping("/transcribe")
    public ResponseEntity<?> transcribe(@RequestParam("audio") MultipartFile audio) {
        if (audio.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "No live audio was received."));
        try { return ResponseEntity.ok().body(liveMeetingService.transcribeChunk(audio)); }
        catch (Exception exception) { return ResponseEntity.internalServerError().body(Map.of("message", message(exception))); }
    }

    @PostMapping("/summarize")
    public ResponseEntity<?> summarize(@RequestBody LiveContext request) {
        if (request.transcript() == null || request.transcript().isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "There is not enough live transcript yet."));
        try { return ResponseEntity.ok().body(liveMeetingService.summarize(request.transcript(), request.period() == null ? "meeting" : request.period())); }
        catch (Exception exception) { return ResponseEntity.internalServerError().body(Map.of("message", message(exception))); }
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody LiveContext request) {
        if (request.question() == null || request.question().isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "Ask a question first."));
        boolean broaderKnowledge = "GLOBAL".equalsIgnoreCase(request.scope());
        if (!broaderKnowledge && (request.transcript() == null || request.transcript().isBlank())) return ResponseEntity.badRequest().body(Map.of("message", "There is not enough live transcript yet."));
        try { return ResponseEntity.ok().body(liveMeetingService.answer(request.transcript() == null ? "" : request.transcript(), request.question(), request.scope())); }
        catch (Exception exception) { return ResponseEntity.internalServerError().body(Map.of("message", message(exception))); }
    }

    private String message(Exception exception) { String message = exception.getMessage(); return message == null ? "Live meeting request failed." : message.substring(0, Math.min(800, message.length())); }
    public record LiveContext(String transcript, String period, String question, String scope) {}
}
