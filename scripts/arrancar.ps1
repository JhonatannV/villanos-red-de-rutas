# Arranca Red de Rutas completo y deja la app lista para la demo.
#   1. Abre Docker Desktop si no esta corriendo y espera al motor.
#   2. docker compose up -d --build en el repo: recompila lo que cambio en el codigo,
#      para no levantar una imagen vieja. Lo que no cambio sale de la cache.
#   3. Espera a que los 5 contenedores esten sanos.
#   4. Calienta el sistema: pide la primera ruta hasta que responda 200.
#   5. Abre la app en el navegador.
# Se usa con doble clic en scripts\arrancar.bat.
param(
    [string]$Repo = (Split-Path -Parent $PSScriptRoot),
    [int]$EsperaMax = 240
)
# Continue: docker escribe su progreso en stderr y PowerShell 5.1 lo trataria como error
$ErrorActionPreference = "Continue"
$Repo = (Resolve-Path $Repo -ErrorAction Stop).Path

function Paso([string]$t) { Write-Host ""; Write-Host ">> $t" -ForegroundColor Yellow }
function Ok([string]$t)   { Write-Host "   OK  $t" -ForegroundColor Green }
function Falla([string]$t) {
    Write-Host ""; Write-Host "   ERROR  $t" -ForegroundColor Red
    Read-Host "Enter para cerrar" | Out-Null; exit 1
}
function MotorListo { cmd /c "docker info >nul 2>&1"; return ($LASTEXITCODE -eq 0) }

# Docker Desktop deja "sockets viejos" si Windows se apaga, reinicia o hiberna con Docker abierto.
# Al volver a abrirlo falla con: rename ...\Docker\run\*.sock ... .sock.stale: The file cannot be
# accessed by the system. Cerrarlo del todo y mover esas dos carpetas lo evita: Docker las vuelve
# a crear limpias al arrancar. No se pierde nada (solo tienen sockets temporales).
function Limpiar-DockerViejo {
    Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*docker*" } |
        Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    $sello = Get-Date -Format "yyyyMMdd-HHmmss"
    foreach ($c in @("$env:LOCALAPPDATA\Docker\run", "$env:LOCALAPPDATA\docker-secrets-engine")) {
        if (Test-Path $c) {
            try {
                Rename-Item $c "$(Split-Path $c -Leaf).viejo-$sello" -ErrorAction Stop
                Write-Host "   limpiado: $c"
            } catch {
                Write-Host "   no se pudo mover $c ($($_.Exception.Message))" -ForegroundColor DarkYellow
            }
        }
    }
    # Copias de limpiezas anteriores: se borran si ya no estan bloqueadas
    Get-ChildItem "$env:LOCALAPPDATA\Docker", "$env:LOCALAPPDATA" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(run|docker-secrets-engine)\.viejo-' -and $_.Name -notlike "*$sello" } |
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
}

function Abrir-Docker([string]$exe, [int]$limite) {
    Limpiar-DockerViejo
    Write-Host "   Abriendo Docker Desktop..."
    Start-Process $exe
    $t = 0
    while (-not (MotorListo)) {
        Start-Sleep -Seconds 3; $t += 3
        Write-Host -NoNewline "."
        if ($t -ge $limite) { Write-Host ""; return $false }
    }
    Write-Host ""
    return $true
}

Write-Host "=== Red de Rutas - Equipo Villanos ===" -ForegroundColor Cyan

