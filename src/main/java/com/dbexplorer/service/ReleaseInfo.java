package com.dbexplorer.service;

/**
 * Immutable value object parsed from the GitHub Releases API response.
 */
public record ReleaseInfo(
    String tagName,      // e.g. "v2.6.0"
    String version,      // tagName stripped of leading "v"
    String body,         // release notes markdown
    String assetUrl,     // direct download URL for the JAR asset
    String assetName,    // filename of the JAR asset
    String checksumUrl   // URL of the .sha256 file, or null if absent
) {}
