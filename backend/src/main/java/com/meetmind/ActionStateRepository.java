package com.meetmind;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ActionStateRepository {
    private final JdbcTemplate jdbc;
    public ActionStateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<Integer, Boolean> find(String meetingId) { return jdbc.query("SELECT action_index, completed FROM action_item_states WHERE meeting_id=?", (rs, row) -> Map.entry(rs.getInt("action_index"), rs.getBoolean("completed")), meetingId).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)); }
    public void set(String meetingId, int index, boolean completed) { jdbc.update("INSERT INTO action_item_states (meeting_id, action_index, completed) VALUES (?, ?, ?) ON CONFLICT(meeting_id, action_index) DO UPDATE SET completed=excluded.completed", meetingId, index, completed); }
}
