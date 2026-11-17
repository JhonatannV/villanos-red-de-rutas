#!/usr/bin/env bash
# =========================================================================
# SCRUM-39 [DEPLOY-02] Arranque en maquina limpia
#
# Criterio: con un solo comando (docker compose up -d) el sistema queda
# arriba y la primera ruta se resuelve en menos de 90 s.
#
# Que hace:
#   1. Deja el proyecto sin contenedores, redes ni volumenes
#      (docker compose down -v). No toca otros proyectos de Docker.
#   2. Con --desde-cero, ademas recompila todas las imagenes sin cache
#      (docker compose build --no-cache) y reporta ese tiempo aparte.
#   3. Cronometra desde "docker compose up -d" hasta que, por el gateway y
#      con el token del usuario de prueba consultor, la ruta
#      Terminal (0) -> Zona Rural La Marina (39) responde 200 con su costo.
#   4. Escribe el reporte en docs/mediciones/arranque_<fecha>.md y .csv.
#
# Solo necesita Docker con Compose v2, bash y curl, asi que sirve igual en
# Linux (por ejemplo, una VM de AWS) y en Windows con Git Bash.
#
# Uso:  bash scripts/arranque_limpio.sh                # imagenes ya construidas
#       bash scripts/arranque_limpio.sh --desde-cero   # recompila todo sin cache
# =========================================================================
set -uo pipefail

LIMITE_S=90
DESDE_CERO=0
SALIDA="docs/mediciones"
GATEWAY="http://localhost:8080"
KEYCLOAK="http://localhost:8085"
FRONTEND="http://localhost:4200"
CONSULTA="/api/rutas/calcular?desde=0&hasta=39"
ESPERA_MAX_S=600

for arg in "$@"; do
    case "$arg" in
        --desde-cero) DESDE_CERO=1 ;;
        --salida=*) SALIDA="${arg#--salida=}" ;;
        *) echo "Argumento desconocido: $arg"; exit 2 ;;
    esac
done

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ" || exit 2
mkdir -p "$SALIDA"
REGISTROS="$(mktemp -d)"   # salida completa de build y up, fuera del repositorio

ahora() { date +%s.%N | cut -c1-14; }
dif() { awk -v a="$1" -v b="$2" 'BEGIN { printf "%.1f", b - a }'; }
paso() { printf '\n== %s\n' "$1"; }

# Clave del usuario de prueba, tomada del realm versionado (no se escribe en el reporte)
CLAVE=$(tr -d '\r\n' < keycloak/rutas-realm.json | grep -o '"username" *: *"consultor".*' \
        | grep -o '"value" *: *"[^"]*"' | head -1 | sed 's/.*: *"\(.*\)"/\1/')
[ -n "$CLAVE" ] || { echo "No se encontro la clave de consultor en keycloak/rutas-realm.json"; exit 2; }

paso "1. Limpieza del proyecto (docker compose down -v)"
docker compose down -v --remove-orphans >/dev/null 2>&1
echo "Contenedores del proyecto: $(docker compose ps -a -q | wc -l)"

T_BUILD="—"
if [ "$DESDE_CERO" = 1 ]; then
    paso "2. Compilacion de todas las imagenes sin cache (docker compose build --no-cache)"
    TB0=$(ahora)
    if ! docker compose build --no-cache > "$REGISTROS/build.log" 2>&1; then
        echo "La compilacion fallo; ver $REGISTROS/build.log"; exit 1
    fi
    T_BUILD=$(dif "$TB0" "$(ahora)")
    echo "Compilacion: $T_BUILD s"
else
    paso "2. Imagenes ya construidas: se verifica que existan"
    if ! docker compose build > /dev/null 2>&1; then echo "No se pudieron preparar las imagenes"; exit 1; fi
fi

paso "3. Arranque con un solo comando: docker compose up -d"
T0=$(ahora)
docker compose up -d > "$REGISTROS/up.log" 2>&1 &
PID_UP=$!

