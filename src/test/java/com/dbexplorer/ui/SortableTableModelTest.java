package com.dbexplorer.ui;

import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SortableTableModel and ColumnType.
 * Feature: query-result-column-sorting
 */
class SortableTableModelTest {

    private SortableTableModel model;

    @BeforeEach
    void setUp() {
        model = new SortableTableModel();
    }

    // -------------------------------------------------------------------------
    // ColumnType.fromSqlType — specific known SQL type constants
    // -------------------------------------------------------------------------

    @Test
    void fromSqlType_integer_returnsNumeric() {
        assertEquals(ColumnType.NUMERIC, ColumnType.fromSqlType(Types.INTEGER));
    }

    @Test
    void fromSqlType_bigint_returnsNumeric() {
        assertEquals(ColumnType.NUMERIC, ColumnType.fromSqlType(Types.BIGINT));
    }

    @Test
    void fromSqlType_double_returnsNumeric() {
        assertEquals(ColumnType.NUMERIC, ColumnType.fromSqlType(Types.DOUBLE));
    }

    @Test
    void fromSqlType_decimal_returnsNumeric() {
        assertEquals(ColumnType.NUMERIC, ColumnType.fromSqlType(Types.DECIMAL));
    }

    @Test
    void fromSqlType_numeric_returnsNumeric() {
        assertEquals(ColumnType.NUMERIC, ColumnType.fromSqlType(Types.NUMERIC));
    }

    @Test
    void fromSqlType_date_returnsDatetime() {
        assertEquals(ColumnType.DATETIME, ColumnType.fromSqlType(Types.DATE));
    }

    @Test
    void fromSqlType_time_returnsDatetime() {
        assertEquals(ColumnType.DATETIME, ColumnType.fromSqlType(Types.TIME));
    }

    @Test
    void fromSqlType_timestamp_returnsDatetime() {
        assertEquals(ColumnType.DATETIME, ColumnType.fromSqlType(Types.TIMESTAMP));
    }

    @Test
    void fromSqlType_varchar_returnsText() {
        assertEquals(ColumnType.TEXT, ColumnType.fromSqlType(Types.VARCHAR));
    }

    @Test
    void fromSqlType_boolean_returnsText() {
        assertEquals(ColumnType.TEXT, ColumnType.fromSqlType(Types.BOOLEAN));
    }

    @Test
    void fromSqlType_unknown_returnsText() {
        assertEquals(ColumnType.TEXT, ColumnType.fromSqlType(Integer.MIN_VALUE));
    }

    // -------------------------------------------------------------------------
    // Requirement 3.4 — DynamoDB / non-lazy path defaults all columns to TEXT
    // -------------------------------------------------------------------------

    @Test
    void initColumns_nullTypes_defaultsAllToText() {
        model.initColumns(List.of("col1", "col2", "col3"), null);
        // Verify by sorting — TEXT sort should work without NPE
        model.appendRows(List.of(new String[]{"b", "z", "1"}, new String[]{"a", "y", "2"}));
        assertDoesNotThrow(() -> model.cycleSort(0));
        // After ascending sort on TEXT column, "a" should come before "b"
        assertEquals("a", model.getValueAt(0, 0));
        assertEquals("b", model.getValueAt(1, 0));
    }

    @Test
    void initColumns_withAllTextTypes_worksCorrectly() {
        ColumnType[] types = {ColumnType.TEXT, ColumnType.TEXT};
        model.initColumns(List.of("name", "value"), types);
        model.appendRows(List.of(new String[]{"Charlie", "3"}, new String[]{"Alice", "1"}, new String[]{"Bob", "2"}));
        model.cycleSort(0);
        assertEquals("Alice", model.getValueAt(0, 0));
        assertEquals("Bob", model.getValueAt(1, 0));
        assertEquals("Charlie", model.getValueAt(2, 0));
    }

    // -------------------------------------------------------------------------
    // Requirement 2.5 — Parse-failure fallback: NUMERIC column with "abc"
    // -------------------------------------------------------------------------

    @Test
    void numericColumn_nonNumericValue_doesNotThrow() {
        ColumnType[] types = {ColumnType.NUMERIC};
        model.initColumns(List.of("num"), types);
        model.appendRows(List.of(
                new String[]{"abc"},
                new String[]{"123"},
                new String[]{"xyz"}
        ));
        // Should not throw — falls back to string comparison
        assertDoesNotThrow(() -> model.cycleSort(0));
    }

    @Test
    void numericColumn_mixedValues_sortsFallbackGracefully() {
        ColumnType[] types = {ColumnType.NUMERIC};
        model.initColumns(List.of("num"), types);
        model.appendRows(List.of(
                new String[]{"10"},
                new String[]{"not-a-number"},
                new String[]{"2"}
        ));
        assertDoesNotThrow(() -> {
            model.cycleSort(0); // ASCENDING
            model.cycleSort(0); // DESCENDING
            model.cycleSort(0); // UNSORTED
        });
    }

    // -------------------------------------------------------------------------
    // Empty model edge case — sorting an empty table is a no-op
    // -------------------------------------------------------------------------

    @Test
    void cycleSort_emptyTable_isNoOp() {
        model.initColumns(List.of("col1", "col2"), new ColumnType[]{ColumnType.TEXT, ColumnType.NUMERIC});
        assertEquals(0, model.getRowCount());
        assertDoesNotThrow(() -> model.cycleSort(0));
        assertEquals(0, model.getRowCount());
    }

