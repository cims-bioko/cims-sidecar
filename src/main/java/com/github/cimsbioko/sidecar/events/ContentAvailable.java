package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Campaign;

import java.io.File;

public class ContentAvailable {

    private final Campaign campaign;
    private final File content, metadata;

    public ContentAvailable(Campaign campaign, File content, File metadata) {
        this.campaign = campaign;
        this.content = content;
        this.metadata = metadata;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public File getContent() {
        return content;
    }

    public File getMetadata() {
        return metadata;
    }

}
