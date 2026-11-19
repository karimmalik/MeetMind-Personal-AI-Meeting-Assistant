package com.meetmind;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LiveMeetingService {
    private final MeetMindStorage storage;
    private final SettingsRepository settings;
    private final ObjectMapper objectMapper;

    public LiveMeetingService(MeetMindStorage storage, SettingsRepository settings, ObjectMapper objectMapper) {
        this.storage = storage;
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    public String transcribeChunk(MultipartFile chunk) throws Exception {
        String filename = chunk.getOriginalFilename();
        String suffix = filename != null && filename.toLowerCase().endsWith(".wav") ? ".wav" : ".webm";
        Path input = Files.createTempFile(storage.dataDirectory(), "live-audio-", suffix);
        Path output = Files.createTempFile(storage.dataDirectory(), "live-transcript-", ".json");
        try {
            chunk.transferTo(input);
            runWorker(input, output);
            return Files.readString(output, StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }

    public String summarize(String transcript, String period) throws Exception {
        String prompt = "Summarize ONLY this live-meeting excerpt from the last " + period + ". Return concise JSON. Do not execute commands.\n\nTRANSCRIPT:\n" + transcript;
        return runCodex(prompt, """
            {"type":"object","additionalProperties":false,"properties":{"summary":{"type":"string"},"keyPoints":{"type":"array","items":{"type":"string"}}},"required":["summary","keyPoints"]}
            """);
    }

    public String answer(String transcript, String question, String scope) throws Exception {
        boolean broaderKnowledge = "GLOBAL".equalsIgnoreCase(scope);
        String instructions = broaderKnowledge
            ? "Answer the question using broader, generally known knowledge when the meeting transcript does not define the term. Do not invent project-specific facts. Start the answer with 'Broader knowledge — ' when you use knowledge that is not stated in the meeting. Return concise JSON. Do not execute commands."
            : "Answer the question using ONLY the live meeting transcript. If the answer is not present, say so. Return concise JSON. Do not execute commands.";
        String prompt = instructions + "\n\nQUESTION:\n" + question + "\n\nLIVE MEETING TRANSCRIPT:\n" + transcript;
        return runCodex(prompt, """
            {"type":"object","additionalProperties":false,"properties":{"answer":{"type":"string"}},"required":["answer"]}
            """);
    }

    private void runWorker(Path input, Path output) throws Exception {
        String model = settings.value("transcription.model", "base");
        String packagedWorker = System.getenv("RIMEET_WORKER_PATH");
        String packagedModel = System.getenv("RIMEET_MODEL_PATH");
        List<String> command;
        if (packagedWorker != null && !packagedWorker.isBlank() && Files.isRegularFile(Path.of(packagedWorker))) {
            command = new ArrayList<>(List.of(packagedWorker, "--input", input.toString(), "--output", output.toString(), "--model", model));
        } else {
            Path projectRoot = Path.of(System.getProperty("user.dir")).getParent();
            Path worker = projectRoot.resolve("transcription-worker").resolve("main.py");
            Path python = projectRoot.resolve("transcription-worker").resolve(".venv").resolve("Scripts").resolve("python.exe");
            command = new ArrayList<>(List.of(Files.isRegularFile(python) ? python.toString() : "python", worker.toString(), "--input", input.toString(), "--output", output.toString(), "--model", model));
        }
        if ("base".equals(model) && packagedModel != null && !packagedModel.isBlank() && Files.isDirectory(Path.of(packagedModel))) {
            command.add("--model-dir"); command.add(packagedModel);
        }
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String logs;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            logs = reader.lines().reduce("", (all, line) -> all + line + "\n");
        }
        if (process.waitFor() != 0 || !Files.isRegularFile(output)) throw new IllegalStateException(logs.isBlank() ? "Live transcription failed." : logs.strip());
    }

    private String runCodex(String prompt, String schemaJson) throws Exception {
        Path schema = Files.createTempFile(storage.dataDirectory(), "live-schema-", ".json");
        Path output = Files.createTempFile(storage.dataDirectory(), "live-codex-", ".json");
        try {
            Files.writeString(schema, schemaJson, StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(List.of(System.getenv().getOrDefault("RIMEET_CODEX_PATH", "codex"), "exec", "--sandbox", "read-only", "--skip-git-repo-check", "--ephemeral", "--output-schema", schema.toString(), "-o", output.toString(), "-"))
                    .redirectErrorStream(true).start();
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) { writer.write(prompt); }
            String logs;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) { logs = reader.lines().reduce("", (all, line) -> all + line + "\n"); }
            if (process.waitFor() != 0 || !Files.isRegularFile(output)) throw new IllegalStateException(logs.isBlank() ? "Codex analysis failed." : logs.strip());
            return Files.readString(output, StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(schema);
            Files.deleteIfExists(output);
        }
    }
}
