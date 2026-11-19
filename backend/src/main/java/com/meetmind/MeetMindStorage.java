package com.meetmind;

import java.nio.file.Path;

public record MeetMindStorage(Path dataDirectory, Path mediaDirectory, Path transcriptDirectory, Path frameDirectory, Path databasePath) {}
