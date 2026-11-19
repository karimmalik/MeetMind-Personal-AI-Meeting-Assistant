package com.meetmind;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

@Configuration
public class StorageConfiguration {
    @Bean
    MeetMindStorage meetMindStorage() throws Exception {
        String appData = System.getenv("APPDATA");
        Path dataDirectory = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".meetmind")
                : Path.of(appData, "MeetMind");
        Path mediaDirectory = dataDirectory.resolve("media");
        Path transcriptDirectory = dataDirectory.resolve("transcripts");
        Path frameDirectory = dataDirectory.resolve("frames");
        Files.createDirectories(mediaDirectory);
        Files.createDirectories(transcriptDirectory);
        Files.createDirectories(frameDirectory);
        return new MeetMindStorage(dataDirectory, mediaDirectory, transcriptDirectory, frameDirectory, dataDirectory.resolve("meetmind.db"));
    }

    @Bean
    DataSource dataSource(MeetMindStorage storage) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + storage.databasePath().toAbsolutePath());
        return dataSource;
    }

    @Bean
    CommandLineRunner initializeDatabase(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS meetings (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                original_filename TEXT NOT NULL,
                source_media_type TEXT NOT NULL,
                processing_mode TEXT NOT NULL,
                status TEXT NOT NULL,
                storage_path TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                created_at TEXT NOT NULL
            )
            """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS meeting_insights (
                    meeting_id TEXT PRIMARY KEY, status TEXT NOT NULL, content TEXT, error_message TEXT, updated_at TEXT NOT NULL
                )
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS meeting_questions (
                    id TEXT PRIMARY KEY, meeting_id TEXT NOT NULL, question TEXT NOT NULL, status TEXT NOT NULL,
                    answer TEXT, error_message TEXT, created_at TEXT NOT NULL, scope TEXT NOT NULL DEFAULT 'MEETING'
                )
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS meeting_insight_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, meeting_id TEXT NOT NULL, content TEXT NOT NULL, created_at TEXT NOT NULL
                )
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_settings (
                    setting_key TEXT PRIMARY KEY, setting_value TEXT NOT NULL
                )
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS video_frames (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, meeting_id TEXT NOT NULL, timestamp_ms INTEGER NOT NULL, image_path TEXT NOT NULL
                )
                """);
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS action_item_states (
                    meeting_id TEXT NOT NULL, action_index INTEGER NOT NULL, completed INTEGER NOT NULL, PRIMARY KEY(meeting_id, action_index)
                )
                """);
            addColumnIfMissing(jdbcTemplate, "transcript_path", "TEXT");
            addColumnIfMissing(jdbcTemplate, "error_message", "TEXT");
            addQuestionColumnIfMissing(jdbcTemplate, "scope", "TEXT NOT NULL DEFAULT 'MEETING'");
        };
    }

    private void addColumnIfMissing(JdbcTemplate jdbcTemplate, String column, String definition) {
        boolean exists = jdbcTemplate.queryForList("PRAGMA table_info(meetings)").stream()
                .anyMatch(row -> column.equals(row.get("name")));
        if (!exists) jdbcTemplate.execute("ALTER TABLE meetings ADD COLUMN " + column + " " + definition);
    }

    private void addQuestionColumnIfMissing(JdbcTemplate jdbcTemplate, String column, String definition) {
        boolean exists = jdbcTemplate.queryForList("PRAGMA table_info(meeting_questions)").stream()
                .anyMatch(row -> column.equals(row.get("name")));
        if (!exists) jdbcTemplate.execute("ALTER TABLE meeting_questions ADD COLUMN " + column + " " + definition);
    }
}
