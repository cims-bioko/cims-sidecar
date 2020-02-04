package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Campaign;

import java.io.File;

public class MetadataFetched implements FetchEvent {

    private final Campaign campaign;
    private final File metadata;

    public MetadataFetched(Campaign campaign, File metadata) {
        this.campaign = campaign;
        this.metadata = metadata;
    }

    @Override
    public Campaign getCampaign() {
        return campaign;
    }

    public File getMetadata() {
        return metadata;
    }
}
