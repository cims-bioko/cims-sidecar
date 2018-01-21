package com.github.cimsbioko.sidecar.events;

import com.github.cimsbioko.sidecar.Content;

public class UpdateRequested {

    private final Content existing;

    public UpdateRequested() {
        this(null);
    }

    public UpdateRequested(Content existing) {
        this.existing = existing;
    }

    public Content getExisting() {
        return existing;
    }
}
