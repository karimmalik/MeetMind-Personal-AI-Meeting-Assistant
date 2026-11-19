package com.meetmind;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MediaProcessingService {
    private final MeetMindStorage storage;
    private final MeetingRepository meetingRepository;
    private final SettingsRepository settings;
    private final VideoFrameRepository frameRepository;

    public MediaProcessingService(MeetMindStorage storage, MeetingRepository meetingRepository, SettingsRepository settings, VideoFrameRepository frameRepository) {
        this.storage = storage;
        this.meetingRepository = meetingRepository;
        this.settings = settings;
        this.frameRepository = frameRepository;
    }

    @Async("meetingExecutor")
    public void transcribe(Meeting meeting, Path mediaFile) {
        Path transcriptFile = storage.transcriptDirectory().resolve(meeting.id() + ".json");
        try {
            meetingRepository.updateStatus(meeting.id(), "PROCESSING", null, null);
            String model = settings.value("transcription.model", "base");
            Path frames = storage.frameDirectory().resolve(meeting.id());
            String packagedWorker = System.getenv("RIMEET_WORKER_PATH");
            String packagedModel = System.getenv("RIMEET_MODEL_PATH");
            List<String> command;
            if (packagedWorker != null && !packagedWorker.isBlank() && Files.isRegularFile(Path.of(packagedWorker))) {
                command = new java.util.ArrayList<>(List.of(packagedWorker, "--input", mediaFile.toString(), "--output", transcriptFile.toString(), "--model", model));
            } else {
                Path projectRoot = Path.of(System.getProperty("user.dir")).getParent();
                Path worker = projectRoot.resolve("transcription-worker").resolve("main.py");
                Path venvPython = projectRoot.resolve("transcription-worker").resolve(".venv").resolve("Scripts").resolve("python.exe");
                String python = Files.exists(venvPython) ? venvPython.toString() : "python";
                command = new java.util.ArrayList<>(List.of(python, worker.toString(), "--input", mediaFile.toString(), "--output", transcriptFile.toString(), "--model", model));
            }
            if ("VIDEO".equals(meeting.sourceMediaType()) && "AUDIO_VIDEO".equals(meeting.processingMode())) { command.add("--frames-dir"); command.add(frames.toString()); command.add("--frame-interval"); command.add(settings.value("video.frameInterval", "60")); }
            if ("base".equals(model) && packagedModel != null && !packagedModel.isBlank() && Files.isDirectory(Path.of(packagedModel))) { command.add("--model-dir"); command.add(packagedModel); }
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true).start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (all, line) -> all + line + "\n");
            }
            if (process.waitFor() != 0) throw new IOException(output.isBlank() ? "Transcription worker failed." : output.strip());
            if (Files.isDirectory(frames)) {
                try (var files = Files.list(frames)) {
                    frameRepository.replace(meeting.id(), files.filter(Files::isRegularFile).map(file -> {
                        String digits = file.getFileName().toString().replaceAll("\\D", "");
                        return new VideoFrameRepository.VideoFrame(Long.parseLong(digits), file.toString());
                    }).toList());
                }
            }
            meetingRepository.updateStatus(meeting.id(), "COMPLETED", transcriptFile.toString(), null);
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? "Processing failed." : exception.getMessage();
            meetingRepository.updateStatus(meeting.id(), "FAILED", null, message.substring(0, Math.min(message.length(), 800)));
        }
    }
}
