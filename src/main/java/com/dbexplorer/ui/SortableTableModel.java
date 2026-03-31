package com.dbexplorer.ui;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import static com.dbexplorer.ui.ColumnSortState.UNSORTED;

/**
 * A table model that supports type-aware column sorting via a sort index.
 * Rows are stored in a plain List<String[]> to avoid DefaultTableModel's
 * internal justifyRows() which conflicts with our getRowCount() override.
 */
public class SortableTableModel extends AbstractTableModel {

    private int sortedColumn = -1;
    private ColumnSortState sortState = UNSORTED;
    private ColumnType[] columnTypes;
    private List<Integer> sortIndex;

    /** Underlying row storage — never reordered; sortIndex provides indirection. */
    private final List<String[]> rows = new ArrayList<>();
    /** Column names in display order. */
    private final List<String> columnNames = new ArrayList<>();

    @Override
    public boolean isCellEditable(int row, int col) { return false; }

    // -------------------------------------------------------------------------
    // AbstractTableModel contract
    // -------------------------------------------------------------------------

    @Override
    public int getRowCount() {
        return sortIndex == null ? 0 : sortIndex.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.size();
    }

    @Override
    public String getColumnName(int col) {
        return (col >= 0 && col < columnNames.size()) ? columnNames.get(col) : "";
    }

    @Override
    public Object getValueAt(int row, int col) {
        if (sortIndex == null || row < 0 || row >= sortIndex.size()) {
            return null;
        }
        int storageRow = sortIndex.get(row);
        String[] r = rows.get(storageRow);
        return (r == null || col < 0 || col >= r.length) ? null : r[col];
    }

    // -------------------------------------------------------------------------
    // DefaultTableModel-compatible API used by ResultPanel
    // -------------------------------------------------------------------------

    /** Clears all rows; resets sort index. */
    public void setRowCount(int rowCount) {
        if (rowCount == 0) {
            rows.clear();
            sortIndex = new ArrayList<>();
            fireTableDataChanged();
        } else {
            // Pre-allocate empty rows for batch setValueAt usage (displayDynamoResult)
            rows.clear();
            for (int i = 0; i < rowCount; i++) {
                rows.add(new String[columnNames.size()]);
            }
            rebuildIdentitySortIndex();
            fireTableDataChanged();
        }
    }

    /** Clears all columns and rows. */
    public void setColumnCount(int columnCount) {
        if (columnCount == 0) {
            columnNames.clear();
            rows.clear();
            sortIndex = new ArrayList<>();
            fireTableStructureChanged();
        }
    }

    /** Adds a column with the given name (no data). */
    public void addColumn(Object columnName) {
        columnNames.add(columnName == null ? "" : columnName.toString());
        fireTableStructureChanged();
    }

    /** Adds a row of data. */
    public void addRow(Object[] rowData) {
        String[] row = new String[columnNames.size()];
        for (int i = 0; i < row.length; i++) {
            Object v = (rowData != null && i < rowData.length) ? rowData[i] : null;
            row[i] = (v == null) ? null : v.toString();
        }
        int idx = rows.size();
        rows.add(row);
        if (sortIndex != null) sortIndex.add(idx);
        fireTableRowsInserted(idx, idx);
    }

    /** Sets a cell value (used by displayDynamoResult). */
    public void setValueAt(Object value, int row, int col) {
        if (sortIndex == null || row < 0 || row >= sortIndex.size()) return;
        int storageRow = sortIndex.get(row);
        String[] r = rows.get(storageRow);
        if (r != null && col >= 0 && col < r.length) {
            r[col] = (value == null) ? null : value.toString();
            fireTableCellUpdated(row, col);
        }
    }

    // -------------------------------------------------------------------------
    // Task 2.1 — Core structure
    // -------------------------------------------------------------------------

    public void initColumns(List<String> columns, ColumnType[] types) {
        rows.clear();
        columnNames.clear();
        columnNames.addAll(columns);

        this.columnTypes = (types != null) ? types : new ColumnType[columns.size()];
        if (types == null) {
            for (int i = 0; i < this.columnTypes.length; i++) {
                this.columnTypes[i] = ColumnType.TEXT;
            }
        }
        sortedColumn = -1;
        sortState = UNSORTED;
        sortIndex = new ArrayList<>();
        fireTableStructureChanged();
    }

    public void resetSort() {
        sortedColumn = -1;
        sortState = UNSORTED;
        rebuildIdentitySortIndex();
        fireTableDataChanged();
    }

    public ColumnSortState getSortState(int col) {
        return (col == sortedColumn) ? sortState : UNSORTED;
    }

