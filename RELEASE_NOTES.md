# DB Explorer Release Notes

## Version 1.1.0 - Enhanced Connection Management & UI Improvements

### New Features
*   **Smart "New Tab"**: Clicking the "New Tab" button now automatically opens a query tab connected to the database currently selected in the left panel. If the connection is closed, it will automatically connect before opening the tab.
*   **Persistent Query Tabs**: Query tabs are now permanently tied to their initial database connection. Even if the connection is closed or dropped, running a query will automatically reconnect to the correct database without user intervention.
*   **View Table Data**: Added a right-click "View Data" option on table nodes to instantly view the top rows of a table in a new tab.
*   **Column Explorer**: Expand table nodes in the connection tree to view column names and data types.

### UI Enhancements
*   **Toolbar Icons**: Added color coding to key actions—Green for "Run Query" and Red for "Clear Console" for better visual distinction.
*   **About Button**: Made the "About" button more prominent with bold text and a distinct blue color.
*   **Welcome Screen**: Increased the size of the application icon on the welcome screen for a better first impression.

### Bug Fixes
*   Fixed an issue where "Explain Plan" would fail silently if the connection had dropped. It now attempts to auto-reconnect.
*   Resolved a compilation issue related to missing `flatlaf-extras` dependency.

---
*Built by Ashish Srivastava | © 2026 Adept Software*
