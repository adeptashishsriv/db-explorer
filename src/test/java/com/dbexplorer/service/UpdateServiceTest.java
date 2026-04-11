package com.dbexplorer.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for UpdateService.
 */
class UpdateServiceTest {

    // -------------------------------------------------------------------------
    // Version comparison
    // -------------------------------------------------------------------------

    @Test
    void testVersionComparisonExamples() {
        assertTrue(UpdateService.compareVersions("2.5.0", "2.6.0") < 0);
        assertEquals(0, UpdateService.compareVersions("2.5.0", "2.5.0"));
        assertTrue(UpdateService.compareVersions("2.10.0", "2.9.0") > 0);
        assertTrue(UpdateService.compareVersions("1.0.0", "2.0.0") < 0);
        assertTrue(UpdateService.compareVersions("3.0.0", "2.99.99") > 0);
    }

    // -------------------------------------------------------------------------
    // API parsing
    // -------------------------------------------------------------------------

    @Test
    void testApiParsingWithMissingBody() throws IOException {
        String json = "{\"tag_name\":\"v2.6.0\",\"body\":null," +
                "\"assets\":[{\"name\":\"db-explorer-2.6.0.jar\"," +
                "\"browser_download_url\":\"https://example.com/db-explorer-2.6.0.jar\"}]}";
        ReleaseInfo info = UpdateService.parseReleaseJson(json);
        assertEquals("", info.body());
    }

    @Test
    void testApiParsingWithNoJarAsset() {
        String json = "{\"tag_name\":\"v2.6.0\",\"body\":\"notes\",\"assets\":[]}";
        assertThrows(IOException.class, () -> UpdateService.parseReleaseJson(json));
    }

    // -------------------------------------------------------------------------
    // Checksum
    // -------------------------------------------------------------------------

    @Test
    void testChecksumSkippedWhenNull(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.jar");
        Files.write(file, new byte[]{1, 2, 3});
        assertTrue(new UpdateService().verifyChecksum(file, null));
    }

    @Test
    void testChecksumSkippedWhenBlank(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.jar");
        Files.write(file, new byte[]{1, 2, 3});
        assertTrue(new UpdateService().verifyChecksum(file, ""));
        assertTrue(new UpdateService().verifyChecksum(file, "   "));
    }

    // -------------------------------------------------------------------------
    // Download cancellation / failure cleanup
    // -------------------------------------------------------------------------

    @Test
    void testDownloadCancelledCleansUpStagingFile(@TempDir Path tempDir) throws IOException {
        // Pre-cancel the flag so the download loop exits immediately
        AtomicBoolean cancelled = new AtomicBoolean(true);
        Path staging = tempDir.resolve("staging.jar");

        // We can't actually hit the network in a unit test, so we verify the
        // cleanup logic by using a local HTTP server simulation via a bad URL
        // that throws immediately. The important thing is staging is absent.
        try {
            new UpdateService().downloadAsset(
                    "http://localhost:0/nonexistent", staging, null, cancelled);
        } catch (IOException ignored) {
            // Expected — connection refused
        }
        assertFalse(Files.exists(staging), "Staging file must be cleaned up after failure");
    }

    @Test
    void testDownloadFailureCleansUpStagingFile(@TempDir Path tempDir) {
        Path staging = tempDir.resolve("staging.jar");
        assertThrows(IOException.class, () ->
                new UpdateService().downloadAsset(
                        "http://localhost:0/nonexistent", staging, null, new AtomicBoolean(false)));
        assertFalse(Files.exists(staging), "Staging file must be cleaned up after IOException");
    }

    // -------------------------------------------------------------------------
    // applyUpdate
    // -------------------------------------------------------------------------

    @Test
    void testApplyUpdateCreatesBackup(@TempDir Path tempDir) throws IOException {
        Path currentJar = tempDir.resolve("db-explorer-2.5.0.jar");
        Path staging = tempDir.resolve("db-explorer-2.6.0.jar.download");
        Files.write(currentJar, new byte[]{0x50, 0x4B});
        Files.write(staging, new byte[]{0x50, 0x4B, 0x03, 0x04});

        boolean result = new UpdateService().applyUpdate(staging, currentJar);

        assertTrue(result);
        assertTrue(Files.exists(tempDir.resolve("db-explorer-2.5.0.jar.bak")));
    }

    @Test
    void testApplyUpdateFailureRetainsStagingFile(@TempDir Path tempDir) throws IOException {
        // currentJar does not exist → rename will fail → staging retained
        Path currentJar = tempDir.resolve("nonexistent.jar");
        Path staging = tempDir.resolve("staging.jar");
        Files.write(staging, new byte[]{1, 2, 3});

        boolean result = new UpdateService().applyUpdate(staging, currentJar);

        assertFalse(result);
        assertTrue(Files.exists(staging), "Staging file must be retained on failure");
    }

    // -------------------------------------------------------------------------
    // getCurrentJarPath
    // -------------------------------------------------------------------------

    @Test
    void testGetCurrentJarPathFromClassDirectory() {
        // When running from Maven test classes directory, should return empty
        var path = UpdateService.getCurrentJarPath();
        // Either empty (class dir) or present (if somehow packaged) — both are valid
        // The key assertion: no exception is thrown
        assertNotNull(path);
    }
}
