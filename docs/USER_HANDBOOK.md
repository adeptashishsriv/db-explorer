# DB Explorer — User Handbook

**Version:** 3.0.1  
**Date:** April 2026  
**Publisher:** Astro Adept AI Labs

---

## Table of Contents

1. [Overview](#1-overview)
2. [System Requirements](#2-system-requirements)
3. [Installation](#3-installation)
4. [Main Window Layout](#4-main-window-layout)
5. [Managing Connections](#5-managing-connections)
6. [Writing and Running Queries](#6-writing-and-running-queries)
7. [Browsing the Schema](#7-browsing-the-schema)
8. [Exporting Data](#8-exporting-data)
9. [Execution Plan](#9-execution-plan)
10. [Database Health Dashboard](#10-database-health-dashboard)
11. [AI SQL Generator](#11-ai-sql-generator)
12. [Themes](#12-themes)
13. [Settings Menu](#13-settings-menu)
14. [Help Menu](#14-help-menu)
15. [Keyboard Shortcuts](#15-keyboard-shortcuts)
16. [Troubleshooting](#16-troubleshooting)
17. [License](#17-license)

---

## 1. Overview

DB Explorer is a cross-platform database query tool and schema explorer built for developers, DBAs, and data analysts. It provides a clean graphical interface for connecting to multiple database systems, writing and executing SQL, exploring schemas, monitoring database health, and generating SQL using AI.

**Key capabilities:**

- Multi-database support (PostgreSQL, MySQL, MariaDB, SQL Server, Oracle, SQLite, DynamoDB)
- Multi-tab SQL editor with syntax highlighting and auto-complete
- Interactive schema browser
- Query result export (CSV, INSERT, UPDATE, DDL)
- Database health monitoring dashboard
- Execution plan viewer
- AI-powered SQL generation from natural language
- 13 built-in themes with live switching

---

## 2. System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| OS | Windows 10, macOS 10.14, Ubuntu 18.04 | Windows 11, macOS 12+, Ubuntu 20.04+ |
| Java | Java 17 | Java 17 LTS |
| RAM | 512 MB | 2 GB |
| Disk | 100 MB | 500 MB |
| Display | 1024×768 | 1920×1080 |

---

## 3. Installation

### Option A — Pre-built Distribution (Recommended)

1. Download the latest release ZIP from the project repository.
2. Extract to any folder.
3. **JVM-bundled version:** run `db-explorer.exe` (Windows) or `./db-explorer` (Linux/macOS).
4. **JVM-independent version:** ensure Java 17+ is installed, then run:
   ```
   java -jar db-explorer.jar
   ```

### Option B — Build from Source

**Prerequisites:** JDK 17+, Apache Maven 3.6+

```bash
git clone <repository-url>
cd db-explorer
mvn clean package
java -jar target/db-explorer-2.4.1.jar
```

---

## 4. Main Window Layout

```
┌──────────────────────────────────────────────────────────────────────┐
│  [+] [Run ▶] [Cancel ■] [New Tab] [Explain] [Health] [AI] [Theme ▾] │  ← Toolbar
├──────────────┬───────────────────────────────────────────────────────┤
│              │  Tab 1 ●  │  Tab 2 ●  │  + New Tab                   │
│  Connections │──────────────────────────────────────────────────────│
│  Tree        │  SQL Editor                                           │
│              │                                                       │
│  ▶ MyDB      │──────────────────────────────────────────────────────│
│    ▶ Tables  │  Results  │  Explain Plan                             │
│    ▶ Views   │                                                       │
├──────────────┴───────────────────────────────────────────────────────┤
│  Status: Connected to MyDB  │  Heap: 256 MB / 1024 MB (25%)  [🗑]   │  ← Status bar
└──────────────────────────────────────────────────────────────────────┘
```

**Toolbar buttons:**

| Button | Action |
|--------|--------|
| `+` | Add new database connection |
| `Run ▶` | Execute current SQL (green) |
| `Cancel ■` | Stop running query (red) |
| `New Tab` | Open a new query tab |
| `Explain` | Show execution plan for current query |
| `Health` | Open database health dashboard |
| `AI` | Open AI SQL Generator |
| `Theme ▾` | Switch UI theme instantly |
| `About` | Show application info |

**Status bar:**  
Shows active connection name, heap memory usage bar, and a garbage collection button (🗑) for manual memory management.

---

## 5. Managing Connections

### Create a Connection

1. Click the **`+`** toolbar button.
2. Fill in the connection form:

| Field | Description |
|-------|-------------|
| Name | A friendly label for this connection |
| Type | Database type (PostgreSQL, MySQL, SQL Server, Oracle, SQLite, DynamoDB, etc.) |
| Host | Server hostname or IP address |
| Port | Port number (pre-filled with defaults) |
| Database | Database or schema name |
| Username | Login username |
| Password | Login password |

3. Click **Test Connection** to verify before saving.
4. Click **Save**.

### Connect / Disconnect

- **Connect:** Double-click a connection in the left panel, or right-click → **Connect**.
- **Disconnect:** Select the connection and click the **Disconnect** toolbar button, or right-click → **Disconnect**. A confirmation dialog prevents accidental disconnection.

### Edit / Delete

Right-click any connection in the left panel for **Edit** and **Delete** options.

### Supported Databases

| Database | Notes |
|----------|-------|
| PostgreSQL 9.0+ | Full schema, live stats, JSON/JSONB support |
| MySQL 5.5+ / MariaDB 10.0+ | Performance schema, stored procedures |
| Microsoft SQL Server 2012+ | DMVs, linked server browsing |
| Oracle 11g+ | PL/SQL objects, v$ views |
| SQLite 3.0+ | File-based, PRAGMA support |
| Amazon DynamoDB | NoSQL table browsing, PartiQL query execution, key schema display |
| Generic JDBC | Any JDBC-compatible database |

---

## 6. Writing and Running Queries

### Opening a Query Tab

- Click **New Tab** in the toolbar, or
- Right-click a connection → **Open Query Tab** to open a tab pre-bound to that connection.

### The SQL Editor

- Full syntax highlighting for SQL keywords, strings, numbers, and comments.
- Auto-complete triggers as you type — table names, column names, and SQL keywords.
- Each tab is independently bound to a connection — you can query multiple databases simultaneously.

### Running a Query

1. Type your SQL in the editor.
2. Press **Ctrl+Enter** or click the green **Run** button.
3. Results appear in the **Results** tab below the editor.

### Result Table

- Click any column header to sort ascending/descending/unsorted (cycles through states).
- Scroll horizontally for wide result sets.
- Large result sets are loaded lazily — scroll down to fetch more rows automatically.

### Cancelling a Query

Click the red **Cancel** button in the toolbar while a query is running.

---

## 7. Browsing the Schema

Expand a connected database in the left panel to browse:

- **Tables** — expand to see columns with data types
- **Views**
- **Stored Procedures / Functions**
- **Indexes and Constraints**

Double-click a table to open a query tab and view its data. Right-click items for context menu options.

Press **F5** to refresh the schema tree.

---

## 8. Exporting Data

After running a query, use the export buttons below the result table:

| Format | Description |
|--------|-------------|
| CSV | Comma-separated values, compatible with Excel and most tools |
| INSERT | SQL INSERT statements for each row |
| UPDATE | SQL UPDATE statements |
| DDL | CREATE TABLE statement for the result schema |

Large exports are streamed in batches — no need to load all rows first.

---

## 9. Execution Plan

The execution plan shows how the database engine will execute your query — useful for identifying slow operations and missing indexes.

### How to Use

1. Write a SELECT query in the editor.
2. Click the **Explain** toolbar button.
3. The plan appears in the **Explain Plan** tab below the editor.
4. Long plan lines scroll horizontally — no text wrapping.

### Reading the Plan

| Term | Meaning |
|------|---------|
| Seq Scan | Full table scan — consider adding an index |
| Index Scan | Efficient lookup via an index |
| Hash Join | Join using a hash table — good for large sets |
| Nested Loop | Join by iterating — best for small sets |
| `cost=X..Y` | Estimated startup and total cost (lower is better) |
| `rows=N` | Estimated row count |

### Database-Specific Behaviour

| Database | Method |
|----------|--------|
| PostgreSQL | `EXPLAIN ANALYZE` — shows actual runtime stats |
| MySQL / MariaDB | `EXPLAIN` — tabular format |
| SQL Server | `SET SHOWPLAN_TEXT ON` — text execution plan |
| Oracle | `EXPLAIN PLAN FOR` + `DBMS_XPLAN.DISPLAY()` |
| DynamoDB | Not supported |

> **Tip:** For PostgreSQL, use `EXPLAIN ANALYZE` directly in the SQL editor for actual execution times alongside estimates.

---

## 10. Database Health Dashboard

The health dashboard provides a live view of your database server's status.

### Opening

Click the **Health** toolbar button while connected to a database.

### Panels

| Panel | Information |
|-------|-------------|
| Connection | Host, port, database name, driver version, ping latency |
| Server Stats | Uptime, active connections, cache hit ratio, database size |
| Active Queries | Currently running queries with duration and state |
| JVM Stats | Local heap usage, GC count, thread count |

Stats refresh automatically. Click **Refresh** for an instant update. Click the Health button again to close the panel.

---

## 11. AI SQL Generator

The AI SQL Generator converts natural language descriptions into SQL queries using your configured AI model.

### Prerequisites

Configure at least one AI profile first — see [Section 13](#13-settings-menu).

### Opening

Click the **AI** toolbar button. You must have an active database connection selected.

> The dialog opens at 85% of the main window size and adapts to your screen.

### Using the Generator

1. The **Database Context** bar at the top shows the connected database. Schema information is loaded automatically.
2. Select an **AI Profile** from the dropdown (top right).
3. Type your request in natural language in the **Describe Your Query** box.  
   Example: *"Get all orders placed in the last 7 days with total value over 500"*
4. Click **Generate SQL**.
5. An animated progress bar shows while the AI is processing.
6. The generated SQL appears in the **Generated SQL** panel.
7. Click **Copy to Clipboard** or **Insert into Query Tab**.

### Tips

- The more specific your description, the better the result.
- The AI uses your actual schema (table and column names) for accurate output.
- Always review generated SQL before executing against production data.

---

## 12. Themes

DB Explorer includes 13 themes switchable at runtime with no restart required.

### How to Switch

Use the **Theme** dropdown in the toolbar. The change applies instantly to all open windows. Your selection is saved and restored on next launch.

### Available Themes

| Theme | Style | Best For |
|-------|-------|----------|
| Flat Dark | Dark | Default — most environments |
| Flat Darcula | Warm dark | JetBrains IDE users |
| Flat Light | Light | Bright rooms, screenshots |
| Flat IntelliJ | Light | IntelliJ IDEA users |
| Ocean Blue | Dark blue | Long sessions, calm palette |
| Forest Green | Dark green | Earthy, relaxed feel |
| Sunset Purple | Dark purple | Vibrant, high contrast |
| Cherry Red | Dark + red accents | Bold, high energy |
| Amber Warm | Warm amber | Evening work |
| Arctic Frost | Cool light | Clean, airy feel |
| Rose Garden | Light + rose | Elegant light theme |
| System | OS native | Matches your OS |
| Metal | Java default | Consistent cross-platform |

### Why Themes Matter

- **Dark themes** reduce eye strain in low-light environments.
- **Light themes** improve readability on bright displays and in printed screenshots.
- **High-contrast options** help users with visual sensitivities.

---

## 13. Settings Menu

Access via **Settings** in the menu bar (`Alt+S`).

### AI Configuration

**Settings → AI Configuration…** opens the AI profile manager.

| Field | Description |
|-------|-------------|
| Name | A label for this profile |
| Provider | OpenAI, Claude, DeepSeek, Gemini, or Custom |
| Model | Exact model name (e.g. `gpt-4o`, `claude-sonnet-4-20250514`) |
| API Key | Your provider API key — stored encrypted on disk |
| Base URL | Auto-filled per provider; change only for custom endpoints |
| Max Tokens | Maximum response length |
| Temperature | Creativity level (0.0 = deterministic, 1.0 = creative) |
| Enabled | Toggle this profile on/off |

**Steps:**
1. Click **Add New**.
2. Fill in the fields.
3. Click **Test Connection** — an animated progress bar shows while testing.
4. Click **Save**.
5. Check **Enable AI Assistant**.

Multiple profiles can be saved and switched from the AI Generator panel.

---

## 14. Help Menu

Access via **Help** in the menu bar (`Alt+H`).

| Menu Item | Opens |
|-----------|-------|
| User Guide | Help dialog — Quick Start tab |
| DB Connection | Help dialog — DB Connection tab |
| AI Configuration | Help dialog — AI Configuration tab |
| DB Health Dashboard | Help dialog — DB Health tab |
| Execution Plan | Help dialog — Execution Plan tab |
| Themes | Help dialog — Themes tab |
| *(separator)* | |
| About DB Explorer | About dialog with version and build info |

---

## 15. Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Enter` | Run current query |
| `Ctrl+Z` | Undo |
| `Ctrl+Y` | Redo |
| `Ctrl+N` | New query tab |
| `Ctrl+S` | Save current query |
| `Ctrl+F` | Find in results |
| `Ctrl+E` | Export results |
| `F5` | Refresh schema tree |
| `Alt+S` | Open Settings menu |
| `Alt+H` | Open Help menu |

---

## 16. Troubleshooting

### Application Won't Start

- Verify Java 17+: `java -version`
- Try increasing heap: `java -Xmx1024m -jar db-explorer.jar`
- Check console output for error messages.

### Connection Fails

- Confirm the database server is running and reachable.
- Check firewall rules for the port.
- Verify username and password.
- Use **Test Connection** in the connection dialog to diagnose.

### Out of Memory

- Increase heap: `java -Xmx2048m -jar db-explorer.jar`
- Reduce `query.max.rows` in `app.properties`.
- Use the 🗑 GC button in the status bar to free memory manually.

### Query is Slow

- Use **Explain Plan** to identify full table scans.
- Add indexes on filtered/joined columns.
- Use `LIMIT` / `FETCH FIRST N ROWS` to reduce result size.

### Execution Plan Shows Query Text Instead of Plan (SQL Server)

- Ensure the user has `SHOWPLAN` permission on the database.
- The plan requires a fresh connection — try disconnecting and reconnecting.

### AI Generator Returns an Error

- Verify the API key is correct in **Settings → AI Configuration…**.
- Use **Test Connection** to confirm the model is reachable.
- Check your API provider's quota and rate limits.

### Schema Tree is Empty After Connect

- Press **F5** to refresh.
- Confirm the user has metadata access privileges.
- For MySQL, ensure `information_schema` access is granted.

---

## 17. License

DB Explorer is proprietary software developed by **Astro Adept AI Labs**.

Copyright © 2026 Astro Adept AI Labs. All rights reserved.

Permission is granted to use this software for personal, educational, and commercial purposes subject to the following conditions:

1. This copyright notice must be included in all copies or substantial portions of the software.
2. The software may not be redistributed or sold without explicit written permission from Astro Adept AI Labs.
3. Any modifications to the source code must be clearly marked and documented.

For licensing inquiries: adeptashish@gmail.com

---

*Built by Astro Adept AI Labs | © 2026*