# ---------- 1. Docker ----------
Paso "1/5 Docker"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Falla "No se encontro Docker. Instala Docker Desktop." }
if (-not (MotorListo)) {
    $exe = @("$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe",
             "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $exe) { Falla "Docker no esta corriendo y no encontre Docker Desktop. Abrelo a mano y vuelve a intentar." }
    if (-not (Abrir-Docker $exe 150)) {
        Write-Host "   Docker no arranco a la primera; limpio otra vez y reintento..." -ForegroundColor DarkYellow
        if (-not (Abrir-Docker $exe 180)) {
            Falla "Docker no arranco. Reinicia el computador (Reiniciar, no Apagar) y vuelve a correr arrancar.bat."
        }
    }
}
Ok "motor de Docker listo"

# ---------- 2. Levantar ----------
Paso "2/5 docker compose up -d --build (recompila lo que haya cambiado)"
Push-Location $Repo
try { docker compose up -d --build; if ($LASTEXITCODE -ne 0) { Falla "docker compose up fallo (mira el mensaje de arriba)." } }
finally { Pop-Location }
Ok "imagenes al dia y contenedores creados"

# ---------- 3. Esperar a que esten sanos ----------
Paso "3/5 Esperando a que los 5 servicios esten sanos"
$nombres = @("keycloak", "grafo-service", "ruta-service", "api-gateway", "frontend")
$inicio = Get-Date
do {
    $estados = foreach ($n in $nombres) {
        $e = cmd /c "docker inspect -f ""{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}"" $n 2>nul"
        [pscustomobject]@{ servicio = $n; estado = "$e".Trim() }
    }
    $listos = @($estados | Where-Object { $_.estado -in @("healthy", "running") }).Count
    $seg = [int]((Get-Date) - $inicio).TotalSeconds
    Write-Host ("   {0,3} s  {1}/5 sanos" -f $seg, $listos)
    if ($listos -eq 5) { break }
    if ($seg -ge $EsperaMax) {
        $estados | Format-Table -AutoSize | Out-String | Write-Host
        Falla "No todos los servicios quedaron sanos en $EsperaMax s. Revisa: docker compose logs <servicio>"
    }
    Start-Sleep -Seconds 4
} while ($true)
Ok "los 5 servicios estan sanos"

# ---------- 4. Calentar ----------
Paso "4/5 Calentando: primera ruta Terminal (0) -> Zona Rural (39)"
$realm = Get-Content (Join-Path $Repo "keycloak/rutas-realm.json") -Raw | ConvertFrom-Json
$u = $realm.users | Where-Object { $_.username -eq "consultor" }
$cuerpo = @{ grant_type = "password"; client_id = "red-rutas-frontend"; username = "consultor"
             password = ($u.credentials | Select-Object -First 1).value }
$t = 0
do {
    try {
        $token = (Invoke-RestMethod -Method Post -Uri "http://localhost:8085/realms/rutas-realm/protocol/openid-connect/token" `
                  -ContentType "application/x-www-form-urlencoded" -Body $cuerpo -ErrorAction Stop).access_token
        $r = Invoke-RestMethod -Uri "http://localhost:8080/api/rutas/calcular?desde=0&hasta=39" -Headers @{ Authorization = "Bearer $token" } -ErrorAction Stop
        Ok ("ruta calculada: costo {0} min, {1} paradas, motor {2}" -f $r.costoTotal, $r.camino.Count, $r.modoEfectivo)
        break
    } catch {
        $t += 5
        if ($t -ge 90) { Write-Host "   La primera ruta no respondio en 90 s; la app abre igual, prueba recargar." -ForegroundColor DarkYellow; break }
        Write-Host "   aun no responde (normal en el arranque), reintento en 5 s..."
        Start-Sleep -Seconds 5
    }
} while ($true)

# ---------- 5. Abrir ----------
Paso "5/5 Abriendo la app"
Start-Process "http://localhost:4200"
Write-Host ""
Write-Host "  App ............ http://localhost:4200" -ForegroundColor Cyan
Write-Host "  API Gateway .... http://localhost:8080"
Write-Host "  Keycloak ....... http://localhost:8085"
Write-Host "  Usuarios de prueba: ver README, seccion Keycloak (consultor y planificador)."
Write-Host "  Para apagar todo: scripts\detener.bat"
Write-Host ""
Read-Host "Listo. Enter para cerrar esta ventana" | Out-Null
