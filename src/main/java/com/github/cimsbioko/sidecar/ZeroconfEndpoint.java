package com.github.cimsbioko.sidecar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ZeroconfEndpoint {

    @Autowired
    private ZeroconfService service;

    @RequestMapping("/zeroconf-status")
    public ResponseEntity<String> listStatus() {
        return ResponseEntity
                .ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(service.getStatus());
    }
}
