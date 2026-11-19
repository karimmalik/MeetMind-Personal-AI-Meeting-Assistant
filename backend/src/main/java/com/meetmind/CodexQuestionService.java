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
public class CodexQuestionService {
    private final QuestionRepository questions;
    private final MeetMindStorage storage;
    public CodexQuestionService(QuestionRepository questions, MeetMindStorage storage) { this.questions = questions; this.storage = storage; }
    @Async("meetingExecutor")
    public void answer(String id, String question, String scope, Path transcriptFile, String insightJson) {
        try {
            Path schema = storage.dataDirectory().resolve("meeting-question-schema.json");
            Path output = storage.dataDirectory().resolve("questions").resolve(id + ".json");
            Files.createDirectories(output.getParent()); Files.writeString(schema, schema(), StandardCharsets.UTF_8);
            String transcript = transcriptFile == null ? "" : Files.readString(transcriptFile, StandardCharsets.UTF_8);
            boolean broaderKnowledge = "GLOBAL".equalsIgnoreCase(scope);
            String instructions = broaderKnowledge
                ? "Answer using broader, generally known knowledge when the meeting does not define the term. Do not invent project-specific facts. Start the answer with 'Broader knowledge — '. Citations must be an empty array when the answer is not supported by the meeting. Return only required JSON."
                : "Answer the question using ONLY the meeting context. If not supported, say so. Give concise direct answer and timestamp citations. Return only required JSON.";
            String prompt = instructions + "\n\nQUESTION:\n" + question + "\n\nEXISTING INSIGHTS:\n" + (insightJson == null ? "None" : insightJson) + "\n\nTRANSCRIPT:\n" + transcript;
            Process process = new ProcessBuilder(List.of(System.getenv().getOrDefault("RIMEET_CODEX_PATH", "codex"), "exec", "--sandbox", "read-only", "--skip-git-repo-check", "--ephemeral", "--output-schema", schema.toString(), "-o", output.toString(), "-" )).redirectErrorStream(true).start();
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) { writer.write(prompt); }
            String logs; try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) { logs = reader.lines().reduce("", (all, line) -> all + line + "\n"); }
            if (process.waitFor() != 0 || !Files.isRegularFile(output)) throw new IllegalStateException(logs.isBlank() ? "Codex question failed." : logs);
            questions.complete(id, Files.readString(output, StandardCharsets.UTF_8));
        } catch (Exception exception) { String message = exception.getMessage() == null ? "Codex question failed." : exception.getMessage(); questions.fail(id, message.substring(0, Math.min(800, message.length()))); }
    }
    private String schema() { return """
        {"type":"object","additionalProperties":false,"properties":{"answer":{"type":"string"},"citations":{"type":"array","items":{"type":"object","additionalProperties":false,"properties":{"start":{"type":"string"},"end":{"type":"string"},"excerpt":{"type":"string"}},"required":["start","end","excerpt"]}}},"required":["answer","citations"]}
        """; }
}
