package com.github.cimsbioko.sidecar;

import com.github.cimsbioko.sidecar.events.ContentReady;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.jmdns.JmDNS;
import java.io.IOException;

import static javax.jmdns.ServiceInfo.create;

@Component
public class ZeroconfService {

    private static final Logger log = LoggerFactory.getLogger(ZeroconfService.class);
    private static final String SERVICE_TYPE = "_cimssc._tcp.local.";

    private int port;

    private JmDNS registry;

    @EventListener
    public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
        port = event.getEmbeddedServletContainer().getPort();
    }

    @EventListener
    public void onApplicationStart(ApplicationReadyEvent event) throws IOException {
        log.info("starting zeroconf");
        registry = JmDNS.create();
    }

    @EventListener
    public void onContentReady(ContentReady event) throws IOException {
        log.info("registering zeroconf service");
        registry.unregisterAllServices();
        registry.registerService(create(SERVICE_TYPE, "sidecar", port, event.getContent().getContentHash()));
    }

    @PreDestroy
    public void shutdown() {
        log.info("stopping zeroconf");
        registry.unregisterAllServices();
    }
}
