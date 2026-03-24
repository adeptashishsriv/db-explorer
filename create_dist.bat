@echo off
echo ==========================================
echo      DB Explorer Distribution Builder
echo ==========================================

REM Check if Maven is installed
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: Maven is not found in your PATH.
    echo Please install Maven and try again.
    pause
    exit /b 1
)

echo.
echo [1/3] Cleaning and packaging project...
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo Build failed! Please check the console output for errors.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Creating distribution directory...
if exist dist rmdir /s /q dist
mkdir dist

echo.
echo [3/3] Copying files...
REM Copy the Uber-JAR
if exist target\db-explorer-1.0.0.jar (
    copy target\db-explorer-1.0.0.jar dist\
) else (
    echo Error: Target JAR not found!
    pause
    exit /b 1
)

REM Copy Documentation
if exist USER_HANDBOOK.md copy USER_HANDBOOK.md dist\

REM Create a handy run script for the end user
echo @echo off > dist\db-explorer.bat
echo start javaw -jar db-explorer-1.0.0.jar >> dist\db-explorer.bat

echo.
echo ==========================================
echo      Distribution Created Successfully!
echo ==========================================
echo.
echo The runnable package is located in the 'dist' folder.
echo You can now ZIP the 'dist' folder and send it to your users.
echo.
pause
