# Implementation Plan: Query Result Column Sorting

## Overview

Implement interactive, type-aware column sorting for the query results JTable. Introduces `ColumnSortState` and `ColumnType` enums, a new `SortableTableModel` class, a `SortIndicatorHeaderRenderer`, and wires everything into the existing `ResultPanel`.

## Tasks

- [x] 1. Create `ColumnSortState` and `ColumnType` enums
  - Create `src/main/java/com/dbexplorer/ui/ColumnSortState.java` with values `ASCENDING`, `DESCENDING`, `UNSORTED` and a `next()` method cycling ASCENDING → DESCENDING → UNSORTED
  - Create `src/main/java/com/dbexplorer/ui/ColumnType.java` with values `NUMERIC`, `DATETIME`, `TEXT` and a static `fromSqlType(int sqlType)` factory mapping `java.sql.Types` constants
  - _Requirements: 1.1, 3.1, 3.2, 3.3_

  - [ ]* 1.1 Write property test for `ColumnType.fromSqlType` SQL type mapping
    - **Property 7: SQL type mapping is exhaustive and correct**
    - **Validates: Requirements 3.1, 3.2, 3.3**

- [x] 2. Implement `SortableTableModel`
  - [x] 2.1 Create `src/main/java/com/dbexplorer/ui/SortableTableModel.java` extending `DefaultTableModel`
    - Add fields: `sortedColumn` (int, -1 = none), `sortState` (ColumnSortState), `columnTypes` (ColumnType[]), `sortIndex` (List<Integer>)
    - Implement `initColumns(List<String> columns, ColumnType[] types)` — sets up columns and resets sort state
    - Override `getValueAt(int row, int col)` to use `sortIndex` for indirection
    - Override `getRowCount()` to return `sortIndex.size()`
    - Implement `resetSort()` — resets `sortedColumn` to -1, `sortState` to UNSORTED, rebuilds identity `sortIndex`
    - _Requirements: 1.1, 1.3, 6.1_

  - [ ]* 2.2 Write property test for sort state cycle
    - **Property 1: Sort state cycle is always ASCENDING → DESCENDING → UNSORTED**
    - **Validates: Requirements 1.1**

  - [ ]* 2.3 Write property test for reset clears all sort states
    - **Property 12: Reset clears all sort states**
    - **Validates: Requirements 6.1**

  - [x] 2.4 Implement type-aware comparators inside `SortableTableModel`
    - Implement NUMERIC comparator: parse as `double`, null/empty as -∞, parse failure falls back to `compareToIgnoreCase`
    - Implement DATETIME comparator: try ISO_LOCAL_DATE_TIME, ISO_LOCAL_DATE, ISO_LOCAL_TIME; null/empty as -∞; parse failure falls back to `compareToIgnoreCase`
    - Implement TEXT comparator: `compareToIgnoreCase`, null/empty as -∞
    - DESCENDING negates the ASCENDING comparator result
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [ ]* 2.5 Write property test for sorted rows in non-decreasing comparator order
    - **Property 2: Sorted rows are in non-decreasing comparator order**
    - **Validates: Requirements 1.2, 2.1, 2.2, 2.3**

  - [ ]* 2.6 Write property test for null/empty values sort position
    - **Property 6: Null/empty values sort last in ASCENDING, first in DESCENDING**
    - **Validates: Requirements 2.4**

  - [ ]* 2.7 Write property test for numeric sort vs lexicographic
    - **Property 5: Numeric sort differs from lexicographic sort on numeric strings**
    - **Validates: Requirements 2.1**

  - [x] 2.8 Implement `cycleSort(int columnIndex)` in `SortableTableModel`
    - Cycle sort state for the clicked column; reset previously sorted column to UNSORTED when a different column is clicked
    - When transitioning to ASCENDING or DESCENDING, sort `sortIndex` using `Collections.sort` (TimSort — stable) with the column's comparator
    - When transitioning to UNSORTED, rebuild identity `sortIndex`
    - Guard with bounds check; no-op on out-of-range index
    - Fire `fireTableDataChanged()` after each sort
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [ ]* 2.9 Write property test for sort then unsort restores original fetch order
    - **Property 3: Sort then unsort restores original fetch order**
    - **Validates: Requirements 1.3**

  - [ ]* 2.10 Write property test for only one column sorted at a time
    - **Property 4: Only one column sorted at a time**
    - **Validates: Requirements 1.4**

  - [ ]* 2.11 Write property test for sort stability
    - **Property 11: Sort is stable — equal keys preserve relative fetch order**
    - **Validates: Requirements 5.3**

