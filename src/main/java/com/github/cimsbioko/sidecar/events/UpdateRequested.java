package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Campaign;
import com.github.cimsbioko.sidecar.Content;

public class UpdateRequested {

    private final Campaign campaign;
    private final Content existing;

    public UpdateRequested(Campaign campaign, Content existing) {
        this.campaign = campaign;
        this.existing = existing;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public Content getExisting() {
        return existing;
    }
}
