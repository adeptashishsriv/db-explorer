# Requirements Document

## Introduction

The self-update feature allows DB Explorer to automatically check for newer released versions and download the latest JAR directly from GitHub Releases. Users can trigger a manual check or opt into automatic startup checks. When a newer version is found, the application presents release notes and lets the user download and apply the update with minimal friction — replacing the running JAR and prompting a restart.

## Glossary

- **Updater**: The component responsible for checking for new versions and downloading update artifacts.
- **Release_API**: The GitHub Releases REST API endpoint `https://api.github.com/repos/adeptashishsriv/db-explorer/releases/latest` used to discover the latest published release, returning version metadata and direct download URLs as JSON.
- **Version**: A semantic version string in the format `MAJOR.MINOR.PATCH` (e.g., `2.5.0`).
- **JAR**: The self-contained executable JAR file distributed as a release asset.
- **Update_Dialog**: The Swing dialog that presents version information and update controls to the user.
- **About_Dialog**: The existing Swing dialog showing product version and company information, which also hosts the "Check for Updates…" button.
- **Current_Version**: The version of the running DB Explorer instance, read from `app.properties`.
- **Latest_Version**: The most recent version published on GitHub Releases.
- **Download_Dir**: The directory containing the currently running JAR file.
- **Staging_File**: A temporary file used to hold the downloaded JAR before it replaces the current one.

---

## Requirements

### Requirement 1: Version Check

**User Story:** As a user, I want DB Explorer to check whether a newer version is available, so that I know when to update.

#### Acceptance Criteria

1. WHEN the user selects "Check for Updates" from the Help menu, THE Updater SHALL query the Release_API to retrieve the Latest_Version.
2. WHEN the Release_API responds successfully, THE Updater SHALL compare the Latest_Version to the Current_Version using semantic version ordering.
3. IF the Latest_Version is greater than the Current_Version, THEN THE Update_Dialog SHALL display the Latest_Version, the release notes, and an option to download the update.
4. IF the Current_Version is equal to or greater than the Latest_Version, THEN THE Update_Dialog SHALL inform the user that DB Explorer is up to date.
5. IF the Release_API request fails or times out after 10 seconds, THEN THE Updater SHALL display an error message describing the failure without crashing the application.
6. THE Updater SHALL perform the version check on a background thread so that THE application UI remains responsive during the check.

---

### Requirement 2: Startup Update Check

**User Story:** As a user, I want DB Explorer to optionally check for updates on startup, so that I am notified of new releases without having to remember to check manually.

#### Acceptance Criteria

1. WHERE the startup update check setting is enabled, THE Updater SHALL perform a version check on a background thread within 5 seconds of application startup, so that the main window launch is not delayed.
2. WHERE the startup update check setting is enabled, IF a newer version is found on startup, THEN THE Update_Dialog SHALL be shown after the main window is fully visible.
3. WHERE the startup update check setting is enabled, IF no newer version is found on startup, THEN THE Updater SHALL log the result silently without showing any dialog.
4. THE application SHALL provide a checkbox in the Update_Dialog to enable or disable the startup update check.
5. THE application SHALL persist the startup update check preference to the user configuration file so that the setting is restored on next launch.
6. THE startup update check setting SHALL default to enabled for new installations.

---

### Requirement 3: JAR Download

**User Story:** As a user, I want to download the latest JAR directly from within DB Explorer, so that I can update without leaving the application or navigating to a browser.

#### Acceptance Criteria

1. WHEN the user clicks "Download Update" in the Update_Dialog, THE Updater SHALL download the JAR asset from GitHub Releases to a Staging_File in the Download_Dir.
2. WHILE the download is in progress, THE Update_Dialog SHALL display a progress bar showing the number of bytes downloaded and the total file size.
3. WHILE the download is in progress, THE Update_Dialog SHALL provide a "Cancel" button that stops the download and removes the Staging_File.
4. WHERE a SHA-256 checksum is published alongside the release asset, IF the downloaded file's SHA-256 checksum does not match the published checksum, THEN THE Updater SHALL delete the Staging_File and display an integrity error to the user.
5. WHERE no SHA-256 checksum is published for the release asset, THE Updater SHALL skip checksum verification and proceed with the downloaded file.
6. IF the download fails due to a network error, THEN THE Updater SHALL delete any partial Staging_File and display an error message with the failure reason.
7. THE Updater SHALL perform the download on a background thread so that THE application UI remains responsive during the download.

---

### Requirement 4: Update Application

**User Story:** As a user, I want the downloaded JAR to replace the current one automatically, so that I can start using the new version without manual file management.

#### Acceptance Criteria

1. WHEN the download completes successfully, THE Updater SHALL rename the current JAR to a backup file and move the Staging_File to the current JAR's path.
2. WHEN the JAR replacement succeeds, THE Update_Dialog SHALL prompt the user to restart DB Explorer to apply the update.
3. IF the JAR replacement fails due to a file system permission error, THEN THE Updater SHALL retain the Staging_File and display instructions for the user to perform the replacement manually.
4. WHEN the user confirms the restart prompt, THE Updater SHALL launch a new process using the updated JAR and then exit the current process.
5. IF the current JAR path cannot be determined at runtime, THEN THE Updater SHALL display an error and offer the user a "Save As" option to save the downloaded JAR to a user-chosen location.

---

### Requirement 5: Release Notes Display

**User Story:** As a user, I want to read the release notes for the new version before updating, so that I can understand what has changed.

#### Acceptance Criteria

1. WHEN the Update_Dialog is shown with a newer version available, THE Update_Dialog SHALL display the release notes body from the Release_API response.
2. THE Update_Dialog SHALL render the release notes in a scrollable text area that supports at least 400 characters of visible content without truncation.
3. IF the release notes body is empty or absent from the Release_API response, THEN THE Update_Dialog SHALL display the message "No release notes available for this version."

---

### Requirement 6: Update Menu and Dialog Integration

**User Story:** As a user, I want to access the update check from the Help menu and the About dialog, so that the feature is easy to discover from multiple entry points.

#### Acceptance Criteria

1. THE application SHALL add a "Check for Updates…" menu item to the Help menu, positioned above the separator that precedes "About DB Explorer".
2. THE About_Dialog SHALL include a "Check for Updates…" button that triggers the same version check as the Help menu item.
3. WHILE an update check or download is already in progress, THE "Check for Updates…" menu item and the About_Dialog button SHALL be disabled.
4. WHEN the update check completes or is cancelled, THE "Check for Updates…" menu item and the About_Dialog button SHALL be re-enabled.
