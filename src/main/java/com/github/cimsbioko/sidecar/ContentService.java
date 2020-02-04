package com.github.cimsbioko.sidecar;

import com.github.batkinson.jrsync.Metadata;
import com.github.batkinson.jrsync.MetadataInputWrapper;
import com.github.batkinson.jrsync.zsync.ZSync;
import com.github.cimsbioko.sidecar.events.*;
import com.github.cimsbioko.sidecar.http.Request;
import com.github.cimsbioko.sidecar.http.RequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.jmdns.ServiceInfo;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.apache.commons.codec.binary.Hex.encodeHexString;

@Component
public class ContentService {

    private static final String UPDATE_REQUEST_METRIC = "updates.total";
    private static final String METADATA_FETCHES_METRIC = "fetches.metadata";
    private static final String DATABASE_FETCHES_METRIC = "fetches.database";
    private static final String VERIFY_FAILURES_METRIC = "updates.verified";
    private static final String INSTALL_FAILURES_METRIC = "updates.installed";
    private static final String UPDATE_FAILURES_METRIC = "updates.failed";
    private static final String UPDATE_NO_CHANGE_METRIC = "updates.nochange";
    private static final String UPDATE_IGNORED_METRIC = "updates.ignored";

    private static final Logger log = LoggerFactory.getLogger(ContentService.class);

    private static final String METADATA_MEDIATYPE = Metadata.MIME_TYPE, DB_MEDIATYPE = "application/x-sqlite3";


    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private CounterService counters;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private FileSystem fs;

    @Value("${app.download.url}")
    private URI downloadUri;

    private URI sideloadUri;

    @Value("${app.download.username}")
    private String username;

    @Value("${app.download.password}")
    private String password;

    private Map<Campaign, Content> verified = new ConcurrentHashMap<>();

    private Map<Campaign, String> updating = new ConcurrentHashMap<>();

    private boolean isUpdating(Campaign campaign) {
        return updating.get(campaign) != null;
    }

    private void setUpdating(Campaign campaign, Content content) {
        updating.put(campaign, content == null ? "missing content" : content.getContentHash());
    }

