package com.github.cimsbioko.sidecar.events;

import java.io.File;

public class MetadataFetched implements FetchEvent {

    private final File metadata;

    public MetadataFetched(File metadata) {
        this.metadata = metadata;
    }

    public File getMetadata() {
        return metadata;
    }
}
