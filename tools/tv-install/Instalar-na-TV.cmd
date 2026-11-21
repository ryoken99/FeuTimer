@echo off
setlocal
cd /d "%~dp0"
if not exist "FeuTimer-AndroidTV.apk" (
  echo Extrai primeiro o ZIP completo para uma pasta.
  goto failed
)
where adb >nul 2>&1
if errorlevel 1 (
  echo Instala Android SDK Platform-Tools e coloca adb no PATH. Consulta INSTALAR-NA-TV.md.
  goto failed
)
adb get-state >nul
if errorlevel 1 (
  echo Liga e autoriza a TV. Com varios dispositivos, define ANDROID_SERIAL.
  adb devices -l
  goto failed
)
adb install -r "FeuTimer-AndroidTV.apk"
if errorlevel 1 goto failed
adb shell am start -W -n pt.feubjj.timer/.MainActivity
if errorlevel 1 goto failed
echo FeuTimer instalado. A aplicacao foi aberta na TV.
if /i not "%~1"=="--sem-pausa" pause
exit /b 0
:failed
echo A instalacao nao terminou. Consulta a mensagem acima.
if /i not "%~1"=="--sem-pausa" pause
exit /b 1
