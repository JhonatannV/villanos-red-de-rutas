@echo off
rem Doble clic: apaga todos los contenedores de Red de Rutas.
cd /d "%~dp0.."
docker compose down
echo.
pause
