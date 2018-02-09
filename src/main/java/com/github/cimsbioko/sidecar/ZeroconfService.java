package com.github.cimsbioko.sidecar;

import com.github.cimsbioko.sidecar.events.ContentReady;
import com.github.cimsbioko.sidecar.events.ZeroconfPrimaryChanged;
import com.github.cimsbioko.sidecar.events.ZeroconfServicesChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;

import static javax.jmdns.ServiceInfo.create;

@Component
@Profile("zeroconf")
public class ZeroconfService implements ServiceListener {

    private static final Logger log = LoggerFactory.getLogger(ZeroconfService.class);

    private static final String SERVICE_TYPE = "_cimssc._tcp.local.";

    private int port;

    private JmDNS registry;

    private ServiceInfo serviceInfo, primaryServiceInfo;

    private ApplicationEventPublisher eventPublisher;

    @Value("${app.download.path}")
    private String downloadPath;

    @Autowired
    public ZeroconfService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
        port = event.getEmbeddedServletContainer().getPort();
    }

    @EventListener
    public void onApplicationStart(ApplicationReadyEvent event) throws IOException {
        log.info("starting zeroconf");
        registry = JmDNS.create();
        registry.addServiceListener(SERVICE_TYPE, this);
    }

    @EventListener
    public void onContentReady(ContentReady event) throws IOException {
        if (serviceInfo == null) {
            log.info("registering zeroconf service");
            serviceInfo = create(SERVICE_TYPE, "sidecar", port, "path=" + downloadPath);
            registry.registerService(serviceInfo);
        }
    }

    @EventListener
    public ZeroconfPrimaryChanged updatePrimary(ZeroconfServicesChanged event) {
        ServiceInfo computed = findPrimaryService();
        if (computed != primaryServiceInfo) {
            primaryServiceInfo = computed;
            return new ZeroconfPrimaryChanged(serviceInfo == primaryServiceInfo, primaryServiceInfo);
        }
        return null;
    }

    private ServiceInfo findPrimaryService() {
        ServiceInfo primary = serviceInfo;
        for (ServiceInfo service : registry.list(SERVICE_TYPE)) {
            log.debug("considering: {}", service);
            if (primary == null || primary.getName().compareTo(service.getName()) > 0) {
                log.debug("new best: {}", service);
                primary = service;
            }
        }
        log.debug("computed primary: {}", primary);
        return primary;
    }

    @Override
    public void serviceAdded(ServiceEvent event) {
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
        eventPublisher.publishEvent(new ZeroconfServicesChanged());
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        eventPublisher.publishEvent(new ZeroconfServicesChanged());
    }

    @PreDestroy
    public void shutdown() {
        log.info("stopping zeroconf");
        registry.unregisterAllServices();
    }

    public String getStatus() {
        return registry.toString();
    }
}
