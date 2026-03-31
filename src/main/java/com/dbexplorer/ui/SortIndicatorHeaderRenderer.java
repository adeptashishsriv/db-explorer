package com.dbexplorer.ui;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

public class SortIndicatorHeaderRenderer implements TableCellRenderer {

    private final SortableTableModel model;
    private final TableCellRenderer defaultRenderer;

    public SortIndicatorHeaderRenderer(SortableTableModel model, TableCellRenderer defaultRenderer) {
        this.model = model;
        this.defaultRenderer = defaultRenderer;
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

        String baseLabel = String.valueOf(table.getColumnModel().getColumn(column).getHeaderValue());

        ColumnSortState state = model.getSortState(column);
        String label;
        switch (state) {
            case ASCENDING:  label = baseLabel + " \u2191"; break;
            case DESCENDING: label = baseLabel + " \u2193"; break;
            default:         label = baseLabel;             break;
        }

        return defaultRenderer.getTableCellRendererComponent(table, label, isSelected, hasFocus, row, column);
    }
}
