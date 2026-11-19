package com.meetmind;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VideoFrameRepository {
    private final JdbcTemplate jdbc;
    public VideoFrameRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void replace(String meetingId, List<VideoFrame> frames) { jdbc.update("DELETE FROM video_frames WHERE meeting_id=?", meetingId); frames.forEach(frame -> jdbc.update("INSERT INTO video_frames (meeting_id, timestamp_ms, image_path) VALUES (?, ?, ?)", meetingId, frame.timestampMs(), frame.imagePath())); }
    public List<VideoFrame> find(String meetingId) { return jdbc.query("SELECT timestamp_ms, image_path FROM video_frames WHERE meeting_id=? ORDER BY timestamp_ms", (rs, row) -> new VideoFrame(rs.getLong("timestamp_ms"), rs.getString("image_path")), meetingId); }
    public record VideoFrame(long timestampMs, String imagePath) {}
}
