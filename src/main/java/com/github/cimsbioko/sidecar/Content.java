package com.github.cimsbioko.sidecar;

import java.io.File;
import java.util.Objects;

public class Content {

    private final String contentHash;
    private final File content, metadata;

    Content(String contentHash, File content, File metadata) {
        this.contentHash = contentHash;
        this.content = content;
        this.metadata = metadata;
    }

    public String getContentHash() {
        return contentHash;
    }

    public File getMetadataFile() {
        return metadata;
    }

    public File getContentFile() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Content content = (Content) o;
        return Objects.equals(contentHash, content.contentHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentHash);
    }
}
