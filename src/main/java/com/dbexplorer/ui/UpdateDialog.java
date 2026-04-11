package com.dbexplorer.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import com.dbexplorer.service.ReleaseInfo;
import com.dbexplorer.service.UpdatePreferences;
import com.dbexplorer.service.UpdateService;

/**
 * Modal dialog that drives the self-update flow.
 *
 * States (CardLayout panels):
 *   CHECKING        — spinner + "Checking for updates…"
 *   UP_TO_DATE      — "DB Explorer is up to date (v X.Y.Z)"
 *   UPDATE_AVAILABLE — version label, release notes, Download button, startup-check checkbox
 *   DOWNLOADING     — progress bar, Cancel button
 *   READY_TO_RESTART — "Update downloaded. Restart to apply." + Restart Now button
 *   ERROR           — error message text
 */
public class UpdateDialog extends JDialog {

    // Card names
    private static final String CARD_CHECKING         = "CHECKING";
    private static final String CARD_UP_TO_DATE       = "UP_TO_DATE";
    private static final String CARD_UPDATE_AVAILABLE = "UPDATE_AVAILABLE";
    private static final String CARD_DOWNLOADING      = "DOWNLOADING";
    private static final String CARD_READY_TO_RESTART = "READY_TO_RESTART";
    private static final String CARD_ERROR            = "ERROR";

    private final AtomicBoolean updateInProgress;
    private final UpdateService updateService = new UpdateService();

