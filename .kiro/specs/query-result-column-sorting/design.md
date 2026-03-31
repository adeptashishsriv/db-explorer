# Design Document: Query Result Column Sorting

## Overview

This feature adds interactive, type-aware column sorting to the query results `JTable` in DB Explorer. Clicking a column header cycles through ASCENDING → DESCENDING → UNSORTED states. The sort operates entirely on the in-memory rows already loaded into the table model — no SQL re-execution occurs. Sorting is type-aware: numeric columns sort numerically, date/timestamp columns sort chronologically, and all other columns sort lexicographically. The implementation integrates with the existing lazy-loading infrastructure so that new rows fetched by scrolling are inserted into the correct sorted position.

The design introduces one new model class (`SortableTableModel`) and one new enum (`ColumnSortState`), and modifies `ResultPanel` to wire them together. No changes are required to `LazyQueryResult`, `QueryExecutor`, or any service layer.

---

## Architecture

The feature is entirely within the UI and model layers. The existing data pipeline (`QueryExecutor` → `LazyQueryResult` → `ResultPanel`) is unchanged; `ResultPanel` simply swaps its `DefaultTableModel` for the new `SortableTableModel`.

```mermaid
flowchart TD
    subgraph Data Layer (unchanged)
        QE[QueryExecutor]
        LQR[LazyQueryResult\nexposes int[] columnTypes]
    end

    subgraph UI Layer
        RP[ResultPanel]
        STM[SortableTableModel]
        TH[JTableHeader\nMouseListener]
        SIR[SortIndicatorRenderer\nTableCellHeaderRenderer]
    end

    QE -->|LazyQueryResult| RP
    RP -->|displayLazyResult| STM
    TH -->|column click| STM
    STM -->|fireTableDataChanged| RP
    STM --> SIR
```

**Key design decisions:**

- `SortableTableModel` extends `DefaultTableModel` so all existing `ResultPanel` code that calls `tableModel.addRow`, `setRowCount`, `addColumn`, etc. continues to work without modification.
- The sort index is a `List<Integer>` mapping display row → underlying row, avoiding copying the row data itself.
- `JTable.setAutoCreateRowSorter(false)` is kept (the default); we implement our own sorter to retain full control over the three-state cycle and lazy-load merge.

---

## Components and Interfaces

### `ColumnSortState` (enum)

```
com.dbexplorer.ui.ColumnSortState
```

Three values: `ASCENDING`, `DESCENDING`, `UNSORTED`.

Provides a `next()` method that cycles ASCENDING → DESCENDING → UNSORTED → ASCENDING.

---

### `ColumnType` (enum)

```
com.dbexplorer.ui.ColumnType
```

Three values: `NUMERIC`, `DATETIME`, `TEXT`.

Provides a static factory `fromSqlType(int sqlType)` that maps `java.sql.Types` constants:
- NUMERIC: `TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, REAL, DOUBLE, NUMERIC, DECIMAL`
- DATETIME: `DATE, TIME, TIMESTAMP, TIME_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE`
- TEXT: everything else (including the default when type info is unavailable)

---

### `SortableTableModel`

```
com.dbexplorer.ui.SortableTableModel extends DefaultTableModel
```

**State:**
| Field | Type | Purpose |
|---|---|---|
| `sortedColumn` | `int` (-1 = none) | Which column is currently sorted |
| `sortState` | `ColumnSortState` | Current state of the sorted column |
| `columnTypes` | `ColumnType[]` | Per-column type, set at initialisation |
| `sortIndex` | `List<Integer>` | Maps display row index → underlying storage row index |

**Key methods:**

```java
// Called by ResultPanel when a new result is loaded
void initColumns(List<String> columns, ColumnType[] types)

// Called by the header MouseListener
void cycleSort(int columnIndex)

// Called by ResultPanel.appendPageToTable
void appendRows(List<String[]> page)

// Overrides DefaultTableModel
Object getValueAt(int row, int col)   // uses sortIndex for indirection
int getRowCount()                      // returns sortIndex.size()
```

**Sort algorithm:**
- Uses `Collections.sort` (TimSort — stable) on `sortIndex`, comparing the underlying rows via the column's `ColumnType` comparator.
- Null/empty strings always sort as less-than non-null values (last in ASCENDING, first in DESCENDING).
- Parse failures fall back to case-insensitive string comparison.

**Lazy-load merge:**
- When `appendRows` is called and a sort is active, new rows are added to the underlying storage and then binary-searched into `sortIndex` using `Collections.binarySearch` with the same comparator. This is O(k log n) for k new rows in a list of n, well within the 16 ms budget for 500 rows.
- When no sort is active, rows are appended in order (O(k)).

---

### `SortIndicatorHeaderRenderer`

```
com.dbexplorer.ui.SortIndicatorHeaderRenderer implements TableCellRenderer
```

Wraps the default header renderer. Appends ` ↑` or ` ↓` to the column label when the column is sorted; shows the plain label otherwise. Queries `SortableTableModel` for the current sort state.

