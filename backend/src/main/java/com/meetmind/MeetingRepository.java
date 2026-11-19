package com.meetmind;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MeetingRepository {
    private final JdbcTemplate jdbcTemplate;

    public MeetingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(Meeting meeting, String storagePath) {
        jdbcTemplate.update("""
            INSERT INTO meetings (id, title, original_filename, source_media_type, processing_mode, status, storage_path, size_bytes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, meeting.id(), meeting.title(), meeting.originalFilename(), meeting.sourceMediaType(),
            meeting.processingMode(), meeting.status(), storagePath, meeting.sizeBytes(), meeting.createdAt().toString());
    }

    public List<Meeting> findAll() {
        return jdbcTemplate.query("SELECT * FROM meetings ORDER BY created_at DESC", new MeetingRowMapper());
    }

    public List<StoredMeeting> findQueued() {
        return jdbcTemplate.query("SELECT * FROM meetings WHERE status = 'QUEUED' ORDER BY created_at ASC", (resultSet, rowNumber) ->
                new StoredMeeting(new Meeting(
                        resultSet.getString("id"), resultSet.getString("title"), resultSet.getString("original_filename"),
                        resultSet.getString("source_media_type"), resultSet.getString("processing_mode"), resultSet.getString("status"),
                        resultSet.getLong("size_bytes"), java.time.Instant.parse(resultSet.getString("created_at")), resultSet.getString("error_message")),
                        resultSet.getString("storage_path"), resultSet.getString("transcript_path")));
    }

    public Optional<StoredMeeting> findStoredById(String id) {
        List<StoredMeeting> result = jdbcTemplate.query("SELECT * FROM meetings WHERE id = ?", (resultSet, rowNumber) ->
                new StoredMeeting(new Meeting(
                        resultSet.getString("id"), resultSet.getString("title"), resultSet.getString("original_filename"),
                        resultSet.getString("source_media_type"), resultSet.getString("processing_mode"), resultSet.getString("status"),
                        resultSet.getLong("size_bytes"), java.time.Instant.parse(resultSet.getString("created_at")), resultSet.getString("error_message")),
                        resultSet.getString("storage_path"), resultSet.getString("transcript_path")), id);
        return result.stream().findFirst();
    }

    public void updateStatus(String id, String status, String transcriptPath, String errorMessage) {
        jdbcTemplate.update("UPDATE meetings SET status = ?, transcript_path = ?, error_message = ? WHERE id = ?",
                status, transcriptPath, errorMessage, id);
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM action_item_states WHERE meeting_id = ?", id);
        jdbcTemplate.update("DELETE FROM video_frames WHERE meeting_id = ?", id);
        jdbcTemplate.update("DELETE FROM meeting_questions WHERE meeting_id = ?", id);
        jdbcTemplate.update("DELETE FROM meeting_insight_history WHERE meeting_id = ?", id);
        jdbcTemplate.update("DELETE FROM meeting_insights WHERE meeting_id = ?", id);
        jdbcTemplate.update("DELETE FROM meetings WHERE id = ?", id);
    }

    private static final class MeetingRowMapper implements RowMapper<Meeting> {
        @Override
        public Meeting mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
            return new Meeting(
                    resultSet.getString("id"),
                    resultSet.getString("title"),
                    resultSet.getString("original_filename"),
                    resultSet.getString("source_media_type"),
                    resultSet.getString("processing_mode"),
                    resultSet.getString("status"),
                    resultSet.getLong("size_bytes"),
                    java.time.Instant.parse(resultSet.getString("created_at")),
                    resultSet.getString("error_message"));
        }
    }
}
