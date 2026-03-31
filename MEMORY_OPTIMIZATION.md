# Memory Optimization & Query Configuration Guide

## Problem Identified

When running the same query repeatedly, memory consumption increased continuously and was not released by garbage collection. This was caused by:

1. **Accumulated rows in the table model** - The `SortableTableModel` stores all loaded rows and they were not being explicitly cleared when switching to a new query.
2. **Lingering references in `LazyQueryResult`** - The first page list held references to data that were not explicitly cleared.
3. **Lack of explicit garbage collection hints** - Internal data structures needed to be explicitly cleared to aid the JVM's garbage collector.

## Solution Implemented

### 1. Externalized Configuration (app.properties)

The following properties are now configurable in `src/main/resources/app.properties`:

```ini
# Query execution settings
query.max.rows=10000          # Maximum rows to load for any query result
query.fetch.size=500          # Fetch size per batch when scrolling
export.fetch.size=500         # Fetch size for export operations
```

**Why this matters:**
- `query.max.rows`: Prevents memory exhaustion by capping the total rows loaded into the UI (default: 10,000)
- `query.fetch.size`: Controls batch size for lazy loading (default: 500 rows per fetch)
- `export.fetch.size`: Optimizes memory usage during table exports

### 2. Explicit Data Structure Cleanup

#### In LazyQueryResult.java
- Added `clearData()` method to explicitly clear the `firstPage` list when done
- This ensures internal collections are immediately eligible for garbage collection

#### In ResultPanel.java
- Enhanced `closeLazyResult()` to call `clearData()` before closing the result
- Added `forceGarbageCollection()` method for manual cleanup if needed

### 3. Memory Cleanup Flow

When a new query is executed:

```
1. closeLazyResult() is called (from displayLazyResult())
   ↓
2. LazyQueryResult.clearData() clears internal lists
   ↓
3. LazyQueryResult.close() closes database resources
   ↓
4. currentLazyResult reference is set to null
   ↓
5. SortableTableModel.initColumns() clears the rows list
   ↓
6. Old result is now eligible for garbage collection
```

## Best Practices for Running Sequential Queries

### 1. **Avoid Excessive Row Limits**
If you're running many large queries in sequence, consider reducing `query.max.rows`:

```ini
query.max.rows=5000    # More aggressive memory management
```

### 2. **Adjust Fetch Size Based on Row Width**
For queries with very wide rows (many columns or long strings), reduce fetch size:

```ini
query.fetch.size=250   # Smaller batches = lower memory spike
```

### 3. **Monitor Memory Usage**
Use JVM monitoring tools to verify memory is being released:
- After each query completes, memory should stabilize
- If memory keeps growing, check for:
  - Very large result sets (consider using LIMIT in SQL)
  - Wide tables with LOB fields
  - Background fetch operations still pending

### 4. **For Large Result Sets**
Instead of loading everything into the UI, consider:
- Adding LIMIT clauses to your queries
- Exporting to CSV/file instead of viewing in the table
- Using database-level pagination (OFFSET/LIMIT)

## Configuration Examples

### Low Memory Environment (e.g., 512MB JVM)
```ini
query.max.rows=2000
query.fetch.size=250
export.fetch.size=250
```

### Standard Environment (e.g., 2GB JVM)
```ini
query.max.rows=10000
query.fetch.size=500
export.fetch.size=500
```

### High Memory Environment (e.g., 8GB+ JVM)
```ini
query.max.rows=50000
query.fetch.size=1000
export.fetch.size=1000
```

## How to Verify the Fix

1. **Monitor memory while running sequential queries:**
   - Run Query A → note heap size
   - Run Query B → heap may increase, then should stabilize/decrease
   - Run Query A again → should NOT continuously grow
   - GC should release memory from previous results

2. **Expected Behavior:**
   - Each new query should show minimal additional memory growth
   - After GC runs, memory should return to baseline (minus current query rows)
   - Memory should **NOT** accumulate linearly with each query

## Technical Details

### What Was Changed

1. **LazyQueryResult.java**
   - Static initializers now load `query.max.rows` and `query.fetch.size` from properties
   - Added `clearData()` method to clear `firstPage` list

2. **TableDataExportService.java**
   - Now loads `export.fetch.size` from properties
   - Allows separate tuning of export operations

3. **ResultPanel.java**
   - `closeLazyResult()` now calls `clearData()` before closing
   - Added `forceGarbageCollection()` for manual cleanup if needed

4. **app.properties**
   - Added three new configurable properties with defaults

### Why This Works

- **Reference Elimination**: By explicitly setting references to null and clearing collections, we remove barriers to garbage collection
- **Early GC Eligibility**: Objects become eligible for GC immediately after cleanup, not waiting for JVM to detect unreachable objects
- **Memory Bounded**: With `query.max.rows` limit, maximum heap usage is predictable regardless of database result size
- **Configurable**: Adjust parameters based on available memory and use case

## Troubleshooting

### Memory still growing?
1. Check JVM heap allocation: `java -Xmx2G ...` (increase if needed)
2. Reduce `query.max.rows` further
3. Check database query performance (index missing?)
4. Monitor with JVisualVM or similar tool for object retention

### Queries slower after changes?
1. Increase `query.fetch.size` (larger batches = better throughput)
2. But watch memory usage carefully

### "Results truncated at X rows" message
1. This is intentional to protect memory
2. Either refine your query with LIMIT/WHERE clauses
3. Or increase `query.max.rows` if you have enough memory

