# Implementation Plan: Materialized View Support

## Overview

Extend the DB Explorer schema tree to treat materialized views as a first-class object type. Changes are additive and localized to four existing classes: `SchemaExplorerService`, `DdlExportService`, `ConnectionListPanel`, and `DbIcons`.

## Tasks

- [x] 1. Add `MAT_VIEW` icon to `DbIcons`
  - Add `public static final Icon MAT_VIEW = px(SIZE, DbIcons::iMatView);` field alongside the existing `VIEW` icon
  - Implement `static void iMatView(Graphics2D g, int s)` painter: reuse the `C_TEAL` background and eye diamond from `iView`, then draw two stacked horizontal rectangles below the eye to represent cached storage
  - _Requirements: 6.1, 6.3_

- [x] 2. Implement `SchemaExplorerService.getMatViews`
  - [x] 2.1 Add `getMatViews(Connection conn, DatabaseType dbType, String schema)` method to `SchemaExplorerService`
    - PostgreSQL: `SELECT matviewname FROM pg_matviews WHERE schemaname = ? ORDER BY matviewname`
    - Oracle: `SELECT mview_name FROM ALL_MVIEWS WHERE owner = ? ORDER BY mview_name`
    - SQL Server: `SELECT name FROM sys.views WHERE schema_id = SCHEMA_ID(?) AND OBJECTPROPERTY(object_id, 'IsIndexed') = 1 ORDER BY name`
    - MySQL / SQLite / DynamoDB: return `Collections.emptyList()`
    - Generic: `DatabaseMetaData.getTables(null, schema, "%", new String[]{"MATERIALIZED VIEW"})`, sort result, return (empty list if driver returns nothing)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [ ]* 2.2 Write property test for unsupported databases (Property 3)
    - **Property 3: Unsupported databases return empty list**
    - **Validates: Requirements 1.4, 2.4**
    - Use jqwik `@Property` over `{MYSQL, SQLITE, DYNAMODB}` enum subset and arbitrary schema strings
    - Mock `Connection` so no real DB is needed

  - [ ]* 2.3 Write property test for alphabetical sort (Property 4)
    - **Property 4: Returned names are sorted alphabetically**
    - **Validates: Requirements 2.1, 2.2, 2.3**
    - Use jqwik `@Property` over arbitrary `List<String>` of names; mock `ResultSet` to return them in arbitrary order; assert result equals `Collections.sort` of the same list

  - [ ]* 2.4 Write unit tests for `getMatViews`
    - Verify correct SQL is issued for PostgreSQL, Oracle, and SQL Server using a mock `Connection` / `PreparedStatement`
    - Verify empty list is returned for MySQL, SQLite, DynamoDB
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 3. Implement `DdlExportService.exportMatViewDdl`
  - [x] 3.1 Add `exportMatViewDdl(Connection conn, DatabaseType dbType, String schema, String name)` method to `DdlExportService`
    - PostgreSQL: `SELECT definition FROM pg_matviews WHERE schemaname = ? AND matviewname = ?` → wrap as `CREATE MATERIALIZED VIEW <schema>.<name> AS\n<definition>;`
    - Oracle: `SELECT QUERY FROM ALL_MVIEWS WHERE OWNER = ? AND MVIEW_NAME = ?` → wrap as `CREATE MATERIALIZED VIEW <schema>.<name> AS\n<query>;`
    - SQL Server: `SELECT OBJECT_DEFINITION(OBJECT_ID(? + '.' + ?))` → return as-is with `;`
    - Others: return `"-- Materialized view DDL not available for " + name`
    - _Requirements: 5.2, 5.3, 5.4, 5.5_

  - [ ]* 3.2 Write property test for DDL output format (Property 8)
    - **Property 8: DDL output is a CREATE MATERIALIZED VIEW statement**
    - **Validates: Requirements 5.2, 5.3, 5.4**
    - Use jqwik `@Property` over arbitrary schema/name strings; mock `ResultSet` to return a non-null definition; assert result starts with `"CREATE MATERIALIZED VIEW"` (case-insensitive) for PostgreSQL, Oracle, SQL Server

  - [ ]* 3.3 Write property test for DDL failure fallback (Property 9)
    - **Property 9: DDL failure returns a comment string**
    - **Validates: Requirements 5.5**
    - Use jqwik `@Property` over unsupported `DatabaseType` values; assert result is non-null and starts with `"--"`

  - [ ]* 3.4 Write unit tests for `exportMatViewDdl`
    - Verify DDL wrapping for each supported database using mock `ResultSet`
    - Verify fallback comment for unsupported databases
    - _Requirements: 5.2, 5.3, 5.4, 5.5_

