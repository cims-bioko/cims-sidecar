package com.github.cimsbioko.sidecar.events;

import java.io.File;

public class ContentAvailable {

    private final File content, metadata;

    public ContentAvailable(File content, File metadata) {
        this.content = content;
        this.metadata = metadata;
    }

    public File getContent() {
        return content;
    }

    public File getMetadata() {
        return metadata;
    }

}