---

### `ResultPanel` (modified)

- Replaces `DefaultTableModel tableModel` field with `SortableTableModel tableModel`.
- Installs a `MouseListener` on `table.getTableHeader()` that calls `tableModel.cycleSort(columnIndex)` and then repaints the header.
- Installs `SortIndicatorHeaderRenderer` on all columns after each new result is loaded.
- `appendPageToTable` delegates to `tableModel.appendRows(page)` instead of calling `addRow` in a loop.
- `displayLazyResult` / `displayLazyResultWithFirstPage` pass `columnTypes` from `LazyQueryResult` to `tableModel.initColumns`.
- `displayDynamoResult` and `displayResult` call `tableModel.initColumns` with all-TEXT types (no type info available).
- On new result load, `tableModel.resetSort()` is called before populating columns.

---

## Data Models

### Sort Index

The sort index is the central data structure. It is a `List<Integer>` where `sortIndex.get(i)` is the index into the underlying `DefaultTableModel` storage for display row `i`.

- **Unsorted state**: `sortIndex` is `[0, 1, 2, ..., n-1]` (identity mapping).
- **Sorted state**: `sortIndex` is a permutation of `[0, 1, ..., n-1]` ordered by the comparator.
- **Append (sorted)**: new rows are added at indices `n, n+1, ...` in storage; each is binary-searched into `sortIndex`.
- **Append (unsorted)**: `sortIndex` grows by appending `n, n+1, ...` directly.

This avoids copying row data and keeps memory overhead to one `int` per loaded row (~4 bytes × 10,000 rows = 40 KB).

### ColumnType Mapping

```
java.sql.Types → ColumnType
─────────────────────────────────────────────────────
TINYINT, SMALLINT, INTEGER, BIGINT,
FLOAT, REAL, DOUBLE, NUMERIC, DECIMAL  →  NUMERIC

DATE, TIME, TIMESTAMP,
TIME_WITH_TIMEZONE,
TIMESTAMP_WITH_TIMEZONE                →  DATETIME

(everything else, or unknown)          →  TEXT
```

### Comparator Logic

For each `ColumnType`, the comparator applied to two `String` cell values `a` and `b`:

```
NUMERIC:
  null/empty → treated as -∞ (less than everything)
  parse failure → fall back to String.compareToIgnoreCase
  otherwise → Double.compare(parseDouble(a), parseDouble(b))

DATETIME:
  null/empty → treated as -∞
  parse failure → fall back to String.compareToIgnoreCase
  otherwise → compare via LocalDateTime / LocalDate / LocalTime parsing
              (try ISO_LOCAL_DATE_TIME, then ISO_LOCAL_DATE, then ISO_LOCAL_TIME)

TEXT:
  null/empty → treated as -∞
  otherwise → String.compareToIgnoreCase(a, b)
```

DESCENDING simply negates the ASCENDING comparator result. Null-last in ASCENDING means null-first in DESCENDING (the negation naturally achieves this).

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Sort state cycle is always ASCENDING → DESCENDING → UNSORTED

*For any* `SortableTableModel` and any valid column index, calling `cycleSort` on that column three times in succession must return the column to UNSORTED, and the intermediate states must be ASCENDING then DESCENDING in that order.

**Validates: Requirements 1.1**

---

### Property 2: Sorted rows are in non-decreasing comparator order

*For any* `SortableTableModel` populated with random rows and any column, after calling `cycleSort` once (ASCENDING), every adjacent pair of display rows `(i, i+1)` must satisfy `comparator(row[i][col], row[i+1][col]) <= 0`. After calling `cycleSort` again (DESCENDING), every adjacent pair must satisfy `comparator(row[i][col], row[i+1][col]) >= 0`.

**Validates: Requirements 1.2, 2.1, 2.2, 2.3**

---

### Property 3: Sort then unsort restores original fetch order

*For any* `SortableTableModel` populated with random rows, sorting a column (ASCENDING or DESCENDING) and then cycling to UNSORTED must produce a display order identical to the original insertion order.

**Validates: Requirements 1.3**

---

### Property 4: Only one column is sorted at a time

*For any* `SortableTableModel` with multiple columns, after calling `cycleSort` on column A and then `cycleSort` on column B (where A ≠ B), column A's sort state must be UNSORTED and column B's sort state must be ASCENDING.

**Validates: Requirements 1.4**

---

### Property 5: Numeric sort differs from lexicographic sort on numeric strings

*For any* list of numeric strings that would produce a different order under numeric vs. lexicographic comparison (e.g. `["10", "9", "2"]`), sorting a NUMERIC column must produce numeric order, not lexicographic order.

**Validates: Requirements 2.1**

---

### Property 6: Null/empty values sort last in ASCENDING, first in DESCENDING

*For any* column type and any list of rows containing at least one null/empty value and at least one non-empty value, after sorting ASCENDING all null/empty values must appear after all non-empty values; after sorting DESCENDING all null/empty values must appear before all non-empty values.

