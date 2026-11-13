# SCRUM-15 [MS-02] · Integración de libgrafo.so con selector por configuración

**Criterio de aceptación:** si la biblioteca C no se encuentra o el modo es `java`, el servicio cambia automáticamente a la versión Java sin caerse.

**Resultado:** ✅ verificado en el contenedor de `ruta-service`, con el sistema levantado por `docker compose`.

| Configuración | `/actuator/info` | Ruta 0 → 11 por el gateway | Estado del servicio |
|---|---|---|---|
| Por defecto (`ALGORITHM_MODE` sin definir) | pedido `native`, activo `native`, "biblioteca nativa cargada" | costo 10, `[0, 9, 10, 11]`, `NATIVE` | UP |
| `ALGORITHM_MODE=java` | pedido `java`, activo `java`, "configurado en modo java" | costo 10, `[0, 9, 10, 11]`, `JAVA` | UP |
| `ALGORITHM_MODE=native` **sin** `libgrafo.so` en la imagen | pedido `native`, activo **`java`**, "no se pudo cargar la biblioteca nativa: Unable to load library 'grafo'" | — | **UP** |
| `ALGORITHM_MODE=rust` | — | — | No arranca: `Valor inválido para app.algorithm.mode: 'rust'` |

Para la fila sin biblioteca se arrancó la misma imagen borrando `/usr/lib/libgrafo.so` antes de iniciar Java.

## Cómo se conecta con Spring

- `config/AlgoritmoConfig` crea el bean `SelectorAlgoritmo` con `app.algorithm.mode`. En `application.yml` esa propiedad toma `ALGORITHM_MODE`.
- `RutaController` recibe el selector por inyección.
- `docker-compose.yml` usa `ALGORITHM_MODE=${ALGORITHM_MODE:-native}`, así que el modo se cambia al levantar el sistema sin editar archivos.
- `/actuator/info` muestra qué implementación respondió y por qué. Es la forma de comprobar en vivo, sin leer logs, si el `.so` cargó dentro del contenedor.

## Pruebas automáticas

`mvn test` en `backend/ruta-service`: 35 pruebas.
- `SelectorAlgoritmoTest` (13): selección, respaldo, validación y C vs. Java en las 1.600 parejas de la red de 40 paradas.
- `AlgoritmoConfigTest` (5):
  - `java` produce un selector Java;
  - sin propiedad se pide `native`;
  - con `native` siempre hay motivo;
  - un valor inválido detiene el contexto;
  - `/actuator/info` publica el modo.
- Las 17 pruebas existentes del servicio siguen pasando.

## Cómo repetirlo

```bash
docker compose up -d --build
curl http://localhost:8082/actuator/info
ALGORITHM_MODE=java docker compose up -d ruta-service
curl http://localhost:8082/actuator/info
```