- [x] 4. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Wire materialized views into `ConnectionListPanel`
  - [x] 5.1 Add `CAT_MAT_VIEWS = "Materialized Views"` constant to `ConnectionListPanel`
    - Insert after `CAT_VIEWS` in the constant declarations
    - _Requirements: 1.1, 1.2_

  - [x] 5.2 Insert `CAT_MAT_VIEWS` into the categories array in `loadCategoriesForSchema`
    - Position: after `CAT_VIEWS`, before `CAT_SEQUENCES`
    - _Requirements: 1.1, 1.2_

  - [x] 5.3 Add `case CAT_MAT_VIEWS -> schemaExplorer.getMatViews(conn, dbType, cn.schema);` to the switch in `loadObjectsForCategory`
    - _Requirements: 1.3, 1.4, 1.5, 1.6_

  - [x] 5.4 Add `isMatView()` helper to the `ObjectNode` record
    - `public boolean isMatView() { return CAT_MAT_VIEWS.equals(category); }`
    - _Requirements: 4.1, 5.1_

  - [x] 5.5 Add context menu for materialized view `ObjectNode` in the right-click popup handler
    - Show "View Data" item: generate `SELECT * FROM <schema>.<name>` and invoke `onViewData` callback (same schema-qualification logic as tables, skip MySQL branch)
    - Show "Export DDL" item: invoke `onExportTable` with key `"MAT_VIEW_DDL\0<schema>\0<name>"`
    - Do NOT include INSERT/UPDATE/CSV export items (materialized views are read-only)
    - _Requirements: 4.1, 4.2, 4.3, 5.1_

  - [x] 5.6 Add `case CAT_MAT_VIEWS -> DbIcons.MAT_VIEW;` to the icon switch in `SchemaTreeCellRenderer`
    - _Requirements: 6.2_

  - [ ]* 5.7 Write property test for SELECT SQL schema-qualification (Property 7)
    - **Property 7: SELECT SQL is correctly schema-qualified**
    - **Validates: Requirements 4.2, 4.3**
    - Use jqwik `@Property` over arbitrary non-null, non-empty schema and name strings; assert generated SQL equals `"SELECT * FROM " + schema + "." + name`

  - [ ]* 5.8 Write property test for renderer icon (Property 10)
    - **Property 10: Renderer applies MAT_VIEW icon to materialized view nodes**
    - **Validates: Requirements 6.2**
    - Use jqwik `@Property` over arbitrary `ObjectNode` instances with `category = CAT_MAT_VIEWS`; assert `SchemaTreeCellRenderer` sets icon to `DbIcons.MAT_VIEW` (reference equality)

  - [ ]* 5.9 Write unit tests for `ConnectionListPanel` context menu
    - Verify "View Data" and "Export DDL" items appear for a mat view `ObjectNode`
    - Verify INSERT/UPDATE/CSV items do NOT appear for a mat view `ObjectNode`
    - _Requirements: 4.1, 5.1_

- [x] 6. Wire `MAT_VIEW_DDL` routing in `MainFrame`
  - Locate the `onExportTable` callback handler in `MainFrame` that dispatches on the key prefix
  - Add a branch for `"MAT_VIEW_DDL"` prefix: parse `schema` and `name` from the key, call `DdlExportService.exportMatViewDdl`, and display the result in the DDL export dialog (same flow as the existing `"DDL"` branch)
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 7. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Property tests use jqwik 1.8.4 (already in `pom.xml`) with `@Property` annotations
- Unit tests use JUnit 5 (already in `pom.xml`)
- All changes are additive — no existing behavior is modified
