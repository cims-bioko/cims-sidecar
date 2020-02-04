package com.github.cimsbioko.sidecar;

import com.github.batkinson.jrsync.Metadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.github.cimsbioko.sidecar.Application.CACHED_FILES_PATH;

@Controller
public class MobileDatabaseEndpoint {

    private static final String DB_UPDATES_METRIC = "updates.manual";
    private static final String DB_DOWNLOADS_METRIC = "downloads";
    private static final String DB_NO_CONTENT_METRIC = "downloads.nocontent";
    private static final String DB_NOT_MODIFIED_METRIC = "downloads.notmodified";
    private static final String METADATA_METRIC = "downloads.metadata";
    private static final String DATABASE_METRIC = "downloads.database";
    private static final String EXPORTS_METRIC = "exports";
    private static final String EXPORTS_NO_CONTENT_METRIC = "exports.nocontent";
    private static final String EXPORTS_NOT_READABLE_METRIC = "exports.notreadable";
    private static final String EXPORTS_FINISHED_METRIC = "exports.finished";

    private static final String ACCEPT = "Accept";
    private static final String MOBILEDB_PATH = "/api/rest/mobiledb/{campaign}";
    private static final String SQLITE_MIME_TYPE = "application/x-sqlite3";
    private static final String MOBILEDB_EXPORT_PATH = "/api/rest/mobiledb/{campaign}/export";
    private static final String INSTALLABLE_FILENAME = "openhds.db";

    @Autowired
    private ContentService contentService;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CounterService counters;

    @GetMapping("/update")
    @ResponseBody
    public String requestUpdate() {
        contentService.requestUpdate();
        counters.increment(DB_UPDATES_METRIC);
        return null;
    }

    @GetMapping(value = MOBILEDB_PATH, produces = {SQLITE_MIME_TYPE, Metadata.MIME_TYPE})
    public String mobileDB(@PathVariable String campaign, WebRequest request) {

        counters.increment(DB_DOWNLOADS_METRIC);

        Content content = campaignService.getCampaign(campaign).map(contentService::getContent).orElse(null);

        if (content == null) {
            counters.increment(DB_NO_CONTENT_METRIC);
            return null;
        }

        if (request.checkNotModified(content.getContentHash())) {
            counters.increment(DB_NOT_MODIFIED_METRIC);
            return null;
        }

        File metadata = content.getMetadataFile();
        String accept = request.getHeader(ACCEPT);

        if (accept != null && accept.contains(Metadata.MIME_TYPE) && metadata.exists()) {
            counters.increment(METADATA_METRIC);
            return "forward:" + CACHED_FILES_PATH + "/" + metadata.getName();
        }
        counters.increment(DATABASE_METRIC);
        return "forward:" + CACHED_FILES_PATH + "/" + content.getContentFile().getName();
    }

    @GetMapping(MOBILEDB_EXPORT_PATH)
    public void browserExport(@PathVariable String campaign, HttpServletResponse response) throws IOException {

        counters.increment(EXPORTS_METRIC);

        Content content = campaignService.getCampaign(campaign).map(contentService::getContent).orElse(null);

        if (content == null || content.getContentFile() == null || !content.getContentFile().exists()) {
            counters.increment(EXPORTS_NO_CONTENT_METRIC);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No content found.");
            return;
        }

        FileSystemResource dbFileRes = new FileSystemResource(content.getContentFile());

        if (!dbFileRes.isReadable()) {
            counters.increment(EXPORTS_NOT_READABLE_METRIC);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Content not readable.");
        } else {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=" + INSTALLABLE_FILENAME + ".zip");
            try (ZipOutputStream zOut = new ZipOutputStream(response.getOutputStream())) {
                ZipEntry e = new ZipEntry(INSTALLABLE_FILENAME);
                e.setSize(dbFileRes.contentLength());
                e.setTime(System.currentTimeMillis());
                zOut.putNextEntry(e);
                StreamUtils.copy(dbFileRes.getInputStream(), zOut);
                zOut.closeEntry();
                zOut.finish();
            }
            counters.increment(EXPORTS_FINISHED_METRIC);
        }
    }
}
