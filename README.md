# DB Explorer — Database Query Tool & Explorer

**Version:** 3.0.1 &nbsp; | &nbsp; **Released:** April 2026 &nbsp; | &nbsp; **By:** Ashish Srivastava &nbsp; | &nbsp; **©** 2026 Astro Adept AI Labs

---

## Overview

DB Explorer is a cross-platform database query tool and schema explorer for developers, DBAs, and data analysts. It provides a clean GUI for connecting to multiple databases, writing SQL, exploring schemas, monitoring health, and generating SQL using AI.

**Key capabilities:** Multi-database support (PostgreSQL, MySQL, MariaDB, SQL Server, Oracle, SQLite, DynamoDB) · Multi-tab SQL editor with syntax highlighting · AI-powered SQL generation · 13 live-switching themes · Execution plan viewer · Database health dashboard · Query result export (CSV, INSERT, UPDATE, DDL)

---

## What's New in v3.0.1

| Feature | Details |
|---------|---------|
| AI SQL Generator | Natural language to SQL — supports OpenAI, Claude, DeepSeek, Gemini, Custom |
| Animated Progress Bars | Shown during query execution, test connection, and AI generation |
| Settings Menu | Replaces "Edit" menu — access via `Alt+S` |
| Help Menu | 6 tabbed in-app help topics + About dialog link — access via `Alt+H` |
| 13 Themes | Live switching from toolbar dropdown, no restart needed |
| SQL Server Explain Fix | Now returns actual execution plan instead of query text |
| Execution Plan | Horizontal scroll added, word wrap disabled |
| Claude API Fix | Null `system` field now omitted (was causing test failures) |
| DeepSeek / Gemini Fix | Null system prompt guard; Gemini uses correct `system_instruction` field |
| AI Dialog Sizing | Opens at 85% of main window size — adapts to screen resolution |
| Welcome Screen | AI setup hint added referencing Settings menu |
| Rebrand | Company renamed to **Astro Adept AI Labs** |

---

## Quick Start

```
# Build from source (requires JDK 17+, Maven 3.6+)
git clone <repository-url>
mvn clean package
java -jar target/db-explorer-3.0.1.jar

# Run pre-built JAR
java -jar db-explorer.jar

# Need more memory?
java -Xmx2048m -jar db-explorer.jar
```

1. Click **`+`** in the toolbar → fill in host, port, database, credentials → **Test Connection** → **Save**
2. Double-click the connection in the left panel to connect
3. Right-click connection → **Open Query Tab** — or click **New Tab** in the toolbar
4. Type SQL → press `Ctrl+Enter` to run
5. For AI: go to **Settings → AI Configuration…** → add a profile → click the **AI** toolbar button

---

## Main Window Layout

```
+-------------------------------------------------------------------------------------------+
|  [+] [Run] [Cancel] [NewTab] [Explain] [Health] [AI]    Theme v    Settings  Help        |
+---------------+-------------------------------------------------------------------+-------+
|               |  Tab 1  |  Tab 2  |  + New                                       |       |
|  Connections  |------------------------------------------------------------------| Health|
|  Tree         |  SQL Editor  (syntax highlighting + auto-complete)               | Panel |
|               |------------------------------------------------------------------|       |
|  > MyDB       |  Results  |  Explain Plan                                        |       |
|    > Tables   |                                                                  |       |
+---------------+-------------------------------------------------------------------+-------+
|  Connected: MyDB (PostgreSQL)                  Heap: 256/1024 MB  [GC]                   |
+-------------------------------------------------------------------------------------------+
```

---

## System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Operating System | Windows 10 / macOS 10.14 / Ubuntu 18.04 | Windows 11 / macOS 12+ / Ubuntu 20.04+ |
| Java Runtime | Java 17 | Java 17 LTS |
| Memory | 512 MB RAM | 2 GB RAM |
| Disk Space | 100 MB | 500 MB |
| Display | 1024x768 | 1920x1080 |

---

## Installation

### Option A — Pre-built Distribution (Recommended)

1. Download the latest release ZIP from the project repository
2. Extract to any folder
3. **JVM-bundled:** run `db-explorer.exe` (Windows) or `./db-explorer` (Linux/macOS)
4. **JVM-independent:** `java -jar db-explorer.jar`

### Option B — Build from Source

```
git clone <repository-url>
cd db-explorer
mvn clean package
java -jar target/db-explorer-3.0.1.jar
```

### Option C — Development Setup

1. Clone the repository
2. Import as Maven project in IntelliJ IDEA or Eclipse
3. Configure JDK 17+
4. Run main class: `com.dbexplorer.ui.MainFrame`

---

## Database Support

