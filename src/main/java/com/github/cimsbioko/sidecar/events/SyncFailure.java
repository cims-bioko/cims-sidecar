package com.github.cimsbioko.sidecar.events;

import java.nio.file.Path;

public class SyncFailure implements FetchEvent {

    private final String message;
    private final Throwable failure;
    private final Path[] tempFiles;

    public SyncFailure(String message) {
        this(message, null);
    }

    public SyncFailure(String message, Throwable failure, Path... tempFiles) {
        this.message = message;
        this.failure = failure;
        this.tempFiles = tempFiles;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getFailure() {
        return failure;
    }

    public Path[] getTempFiles() {
        return tempFiles;
    }
}