    @Test
    void cycleSort_outOfRangeIndex_isNoOp() {
        model.initColumns(List.of("col1"), new ColumnType[]{ColumnType.TEXT});
        model.appendRows(List.of(new String[]{"a"}, new String[]{"b"}));
        assertDoesNotThrow(() -> model.cycleSort(99));
        assertDoesNotThrow(() -> model.cycleSort(-1));
        // Sort state should remain UNSORTED
        assertEquals(ColumnSortState.UNSORTED, model.getSortState(0));
    }

    // -------------------------------------------------------------------------
    // resetSort — clears sort indicators after a new result is loaded
    // -------------------------------------------------------------------------

    @Test
    void resetSort_afterSorting_clearsSortState() {
        model.initColumns(List.of("col1"), new ColumnType[]{ColumnType.TEXT});
        model.appendRows(List.of(new String[]{"b"}, new String[]{"a"}));
        model.cycleSort(0); // ASCENDING
        assertEquals(ColumnSortState.ASCENDING, model.getSortState(0));

        model.resetSort();

        assertEquals(ColumnSortState.UNSORTED, model.getSortState(0));
    }

    @Test
    void resetSort_restoresOriginalOrder() {
        model.initColumns(List.of("col1"), new ColumnType[]{ColumnType.TEXT});
        model.appendRows(List.of(new String[]{"c"}, new String[]{"a"}, new String[]{"b"}));

        model.cycleSort(0); // ASCENDING: a, b, c
        assertEquals("a", model.getValueAt(0, 0));

        model.resetSort(); // back to original: c, a, b
        assertEquals("c", model.getValueAt(0, 0));
        assertEquals("a", model.getValueAt(1, 0));
        assertEquals("b", model.getValueAt(2, 0));
    }

    @Test
    void initColumns_newResult_resetsSortState() {
        model.initColumns(List.of("col1"), new ColumnType[]{ColumnType.TEXT});
        model.appendRows(List.of(new String[]{"b"}, new String[]{"a"}));
        model.cycleSort(0); // ASCENDING

        // Simulate loading a new query result
        model.initColumns(List.of("newcol"), new ColumnType[]{ColumnType.NUMERIC});
        assertEquals(ColumnSortState.UNSORTED, model.getSortState(0));
        assertEquals(0, model.getRowCount());
    }

    // -------------------------------------------------------------------------
    // Basic sort correctness
    // -------------------------------------------------------------------------

    @Test
    void cycleSort_ascending_sortsCorrectly() {
        ColumnType[] types = {ColumnType.NUMERIC};
        model.initColumns(List.of("num"), types);
        model.appendRows(List.of(
                new String[]{"10"},
                new String[]{"2"},
                new String[]{"30"}
        ));
        model.cycleSort(0); // ASCENDING
        assertEquals("2", model.getValueAt(0, 0));
        assertEquals("10", model.getValueAt(1, 0));
        assertEquals("30", model.getValueAt(2, 0));
    }

    @Test
    void cycleSort_descending_sortsCorrectly() {
        ColumnType[] types = {ColumnType.NUMERIC};
        model.initColumns(List.of("num"), types);
        model.appendRows(List.of(
                new String[]{"10"},
                new String[]{"2"},
                new String[]{"30"}
        ));
        model.cycleSort(0); // ASCENDING
        model.cycleSort(0); // DESCENDING
        assertEquals("30", model.getValueAt(0, 0));
        assertEquals("10", model.getValueAt(1, 0));
        assertEquals("2", model.getValueAt(2, 0));
    }

    @Test
    void cycleSort_unsorted_restoresOriginalOrder() {
        ColumnType[] types = {ColumnType.TEXT};
        model.initColumns(List.of("name"), types);
        model.appendRows(List.of(
                new String[]{"Charlie"},
                new String[]{"Alice"},
                new String[]{"Bob"}
        ));
        model.cycleSort(0); // ASCENDING
        model.cycleSort(0); // DESCENDING
        model.cycleSort(0); // UNSORTED
        assertEquals("Charlie", model.getValueAt(0, 0));
        assertEquals("Alice", model.getValueAt(1, 0));
        assertEquals("Bob", model.getValueAt(2, 0));
    }

    @Test
    void nullValues_sortLastInAscending() {
        ColumnType[] types = {ColumnType.TEXT};
        model.initColumns(List.of("col"), types);
        model.appendRows(List.of(
                new String[]{"b"},
                new String[]{null},
                new String[]{"a"}
        ));
        model.cycleSort(0); // ASCENDING
        assertEquals("a", model.getValueAt(0, 0));
        assertEquals("b", model.getValueAt(1, 0));
        assertNull(model.getValueAt(2, 0));
    }

    @Test
    void emptyValues_sortLastInAscending() {
        ColumnType[] types = {ColumnType.TEXT};
        model.initColumns(List.of("col"), types);
        model.appendRows(List.of(
                new String[]{"b"},
                new String[]{""},
                new String[]{"a"}
        ));
        model.cycleSort(0); // ASCENDING
        assertEquals("a", model.getValueAt(0, 0));
        assertEquals("b", model.getValueAt(1, 0));
        assertEquals("", model.getValueAt(2, 0));
    }
}
