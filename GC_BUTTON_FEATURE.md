# Garbage Collection Button Feature

## Overview

A new garbage collection button (🗑) has been added to the status bar in the bottom-right corner next to the heap memory display. This allows users to manually trigger garbage collection with a single click to help manage memory usage during interactive query sessions.

## Location

The GC button is located in the **footer status bar**, positioned immediately to the left of the heap memory information display:

```
[Query Status]                    🗑 Heap: 256 MB / 2048 MB  (12%)
```

## Features

### Button Appearance
- **Icon**: Trash bin emoji (🗑)
- **Style**: Flat, minimal button that matches the application theme
- **Hover Effect**: Subtle hover effect with background highlight
- **Tooltip**: "Force Garbage Collection"

### Functionality
When clicked, the button:
1. Calls `System.gc()` to request garbage collection from the JVM
2. Immediately refreshes the heap memory display
3. Logs the action in the console: "Garbage collection requested."

### Memory Display
The heap label shows:
- **Current Usage**: Memory currently in use (in MB)
- **Max Available**: Maximum heap size (in MB)
- **Percentage**: Percentage of heap in use
- **Color Coding**:
  - 🟢 Green: 0-70% usage
  - 🟡 Amber: 70-90% usage
  - 🔴 Red: 90%+ usage (warning state)

**Tooltip Info** (hover over heap display):
- Used memory
- Allocated memory
- Maximum memory

## Usage Example

### Scenario: Running Multiple Large Queries
When running sequential large queries, heap memory usage can accumulate:

1. Run Query A → Heap shows 60% (1229 MB / 2048 MB)
2. Run Query B → Heap shows 75% (1536 MB / 2048 MB) ⚠️
3. Click the 🗑 button → GC runs → Heap drops to 35% (716 MB / 2048 MB)
4. Run Query A again → Heap shows 62% (1267 MB / 2048 MB)

### Best Practices
- **Click before large operations**: If heap is above 70%, click the GC button before running the next large query
- **Monitor the color**: Red (90%+) is a good time to click the button
- **Don't worry about frequent GC**: Modern JVM handles garbage collection efficiently
- **Works with the memory limit feature**: Combined with the configurable `query.max.rows` limit from `app.properties`, this provides comprehensive memory management

## Technical Implementation

### Changes Made

**File**: `src/main/java/com/dbexplorer/ui/MainFrame.java`

1. **Added field**:
   ```java
   private JButton gcButton;
   ```

2. **Created button in initLayout()**:
   - Styled as a flat, minimal button
   - Added hover effect for consistency
   - Wired to `System.gc()` with heap refresh
   - Positioned in a FlowLayout panel next to heap label

3. **Action listener**:
   ```java
   gcButton.addActionListener(e -> {
       System.gc();
       updateHeapLabel();
       logPanel.logInfo("Garbage collection requested.");
   });
   ```

## Integration with Memory Optimization

This feature complements the existing memory optimization measures:

| Feature | Purpose | Trigger |
|---------|---------|---------|
| **Max Row Limit** | Prevent loading > 10,000 rows | Automatic (configurable in `app.properties`) |
| **Fetch Size** | Control batch size | Automatic (configurable in `app.properties`) |
| **Data Cleanup** | Explicit clearing of old results | Automatic (on new query) |
| **GC Button** | Manual memory cleanup | Manual (user clicks) |

## Configuration

The button behavior can be indirectly controlled via `app.properties`:

```ini
# Smaller max rows = less memory pressure = less need for manual GC
query.max.rows=5000

# Smaller fetch size = smaller per-batch memory usage
query.fetch.size=250
```

## Notes

- **Suggestion, not Guarantee**: `System.gc()` is a suggestion to the JVM; it doesn't guarantee immediate collection
- **Non-blocking**: The GC call is synchronous but typically completes within milliseconds
- **Safe to Use**: Clicking frequently has minimal overhead and won't cause issues
- **Monitor if Needed**: Use `jcmd <pid> VM.memory_info` or JVisualVM for detailed memory analysis if memory issues persist

## Future Enhancements

Potential improvements:
- Add GC frequency statistics (total collections, total time)
- Show "collection in progress" indicator
- Auto-GC after large query completion (configurable)
- GC scheduling (e.g., every 5 minutes)

