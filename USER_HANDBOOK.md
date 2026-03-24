# DB Explorer User Handbook

**DB Explorer** is a lightweight, modern database client designed for developers who need quick access to their SQL and NoSQL data. It supports multiple database types including PostgreSQL, MySQL, Oracle, SQL Server, and AWS DynamoDB.

## Table of Contents
1. [Getting Started](#getting-started)
2. [Supported Databases](#supported-databases)
3. [Managing Connections](#managing-connections)
4. [Browsing Schemas & Tables](#browsing-schemas--tables)
5. [Query Editor](#query-editor)
6. [Viewing Results](#viewing-results)
7. [DynamoDB Support](#dynamodb-support)
8. [Keyboard Shortcuts](#keyboard-shortcuts)
9. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Prerequisites
*   Java Runtime Environment (JRE) 17 or higher.
*   Internet connection (for downloading dependencies if building from source, or accessing remote databases).

### Launching the Application
1.  Run the application JAR file: `java -jar db-explorer-1.0.0.jar`
2.  On Windows/Mac/Linux, you may be able to double-click the JAR if Java is set up correctly.

Upon launch, you will see the **Welcome Screen** with quick tips and shortcuts.

---

## Supported Databases

DB Explorer currently supports the following database systems:

*   **PostgreSQL**: Port 5432
*   **MySQL**: Port 3306
*   **Oracle**: Port 1521
*   **SQL Server**: Port 1433
*   **AWS DynamoDB**: (NoSQL)

---

## Managing Connections

### Adding a Connection
1.  Click the **Add Connection** button (`➕`) in the top toolbar.
2.  **Name**: Enter a friendly name for this connection (e.g., "Local Postgres").
3.  **Database Type**: Select your database vendor.
4.  **JDBC Details** (for SQL DBs):
    *   **Host**: IP address or hostname (e.g., `localhost`).
    *   **Port**: Default ports are auto-filled but can be changed.
    *   **Database**: The specific database name to connect to.
    *   **Username/Password**: Your credentials.
5.  **Test Connection**: Click to verify connectivity before saving.
6.  Click **Save**.

### Editing or Deleting
*   **Edit**: Right-click a connection in the left sidebar and select **Edit**.
*   **Delete**: Right-click a connection and select **Delete**.

> **Note**: Your passwords and AWS keys are encrypted using a machine-specific key before being saved to your local disk (`~/.dbexplorer/connections.json`).

---

## Browsing Schemas & Tables

The left panel is the **Connection Tree**.

1.  **Connect**: Double-click a connection name (or right-click -> "Connect").
    *   A green indicator will appear when connected.
2.  **Expand**: Click the arrow (or double-click) to expand the node.
    *   **SQL Databases**: You will see a list of **Schemas**.
        *   Expand a schema to see folders for **Tables**, **Views**, **Sequences**, **Indexes**, **Functions**, and **Procedures**.
        *   **Columns**: Expand any **Table** node to see a full list of its columns and their data types (e.g., `id (int4)`, `name (varchar)`).
    *   **DynamoDB**: You will see a list of **Tables** directly.
3.  **View Data**:
    *   Right-click on any **Table** node and select **View Data**.
    *   This automatically opens a new tab and runs a `SELECT * FROM table` query, displaying the data instantly.
4.  **Refresh**: Right-click the connection and select "Refresh Schemas" to reload metadata.

---

## Query Editor

DB Explorer provides a multi-tabbed SQL editor with syntax highlighting.

### Opening a Tab
*   **New Blank Tab**: Click the **New Tab** button (`📄`) in the toolbar.
*   **From Connection**: Right-click a connection in the tree and select **Open Query Tab**. This opens a tab automatically bound to that connection.
*   **View Data**: Use the right-click "View Data" feature on a table to open a tab pre-filled with a selection query.

### Writing Queries
*   Type your SQL in the top editor pane.
*   **Syntax Highlighting**: Keywords (SELECT, FROM, WHERE, etc.) are highlighted in blue.
*   **Auto-Indent**: Pressing `Tab` inserts 4 spaces.

### Running Queries
*   **Run**: Click the **Run Query** button (`▶`) in the toolbar.
*   **Shortcut**: Press `Ctrl + Enter` (or `Cmd + Enter` on macOS).
*   **Status**: The bottom status bar shows execution time and row counts.

### Explain Plan
To see how the database will execute your query:
1.  Type your query.
2.  Click the **Explain Plan** button (`📊`).
3.  The plan output will appear in the "Explain Plan" tab at the bottom of the editor.
    *   *Note: Not supported for DynamoDB.*

---

## Viewing Results

Results appear in the bottom half of the Query Tab.

*   **Lazy Loading**: For large result sets, DB Explorer loads rows in pages (default 200 rows).
    *   **Scroll Down**: Scroll to the bottom of the result table to automatically fetch the next page.
    *   Status label shows "Fetching..." when loading more rows.
*   **Grid**: Results are displayed in a resizeable grid.
*   **Console/Logs**: The bottom-most panel (collapsible) shows a history of executed queries, timings, and any errors. Use the **Clear Console** (`🗑`) button to wipe it.

---

## DynamoDB Support

DB Explorer supports AWS DynamoDB using **PartiQL** (SQL-compatible query language for DynamoDB).

1.  **Add Connection**: Select "DynamoDB".
    *   Provide **AWS Region** (e.g., `us-east-1`).
    *   Provide **Access Key ID** and **Secret Access Key**.
    *   (Optional) **Endpoint**: Use for local DynamoDB (e.g., `http://localhost:8000`).
2.  **Querying**:
    *   Write PartiQL statements: `SELECT * FROM "MyTable" WHERE id = '123'`.
    *   *Note: Table names in DynamoDB are case-sensitive and often require double quotes in PartiQL.*
3.  **Results**: Data types (Map, List, Boolean) are formatted as strings in the result grid.

---

## Keyboard Shortcuts

| Action | Shortcut |
| :--- | :--- |
| **Run Query** | `Ctrl + Enter` |
| **Undo** | `Ctrl + Z` |
| **Redo** | `Ctrl + Y` (or `Ctrl + Shift + Z`) |
| **New Tab** | (Toolbar Button) |
| **Switch Theme** | (Toolbar Dropdown) |

---

## Troubleshooting

*   **"JDBC driver not found"**: Ensure the dependencies are correctly bundled in the JAR or available on the classpath.
*   **Connection Failed**:
    *   Check if the database server is running.
    *   Verify Host/Port and Firewall settings.
    *   For Cloud DBs, ensure your IP is whitelisted.
*   **DynamoDB "Not Connected"**: AWS credentials expire or network issues may disconnect the client. Right-click and "Disconnect", then "Connect" again.
*   **Themes**: If the UI looks incorrect after switching themes, try restarting the application.

---

**Built by Ashish Srivastava** | © 2026 Adept Software
