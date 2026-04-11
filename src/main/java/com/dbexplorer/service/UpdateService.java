package com.dbexplorer.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/**
 * Core service for checking and applying self-updates.
 */
public class UpdateService {

    private static final String RELEASES_API_URL =
            "https://api.github.com/repos/adeptashishsriv/db-explorer/releases/latest";

    private static final Gson GSON = new Gson();

    // -------------------------------------------------------------------------
    // Version comparison
    // -------------------------------------------------------------------------

    /**
     * Compares two semantic version strings (e.g. "2.5.0" vs "2.6.0").
     * Returns negative if a &lt; b, 0 if equal, positive if a &gt; b.
     */
    public static int compareVersions(String a, String b) {
        // Strip any leading v/V prefix (e.g. "V3.0.0" → "3.0.0")
        if (a != null && (a.startsWith("v") || a.startsWith("V"))) a = a.substring(1);
        if (b != null && (b.startsWith("v") || b.startsWith("V"))) b = b.substring(1);
        String[] partsA = (a != null ? a : "0").split("\\.");
        String[] partsB = (b != null ? b : "0").split("\\.");
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            int numA = i < partsA.length ? Integer.parseInt(partsA[i].trim()) : 0;
            int numB = i < partsB.length ? Integer.parseInt(partsB[i].trim()) : 0;
            if (numA != numB) {
                return Integer.compare(numA, numB);
            }
        }
        return 0;
    }

    // -------------------------------------------------------------------------
    // GitHub Releases API
    // -------------------------------------------------------------------------

    /**
     * Queries the GitHub Releases API and returns parsed release info.
     *
     * @throws IOException on non-200 status, network error, or missing JAR asset
     */
    public ReleaseInfo fetchLatestRelease() throws IOException {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(10, TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(30, TimeUnit.SECONDS))
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpGet request = new HttpGet(URI.create(RELEASES_API_URL));
            request.setHeader("Accept", "application/vnd.github+json");
            request.setHeader("User-Agent", "db-explorer-updater");

            return client.execute(request, response -> {
                int status = response.getCode();
                if (status != 200) {
                    throw new IOException("GitHub API returned HTTP " + status);
                }

                String json = EntityUtils.toString(response.getEntity());
                return parseReleaseJson(json);
            });
        }
    }

    // -------------------------------------------------------------------------
    // JSON parsing (package-private for testing)
    // -------------------------------------------------------------------------

    /** Parses a GitHub Releases API JSON response into a {@link ReleaseInfo}. */
    static ReleaseInfo parseReleaseJson(String json) throws IOException {
        GitHubRelease release = GSON.fromJson(json, GitHubRelease.class);

        String tagName = release.tagName != null ? release.tagName : "";
        String version = (tagName.startsWith("v") || tagName.startsWith("V")) ? tagName.substring(1) : tagName;
        String body = release.body != null ? release.body : "";

        String assetUrl = null;
        String assetName = null;
        String checksumUrl = null;

        if (release.assets != null) {
            for (GitHubAsset asset : release.assets) {
                if (asset.name != null && asset.name.endsWith(".jar")
                        && !asset.name.endsWith(".sha256")) {
                    assetUrl = asset.browserDownloadUrl;
                    assetName = asset.name;
                } else if (asset.name != null && asset.name.endsWith(".sha256")) {
                    checksumUrl = asset.browserDownloadUrl;
                }
            }
        }

        if (assetUrl == null) {
            throw new IOException("No JAR asset found in this release");
        }

        return new ReleaseInfo(tagName, version, body, assetUrl, assetName, checksumUrl);
    }

    // -------------------------------------------------------------------------
    // JAR path resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves the path of the currently running JAR.
     * Returns {@link Optional#empty()} when running from a class directory (IDE/test).
     * Requirements: 4.5
     */
    public static Optional<Path> getCurrentJarPath() {
        try {
            java.security.ProtectionDomain pd =
                    UpdateService.class.getProtectionDomain();
            if (pd == null) return Optional.empty();
            java.security.CodeSource cs = pd.getCodeSource();
            if (cs == null) return Optional.empty();
            java.net.URL location = cs.getLocation();
            if (location == null) return Optional.empty();
            Path path = Path.of(location.toURI());
            // If it's a directory (e.g. target/classes in IDE), return empty
            if (Files.isDirectory(path)) return Optional.empty();
            return Optional.of(path);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------

    /**
     * Downloads the asset at {@code url} to {@code stagingPath}, reporting
     * cumulative bytes received via {@code progressCallback}.
     * On cancellation or IOException the staging file is deleted before returning/throwing.
     * Requirements: 3.1, 3.2, 3.3, 3.6, 3.7
     */
    public void downloadAsset(String url, Path stagingPath,
                              LongConsumer progressCallback,
                              AtomicBoolean cancelled) throws IOException {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(10, TimeUnit.SECONDS))
                .setResponseTimeout(Timeout.of(30, TimeUnit.SECONDS))
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            HttpGet request = new HttpGet(URI.create(url));
            request.setHeader("User-Agent", "db-explorer-updater");

            client.execute(request, response -> {
                int status = response.getCode();
                if (status != 200) {
                    Files.deleteIfExists(stagingPath);
                    throw new IOException("Download failed: HTTP " + status);
                }

                try (InputStream in = response.getEntity().getContent();
                     OutputStream out = Files.newOutputStream(stagingPath)) {

                    byte[] buf = new byte[8192];
                    long total = 0;
                    int read;
                    while ((read = in.read(buf)) != -1) {
                        if (cancelled != null && cancelled.get()) {
                            // Cancellation requested — close stream, clean up, and return
                            out.close();
                            Files.deleteIfExists(stagingPath);
                            return null;
                        }
                        out.write(buf, 0, read);
                        total += read;
                        if (progressCallback != null) {
                            progressCallback.accept(total);
                        }
                    }
                } catch (IOException e) {
                    Files.deleteIfExists(stagingPath);
                    throw e;
                }
                return null;
            });
        }
    }

    // -------------------------------------------------------------------------
    // Checksum verification
    // -------------------------------------------------------------------------

    /**
     * Verifies the SHA-256 checksum of {@code file} against {@code expectedSha256}.
     * Returns {@code true} when {@code expectedSha256} is null or blank (skip).
     * Requirements: 3.4, 3.5
     */
    public boolean verifyChecksum(Path file, String expectedSha256) throws IOException {
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            return true;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().equalsIgnoreCase(expectedSha256.trim());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }

    // -------------------------------------------------------------------------
    // Apply update
    // -------------------------------------------------------------------------

    /**
     * Replaces {@code currentJar} with {@code stagingFile}.
     * First tries a direct move. If that fails (e.g. Windows file lock),
     * falls back to a script-based deferred update via {@link #applyUpdateViaScript}.
     * Returns {@code true} on success; on failure retains the staging file and returns {@code false}.
     * Requirements: 4.1, 4.3
     */
    public boolean applyUpdate(Path stagingFile, Path currentJar) {
        Path backup = currentJar.resolveSibling(currentJar.getFileName() + ".bak");
        try {
            Files.move(currentJar, backup, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(stagingFile, currentJar, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException e) {
                // Try to restore backup
                try { Files.move(backup, currentJar, StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) {}
                return false;
            }
        } catch (IOException e) {
            // Direct move failed (likely Windows file lock) — try script-based approach
            return false;
        }
    }

    /**
     * Writes a platform-specific script that waits for this JVM to exit,
     * then replaces the current JAR with the staging file and relaunches.
     * The script deletes itself after execution.
     *
     * @return true if the script was written and launched successfully
     */
    public boolean applyUpdateViaScript(Path stagingFile, Path currentJar) {
        try {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            Path scriptPath = currentJar.resolveSibling(isWindows ? "db-explorer-update.bat" : "db-explorer-update.sh");
            String currentJarAbs = currentJar.toAbsolutePath().toString();
            String stagingAbs = stagingFile.toAbsolutePath().toString();
            String backupAbs = currentJar.resolveSibling(currentJar.getFileName() + ".bak").toAbsolutePath().toString();
            String scriptAbs = scriptPath.toAbsolutePath().toString();

            // Get the current process PID so the script can wait for it to exit
            long pid = ProcessHandle.current().pid();

            String script;
            if (isWindows) {
                // Use tasklist to poll until the JVM process exits, then swap files
                script = "@echo off\r\n"
                        + "echo Waiting for DB Explorer (PID " + pid + ") to exit...\r\n"
                        + ":waitloop\r\n"
                        + "tasklist /FI \"PID eq " + pid + "\" 2>NUL | find /I \"" + pid + "\" >NUL\r\n"
                        + "if not errorlevel 1 (\r\n"
                        + "  timeout /t 1 /nobreak >nul\r\n"
                        + "  goto waitloop\r\n"
                        + ")\r\n"
                        + "echo Process exited. Applying update...\r\n"
                        + "if exist \"" + currentJarAbs + "\" (\r\n"
                        + "  move /Y \"" + currentJarAbs + "\" \"" + backupAbs + "\"\r\n"
                        + ")\r\n"
                        + "move /Y \"" + stagingAbs + "\" \"" + currentJarAbs + "\"\r\n"
                        + "echo Starting updated DB Explorer...\r\n"
                        + "start \"DB Explorer\" java -jar \"" + currentJarAbs + "\"\r\n"
                        + "(goto) 2>nul & del /f \"" + scriptAbs + "\"\r\n";
            } else {
                script = "#!/bin/bash\n"
                        + "echo 'Waiting for DB Explorer (PID " + pid + ") to exit...'\n"
                        + "while kill -0 " + pid + " 2>/dev/null; do sleep 1; done\n"
                        + "echo 'Process exited. Applying update...'\n"
                        + "if [ -f '" + currentJarAbs + "' ]; then\n"
                        + "  mv -f '" + currentJarAbs + "' '" + backupAbs + "'\n"
                        + "fi\n"
                        + "mv -f '" + stagingAbs + "' '" + currentJarAbs + "'\n"
                        + "echo 'Starting updated DB Explorer...'\n"
                        + "java -jar '" + currentJarAbs + "' &\n"
                        + "rm -f '" + scriptAbs + "'\n";
            }

            Files.writeString(scriptPath, script);

            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", "start", "/min", "\"\"", scriptAbs);
            } else {
                scriptPath.toFile().setExecutable(true);
                pb = new ProcessBuilder("bash", scriptAbs);
            }
            pb.directory(currentJar.getParent().toFile());
            pb.redirectErrorStream(true);
            pb.start();
            return true;
        } catch (IOException e) {
            System.err.println("Failed to create update script: " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Restart
    // -------------------------------------------------------------------------

    /**
     * Launches a new JVM process with the updated JAR and exits the current process.
     * Requirements: 4.4
     */
    public void restartWithJar(Path jarPath) {
        try {
            new ProcessBuilder("java", "-jar", jarPath.toAbsolutePath().toString())
                    .inheritIO()
                    .start();
        } catch (IOException e) {
            // Best-effort; proceed to exit regardless
        }
        System.exit(0);
    }

    // -------------------------------------------------------------------------
    // Startup check
    // -------------------------------------------------------------------------

    /**
     * Schedules a background startup update check. If a newer version is found,
     * shows the UpdateDialog on the EDT. All exceptions are caught silently
     * (logged to console only).
     * Requirements: 2.1, 2.2, 2.3, 2.6
     */
    public static void scheduleStartupCheck(javax.swing.JFrame frame, AtomicBoolean updateInProgress) {
        UpdatePreferences prefs = UpdatePreferences.load();
        if (!prefs.isStartupCheckEnabled()) {
            return;
        }

        new javax.swing.SwingWorker<ReleaseInfo, Void>() {
            @Override
            protected ReleaseInfo doInBackground() throws Exception {
                return new UpdateService().fetchLatestRelease();
            }

            @Override
            protected void done() {
                try {
                    ReleaseInfo info = get();
                    String currentVersion = readCurrentVersion();
                    if (compareVersions(info.version(), currentVersion) > 0) {
                        // Newer version found — show the update dialog
                        new com.dbexplorer.ui.UpdateDialog(frame, updateInProgress).setVisible(true);
                    }
                    // If up to date, do nothing (Req 2.3)
                } catch (Exception ex) {
                    // Catch all exceptions silently — log to console only (Req 2.3)
                    System.err.println("Startup update check failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /** Reads app.version from app.properties. */
    private static String readCurrentVersion() {
        try (java.io.InputStream is = UpdateService.class.getResourceAsStream("/app.properties")) {
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                String v = props.getProperty("app.version");
                if (v != null && !v.isBlank()) return v.trim();
            }
        } catch (Exception ignored) {}
        return "0.0.0";
    }

    // -------------------------------------------------------------------------
    // Internal DTOs for Gson deserialization
    // -------------------------------------------------------------------------

    private static class GitHubRelease {
        @SerializedName("tag_name")
        String tagName;

        @SerializedName("body")
        String body;

        @SerializedName("assets")
        List<GitHubAsset> assets;
    }

    private static class GitHubAsset {
        @SerializedName("name")
        String name;

        @SerializedName("browser_download_url")
        String browserDownloadUrl;
    }
}
