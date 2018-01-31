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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.jmdns.ServiceInfo;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

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

    @Value("${app.data.dir}/cims-tablet.db")
    private File content;

    @Value("${app.data.dir}/cims-tablet.db." + Metadata.FILE_EXT)
    private File metadata;

    @Value("${app.download.url}")
    private URL downloadUri;

    private URL sideloadUri;

    @Value("${app.download.username}")
    private String username;

    @Value("${app.download.password}")
    private String password;

    private Content verified;

    private String updating;

    private boolean isUpdating() {
        return updating != null;
    }

    private void setUpdating(Content c) {
        updating = c == null ? "missing content" : c.getContentHash();
    }

    private void clearUpdating() {
        updating = null;
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void requestUpdate() {
        counters.increment(UPDATE_REQUEST_METRIC);
        if (isUpdating()) {
            log.info("update to {} in progress, ignoring update request", updating);
            counters.increment(UPDATE_IGNORED_METRIC);
        } else {
            setUpdating(verified);
            log.info("requesting update to {}", updating);
            eventPublisher.publishEvent(new UpdateRequested(verified));
        }
    }

    public Content getContent() {
        return verified;
    }

    @EventListener
    public Object onApplicationReady(ApplicationReadyEvent event) {
        if (content.exists() && metadata.exists()) {
            log.info("existing content available");
            return new ContentAvailable(content, metadata);
        } else {
            log.info("existing content insufficient");
            return new ContentMissing();
        }
    }

    @EventListener
    public void onContentMissing(ContentMissing event) {
        requestUpdate();
    }

    @EventListener
    public Object onContentAvailable(ContentAvailable event) {
        try {
            Metadata metadata = loadMetadata(event.getMetadata());
            byte[] metadataHash = metadata.getFileHash(), computedHash = computeHash(event.getContent(), metadata.getFileHashAlg());
            String contentHash = encodeHexString(computedHash);
            if (Arrays.equals(metadataHash, computedHash)) {
                log.info("content verified");
                Content confirmed = new Content(contentHash, event.getContent(), event.getMetadata());
                return new ContentVerified(confirmed);
            } else {
                counters.increment(VERIFY_FAILURES_METRIC);
                if (log.isWarnEnabled()) {
                    log.warn("content failed verification: expected {}, computed {}", encodeHexString(metadataHash), contentHash);
                }
                return new SyncFailure("content verification failed, cleaning up", null,
                        event.getContent().toPath(), event.getMetadata().toPath());
            }
        } catch (Exception e) {
            return new SyncFailure("content verification failed", e);
        }
    }

    @EventListener
    public Object onContentVerified(ContentVerified event) {
        log.info("installing content {}", event.getContent().getContentHash());
        Content confirmed = event.getContent();
        if (confirmed.getContentFile().renameTo(content)) {
            if (confirmed.getMetadataFile().renameTo(metadata)) {
                return new ContentReady(new Content(confirmed.getContentHash(), content, metadata));
            } else {
                log.warn("failed to move metadata: {} to {}", confirmed.getMetadataFile(), metadata);
            }
        } else {
            log.warn("failed to move content: {} to {}", confirmed.getContentFile(), content);
        }
        counters.increment(INSTALL_FAILURES_METRIC);
        return new SyncFailure("failure installing content, cleaning up", null, content.toPath(), metadata.toPath());
    }

    @EventListener
    public void onContentReady(ContentReady event) {
        log.info("publishing content {}", event.getContent().getContentHash());
        verified = event.getContent();
        clearUpdating();
    }

    @EventListener
    public FetchEvent onUpdateRequested(UpdateRequested event) throws IOException {

        Request request = getDownloadRequestFactory(event.getExisting()).create();

        switch (request.getResponseCode()) {
            case SC_NOT_MODIFIED:
                log.info("no new content");
                return new SyncUnnecessary();
            case SC_OK:
                Path contentParent = content.toPath().getParent();
                if (request.getContentType().contains(METADATA_MEDIATYPE)) {
                    log.info("fetching metadata");
                    Path newMeta = createTempFile(contentParent, "metadata-", "." + Metadata.FILE_EXT);
                    try {
                        Files.copy(request.getInputStream(), newMeta, REPLACE_EXISTING);
                        return new MetadataFetched(newMeta.toFile());
                    } catch (IOException e) {
                        return new SyncFailure("metadata fetch failed", e, newMeta);
                    }
                } else if (request.getContentType().contains(DB_MEDIATYPE)) {
                    log.info("fetching database");
                    Path newDb = createTempFile(contentParent, "database-", ".db");
                    try (MetadataInputWrapper wrapper = new MetadataInputWrapper(request.getInputStream(), "", 65536, "MD5", "MD5", contentParent.toFile())) {
                        Files.copy(wrapper, newDb, REPLACE_EXISTING);
                        return new DatabaseFetched(wrapper.getMetadataFile(), newDb.toFile());
                    } catch (NoSuchAlgorithmException | IOException e) {
                        return new SyncFailure("database fetch failed", e, newDb);
                    }
                } else {
                    return new SyncFailure("unknown content " + request.getContentType());
                }
            default:
                return new SyncFailure("unexpected response: " + request.getResponseCode());
        }
    }

    @EventListener
    public Object onMetadataFetched(MetadataFetched event) {
        counters.increment(METADATA_FETCHES_METRIC);
        Path newDb = null;
        try {
            Metadata metadata = loadMetadata(event.getMetadata());
            log.info("incremental: {}", encodeHexString(metadata.getFileHash()));
            newDb = createTempFile(content.toPath().getParent(), "database-", ".db");
            ZSync.sync(metadata, content, newDb.toFile(), getSyncRequestFactory());
            return new ContentAvailable(newDb.toFile(), event.getMetadata());
        } catch (Exception e) {
            return newDb != null ? new SyncFailure("sync failed", e, newDb) : new SyncFailure("sync failed", e);
        }
    }

    @EventListener
    public Object onDatabaseFetched(DatabaseFetched event) {
        counters.increment(DATABASE_FETCHES_METRIC);
        try {
            if (log.isInfoEnabled()) {
                Metadata metadata = loadMetadata(event.getMetadata());
                log.info("full download: {}", encodeHexString(metadata.getFileHash()));
            }
            return new ContentAvailable(event.getDatabase(), event.getMetadata());
        } catch (Exception e) {
            return new SyncFailure("sync failed", e);
        }
    }

    @EventListener
    public void onPrimaryChanged(ZeroconfPrimaryChanged event) throws MalformedURLException {
        if (event.isServicePrimary()) {
            sideloadUri = null;
        } else {
            ServiceInfo primary = event.getPrimaryServiceInfo();
            String[] primaryUrls = primary.getURLs();
            if (primaryUrls.length > 0) {
                sideloadUri = new URL(primaryUrls[0]);
            } else {
                log.warn("primary without urls: {}", primary);
            }
        }
        log.info("zeroconf change, sync endpoint: {}", getURL());
    }

    @EventListener
    public void onSyncFailure(SyncFailure event) throws IOException {
        counters.increment(UPDATE_FAILURES_METRIC);
        if (event.getFailure() != null) {
            log.warn(event.getMessage(), event.getFailure());
        } else {
            log.warn(event.getMessage());
        }
        cleanupFiles(event.getTempFiles());
        clearUpdating();
    }

    @EventListener
    public void onSyncUnncessary(SyncUnnecessary event) throws IOException {
        counters.increment(UPDATE_NO_CHANGE_METRIC);
        clearUpdating();
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

    private URL getURL() {
        return sideloadUri != null ? sideloadUri : downloadUri;
    }

    private String getCreds() {
        return sideloadUri != null ? null : getBasicAuthCreds(username, password);
    }

    private RequestFactory getDownloadRequestFactory(Content existing) {
        if (existing != null) {
            String accept = String.join(", ", METADATA_MEDIATYPE, DB_MEDIATYPE);
            return new RequestFactory(getURL(), accept, getCreds(), existing.getContentHash());
        } else {
            return new RequestFactory(getURL(), DB_MEDIATYPE, getCreds());
        }
    }

    private RequestFactory getSyncRequestFactory() {
        return new RequestFactory(getURL(), DB_MEDIATYPE, getCreds());
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
