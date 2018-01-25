package com.github.cimsbioko.sidecar;

import com.github.batkinson.jrsync.Metadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.github.cimsbioko.sidecar.Application.CACHED_FILES_PATH;

@Controller
public class MobileDatabaseEndpoint {

    private static final String ACCEPT = "Accept";
    private static final String MOBILEDB_PATH = "/api/rest/mobiledb";
    private static final String SQLITE_MIME_TYPE = "application/x-sqlite3";
    private static final String MOBILEDB_EXPORT_PATH = "/api/rest/mobiledb/export";
    private static final String INSTALLABLE_FILENAME = "openhds.db";

    @Autowired
    private ContentService manager;

    @RequestMapping(value = "/update", method = RequestMethod.GET)
    @ResponseBody
    public String requestUpdate() {
        manager.requestUpdate();
        return null;
    }

    @RequestMapping(value = MOBILEDB_PATH, method = RequestMethod.GET, produces = {SQLITE_MIME_TYPE, Metadata.MIME_TYPE})
    public String mobileDB(WebRequest request) {

        Content content = manager.getContent();

        if (content == null) {
            return null;
        }

        if (request.checkNotModified(content.getContentHash())) {
            return null;
        }

        File metadata = content.getMetadataFile();
        String accept = request.getHeader(ACCEPT);

        if (accept != null && accept.contains(Metadata.MIME_TYPE) && metadata.exists()) {
            return "forward:" + CACHED_FILES_PATH + "/" + metadata.getName();
        }
        return "forward:" + CACHED_FILES_PATH + "/" + content.getContentFile().getName();
    }

    @RequestMapping(value = MOBILEDB_EXPORT_PATH, method = RequestMethod.GET)
    public void browserExport(HttpServletResponse response) throws IOException {

        Content content = manager.getContent();

        if (content == null || content.getContentFile() == null || !content.getContentFile().exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "No content found.");
            return;
        }

        FileSystemResource dbFileRes = new FileSystemResource(content.getContentFile());

        if (!dbFileRes.isReadable()) {
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
        }
    }
}
