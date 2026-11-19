package com.meetmind;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CodexInsightService {
    private final InsightRepository insights;
    private final MeetMindStorage storage;
    public CodexInsightService(InsightRepository insights, MeetMindStorage storage) { this.insights = insights; this.storage = storage; }

    @Async("meetingExecutor")
    public void generate(String meetingId, Path transcriptFile) {
        insights.set(meetingId, "GENERATING", null, null);
        try {
            Path schema = storage.dataDirectory().resolve("meeting-insight-schema.json");
            Path output = storage.dataDirectory().resolve("insights").resolve(meetingId + ".json");
            Files.createDirectories(output.getParent());
            Files.writeString(schema, schemaJson(), StandardCharsets.UTF_8);
            String transcript = Files.readString(transcriptFile, StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(List.of(System.getenv().getOrDefault("RIMEET_CODEX_PATH", "codex"), "exec", "--sandbox", "read-only", "--skip-git-repo-check", "--ephemeral", "--output-schema", schema.toString(), "-o", output.toString(), "-"))
                    .redirectErrorStream(true).start();
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write("Analyze this meeting transcript. Return only the required JSON. Do not execute commands or access files.\n\nTRANSCRIPT:\n" + transcript);
            }
            String logs;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                logs = reader.lines().reduce("", (all, line) -> all + line + "\n");
            }
            if (process.waitFor() != 0 || !Files.isRegularFile(output)) throw new IllegalStateException(logs.isBlank() ? "Codex analysis failed." : logs);
            insights.set(meetingId, "COMPLETED", Files.readString(output, StandardCharsets.UTF_8), null);
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? "Codex analysis failed." : exception.getMessage();
            insights.set(meetingId, "FAILED", null, message.substring(0, Math.min(message.length(), 800)));
        }
    }

    private String schemaJson() {
        return """
            {"type":"object","additionalProperties":false,"properties":{"summary":{"type":"string"},"decisions":{"type":"array","items":{"type":"string"}},"actionItems":{"type":"array","items":{"type":"object","additionalProperties":false,"properties":{"task":{"type":"string"},"owner":{"type":"string"},"dueDate":{"type":"string"}},"required":["task","owner","dueDate"]}},"risks":{"type":"array","items":{"type":"string"}},"followUp":{"type":"string"}},"required":["summary","decisions","actionItems","risks","followUp"]}
            """;
    }
}
