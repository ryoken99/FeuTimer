@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>&1 || goto deps
where python >nul 2>&1 || goto deps
where gh >nul 2>&1 || goto deps
gh auth status --hostname github.com >nul 2>&1 || goto deps
node launcher.cjs "%LOCALAPPDATA%\FeuTimer-PC\instalacao" 8792 --open
if errorlevel 1 pause
exit /b
:deps
echo Instala Node.js 18 ou posterior, Python 3.9 ou posterior e GitHub CLI.
echo Inicia sessao com gh auth login numa conta com acesso ao FeuTimer.
pause
exit /b 1
