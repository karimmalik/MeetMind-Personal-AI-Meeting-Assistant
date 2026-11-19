package com.meetmind;

public record StoredMeeting(Meeting meeting, String storagePath, String transcriptPath) {}
