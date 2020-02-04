package com.github.cimsbioko.sidecar;

import com.github.cimsbioko.sidecar.events.CampaignsUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.*;
import static java.util.Collections.unmodifiableMap;

@Component
public class CampaignService {

    private final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private final RestTemplate rest;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${app.mycampaigns.url}")
    private URI downloadUri;

    private Map<String, Campaign> campaigns = unmodifiableMap(emptyMap());

    public CampaignService(RestTemplate rest) {
        this.rest = rest;
    }

    public List<Campaign> fetchCampaigns() {
        return Arrays.asList(rest.getForEntity(downloadUri, Campaign[].class).getBody());
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 20 * 60 * 1000)
    public void updateCampaigns() {
        List<Campaign> fetched = fetchCampaigns();
        Map<String, Campaign> oldCampaigns = campaigns,
                newCampaigns = fetched.stream().collect(Collectors.toMap(Campaign::getUuid, Function.identity()));
        if (!oldCampaigns.equals(newCampaigns)) {
            log.info("campaign update: {}", fetched.stream().map(c -> String.format("%s (%s)", c.getUuid(), c.getName())).collect(Collectors.toList()));
            campaigns = unmodifiableMap(newCampaigns);
            eventPublisher.publishEvent(new CampaignsUpdated(unmodifiableMap(oldCampaigns), campaigns));
        }
    }

    public Optional<Campaign> getCampaign(String uuid) {
        return Optional.ofNullable(campaigns.get(uuid));
    }

    public Iterable<Campaign> getCampaigns() {
        return campaigns.values();
    }

    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        updateCampaigns();
    }
}
