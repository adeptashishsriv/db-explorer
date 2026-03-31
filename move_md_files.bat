@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Move all .md files to docs folder
REM Run from: C:\Users\ashis\IdeaProjects\db-explorer\
REM ============================================

title Moving Markdown Files...

cd /d "C:\Users\ashis\IdeaProjects\db-explorer" || exit /b 1

REM Create docs folder if it doesn't exist
if not exist "docs\" (
    echo Creating docs folder...
    mkdir docs
)

set "count=0"
set "skip=0"

echo.
echo ====================================================
echo Moving markdown files to docs folder...
echo ====================================================
echo.

REM Loop through all .md files in root
for %%F in (*.md) do (
    if exist "docs\%%F" (
        echo [SKIP] docs\%%F already exists
        set /a skip+=1
    ) else (
        echo [MOVE] %%F ^-^> docs\
        move "%%F" "docs\%%F" >nul 2>&1
        if !errorlevel! equ 0 (
            set /a count+=1
        ) else (
            echo [ERROR] Failed to move %%F
        )
    )
)

echo.
echo ====================================================
echo Results:
echo   Moved:  !count! files
echo   Skipped: !skip! files
echo ====================================================
echo.

REM List files in docs folder
echo Files in docs folder:
echo.
dir "docs\*.md" /B

echo.
echo ✅ Done! All markdown files are now in the docs/ folder
echo.
echo 📂 Location: C:\Users\ashis\IdeaProjects\db-explorer\docs\
echo 📖 Start with: docs\README.md or docs\QUICK_START.md
echo.

pause

