@echo off
set SCRIPT_DIR=%~dp0
start "" "%SCRIPT_DIR%jre\bin\javaw.exe" -jar "%SCRIPT_DIR%db-explorer-2.4.0.jar"
