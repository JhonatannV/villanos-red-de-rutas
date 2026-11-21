@echo off
rem Doble clic: arranca Red de Rutas (Docker, contenedores, primera ruta) y abre la app.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0arrancar.ps1" %*
