# =========================================================================
# SCRUM-18 [TEST-01] Medicion de RQ-01 Disponibilidad
#
# Escenario: ruta-service se cae mientras alguien consulta la red.
#   - El gateway responde 503 con un mensaje que explica la falla.
#   - Cada respuesta llega en menos de 3 s (timeout del gateway: 2 s).
#   - grafo-service sigue respondiendo, asi que el mapa se puede dibujar.
#   - Al volver el servicio, las rutas funcionan otra vez en menos de 30 s
#     sin reiniciar ni reconfigurar nada.
#
# Se miden dos fallas:
#   apagado  -> docker stop  (el proceso muere: conexion rechazada)
#   colgado  -> docker pause (el proceso no responde: vence el timeout)
#
# Uso (con el sistema levantado con docker compose up -d):
#   powershell -ExecutionPolicy Bypass -File scripts/medir_rq01.ps1
#   powershell -ExecutionPolicy Bypass -File scripts/medir_rq01.ps1 -Falla apagado -Repeticiones 10
# =========================================================================
param(
    [string]$Gateway = "http://localhost:8080",
    [string]$Keycloak = "http://localhost:8085",
    [string]$Usuario = "consultor",
    [string]$Clave = "",
    [string]$Contenedor = "ruta-service",
    [int]$Repeticiones = 10,
    [ValidateSet("apagado", "colgado", "ambos")]
    [string]$Falla = "ambos",
    [int]$LimiteRespuestaMs = 3000,
    [int]$LimiteRecuperacionS = 30,
    [string]$Salida = "docs/mediciones"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

$raiz = Split-Path -Parent $PSScriptRoot
$consultaRuta = "/api/rutas/calcular?desde=0&hasta=11"   # Terminal -> Coliseo Benicio, costo esperado 10
$consultaRed = "/api/redes/1"

function Escribir($texto, $color = "Gray") { Write-Host $texto -ForegroundColor $color }

# ---------- Token de Keycloak (usuario de prueba del realm) ----------
function Obtener-Token {
    $clave = $Clave
    if ([string]::IsNullOrEmpty($clave)) {
        $realm = Get-Content (Join-Path $raiz "keycloak/rutas-realm.json") -Raw | ConvertFrom-Json
        $u = $realm.users | Where-Object { $_.username -eq $Usuario }
        $clave = ($u.credentials | Select-Object -First 1).value
    }
    $cuerpo = @{
        grant_type = "password"; client_id = "red-rutas-frontend"
        username = $Usuario; password = $clave
    }
    $r = Invoke-RestMethod -Method Post -Uri "$Keycloak/realms/rutas-realm/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" -Body $cuerpo
    return $r.access_token
}

# ---------- Una peticion medida ----------
$cliente = New-Object System.Net.Http.HttpClient
$cliente.Timeout = [TimeSpan]::FromSeconds(15)

function Medir([string]$ruta) {
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, "$Gateway$ruta")
    $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $script:token)
    $reloj = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $resp = $cliente.SendAsync($req).GetAwaiter().GetResult()
        $cuerpo = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $reloj.Stop()
        $json = $null
        try { $json = $cuerpo | ConvertFrom-Json } catch { }
        return [pscustomobject]@{
            ms = [math]::Round($reloj.Elapsed.TotalMilliseconds, 1)
            estado = [int]$resp.StatusCode
            motivo = if ($json -and $json.motivo) { $json.motivo } else { "" }
            mensaje = if ($json -and $json.mensaje) { $json.mensaje } else { "" }
            costo = if ($json -and $null -ne $json.costoTotal) { $json.costoTotal } else { $null }
        }
    } catch {
        $reloj.Stop()
        return [pscustomobject]@{
            ms = [math]::Round($reloj.Elapsed.TotalMilliseconds, 1); estado = 0
            motivo = "SIN_RESPUESTA"; mensaje = $_.Exception.Message; costo = $null
        }
    }
}

function Invocar-Docker([string[]]$argumentos) {
    & docker.exe @argumentos | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "docker $($argumentos -join ' ') fallo" }
}

