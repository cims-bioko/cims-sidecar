package com.github.cimsbioko.sidecar;

import com.github.batkinson.jrsync.Metadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

import java.io.File;

import static com.github.cimsbioko.sidecar.Application.CACHED_FILES_PATH;

@Controller
public class MobileDatabaseEndpoint {

    private static final String ACCEPT = "Accept";
    private static final String MOBILEDB_PATH = "/api/rest/mobiledb";
    private static final String SQLITE_MIME_TYPE = "application/x-sqlite3";

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
}
