package com.meetmind;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRepository {
    private final JdbcTemplate jdbc;
    public QuestionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public String create(String meetingId, String question, String scope) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO meeting_questions (id, meeting_id, question, status, created_at, scope) VALUES (?, ?, ?, 'GENERATING', ?, ?)", id, meetingId, question, Instant.now().toString(), scope);
        return id;
    }
    public void complete(String id, String answer) { jdbc.update("UPDATE meeting_questions SET status='COMPLETED', answer=?, error_message=NULL WHERE id=?", answer, id); }
    public void fail(String id, String error) { jdbc.update("UPDATE meeting_questions SET status='FAILED', error_message=? WHERE id=?", error, id); }
    public List<Question> findByMeetingId(String meetingId) { return jdbc.query("SELECT * FROM meeting_questions WHERE meeting_id=? ORDER BY created_at ASC", (rs, row) -> new Question(rs.getString("id"), rs.getString("question"), rs.getString("scope"), rs.getString("status"), rs.getString("answer"), rs.getString("error_message"), rs.getString("created_at")), meetingId); }
    public record Question(String id, String question, String scope, String status, String answer, String errorMessage, String createdAt) {}
}
