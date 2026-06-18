package com.example.releasepulse.model;

/**
 * Data record representing a structured release note card.
 */
// Define an immutable data carrier Record representing parsed update card attributes
public record ReleaseUpdate(
    // Unique card text identifier matched with HTML anchor element IDs
    String id,
    // Version label representing the parent release notes section (e.g. 2.0.0)
    String version,
    // Calculated categorization category string mapped using title heuristics
    String category,
    // Original category section header string retrieved from DOM h3 elements
    String subCategory,
    // Extracted header title string representing this specific update card
    String title,
    // Inner HTML details body content parsed from nested DOM elements
    String htmlContent,
    // Direct URL link pointing to the official documentation anchor section
    String link
) {}