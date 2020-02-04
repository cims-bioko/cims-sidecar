package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Campaign;

import java.io.File;

public class DatabaseFetched implements FetchEvent {

    private Campaign campaign;
    private final File database, metadata;

    public DatabaseFetched(Campaign campaign, File metadata, File database) {
        this.campaign = campaign;
        this.metadata = metadata;
        this.database = database;
    }

    @Override
    public Campaign getCampaign() {
        return campaign;
    }

    public File getMetadata() {
        return metadata;
    }

    public File getDatabase() {
        return database;
    }
}
