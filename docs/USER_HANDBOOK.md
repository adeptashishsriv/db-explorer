# DB Explorer User Handbook

**DB Explorer** is a lightweight, modern database client designed for developers who need quick access to their SQL and NoSQL data. It supports PostgreSQL, MySQL, Oracle, SQL Server, SQLite, AWS DynamoDB, and any Generic JDBC-compatible database.

## Table of Contents
1. [Getting Started](#getting-started)
2. [Supported Databases](#supported-databases)
3. [Managing Connections](#managing-connections)
4. [Browsing Schemas & Tables](#browsing-schemas--tables)
5. [Query Editor](#query-editor)
6. [Viewing Results](#viewing-results)
7. [Explain Plan](#explain-plan)
8. [Schema Diagram](#schema-diagram)
9. [DDL Export](#ddl-export)
10. [Table Data Export](#table-data-export)
11. [Health Dashboard](#health-dashboard)
12. [Themes](#themes)
13. [DynamoDB Support](#dynamodb-support)
14. [Keyboard Shortcuts](#keyboard-shortcuts)
15. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Prerequisites
- Java Runtime Environment (JRE) 17 or higher
- Network access to your target database

### Launching the Application
```
java -jar db-explorer-2.1.0.jar
```
On Windows you can also double-click the JAR if Java is associated with `.jar` files, or use the bundled `db-explorer.bat` launcher from the `dist` folder.

Upon launch you will see the **Welcome Screen** with quick-start tips.

---

## Supported Databases

| Database | Default Port | Notes |
|---|---|---|
| PostgreSQL | 5432 | Full system-view stats in Health Dashboard |
| MySQL / MariaDB | 3306 | Full system-view stats in Health Dashboard |
| Oracle | 1521 | Full system-view stats in Health Dashboard |
| SQL Server | 1433 | Full system-view stats in Health Dashboard |
| SQLite | — | File-based; limited Health Dashboard stats |
| AWS DynamoDB | — | NoSQL / PartiQL; serverless Health Dashboard |
| Generic JDBC | custom | Any JDBC driver; basic Health Dashboard stats |

---

## Managing Connections

### Adding a Connection
1. Click **Add Connection** (`➕`) in the toolbar
2. Fill in the connection details:
   - **Name** — a friendly label (e.g. "Prod Postgres")
   - **Database Type** — select your engine
   - **Host / Port / Database** — for SQL databases
   - **Username / Password** — your credentials
   - **AWS Region / Keys** — for DynamoDB
   - **JDBC URL / Driver Class / Driver JAR** — for Generic JDBC
3. Click **Test Connection** to verify before saving
4. Click **Save**

Passwords and AWS keys are encrypted with a machine-specific key and stored in `~/.dbexplorer/connections.json`.

### Editing or Deleting
- **Edit**: Right-click a connection → **Edit**
- **Delete**: Right-click a connection → **Delete**

### Connecting / Disconnecting
- **Connect**: Double-click a connection, or right-click → **Connect**. A green dot appears when connected.
- **Disconnect**: Click the **Disconnect** button in the toolbar, or right-click → **Disconnect**. Open query tabs keep their binding and will auto-reconnect when you run a query.

---

## Browsing Schemas & Tables

The left panel is the **Connection Tree**.

- Expand a connected SQL database to see **Schemas**
- Expand a schema to see folders for **Tables**, **Views**, **Sequences**, **Indexes**, **Functions**, and **Procedures**
- Expand any **Table** node to see its **columns** with data types (e.g. `id (int4)`, `name (varchar)`)
- For **DynamoDB**, tables appear directly under the connection node

### Right-click actions on a table node
| Action | Description |
|---|---|
| Open Query Tab | Opens a new editor tab bound to this connection |
| View Data | Opens a tab pre-filled with `SELECT * FROM table` |
| Export DDL | Generates the CREATE TABLE statement |
| Export CSV | Exports table data to a CSV file |
| Schema Diagram | Opens a visual ER-style diagram for the schema |

---

## Query Editor

A multi-tabbed SQL editor with syntax highlighting, undo/redo, and auto-indent.

### Opening a Tab
- **New Blank Tab**: Click **New Tab** (`📄`) in the toolbar — opens a tab bound to the currently selected connection
- **From Tree**: Right-click a connection → **Open Query Tab**
- **View Data**: Right-click a table → **View Data** — opens a tab pre-filled with a SELECT query

### Writing Queries
- SQL keywords are highlighted in blue (adapts to light/dark themes)
- `Tab` inserts 4 spaces
- `Ctrl+Z` / `Ctrl+Y` for undo/redo

### Running Queries
- Click **Run Query** (`▶`) in the toolbar, or press `Ctrl+Enter`
- To run only part of a query, **select the text** first — only the selection will execute
- Execution time and row count appear in the status bar

### Persistent Tab Binding
Each tab is permanently bound to the connection it was opened with. If the connection drops, running a query will automatically reconnect — you never lose your tab context.

---

## Viewing Results

Results appear in the bottom pane of each query tab.

- **Lazy loading**: Large result sets load in pages (200 rows by default). Scroll to the bottom to fetch the next page automatically.
- **Grid**: Resizable columns, sortable by clicking headers
- **Console**: The bottom-most panel logs all executed queries, timings, and errors. Click **Clear Console** (`🗑`) to wipe it.

---

## Explain Plan

1. Type your query in the editor
2. Click the **Explain Plan** button (`📊`) in the toolbar
3. The execution plan appears in the **Explain Plan** tab at the bottom of the editor

> Not supported for DynamoDB.

---

## Schema Diagram

Visualise the relationships between tables in a schema.

1. Right-click a connection or schema node in the tree
2. Select **Schema Diagram**
3. A diagram window opens showing tables and their foreign-key relationships

---

## DDL Export

Generate the `CREATE TABLE` (and related) DDL for any table or schema.

1. Right-click a table or schema node → **Export DDL**
2. The DDL export dialog opens — choose scope and options
3. Copy or save the generated SQL

---

## Table Data Export

Export table contents to a file.

1. Right-click a table node → **Export CSV** (or use the toolbar export button)
2. Choose destination file and delimiter options
3. Click **Export**

---

## Health Dashboard

The Health Dashboard gives you a live view of your database server's vital statistics. It runs on a completely separate background thread with its own dedicated connection — it never interferes with your active queries.

### Enabling the Dashboard
1. Select a connection in the left panel (must be connected)
2. Click the **bar-chart icon** (`📊`) in the toolbar to toggle the dashboard on
3. The dashboard panel slides in on the right side of the window
4. Click the same button again to turn it off — the background thread and connection stop immediately

The enabled/disabled state is saved **per connection** and persists across restarts. Enabling it for one connection has no effect on others.

### Dashboard Sections

**Connection Health**
- Live validity check (`isValid`) with green/red indicator
- Timestamp of last successful check
- Reconnect attempt count

**Database Metadata** *(fetched once per session)*
- Product name and version
- Driver name and version
- Maximum connections supported
- Supported features: transactions, savepoints, batch updates, stored procedures

**Server Activity** *(all clients, not just DB Explorer)*

Pulled directly from the database's own system views:

| Engine | Source |
|---|---|
| PostgreSQL | `pg_stat_activity`, `pg_stat_database`, `pg_stat_user_tables`, `pg_stat_bgwriter` |
| MySQL/MariaDB | `performance_schema`, `SHOW GLOBAL STATUS`, `SHOW PROCESSLIST` |
| Oracle | `v$session`, `v$sql`, `v$sysstat`, `v$waitstat` |
| SQL Server | `sys.dm_exec_sessions`, `sys.dm_exec_requests`, `sys.dm_os_wait_stats` |
| SQLite | PRAGMA statements (page count, page size) |
| DynamoDB / Generic | Not available — section shows an informational message |

Metrics shown (where supported):
- Active sessions and total connections
- Currently running queries with duration
- Total commits and rollbacks (server-wide)
- Cache hit ratio
- Lock waits and deadlock count
- Sequential and index scan counts
- Slow query count
- Database size on disk

**Live Connections**
- Table of all currently active sessions on the server (all clients)
- Columns: Connection ID, Username, Host, State, Current Query, Duration
- DB Explorer's own session is highlighted in bold
- For SQLite/DynamoDB/Generic JDBC, shows a single row with your connection details and an explanatory note

**SQL Warnings**
- Last 10 SQL warnings raised by the server, with timestamp and SQLState code
- Circular buffer of up to 100 entries

**JVM Resources**
- Heap used / max with a progress bar (turns amber above 70%, red above 90%)
- Live thread count
- GC collection count and total time

### Poll Interval
The dashboard refreshes every **10 seconds** by default. This is configurable (5–30 seconds) and saved per connection. The background thread uses `scheduleWithFixedDelay` — if a poll cycle takes longer than the interval, the next cycle is skipped rather than queued.

---

## Themes

DB Explorer ships with 10 themes — 4 built-in FlatLaf themes and 6 custom colorful themes.

| Theme | Style |
|---|---|
| Flat Dark | Dark (built-in) |
| Flat Darcula | Dark (built-in) |
| Flat Light | Light (built-in) |
| Flat IntelliJ | Light (built-in) |
| Ocean Blue | Dark custom |
| Forest Green | Dark custom |
| Sunset Purple | Dark custom |
| Cherry Red | Dark custom |
| Amber Warm | Dark custom |
| Arctic Frost | Light custom |
| Rose Garden | Light custom |

Switch themes using the **Theme** dropdown in the toolbar. The selection is saved and restored on next launch. All UI elements — including table headers, tab colors, and the Health Dashboard — update immediately when you switch.

---

## DynamoDB Support

DB Explorer supports AWS DynamoDB using **PartiQL**.

1. Add a connection, select **DynamoDB**
2. Provide **AWS Region**, **Access Key ID**, and **Secret Access Key**
3. Optionally provide an **Endpoint** for local DynamoDB (e.g. `http://localhost:8000`)
4. Write PartiQL queries: `SELECT * FROM "MyTable" WHERE id = '123'`

> Table names in DynamoDB are case-sensitive and usually require double quotes in PartiQL.

---

## Keyboard Shortcuts

| Action | Shortcut |
|---|---|
| Run Query | `Ctrl+Enter` |
| Run Selected SQL | Select text, then `Ctrl+Enter` |
| Undo | `Ctrl+Z` |
| Redo | `Ctrl+Y` or `Ctrl+Shift+Z` |
| Insert Tab (indent) | `Tab` |

---

## Troubleshooting

**Connection failed**
- Verify the host, port, and firewall rules
- For cloud databases, ensure your IP is whitelisted
- Click **Test Connection** in the connection dialog before saving

**JDBC driver not found**
- The shaded JAR bundles all supported drivers. If using Generic JDBC, provide the path to your driver JAR in the connection settings.

**DynamoDB disconnects**
- AWS credentials may expire. Right-click the connection → Disconnect, then reconnect.

**Health Dashboard shows no Server Activity**
- For Generic JDBC, SQLite, or DynamoDB connections this is expected — those engines don't expose system views via JDBC
- For supported engines, ensure your user has SELECT permission on the relevant system views (e.g. `pg_stat_activity`, `v$session`)

**Theme looks wrong after switching**
- This was fixed in v2.1.0. If you see stale colors, restart the application once to clear any cached state.

---

*Built Adept Software*  | © 2026
