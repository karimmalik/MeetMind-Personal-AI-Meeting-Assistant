package com.meetmind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "wav", "m4a", "aac", "flac", "ogg");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "mkv", "webm", "avi");
    private static final Set<String> PROCESSING_MODES = Set.of("AUDIO_ONLY", "AUDIO_VIDEO");

    private final MeetMindStorage storage;
    private final MeetingRepository meetingRepository;
    private final MediaProcessingService mediaProcessingService;
    private final CodexInsightService codexInsightService;
    private final InsightRepository insightRepository;
    private final QuestionRepository questionRepository;
    private final CodexQuestionService codexQuestionService;
    private final VideoFrameRepository frameRepository;
    private final ActionStateRepository actionStateRepository;

    public MeetingController(MeetMindStorage storage, MeetingRepository meetingRepository, MediaProcessingService mediaProcessingService, CodexInsightService codexInsightService, InsightRepository insightRepository, QuestionRepository questionRepository, CodexQuestionService codexQuestionService, VideoFrameRepository frameRepository, ActionStateRepository actionStateRepository) {
        this.storage = storage;
        this.meetingRepository = meetingRepository;
        this.mediaProcessingService = mediaProcessingService;
        this.codexInsightService = codexInsightService;
        this.insightRepository = insightRepository;
        this.questionRepository = questionRepository;
        this.codexQuestionService = codexQuestionService;
        this.frameRepository = frameRepository;
        this.actionStateRepository = actionStateRepository;
    }

    @GetMapping
    public List<Meeting> list() {
        return meetingRepository.findAll();
    }

    @GetMapping(value = "/{id}/transcript", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> transcript(@PathVariable String id) throws IOException {
        var stored = meetingRepository.findStoredById(id);
        if (stored.isEmpty()) return ResponseEntity.notFound().build();
        String transcriptPath = stored.get().transcriptPath();
        if (!"COMPLETED".equals(stored.get().meeting().status()) || transcriptPath == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("message", "Transcript is not ready yet."));
        }
        Path file = Path.of(transcriptPath);
        if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Files.readString(file, StandardCharsets.UTF_8));
    }

    @PostMapping("/{id}/insights")
    public ResponseEntity<?> generateInsights(@PathVariable String id) {
        var stored = meetingRepository.findStoredById(id);
        if (stored.isEmpty()) return ResponseEntity.notFound().build();
        if (!"COMPLETED".equals(stored.get().meeting().status()) || stored.get().transcriptPath() == null) return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("message", "Transcript is not ready yet."));
        codexInsightService.generate(id, Path.of(stored.get().transcriptPath()));
        return ResponseEntity.accepted().body(java.util.Map.of("status", "GENERATING"));
    }

    @GetMapping("/{id}/insights")
    public ResponseEntity<?> insights(@PathVariable String id) {
        return insightRepository.find(id).<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/insights/history")
    public List<InsightRepository.InsightHistory> insightHistory(@PathVariable String id) { return insightRepository.history(id); }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable String id) {
        var stored = meetingRepository.findStoredById(id);
        if (stored.isEmpty()) return ResponseEntity.notFound().build();
        if (!"FAILED".equals(stored.get().meeting().status())) return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("message", "Only failed meetings can be retried."));
        if (!Files.isRegularFile(Path.of(stored.get().storagePath()))) return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("message", "The original local media file is unavailable."));
        meetingRepository.updateStatus(id, "QUEUED", null, null);
        mediaProcessingService.transcribe(stored.get().meeting(), Path.of(stored.get().storagePath()));
        return ResponseEntity.accepted().body(java.util.Map.of("status", "QUEUED"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) throws IOException {
        var stored = meetingRepository.findStoredById(id);
        if (stored.isEmpty()) return ResponseEntity.notFound().build();
        var questions = questionRepository.findByMeetingId(id);
        deleteWithin(storage.mediaDirectory(), Path.of(stored.get().storagePath()).getParent());
        if (stored.get().transcriptPath() != null) deleteWithin(storage.transcriptDirectory(), Path.of(stored.get().transcriptPath()));
        deleteWithin(storage.frameDirectory(), storage.frameDirectory().resolve(id));
        deleteWithin(storage.dataDirectory().resolve("insights"), storage.dataDirectory().resolve("insights").resolve(id + ".json"));
        for (var question : questions) deleteWithin(storage.dataDirectory().resolve("questions"), storage.dataDirectory().resolve("questions").resolve(question.id() + ".json"));
        meetingRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/questions")
    public List<QuestionRepository.Question> questions(@PathVariable String id) { return questionRepository.findByMeetingId(id); }

    @GetMapping("/{id}/frames")
    public List<VideoFrameRepository.VideoFrame> frames(@PathVariable String id) { return frameRepository.find(id); }

    @GetMapping(value = "/{id}/frames/{timestamp}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> frameImage(@PathVariable String id, @PathVariable long timestamp) throws IOException {
        var matchingFrame = frameRepository.find(id).stream().filter(frame -> frame.timestampMs() == timestamp).findFirst();
        if (matchingFrame.isEmpty()) return ResponseEntity.notFound().build();
        Path file = Path.of(matchingFrame.get().imagePath());
        if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Files.readAllBytes(file));
    }

    @GetMapping("/{id}/actions")
    public java.util.Map<Integer, Boolean> actionStates(@PathVariable String id) { return actionStateRepository.find(id); }

    @PostMapping("/{id}/actions")
    public ResponseEntity<?> updateAction(@PathVariable String id, @RequestParam int index, @RequestParam boolean completed) { actionStateRepository.set(id, index, completed); return ResponseEntity.noContent().build(); }

    @PostMapping("/{id}/questions")
    public ResponseEntity<?> ask(@PathVariable String id, @RequestParam String question, @RequestParam(defaultValue = "MEETING") String scope) {
        if (question.isBlank()) return ResponseEntity.badRequest().body(java.util.Map.of("message", "Ask a question first."));
        var stored = meetingRepository.findStoredById(id);
        boolean broaderKnowledge = "GLOBAL".equalsIgnoreCase(scope);
        if (stored.isEmpty() || (!broaderKnowledge && stored.get().transcriptPath() == null)) return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("message", "Transcript is not ready yet."));
        String normalizedScope = broaderKnowledge ? "GLOBAL" : "MEETING";
        String questionId = questionRepository.create(id, question.trim(), normalizedScope);
        String insights = insightRepository.find(id).map(InsightRepository.Insight::content).orElse(null);
        codexQuestionService.answer(questionId, question.trim(), normalizedScope, stored.get().transcriptPath() == null ? null : Path.of(stored.get().transcriptPath()), insights);
        return ResponseEntity.accepted().body(java.util.Map.of("id", questionId, "status", "GENERATING"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "AUDIO_ONLY") String processingMode) throws IOException {
        if (file.isEmpty()) return invalid("Select an audio or video file before creating a meeting.");

        String originalFilename = cleanFilename(file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        String sourceMediaType = mediaTypeFor(extension);
        if (sourceMediaType == null) return invalid("Supported formats: MP3, WAV, M4A, AAC, FLAC, OGG, MP4, MOV, MKV, WEBM, AVI.");

        String normalizedMode = processingMode.toUpperCase(Locale.ROOT);
        if (!PROCESSING_MODES.contains(normalizedMode)) return invalid("Invalid processing mode.");
        if (sourceMediaType.equals("AUDIO") && normalizedMode.equals("AUDIO_VIDEO")) {
            return invalid("Video + Audio analysis requires a video file.");
        }

        String id = UUID.randomUUID().toString();
        Path mediaDirectory = storage.mediaDirectory().resolve(id);
        Files.createDirectories(mediaDirectory);
        Path storedFile = mediaDirectory.resolve(originalFilename);
        Files.copy(file.getInputStream(), storedFile);

        String resolvedTitle = title == null || title.isBlank() ? filenameWithoutExtension(originalFilename) : title.trim();
        Meeting meeting = new Meeting(id, resolvedTitle, originalFilename, sourceMediaType, normalizedMode,
                "QUEUED", file.getSize(), Instant.now(), null);
        meetingRepository.save(meeting, storedFile.toString());
        mediaProcessingService.transcribe(meeting, storedFile);
        return ResponseEntity.status(HttpStatus.CREATED).body(meeting);
    }

    private ResponseEntity<?> invalid(String message) {
        return ResponseEntity.badRequest().body(java.util.Map.of("message", message));
    }

    private String mediaTypeFor(String extension) {
        if (AUDIO_EXTENSIONS.contains(extension)) return "AUDIO";
        if (VIDEO_EXTENSIONS.contains(extension)) return "VIDEO";
        return null;
    }

    private String cleanFilename(String filename) {
        if (filename == null || filename.isBlank()) return "meeting-media";
        return Path.of(filename).getFileName().toString().replaceAll("[^A-Za-z0-9._ -]", "_");
    }

    private String extensionOf(String filename) {
        int separator = filename.lastIndexOf('.');
        return separator < 0 ? "" : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String filenameWithoutExtension(String filename) {
        int separator = filename.lastIndexOf('.');
        return separator < 0 ? filename : filename.substring(0, separator);
    }

    private void deleteWithin(Path allowedRoot, Path target) throws IOException {
        Path root = allowedRoot.toAbsolutePath().normalize();
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || !Files.exists(normalized)) return;
        if (Files.isDirectory(normalized)) {
            try (var paths = Files.walk(normalized)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
                });
            } catch (java.io.UncheckedIOException exception) { throw exception.getCause(); }
        } else {
            Files.deleteIfExists(normalized);
        }
    }
}
