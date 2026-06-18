package com.example.releasepulse.service;

// Import the data carrier record class to capture update properties
import com.example.releasepulse.model.ReleaseUpdate;
// Import the central Jsoup entry class to execute HTTP requests and create Documents
import org.jsoup.Jsoup;
// Import Document class representing HTML DOM trees
import org.jsoup.nodes.Document;
// Import Element class representing HTML tag nodes
import org.jsoup.nodes.Element;
// Import Elements container wrapper class representing query lists of Elements
import org.jsoup.select.Elements;
// Import Service annotation to register this class as a Spring component bean
import org.springframework.stereotype.Service;

// Import IOException to intercept connection failures and timeout exceptions
import java.io.IOException;
// Import LocalDateTime class to retrieve local timestamps
import java.time.LocalDateTime;
// Import DateTimeFormatter class to build printable date-time string forms
import java.time.format.DateTimeFormatter;
// Import standard collections classes used to build maps, list, and arrays
import java.util.*;
// Import logging utilities to report scraper activity and trace connection logs
import java.util.logging.Logger;

// Designate class as a service layer component bean managed by Spring container
@Service
public class ScraperService {
    // Instantiate a standard Logger named after this service class
    private static final Logger logger = Logger.getLogger(ScraperService.class.getName());
    // Define target upgrade notes URL for crawling
    private static final String UPGRADE_NOTES_URL = "https://docs.spring.io/spring-ai/reference/upgrade-notes.html";

    // In-memory list cache to store parsed release updates records
    private List<ReleaseUpdate> cachedUpdates = null;
    // In-memory string cache to store the last refresh timestamp
    private String lastUpdated = null;

    // Define thread-safe public method to get release notes payload Map
    public synchronized Map<String, Object> getReleases(boolean forceRefresh) throws IOException {
        // If cache records exist and client did not request forced sync, return cache
        if (cachedUpdates != null && !forceRefresh) {
            // Output log notice returning cache list
            logger.info("Returning cached updates");
            // Build response containing cache list and true cached flag
            return buildResponse(cachedUpdates, true);
        }

        // Output log notice indicating active download
        logger.info("Fetching and parsing live release notes...");
        // Invoke local scrape routine to get fresh results
        List<ReleaseUpdate> updates = fetchAndParse();
        // Update local cache records list
        cachedUpdates = updates;
        // Save current timestamp formatted as Year-Month-Day Hour:Minute:Second
        lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        // Return results and set cached flag to false
        return buildResponse(updates, false);
    }

    // Define public method to retrieve cached updates safely without throwing exceptions
    public synchronized Map<String, Object> getCachedUpdatesIfAvailable() {
        // If cache list is populated
        if (cachedUpdates != null) {
            // Build and return cache map
            return buildResponse(cachedUpdates, true);
        }
        // Return null if cache has not been populated yet
        return null;
    }

    // Helper method to wrap data arrays into structured API response maps
    private Map<String, Object> buildResponse(List<ReleaseUpdate> updates, boolean cached) {
        // Instantiate a new HashMap container
        Map<String, Object> response = new HashMap<>();
        // Set api response status field to success
        response.put("status", "success");
        // Add the updates records list
        response.put("releases", updates);
        // Add the cache timestamp string
        response.put("last_updated", lastUpdated);
        // Add the boolean flag indicating if the data came from cache
        response.put("cached", cached);
        // Return the compiled Map
        return response;
    }

