@echo off
title Word Hunt Arena - Game Server (TCP 5555 / RMI 1099)
cd /d "%~dp0"
echo ==============================================================
echo   WORD HUNT ARENA - STARTING SERVER (TCP: 5555, RMI: 1099)
echo ==============================================================

if not exist "target\classes\edu\ieu\se360\wordhunt\network\GameServer.class" (
    echo [INFO] Compiled classes not found. Running Maven compile + copy-dependencies...
    call mvn compile dependency:copy-dependencies
) else if not exist "target\dependency\*" (
    echo [INFO] Dependencies folder not found. Copying runtime dependencies...
    call mvn dependency:copy-dependencies
)

java -cp "target\classes;target\dependency\*" edu.ieu.se360.wordhunt.network.GameServer
pause
