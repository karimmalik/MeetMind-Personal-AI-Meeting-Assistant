package com.meetmind;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsRepository {
    private final JdbcTemplate jdbc;
    public SettingsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<String, String> all() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("transcription.model", "base");
        settings.put("video.frameInterval", "60");
        jdbc.queryForList("SELECT setting_key, setting_value FROM app_settings").forEach(row -> settings.put((String) row.get("setting_key"), (String) row.get("setting_value")));
        return settings;
    }
    public String value(String key, String fallback) { return all().getOrDefault(key, fallback); }
    public void save(Map<String, String> settings) { settings.forEach((key, value) -> jdbc.update("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value", key, value)); }
}
