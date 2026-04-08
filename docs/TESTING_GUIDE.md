# Testing Guide: Disconnect & Run Query Button Improvements

## Quick Reference

### Disconnect Button - Test Cases

| Test # | Setup | Action | Expected Outcome |
|--------|-------|--------|------------------|
| D1 | Select "Database1" in tree, Database1 is connected | Click [Disconnect DB] | Confirmation dialog appears asking "Disconnect from Database1?" |
| D2 | User clicks "Yes" in confirmation dialog (D1) | Complete dialog | Success dialog shows "Disconnected from Database1"; Connection tree updates; Tabs with Database1 remain bound but show "not connected" |
| D3 | User clicks "No" in confirmation dialog (D1) | Complete dialog | No changes; Tabs remain connected; Console shows no disconnect message |
| D4 | No connection selected in tree, no tab open | Click [Disconnect DB] | Info dialog shows "No connection selected. Select a connection and try again." |
| D5 | Database1 is already disconnected; Select it in tree | Click [Disconnect DB] | Info dialog shows "Database1 is already disconnected." |
| D6 | Tab with Database1 is active (tree selection empty) | Click [Disconnect DB] | Confirmation dialog appears asking "Disconnect from Database1?" (gets connection from tab) |
| D7 | Perform successful disconnect (D2) | Check console | Console log shows: "Disconnected: Database1" |

### Run Query Button - Test Cases

| Test # | Setup | Action | Expected Outcome |
|--------|-------|--------|------------------|
| R1 | No tabs open | Click [Run Query] | Status bar shows red "⚠ No tab open" for 5 seconds; Query does NOT execute |
| R2 | Tab open, NO connection bound, SQL present | Click [Run Query] | Status bar shows amber "⚡ No connection on this tab" for 5 seconds; Query does NOT execute |
| R3 | Tab open, connection bound, editor EMPTY | Click [Run Query] | Status bar shows amber "⚡ SQL editor is empty" for 5 seconds; Query does NOT execute |
| R4 | Tab open, connection bound, SQL present | Click [Run Query] | Query executes normally (existing behavior); Status bar shows query execution message |
| R5 | Valid query (R4 conditions), press Ctrl+Enter | Complete query | Query executes via keyboard shortcut (unchanged behavior) |
| R6 | After status message appears (R1, R2, or R3) | Wait 5 seconds | Status message auto-disappears; Status bar returns to normal connection display |
| R7 | Hover over [Run Query] button | Wait 1 second | Tooltip appears: "Execute the current query\nKeyboard: Ctrl+Enter" (HTML formatted) |

### Disconnect Button - Edge Cases

| Test # | Setup | Action | Expected Outcome |
|--------|-------|--------|------------------|
| DE1 | Two connections in tree; Select Database1 | Right-click Database1 → "Open Query Tab" | New tab opens, bound to Database1 |
| DE2 | Database1 and Database2 both connected; Tab1→DB1, Tab2→DB2 | Select DB2, click [Disconnect DB] | Confirmation for DB2; After disconnect, Tab2 shows "not connected" but Tab1 still shows connected |
| DE3 | Multiple tabs, one tab title shows "Database1 (not connected)" | Click [Disconnect DB] on active tab | Confirmation dialog; After disconnect, tab title updates |

### Run Query Button - Edge Cases