| Database | Version | Features |
|----------|---------|---------|
| PostgreSQL | 9.0+ | Full schema, `EXPLAIN ANALYZE`, JSON/JSONB, pg_stat views |
| MySQL / MariaDB | 5.5+ / 10.0+ | Performance schema, stored procedures, `EXPLAIN` tabular |
| Microsoft SQL Server | 2012+ | DMVs, `SET SHOWPLAN_TEXT`, linked server browsing |
| Oracle Database | 11g+ | PL/SQL objects, `EXPLAIN PLAN` + `DBMS_XPLAN`, v$ views |
| SQLite | 3.0+ | File-based, PRAGMA statements, WAL mode |
| Amazon DynamoDB | — | PartiQL queries, table browsing, key schema display |
| Generic JDBC | — | Any JDBC-compatible database |

---

## Toolbar Reference

| Button | Shortcut | Action |
|--------|----------|--------|
| `+` Add Connection | — | Open new connection dialog |
| Run | `Ctrl+Enter` | Execute current SQL query (green) |
| Cancel | — | Stop running query (red) |
| New Tab | `Ctrl+N` | Open a new query editor tab |
| Explain | — | Show execution plan for current query |
| Health | — | Open database health dashboard |
| AI | — | Open AI SQL Generator |
| Theme dropdown | — | Switch theme instantly — saved automatically |
| About | — | Show version and build info |

---

## Menu Bar

| Menu | Shortcut | Items |
|------|----------|-------|
| Settings | `Alt+S` | AI Configuration… |
| Help | `Alt+H` | User Guide · DB Connection · AI Configuration · DB Health · Execution Plan · Themes · *(separator)* · About DB Explorer |

---

## Keyboard Shortcuts

| Shortcut | Action | Shortcut | Action |
|----------|--------|----------|--------|
| `Ctrl+Enter` | Run query | `F5` | Refresh schema tree |
| `Ctrl+Z` | Undo | `Ctrl+Y` | Redo |
| `Ctrl+N` | New query tab | `Ctrl+S` | Save query |
| `Ctrl+E` | Export results | `Ctrl+F` | Find in results |
| `Alt+S` | Settings menu | `Alt+H` | Help menu |

---

## AI SQL Generator

Convert natural language descriptions into SQL using your configured AI model.

| Provider | Example Models | Notes |
|----------|---------------|-------|
| OpenAI | `gpt-4o`, `gpt-3.5-turbo` | Standard OpenAI API |
| Claude | `claude-sonnet-4-20250514` | Anthropic API |
| DeepSeek | `deepseek-chat` | OpenAI-compatible format |
| Gemini | `gemini-1.5-pro` | Google Generative AI |
| Custom | Any model name | Any OpenAI-compatible endpoint |

**Setup:** Go to **Settings → AI Configuration…** → click **Add New** → fill provider, model, API key → **Test Connection** → **Save** → enable the profile.

**Usage:** Click the **AI** toolbar button (requires active connection) → select AI profile → describe your query → click **Generate SQL** → review → **Copy** or **Insert into Query Tab**.

> The generator is schema-aware — it uses your actual table and column names for accurate output. Always review generated SQL before running against production data.

---

## Execution Plan

| Database | Method | Notes |
|----------|--------|-------|
| PostgreSQL | `EXPLAIN ANALYZE` | Shows actual runtime stats |
| MySQL / MariaDB | `EXPLAIN` | Rendered as tabular view |
| SQL Server | `SET SHOWPLAN_TEXT ON` | Returns text execution plan |
| Oracle | `EXPLAIN PLAN FOR` + `DBMS_XPLAN.DISPLAY()` | Full plan output |
| DynamoDB | Not supported | — |

Click **Explain** in the toolbar after writing a SELECT query. The plan appears in the **Explain Plan** tab below the editor with horizontal scrolling and no word wrap.

---

## Database Health Dashboard

Click the **Health** toolbar button while connected to open the live monitoring panel.

| Panel | Information Shown |
|-------|------------------|
| Connection | Host, port, database name, driver version, ping latency |
| Server Stats | Uptime, active connections, cache hit ratio, database size |
| Active Queries | Currently running queries with duration and state |
| JVM Stats | Local heap usage, GC count, thread count |

Stats refresh automatically. Click **Refresh** for an instant update.

---

## Themes

Switch live from the **Theme** dropdown in the toolbar — no restart needed. Your selection is saved and restored on next launch.

