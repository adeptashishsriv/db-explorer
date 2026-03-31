@echo off
REM Move all .md files to docs folder

cd /d "C:\Users\ashis\IdeaProjects\db-explorer"

REM Create docs folder
if not exist "docs" mkdir docs

REM Move all .md files
echo Moving markdown files to docs folder...
for %%F in (*.md) do (
    move "%%F" "docs\%%F" >nul 2>&1
    echo Moved: %%F
)

echo.
echo Complete! All .md files moved to docs folder.
pause

