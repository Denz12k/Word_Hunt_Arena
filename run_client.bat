@echo off
title Word Hunt Arena - Player Client Launcher
cd /d "%~dp0"
echo ==============================================================
echo   WORD HUNT ARENA - LAUNCHING SWING PLAYER CLIENT
echo ==============================================================

if not exist "target\classes\edu\ieu\se360\wordhunt\gui\MainClientApp.class" (
    echo [INFO] Compiled classes not found. Running Maven compile + copy-dependencies...
    call mvn compile dependency:copy-dependencies
) else if not exist "target\dependency\*" (
    echo [INFO] Dependencies folder not found. Copying runtime dependencies...
    call mvn dependency:copy-dependencies
)

start "Word Hunt Arena - Client" javaw -cp "target\classes;target\dependency\*" edu.ieu.se360.wordhunt.gui.MainClientApp
