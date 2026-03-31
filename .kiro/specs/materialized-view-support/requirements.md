# Requirements Document

## Introduction

This feature adds materialized views as a first-class object type in the DB Explorer schema tree, at the same level as Tables, Views, Sequences, Indexes, Functions, and Procedures. Users will be able to browse materialized views per schema, inspect their columns, view their data, and export their DDL — with support scoped to the databases that natively support materialized views (PostgreSQL, Oracle, SQL Server).

## Glossary

- **Materialized_View**: A database object that stores the result of a query physically on disk, unlike a regular view which is computed on demand. Natively supported in PostgreSQL, Oracle, and SQL Server. Generic JDBC connections may expose materialized views via the standard `DatabaseMetaData.getTables()` API depending on the driver.
- **Schema_Tree**: The left-panel JTree in DB Explorer that displays connections, schemas, and their child object categories (Tables, Views, Sequences, etc.).
- **Category_Node**: A folder node in the Schema_Tree representing a group of objects of the same type (e.g., "Tables", "Views").
- **Object_Node**: A leaf node in the Schema_Tree representing a single named database object.
- **SchemaExplorerService**: The service class responsible for fetching schema metadata from a JDBC connection.
- **ConnectionListPanel**: The UI component that owns and renders the Schema_Tree.
- **DdlExportService**: The service class responsible for generating DDL strings for database objects.
- **DbIcons**: The class that defines all icons used in the Schema_Tree and menus.

## Requirements

### Requirement 1: Materialized View Category in Schema Tree

**User Story:** As a database developer, I want to see a "Materialized Views" category in the schema tree alongside Tables, Views, and Sequences, so that I can quickly discover and navigate materialized views in my database.

#### Acceptance Criteria

1. THE Schema_Tree SHALL display a "Materialized Views" category node under each schema node, at the same level as Tables, Views, Sequences, Indexes, Functions, and Procedures.
2. WHEN a schema node is expanded, THE ConnectionListPanel SHALL add the "Materialized Views" category node to the list of category folders for that schema.
3. WHILE connected to a PostgreSQL, Oracle, or SQL Server database, THE Schema_Tree SHALL populate the "Materialized Views" category with the names of materialized views in that schema.
4. WHILE connected to a MySQL, SQLite, or DynamoDB database, THE Schema_Tree SHALL display "(not supported)" under the "Materialized Views" category node.
5. WHILE connected to a Generic JDBC database, THE Schema_Tree SHALL attempt to populate the "Materialized Views" category using the standard JDBC metadata API, and SHALL display "(not supported)" only if no materialized views are returned.
5. IF fetching materialized view names fails due to a database error, THEN THE Schema_Tree SHALL display an error message under the "Materialized Views" category node without affecting other category nodes.

### Requirement 2: Fetch Materialized View Names

**User Story:** As a database developer, I want the application to retrieve the list of materialized views from the connected database, so that the schema tree is accurate and up to date.

#### Acceptance Criteria

1. WHEN the "Materialized Views" category node is expanded for a PostgreSQL schema, THE SchemaExplorerService SHALL query `pg_matviews` and return the list of materialized view names in that schema, sorted alphabetically.
2. WHEN the "Materialized Views" category node is expanded for an Oracle schema, THE SchemaExplorerService SHALL query `ALL_MVIEWS` and return the list of materialized view names owned by that schema, sorted alphabetically.
3. WHEN the "Materialized Views" category node is expanded for a SQL Server schema, THE SchemaExplorerService SHALL query `sys.tables` filtered by `type = 'U'` joined with indexed views or query `sys.views` with `OBJECTPROPERTY` to identify materialized (indexed) views, and return the list sorted alphabetically.
4. WHEN the "Materialized Views" category node is expanded for a MySQL, SQLite, or DynamoDB database, THE SchemaExplorerService SHALL return an empty list.
5. WHEN the "Materialized Views" category node is expanded for a Generic JDBC database, THE SchemaExplorerService SHALL call `DatabaseMetaData.getTables()` with table type `"MATERIALIZED VIEW"` and return the resulting list of names sorted alphabetically. IF the result is empty, THE SchemaExplorerService SHALL return an empty list so the UI falls back to "(not supported)".
5. THE SchemaExplorerService SHALL expose a `getMatViews(Connection, DatabaseType, String schema)` method that returns a `List<String>` of materialized view names.

### Requirement 3: Column Inspection for Materialized Views

**User Story:** As a database developer, I want to expand a materialized view node in the schema tree to see its columns, so that I can understand the view's structure without writing a query.

#### Acceptance Criteria

1. WHEN a materialized view Object_Node is expanded in the Schema_Tree, THE ConnectionListPanel SHALL load and display the column names and types for that materialized view.
2. THE SchemaExplorerService SHALL retrieve column metadata for materialized views using the same `getColumns` method used for tables, since materialized views expose column metadata via JDBC `DatabaseMetaData.getColumns`.
3. IF column metadata cannot be retrieved for a materialized view, THEN THE ConnectionListPanel SHALL display "(no columns)" under that node.

### Requirement 4: View Data for Materialized Views

**User Story:** As a database developer, I want to right-click a materialized view and select "View Data" to run a SELECT query against it, so that I can inspect the cached data without manually typing a query.

#### Acceptance Criteria

1. WHEN a user right-clicks a materialized view Object_Node, THE ConnectionListPanel SHALL display a context menu containing a "View Data" option.
2. WHEN the user selects "View Data" from the context menu of a materialized view, THE ConnectionListPanel SHALL generate a `SELECT * FROM <schema>.<matview_name>` SQL statement and invoke the `onViewData` callback.
3. THE generated SELECT statement SHALL qualify the materialized view name with its schema for PostgreSQL, Oracle, and SQL Server connections.

### Requirement 5: DDL Export for Materialized Views

**User Story:** As a database developer, I want to export the DDL of a materialized view, so that I can document or recreate the view in another environment.

#### Acceptance Criteria

1. WHEN a user right-clicks a materialized view Object_Node, THE ConnectionListPanel SHALL include a "Export DDL" option in the context menu.
2. WHEN the user selects "Export DDL" for a materialized view on a PostgreSQL connection, THE DdlExportService SHALL query `pg_get_viewdef` (or the `pg_matviews.definition` column) and return a `CREATE MATERIALIZED VIEW` DDL statement.
3. WHEN the user selects "Export DDL" for a materialized view on an Oracle connection, THE DdlExportService SHALL query `ALL_MVIEWS.QUERY` and return a `CREATE MATERIALIZED VIEW` DDL statement.
4. WHEN the user selects "Export DDL" for a materialized view on a SQL Server connection, THE DdlExportService SHALL query `OBJECT_DEFINITION` and return the view definition.
5. IF DDL cannot be retrieved for a materialized view, THEN THE DdlExportService SHALL return a comment string indicating DDL is unavailable for that object.

### Requirement 6: Distinct Icon for Materialized Views

**User Story:** As a database developer, I want materialized views to have a visually distinct icon in the schema tree, so that I can distinguish them from regular views and tables at a glance.

#### Acceptance Criteria

1. THE DbIcons class SHALL provide a `MAT_VIEW` icon that is visually distinct from the existing `VIEW`, `TABLE`, and other object icons.
2. WHEN a materialized view Object_Node is rendered in the Schema_Tree, THE SchemaTreeCellRenderer SHALL apply the `DbIcons.MAT_VIEW` icon to that node.
3. THE `MAT_VIEW` icon SHALL use a color or symbol that communicates "cached/materialized" — for example, combining the eye motif of the view icon with a storage/layer indicator.
