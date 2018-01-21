package com.github.cimsbioko.sidecar.http;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class RequestFactory implements com.github.batkinson.jrsync.zsync.RangeRequestFactory {

    static final String GZIP = "gzip";

    private final URL endpoint;
    private final String mimeType;
    private final String auth;
    private final String eTag;

    public RequestFactory(URL endpoint, String mimeType, String auth) {
        this(endpoint, mimeType, auth, null);
    }

    public RequestFactory(URL endpoint, String mimeType, String auth, String eTag) {
        this.endpoint = endpoint;
        this.mimeType = mimeType;
        this.auth = auth;
        this.eTag = eTag;
    }

    @Override
    public Request create() throws IOException {
        HttpURLConnection c = (HttpURLConnection) endpoint.openConnection();
        c.addRequestProperty("Accept-Encoding", GZIP);
        if (mimeType != null) {
            c.addRequestProperty("Accept", mimeType);
        }
        if (auth != null) {
            c.addRequestProperty("Authorization", auth);
        }
        if (eTag != null) {
            c.addRequestProperty("If-None-Match", "\"" + eTag + "\"");
        }
        return new Request(c);
    }
}

