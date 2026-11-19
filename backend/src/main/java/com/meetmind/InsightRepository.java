package com.meetmind;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InsightRepository {
    private final JdbcTemplate jdbc;
    public InsightRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void set(String meetingId, String status, String content, String error) {
        jdbc.update("""
            INSERT INTO meeting_insights (meeting_id, status, content, error_message, updated_at) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(meeting_id) DO UPDATE SET status=excluded.status, content=excluded.content, error_message=excluded.error_message, updated_at=excluded.updated_at
            """, meetingId, status, content, error, Instant.now().toString());
        if ("COMPLETED".equals(status) && content != null) jdbc.update("INSERT INTO meeting_insight_history (meeting_id, content, created_at) VALUES (?, ?, ?)", meetingId, content, Instant.now().toString());
    }
    public Optional<Insight> find(String meetingId) {
        return jdbc.query("SELECT status, content, error_message FROM meeting_insights WHERE meeting_id = ?", (rs, row) ->
                new Insight(rs.getString("status"), rs.getString("content"), rs.getString("error_message")), meetingId).stream().findFirst();
    }
    public java.util.List<InsightHistory> history(String meetingId) { return jdbc.query("SELECT id, content, created_at FROM meeting_insight_history WHERE meeting_id=? ORDER BY id DESC", (rs, row) -> new InsightHistory(rs.getLong("id"), rs.getString("content"), rs.getString("created_at")), meetingId); }
    public record Insight(String status, String content, String errorMessage) {}
    public record InsightHistory(long id, String content, String createdAt) {}
}
