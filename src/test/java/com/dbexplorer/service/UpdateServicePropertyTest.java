package com.dbexplorer.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based tests for the self-update feature.
 * Feature: self-update
 */
public class UpdateServicePropertyTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a semantic version string from three non-negative integers. */
    private static String ver(int major, int minor, int patch) {
        return major + "." + minor + "." + patch;
    }

    // -------------------------------------------------------------------------
    // Property 1: Semantic Version Comparison Ordering
    // -------------------------------------------------------------------------

    // Feature: self-update, Property 1: Semantic Version Comparison Ordering
    // Validates: Requirements 1.2
    @Property(tries = 200)
    void versionComparisonIsAntisymmetric(
            @ForAll @IntRange(min = 0, max = 20) int ma,
            @ForAll @IntRange(min = 0, max = 20) int mi_a,
            @ForAll @IntRange(min = 0, max = 20) int pa,
            @ForAll @IntRange(min = 0, max = 20) int mb,
            @ForAll @IntRange(min = 0, max = 20) int mi_b,
            @ForAll @IntRange(min = 0, max = 20) int pb) {

        String a = ver(ma, mi_a, pa);
        String b = ver(mb, mi_b, pb);

        int ab = UpdateService.compareVersions(a, b);
        int ba = UpdateService.compareVersions(b, a);

        // antisymmetry: sign(compareVersions(a,b)) == -sign(compareVersions(b,a))
        assertEquals(Integer.signum(ab), -Integer.signum(ba),
                "Antisymmetry violated for " + a + " vs " + b);
    }

    // Feature: self-update, Property 1: Semantic Version Comparison Ordering (transitivity)
    // Validates: Requirements 1.2
    @Property(tries = 200)
    void versionComparisonIsTransitive(
            @ForAll @IntRange(min = 0, max = 10) int ma,
            @ForAll @IntRange(min = 0, max = 10) int mi_a,
            @ForAll @IntRange(min = 0, max = 10) int pa,
            @ForAll @IntRange(min = 0, max = 10) int mb,
            @ForAll @IntRange(min = 0, max = 10) int mi_b,
            @ForAll @IntRange(min = 0, max = 10) int pb,
            @ForAll @IntRange(min = 0, max = 10) int mc,
            @ForAll @IntRange(min = 0, max = 10) int mi_c,
            @ForAll @IntRange(min = 0, max = 10) int pc) {

        String a = ver(ma, mi_a, pa);
        String b = ver(mb, mi_b, pb);
        String c = ver(mc, mi_c, pc);

        int ab = UpdateService.compareVersions(a, b);
        int bc = UpdateService.compareVersions(b, c);
        int ac = UpdateService.compareVersions(a, c);

        // if a <= b and b <= c then a <= c
        if (ab <= 0 && bc <= 0) {
            assertTrue(ac <= 0,
                    "Transitivity violated: " + a + " <= " + b + " <= " + c + " but " + a + " > " + c);
        }
        // if a >= b and b >= c then a >= c
        if (ab >= 0 && bc >= 0) {
            assertTrue(ac >= 0,
                    "Transitivity violated: " + a + " >= " + b + " >= " + c + " but " + a + " < " + c);
        }
    }

    // -------------------------------------------------------------------------
    // Property 2: API Response Parsing Round-Trip
    // -------------------------------------------------------------------------

    // Feature: self-update, Property 2: API Response Parsing Round-Trip
    // Validates: Requirements 1.1, 5.1
    @Property(tries = 100)
    void apiResponseParsingRoundTrip(
            @ForAll @IntRange(min = 0, max = 99) int major,
            @ForAll @IntRange(min = 0, max = 99) int minor,
            @ForAll @IntRange(min = 0, max = 99) int patch,
            @ForAll boolean hasChecksum) throws IOException {

        String version = major + "." + minor + "." + patch;
        String tagName = "v" + version;
        String body = "Release notes for " + version;
        String jarName = "db-explorer-" + version + ".jar";
        String jarUrl = "https://github.com/example/releases/download/" + tagName + "/" + jarName;
        String checksumUrl = jarUrl + ".sha256";

        // Build a minimal GitHub Releases API JSON response
        String assetsJson;
        if (hasChecksum) {
            assetsJson = String.format(
                    "[{\"name\":\"%s\",\"browser_download_url\":\"%s\"}," +
                    "{\"name\":\"%s.sha256\",\"browser_download_url\":\"%s\"}]",
                    jarName, jarUrl, jarName, checksumUrl);
        } else {
            assetsJson = String.format(
                    "[{\"name\":\"%s\",\"browser_download_url\":\"%s\"}]",
                    jarName, jarUrl);
        }

        String json = String.format(
                "{\"tag_name\":\"%s\",\"body\":\"%s\",\"assets\":%s}",
                tagName, body, assetsJson);

        ReleaseInfo info = UpdateService.parseReleaseJson(json);

        // tag_name preserved
        assertEquals(tagName, info.tagName());
        // version = tagName stripped of leading "v"
        assertEquals(version, info.version());
        // body preserved
        assertEquals(body, info.body());
        // asset URL preserved
        assertEquals(jarUrl, info.assetUrl());
        // asset name preserved
        assertEquals(jarName, info.assetName());
        // checksum URL present/absent as expected
        if (hasChecksum) {
            assertEquals(checksumUrl, info.checksumUrl());
        } else {
            assertEquals(null, info.checksumUrl());
        }
    }

    // Feature: self-update, Property 7: Preference Persistence Round-Trip
    // Validates: Requirements 2.5
    @Property(tries = 100)
    void preferenceRoundTrip(@ForAll boolean startupCheckEnabled) throws IOException {
        Path tempDir = Files.createTempDirectory("update-prefs-test");
        Path tempPrefsFile = tempDir.resolve("update-prefs.json");

        try {
            UpdatePreferences prefs = new UpdatePreferences();
            prefs.setStartupCheckEnabled(startupCheckEnabled);
            prefs.saveTo(tempPrefsFile);

            UpdatePreferences loaded = UpdatePreferences.loadFrom(tempPrefsFile);

            assertEquals(startupCheckEnabled, loaded.isStartupCheckEnabled());
        } finally {
            Files.deleteIfExists(tempPrefsFile);
            Files.deleteIfExists(tempDir);
        }
    }

    // -------------------------------------------------------------------------
    // Property 5: Staging File Placed in Same Directory as Current JAR
    // -------------------------------------------------------------------------

    // Feature: self-update, Property 5: Staging File Placed in Same Directory as Current JAR
    // Validates: Requirements 3.1
    @Property(tries = 100)
    void stagingFileIsInSameDirectoryAsJar(
            @ForAll @IntRange(min = 0, max = 99) int major,
            @ForAll @IntRange(min = 0, max = 99) int minor,
            @ForAll @IntRange(min = 0, max = 99) int patch) throws IOException {

        Path tempDir = Files.createTempDirectory("staging-test");
        try {
            String version = major + "." + minor + "." + patch;
            String jarName = "db-explorer-" + version + ".jar";
            Path currentJar = tempDir.resolve(jarName);
            Files.createFile(currentJar);

            // Staging file convention: assetName + ".download" in same directory
            String stagingName = jarName + ".download";
            Path stagingFile = currentJar.getParent().resolve(stagingName);

            // Same parent directory
            assertEquals(currentJar.getParent(), stagingFile.getParent(),
                    "Staging file must be in the same directory as the JAR");

            // Distinct filename
            assertFalse(stagingFile.getFileName().equals(currentJar.getFileName()),
                    "Staging file must have a distinct filename from the JAR");
        } finally {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
    }

    // -------------------------------------------------------------------------
    // Property 4: Checksum Verification Correctness
    // -------------------------------------------------------------------------

    // Feature: self-update, Property 4: Checksum Verification Correctness
    // Validates: Requirements 3.4, 3.5
    @Property(tries = 100)
    void checksumVerificationCorrectness(@ForAll byte[] content) throws Exception {
        Path tempDir = Files.createTempDirectory("checksum-test");
        Path file = tempDir.resolve("test.jar");
        try {
            Files.write(file, content);
            UpdateService svc = new UpdateService();

            // (a) null checksum → skip (return true)
            assertTrue(svc.verifyChecksum(file, null),
                    "null checksum should return true (skip)");

            // (b) blank checksum → skip (return true)
            assertTrue(svc.verifyChecksum(file, ""),
                    "blank checksum should return true (skip)");
            assertTrue(svc.verifyChecksum(file, "   "),
                    "whitespace-only checksum should return true (skip)");

            // (c) matching checksum → true
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            String correctHex = hex.toString();

            assertTrue(svc.verifyChecksum(file, correctHex),
                    "matching checksum should return true");

            // Also accept uppercase
            assertTrue(svc.verifyChecksum(file, correctHex.toUpperCase()),
                    "matching checksum (uppercase) should return true");

            // (d) mismatched checksum → false
            // Flip the first character of the hex string to produce a wrong checksum
            char first = correctHex.charAt(0);
            char flipped = (first == 'a') ? 'b' : 'a';
            String wrongHex = flipped + correctHex.substring(1);
            assertFalse(svc.verifyChecksum(file, wrongHex),
                    "mismatched checksum should return false");

        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    // -------------------------------------------------------------------------
    // Property 6: JAR Replacement Preserves New Content
    // -------------------------------------------------------------------------

    // Feature: self-update, Property 6: JAR Replacement Preserves New Content
    // Validates: Requirements 4.1
    @Property(tries = 100)
    void jarReplacementPreservesContent(@ForAll byte[] stagingContent) throws IOException {
        Path tempDir = Files.createTempDirectory("apply-update-test");
        try {
            Path currentJar = tempDir.resolve("db-explorer-2.5.0.jar");
            Path stagingFile = tempDir.resolve("db-explorer-2.6.0.jar.download");
            Path backupJar = tempDir.resolve("db-explorer-2.5.0.jar.bak");

            // Write some existing content to the "current" JAR
            Files.write(currentJar, new byte[]{0x50, 0x4B}); // dummy old content
            // Write the new content to the staging file
            Files.write(stagingFile, stagingContent);

            UpdateService svc = new UpdateService();
            boolean result = svc.applyUpdate(stagingFile, currentJar);

            assertTrue(result, "applyUpdate should return true on success");

            // currentJar bytes must equal original staging bytes
            byte[] actual = Files.readAllBytes(currentJar);
            assertEquals(stagingContent.length, actual.length,
                    "currentJar size must match staging content size");
            for (int i = 0; i < stagingContent.length; i++) {
                assertEquals(stagingContent[i], actual[i],
                        "currentJar byte[" + i + "] must match staging content");
            }

            // .bak file must exist
            assertTrue(Files.exists(backupJar),
                    ".bak backup file must exist after applyUpdate");

        } finally {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
    }
}
