# Demo en vivo de la biblioteca en C (libgrafo.so) llamada por JNA desde ruta-service.
# Requisito: el sistema arriba con "docker compose up -d" y la primera ruta respondiendo.
#
# Uso (desde PowerShell):
#   .\scripts\demo_biblioteca.ps1           # corre los 5 pasos, con pausa entre cada uno
#   .\scripts\demo_biblioteca.ps1 -SinPausa # sin pausas (para ensayar)
#
# El token del usuario consultor se pide a Keycloak con las credenciales de prueba
# del realm (keycloak/rutas-realm.json); no se imprime.
param(
    [string]$Repo = (Split-Path -Parent $PSScriptRoot),
    [string]$Gateway = "http://localhost:8080",
    [string]$Keycloak = "http://localhost:8085",
    [switch]$SinPausa
)
$ErrorActionPreference = "Stop"

function Titulo([string]$texto) {
    Write-Host ""
    Write-Host "== $texto ==" -ForegroundColor Yellow
}
function Pausa {
    if (-not $SinPausa) { Read-Host "  [Enter para seguir]" | Out-Null }
}

# ---------- Token del consultor ----------
$realm = Get-Content (Join-Path $Repo "keycloak/rutas-realm.json") -Raw | ConvertFrom-Json
$u = $realm.users | Where-Object { $_.username -eq "consultor" }
$cuerpo = @{
    grant_type = "password"; client_id = "red-rutas-frontend"
    username = "consultor"; password = ($u.credentials | Select-Object -First 1).value
}
$token = (Invoke-RestMethod -Method Post -Uri "$Keycloak/realms/rutas-realm/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" -Body $cuerpo).access_token
$cabecera = @{ Authorization = "Bearer $token" }

function Ruta([string]$consulta) {
    $t = [System.Diagnostics.Stopwatch]::StartNew()
    $r = Invoke-RestMethod -Uri "$Gateway/api/rutas/calcular?$consulta" -Headers $cabecera
    $t.Stop()
    [pscustomobject]@{
        modoEfectivo = $r.modoEfectivo
        costoTotal   = $r.costoTotal
        paradas      = $r.camino.Count
        camino       = ($r.camino -join " > ")
        ms_http      = [math]::Round($t.Elapsed.TotalMilliseconds, 1)
    }
}

# ---------- 1. La biblioteca existe dentro del contenedor ----------
Titulo "1. libgrafo.so dentro del contenedor (compilada con gcc en la etapa 1 del Dockerfile)"
docker exec ruta-service ls -l /usr/lib/libgrafo.so
Write-Host "  Imagen final sin gcc ni codigo fuente C:" -ForegroundColor DarkGray
docker exec ruta-service sh -c "which gcc || echo '  gcc: no esta'"
Pausa

# ---------- 2. Java la cargo al arrancar ----------
Titulo "2. Log de arranque de ruta-service: JNA cargo la biblioteca"
docker logs ruta-service 2>&1 | Select-String "main\].*(biblioteca nativa libgrafo|no se pudo cargar la biblioteca)" | Select-Object -Last 1 | ForEach-Object { "  " + $_.Line.Trim() }
Pausa

# ---------- 3. Misma ruta por C y por Java ----------
Titulo "3. Terminal de Transportes (0) -> Zona Rural La Marina (39): C por JNA contra Java"
$c = Ruta "desde=0&hasta=39&modo=native"
$j = Ruta "desde=0&hasta=39&modo=java"
$c, $j | Format-Table -AutoSize
if ($c.costoTotal -eq $j.costoTotal -and $c.camino -eq $j.camino) {
    Write-Host "  Mismo costo y mismo camino en C y en Java (regla de desempate D-06)." -ForegroundColor Green
}
Pausa

# ---------- 4. Codigo de error de C: no hay ruta ----------
Titulo "4. Cerrar el tramo 90: la Zona Rural queda aislada y dijkstra() devuelve -1 (GRAFO_SIN_RUTA)"
Ruta "desde=0&hasta=39&modo=native&sin=90" | Format-Table -AutoSize
Write-Host "  costoTotal -1 y camino vacio: Java traduce el codigo negativo de C, no se cae." -ForegroundColor Green
Pausa

# ---------- 5. Parada que no existe ----------
Titulo "5. Parada inexistente (hasta=99): se rechaza antes de llegar a C"
# curl.exe abre una conexion nueva (Invoke-WebRequest reutiliza una que el gateway ya cerro)
$codigo = curl.exe -s -o NUL -w "%{http_code}" -H "Authorization: Bearer $token" "$Gateway/api/rutas/calcular?desde=0&hasta=99&modo=native"
Write-Host "  HTTP $codigo -> el mismo caso que en C seria -2 (GRAFO_FUERA_DE_RANGO)" -ForegroundColor Green
Write-Host ""
