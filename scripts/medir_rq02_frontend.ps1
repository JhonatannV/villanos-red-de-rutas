# =========================================================================
# SCRUM-35 [W-RQ-02] Medicion de Desempeno Frontend Angular
#
# Escenario: La red crece de 40 a 400 paradas y 1.100 tramos.
# Criterios de Aceptacion:
#   1. Dibujo inicial < 2 s (2.000 ms).
#   2. Crecimiento lineal en tramos O(M).
#   3. Ninguna tarea del hilo principal > 100 ms.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File scripts/medir_rq02_frontend.ps1
# =========================================================================

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot

Write-Host "=========================================================================" -ForegroundColor Cyan
Write-Host " BENCHMARK AUTOMATIZADO SCRUM-35 [W-RQ-02] - DESEMPENO FRONTEND ANGULAR" -ForegroundColor Cyan
Write-Host " Equipo Villanos - UCEVA (Jhonatan Varon Henao)" -ForegroundColor Cyan
Write-Host "=========================================================================" -ForegroundColor Cyan

# 1. Ejecutar benchmark de complejidad temporal asintotica
Write-Host "`n[1/2] Ejecutando analisis de complejidad O(M) y resolucion espacial..." -ForegroundColor Yellow
$scriptNode = Join-Path $PSScriptRoot "medir_rq02_frontend.js"
& node $scriptNode

if ($LASTEXITCODE -ne 0) {
    Write-Error "Fallo la ejecucion del benchmark de JavaScript."
    exit 1
}

# 2. Ejecutar suite de pruebas unitarias en Angular Testbed
Write-Host "`n[2/2] Ejecutando pruebas unitarias de renderizado DOM SVG en Angular..." -ForegroundColor Yellow
$frontendDir = Join-Path $raiz "frontend/red-rutas-web"

Push-Location $frontendDir
try {
    & npm test -- --watch=false
} finally {
    Pop-Location
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "Fallo la suite de pruebas unitarias de Angular."
    exit 1
}

Write-Host "`n=========================================================================" -ForegroundColor Green
Write-Host " TODOS LOS CRITERIOS DEL REQUERIMIENTO W-RQ-02 SE CUMPLEN AL 100%!" -ForegroundColor Green
Write-Host " Evidencia registrada en docs/mediciones/SCRUM-35-desempeno-frontend.md" -ForegroundColor Green
Write-Host "=========================================================================" -ForegroundColor Green
