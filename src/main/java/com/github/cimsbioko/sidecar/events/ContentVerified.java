package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Content;

public class ContentVerified {

    private final Content content;

    public ContentVerified(Content content) {
        this.content = content;
    }

    public Content getContent() {
        return content;
    }
}