    private void clearUpdating(Campaign campaign) {
        updating.remove(campaign);
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void requestUpdate() {
        counters.increment(UPDATE_REQUEST_METRIC);
        for (Campaign campaign : campaignService.getCampaigns()) {
            requestUpdate(campaign);
        }
    }

    private void requestUpdate(Campaign campaign) {
        if (isUpdating(campaign)) {
            log.info("update to {} ({}) in progress, ignoring update request", updating.get(campaign), campaign.getName());
            counters.increment(UPDATE_IGNORED_METRIC);
        } else {
            setUpdating(campaign, verified.get(campaign));
            log.info("requesting update to {} ({})", updating.get(campaign), campaign.getName());
            eventPublisher.publishEvent(new UpdateRequested(campaign, verified.get(campaign)));
        }
    }

    public Content getContent(Campaign campaign) {
        return verified.get(campaign);
    }

    @EventListener
    public void onCampaignUpdate(CampaignsUpdated event) {
        cleanupCampaignContent(event.getOldCampaigns(), event.getNewCampaigns());
        init();
    }

    private void init() {
        for (Campaign campaign : campaignService.getCampaigns()) {
            File content = fs.getContent(campaign), metadata = fs.getMetadata(campaign);
            if (content.exists() && metadata.exists()) {
                log.info("existing content available {} ({})", campaign.getUuid(), campaign.getName());
                eventPublisher.publishEvent(new ContentAvailable(campaign, content, metadata));
            } else {
                log.info("existing content insufficient {} ({})", campaign.getUuid(), campaign.getName());
                eventPublisher.publishEvent(new ContentMissing(campaign));
            }
        }
    }

    private void cleanupCampaignContent(Map<String, Campaign> old, Map<String, Campaign> now) {
        Set<Campaign> removed = new HashSet<>(old.values());
        removed.removeAll(now.values());
        for (Campaign c : removed) {
            try {
                log.info("cleaning up {} ({})", c.getUuid(), c.getName());
                removeCampaign(c);
            } catch (IOException e) {
                log.warn("failed to cleanup content", e);
            }
        }
    }

    private void removeCampaign(Campaign c) throws IOException {
        verified.remove(c);
        cleanupFiles(fs.getContent(c).toPath(), fs.getMetadata(c).toPath());
    }

    @EventListener
    public void onContentMissing(ContentMissing event) {
        requestUpdate(event.getCampaign());
    }

    @EventListener
    public Object onContentAvailable(ContentAvailable event) {
        Campaign campaign = event.getCampaign();
        try {
            Metadata metadata = loadMetadata(event.getMetadata());
            byte[] metadataHash = metadata.getFileHash(), computedHash = computeHash(event.getContent(), metadata.getFileHashAlg());
            String contentHash = encodeHexString(computedHash);
            if (Arrays.equals(metadataHash, computedHash)) {
                log.info("content verified {} ({})", campaign.getUuid(), campaign.getName());
                Content confirmed = new Content(contentHash, event.getContent(), event.getMetadata());
                return new ContentVerified(campaign, confirmed);
            } else {
                counters.increment(VERIFY_FAILURES_METRIC);
                if (log.isWarnEnabled()) {
                    log.warn("content failed verification {} ({}): expected {}, computed {}",
                            campaign.getUuid(), campaign.getName(), encodeHexString(metadataHash), contentHash);
                }
                return new SyncFailure(event.getCampaign(), "content verification failed, cleaning up", null,
                        event.getContent().toPath(), event.getMetadata().toPath());
            }
        } catch (Exception e) {
            return new SyncFailure(event.getCampaign(), "content verification failed", e);
        }
    }

    @EventListener
    public Object onContentVerified(ContentVerified event) {
        Campaign campaign = event.getCampaign();
        Content confirmed = event.getContent();
        log.info("installing content {} ({}), hash: {}", campaign.getUuid(), campaign.getName(), confirmed.getContentHash());
        File content = fs.getContent(campaign), metadata = fs.getMetadata(campaign);
        if (confirmed.getContentFile().renameTo(content)) {
            if (confirmed.getMetadataFile().renameTo(metadata)) {
                return new ContentReady(campaign, new Content(confirmed.getContentHash(), content, metadata));
            } else {
                log.warn("failed to move metadata {} ({}): {} to {}",
                        campaign.getUuid(), campaign.getName(), confirmed.getMetadataFile(), metadata);
            }
        } else {
            log.warn("failed to move content {} ({}): {} to {}",
                    campaign.getUuid(), campaign.getName(), confirmed.getContentFile(), content);
        }
        counters.increment(INSTALL_FAILURES_METRIC);
        return new SyncFailure(campaign, "failure installing content, cleaning up", null, content.toPath(), metadata.toPath());
    }

    @EventListener
    public void onContentReady(ContentReady event) {
        Campaign campaign = event.getCampaign();
        Content content = event.getContent();
        log.info("publishing content {} ({}), hash: {}", campaign.getUuid(), campaign.getName(), content.getContentHash());
        verified.put(campaign, content);
        clearUpdating(campaign);
    }

    @EventListener
    public FetchEvent onUpdateRequested(UpdateRequested event) {
        Campaign campaign = event.getCampaign();
        try {
            Request request = getDownloadRequestFactory(campaign, event.getExisting()).create();
            switch (request.getResponseCode()) {
                case SC_NOT_MODIFIED:
                    log.info("no new content {} ({})", campaign.getUuid(), campaign.getName());
                    return new SyncUnnecessary(campaign);
                case SC_OK:
                    Path contentParent = fs.getContent(campaign).toPath().getParent();
                    if (request.getContentType().contains(METADATA_MEDIATYPE)) {
                        log.info("fetching metadata {} ({})", campaign.getUuid(), campaign.getName());
                        Path newMeta = createTempFile(contentParent, "metadata-", "." + Metadata.FILE_EXT);
                        try {
                            Files.copy(request.getInputStream(), newMeta, REPLACE_EXISTING);
                            return new MetadataFetched(campaign, newMeta.toFile());
                        } catch (IOException e) {
                            return new SyncFailure(campaign, "metadata fetch failed", e, newMeta);
                        }
                    } else if (request.getContentType().contains(DB_MEDIATYPE)) {
                        log.info("fetching database {} ({})", campaign.getUuid(), campaign.getName());
                        Path newDb = createTempFile(contentParent, "database-", ".db");
                        try (MetadataInputWrapper wrapper = new MetadataInputWrapper(request.getInputStream(), "", 65536, "MD5", "MD5", contentParent.toFile())) {
                            Files.copy(wrapper, newDb, REPLACE_EXISTING);
                            return new DatabaseFetched(campaign, wrapper.getMetadataFile(), newDb.toFile());
                        } catch (NoSuchAlgorithmException | IOException e) {
                            return new SyncFailure(campaign, "database fetch failed", e, newDb);
                        }
                    } else {
                        return new SyncFailure(campaign, "unknown content " + request.getContentType());
                    }
                default:
                    return new SyncFailure(campaign, "unexpected response: " + request.getResponseCode());
            }
        } catch (IOException e) {
            return new SyncFailure(campaign, "io error handling update request", e);
        }
    }

    @EventListener
    public Object onMetadataFetched(MetadataFetched event) {
        counters.increment(METADATA_FETCHES_METRIC);
        Path newDb = null;
        Campaign campaign = event.getCampaign();
        try {
            Metadata metadata = loadMetadata(event.getMetadata());
            log.info("incremental {} ({}): hash {}",
                    campaign.getUuid(), campaign.getName(), encodeHexString(metadata.getFileHash()));
            newDb = createTempFile(fs.getContent(campaign).toPath().getParent(), "database-", ".db");
            ZSync.sync(metadata, fs.getContent(campaign), newDb.toFile(), getSyncRequestFactory(campaign));
            return new ContentAvailable(campaign, newDb.toFile(), event.getMetadata());
        } catch (Exception e) {
            return newDb != null ? new SyncFailure(campaign, "sync failed", e, newDb) : new SyncFailure(campaign, "sync failed", e);
        }
    }

    @EventListener
    public Object onDatabaseFetched(DatabaseFetched event) {
        counters.increment(DATABASE_FETCHES_METRIC);
        Campaign campaign = event.getCampaign();
        try {
            if (log.isInfoEnabled()) {
                Metadata metadata = loadMetadata(event.getMetadata());
                log.info("full download {} ({}): hash {}",
                        campaign.getUuid(), campaign.getName(), encodeHexString(metadata.getFileHash()));
            }
            return new ContentAvailable(campaign, event.getDatabase(), event.getMetadata());
        } catch (Exception e) {
            return new SyncFailure(campaign, "sync failed", e);
        }
    }

    @EventListener
    public void onPrimaryChanged(ZeroconfPrimaryChanged event) {
        if (event.isServicePrimary()) {
            sideloadUri = null;
        } else {
            ServiceInfo primary = event.getPrimaryServiceInfo();
            String[] primaryUrls = primary.getURLs();
            if (primaryUrls.length > 0) {
                String url = primaryUrls[0];
                try {
                    sideloadUri = new URI(url);
                } catch (URISyntaxException e) {
                    log.warn("new primary has bad url: {}", url);
                }
            } else {
                log.warn("primary without urls: {}", primary);
            }
        }
        log.info("zeroconf change, sync endpoint: {}", getURI());
    }

    @EventListener
    public void onSyncFailure(SyncFailure event) {
        counters.increment(UPDATE_FAILURES_METRIC);
        clearUpdating(event.getCampaign());
        if (event.getFailure() != null) {
            log.warn(event.getMessage(), event.getFailure());
        } else {
            log.warn(event.getMessage());
        }
        try {
            cleanupFiles(event.getTempFiles());
        } catch (IOException e) {
            log.warn("failed during cleanup", e);
        }
    }

    @EventListener
    public void onSyncUnncessary(SyncUnnecessary event) {
        counters.increment(UPDATE_NO_CHANGE_METRIC);
        clearUpdating(event.getCampaign());
    }

    private void cleanupFiles(Path... filesToRemove) throws IOException {
        if (filesToRemove != null) {
            for (Path p : filesToRemove) {
                if (!deleteIfExists(p)) {
                    log.warn("failed to delete file {}", p);
                }
            }
        }
    }

    private URI getURI() {
        return sideloadUri != null ? sideloadUri : downloadUri;
    }

    private URI getURI(Campaign campaign) {
        return getURI().resolve(campaign.getUuid());
    }

    private String getCreds() {
        return sideloadUri != null ? null : getBasicAuthCreds(username, password);
    }

    private RequestFactory getDownloadRequestFactory(Campaign campaign, Content existing) {
        if (existing != null) {
            String accept = String.join(", ", METADATA_MEDIATYPE, DB_MEDIATYPE);
            return new RequestFactory(getURI(campaign), accept, getCreds(), existing.getContentHash());
        } else {
            return new RequestFactory(getURI(campaign), DB_MEDIATYPE, getCreds());
        }
    }

    private RequestFactory getSyncRequestFactory(Campaign campaign) {
        return new RequestFactory(getURI(campaign), DB_MEDIATYPE, getCreds());
    }

    private byte[] computeHash(File content, String fileHashAlg) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance(fileHashAlg);
        try (FileInputStream fin = new FileInputStream(content)) {
            byte[] buf = new byte[8192];
            int bytesRead;
            do {
                bytesRead = fin.read(buf);
                if (bytesRead > 0) {
                    digest.update(buf, 0, bytesRead);
                }
            } while (bytesRead >= 0);
            return digest.digest();
        }
    }

    private Metadata loadMetadata(File metaFile) throws IOException, NoSuchAlgorithmException {
        try (DataInputStream din = new DataInputStream(new FileInputStream(metaFile))) {
            return Metadata.read(din);
        }
    }

    private String getBasicAuthCreds(String username, String password) {
        return "Basic " + encodeBase64String((username + ":" + password).getBytes(Charset.forName("US-ASCII")));
    }
}