**Validates: Requirements 2.4** *(edge case)*

---

### Property 7: SQL type mapping is exhaustive and correct

*For any* `java.sql.Types` constant, `ColumnType.fromSqlType` must return NUMERIC for all numeric SQL types (TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, REAL, DOUBLE, NUMERIC, DECIMAL), DATETIME for all temporal SQL types (DATE, TIME, TIMESTAMP, TIME_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE), and TEXT for all other values.

**Validates: Requirements 3.1, 3.2, 3.3**

---

### Property 8: Sort indicator renderer reflects sort state

*For any* column name and `ColumnSortState`, the `SortIndicatorHeaderRenderer` must include `↑` in the rendered label when state is ASCENDING, `↓` when DESCENDING, and neither `↑` nor `↓` when UNSORTED.

**Validates: Requirements 4.1, 4.2, 4.3**

---

### Property 9: Appending rows preserves sorted order

*For any* `SortableTableModel` with an active sort, appending a new page of random rows must result in a display order where every adjacent pair of rows still satisfies the sort comparator.

**Validates: Requirements 5.1**

---

### Property 10: Appending rows without active sort preserves fetch order

*For any* `SortableTableModel` with no active sort, appending a page of rows must result in those rows appearing at the end of the table in the same order they were provided.

**Validates: Requirements 5.2**

---

### Property 11: Sort is stable — equal keys preserve relative fetch order

*For any* list of rows where multiple rows share the same value in the sort column, after sorting, those rows must appear in the same relative order as they were originally inserted.

**Validates: Requirements 5.3**

---

### Property 12: Reset clears all sort states

*For any* `SortableTableModel` where one or more columns have been sorted, calling `resetSort()` must result in every column having sort state UNSORTED and `sortedColumn` returning -1.

**Validates: Requirements 6.1**

---

## Error Handling

| Scenario | Handling |
|---|---|
| Cell value cannot be parsed as NUMERIC | Fall back to `String.compareToIgnoreCase`; no exception propagated |
| Cell value cannot be parsed as DATETIME | Fall back to `String.compareToIgnoreCase`; no exception propagated |
| `cycleSort` called with out-of-range column index | Guard with bounds check; log warning; no-op |
| `appendRows` called with empty list | No-op; no model events fired |
| `LazyQueryResult.columnTypes` length mismatches column count | Pad missing entries with TEXT; log warning |
| `initColumns` called with null types array | Default all columns to TEXT |

All comparator exceptions are caught at the comparator level and fall back to string comparison, ensuring the sort never throws to the EDT.

---

## Testing Strategy

### Dual Testing Approach

Both unit tests and property-based tests are required. Unit tests cover specific examples, integration points, and edge cases. Property tests verify universal correctness across randomly generated inputs.

### Property-Based Testing

The project already includes **jqwik 1.8.4** as a test dependency (see `pom.xml`). All property tests use jqwik.

Each property test must run a minimum of **100 iterations** (jqwik default is 1000, which is fine).

Each property test must include a comment in the format:
```
// Feature: query-result-column-sorting, Property N: <property text>
```

**Property test file:** `src/test/java/com/dbexplorer/ui/SortableTableModelPropertyTest.java`

| Property | Test method | jqwik annotation |
|---|---|---|
| Property 1: Sort state cycle | `sortStateCyclesCorrectly` | `@Property` |
| Property 2: Sorted rows in order | `sortedRowsAreOrdered` | `@Property` |
| Property 3: Sort then unsort restores order | `sortThenUnsortRestoresOrder` | `@Property` |
| Property 4: Only one column sorted | `onlyOneColumnSortedAtATime` | `@Property` |
| Property 5: Numeric sort vs lexicographic | `numericSortDiffersFromLexicographic` | `@Property` |
| Property 6: Nulls sort last/first | `nullValuesSortCorrectly` | `@Property` |
| Property 7: SQL type mapping | `sqlTypeMappingIsExhaustive` | `@Property` |
| Property 8: Renderer reflects state | `rendererReflectsSortState` | `@Property` |
| Property 9: Append preserves sort | `appendPreservesSortedOrder` | `@Property` |
| Property 10: Append without sort preserves fetch order | `appendWithoutSortPreservesFetchOrder` | `@Property` |
| Property 11: Sort is stable | `sortIsStable` | `@Property` |
| Property 12: Reset clears all states | `resetClearsAllSortStates` | `@Property` |

### Unit Tests

**Unit test file:** `src/test/java/com/dbexplorer/ui/SortableTableModelTest.java`

Focus areas:
- `ColumnType.fromSqlType` for specific known SQL type constants (example-based)
- DynamoDB / non-lazy path defaults all columns to TEXT (Requirement 3.4 example)
- Parse-failure fallback: a NUMERIC column containing `"abc"` does not throw
- Empty model edge case: sorting an empty table is a no-op
- `resetSort` after loading a new result clears indicators (integration with `ResultPanel`)
