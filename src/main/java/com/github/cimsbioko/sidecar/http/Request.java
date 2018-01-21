package com.github.cimsbioko.sidecar.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.zip.GZIPInputStream;

import static com.github.cimsbioko.sidecar.http.RequestFactory.GZIP;

public class Request implements com.github.batkinson.jrsync.zsync.RangeRequest {

    private final HttpURLConnection c;

    Request(HttpURLConnection c) {
        this.c = c;
    }

    @Override
    public int getResponseCode() throws IOException {
        return c.getResponseCode();
    }

    @Override
    public String getContentType() {
        return c.getContentType();
    }

    @Override
    public String getHeader(String name) {
        return c.getHeaderField(name);
    }

    @Override
    public void setHeader(String name, String value) {
        c.setRequestProperty(name, value);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (GZIP.equals(c.getContentEncoding())) {
            return new GZIPInputStream(c.getInputStream());
        } else {
            return c.getInputStream();
        }
    }

    @Override
    public void close() {
        c.disconnect();
    }
}
