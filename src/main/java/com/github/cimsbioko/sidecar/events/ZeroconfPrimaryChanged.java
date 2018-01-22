package com.github.cimsbioko.sidecar.events;

import javax.jmdns.ServiceInfo;

public class ZeroconfPrimaryChanged {

    private final boolean servicePrimary;
    private final ServiceInfo primaryServiceInfo;

    public ZeroconfPrimaryChanged(boolean servicePrimary, ServiceInfo primaryServiceInfo) {
        this.servicePrimary = servicePrimary;
        this.primaryServiceInfo = primaryServiceInfo;
    }

    public boolean isServicePrimary() {
        return servicePrimary;
    }

    public ServiceInfo getPrimaryServiceInfo() {
        return primaryServiceInfo;
    }
}
