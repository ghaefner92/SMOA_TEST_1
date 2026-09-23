@echo off
setlocal
cd /d "%~dp0"

if not exist "local.properties" (
  echo ERROR: falta local.properties.
  echo Copie local.properties.example a local.properties y ajuste sdk.dir.
  pause
  exit /b 1
)

call gradlew.bat testLocalEmulatorDebugUnitTest lintLocalEmulatorDebug assembleLocalEmulatorDebug assembleLocalUsbDebug
if errorlevel 1 (
  echo ERROR: la compilacion o una prueba fallo.
  pause
  exit /b 1
)

echo.
echo APK emulador: app\build\outputs\apk\localEmulator\debug\app-localEmulator-debug.apk
echo APK USB: app\build\outputs\apk\localUsb\debug\app-localUsb-debug.apk
pause
