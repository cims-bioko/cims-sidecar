package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Campaign;
import com.github.cimsbioko.sidecar.Content;

public class ContentVerified {

    private final Content content;
    private final Campaign campaign;

    public ContentVerified(Campaign campaign, Content content) {
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
