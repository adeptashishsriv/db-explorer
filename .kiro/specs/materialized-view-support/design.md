# Design Document: Materialized View Support

## Overview

This feature adds materialized views as a first-class object type in the DB Explorer schema tree. Materialized views are physically stored query results supported natively by PostgreSQL, Oracle, and SQL Server. The feature extends three existing classes — `SchemaExplorerService`, `ConnectionListPanel`, and `DdlExportService` — plus `DbIcons`, following the exact same patterns already used for Tables, Views, Sequences, Indexes, Functions, and Procedures.

The scope is deliberately narrow: no new architectural layers are introduced. The changes are additive and localized to the four classes above.

### Database Support Matrix

| Database   | Materialized Views | Metadata Source                          |
|------------|--------------------|------------------------------------------|
| PostgreSQL | Yes                | `pg_matviews`                            |
| Oracle     | Yes                | `ALL_MVIEWS`                             |
| SQL Server | Yes                | `sys.views` + `OBJECTPROPERTY(..., 'IsIndexed')` |
| MySQL      | No                 | Return empty list → "(not supported)"    |
| SQLite     | No                 | Return empty list → "(not supported)"    |
| DynamoDB   | No                 | Return empty list → "(not supported)"    |
| Generic    | Try JDBC fallback  | `DatabaseMetaData.getTables("MATERIALIZED VIEW")` |

---

## Architecture

The feature follows the existing layered architecture without modification:

```
ConnectionListPanel (UI / Swing EDT)
        │
        │  SwingWorker (background thread)
        ▼
SchemaExplorerService.getMatViews(conn, dbType, schema)
        │
        ▼
JDBC Connection → Database-specific SQL or DatabaseMetaData API
```

```
DdlExportService.exportMatViewDdl(conn, dbType, schema, name)
        │
        ▼
Database-specific DDL query (pg_matviews.definition, ALL_MVIEWS.QUERY, OBJECT_DEFINITION)
```

The `ConnectionListPanel` already uses a `SwingWorker` per category to load objects lazily. The "Materialized Views" category plugs into this same mechanism via a new `CAT_MAT_VIEWS` constant and a new branch in the `loadObjectsForCategory` switch expression.

---

## Components and Interfaces

### 1. `SchemaExplorerService` — new method

```java
/**
 * Returns materialized view names for the given schema, sorted alphabetically.
 * Returns an empty list for databases that do not support materialized views
 * (MySQL, SQLite, DynamoDB). For Generic JDBC, attempts the standard
 * "MATERIALIZED VIEW" table type and returns whatever the driver provides.
 */
public List<String> getMatViews(Connection conn, DatabaseType dbType, String schema)
        throws SQLException
```

Implementation strategy per database type:

- **PostgreSQL**: `SELECT matviewname FROM pg_matviews WHERE schemaname = ? ORDER BY matviewname`
- **Oracle**: `SELECT mview_name FROM ALL_MVIEWS WHERE owner = ? ORDER BY mview_name`
- **SQL Server**: `SELECT name FROM sys.views WHERE schema_id = SCHEMA_ID(?) AND OBJECTPROPERTY(object_id, 'IsIndexed') = 1 ORDER BY name`
- **MySQL / SQLite / DynamoDB**: return `Collections.emptyList()`
- **Generic**: `DatabaseMetaData.getTables(null, schema, "%", new String[]{"MATERIALIZED VIEW"})`

### 2. `DdlExportService` — new method

```java
/**
 * Generates a CREATE MATERIALIZED VIEW DDL string for the named object.
 * Falls back to a comment if DDL cannot be retrieved.
 */
public String exportMatViewDdl(Connection conn, DatabaseType dbType,
                                String schema, String name) throws SQLException
```

Implementation strategy per database type:

- **PostgreSQL**: `SELECT definition FROM pg_matviews WHERE schemaname = ? AND matviewname = ?` → wrap in `CREATE MATERIALIZED VIEW <schema>.<name> AS\n<definition>;`
- **Oracle**: `SELECT QUERY FROM ALL_MVIEWS WHERE OWNER = ? AND MVIEW_NAME = ?` → wrap in `CREATE MATERIALIZED VIEW <schema>.<name> AS\n<query>;`
- **SQL Server**: `SELECT OBJECT_DEFINITION(OBJECT_ID(? + '.' + ?))` → return as-is with `;`
- **Others**: return `-- Materialized view DDL not available for <name>`

### 3. `ConnectionListPanel` — changes