    private void rebuildIdentitySortIndex() {
        sortIndex = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            sortIndex.add(i);
        }
    }

    // -------------------------------------------------------------------------
    // Task 2.4 — Type-aware comparators
    // -------------------------------------------------------------------------

    private Comparator<Integer> buildComparator(int col) {
        ColumnType type = (columnTypes != null && col < columnTypes.length)
                ? columnTypes[col]
                : ColumnType.TEXT;

        Comparator<Integer> ascending;

        // noinspection EnhancedSwitchMigration
        switch (type) {
            case NUMERIC:
                ascending = (a, b) -> {
                    String sa = cellString(a, col);
                    String sb = cellString(b, col);
                    boolean aEmpty = sa == null || sa.isEmpty();
                    boolean bEmpty = sb == null || sb.isEmpty();
                    if (aEmpty && bEmpty) return 0;
                    if (aEmpty) return 1;   // nulls sort last in ASCENDING
                    if (bEmpty) return -1;
                    try {
                        double da = Double.parseDouble(sa);
                        double db = Double.parseDouble(sb);
                        return Double.compare(da, db);
                    } catch (NumberFormatException e) {
                        return sa.compareToIgnoreCase(sb);
                    }
                };
                break;

            case DATETIME:
                ascending = (a, b) -> {
                    String sa = cellString(a, col);
                    String sb = cellString(b, col);
                    boolean aEmpty = sa == null || sa.isEmpty();
                    boolean bEmpty = sb == null || sb.isEmpty();
                    if (aEmpty && bEmpty) return 0;
                    if (aEmpty) return 1;   // nulls sort last in ASCENDING
                    if (bEmpty) return -1;
                    try {
                        Comparable<Object> ca = parseDatetime(sa);
                        Comparable<Object> cb = parseDatetime(sb);
                        if (ca != null && cb != null) {
                            return ca.compareTo(cb);
                        }
                    } catch (Exception ignored) {
                        // fall through
                    }
                    return sa.compareToIgnoreCase(sb);
                };
                break;

            default: // TEXT
                ascending = (a, b) -> {
                    String sa = cellString(a, col);
                    String sb = cellString(b, col);
                    boolean aEmpty = sa == null || sa.isEmpty();
                    boolean bEmpty = sb == null || sb.isEmpty();
                    if (aEmpty && bEmpty) return 0;
                    if (aEmpty) return 1;   // nulls sort last in ASCENDING
                    if (bEmpty) return -1;
                    return sa.compareToIgnoreCase(sb);
                };
                break;
        }

        return (sortState == ColumnSortState.DESCENDING)
                ? ascending.reversed()
                : ascending;
    }

    private String cellString(int storageRow, int col) {
        String[] r = rows.get(storageRow);
        if (r == null || col >= r.length) return null;
        return r[col];
    }

    @SuppressWarnings("unchecked")
    private Comparable<Object> parseDatetime(String s) {
        try {
            return (Comparable<Object>) (Object) LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {}
        try {
            return (Comparable<Object>) (Object) LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {}
        try {
            return (Comparable<Object>) (Object) LocalTime.parse(s, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (Exception ignored) {}
        return null;
    }

    // -------------------------------------------------------------------------
    // Task 2.8 — cycleSort
    // -------------------------------------------------------------------------

    public void cycleSort(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= getColumnCount()) {
            return;
        }

        if (columnIndex != sortedColumn) {
            // Clicking a different column — reset previous, start fresh on new column
            sortedColumn = columnIndex;
            sortState = ColumnSortState.ASCENDING;
        } else {
            sortState = sortState.next();
        }

        if (sortState == ColumnSortState.ASCENDING || sortState == ColumnSortState.DESCENDING) {
            Comparator<Integer> comparator = buildComparator(columnIndex);
            Collections.sort(sortIndex, comparator);
        } else {
            // UNSORTED — restore identity order
            rebuildIdentitySortIndex();
        }

        fireTableDataChanged();
    }

    // -------------------------------------------------------------------------
    // Task 4.1 — appendRows with lazy-load merge
    // -------------------------------------------------------------------------

    public void appendRows(List<String[]> page) {
        if (page == null || page.isEmpty()) {
            return;
        }
        int baseIndex = rows.size();
        rows.addAll(page);

        if (sortState == UNSORTED) {
            for (int i = 0; i < page.size(); i++) {
                sortIndex.add(baseIndex + i);
            }
        } else {
            Comparator<Integer> comparator = buildComparator(sortedColumn);
            for (int i = 0; i < page.size(); i++) {
                int newStorageRow = baseIndex + i;
                int pos = Collections.binarySearch(sortIndex, newStorageRow, comparator);
                if (pos < 0) pos = -(pos + 1);
                sortIndex.add(pos, newStorageRow);
            }
        }
        fireTableDataChanged();
    }
}