    // CardLayout host
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);

    // UP_TO_DATE
    private final JLabel upToDateLabel = new JLabel();

    // UPDATE_AVAILABLE
    private final JLabel availableVersionLabel = new JLabel();
    private final JTextArea releaseNotesArea   = new JTextArea(8, 40);
    private final JCheckBox startupCheckBox    = new JCheckBox("Check for updates on startup");
    private final JButton   downloadBtn        = new JButton("Download Update");

    // DOWNLOADING
    private final JProgressBar progressBar     = new JProgressBar(0, 100);
    private final JLabel       progressLabel   = new JLabel("Downloading…");
    private final JButton      cancelBtn       = new JButton("Cancel");
    private final AtomicBoolean downloadCancelled = new AtomicBoolean(false);

    // Tracks whether the script-based deferred update path was used
    private volatile boolean scriptBasedUpdate = false;

    // READY_TO_RESTART
    private final JButton restartBtn = new JButton("Restart Now");

    // ERROR
    private final JTextArea errorArea = new JTextArea(4, 40);

    // Close button (shown in all terminal states)
    private final JButton closeBtn = new JButton("Close");

    // Resolved at download time
    private ReleaseInfo pendingRelease;
    private Path        currentJarPath;   // may be null if undetermined

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public UpdateDialog(Frame owner, AtomicBoolean updateInProgress) {
        super(owner, "Check for Updates", true);
        this.updateInProgress = updateInProgress;
        setResizable(false);
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void initUI() {
        cardPanel.add(buildCheckingPanel(),        CARD_CHECKING);
        cardPanel.add(buildUpToDatePanel(),        CARD_UP_TO_DATE);
        cardPanel.add(buildUpdateAvailablePanel(), CARD_UPDATE_AVAILABLE);
        cardPanel.add(buildDownloadingPanel(),     CARD_DOWNLOADING);
        cardPanel.add(buildReadyToRestartPanel(),  CARD_READY_TO_RESTART);
        cardPanel.add(buildErrorPanel(),           CARD_ERROR);

        cardPanel.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));

        // Bottom button row — Close is always available
        closeBtn.addActionListener(e -> onClose());
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.add(closeBtn);

        setLayout(new BorderLayout());
        add(cardPanel, BorderLayout.CENTER);
        add(btnRow, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(closeBtn);

        // Kick off the version check as soon as the dialog is shown
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                startVersionCheck();
            }
        });
    }

    private JPanel buildCheckingPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        JLabel lbl = new JLabel("Checking for updates…");
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 13f));

        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setAlignmentX(Component.CENTER_ALIGNMENT);
        spinner.setMaximumSize(new Dimension(260, 16));

        p.add(Box.createVerticalGlue());
        p.add(lbl);
        p.add(Box.createVerticalStrut(12));
        p.add(spinner);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildUpToDatePanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        upToDateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        upToDateLabel.setFont(upToDateLabel.getFont().deriveFont(Font.PLAIN, 13f));
        upToDateLabel.setHorizontalAlignment(SwingConstants.CENTER);

        p.add(Box.createVerticalGlue());
        p.add(upToDateLabel);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildUpdateAvailablePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        // Version label
        availableVersionLabel.setFont(availableVersionLabel.getFont().deriveFont(Font.BOLD, 13f));
        gbc.gridy = 0;
        p.add(availableVersionLabel, gbc);

        // Release notes label
        JLabel notesLbl = new JLabel("Release Notes:");
        notesLbl.setFont(notesLbl.getFont().deriveFont(Font.PLAIN, 12f));
        gbc.gridy = 1;
        p.add(notesLbl, gbc);

        // Scrollable release notes area — min 400 chars visible (Req 5.2)
        releaseNotesArea.setEditable(false);
        releaseNotesArea.setLineWrap(true);
        releaseNotesArea.setWrapStyleWord(true);
        releaseNotesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(releaseNotesArea);
        scroll.setPreferredSize(new Dimension(420, 160));
        scroll.setMinimumSize(new Dimension(420, 100));
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        p.add(scroll, gbc);

        // Startup check checkbox (Req 2.4)
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;
        startupCheckBox.setFont(startupCheckBox.getFont().deriveFont(Font.PLAIN, 12f));
        // Load persisted preference
        UpdatePreferences prefs = UpdatePreferences.load();
        startupCheckBox.setSelected(prefs.isStartupCheckEnabled());
        startupCheckBox.addActionListener(e -> {
            UpdatePreferences p2 = UpdatePreferences.load();
            p2.setStartupCheckEnabled(startupCheckBox.isSelected());
            p2.save();
        });
        p.add(startupCheckBox, gbc);

        // Download button
        downloadBtn.addActionListener(e -> startDownload());
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        btnRow.add(downloadBtn);
        gbc.gridy = 4;
        p.add(btnRow, gbc);

        return p;
    }

    private JPanel buildDownloadingPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        progressLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.PLAIN, 12f));

        progressBar.setStringPainted(true);
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        progressBar.setMaximumSize(new Dimension(380, 22));

        cancelBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancelBtn.addActionListener(e -> {
            downloadCancelled.set(true);
            cancelBtn.setEnabled(false);
            progressLabel.setText("Cancelling…");
        });

        p.add(Box.createVerticalGlue());
        p.add(progressLabel);
        p.add(Box.createVerticalStrut(10));
        p.add(progressBar);
        p.add(Box.createVerticalStrut(12));
        p.add(cancelBtn);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildReadyToRestartPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        JLabel lbl = new JLabel("Update downloaded. Restart to apply.");
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 13f));

        restartBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        restartBtn.addActionListener(e -> onRestartNow());

        p.add(Box.createVerticalGlue());
        p.add(lbl);
        p.add(Box.createVerticalStrut(14));
        p.add(restartBtn);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildErrorPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));

        errorArea.setEditable(false);
        errorArea.setLineWrap(true);
        errorArea.setWrapStyleWord(true);
        errorArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        errorArea.setOpaque(false);
        JScrollPane scroll = new JScrollPane(errorArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // -------------------------------------------------------------------------
    // State transitions
    // -------------------------------------------------------------------------

    private void showCard(String card) {
        cardLayout.show(cardPanel, card);
    }

    private void switchToUpToDate(String currentVersion) {
        upToDateLabel.setText("DB Explorer is up to date (v " + currentVersion + ")");
        showCard(CARD_UP_TO_DATE);
        clearUpdateInProgress();
    }

    private void switchToUpdateAvailable(ReleaseInfo info) {
        pendingRelease = info;
        availableVersionLabel.setText("A new version is available: v" + info.version());
        String notes = (info.body() == null || info.body().isBlank())
                ? "No release notes available for this version."
                : info.body();
        releaseNotesArea.setText(notes);
        releaseNotesArea.setCaretPosition(0);
        showCard(CARD_UPDATE_AVAILABLE);
        // updateInProgress stays true — user may start a download
    }

    private void switchToDownloading() {
        downloadCancelled.set(false);
        cancelBtn.setEnabled(true);
        progressBar.setValue(0);
        progressBar.setString("0%");
        progressLabel.setText("Downloading…");
        showCard(CARD_DOWNLOADING);
        // updateInProgress stays true
    }

    private void switchToReadyToRestart() {
        showCard(CARD_READY_TO_RESTART);
        clearUpdateInProgress();
    }

    private void switchToError(String message) {
        errorArea.setText(message);
        showCard(CARD_ERROR);
        clearUpdateInProgress();
    }

    private void clearUpdateInProgress() {
        updateInProgress.set(false);
    }

    // -------------------------------------------------------------------------
    // Version check worker (6.2)
    // -------------------------------------------------------------------------

    private void startVersionCheck() {
        updateInProgress.set(true);
        showCard(CARD_CHECKING);

        new SwingWorker<ReleaseInfo, Void>() {
            @Override
            protected ReleaseInfo doInBackground() throws Exception {
                return updateService.fetchLatestRelease();
            }

            @Override
            protected void done() {
                try {
                    ReleaseInfo info = get();
                    String currentVersion = readCurrentVersion();
                    if (UpdateService.compareVersions(info.version(), currentVersion) > 0) {
                        switchToUpdateAvailable(info);
                    } else {
                        switchToUpToDate(currentVersion);
                    }
                } catch (Exception ex) {
                    switchToError("Failed to check for updates:\n" + ex.getMessage());
                }
            }
        }.execute();
    }

    // -------------------------------------------------------------------------
    // Download worker (6.4)
    // -------------------------------------------------------------------------

    private void startDownload() {
        if (pendingRelease == null) return;

        // Resolve current JAR path
        Optional<Path> jarOpt = UpdateService.getCurrentJarPath();
        if (jarOpt.isEmpty()) {
            // Offer Save As fallback (Req 4.5)
            int choice = JOptionPane.showConfirmDialog(this,
                    "Cannot determine the running JAR path.\n" +
                    "Would you like to save the downloaded update to a custom location?",
                    "JAR Path Unknown", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new java.io.File(pendingRelease.assetName()));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path savePath = fc.getSelectedFile().toPath();
            startDownloadToPath(null, savePath, true);
            return;
        }

        currentJarPath = jarOpt.get();
        Path stagingPath = currentJarPath.getParent()
                .resolve(pendingRelease.assetName() + ".download");
        startDownloadToPath(currentJarPath, stagingPath, false);
    }

    /**
     * @param currentJar  null when doing a Save-As fallback
     * @param targetPath  staging path (or final save path for Save-As)
     * @param saveAsMode  true → skip applyUpdate, just save to targetPath
     */
    private void startDownloadToPath(Path currentJar, Path targetPath, boolean saveAsMode) {
        switchToDownloading();

        final long[] totalSizeHolder = {-1};

        new SwingWorker<Void, long[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                // We need content-length for the progress bar — use a wrapper
                // that first does a HEAD or reads Content-Length from the GET response.
                // UpdateService.downloadAsset streams the download; we track bytes here.
                updateService.downloadAsset(
                        pendingRelease.assetUrl(),
                        targetPath,
                        bytesReceived -> publish(new long[]{bytesReceived}),
                        downloadCancelled);
                return null;
            }

            @Override
            protected void process(java.util.List<long[]> chunks) {
                if (chunks.isEmpty()) return;
                long bytes = chunks.get(chunks.size() - 1)[0];
                // Indeterminate progress if we don't know total size
                if (totalSizeHolder[0] <= 0) {
                    progressBar.setIndeterminate(true);
                    progressLabel.setText(formatBytes(bytes) + " downloaded");
                } else {
                    progressBar.setIndeterminate(false);
                    int pct = (int) (bytes * 100L / totalSizeHolder[0]);
                    progressBar.setValue(pct);
                    progressBar.setString(pct + "%");
                    progressLabel.setText(formatBytes(bytes) + " / " + formatBytes(totalSizeHolder[0]));
                }
            }

            @Override
            protected void done() {
                if (downloadCancelled.get()) {
                    // Staging file already cleaned up by UpdateService
                    switchToError("Download cancelled.");
                    clearUpdateInProgress();
                    return;
                }
                try {
                    get(); // rethrow any exception from doInBackground
                } catch (Exception ex) {
                    switchToError("Download failed:\n" + ex.getMessage());
                    return;
                }

                if (saveAsMode) {
                    switchToError("File saved to:\n" + targetPath.toAbsolutePath());
                    clearUpdateInProgress();
                    return;
                }

                // Checksum verification (Req 3.4, 3.5)
                if (pendingRelease.checksumUrl() != null) {
                    try {
                        String expectedChecksum = fetchChecksumText(pendingRelease.checksumUrl());
                        if (!updateService.verifyChecksum(targetPath, expectedChecksum)) {
                            try { Files.deleteIfExists(targetPath); } catch (IOException ignored) {}
                            switchToError("Integrity check failed: the downloaded file's checksum does not match.\n" +
                                          "The staging file has been deleted. Please try again.");
                            return;
                        }
                    } catch (IOException ex) {
                        // Non-fatal: log and continue without checksum
                        System.err.println("Could not fetch checksum: " + ex.getMessage());
                    }
                }

                // Apply update (Req 4.1, 4.3)
                boolean applied = updateService.applyUpdate(targetPath, currentJar);
                if (applied) {
                    switchToReadyToRestart();
                } else {
                    // Direct move failed (likely Windows file lock) — try script-based deferred update
                    boolean scriptLaunched = updateService.applyUpdateViaScript(targetPath, currentJar);
                    if (scriptLaunched) {
                        scriptBasedUpdate = true;
                        switchToReadyToRestart();
                    } else {
                        switchToError("Failed to replace the running JAR.\n" +
                                      "The downloaded update is retained at:\n" +
                                      targetPath.toAbsolutePath() + "\n\n" +
                                      "You can manually replace the JAR with this file.");
                    }
                }
            }
        }.execute();
    }

    // -------------------------------------------------------------------------
    // Restart (6.5)
    // -------------------------------------------------------------------------

    private void onRestartNow() {
        if (scriptBasedUpdate) {
            // Script is already waiting for this process to exit — just exit
            System.exit(0);
        } else if (currentJarPath != null) {
            updateService.restartWithJar(currentJarPath);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Cannot determine JAR path for restart.\nPlease restart the application manually.",
                    "Restart Required", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        }
    }

    // -------------------------------------------------------------------------
    // Close / cancel
    // -------------------------------------------------------------------------

    private void onClose() {
        if (downloadCancelled.get() || isDownloading()) {
            downloadCancelled.set(true);
        }
        clearUpdateInProgress();
        dispose();
    }

    private boolean isDownloading() {
        // Check if the downloading card is currently visible
        for (Component c : cardPanel.getComponents()) {
            if (c.isVisible() && cardPanel.getLayout() instanceof CardLayout) {
                // We track state via the cancel button being enabled
                return cancelBtn.isEnabled() && cancelBtn.isShowing();
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Reads app.version from app.properties (same pattern as AboutDialog). */
    private static String readCurrentVersion() {
        try (InputStream is = UpdateDialog.class.getResourceAsStream("/app.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String v = props.getProperty("app.version");
                if (v != null && !v.isBlank()) return v.trim();
            }
        } catch (Exception ignored) {}
        return "0.0.0";
    }

    /** Fetches the raw text content of a checksum URL. */
    private static String fetchChecksumText(String url) throws IOException {
        java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        conn.setRequestProperty("User-Agent", "db-explorer-updater");
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes()).trim();
        } finally {
            conn.disconnect();
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
