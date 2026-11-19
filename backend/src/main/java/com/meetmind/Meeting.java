package com.meetmind;

import java.time.Instant;

public record Meeting(
        String id,
        String title,
        String originalFilename,
        String sourceMediaType,
        String processingMode,
        String status,
        long sizeBytes,
        Instant createdAt,
        String errorMessage) {}
