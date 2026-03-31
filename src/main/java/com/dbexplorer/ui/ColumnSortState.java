package com.dbexplorer.ui;

public enum ColumnSortState {
    ASCENDING,
    DESCENDING,
    UNSORTED;

    public ColumnSortState next() {
        switch (this) {
            case ASCENDING:  return DESCENDING;
            case DESCENDING: return UNSORTED;
            case UNSORTED:   return ASCENDING;
            default:         return ASCENDING;
        }
    }
}