- Add constant: `static final String CAT_MAT_VIEWS = "Materialized Views";`
- In `loadCategoriesForSchema`: insert `CAT_MAT_VIEWS` into the categories array (after `CAT_VIEWS`, before `CAT_SEQUENCES`).
- In `loadObjectsForCategory` switch: add `case CAT_MAT_VIEWS -> schemaExplorer.getMatViews(conn, dbType, cn.schema);`
- In the right-click popup for `ObjectNode`: extend the existing `isTable()` check to also handle materialized views. Materialized view nodes get "View Data" and "Export DDL" menu items (same as tables, but DDL export routes through `exportMatViewDdl`).
- In `SchemaTreeCellRenderer`: add `case CAT_MAT_VIEWS -> DbIcons.MAT_VIEW;` to the icon switch.

A new helper method `isMatView()` is added to `ObjectNode`:

```java
record ObjectNode(String name, String category, String schema, ConnectionInfo connectionInfo) {
    public boolean isTable()   { return CAT_TABLES.equals(category); }
    public boolean isMatView() { return CAT_MAT_VIEWS.equals(category); }
}
```

The context menu for materialized view nodes mirrors the table context menu but omits INSERT/UPDATE export options (materialized views are read-only). The "Export DDL" action encodes the key as `"MAT_VIEW_DDL\0<schema>\0<name>"` so `MainFrame` can route it to `exportMatViewDdl`.

### 4. `DbIcons` — new icon

```java
public static final Icon MAT_VIEW = px(SIZE, DbIcons::iMatView);
```

The `iMatView` painter combines the eye motif from `iView` with a layered-storage indicator (two stacked rectangles beneath the eye diamond), using the existing `C_TEAL` background. This visually communicates "view + cached storage".

---

## Data Models

No new persistent data models are required. The feature operates entirely on live JDBC metadata.

### Tree Node Model (existing, extended)

The existing `ObjectNode` record in `ConnectionListPanel` is extended with `isMatView()`. No new record types are needed.

```
CategoryNode("Materialized Views", schema, connectionInfo)
    └── ObjectNode(name, "Materialized Views", schema, connectionInfo)
            └── "<column_name> (<type>(<size>))"   ← string leaf, same as tables
```

### DDL Export Routing

The existing `onExportTable` callback uses a `"\0"`-delimited key string. A new prefix `"MAT_VIEW_DDL"` is added to distinguish materialized view DDL exports from table DDL exports:

| Prefix         | Handler                                  |
|----------------|------------------------------------------|
| `DDL`          | `DdlExportService.exportTableDdl`        |
| `INSERT`       | `TableDataExportService` (INSERT mode)   |
| `UPDATE`       | `TableDataExportService` (UPDATE mode)   |
| `CSV`          | `TableDataExportService` (CSV mode)      |
| `MAT_VIEW_DDL` | `DdlExportService.exportMatViewDdl` *(new)* |

Column metadata for materialized views is fetched via the existing `SchemaExplorerService.getColumns` method, which uses `DatabaseMetaData.getColumns` — this works for materialized views on all supported databases since they expose column metadata through the standard JDBC API.


---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Category node presence

*For any* schema node expanded in the ConnectionListPanel, the resulting list of category folder nodes SHALL contain exactly one node with the label "Materialized Views".

**Validates: Requirements 1.1, 1.2**

---

### Property 2: Supported databases return mat view names

*For any* PostgreSQL, Oracle, or SQL Server connection and any schema that contains at least one materialized view, `SchemaExplorerService.getMatViews` SHALL return a non-empty list containing the names of those materialized views.

**Validates: Requirements 1.3, 2.1, 2.2, 2.3**

---

### Property 3: Unsupported databases return empty list

*For any* MySQL, SQLite, or DynamoDB connection and any schema string, `SchemaExplorerService.getMatViews` SHALL return an empty list.

**Validates: Requirements 1.4, 2.4**

---

### Property 4: Returned names are sorted alphabetically

*For any* connection type and schema, the list returned by `SchemaExplorerService.getMatViews` SHALL be in ascending alphabetical order (i.e., equal to `Collections.sort` applied to the same list).

**Validates: Requirements 2.1, 2.2, 2.3**

---

### Property 5: Column loading works for materialized view nodes

*For any* materialized view `ObjectNode`, calling `SchemaExplorerService.getColumns` with the node's schema and name SHALL return the same column metadata as calling it for a regular table with the same schema and name structure.

**Validates: Requirements 3.1, 3.2**

---

### Property 6: Context menu contains required items for materialized view nodes

*For any* materialized view `ObjectNode`, the right-click context menu SHALL contain both a "View Data" item and an "Export DDL" item.

**Validates: Requirements 4.1, 5.1**

---

### Property 7: SELECT SQL is correctly schema-qualified

*For any* materialized view `ObjectNode` with a non-null, non-empty schema and name, the SQL string generated by the "View Data" action SHALL be exactly `"SELECT * FROM <schema>.<name>"`.

**Validates: Requirements 4.2, 4.3**

---

### Property 8: DDL output is a CREATE MATERIALIZED VIEW statement

