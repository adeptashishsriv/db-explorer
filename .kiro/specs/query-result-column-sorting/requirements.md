# Requirements Document

## Introduction

This feature adds interactive column sorting to the query results JTable in the DB Explorer application. Clicking a column header toggles the sort order through three states: ascending → descending → unsorted (original fetch order). Sort comparators are type-aware: numeric columns sort numerically, date/timestamp columns sort chronologically, and all other columns sort lexicographically. The sort operates on the rows currently loaded in the table model and does not re-execute the SQL query.

## Glossary

- **ResultPanel**: The Swing panel (`com.dbexplorer.ui.ResultPanel`) that hosts the JTable displaying query output.
- **SortableTableModel**: A table model wrapper that maintains a sort index over the underlying row data and supports type-aware comparison.
- **ColumnSortState**: The three-state sort cycle for a single column: ASCENDING → DESCENDING → UNSORTED.
- **ColumnType**: The inferred data type of a column used to select the appropriate comparator — one of NUMERIC, DATETIME, or TEXT.
- **SortIndicator**: The visual arrow rendered in the column header to communicate the current sort state (↑ ascending, ↓ descending, absent when unsorted).
- **LazyQueryResult**: The existing class (`com.dbexplorer.model.LazyQueryResult`) that streams rows from the database and exposes `int[] columnTypes` (java.sql.Types values).

---

## Requirements

### Requirement 1: Toggle Sort on Column Header Click

**User Story:** As a database developer, I want to click a column header in the query results table to sort by that column, so that I can quickly find and compare rows without rewriting my SQL query.

#### Acceptance Criteria

1. WHEN the user clicks a column header in the results JTable, THE SortableTableModel SHALL cycle the sort state for that column through ASCENDING → DESCENDING → UNSORTED.
2. WHEN a column's sort state transitions to ASCENDING or DESCENDING, THE SortableTableModel SHALL sort all currently loaded rows using the comparator appropriate for that column's ColumnType.
3. WHEN a column's sort state transitions to UNSORTED, THE SortableTableModel SHALL restore the rows to their original fetch order.
4. WHEN the user clicks a different column header while another column is already sorted, THE SortableTableModel SHALL reset the previously sorted column to UNSORTED and begin the ASCENDING → DESCENDING → UNSORTED cycle on the newly clicked column.
5. THE SortableTableModel SHALL preserve the sort state and order when new rows are appended via lazy loading (scroll-triggered fetch).

---

### Requirement 2: Type-Aware Sort Comparators

**User Story:** As a database developer, I want numeric and date columns to sort by value rather than alphabetically, so that the sort order reflects the actual data semantics.

#### Acceptance Criteria

1. WHEN a column's ColumnType is NUMERIC, THE SortableTableModel SHALL compare cell values as double-precision floating-point numbers.
2. WHEN a column's ColumnType is DATETIME, THE SortableTableModel SHALL compare cell values as temporal values using chronological order.
3. WHEN a column's ColumnType is TEXT, THE SortableTableModel SHALL compare cell values using case-insensitive lexicographic order.
4. WHEN a cell value is null or an empty string, THE SortableTableModel SHALL treat that value as less than any non-null, non-empty value during comparison (nulls sort last in ASCENDING, first in DESCENDING).
5. WHEN a cell value cannot be parsed as the expected ColumnType (e.g. a non-numeric string in a NUMERIC column), THE SortableTableModel SHALL fall back to case-insensitive lexicographic comparison for that value.

---

### Requirement 3: Column Type Inference

**User Story:** As a database developer, I want the sort to automatically use the correct comparator for each column, so that I do not need to configure anything manually.

#### Acceptance Criteria

1. WHEN `LazyQueryResult` provides a `java.sql.Types` value for a column that maps to an integer, long, float, double, decimal, or numeric SQL type, THE SortableTableModel SHALL assign ColumnType NUMERIC to that column.
2. WHEN `LazyQueryResult` provides a `java.sql.Types` value for a column that maps to a date, time, or timestamp SQL type, THE SortableTableModel SHALL assign ColumnType DATETIME to that column.
3. WHEN `LazyQueryResult` provides a `java.sql.Types` value that does not map to NUMERIC or DATETIME, THE SortableTableModel SHALL assign ColumnType TEXT to that column.
4. WHEN column type information is unavailable (e.g. DynamoDB results or non-lazy query paths), THE SortableTableModel SHALL default to ColumnType TEXT for all columns.

---

### Requirement 4: Sort Indicator in Column Header

**User Story:** As a database developer, I want to see a visual indicator in the column header showing the current sort direction, so that I always know which column is sorted and in which direction.

#### Acceptance Criteria

1. WHEN a column's sort state is ASCENDING, THE ResultPanel SHALL render an upward-pointing arrow (↑) in that column's header.
2. WHEN a column's sort state is DESCENDING, THE ResultPanel SHALL render a downward-pointing arrow (↓) in that column's header.
3. WHEN a column's sort state is UNSORTED, THE ResultPanel SHALL render no sort arrow in that column's header.
4. WHEN the sort state changes, THE ResultPanel SHALL repaint the table header within the same EDT event dispatch cycle as the model update.

---

### Requirement 5: Sort Stability and Lazy-Load Compatibility

**User Story:** As a database developer, I want sorting to remain consistent as more rows are loaded via scrolling, so that the table does not jump or lose my sort context.

#### Acceptance Criteria

1. WHEN new rows are appended to the SortableTableModel during a lazy-load page fetch, THE SortableTableModel SHALL insert those rows into the correct sorted position if a sort is currently active.
2. WHEN new rows are appended and no sort is active, THE SortableTableModel SHALL append the rows at the end of the table in fetch order.
3. THE SortableTableModel SHALL use a stable sort algorithm so that rows with equal values in the sorted column retain their relative fetch order.
4. IF a sort is active and a new page of rows is appended, THEN THE SortableTableModel SHALL complete the merge without blocking the EDT for more than 16 ms per page of 500 rows.

---

### Requirement 6: Sort Reset on New Query

**User Story:** As a database developer, I want the sort state to reset when I run a new query, so that stale sort indicators from a previous result set do not carry over.

#### Acceptance Criteria

1. WHEN a new query result is loaded into the ResultPanel, THE SortableTableModel SHALL reset all column sort states to UNSORTED.
2. WHEN a new query result is loaded, THE ResultPanel SHALL remove all sort indicator arrows from all column headers.
