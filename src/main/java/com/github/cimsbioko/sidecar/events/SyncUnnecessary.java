package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Campaign;

public class SyncUnnecessary implements FetchEvent {

    private final Campaign campaign;

    public SyncUnnecessary(Campaign campaign) {
        this.campaign = campaign;
    }

    @Override
    public Campaign getCampaign() {
        return campaign;
    }
}
