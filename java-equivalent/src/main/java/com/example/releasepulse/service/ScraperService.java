package com.example.releasepulse.service;

import com.example.releasepulse.model.ReleaseUpdate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

@Service
public class ScraperService {
    private static final Logger logger = Logger.getLogger(ScraperService.class.getName());
    private static final String UPGRADE_NOTES_URL = "https://docs.spring.io/spring-ai/reference/upgrade-notes.html";

    // In-memory cache variables
    private List<ReleaseUpdate> cachedUpdates = null;
    private String lastUpdated = null;

    public synchronized Map<String, Object> getReleases(boolean forceRefresh) throws IOException {
        if (cachedUpdates != null && !forceRefresh) {
            logger.info("Returning cached updates");
            return buildResponse(cachedUpdates, true);
        }

        logger.info("Fetching and parsing live release notes...");
        List<ReleaseUpdate> updates = fetchAndParse();
        cachedUpdates = updates;
        lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return buildResponse(updates, false);
    }

    public synchronized Map<String, Object> getCachedUpdatesIfAvailable() {
        if (cachedUpdates != null) {
            return buildResponse(cachedUpdates, true);
        }
        return null;
    }

    private Map<String, Object> buildResponse(List<ReleaseUpdate> updates, boolean cached) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("releases", updates);
        response.put("last_updated", lastUpdated);
        response.put("cached", cached);
        return response;
    }

    private List<ReleaseUpdate> fetchAndParse() throws IOException {
        // Fetch and parse the HTML documentation using JSoup
        Document doc = Jsoup.connect(UPGRADE_NOTES_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(20000)
                .get();

        List<ReleaseUpdate> updates = new ArrayList<>();
        Element mainDoc = doc.selectFirst("article.doc");
        if (mainDoc == null) {
            mainDoc = doc.body();
            if (mainDoc == null) return updates;
        }

        Elements sect1Divs = mainDoc.select("div.sect1");
        for (Element sect1 : sect1Divs) {
            Element h2 = sect1.selectFirst("h2");
            if (h2 == null) continue;

            String versionText = h2.text().replace("Upgrading to ", "").trim();

            Elements sect2Divs = sect1.select("div.sect2");
            for (Element sect2 : sect2Divs) {
                Element h3 = sect2.selectFirst("h3");
                if (h3 == null) continue;

                String categoryText = h3.text().trim();
                String categoryId = h3.id();

                Elements sect3Divs = sect2.select("div.sect3");
                boolean hasDescriptiveSubheadings = false;
                List<String> genericWords = Arrays.asList("impact", "migration", "why", "detail");

                for (Element sect3 : sect3Divs) {
                    Element h4 = sect3.selectFirst("h4");
                    if (h4 != null) {
                        String h4Text = h4.text().toLowerCase().trim();
                        boolean isGeneric = false;
                        for (String gen : genericWords) {
                            if (h4Text.contains(gen)) {
                                isGeneric = true;
                                break;
                            }
                        }
                        if (!isGeneric) {
                            hasDescriptiveSubheadings = true;
                            break;
                        }
                    }
                }

                if (!sect3Divs.isEmpty() && hasDescriptiveSubheadings) {
                    for (Element sect3 : sect3Divs) {
                        Element h4 = sect3.selectFirst("h4");
                        if (h4 == null) continue;

                        String updateTitle = h4.text().trim();
                        String updateId = h4.id();

                        // Accumulate HTML content inside sect3 excluding the h4 header
                        StringBuilder contentHtml = new StringBuilder();
                        for (Element child : sect3.children()) {
                            if (child.tagName().equals("h4")) continue;
                            contentHtml.append(child.outerHtml());
                        }

                        String cat = determineCategory(updateTitle, categoryText);
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
                } else {
                    StringBuilder contentHtml = new StringBuilder();
                    for (Element child : sect2.children()) {
                        if (child.tagName().equals("h3")) continue;
                        contentHtml.append(child.outerHtml());
                    }

                    String cat = determineCategory(categoryText, "");
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
        return updates;
    }

    private String determineCategory(String title, String parentCategory) {
        String t = (title + " " + parentCategory).toLowerCase();
        if (t.contains("advisor") || t.contains("vector-store-advisor")) return "Advisors";
        if (t.contains("tool") || t.contains("mcp") || t.contains("callback") || t.contains("functioncall")) return "Tool Calling & MCP";
        if (t.contains("memory") || t.contains("chatmemory") || t.contains("history")) return "Chat Memory";
        if (t.contains("model") || t.contains("ollama") || t.contains("openai") || t.contains("minimax") || t.contains("genai") || t.contains("anthropic") || t.contains("vertex") || t.contains("bedrock")) return "Models & Providers";
        if (t.contains("converter") || t.contains("schema") || t.contains("output") || t.contains("structured")) return "Structured Output";
        if (t.contains("observability") || t.contains("metric") || t.contains("span") || t.contains("trace") || t.contains("instrument")) return "Observability";
        if (t.contains("database") || t.contains("db") || t.contains("cosmos") || t.contains("cassandra") || t.contains("pgvector") || t.contains("store") || t.contains("vectordb")) return "Vector Databases";
        if (t.contains("json") || t.contains("jackson") || t.contains("helper")) return "JSON Utilities";
        return "General";
    }
}
