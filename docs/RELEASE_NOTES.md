# DB Explorer Release Notes

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
*Built by Ashish Srivastava | © 2026 Astro Adept AI Labs*
