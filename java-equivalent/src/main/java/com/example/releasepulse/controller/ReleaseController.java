package com.example.releasepulse.controller;

import com.example.releasepulse.service.ScraperService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
public class ReleaseController {
    private final ScraperService scraperService;

    public ReleaseController(ScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @GetMapping("/api/releases")
    public ResponseEntity<Map<String, Object>> getReleases(
            @RequestParam(value = "force", defaultValue = "false") boolean force) {
        try {
            Map<String, Object> data = scraperService.getReleases(force);
            return ResponseEntity.ok(data);
        } catch (IOException e) {
            Map<String, Object> fallback = scraperService.getCachedUpdatesIfAvailable();
            if (fallback != null) {
                Map<String, Object> warningResponse = new HashMap<>(fallback);
                warningResponse.put("status", "warning");
                warningResponse.put("message", "Failed to refresh: " + e.getMessage() + ". Showing last cached version.");
                return ResponseEntity.ok(warningResponse);
            }
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("releases", new Object[]{});
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