| Test # | Setup | Action | Expected Outcome |
|--------|-------|--------|------------------|
| RE1 | Valid setup (R4) | Select all SQL, press Delete | Tab open with empty editor |
| RE2 | Tab with only whitespace in editor | Click [Run Query] | Shows "⚡ SQL editor is empty" (whitespace-only is treated as empty) |
| RE3 | Tab1→DB1 connected, Tab2→DB2 disconnected | Activate Tab2, Click [Run Query] | Shows "⚡ No connection on this tab" |
| RE4 | Execute valid query (R4) | Immediately click [Run Query] again | Queue query or show appropriate message |
| RE5 | Status message showing | User types in editor | Status message may still be visible for remainder of 5 seconds (doesn't immediately clear) |

---

## Testing Checklist

### Pre-Test Setup
- [ ] Application built successfully
- [ ] Database connection configured
- [ ] Sample tables available for testing
- [ ] Console logs visible
- [ ] Status bar at bottom of window visible

### Disconnect Button Tests
- [ ] Run tests D1-D7
- [ ] Run tests DE1-DE3
- [ ] Verify console logs match expected messages
- [ ] Check that keyboard shortcuts still work (if any)
- [ ] Verify tab binding is preserved after disconnect
- [ ] Test with multiple databases

### Run Query Button Tests
- [ ] Run tests R1-R7
- [ ] Run tests RE1-RE5
- [ ] Verify status bar messages are colored correctly:
  - Red for errors
  - Amber for warnings
  - Green for success (if any)
- [ ] Verify auto-clear timing (5 seconds)
- [ ] Verify Ctrl+Enter still works (unchanged)
- [ ] Test with different SQL types:
  - SELECT queries
  - INSERT/UPDATE/DELETE
  - Long-running queries

### Visual Tests
- [ ] Tooltips display as HTML (formatted with line breaks)
- [ ] Button hover effect still works (gray background on hover)
- [ ] Dialog boxes display correctly (multi-line text)
- [ ] Status bar colors are consistent with theme
- [ ] Status bar text is readable (not cut off)

### Integration Tests
- [ ] Disconnect Database1, verify Run Query shows error
- [ ] Run Query on tab, then disconnect connection
- [ ] Switch tabs multiple times, verify statuses
- [ ] Create new tab without connection, test Run Query
- [ ] Open multiple query tabs with different connections

### Regression Tests
- [ ] Existing Run Query functionality works unchanged
- [ ] Keyboard shortcut Ctrl+Enter works unchanged
- [ ] Connection list still updates after disconnect
- [ ] Tab headers still show connection status
- [ ] Console logging still works
- [ ] Connection tree still toggles connection on click
- [ ] New tabs can still be created

### Performance Tests
- [ ] Buttons respond instantly to clicks
- [ ] No lag when showing status messages
- [ ] Dialog boxes appear quickly
- [ ] No memory leaks from repeated button clicks (test 20+ times)
- [ ] Status message timers don't accumulate

---

## Expected Behavior Summary

### Disconnect Button
```
┌─────────────────────────────────────────┐
│ DISCONNECT BUTTON BEHAVIOR              │
├─────────────────────────────────────────┤
│                                         │
│ 1. Finds connection (tree or tab)      │
│ 2. Validates connection exists          │
│ 3. Checks if not already disconnected   │
│ 4. Shows confirmation dialog            │
│ 5. Executes disconnect if confirmed     │
│ 6. Shows success dialog                 │
│ 7. Logs action to console               │
│ 8. Updates UI (tree, tabs, status)     │
│                                         │
└─────────────────────────────────────────┘
```

### Run Query Button
```
┌─────────────────────────────────────────┐
│ RUN QUERY BUTTON BEHAVIOR               │
├─────────────────────────────────────────┤
│                                         │
│ 1. Check: Tab open?                    │
│    ✗ → Show red status "No tab open"   │
│                                         │
│ 2. Check: Connection bound?            │
│    ✗ → Show amber status "No conn"     │
│                                         │
│ 3. Check: SQL not empty?               │
│    ✗ → Show amber status "Empty SQL"   │
│                                         │
│ 4. All checks pass? Execute query      │
│ 5. Status message auto-clears (5 sec)  │
│                                         │
└─────────────────────────────────────────┘
```

---

## Troubleshooting

### Status Message Doesn't Appear
- [ ] Check status bar is visible at bottom
- [ ] Ensure message timer is running
- [ ] Verify color is not matching background
- [ ] Check that original status color is saved correctly

### Confirmation Dialog Doesn't Show
- [ ] Verify JOptionPane is properly initialized
- [ ] Check that connection info is not null
- [ ] Ensure dialog is modal (blocks other actions)

### Auto-Clear Not Working
- [ ] Check that Timer is started (not repeatedly)
- [ ] Verify 5000ms delay is set
- [ ] Ensure updateStatus() is being called
- [ ] Check that label foreground is being restored

### Keyboard Shortcut Not Working
- [ ] This should NOT be affected by changes
- [ ] Check KeyListener in SqlEditorPanel
- [ ] Verify Ctrl+Enter still calls runQuery()
- [ ] Check that event handlers are registered

---

## Test Data

### Sample SQL Queries

```sql
-- Valid SELECT
SELECT * FROM users WHERE id > 100;

-- Valid INSERT
INSERT INTO users (name, email) VALUES ('Test', 'test@example.com');

-- Valid DELETE
DELETE FROM users WHERE id > 1000;

-- Valid UPDATE
UPDATE users SET email = 'new@example.com' WHERE id = 1;
```

### Sample Connections

```
Connection 1:
  Name: Database1
  Type: MySQL
  Host: localhost
  Port: 3306
  Database: testdb

Connection 2:
  Name: Database2
  Type: PostgreSQL
  Host: localhost
  Port: 5432
  Database: testdb
```

---

## Known Limitations

1. Status messages may overlap if user clicks Run Query multiple times within 5 seconds
   - Expected behavior: Latest message takes precedence
   
2. Disconnect confirmation dialog blocks all other UI actions until dismissed
   - Expected behavior: This is by design (standard dialog)

3. Status message color depends on current theme
   - Ensure adequate contrast in all themes

4. Keyboard shortcut cannot be customized
   - Ctrl+Enter is hardcoded
   - Can be enhanced in future versions

---

## Success Criteria

Test is PASSED when:
- ✅ All test cases (D1-D7, DE1-DE3, R1-R7, RE1-RE5) pass
- ✅ No regression in existing functionality
- ✅ Console logs are accurate
- ✅ UI updates are consistent
- ✅ Performance is acceptable
- ✅ No unhandled exceptions
- ✅ All tooltips display correctly
- ✅ All dialogs display correctly
- ✅ Status messages auto-clear after 5 seconds


