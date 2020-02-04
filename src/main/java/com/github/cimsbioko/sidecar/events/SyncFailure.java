package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Campaign;

import java.nio.file.Path;

public class SyncFailure implements FetchEvent {

    private final Campaign campaign;
    private final String message;
    private final Throwable failure;
    private final Path[] tempFiles;

    public SyncFailure(Campaign campaign, String message) {
        this(campaign, message, null);
    }

    public SyncFailure(Campaign campaign, String message, Throwable failure, Path... tempFiles) {
        this.campaign = campaign;
        this.message = message;
        this.failure = failure;
        this.tempFiles = tempFiles;
    }

    @Override
    public Campaign getCampaign() {
        return campaign;
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
