# Implementation Plan: Self-Update Feature

## Overview

Implement the self-update feature in Java 17 using the existing project stack (Gson, Apache HttpClient 5, Java NIO). The implementation follows the layered architecture: `UpdateService` and `UpdatePreferences` in the service layer, `UpdateDialog` in the UI layer, with integration points in `MainFrame`, `AboutDialog`, and `App`.

## Tasks

- [x] 1. Create `ReleaseInfo` record and `UpdatePreferences` class
  - [x] 1.1 Create `src/main/java/com/dbexplorer/service/ReleaseInfo.java`
    - Implement the `ReleaseInfo` record with fields: `tagName`, `version`, `body`, `assetUrl`, `assetName`, `checksumUrl`
    - _Requirements: 1.1, 5.1_
  - [x] 1.2 Create `src/main/java/com/dbexplorer/service/UpdatePreferences.java`
    - Implement `isStartupCheckEnabled()`, `setStartupCheckEnabled(boolean)`, `save()`, and static `load()`
    - Persist to `~/.dbexplorer/update-prefs.json` using Gson, defaulting `startupCheckEnabled` to `true`
    - _Requirements: 2.4, 2.5, 2.6_
  - [ ]* 1.3 Write property test for preference persistence round-trip
    - **Property 7: Preference Persistence Round-Trip**
    - **Validates: Requirements 2.5**
    - File: `src/test/java/com/dbexplorer/service/UpdateServicePropertyTest.java`

- [x] 2. Implement `UpdateService` — version comparison and API parsing
  - [x] 2.1 Create `src/main/java/com/dbexplorer/service/UpdateService.java` with `compareVersions(String a, String b)`
    - Parse each version string into integer arrays by splitting on `.`
    - Compare element-by-element; return negative/zero/positive
    - _Requirements: 1.2_
  - [ ]* 2.2 Write property test for version comparison ordering
    - **Property 1: Semantic Version Comparison Ordering**
    - **Validates: Requirements 1.2**
    - File: `src/test/java/com/dbexplorer/service/UpdateServicePropertyTest.java`
  - [x] 2.3 Implement `fetchLatestRelease()` in `UpdateService`
    - Use Apache HttpClient 5 with 10 s connect / 30 s read timeouts
    - Parse JSON response with Gson: extract `tag_name`, `body`, and the `.jar` asset URL; find `.sha256` asset if present
    - Strip leading `v` from `tag_name` to produce `version`
    - Throw `IOException` on non-200 status or missing JAR asset
    - _Requirements: 1.1, 1.5, 5.1, 5.3_
  - [ ]* 2.4 Write property test for API response parsing round-trip
    - **Property 2: API Response Parsing Round-Trip**
    - **Validates: Requirements 1.1, 5.1**
    - File: `src/test/java/com/dbexplorer/service/UpdateServicePropertyTest.java`
  - [ ]* 2.5 Write unit tests for `UpdateService` version comparison and parsing
    - `testVersionComparisonExamples()` — spot-check `2.5.0 < 2.6.0`, `2.5.0 == 2.5.0`, `2.10.0 > 2.9.0`
    - `testApiParsingWithMissingBody()` — null `body` falls back to empty string
    - `testApiParsingWithNoJarAsset()` — throws when no `.jar` asset present
    - File: `src/test/java/com/dbexplorer/service/UpdateServiceTest.java`
    - _Requirements: 1.1, 1.2, 5.3_

- [x] 3. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement `UpdateService` — download, checksum, and apply
  - [x] 4.1 Implement `getCurrentJarPath()` static method
    - Resolve the running JAR path from `ProtectionDomain`; return `Optional.empty()` when running from a class directory
    - _Requirements: 4.5_
  - [x] 4.2 Implement `downloadAsset(String url, Path stagingPath, LongConsumer progressCallback, AtomicBoolean cancelled)`
    - Stream download via HttpClient 5; invoke `progressCallback` with bytes received
    - On cancellation or `IOException`, delete the staging file before returning/throwing
    - _Requirements: 3.1, 3.2, 3.3, 3.6, 3.7_
  - [ ]* 4.3 Write property test for staging file location
    - **Property 5: Staging File Placed in Same Directory as Current JAR**
    - **Validates: Requirements 3.1**
    - File: `src/test/java/com/dbexplorer/service/UpdateServicePropertyTest.java`
  - [x] 4.4 Implement `verifyChecksum(Path file, String expectedSha256)`
    - Return `true` when `expectedSha256` is null or blank (skip)
    - Compute SHA-256 digest with `MessageDigest`; compare hex strings case-insensitively
    - _Requirements: 3.4, 3.5_
  - [ ]* 4.5 Write property test for checksum verification correctness
    - **Property 4: Checksum Verification Correctness**
    - **Validates: Requirements 3.4, 3.5**
    - File: `src/test/java/com/dbexplorer/service/UpdateServicePropertyTest.java`
  - [x] 4.6 Implement `applyUpdate(Path stagingFile, Path currentJar)`
    - Rename `currentJar` to `currentJar.bak` with `Files.move`; then move `stagingFile` to `currentJar`
    - Return `true` on success; on failure retain staging file and return `false`
    - _Requirements: 4.1, 4.3_
  - [ ]* 4.7 Write property test for JAR replacement content preservation
    - **Property 6: JAR Replacement Preserves New Content**
    - **Validates: Requirements 4.1**
    - File: `src/test/java/com/dbexplorer/service/UpdateServicePropertyTest.java`
  - [x] 4.8 Implement `restartWithJar(Path jarPath)`
    - Build a `ProcessBuilder` command: `java -jar <jarPath>`; start the process and call `System.exit(0)`
    - _Requirements: 4.4_
  - [ ]* 4.9 Write unit tests for download, checksum, and apply
    - `testChecksumSkippedWhenNull()`, `testChecksumSkippedWhenBlank()`
    - `testDownloadCancelledCleansUpStagingFile()`, `testDownloadFailureCleansUpStagingFile()`
    - `testApplyUpdateCreatesBackup()`, `testApplyUpdateFailureRetainsStagingFile()`
    - `testGetCurrentJarPathFromClassDirectory()`
    - File: `src/test/java/com/dbexplorer/service/UpdateServiceTest.java`
    - _Requirements: 3.3, 3.4, 3.5, 3.6, 4.1, 4.3, 4.5_

