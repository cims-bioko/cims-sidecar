package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Campaign;

public class ContentMissing {

    private final Campaign campaign;

    public ContentMissing(Campaign campaign) {
        this.campaign = campaign;
    }

    public Campaign getCampaign() {
        return campaign;
    }
}
