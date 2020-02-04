package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Campaign;
import com.github.cimsbioko.sidecar.Content;

public class ContentReady {

    private final Campaign campaign;
    private final Content content;

    public ContentReady(Campaign campaign, Content content) {
        this.campaign = campaign;
        this.content = content;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public Content getContent() {
        return content;
    }
}
