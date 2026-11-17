# SCRUM-39 [DEPLOY-02] · Arranque en máquina limpia

**Criterio de aceptación:** levantar el sistema con un solo comando y resolver la primera ruta en menos de 90 s desde cero.

**Resultado:** ✅ con `docker compose up -d`, sin contenedores ni volúmenes del proyecto, la primera ruta se resolvió en **44,1 a 44,7 s**, y en **46,9 s** recién compiladas todas las imágenes sin caché. Para llegar a eso hubo que cambiar el orden de arranque ([D-19](../Bitacora_Decisiones.md#d-19)): con el `docker-compose.yml` anterior tardaba **108,8 s** y no cumplía.

| Corrida | Escenario | Primera ruta | Keycloak sano | grafo-service | ruta-service | api-gateway |
|---|---|---:|---:|---:|---:|---:|
| Antes de D-19 | Imágenes construidas | **108,8 s** ❌ | 57,4 s | 85,7 s | 96,9 s | 111,7 s |
| [1](arranque_2026-11-17_1312.md) | Imágenes construidas | **44,7 s** ✅ | 47,5 s | 20,8 s | 10,1 s | 14,2 s |
| [2](arranque_2026-11-17_1313.md) | Imágenes construidas | **44,6 s** ✅ | 44,6 s | 31,5 s | 7,9 s | 15,6 s |
| [3](arranque_2026-11-17_1314.md) | Imágenes construidas | **44,1 s** ✅ | 44,1 s | 21,3 s | 10,7 s | 14,8 s |
| [4](arranque_2026-11-17_1317.md) | Desde cero: `build --no-cache` (75,1 s) y luego `up -d` | **46,9 s** ✅ | 49,9 s | 22,0 s | 10,8 s | 14,8 s |

Los tiempos se cuentan desde que se ejecuta `docker compose up -d`. En todas las corridas la ruta Terminal de Transportes (0) → Zona Rural La Marina (39) respondió 200 con costo 46, camino `[0, 9, 10, 19, 27, 35, 36, 37, 39]` y modo `NATIVE`. El frontend respondió 200 a los pocos segundos.

## Por qué tardaba 108,8 s

`docker-compose.yml` hacía que cada servicio esperara a que el anterior estuviera **sano** (`condition: service_healthy`):

```
keycloak (57 s) → grafo-service (86 s) → ruta-service (97 s) → api-gateway (112 s) → frontend
```

- Los servicios de Spring Boot arrancan en 10 a 20 s, pero no empezaban hasta que Keycloak estuviera listo, y luego uno detrás del otro.
- Cada revisión de salud se hacía cada 10 s, así que cada eslabón perdía hasta 10 s en enterarse de que el anterior ya estaba sano.

Esa cadena no protegía nada:

- Los tres servicios piden las claves de Keycloak (`jwk-set-uri`) recién cuando llega el primer token.
- `ruta-service` pide la red a `grafo-service` en cada consulta.
- El gateway responde 503 mientras un servicio no contesta (W-RQ-01).

## Qué se cambió (D-19)

- `depends_on` usa `condition: service_started`: los servicios arrancan en paralelo con Keycloak.
- Cada healthcheck tiene `start_interval: 2s`: mientras el contenedor arranca, su salud se revisa cada 2 s; después sigue cada 10 s.

Ahora el camino crítico es solo el arranque de Keycloak, que importa el realm. El primer token sale cuando Keycloak termina, y en ese mismo instante la ruta ya se resuelve, porque el resto lleva 20 a 30 s listo.

Los límites de CPU y memoria de SCRUM-38 no cambiaron.

## Qué no cubre esta medición

- **Fue en el mismo computador de desarrollo** (Windows 11 con Docker Desktop, 12 CPU), no en una máquina distinta como pide el enunciado. El script está hecho para repetirlo en otra máquina; ver abajo.
- **"Desde cero" no incluyó descargar las imágenes base.** Keycloak, Maven, Node y Alpine ya estaban en este equipo. En una máquina nueva hay que sumar esa descarga, que depende de la conexión, más la compilación (75,1 s aquí).
- **Se midió con Docker Compose**, no con Swarm ni Kubernetes. Los manifiestos de Kubernetes de SCRUM-38 están en `k8s/`.

## Cómo repetirlo (por ejemplo, en una VM nueva)

Solo hace falta Docker con Compose v2, bash, curl y git:

```bash
git clone https://github.com/JhonatannV/villanos-red-de-rutas.git
cd villanos-red-de-rutas
bash scripts/arranque_limpio.sh --desde-cero   # compila sin caché y luego mide el arranque
bash scripts/arranque_limpio.sh                # mide el arranque con las imágenes ya construidas
```

El script `scripts/arranque_limpio.sh` hace esto:

1. **Limpia** el proyecto con `docker compose down -v`. No toca otros proyectos de Docker.
2. **Ejecuta** `docker compose up -d` y lo cronometra.
3. **Sigue** a la vez:
   - cuándo queda sano cada contenedor;
   - cuándo Keycloak entrega el primer token del usuario `consultor`. La clave se lee del realm versionado y no se escribe en el reporte;
   - cuándo el gateway resuelve la ruta 0 → 39.
4. **Guarda** el reporte en `docs/mediciones/arranque_<fecha>.md` y `.csv`.
5. **Termina** con código 0 solo si la primera ruta llegó antes de 90 s.

En Windows se corre con Git Bash. `.gitattributes` obliga a que los `.sh` se guarden con fin de línea LF, para que también funcionen en Linux.
