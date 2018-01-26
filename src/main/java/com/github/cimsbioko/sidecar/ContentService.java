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
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static javax.servlet.http.HttpServletResponse.*;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.apache.commons.codec.binary.Hex.encodeHexString;

@Component
public class ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentService.class);

    private static final String METADATA_MEDIATYPE = Metadata.MIME_TYPE, DB_MEDIATYPE = "application/x-sqlite3";


    @Autowired
    private ApplicationEventPublisher eventPublisher;

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

    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void requestUpdate() {
        eventPublisher.publishEvent(new UpdateRequested(verified));
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
            log.info("existing content insufficient, requesting update");
            return new UpdateRequested();
        }
    }

    @EventListener
    public ContentVerified onContentAvailable(ContentAvailable event) throws IOException, NoSuchAlgorithmException {
        Metadata metadata = loadMetadata(event.getMetadata());
        byte[] metadataHash = metadata.getFileHash(),
                computedHash = computeHash(event.getContent(), metadata.getFileHashAlg());
        String contentHash = encodeHexString(computedHash);
        if (Arrays.equals(metadataHash, computedHash)) {
            log.info("content verified");
            Content confirmed = new Content(contentHash, event.getContent(), event.getMetadata());
            return new ContentVerified(confirmed);
        } else {
            if (log.isWarnEnabled()) {
                log.warn("content failed verification: expected {}, computed {}", encodeHexString(metadataHash), contentHash);
            }
            return null;
        }
    }

    @EventListener
    public ContentReady onContentVerified(ContentVerified event) {
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
        return null;
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

    @EventListener
    public void onContentReady(ContentReady event) {
        log.info("publishing content {}", event.getContent().getContentHash());
        verified = event.getContent();
    }

    @EventListener
    public FetchEvent onUpdateRequested(UpdateRequested event) throws IOException {

        Request request = getDownloadRequestFactory(event.getExisting()).create();

        switch (request.getResponseCode()) {
            case SC_NOT_MODIFIED:
                log.info("no new content");
                break;
            case SC_OK:
                Path contentParent = content.toPath().getParent();
                if (request.getContentType().contains(METADATA_MEDIATYPE)) {
                    log.info("fetching metadata");
                    Path newMeta = createTempFile(contentParent, "metadata-", "." + Metadata.FILE_EXT);
                    Files.copy(request.getInputStream(), newMeta, REPLACE_EXISTING);
                    return new MetadataFetched(newMeta.toFile());
                } else if (request.getContentType().contains(DB_MEDIATYPE)) {
                    log.info("fetching database");
                    Path newDb = createTempFile(contentParent, "database-", ".db");
                    try {
                        try (MetadataInputWrapper wrapper = new MetadataInputWrapper(request.getInputStream(), "", 65536, "MD5", "MD5", contentParent.toFile())) {
                            Files.copy(wrapper, newDb, REPLACE_EXISTING);
                            return new DatabaseFetched(wrapper.getMetadataFile(), newDb.toFile());
                        }
                    } catch (NoSuchAlgorithmException e) {
                        log.error("failure downloading content", e);
                    }
                }
                break;
            case SC_NOT_FOUND:
                log.info("not found");
            default:
                log.info("unknown response: {}", request.getResponseCode());
        }

        return null;
    }

    private String getBasicAuthCreds(String username, String password) {
        return "Basic " + encodeBase64String((username + ":" + password).getBytes(Charset.forName("US-ASCII")));
    }

    @EventListener
    public ContentAvailable onMetadataFetched(MetadataFetched event) throws IOException, NoSuchAlgorithmException, InterruptedException {
        Metadata metadata = loadMetadata(event.getMetadata());
        log.info("incremental: {}", encodeHexString(metadata.getFileHash()));
        Path newDb = createTempFile(content.toPath().getParent(), "database-", ".db");
        ZSync.sync(metadata, content, newDb.toFile(), getSyncRequestFactory());
        return new ContentAvailable(newDb.toFile(), event.getMetadata());
    }

    @EventListener
    public ContentAvailable onDatabaseFetched(DatabaseFetched event) throws IOException, NoSuchAlgorithmException {
        Metadata metadata = loadMetadata(event.getMetadata());
        log.info("full download: {}", encodeHexString(metadata.getFileHash()));
        return new ContentAvailable(event.getDatabase(), event.getMetadata());
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
        log.info("syncing to: {}", getURL());
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
}
