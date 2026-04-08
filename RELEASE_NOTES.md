# DB Explorer Release Notes

## Version 2.4.1 — Connection Safety & Double-Click Protection

### New Features

**Connection Disconnect Warning**
Added a confirmation dialog when double-clicking on connected database connections in the tree panel to prevent accidental disconnects.

- Double-clicking a connected database now shows a confirmation dialog
- Dialog displays the connection name and explains that open query tabs remain bound
- Yes/No buttons allow users to confirm or cancel the disconnect action
- Non-connected databases still connect immediately without dialog
- Consistent with existing disconnect button confirmation pattern

### Bug Fixes

- Fixed potential accidental database disconnections through tree double-clicks
- Improved user safety when navigating the connection tree

---

## Version 2.4.0 — Enhanced User Experience & Status Feedback

### New Features

**Enhanced Disconnect Button**
Improved the Disconnect button with better validation and user feedback.

- Confirmation dialog appears before disconnecting
- HTML-formatted dialog shows connection name and helpful context
- Success confirmation dialog after successful disconnect
- Better tooltip with connection context information
- Smart connection selection (prefers tree selection, falls back to active tab)

**Enhanced Run Query Button**
Added pre-flight validation and instant feedback for the Run Query button.

- Pre-execution validation checks for open tab, active connection, and non-empty SQL
- Color-coded status messages appear instantly in the status bar:
  - 🔴 Red for errors (no tab open, etc.)
  - 🟠 Amber for warnings (no connection, empty SQL)
  - 🟢 Green for success
- Status messages auto-clear after 5 seconds
- Improved tooltip showing keyboard shortcut (Ctrl+Enter)
- Better user guidance and error prevention

**Status Message System**
New comprehensive status feedback system for better user experience.

- Color-coded messages with Unicode icons for quick recognition
- Auto-clearing messages prevent status bar clutter
- Consistent visual feedback across all operations
- Professional appearance with proper HTML formatting

### UI Improvements

- Enhanced button tooltips with HTML formatting and context information
- Improved visual feedback for all user actions
- Better error visibility and user guidance
- Consistent confirmation dialog patterns

### Bug Fixes

- Fixed silent failures when running queries without proper validation
- Improved error handling for disconnected states
- Better user feedback for all button operations

---

## Version 2.3.0 — Memory Management & Garbage Collection Control

### New Features

**Garbage Collection Button**
Added a manual garbage collection button to the status bar for memory management.

- 🗑 button appears in the bottom-right corner next to heap memory display
- Click triggers `System.gc()` to force garbage collection
- Heap display updates immediately after GC operation
- Console logging shows when GC was requested
- Hover effects for better visual feedback
- Small, unobtrusive design that doesn't interfere with other UI elements

### Performance Improvements

- Better memory management control for users
- Immediate visual feedback on memory usage
- Proactive memory management capabilities

---

## Version 2.2.0 — Memory Leak Fixes & Configuration Externalization

### New Features

**Memory Leak Resolution**
Fixed critical memory leak that occurred during repeated query executions.

- Added explicit `clearData()` method to `LazyQueryResult` class
- Proper cleanup of internal data structures when switching between queries
- Memory usage now stabilizes after each query instead of continuous growth
- Improved garbage collection behavior for large result sets

**Configuration Externalization**
Moved hardcoded values to external configuration file for better flexibility.

- Query limits and fetch sizes now configurable via `app.properties`
- Three new configuration properties:
  - `query.max.rows=10000` - Maximum rows to load per query
  - `query.fetch.size=500` - Rows fetched per page
  - `export.fetch.size=500` - Rows fetched during table exports
- Runtime configuration changes without recompilation
- Sensible fallback defaults maintained

### Performance Improvements

- Stable memory usage during long query sessions
- Better performance for applications with large result sets
- Configurable memory limits based on user needs
- Improved resource cleanup and garbage collection

### Bug Fixes

- Fixed unbounded memory growth during repeated query execution
- Resolved memory leaks in lazy result loading
- Improved resource management for database connections

---

## Version 2.1.0 — Database Health Dashboard & Theme Fixes

### New Features

**Database Health Dashboard**
A new opt-in health monitoring panel accessible via the toolbar bar-chart button. Disabled by default and toggled per connection — no background activity runs unless explicitly enabled.

- Connection health indicator (green/red) with last-check timestamp and reconnect count
- Database metadata: product name/version, driver, max connections, supported features
- Server Activity panel pulling live stats directly from the database's own system views:
  - PostgreSQL: `pg_stat_activity`, `pg_stat_database`, `pg_stat_user_tables`, `pg_stat_bgwriter`
  - MySQL/MariaDB: `performance_schema`, `SHOW GLOBAL STATUS`, `SHOW PROCESSLIST`
  - Oracle: `v$session`, `v$sql`, `v$sysstat`, `v$waitstat`
  - SQL Server: `sys.dm_exec_sessions`, `sys.dm_exec_requests`, `sys.dm_os_wait_stats`
  - SQLite: PRAGMA-based size stats
  - Generic JDBC: connection health, metadata, warnings, and JVM stats only
- Live Connections table showing all active sessions from all clients, with DB Explorer's own session highlighted
- SQL Warnings log (circular buffer, last 100 entries)
- JVM Resources: heap usage progress bar (amber >70%, red >90%), thread count, GC stats
- Configurable poll interval (5–30 seconds, default 10s) persisted per connection
- Uses a dedicated isolated JDBC connection — never interferes with active queries
- Stopping the dashboard immediately closes the background thread and JDBC connection

### Bug Fixes

- Fixed new query tabs picking up stale colors from the previous theme after a theme switch
- Fixed welcome panel text retaining old theme colors after switching themes
- Table headers now visually distinct from table body in all themes — lighter background with accent-colored text
- Tab separators more visible in dark themes with improved contrast
- Dashboard panel tables now resize correctly when the split pane divider is dragged
- `create_dist.bat` updated to reference correct JAR version

### Other

- `.gitignore` added — compiled classes, IDE files, and build artifacts excluded from version control

---

## Version 2.0.0 — Enhanced Connection Management & UI Improvements

### New Features
- **Smart "New Tab"**: Opens a query tab connected to the currently selected database, auto-connecting if needed
- **Persistent Query Tabs**: Tabs stay bound to their connection and auto-reconnect on query run
- **View Table Data**: Right-click a table node to instantly view top rows in a new tab
- **Column Explorer**: Expand table nodes to see column names and data types

### UI Enhancements
- Toolbar icons color-coded: green for Run Query, red for Clear Console
- About button more prominent with bold text and blue color
- Larger application icon on the welcome screen

### Bug Fixes
- Explain Plan no longer fails silently on dropped connections — auto-reconnects
- Fixed missing `flatlaf-extras` dependency compilation issue

---

## Version 1.1.0 — Enhanced Connection Management & UI Improvements

### New Features
- **Smart "New Tab"**: Opens a query tab connected to the currently selected database, auto-connecting if needed
- **Persistent Query Tabs**: Tabs stay bound to their connection and auto-reconnect on query run
- **View Table Data**: Right-click a table node to instantly view top rows in a new tab
- **Column Explorer**: Expand table nodes to see column names and data types

### UI Enhancements
- Toolbar icons color-coded: green for Run Query, red for Clear Console
- About button more prominent with bold text and blue color
- Larger application icon on the welcome screen

### Bug Fixes
- Explain Plan no longer fails silently on dropped connections — auto-reconnects
- Fixed missing `flatlaf-extras` dependency compilation issue

---
*Built by Astro Adept AI Labs | © 2026 *
