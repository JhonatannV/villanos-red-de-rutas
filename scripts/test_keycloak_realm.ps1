# =========================================================================
# Verificacion de Conformidad de Keycloak y Realm (SCRUM-19 [RA-01])
# =========================================================================

$ErrorActionPreference = "Stop"

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "  VALIDACION DE REALM RUTAS-REALM.JSON (SCRUM-19 [RA-01])" -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan

$realmPath = "keycloak/rutas-realm.json"
if (-not (Test-Path $realmPath)) {
    Write-Error "No se encontro el archivo $realmPath"
}

$rawJson = Get-Content $realmPath -Raw
try {
    $realm = $rawJson | ConvertFrom-Json
} catch {
    Write-Error "El archivo $realmPath no contiene un JSON valido: $_"
}

Write-Host "[OK] JSON sintacticamente valido." -ForegroundColor Green

# 1. Validar Realm
if ($realm.realm -ne "rutas-realm") {
    Write-Error "El nombre del realm debe ser 'rutas-realm', encontrado: $($realm.realm)"
}
Write-Host "[OK] Realm: $($realm.realm)" -ForegroundColor Green

# 2. Validar Roles
$roles = $realm.roles.realm | ForEach-Object { $_.name }
$requiredRoles = @("planificador", "consultor", "admin")
foreach ($r in $requiredRoles) {
    if ($roles -notcontains $r) {
        Write-Error "Falta el rol requerido '$r' en el realm"
    }
}
Write-Host "[OK] Roles verificados: $($roles -join ', ')" -ForegroundColor Green

# 3. Validar Clientes
$clientIds = $realm.clients | ForEach-Object { $_.clientId }
if ($clientIds -notcontains "red-rutas-frontend") {
    Write-Error "Falta el cliente 'red-rutas-frontend'"
}
if ($clientIds -notcontains "red-rutas-backend") {
    Write-Error "Falta el cliente 'red-rutas-backend'"
}

$frontendClient = $realm.clients | Where-Object { $_.clientId -eq "red-rutas-frontend" }
if ($frontendClient.attributes.'pkce.code.challenge.method' -ne "S256") {
    Write-Error "El cliente frontend no tiene PKCE con metodo S256 configurado"
}
Write-Host "[OK] Clientes verificados: red-rutas-frontend (PKCE S256) y red-rutas-backend" -ForegroundColor Green

# 4. Validar Usuarios
$users = $realm.users | ForEach-Object { $_.username }
$requiredUsers = @("planificador", "consultor", "admin")
foreach ($u in $requiredUsers) {
    if ($users -notcontains $u) {
        Write-Error "Falta el usuario requerido '$u'"
    }
}
Write-Host "[OK] Usuarios verificados: $($users -join ', ')" -ForegroundColor Green

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "  TODAS LAS VALIDACIONES ESTATICAS PASARON EXITOSAMENTE" -ForegroundColor Green
Write-Host "=========================================================" -ForegroundColor Cyan
