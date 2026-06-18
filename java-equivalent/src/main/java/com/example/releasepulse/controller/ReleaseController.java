package com.example.releasepulse.controller;

// Import the scraping service to retrieve parsed updates list
import com.example.releasepulse.service.ScraperService;
// Import HTTP Status constants for custom response header codes
import org.springframework.http.HttpStatus;
// Import ResponseEntity container class to return status codes and payloads
import org.springframework.http.ResponseEntity;
// Import GetMapping annotation to map requests to GET endpoints
import org.springframework.web.bind.annotation.GetMapping;
// Import RequestParam annotation to bind HTTP URL query parameters to Java variables
import org.springframework.web.bind.annotation.RequestParam;
// Import RestController annotation to mark this class as a REST JSON controller
import org.springframework.web.bind.annotation.RestController;

// Import standard IOException to handle networking and scrape errors
import java.io.IOException;
// Import HashMap class to construct warning and error response payloads
import java.util.HashMap;
// Import Map interface to return key-value dictionary formats
import java.util.Map;

// Designate class as a REST controller that handles incoming API request paths
@RestController
public class ReleaseController {
    // Define a reference pointer targeting our custom ScraperService instance
    private final ScraperService scraperService;

    // Use constructor injection to autowire and instantiate ScraperService
    public ReleaseController(ScraperService scraperService) {
        // Bind the injected scraper service parameter to local field instance
        this.scraperService = scraperService;
    }

    // Map incoming GET requests on path /api/releases to this handler method
    @GetMapping("/api/releases")
    public ResponseEntity<Map<String, Object>> getReleases(
            // Bind URL query query parameter 'force' to boolean variable, default to false
            @RequestParam(value = "force", defaultValue = "false") boolean force) {
        // Intercept any potential scraping connection failures
        try {
            // Retrieve structured map response from service (scrapes or pulls from cache)
            Map<String, Object> data = scraperService.getReleases(force);
            // Return successful HTTP 200 OK along with releases data payload
            return ResponseEntity.ok(data);
        // Catch network time-outs or page fetch exceptions
        } catch (IOException e) {
            // Retrieve last cached records list to prevent client data failures
            Map<String, Object> fallback = scraperService.getCachedUpdatesIfAvailable();
            // If cache records exist, return cached copy with warning
            if (fallback != null) {
                // Initialize a new map copy to safely add warning messages
                Map<String, Object> warningResponse = new HashMap<>(fallback);
                // Set response warning label status
                warningResponse.put("status", "warning");
                // Construct warning text containing the scrape exception message
                warningResponse.put("message", "Failed to refresh: " + e.getMessage() + ". Showing last cached version.");
                // Return HTTP 200 OK containing fallback cached updates
                return ResponseEntity.ok(warningResponse);
            }
            // If cache is empty and connection fails, build error payload
            Map<String, Object> errorResponse = new HashMap<>();
            // Set error label status
            errorResponse.put("status", "error");
            // Set error message describing connection issue
            errorResponse.put("message", e.getMessage());
            // Provide empty array placeholder for releases field
            errorResponse.put("releases", new Object[]{});
            // Return HTTP 500 Server Error along with error description
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}