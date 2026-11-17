# SCRUM-18 [TEST-01] · Demostración en vivo de RQ-01 Disponibilidad

**Escenario (W-RQ-01):** `ruta-service` se cae mientras alguien consulta la red. El sistema sigue entregando el grafo y avisa que el cálculo de rutas no está disponible, en vez de quedarse colgado.

**Criterio de aceptación:**
- respuesta en menos de 3 s;
- 10 pruebas consecutivas, reportando el peor caso;
- al volver el servicio, se recupera en menos de 30 s sin recargar.

**Resultado:** ✅ se cumple con el sistema real levantado con `docker compose`, en los dos tipos de falla.

| Falla | Cómo se provoca | Peor caso | Respuestas 503 | `grafo-service` | Recuperación |
|---|---|---:|---:|---|---:|
| **Servicio apagado** (el proceso muere) | `docker stop ruta-service` | **2.434 ms** | 10/10 | 200 en las 10 | **4 s** |
| **Servicio colgado** (no responde) | `docker pause ruta-service` | **2.016 ms** | 10/10 | 200 en las 10 | **0 s** |

Línea base con todo arriba: la ruta Terminal → Coliseo Benicio responde **200 en 42 ms**, con costo 10, camino `[0, 9, 10, 11]` y `modoEfectivo: NATIVE` (`libgrafo.so` por JNA dentro del contenedor).

Datos crudos: [`rq01_2026-11-12_2310.md`](rq01_2026-11-12_2310.md) y [`rq01_2026-11-12_2310.csv`](rq01_2026-11-12_2310.csv).

## Qué se observa

- **Ninguna petición queda colgada.** Las 20 consultas a `/api/rutas` durante la falla responden 503 con un cuerpo JSON que nombra el servicio caído y el motivo. Todas llegan antes de 3 s.
- **El grafo sigue disponible.** En cada intento también se consulta `/api/redes/1` por el gateway, y responde 200 en unos 40 ms. El frontend puede seguir dibujando el mapa (D-01 de la bitácora).
- **La recuperación es automática.** Con `docker start`, `ruta-service` arranca en unos 3,5 s, y el gateway vuelve a enrutar sin reiniciarse ni reconfigurarse. Con `docker unpause`, la siguiente consulta ya responde 200.
- **En Docker, "apagado" también tarda ~2 s.** En la prueba unitaria de SCRUM-17, un puerto cerrado responde en ~20 ms (`CONEXION_RECHAZADA`). Dentro de la red de Docker, al detener el contenedor su IP deja de existir: nadie rechaza la conexión, y el gateway espera su límite de conexión de 2 s. Por eso el motivo reportado es `TIMEOUT` y el peor caso ronda los 2 s. Sigue dentro de los 3 s, pero confirma que **el límite de 2 s del gateway es lo que garantiza el escenario**, no la rapidez del rechazo.

## Correcciones necesarias para la demostración

Al levantar el sistema completo por primera vez aparecieron tres fallas de integración. Se corrigieron en el mismo cambio:

1. **Keycloak nunca quedaba "saludable"** y el resto del sistema no arrancaba. El `healthcheck` usaba `curl`, que la imagen de Keycloak no incluye. Ahora consulta `/health/ready` con `/dev/tcp` de bash (`docker-compose.yml`).
2. **`grafo-service` entregaba una red vacía** en Docker: la imagen no incluía `data/grafo_40_paradas.json`. Se copia en la etapa final del Dockerfile.
3. **`ruta-service` recibía 401 de `grafo-service`**, porque no reenviaba el token del usuario. Ahora reenvía la cabecera `Authorization` al pedir la red (`RutaController`).

## Alcance

Esta medición es a nivel de API, a través del gateway, que es donde se decide el escenario. La parte visual de RQ-01 (aviso en pantalla y botón "Reintentar" con el mapa visible) se midió en SCRUM-32: ver [`SCRUM-32-modo-degradado.md`](SCRUM-32-modo-degradado.md).

## Cómo repetirlo

```powershell
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File scripts/medir_rq01.ps1                 # apagado y colgado, 10 intentos
powershell -ExecutionPolicy Bypass -File scripts/medir_rq01.ps1 -Falla apagado  # solo una falla
```

El script:
1. obtiene un token del usuario de prueba `consultor` del realm;
2. verifica la línea base;
3. provoca cada falla y lanza los intentos;
4. cronometra la recuperación;
5. guarda la evidencia en `docs/mediciones/rq01_<fecha>.md` y `.csv`.

Termina con código 0 solo si todo cumple.