# ---------- Una falla completa: caer, consultar N veces, volver, cronometrar ----------
function Medir-Falla([string]$tipo) {
    $caer = if ($tipo -eq "apagado") { @("stop", $Contenedor) } else { @("pause", $Contenedor) }
    $volver = if ($tipo -eq "apagado") { @("start", $Contenedor) } else { @("unpause", $Contenedor) }

    Escribir "`n--- Falla '$tipo': docker $($caer -join ' ') ---" Cyan
    Invocar-Docker $caer

    $filas = @()
    for ($i = 1; $i -le $Repeticiones; $i++) {
        $r = Medir $consultaRuta
        $red = Medir $consultaRed
        $ok = ($r.estado -eq 503) -and ($r.ms -lt $LimiteRespuestaMs) -and ($red.estado -eq 200)
        $filas += [pscustomobject]@{
            falla = $tipo; intento = $i; estado = $r.estado; ms = $r.ms; motivo = $r.motivo
            red_estado = $red.estado; red_ms = $red.ms; cumple = $ok
        }
        Escribir ("  {0,2}. rutas -> {1} en {2,7} ms ({3})   red -> {4}" -f $i, $r.estado, $r.ms, $r.motivo, $red.estado) $(if ($ok) { "Green" } else { "Red" })
    }
    $mensaje = (Medir $consultaRuta).mensaje

    Escribir "  Vuelve el servicio: docker $($volver -join ' ')" Cyan
    Invocar-Docker $volver
    $reloj = [System.Diagnostics.Stopwatch]::StartNew()
    $recuperado = $false
    while ($reloj.Elapsed.TotalSeconds -lt 120) {
        $r = Medir $consultaRuta
        if ($r.estado -eq 200 -and $r.costo -eq 10) { $recuperado = $true; break }
        Start-Sleep -Milliseconds 500
    }
    $reloj.Stop()
    $recuperacion = [math]::Round($reloj.Elapsed.TotalSeconds, 1)
    Escribir ("  Recuperacion: {0} s" -f $recuperacion) $(if ($recuperado -and $recuperacion -lt $LimiteRecuperacionS) { "Green" } else { "Red" })

    return [pscustomobject]@{
        falla = $tipo; filas = $filas; mensaje = $mensaje
        peor_ms = ($filas | Measure-Object ms -Maximum).Maximum
        todas_503 = -not ($filas | Where-Object { $_.estado -ne 503 })
        red_viva = -not ($filas | Where-Object { $_.red_estado -ne 200 })
        recuperado = $recuperado; recuperacion_s = $recuperacion
    }
}

# =========================================================================
Escribir "=== SCRUM-18 [TEST-01] RQ-01 Disponibilidad ===" Cyan
$script:token = Obtener-Token
Escribir "Token obtenido para el usuario '$Usuario'."

$base = Medir $consultaRuta
if ($base.estado -ne 200 -or $base.costo -ne 10) {
    throw "Linea base fallida: la ruta 0 -> 11 debe responder 200 con costo 10 (respondio $($base.estado))."
}
Escribir ("Linea base: rutas -> 200 en {0} ms, costo {1}" -f $base.ms, $base.costo) Green

$fallas = if ($Falla -eq "ambos") { @("apagado", "colgado") } else { @($Falla) }
$resultados = @()
foreach ($f in $fallas) { $resultados += Medir-Falla $f }

# ---------- Veredicto ----------
Escribir "`n=== Resultado ===" Cyan
$todoOk = $true
foreach ($r in $resultados) {
    $cumple = $r.todas_503 -and $r.red_viva -and ($r.peor_ms -lt $LimiteRespuestaMs) -and $r.recuperado -and ($r.recuperacion_s -lt $LimiteRecuperacionS)
    if (-not $cumple) { $todoOk = $false }
    Escribir ("{0,-8} peor caso {1} ms | 503 en {2}/{3} | red viva: {4} | recuperacion {5} s -> {6}" -f `
        $r.falla, $r.peor_ms, ($r.filas | Where-Object { $_.estado -eq 503 }).Count, $Repeticiones, $r.red_viva, $r.recuperacion_s, $(if ($cumple) { "CUMPLE" } else { "NO CUMPLE" })) $(if ($cumple) { "Green" } else { "Red" })
}

# ---------- Evidencia ----------
$fecha = Get-Date -Format "yyyy-MM-dd_HHmm"
$dir = Join-Path $raiz $Salida
New-Item -ItemType Directory -Force $dir | Out-Null
$csv = Join-Path $dir "rq01_$fecha.csv"
$resultados | ForEach-Object { $_.filas } | Export-Csv -NoTypeInformation -Encoding UTF8 $csv

$md = @("# RQ-01 Disponibilidad - corrida $fecha", "",
        "Gateway: $Gateway - consulta: GET $consultaRuta - repeticiones: $Repeticiones", "",
        "| Falla | Peor caso (ms) | Respuestas 503 | grafo-service vivo | Recuperacion (s) | Cumple |",
        "|---|---:|---:|---|---:|---|")
foreach ($r in $resultados) {
    $cumple = $r.todas_503 -and $r.red_viva -and ($r.peor_ms -lt $LimiteRespuestaMs) -and $r.recuperado -and ($r.recuperacion_s -lt $LimiteRecuperacionS)
    $md += "| $($r.falla) | $($r.peor_ms) | $(($r.filas | Where-Object { $_.estado -eq 503 }).Count)/$Repeticiones | $($r.red_viva) | $($r.recuperacion_s) | $(if ($cumple) { 'Si' } else { 'No' }) |"
}
$md += ""
foreach ($r in $resultados) { $md += "Mensaje del gateway ($($r.falla)): $($r.mensaje)"; $md += "" }
$md += "Datos por intento: rq01_$fecha.csv"
$md | Out-File -Encoding utf8 (Join-Path $dir "rq01_$fecha.md")

Escribir "`nEvidencia guardada en $Salida/rq01_$fecha.md y .csv"
if ($todoOk) { exit 0 } else { exit 1 }