    // Scrape routine that requests docs, parses DOM trees, and runs category mapping heuristics
    private List<ReleaseUpdate> fetchAndParse() throws IOException {
        // Request the HTML documentation using JSoup with specific agent headers and 20s timeout
        Document doc = Jsoup.connect(UPGRADE_NOTES_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(20000)
                .get();

        // Instantiate empty list to store compiled ReleaseUpdate records
        List<ReleaseUpdate> updates = new ArrayList<>();
        // Locate primary article container tag
        Element mainDoc = doc.selectFirst("article.doc");
        // If article.doc is missing, fallback to parsing body directly
        if (mainDoc == null) {
            // Assign body element as primary container
            mainDoc = doc.body();
            // If body is missing, return empty list immediately
            if (mainDoc == null) return updates;
        }

        // Query all version section divisions
        Elements sect1Divs = mainDoc.select("div.sect1");
        // Iterate through all version containers
        for (Element sect1 : sect1Divs) {
            // Locate the section h2 heading element
            Element h2 = sect1.selectFirst("h2");
            // Skip section if header is missing
            if (h2 == null) continue;

            // Strip version label prefix to isolate pure numbers (e.g. 2.0.0)
            String versionText = h2.text().replace("Upgrading to ", "").trim();

            // Query all nested category subsections (sect2 divs)
            Elements sect2Divs = sect1.select("div.sect2");
            // Iterate through category subsection divs
            for (Element sect2 : sect2Divs) {
                // Locate the subsection h3 heading element
                Element h3 = sect2.selectFirst("h3");
                // Skip subsection if category header is missing
                if (h3 == null) continue;

                // Strip leading/trailing whitespaces from category header
                String categoryText = h3.text().trim();
                // Retrieve the id attribute representing the page anchor link segment
                String categoryId = h3.id();

                // Query all nested subheadings (sect3 divs) in this category
                Elements sect3Divs = sect2.select("div.sect3");
                // Setup flag indicator to evaluate subhead descriptive properties
                boolean hasDescriptiveSubheadings = false;
                // List generic headings indicating standard layouts
                List<String> genericWords = Arrays.asList("impact", "migration", "why", "detail");

                // Evaluate subheads descriptive properties
                for (Element sect3 : sect3Divs) {
                    // Locate the h4 heading tag of this block
                    Element h4 = sect3.selectFirst("h4");
                    // If an h4 heading exists
                    if (h4 != null) {
                        // Clean and check heading text against generic patterns
                        String h4Text = h4.text().toLowerCase().trim();
                        boolean isGeneric = false;
                        for (String gen : genericWords) {
                            if (h4Text.contains(gen)) {
                                isGeneric = true;
                                break;
                            }
                        }
                        // If the subheading represents a specific feature topic
                        if (!isGeneric) {
                            // Set descriptive flag to true
                            hasDescriptiveSubheadings = true;
                            // Break out of loop since one descriptive subhead is enough
                            break;
                        }
                    }
                }

                // If descriptive subheads exist, parse each sect3 as an individual card
                if (!sect3Divs.isEmpty() && hasDescriptiveSubheadings) {
                    for (Element sect3 : sect3Divs) {
                        // Locate the h4 heading tag
                        Element h4 = sect3.selectFirst("h4");
                        // Skip if heading is missing
                        if (h4 == null) continue;

                        // Retrieve the heading title string
                        String updateTitle = h4.text().trim();
                        // Retrieve the anchor ID string of the update card
                        String updateId = h4.id();

                        // Accumulate outer HTML string of all child elements excluding the h4 title tag
                        StringBuilder contentHtml = new StringBuilder();
                        for (Element child : sect3.children()) {
                            // Skip the title element node
                            if (child.tagName().equals("h4")) continue;
                            // Append the sibling element outer HTML
                            contentHtml.append(child.outerHtml());
                        }

                        // Determine category tag string using mapping heuristics
                        String cat = determineCategory(updateTitle, categoryText);
                        // Add record to compilation list
                        updates.add(new ReleaseUpdate(
                                updateId.isEmpty() ? "up-" + updates.size() : updateId,
                                versionText,
                                cat,
                                categoryText,
                                updateTitle,
                                contentHtml.toString().trim(),
                                updateId.isEmpty() ? UPGRADE_NOTES_URL : UPGRADE_NOTES_URL + "#" + updateId
                        ));
                    }
                // Else, consolidate the entire category section (sect2) into a single update card
                } else {
                    // Accumulate outer HTML string of all child elements excluding the h3 title tag
                    StringBuilder contentHtml = new StringBuilder();
                    for (Element child : sect2.children()) {
                        // Skip the section title tag element node
                        if (child.tagName().equals("h3")) continue;
                        // Append the child element outer HTML
                        contentHtml.append(child.outerHtml());
                    }

                    // Determine category tag string using mapping heuristics
                    String cat = determineCategory(categoryText, "");
                    // Add consolidated record to compilation list
                    updates.add(new ReleaseUpdate(
                            categoryId.isEmpty() ? "up-" + updates.size() : categoryId,
                            versionText,
                            cat,
                            "General",
                            categoryText,
                            contentHtml.toString().trim(),
                            categoryId.isEmpty() ? UPGRADE_NOTES_URL : UPGRADE_NOTES_URL + "#" + categoryId
                    ));
                }
            }
        }
        // Return compiled updates list
        return updates;
    }

    // Determine category based on checking title string and parent category string
    private String determineCategory(String title, String parentCategory) {
        // Convert terms to lowercase for robust match checks
        String t = (title + " " + parentCategory).toLowerCase();
        // Check for advisor words
        if (t.contains("advisor") || t.contains("vector-store-advisor")) return "Advisors";
        // Check for tool calling words
        if (t.contains("tool") || t.contains("mcp") || t.contains("callback") || t.contains("functioncall")) return "Tool Calling & MCP";
        // Check for memory words
        if (t.contains("memory") || t.contains("chatmemory") || t.contains("history")) return "Chat Memory";
        // Check for model provider words
        if (t.contains("model") || t.contains("ollama") || t.contains("openai") || t.contains("minimax") || t.contains("genai") || t.contains("anthropic") || t.contains("vertex") || t.contains("bedrock")) return "Models & Providers";
        // Check for structured converter words
        if (t.contains("converter") || t.contains("schema") || t.contains("output") || t.contains("structured")) return "Structured Output";
        // Check for observability/span words
        if (t.contains("observability") || t.contains("metric") || t.contains("span") || t.contains("trace") || t.contains("instrument")) return "Observability";
        // Check for database store words
        if (t.contains("database") || t.contains("db") || t.contains("cosmos") || t.contains("cassandra") || t.contains("pgvector") || t.contains("store") || t.contains("vectordb")) return "Vector Databases";
        // Check for json jackson mapping helper words
        if (t.contains("json") || t.contains("jackson") || t.contains("helper")) return "JSON Utilities";
        // Default category fallback string
        return "General";
    }
}