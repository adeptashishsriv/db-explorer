# Garbage Collection Button - Visual Location Guide

## Button Location in UI

```
╔════════════════════════════════════════════════════════════════════════════════╗
║                           DB Explorer - Main Window                            ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ [File] [Edit] [Tools]                                                          ║
║ ┌─ Toolbar ───────────────────────────────────────────────────────────────┐   ║
║ │ [Add] [Disconnect] | [Run] [Cancel] [New Tab] | [Clear] | [Dashboard] │   ║
║ │ Theme: [Light v]                                               [About] │   ║
║ └─────────────────────────────────────────────────────────────────────────┘   ║
╠════════════════════════════════════════════════════════════════════════════════╣
║                                                                                  ║
║  Connections        │    SQL Editor                                             ║
║  ─────────────────  │    ──────────────────────────────────────────────────    ║
║  ◯ Database 1       │                                                           ║
║  ◯ Database 2       │    SELECT * FROM users WHERE id > 100                     ║
║  ◯ Database 3       │                                                           ║
║                     │    Results                                                ║
║                     │    ──────────────────────────────────────────────────    ║
║                     │                                                           ║
║                     │    id    name         email                               ║
║                     │    ──────────────────────────────────────────────────    ║
║                     │    1     John Smith   john@example.com                    ║
║                     │    2     Jane Doe     jane@example.com                    ║
║                     │                                                           ║
║                     │                                                           ║
║                     │    Console                                                ║
║                     │    ──────────────────────────────────────────────────    ║
║                     │    [13:45:22] Query executed in 245 ms                    ║
║                     │    [13:45:25] Garbage collection requested.               ║
║                     │                                                           ║
╠════════════════════════════════════════════════════════════════════════════════╣
║ Connected: MyDB - SELECT...              🗑 Heap: 512 MB / 2048 MB (25%)       ║
║                                          ↑                                       ║
║                                     BUTTON HERE                                  ║
╚════════════════════════════════════════════════════════════════════════════════╝
```

## Detailed Status Bar Layout

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ STATUS BAR (Bottom of window)                                                   │
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│ [Query Status Left Side]        [Heap Display Right Side]                      │
│ Connected: MyDB - SELECT...     🗑  Heap: 512 MB / 2048 MB (25%)               │
│                                ↑   ↑                                            │
│                                │   └─ Heap memory label                         │
│                                │      (updates every 2 seconds)                 │
│                                │      Color codes:                              │
│                                │      🟢 0-70% (normal)                         │
│                                │      🟡 70-90% (amber)                         │
│                                │      🔴 90%+ (red)                             │
│                                │                                                │
│                                └─ GC Button                                     │
│                                   - Icon: 🗑                                     │
│                                   - Tooltip: "Force Garbage Collection"          │
│                                   - Action: System.gc()                         │
│                                   - Updates heap display                        │
│                                   - Logs to console                             │
│                                                                                  │
│ [3px top margin] [8px left padding]  [4px spacing]  [8px right padding]        │
└────────────────────────────────────────────────────────────────────────────────┘
```

## Button States

### Default State
```
🗑 Heap: 512 MB / 2048 MB (25%)
```
- No background
- White/default text color
- Standard cursor

### Hover State
```
🗑 Heap: 512 MB / 2048 MB (25%)
[subtle gray background]
```
- Light gray background appears
- Hand cursor
- Slightly elevated appearance

### Click Action
```
🗑 Heap: 512 MB / 2048 MB (25%)
→ System.gc() called
→ Heap display refreshes
→ Console: "Garbage collection requested."
```

## Keyboard & Mouse Interactions

| Action | Result |
|--------|--------|
| **Hover** | Light background appears, cursor changes to hand |
| **Click** | GC triggered, heap updates, console logs event |
| **Tooltip hover** | Shows "Force Garbage Collection" text |

## Related Elements

### Heap Label Positioning
The button and heap label are in a FlowLayout panel positioned at BorderLayout.EAST of the status bar:

```
FlowLayout Panel (RIGHT alignment, 4px gap)
├── GC Button (🗑)
│   ├── Font: 10pt
│   ├── Margin: 4px left/right
│   ├── Cursor: HAND
│   └── Hover Effect: ON
│
└── Heap Label
    ├── Font: 11pt
    ├── Text: "Heap: XXX MB / XXX MB (XX%)"
    ├── Color: Green/Amber/Red based on usage
    └── Tooltip: "Used: XXX MB | Allocated: XXX MB | Max: XXX MB"
```

## Memory Visualization

As you interact with the application:

```
Before GC:
┌──────────────────────────────┐
│ HEAP (2048 MB total)         │
├──────────────────────────────┤
│ ████████████████████░░░░░░░░ │  ← Used: 1536 MB (75%)
│                              │     Status: ⚠️ AMBER
└──────────────────────────────┘

Click 🗑 button...

After GC:
┌──────────────────────────────┐
│ HEAP (2048 MB total)         │
├──────────────────────────────┤
│ ███████░░░░░░░░░░░░░░░░░░░░ │  ← Used: 716 MB (35%)
│                              │     Status: ✅ GREEN
└──────────────────────────────┘
```

## Console Output Example

```
[13:45:22] Connected: Production DB
[13:45:25] Executing: SELECT COUNT(*) FROM orders WHERE status = 'pending'
[13:45:25] Query executed in 245 ms | fetch size 500
[13:45:30] User clicked 'Garbage collection' button
[13:45:30] Garbage collection requested.
[13:45:31] Heap memory reduced from 1536 MB to 716 MB
```

## Integration Points

The GC button works with:

1. **Heap Timer** - Updates heap display every 2 seconds
2. **Log Panel** - Logs GC requests to console
3. **Memory Optimization** - Complements automatic row limits and cleanup
4. **Status Bar** - Positioned in status bar.EAST region

## Accessibility Features

- ✅ Tooltip for users unfamiliar with trash icon
- ✅ Clear visual feedback (hover effect)
- ✅ Console logging for audit trail
- ✅ Works with existing memory display

---

**Note**: The button is always visible and available, making garbage collection accessible with a single click whenever needed.

