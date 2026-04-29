@echo off
setlocal
cd /d "%~dp0"
color 06

:menu
cls
title Taiko Phone Drum
echo.
echo ============================================================
echo                  TAIKO PHONE DRUM HUB
echo ============================================================
echo.
echo  Phone APK: four-zone taiko controller
echo  Receiver : scan-code keyboard input for Taiko no Tatsujin
echo.
echo  [1] Start Wi-Fi receiver
echo  [2] Start Wi-Fi receiver with token
echo  [3] Start Wi-Fi receiver as Administrator
echo  [4] Setup USB mode
echo  [5] Install APK to connected phone
echo  [6] Build APK from source
echo  [7] Exit
echo.
choice /C 1234567 /N /M "Choose an option: "

if errorlevel 7 goto exit
if errorlevel 6 goto build
if errorlevel 5 goto install
if errorlevel 4 goto usb
if errorlevel 3 goto receiver_admin
if errorlevel 2 goto receiver_token
if errorlevel 1 goto receiver

:receiver
cls
color 04
echo Starting Wi-Fi receiver on port 27183...
echo Type the shown PC IP into the phone app, then tap Wi-Fi.
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-receiver.ps1" --port 27183
echo.
pause
color 06
goto menu

:receiver_admin
cls
color 0C
echo Starting Wi-Fi receiver as Administrator on port 27183...
echo Accept the Windows UAC prompt.
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell.exe -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -NoExit -File ""%~dp0scripts\run-receiver.ps1"" --port 27183'"
echo.
pause
color 06
goto menu

:receiver_token
cls
color 04
set "TOKEN="
set /p TOKEN=Enter the token shown in the phone app: 
if "%TOKEN%"=="" goto receiver
echo.
echo Starting Wi-Fi receiver on port 27183 with token %TOKEN%...
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-receiver.ps1" --port 27183 --token "%TOKEN%"
echo.
pause
color 06
goto menu

:usb
cls
color 01
echo Setting up USB mode with ADB reverse on port 27183...
echo After this, tap USB in the phone app.
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\usb-adb-reverse.ps1" -Port 27183
echo.
pause
color 06
goto menu

:build
cls
color 06
echo Building debug APK...
if not exist "%~dp0gradlew.bat" (
    echo.
    echo Source project files are not included in this release package.
    echo Download the repository source if you want to rebuild the APK.
    echo.
    pause
    goto menu
)
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-android.ps1"
echo.
pause
goto menu

:install
cls
color 02
echo Installing debug APK to connected phone...
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\install-apk.ps1"
echo.
pause
color 06
goto menu

:exit
exit /b 0
