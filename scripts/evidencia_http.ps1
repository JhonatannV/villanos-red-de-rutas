# =========================================================================
# SCRUM-26 [TEST-02] Registro reproducible de las respuestas HTTP de seguridad
#
# Evidencia del entregable 06 (configuracion de identidad):
#   - sin cabecera Authorization            -> 401
#   - token con la firma alterada           -> 401
#   - token valido con el rol equivocado    -> 403 (en las dos operaciones prohibidas de la matriz)
#   - token valido con el rol correcto      -> 200
# Las peticiones van contra los servicios (gateway y cada microservicio), no contra el frontend.
#
# Uso (con el sistema levantado con docker compose up -d):
#   powershell -ExecutionPolicy Bypass -File scripts/evidencia_http.ps1
# =========================================================================
param(
    [string]$Gateway = "http://localhost:8080",
    [string]$Grafo = "http://localhost:8081",
    [string]$Ruta = "http://localhost:8082",
    [string]$Keycloak = "http://localhost:8085",
    [string]$Salida = "docs/pruebas"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
$raiz = Split-Path -Parent $PSScriptRoot

# ---------- Tokens de los usuarios de prueba del realm ----------
$realm = Get-Content (Join-Path $raiz "keycloak/rutas-realm.json") -Raw | ConvertFrom-Json
function Obtener-Token([string]$usuario) {
    $u = $realm.users | Where-Object { $_.username -eq $usuario }
    $cuerpo = @{
        grant_type = "password"; client_id = "red-rutas-frontend"
        username = $usuario; password = ($u.credentials | Select-Object -First 1).value
    }
    (Invoke-RestMethod -Method Post -Uri "$Keycloak/realms/rutas-realm/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" -Body $cuerpo).access_token
}
$tokens = @{ consultor = Obtener-Token "consultor"; planificador = Obtener-Token "planificador" }

# Token alterado: se cambia un caracter de la firma (tercera parte del JWT)
$partes = $tokens.consultor.Split(".")
$firma = $partes[2].ToCharArray()
$firma[10] = if ($firma[10] -eq "A") { "B" } else { "A" }
$tokens.alterado = "$($partes[0]).$($partes[1]).$(-join $firma)"

# ---------- Una peticion registrada ----------
$cliente = New-Object System.Net.Http.HttpClient
$cliente.Timeout = [TimeSpan]::FromSeconds(15)
$registros = @()

function Probar([string]$caso, [string]$metodo, [string]$url, [string]$quien, [int]$esperado, [string]$cuerpoJson = "") {
    $req = New-Object System.Net.Http.HttpRequestMessage((New-Object System.Net.Http.HttpMethod($metodo)), $url)
    if ($quien -ne "sin token") {
        $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $tokens[$quien])
    }
    if ($cuerpoJson) {
        $req.Content = New-Object System.Net.Http.StringContent($cuerpoJson, [System.Text.Encoding]::UTF8, "application/json")
    }
    $resp = $cliente.SendAsync($req).GetAwaiter().GetResult()
    $codigo = [int]$resp.StatusCode
    $texto = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if ($texto.Length -gt 160) { $texto = $texto.Substring(0, 160) + "..." }
    $auth = if ($quien -eq "sin token") { "" } else { " -H `"Authorization: Bearer <token $quien>`"" }
    $datos = if ($cuerpoJson) { " -H `"Content-Type: application/json`" -d '$cuerpoJson'" } else { "" }
    $ok = $codigo -eq $esperado
    $script:registros += [pscustomobject]@{
        caso = $caso; comando = "curl -X $metodo$auth$datos `"$url`""
        esperado = $esperado; obtenido = $codigo; cumple = $ok; respuesta = $texto.Trim()
    }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host ("{0,-62} {1} -> {2} (esperado {3})" -f $caso, $metodo, $codigo, $esperado) -ForegroundColor $color
}

$paradaNueva = '{"nombre":"Parada de prueba","x":10,"y":10}'

Write-Host "=== SCRUM-26 [TEST-02] Evidencia HTTP de seguridad ===" -ForegroundColor Cyan

# 1. Sin token: 401 en el gateway y en cada servicio por separado (RA-04)
Probar "Sin token, gateway, consultar ruta"               GET "$Gateway/api/rutas/calcular?desde=0&hasta=11" "sin token" 401
Probar "Sin token, grafo-service directo, consultar red"  GET "$Grafo/api/redes/1"                          "sin token" 401
Probar "Sin token, ruta-service directo, consultar ruta"  GET "$Ruta/api/rutas/calcular?desde=0&hasta=11"   "sin token" 401

# 2. Firma alterada: 401 en el gateway y en cada servicio
Probar "Firma alterada, gateway, consultar ruta"              GET "$Gateway/api/rutas/calcular?desde=0&hasta=11" alterado 401
Probar "Firma alterada, grafo-service directo, consultar red" GET "$Grafo/api/redes/1"                          alterado 401
Probar "Firma alterada, ruta-service directo, consultar ruta" GET "$Ruta/api/rutas/calcular?desde=0&hasta=11"   alterado 401

# 3. Rol equivocado: el consultor no modifica la topologia (las celdas "No" de la matriz)
Probar "Consultor intenta crear una parada (nodo)"        POST   "$Gateway/api/redes/1/paradas"                     consultor 403 $paradaNueva
Probar "Consultor intenta modificar un tramo (arista)"    PUT    "$Gateway/api/redes/1/tramos/5/estado?abierto=true" consultor 403
Probar "Consultor intenta eliminar una parada (nodo)"     DELETE "$Gateway/api/redes/1/paradas/39"                  consultor 403
Probar "Consultor intenta crear una parada, grafo-service directo" POST "$Grafo/api/redes/1/paradas"               consultor 403 $paradaNueva

# 4. Rol correcto: 200
Probar "Consultor consulta la red"                        GET "$Gateway/api/redes/1"                          consultor 200
Probar "Consultor calcula una ruta"                       GET "$Gateway/api/rutas/calcular?desde=0&hasta=11"  consultor 200
Probar "Planificador modifica un tramo (lo deja abierto)" PUT "$Gateway/api/redes/1/tramos/5/estado?abierto=true" planificador 200
Probar "Planificador consulta la auditoria"               GET "$Gateway/api/redes/1/auditoria"                planificador 200

# ---------- Evidencia ----------
$fecha = Get-Date -Format "yyyy-MM-dd_HHmm"
$dir = Join-Path $raiz $Salida
New-Item -ItemType Directory -Force $dir | Out-Null
$archivo = Join-Path $dir "http_evidencia_$fecha.md"
$lineas = @("# Evidencia HTTP de seguridad - corrida $fecha", "",
            "Usuarios de prueba del realm rutas-realm: consultor y planificador. Los tokens se omiten.", "",
            "| # | Caso | Esperado | Obtenido | Cumple |", "|---:|---|:---:|:---:|:---:|")
$i = 0
foreach ($r in $registros) { $i++; $lineas += "| $i | $($r.caso) | $($r.esperado) | $($r.obtenido) | $(if ($r.cumple) { 'Si' } else { 'No' }) |" }
$lineas += ""; $lineas += "## Comandos y respuestas"; $lineas += ""
$i = 0
foreach ($r in $registros) {
    $i++
    $lineas += "### $i. $($r.caso)"
    $lineas += '```'
    $lineas += $r.comando
    $lineas += "-> HTTP $($r.obtenido)"
    if ($r.respuesta) { $lineas += $r.respuesta }
    $lineas += '```'
    $lineas += ""
}
$lineas | Out-File -Encoding utf8 $archivo

$fallos = ($registros | Where-Object { -not $_.cumple }).Count
Write-Host ("`n{0} de {1} respuestas como se esperaba. Evidencia: {2}/http_evidencia_{3}.md" -f ($registros.Count - $fallos), $registros.Count, $Salida, $fecha) -ForegroundColor $(if ($fallos -eq 0) { "Green" } else { "Red" })
if ($fallos -eq 0) { exit 0 } else { exit 1 }