- [x] 3. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement `appendRows` with lazy-load merge in `SortableTableModel`
  - [x] 4.1 Implement `appendRows(List<String[]> page)` in `SortableTableModel`
    - When no sort is active: add rows to underlying storage and append their indices to `sortIndex` in order
    - When a sort is active: add rows to underlying storage, then binary-search each new row index into `sortIndex` using `Collections.binarySearch` with the active comparator
    - No-op on empty page; fire `fireTableDataChanged()` once after all rows are inserted
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [ ]* 4.2 Write property test for append preserves sorted order
    - **Property 9: Appending rows preserves sorted order**
    - **Validates: Requirements 5.1**

  - [ ]* 4.3 Write property test for append without sort preserves fetch order
    - **Property 10: Appending rows without active sort preserves fetch order**
    - **Validates: Requirements 5.2**

- [x] 5. Implement `SortIndicatorHeaderRenderer`
  - Create `src/main/java/com/dbexplorer/ui/SortIndicatorHeaderRenderer.java` implementing `TableCellRenderer`
  - Wrap the default header renderer; append ` ↑` when ASCENDING, ` ↓` when DESCENDING, plain label when UNSORTED
  - Query `SortableTableModel` for the current sort state of each column
  - _Requirements: 4.1, 4.2, 4.3_

  - [ ]* 5.1 Write property test for renderer reflects sort state
    - **Property 8: Sort indicator renderer reflects sort state**
    - **Validates: Requirements 4.1, 4.2, 4.3**

- [x] 6. Wire `SortableTableModel` and `SortIndicatorHeaderRenderer` into `ResultPanel`
  - [x] 6.1 Replace `DefaultTableModel tableModel` field with `SortableTableModel tableModel` in `ResultPanel`
    - Update field declaration and constructor initialisation
    - _Requirements: 1.1, 6.1_

  - [x] 6.2 Install `MouseListener` on `table.getTableHeader()` in `ResultPanel`
    - On click, resolve the column index via `columnAtPoint`, call `tableModel.cycleSort(columnIndex)`, then repaint the table header within the same EDT dispatch cycle
    - _Requirements: 1.1, 4.4_

  - [x] 6.3 Update `displayLazyResultWithFirstPage` and `displayLazyResult` in `ResultPanel`
    - Call `tableModel.resetSort()` before populating columns
    - Derive `ColumnType[]` from `lazyResult.columnTypes` using `ColumnType.fromSqlType`
    - Pass columns and types to `tableModel.initColumns`
    - Install `SortIndicatorHeaderRenderer` on all columns after initialisation
    - _Requirements: 3.1, 3.2, 3.3, 6.1, 6.2_

  - [x] 6.4 Update `displayDynamoResult` and `displayResult` in `ResultPanel`
    - Call `tableModel.resetSort()` before populating columns
    - Pass all-TEXT `ColumnType[]` to `tableModel.initColumns` (no type info available)
    - Install `SortIndicatorHeaderRenderer` on all columns after initialisation
    - _Requirements: 3.4, 6.1, 6.2_

  - [x] 6.5 Update `appendPageToTable` in `ResultPanel` to delegate to `tableModel.appendRows(page)`
    - Remove the existing `addRow` loop; replace with single `tableModel.appendRows(page)` call
    - _Requirements: 5.1, 5.2_

- [x] 7. Write unit tests for `SortableTableModel` and `ColumnType`
  - [ ]* 7.1 Write unit tests in `src/test/java/com/dbexplorer/ui/SortableTableModelTest.java`
    - Test `ColumnType.fromSqlType` for specific known SQL type constants
    - Test DynamoDB / non-lazy path defaults all columns to TEXT (Requirement 3.4)
    - Test parse-failure fallback: a NUMERIC column containing `"abc"` does not throw
    - Test empty model edge case: sorting an empty table is a no-op
    - Test `resetSort` clears sort indicators after a new result is loaded
    - _Requirements: 2.5, 3.4_

- [x] 8. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Property tests use jqwik 1.8.4 (already in `pom.xml`); file: `SortableTableModelPropertyTest.java`
- Each property test must include a comment: `// Feature: query-result-column-sorting, Property N: <text>`
- The sort index (`List<Integer>`) avoids copying row data — ~40 KB overhead for 10,000 rows
- `Collections.sort` (TimSort) guarantees stability for Property 11