declare -A SANO
T_UP="" ; T_TOKEN="" ; T_RUTA="" ; T_FRONT="" ; RESPUESTA="" ; TOKEN=""
while :; do
    T=$(ahora); S=$(dif "$T0" "$T")
    if awk -v s="$S" -v m="$ESPERA_MAX_S" 'BEGIN { exit !(s > m) }'; then
        echo "Se agotaron $ESPERA_MAX_S s sin resolver la ruta"; break
    fi
    if [ -z "$T_UP" ] && ! kill -0 "$PID_UP" 2>/dev/null; then T_UP=$S; echo "  docker compose up -d termino a los $S s"; fi
    for c in keycloak grafo-service ruta-service api-gateway frontend; do
        if [ -z "${SANO[$c]:-}" ] && [ "$(docker inspect -f '{{.State.Health.Status}}' "$c" 2>/dev/null)" = "healthy" ]; then
            SANO[$c]=$S; echo "  $c sano a los $S s"
        fi
    done
    if [ -z "$TOKEN" ]; then
        TOKEN=$(curl -s -m 3 -X POST "$KEYCLOAK/realms/rutas-realm/protocol/openid-connect/token" \
            -d grant_type=password -d client_id=red-rutas-frontend -d username=consultor \
            --data-urlencode "password=$CLAVE" 2>/dev/null | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
        [ -n "$TOKEN" ] && { T_TOKEN=$S; echo "  primer token de Keycloak a los $S s"; }
    fi
    if [ -n "$TOKEN" ] && [ -z "$T_RUTA" ]; then
        R=$(curl -s -m 5 -w ' HTTP%{http_code}' -H "Authorization: Bearer $TOKEN" "$GATEWAY$CONSULTA" 2>/dev/null)
        if [[ "$R" == *HTTP200 ]] && [[ "$R" == *'"costoTotal"'* ]]; then
            T_RUTA=$S; RESPUESTA="${R% HTTP200}"; echo "  PRIMERA RUTA resuelta a los $S s"
        fi
    fi
    if [ -z "$T_FRONT" ] && [ "$(docker inspect -f '{{.State.Running}}' frontend 2>/dev/null)" = "true" ]        && [ "$(curl -s -m 2 -o /dev/null -w '%{http_code}' "$FRONTEND" 2>/dev/null)" = "200" ]; then
        T_FRONT=$S; echo "  frontend responde a los $S s"
    fi
    # Se sigue hasta ver los cinco servicios sanos (como maximo 60 s despues de la ruta)
    if [ -n "$T_RUTA" ] && [ -n "$T_FRONT" ] && [ -n "$T_UP" ]; then
        [ "${#SANO[@]}" -ge 5 ] && break
        awk -v s="$S" -v r="$T_RUTA" 'BEGIN { exit !(s > r + 60) }' && break
    fi
    sleep 0.5
done
wait "$PID_UP" 2>/dev/null

COSTO=$(echo "$RESPUESTA" | grep -o '"costoTotal":[-0-9]*' | cut -d: -f2)
CAMINO=$(echo "$RESPUESTA" | grep -o '"camino":\[[0-9,]*\]' | cut -d: -f2)
MODO=$(echo "$RESPUESTA" | grep -o '"modoEfectivo":"[A-Z]*"' | cut -d'"' -f4)
CUMPLE="NO"
[ -n "$T_RUTA" ] && awk -v s="$T_RUTA" -v l="$LIMITE_S" 'BEGIN { exit !(s < l) }' && CUMPLE="SI"

FECHA=$(date +%Y-%m-%d_%H%M)
ESCENARIO=$([ "$DESDE_CERO" = 1 ] && echo "desde cero (build sin cache)" || echo "imagenes construidas sin contenedores ni volumenes")
MAQUINA="$(docker info --format '{{.OperatingSystem}} · {{.NCPU}} CPU · {{.MemTotal}} B RAM · Docker {{.ServerVersion}}' 2>/dev/null)"
HOST="$(uname -s) $(uname -m)"

CSV="$SALIDA/arranque_$FECHA.csv"
{
    echo "fecha,escenario,build_s,up_s,keycloak_s,grafo_s,ruta_s,gateway_s,frontend_sano_s,token_s,primera_ruta_s,frontend_200_s,costo,cumple_90s"
    echo "$FECHA,$ESCENARIO,$T_BUILD,$T_UP,${SANO[keycloak]:-},${SANO[grafo-service]:-},${SANO[ruta-service]:-},${SANO[api-gateway]:-},${SANO[frontend]:-},$T_TOKEN,$T_RUTA,$T_FRONT,$COSTO,$CUMPLE"
} > "$CSV"

MD="$SALIDA/arranque_$FECHA.md"
cat > "$MD" <<FIN
# Arranque en máquina limpia · $FECHA

- **Escenario:** $ESCENARIO
- **Máquina:** $HOST · $MAQUINA
- **Comando:** \`docker compose up -d\`
- **Primera ruta** (\`GET $CONSULTA\` por el gateway, usuario \`consultor\`): **${T_RUTA:-no se resolvió} s** · límite $LIMITE_S s · cumple: **$CUMPLE**

| Hito | Segundos desde \`up -d\` |
|---|---:|
| Compilación sin caché (antes del arranque) | $T_BUILD |
| \`docker compose up -d\` termina | ${T_UP:-—} |
| keycloak sano | ${SANO[keycloak]:-—} |
| grafo-service sano | ${SANO[grafo-service]:-—} |
| ruta-service sano | ${SANO[ruta-service]:-—} |
| api-gateway sano | ${SANO[api-gateway]:-—} |
| frontend sano | ${SANO[frontend]:-—} |
| Primer token de Keycloak | ${T_TOKEN:-—} |
| **Primera ruta resuelta** | **${T_RUTA:-—}** |
| Frontend responde 200 | ${T_FRONT:-—} |

Respuesta: costo **${COSTO:-—}**, camino \`${CAMINO:-—}\`, modo \`${MODO:-—}\`.
FIN

paso "Resultado"
cat "$MD"
[ "$CUMPLE" = "SI" ]
