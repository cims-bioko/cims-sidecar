package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Campaign;

import java.util.Map;

public class CampaignsUpdated {

    private final Map<String, Campaign> oldCampaigns, newCampaigns;

    public CampaignsUpdated(Map<String, Campaign> oldCampaigns, Map<String, Campaign> newCampaigns) {
        this.oldCampaigns = oldCampaigns;
        this.newCampaigns = newCampaigns;
    }

    public Map<String, Campaign> getOldCampaigns() {
        return oldCampaigns;
    }

    public Map<String, Campaign> getNewCampaigns() {
        return newCampaigns;
    }
}