*For any* PostgreSQL, Oracle, or SQL Server connection and any materialized view name, `DdlExportService.exportMatViewDdl` SHALL return a string that starts with `"CREATE MATERIALIZED VIEW"` (case-insensitive).

**Validates: Requirements 5.2, 5.3, 5.4**

---

### Property 9: DDL failure returns a comment string

*For any* connection and materialized view name for which DDL cannot be retrieved (unsupported database or query failure), `DdlExportService.exportMatViewDdl` SHALL return a non-null string that starts with `"--"`.

**Validates: Requirements 5.5**

---

### Property 10: Renderer applies MAT_VIEW icon to materialized view nodes

*For any* `ObjectNode` whose category is `"Materialized Views"`, the `SchemaTreeCellRenderer` SHALL set the node's icon to `DbIcons.MAT_VIEW` (reference equality).

**Validates: Requirements 6.2**

---

## Error Handling

| Scenario | Behavior |
|---|---|
| `getMatViews` throws `SQLException` | `SwingWorker.done()` catches the exception; the category node shows `"Error: <message>"`. Other category nodes are unaffected. |
| `getColumns` throws `SQLException` for a mat view | The mat view node shows `"(no columns)"`. |
| `exportMatViewDdl` cannot retrieve DDL | Returns `"-- Materialized view DDL not available for <name>"`. |
| Database does not support mat views | `getMatViews` returns empty list; UI shows `"(not supported)"`. |
| Generic JDBC driver returns no rows for `"MATERIALIZED VIEW"` type | `getMatViews` returns empty list; UI shows `"(not supported)"`. |

All error paths follow the existing pattern in `ConnectionListPanel`: errors are caught in `SwingWorker.done()`, displayed as a child node string, and never propagate to the EDT as uncaught exceptions.

---

## Testing Strategy

### Dual Testing Approach

Both unit tests and property-based tests are required. Unit tests cover specific examples and integration points; property tests verify universal correctness across generated inputs.

### Property-Based Testing

The project already includes **jqwik 1.8.4** as a test dependency. Each property test uses `@Property` with at least 100 tries (jqwik default is 1000, which is fine).

Each property test is tagged with a comment in the format:
`// Feature: materialized-view-support, Property <N>: <property_text>`

**Property tests to implement** (one test per property):

| Test class | Property | jqwik annotation |
|---|---|---|
| `SchemaExplorerServicePropertyTest` | P3: Unsupported DBs return empty | `@Property` over `DatabaseType` enum subset |
| `SchemaExplorerServicePropertyTest` | P4: Results are sorted | `@Property` over arbitrary `List<String>` of names |
| `DdlExportServicePropertyTest` | P8: DDL starts with CREATE MATERIALIZED VIEW | `@Property` over schema/name strings |
| `DdlExportServicePropertyTest` | P9: Failure returns comment string | `@Property` over unsupported DB types |
| `ConnectionListPanelPropertyTest` | P7: SELECT SQL is schema-qualified | `@Property` over schema/name strings |
| `ConnectionListPanelPropertyTest` | P10: Renderer uses MAT_VIEW icon | `@Property` over arbitrary ObjectNode mat view instances |

Properties P1, P2, P5, P6 require a live JDBC connection and are better covered by integration/unit tests.

### Unit Tests

Unit tests (JUnit 5) cover:

- `SchemaExplorerServiceTest`: verify `getMatViews` returns the correct SQL query string for each supported `DatabaseType` (mock `Connection` / `PreparedStatement`).
- `DdlExportServiceTest`: verify DDL wrapping logic for each supported database using mock `ResultSet`.
- `ConnectionListPanelTest`: verify context menu items for a mat view `ObjectNode` vs. a table `ObjectNode`.
- `DbIconsTest`: verify `DbIcons.MAT_VIEW` is non-null and has dimensions `SIZE × SIZE` (example test for Requirement 6.1).

### Test Configuration

```java
// Example property test skeleton
@Property(tries = 100)
void unsupportedDatabasesReturnEmptyList(
        @ForAll @From("unsupportedTypes") DatabaseType dbType,
        @ForAll @StringLength(min=1, max=64) String schema) throws SQLException {
    // Feature: materialized-view-support, Property 3: Unsupported databases return empty list
    SchemaExplorerService svc = new SchemaExplorerService();
    List<String> result = svc.getMatViews(mockConnection(), dbType, schema);
    assertThat(result).isEmpty();
}

@Property(tries = 100)
void returnedNamesAreSorted(@ForAll List<@AlphaChars @StringLength(min=1, max=32) String> names)
        throws SQLException {
    // Feature: materialized-view-support, Property 4: Returned names are sorted alphabetically
    // Arrange: mock a ResultSet that returns `names` in arbitrary order
    // Assert: result equals Collections.sort(names)
    List<String> sorted = new ArrayList<>(names);
    Collections.sort(sorted);
    assertThat(result).isEqualTo(sorted);
}
```