| Theme | Style | Best For | Theme | Style | Best For |
|-------|-------|----------|-------|-------|----------|
| Flat Dark | Dark | Default, most environments | Flat Darcula | Warm dark | JetBrains IDE users |
| Flat Light | Light | Bright rooms, screenshots | Flat IntelliJ | Light | IntelliJ IDEA users |
| Ocean Blue | Dark blue | Long sessions, calm palette | Forest Green | Dark green | Relaxed, earthy feel |
| Sunset Purple | Dark purple | Vibrant, high contrast | Cherry Red | Dark + red | Bold, high energy |
| Amber Warm | Warm amber | Evening work | Arctic Frost | Cool light | Clean, airy feel |
| Rose Garden | Light + rose | Elegant light theme | System | OS native | Matches your OS |
| Metal | Java default | Consistent cross-platform | | | |

---

## Configuration

Config file location:
- **Windows:** `%APPDATA%\DBExplorer\app.properties`
- **macOS:** `~/Library/Application Support/DBExplorer/app.properties`
- **Linux:** `~/.config/DBExplorer/app.properties`

```properties
query.max.rows=10000      # Maximum rows to load per query
query.fetch.size=500      # Rows fetched per page
export.fetch.size=500     # Rows fetched during export
connection.timeout=30     # Connection timeout in seconds
```

---

## Troubleshooting

| Problem | Solution |
|---------|---------|
| App won't start | Verify Java 17+: `java -version` · Try `java -Xmx1024m -jar db-explorer.jar` |
| Connection fails | Check server is running · Verify firewall/port · Use Test Connection in dialog |
| Out of memory | Use `java -Xmx2048m` · Reduce `query.max.rows` · Click GC button in status bar |
| SQL Server plan shows query text | User needs `SHOWPLAN` permission · Disconnect and reconnect, then retry |
| AI generator returns error | Check API key in Settings · Use Test Connection · Check provider quota/rate limits |
| Schema tree empty after connect | Press `F5` to refresh · Verify user has metadata access privileges |
| Slow queries | Use Explain Plan to find full table scans · Add indexes · Use `LIMIT` clauses |

**Enable debug logging:** add `-Dlogging.level.com.dbexplorer=DEBUG` to JVM arguments.

Log locations: Windows `%APPDATA%\DBExplorer\logs\` · macOS `~/Library/Logs/DBExplorer/` · Linux `~/.cache/DBExplorer/logs/`

---

## Release Notes

### v3.0.1 — April 2026 *(Latest)*

| Area | Change |
|------|--------|
| AI SQL Generator | New feature — natural language to SQL with schema awareness |
| Progress Bars | Animated indeterminate bars on query execution, test connection, AI generation |
| Settings Menu | Renamed from "Edit" — `Alt+S` mnemonic |
| Help Menu | 6 tabbed topics (User Guide, DB Connection, AI Config, DB Health, Exec Plan, Themes) + About |
| Themes | 13 themes with live switching and persistence |
| SQL Server Explain | Fixed — now correctly returns execution plan via `SHOWPLAN_TEXT` |
| Execution Plan UI | Word wrap disabled, horizontal scrollbar added |
| Claude API | Fixed null `system` field being sent (caused 400 errors) |
| DeepSeek | Fixed null system prompt being sent as `"null"` string |
| Gemini | Fixed system prompt — now uses `system_instruction` field correctly |
| AI Dialog | Size derived from main window (85% width x 80% height) |
| Welcome Screen | Added AI setup hint referencing Settings menu |
| Branding | Renamed from Adept Software to Astro Adept AI Labs |

### v2.4.0 — March 31, 2026
Enhanced UX for Disconnect and Run Query buttons · Color-coded status messages · Improved validation

### v2.3.0 — March 31, 2026
Garbage collection button (GC) in status bar · Manual memory control

### v2.2.0 — March 31, 2026
Memory leak fixes · Configuration externalization · Performance improvements

### v2.1.0
Database Health Dashboard · Theme fixes · UI improvements

---

## License

DB Explorer is proprietary software developed by **Astro Adept AI Labs**.

Copyright (c) 2026 Astro Adept AI Labs. All rights reserved.

1. This copyright notice must be included in all copies or substantial portions of the software.
2. The software may not be redistributed or sold without explicit written permission from Astro Adept AI Labs.
3. Any modifications to the source code must be clearly marked and documented.

For licensing inquiries: adeptashish@gmail.com

---

## Support

| Channel | Details |
|---------|---------|
| User Handbook | [docs/USER_HANDBOOK.md](docs/USER_HANDBOOK.md) — full feature documentation |
| Email | adeptashish@gmail.com |
| Bug Reports | GitHub Issues — include version, OS, Java version, DB type, steps to reproduce |
| Feature Requests | GitHub Issues — include use case, expected behaviour, affected DB systems |
| Q&A | Stack Overflow — tag: `db-explorer` |

---

*Built by Astro Adept AI Labs | © 2026 | Happy querying!*
