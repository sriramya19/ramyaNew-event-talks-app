package com.example.releasepulse.model;

/**
 * Data record representing a structured release note card.
 */
public record ReleaseUpdate(
    String id,
    String version,
    String category,
    String subCategory,
    String title,
    String htmlContent,
    String link
) {}