- [x] 5. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Create `UpdateDialog`
  - [x] 6.1 Create `src/main/java/com/dbexplorer/ui/UpdateDialog.java`
    - Modal `JDialog` accepting `Frame owner` and `AtomicBoolean updateInProgress`
    - Implement state machine with panels: Checking, UpToDate, UpdateAvailable, Downloading, ReadyToRestart, Error
    - _Requirements: 1.3, 1.4, 1.5, 1.6_
  - [x] 6.2 Implement the "Checking" and result states in `UpdateDialog`
    - On open, fire a `SwingWorker` calling `UpdateService.fetchLatestRelease()`
    - Use `compareVersions` to decide UpToDate vs UpdateAvailable state
    - Show version label, scrollable release notes area (min 400 chars visible), and Download button in UpdateAvailable state
    - Show fallback text "No release notes available for this version." when body is empty
    - _Requirements: 1.3, 1.4, 3.2, 5.1, 5.2, 5.3_
  - [ ]* 6.3 Write property test for dialog state determined by version comparison
    - **Property 3: Dialog State Determined by Version Comparison**
    - **Validates: Requirements 1.3, 1.4**
    - File: `src/test/java/com/dbexplorer/service/UpdateServicePropertyTest.java`
  - [x] 6.4 Implement download flow in `UpdateDialog`
    - "Download Update" button fires a `SwingWorker` calling `downloadAsset` with a progress callback that updates the `JProgressBar`
    - Fetch and verify checksum if `checksumUrl` is non-null; on mismatch show integrity error
    - On success, call `applyUpdate`; on failure show error with staging path if replacement failed
    - Show "Cancel" button during download; on cancel call `cancelled.set(true)`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.3, 4.5_
  - [x] 6.5 Implement restart prompt and startup-check checkbox in `UpdateDialog`
    - After successful `applyUpdate`, switch to ReadyToRestart state with a "Restart Now" button
    - "Restart Now" calls `UpdateService.restartWithJar(currentJarPath)`
    - Add startup-check `JCheckBox` in UpdateAvailable state; persist changes via `UpdatePreferences`
    - _Requirements: 2.4, 2.5, 4.2, 4.4_
  - [x] 6.6 Wire `updateInProgress` flag in `UpdateDialog`
    - Set `updateInProgress.set(true)` when a check or download starts; clear it in all terminal states (UpToDate, Error, ReadyToRestart, cancel)
    - _Requirements: 6.3, 6.4_

- [x] 7. Integrate into `MainFrame`, `AboutDialog`, and `App`
  - [x] 7.1 Add `AtomicBoolean updateInProgress` field to `MainFrame`
    - Declare `private final AtomicBoolean updateInProgress = new AtomicBoolean(false);`
    - _Requirements: 6.3_
  - [x] 7.2 Add "Check for Updates…" menu item to the Help menu in `MainFrame.createMenuBar()`
    - Insert the item above the separator that precedes "About DB Explorer"
    - Action: `new UpdateDialog(this, updateInProgress).setVisible(true)`
    - Disable the item while `updateInProgress` is true; re-enable when the dialog closes
    - _Requirements: 6.1, 6.3, 6.4_
  - [x] 7.3 Add "Check for Updates…" button to `AboutDialog`
    - Add a `JButton` below the existing OK button in `initUI()`
    - Accept `AtomicBoolean updateInProgress` via a new constructor overload or pass from `MainFrame`
    - Disable the button while `updateInProgress` is true
    - _Requirements: 6.2, 6.3, 6.4_
  - [x] 7.4 Add startup check in `App.main()` via `UpdateService.scheduleStartupCheck()`
    - Implement `scheduleStartupCheck(MainFrame frame, UpdatePreferences prefs)` in `UpdateService`
    - Load `UpdatePreferences`; if enabled, fire a `SwingWorker` that waits for the frame to be fully painted, then calls `fetchLatestRelease()` and shows `UpdateDialog` only if a newer version is found
    - Catch all exceptions silently (log to console only)
    - Call `scheduleStartupCheck` in `App.main()` after `frame.setVisible(true)`
    - _Requirements: 2.1, 2.2, 2.3, 2.6_

- [x] 8. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Property tests use jqwik 1.8.4 (already in `pom.xml`); each test method must include a comment `// Feature: self-update, Property N: <property text>`
- Unit tests use JUnit Jupiter 5.10.2 (already in `pom.xml`)
- All network calls use 10 s connect / 30 s read timeouts via `HttpClient` configuration
- The `updateInProgress` flag is the coordination mechanism between `MainFrame` menu item, `AboutDialog` button, and `UpdateDialog`
