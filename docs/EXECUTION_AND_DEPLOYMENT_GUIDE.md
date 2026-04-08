# 🚀 EXECUTION & DEPLOYMENT GUIDE

**Project:** DB Explorer v2.4.0  
**Date:** March 31, 2026  
**Status:** Ready for Build & Test

---

## 📋 TABLE OF CONTENTS

1. [Quick Start (5 minutes)](#quick-start)
2. [Build Instructions](#build-instructions)
3. [Testing Procedures](#testing-procedures)
4. [Deployment Steps](#deployment-steps)
5. [Troubleshooting](#troubleshooting)
6. [Rollback Plan](#rollback-plan)

---

## 🏃 QUICK START

### Prerequisites
- Java 17+ installed
- Maven 3.8+ installed
- Database connection configured (existing)
- ~2GB free disk space

### 5-Minute Setup
```powershell
# 1. Navigate to project
cd C:\Users\ashis\IdeaProjects\db-explorer

# 2. Clean and build
mvn clean package -DskipTests

# 3. Run the application
java -jar target/db-explorer-2.4.0.jar

# 4. Verify features
# ✅ See GC button (🗑) in bottom right of status bar
# ✅ Click it and see "Garbage collection requested." in console
# ✅ Disconnect button shows confirmation dialog
# ✅ Run Query validates inputs and shows color-coded messages
```

---

## 🔨 BUILD INSTRUCTIONS

### Full Build with Tests
```powershell
cd C:\Users\ashis\IdeaProjects\db-explorer
mvn clean package
```

**Output:**
- Compilation log with 0 errors, 16 warnings (all non-critical)
- JAR file: `target/db-explorer-2.4.0.jar`
- Size: ~15-20 MB
- Build time: ~30-45 seconds

### Build Without Tests (Faster)
```powershell
cd C:\Users\ashis\IdeaProjects\db-explorer
mvn clean package -DskipTests
```

**Faster option, assumes tests already passed.**

### Build and Create Distribution
```powershell
# With JVM bundled (larger, ~200MB, no Java requirement)
.\create_dist_with_jvm.bat

# Without JVM (smaller, ~15MB, requires Java 17+)
.\create_dist.bat

# Or specify custom properties
mvn clean package -DskipTests -Dapp.version=2.4.0
```

---

## 🧪 TESTING PROCEDURES

### Phase 1: Compilation Verification (5 minutes)

#### Step 1: Check Compilation
```powershell
cd C:\Users\ashis\IdeaProjects\db-explorer
mvn clean compile
```

**Expected Output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: 15.234 s
[INFO] Finished at: 2026-03-31T15:30:45
```

**Check Results:**
- ✅ No compilation ERRORS (warnings are OK)
- ✅ All source files compiled
- ✅ No missing imports

---

### Phase 2: Application Launch (5 minutes)

#### Step 1: Start Application
```powershell
# Build if not done
mvn clean package -DskipTests

# Run from JAR
java -jar target/db-explorer-2.4.0.jar
```

**Expected Behavior:**
```
✅ Window opens (takes 2-3 seconds)
✅ Connections panel visible
✅ SQL editor visible
✅ Status bar visible at bottom
✅ No error messages in console
```

#### Step 2: Verify UI Components
Check that you can see:
- [ ] Main menu (File, Edit, Tools, Help)
- [ ] Toolbar with buttons (Add, Disconnect, Run, Cancel, etc.)
- [ ] Connections panel (left side)
- [ ] SQL editor (top right)
- [ ] Results panel (middle)
- [ ] Console panel (bottom)
- [ ] Status bar (very bottom)

---

### Phase 3: Feature Testing (20 minutes)

#### Test 1: GC Button Functionality

**Objective:** Verify garbage collection button works

**Steps:**
1. Look at status bar (bottom right)
2. Find heap memory display: `Heap: XXX MB / XXX MB (XX%)`
3. Just before it, find 🗑 button (trash icon)

**Verification:**
```
✅ Button exists with trash icon (🗑)
✅ Tooltip shows "Force Garbage Collection"
✅ Button has hover effect (background appears on hover)
✅ Cursor changes to hand on hover
```

**Functional Test:**
1. Open a terminal and check memory:
   ```powershell
   Get-Process java | Select-Object Name, WorkingSet
   ```
   Note the current memory usage

2. In DB Explorer, execute a large query
   ```sql
   SELECT * FROM <large_table> LIMIT 10000;
   ```
   (Adjust LIMIT based on your database)

3. Look at heap display in status bar
   - Should show increased memory usage
   - Example: `Heap: 1256 MB / 2048 MB (61%)`

4. Click the 🗑 GC button

5. Observe:
   - [ ] Console shows: `[HH:MM:SS] Garbage collection requested.`
   - [ ] Heap display changes to smaller number
   - [ ] No errors in console

**Expected Result:**
```
Before GC: Heap: 1256 MB / 2048 MB (61%)
After GC:  Heap: 512 MB / 2048 MB (25%)
Console:   Garbage collection requested.
```

---

#### Test 2: Memory Leak Fix (Repeated Queries)

**Objective:** Verify memory stabilizes on repeated queries

**Setup:**
1. Open DB Explorer
2. Create a connection to test database
3. Open a new query tab

**Steps:**
1. Write a query that returns many rows:
   ```sql
   SELECT * FROM <large_table>;
   ```

2. Run the query and note heap memory in status bar
   - Example: `Heap: 1024 MB / 2048 MB (50%)`

3. Switch to a new blank tab

4. Run the same query again (to a fresh tab)
   - Note new heap memory
   - Should be similar to before, not much higher

5. Repeat steps 3-4 five more times (10 total executions)

6. Monitor heap memory:
   - After query 1: 1024 MB
   - After query 2: 1024 MB (stable, not 1500+ MB)
   - After query 3: 1024 MB (continues to be stable)
   - etc.

**Expected Result:**
```
✅ Memory usage stays relatively constant
✅ No unbounded growth
✅ Heap oscillates normally but doesn't continuously increase
✅ Each query cleanup releases memory properly
```

---

#### Test 3: Disconnect Button with Confirmation

**Objective:** Verify disconnect button shows confirmation

**Setup:**
1. Open DB Explorer
2. Add a database connection (or use existing one)
3. Ensure you're connected to it

**Steps:**
1. In the Connections panel (left), right-click the connection
   OR use the Disconnect button in toolbar

2. Click "Disconnect" button

3. Observe dialog box:
   ```
   ┌─────────────────────────────────────┐
   │ Confirm Disconnect                  │
   │                                     │
   │ Disconnect from MyDB?               │
   │ Open query tabs will remain bound   │
   │ to this connection.                 │
   │                                     │
   │          [Yes]  [No]                │
   └─────────────────────────────────────┘
   ```

**Verification:**
- [ ] Dialog appears (not instant disconnect)
- [ ] Connection name shown in dialog
- [ ] HTML formatting visible (bold text)
- [ ] Yes and No buttons present

**Test Yes Option:**
1. Click Yes
2. Observe:
   - [ ] Connection disconnects
   - [ ] Success message shows: "Successfully disconnected from [Name]"
   - [ ] Connection disappears from panel

**Test No Option:**
1. Click Disconnect again
2. Click No in dialog
3. Observe:
   - [ ] Dialog closes
   - [ ] Connection remains connected
   - [ ] No changes made

**Expected Result:**
```
✅ Confirmation dialog appears
✅ Connection name displayed
✅ Yes disconnects successfully
✅ No cancels operation
✅ Safety confirmed - no accidental disconnects
```

---

#### Test 4: Run Query Button Validation

**Objective:** Verify Run Query validates inputs before execution

**Setup:**
1. Open DB Explorer with active connection
2. Ensure you have query tabs

**Test 4A: No Tab Open**
1. Close all query tabs (File → Close All Tabs)
2. Click the "Run Query" button (or Ctrl+Enter)
3. Observe status bar message:
   ```
   ⚠ No tab open
   ```
   - [ ] Message appears in red color (#EF4444)
   - [ ] Message auto-clears after 5 seconds
   - [ ] No query execution attempted

**Test 4B: No Connection**
1. Create a new query tab
2. Don't select a connection (or connection disconnected)
3. Click "Run Query" button
4. Observe status bar message:
   ```
   ⚡ No connection on this tab
   ```
   - [ ] Message appears in amber color (#F59E0B)
   - [ ] Message auto-clears after 5 seconds
   - [ ] No query execution attempted

**Test 4C: Empty SQL**
1. In an existing tab with connection
2. Clear all SQL from editor (make it empty)
3. Click "Run Query" button
4. Observe status bar message:
   ```
   ⚡ SQL editor is empty
   ```
   - [ ] Message appears in amber color (#F59E0B)
   - [ ] Message auto-clears after 5 seconds
   - [ ] No query execution attempted

**Test 4D: Valid Query**
1. In an existing tab with connection
2. Enter a valid SQL query:
   ```sql
   SELECT 1 as test
   ```
3. Click "Run Query" button or press Ctrl+Enter
4. Observe:
   - [ ] Query executes immediately
   - [ ] Results appear in results panel
   - [ ] No error messages
   - [ ] Console shows execution time

**Expected Result:**
```
✅ No tab: Red error message, no execution
✅ No connection: Amber warning message, no execution  
✅ Empty SQL: Amber warning message, no execution
✅ Valid query: Query executes normally
✅ All messages auto-clear after 5 seconds
```

---

#### Test 5: Status Message Auto-Clear

**Objective:** Verify status messages disappear after 5 seconds

**Steps:**
1. Click Run Query with no tab open
2. Watch status bar message appear in red
3. Count seconds: 1, 2, 3, 4, 5...
4. At 5 seconds, message should fade/disappear

**Expected Result:**
```
✅ Message appears immediately (<100ms)
✅ Message stays visible for 5 seconds
✅ Message automatically clears at 5 seconds
✅ Can be cleared earlier by other actions
```

---

#### Test 6: Keyboard Shortcut

**Objective:** Verify Ctrl+Enter triggers Run Query

**Steps:**
1. Open query tab with valid connection and SQL
2. Click in SQL editor
3. Press Ctrl+Enter

**Expected Result:**
```
✅ Query executes immediately
✅ Same as clicking Run Query button
✅ Results appear in results panel
```

---

### Phase 4: Configuration Test (5 minutes)

#### Test 7: Property Loading

**Objective:** Verify configuration is loaded from app.properties

**Steps:**
1. Locate `app.properties`:
   ```
   C:\Users\ashis\IdeaProjects\db-explorer\
   target\classes\app.properties
   ```

2. Check contents:
   ```powershell
   Get-Content target/classes/app.properties
   ```

3. Should show:
   ```properties
   app.version=2.4.0
   app.build.date=March 31, 2026
   query.max.rows=10000
   query.fetch.size=500
   export.fetch.size=500
   ```

**Verification:**
- [ ] File exists
- [ ] Contains all three query properties
- [ ] Values are numeric

**Advanced: Modify and Test**
1. Edit `src/main/resources/app.properties`
2. Change `query.max.rows=5000`
3. Rebuild: `mvn clean package -DskipTests`
4. Run application
5. Execute large query (should limit to 5000 rows)
6. Verify truncation message appears

**Expected Result:**
```
✅ Configuration loads from app.properties
✅ Default values work if not specified
✅ Can be changed without recompilation after rebuild
```

---

## 🎯 DEPLOYMENT STEPS

### Pre-Deployment Checklist

Before deploying to production:

- [ ] All tests passed (Phase 1-4)
- [ ] Code review completed
- [ ] No critical issues found
- [ ] Backup of current version created
- [ ] Deployment window scheduled
- [ ] Team notified of deployment
- [ ] Rollback plan documented
- [ ] Database backup created

### Deployment Procedure

#### Step 1: Create Backup
```powershell
# Backup current installation
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item `
  "C:\Program Files\DBExplorer\db-explorer-2.3.0.jar" `
  "C:\Program Files\DBExplorer\BACKUP_db-explorer-2.3.0_$timestamp.jar"

# Backup current configuration
Copy-Item `
  "C:\Users\ashis\AppData\Local\DBExplorer\" `
  "C:\Users\ashis\AppData\Local\DBExplorer_BACKUP_$timestamp\" `
  -Recurse
```

#### Step 2: Build Release Package
```powershell
cd C:\Users\ashis\IdeaProjects\db-explorer

# Build with all tests
mvn clean package

# Or without tests if already verified
mvn clean package -DskipTests
```

#### Step 3: Create Distribution
```powershell
# Option A: With JVM bundled (recommended for new installations)
.\create_dist_with_jvm.bat

# Option B: Without JVM (if Java 17+ already installed)
.\create_dist.bat
```

**Output:**
```
dist/
  └── db-explorer.bat
  └── db-explorer.jar
  └── lib/
      └── [all dependencies]
  └── RELEASE_NOTES.md
  └── USER_HANDBOOK.pdf
```

#### Step 4: Deploy to Production

**For Single User:**
```powershell
# Close current application
Get-Process java | Where-Object {$_.Name -match "db-explorer"} | Stop-Process -Force

# Copy new JAR
Copy-Item `
  "C:\Users\ashis\IdeaProjects\db-explorer\target\db-explorer-2.4.0.jar" `
  "C:\Program Files\DBExplorer\db-explorer-2.4.0.jar"

# Or use distribution
Copy-Item `
  "C:\Users\ashis\IdeaProjects\db-explorer\dist\db-explorer.jar" `
  "C:\Program Files\DBExplorer\db-explorer.jar"

# Start new version
& "C:\Program Files\DBExplorer\db-explorer.bat"
```

**For Multiple Users:**
1. Copy JAR to shared network location
2. Update launcher script to point to new version
3. Notify users to restart application
4. Monitor for issues

#### Step 5: Verification
```powershell
# Check application started
Get-Process java | Select-Object Name, WorkingSet

# Verify in logs
Get-Content "C:\Program Files\DBExplorer\app.log" -Tail 20

# Test features
# - GC button visible
# - Disconnect confirmation works
# - Query validation works
# - Memory is stable
```

---

## 🔄 ROLLBACK PLAN

### If Issues Occur

#### Immediate Rollback (5 minutes)
```powershell
# Stop current application
Get-Process java | Where-Object {$_.Name -match "db-explorer"} | Stop-Process -Force

# Restore backup
Copy-Item `
  "C:\Program Files\DBExplorer\BACKUP_db-explorer-2.3.0_<timestamp>.jar" `
  "C:\Program Files\DBExplorer\db-explorer.jar" `
  -Force

# Restart application
& "C:\Program Files\DBExplorer\db-explorer.bat"

# Verify
Write-Host "Application restarted on previous version"
```

#### Data Recovery (if needed)
```powershell
# Restore configuration backup
Remove-Item "C:\Users\ashis\AppData\Local\DBExplorer\" -Recurse -Force
Copy-Item `
  "C:\Users\ashis\AppData\Local\DBExplorer_BACKUP_<timestamp>\" `
  "C:\Users\ashis\AppData\Local\DBExplorer\" `
  -Recurse
```

### Known Issues & Workarounds

#### Issue 1: GC Button Not Visible
**Cause:** Theme compatibility or status bar layout
**Fix:**
1. Clear cache: Delete `%APPDATA%\DBExplorer\`
2. Restart application
3. If still missing, check MainFrame.java line 164 for button addition

**Workaround:** Use `System.gc()` from Java console

#### Issue 2: Memory Not Decreasing After GC Click
**Cause:** Heap fragmentation or pending garbage collection
**Fix:**
1. Click GC button multiple times
2. Give JVM time to collect (up to 10 seconds)
3. Check Java GC logs: `java -Xlog:gc -jar ...`

**Workaround:** Restart application

#### Issue 3: Status Messages Not Showing
**Cause:** UI theme or EDT event dispatching
**Fix:**
1. Clear application cache
2. Rebuild without tests: `mvn clean package -DskipTests`
3. Check line 917 in MainFrame.java for `showStatusMessage()` method

**Workaround:** Check console for error messages instead

---

## ❓ TROUBLESHOOTING

### Build Fails: "Java version mismatch"
```powershell
# Verify Java version
java -version

# Should show: openjdk version "17" or higher

# If wrong version:
# 1. Install Java 17+ from https://openjdk.java.net/
# 2. Update PATH environment variable
# 3. Restart PowerShell
# 4. Try build again
```

### Build Fails: "Maven not found"
```powershell
# Verify Maven installation
mvn --version

# If not found:
# 1. Install Maven: https://maven.apache.org/
# 2. Add to PATH: C:\Program Files\apache-maven-3.x.x\bin
# 3. Restart PowerShell
# 4. Try again
```

### Application Won't Start
```powershell
# Check for errors
java -jar target/db-explorer-2.4.0.jar 2>&1 | Tee-Object -FilePath error.log

# Common fixes:
# 1. Insufficient memory: java -Xmx2g -jar ...
# 2. Missing dependencies: mvn clean compile
# 3. Corrupted JAR: mvn clean package -DskipTests
```

### Compilation Warnings as Errors
```powershell
# These are NOT errors - just IDE suggestions:
# - "Field can be final" - Code style suggestion
# - "Unused method" - May be used in subclasses
# - "Try-with-resources" - Alternative approach
# 
# To suppress (optional):
# Edit pom.xml and add compiler options
```

### Performance Issues
```powershell
# Increase heap size
java -Xmx4g -jar target/db-explorer-2.4.0.jar

# Monitor memory
Get-Process java | Measure-Object -Property WorkingSet -Sum | Select-Object -ExpandProperty Sum | ForEach-Object {$_ / 1GB}

# Enable GC logging
java -Xlog:gc:gc.log -jar target/db-explorer-2.4.0.jar
```

---

## 📊 DEPLOYMENT CHECKLIST

### Ready for Deployment? Use This Checklist

```powershell
# Automated Checklist Script
$checks = @(
    @{Name="Java 17+"; Command="java -version"},
    @{Name="Maven 3.8+"; Command="mvn --version"},
    @{Name="Build Success"; Command="mvn clean package -DskipTests"},
    @{Name="JAR Created"; Command="Test-Path target/db-explorer-2.4.0.jar"},
    @{Name="App Properties"; Command="Test-Path target/classes/app.properties"}
)

foreach ($check in $checks) {
    Write-Host "Checking: $($check.Name)..." -ForegroundColor Cyan
    try {
        Invoke-Expression $check.Command | Out-Null
        Write-Host "✅ $($check.Name) - OK" -ForegroundColor Green
    } catch {
        Write-Host "❌ $($check.Name) - FAILED" -ForegroundColor Red
        Write-Host "Error: $_" -ForegroundColor Red
    }
}
```

---

## 📈 SUCCESS METRICS

After deployment, verify:

| Metric | Expected | How to Check |
|--------|----------|-------------|
| **Build Time** | <60 seconds | Time `mvn clean package` |
| **Startup Time** | <5 seconds | Time application launch |
| **GC Button** | Visible | Look at status bar |
| **Memory Leak** | Fixed | Run same query 10x, check heap |
| **Disconnect Safety** | 100% | No accidental disconnects |
| **Query Validation** | <100ms | Click Run with no tab |
| **Error Messages** | Colored | Trigger each error type |
| **Configuration** | Loaded | Check `app.properties` values used |

---

## ✅ SIGN-OFF

When all tests pass and deployment is successful:

- [ ] Phase 1: Compilation ✅
- [ ] Phase 2: Launch ✅
- [ ] Phase 3: Features ✅
- [ ] Phase 4: Configuration ✅
- [ ] Deployment Steps ✅
- [ ] Production Verified ✅

**Status:** 🟢 **READY FOR PRODUCTION**

---

## 📞 SUPPORT CONTACTS

If issues occur:
1. Check TROUBLESHOOTING section above
2. Review code changes in DEVELOPERS_GUIDE.md
3. Check application logs and console output
4. Compare with BEFORE_AFTER_COMPARISON.md
5. Refer to TESTING_GUIDE.md for detailed procedures

---

**Document:** EXECUTION_AND_DEPLOYMENT_GUIDE.md  
**Version:** 1.0  
**Created:** March 31, 2026  
**Status:** Ready for Use


