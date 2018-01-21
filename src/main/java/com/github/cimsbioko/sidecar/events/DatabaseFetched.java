package com.github.cimsbioko.sidecar.events;

import java.io.File;

public class DatabaseFetched implements FetchEvent {

    private final File database, metadata;

    public DatabaseFetched(File metadata, File database) {
        this.metadata = metadata;
        this.database = database;
    }

    public File getMetadata() {
        return metadata;
    }

    public File getDatabase() {
        return database;
    }
}
