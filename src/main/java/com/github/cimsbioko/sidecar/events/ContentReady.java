package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Content;

public class ContentReady {

    private final Content content;

    public ContentReady(Content content) {
        this.content = content;
    }

    public Content getContent() {
        return content;
    }
}
