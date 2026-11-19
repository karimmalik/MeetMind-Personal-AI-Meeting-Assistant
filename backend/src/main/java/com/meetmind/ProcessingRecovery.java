package com.meetmind;

import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class ProcessingRecovery implements CommandLineRunner {
    private final MeetingRepository meetingRepository;
    private final MediaProcessingService mediaProcessingService;
    private final JdbcTemplate jdbc;

    public ProcessingRecovery(MeetingRepository meetingRepository, MediaProcessingService mediaProcessingService, JdbcTemplate jdbc) {
        this.meetingRepository = meetingRepository;
        this.mediaProcessingService = mediaProcessingService;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        jdbc.update("UPDATE meetings SET status = 'FAILED', error_message = 'Processing was interrupted when RIMeet closed. Use Retry to continue.' WHERE status = 'PROCESSING'");
        jdbc.update("UPDATE meeting_insights SET status = 'FAILED', error_message = 'Insight generation was interrupted when RIMeet closed. Generate again to retry.' WHERE status = 'GENERATING'");
        jdbc.update("UPDATE meeting_questions SET status = 'FAILED', error_message = 'Question generation was interrupted when RIMeet closed. Ask again to retry.' WHERE status = 'GENERATING'");
        meetingRepository.findQueued().forEach(stored -> mediaProcessingService.transcribe(stored.meeting(), Path.of(stored.storagePath())));
    }
}
