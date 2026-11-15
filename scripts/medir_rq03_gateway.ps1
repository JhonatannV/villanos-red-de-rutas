# =========================================================================
# SCRUM-36 [W-RQ-03] Medicion de Latencia en API Gateway (p95 < 400 ms)
#
# Escenario: Un usuario pide la ruta mas corta en la red grande (400 paradas, 1.100 tramos).
# Criterios de Aceptacion:
#   1. Ejecucion de 20 consultas consecutivas midiendo latencia desde el Gateway.
#   2. Percentil 95 (p95) inferior a 400 ms.
#   3. Pedir la traza paso a paso no duplica el tiempo (ratio < 2.0x).
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File scripts/medir_rq03_gateway.ps1
# =========================================================================

param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$KeycloakUrl = "http://localhost:8085",
    [int]$Repeticiones = 20,
    [double]$LimiteP95Ms = 400.0,
    [double]$LimiteRatioTraza = 2.0
)

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot

Write-Host "=========================================================================" -ForegroundColor Cyan
Write-Host " BENCHMARK AUTOMATIZADO SCRUM-36 [W-RQ-03] - LATENCIA API GATEWAY" -ForegroundColor Cyan
Write-Host " Equipo Villanos - UCEVA (Jhonatan Varon Henao)" -ForegroundColor Cyan
Write-Host "=========================================================================" -ForegroundColor Cyan

# 1. Ejecutar script de benchmark de 20 consultas consecutivas
$scriptNode = Join-Path $PSScriptRoot "medir_rq03_gateway.js"
& node $scriptNode

if ($LASTEXITCODE -ne 0) {
    Write-Error "Fallo la ejecucion del benchmark de latencia en Gateway."
    exit 1
}

Write-Host "`n=========================================================================" -ForegroundColor Green
Write-Host " TODOS LOS CRITERIOS DEL REQUERIMIENTO W-RQ-03 SE CUMPLEN AL 100%!" -ForegroundColor Green
Write-Host " Evidencia registrada en docs/mediciones/SCRUM-36-latencia-gateway.md" -ForegroundColor Green
Write-Host " Archivo CSV disponible en docs/mediciones/w_rq03_latencia_gateway.csv" -ForegroundColor Green
Write-Host "=========================================================================" -ForegroundColor Green
