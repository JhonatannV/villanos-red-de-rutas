# SCRUM-17 [RQ-01] · Resiliencia y disponibilidad en API Gateway

**Criterio de aceptación:** configurar en `api-gateway` un tiempo límite de espera de 2 segundos hacia `ruta-service` y un fallback que retorne HTTP 503 con cuerpo informativo cuando `ruta-service` no responda. Peticiones a `/api/rutas` con `ruta-service` apagado o colgado responden 503 en menos de 2 segundos sin dejar conexiones colgadas.

**Resultado:** se cumple. Las peticiones hacia un servicio apagado devuelven HTTP 503 en menos de 30 ms (conexión rechazada inmediata). Las peticiones hacia un servicio colgado (retardo simulado de 4.000 ms) se interrumpen exactamente a los 2.000 ms configurados (2.010 ms medidos), retornando HTTP 503 con cuerpo JSON explicativo (`motivo: TIMEOUT`) y liberando los recursos del hilo y socket.

---

## Mediciones de resiliencia

| Escenario | Estado del servicio destino | Retardo simulado | Tiempo medido en Gateway | Código HTTP | Motivo en respuesta |
|---|---|---:|---:|:---:|---|
| **Servicio apagado** | Inaccesible / puerto cerrado | 0 ms | **20 ms** | **503** | `CONEXION_RECHAZADA` |
| **Servicio colgado** | Socket abierto sin responder | 4.000 ms | **2.010 ms** | **503** | `TIMEOUT` |
| **Health check** | Gateway local | 0 ms | **12 ms** | **200** | `status: UP` |

---

## Estructura de la respuesta de contingencia (HTTP 503)

Cuando el servicio no responde o supera el umbral de 2 segundos, el Gateway intercepta la excepción `ResourceAccessException` y entrega una respuesta estructurada en formato JSON:

```json
{
  "timestamp": "2026-11-12T20:20:10.280Z",
  "status": 503,
  "error": "Service Unavailable",
  "servicio": "ruta-service",
  "motivo": "TIMEOUT",
  "mensaje": "El microservicio ruta-service excedio el tiempo limite de espera (timeout 2s).",
  "detalle": "I/O error on GET request for \"http://localhost:8082/api/rutas/calcular\": Read timed out"
}
```

---

## Implementación técnica

1. **Gestor de conexiones (Apache HttpClient 5):**
   - Se configuró `PoolingHttpClientConnectionManager` con `ConnectionConfig`:
     - `setConnectTimeout(2000 ms)`: tiempo máximo para establecer el handshake TCP.
     - `setSocketTimeout(2000 ms)`: tiempo máximo de inactividad de lectura en el socket (`SO_TIMEOUT`).
   - `RequestConfig`:
     - `setResponseTimeout(2000 ms)`: tiempo límite para recibir los bytes de la respuesta.
     - `disableAutomaticRetries()`: evita reintentos automáticos en fallos idempotentes de lectura, garantizando el corte estricto a los 2 segundos.

2. **Controlador de Gateway (`GatewayController.java`):**
   - Intercepción de `ResourceAccessException`.
   - Clasificación de causa: si la causa raíz es `SocketTimeoutException` o contiene `timed out`, se cataloga como `TIMEOUT`; en caso contrario se clasifica como `CONEXION_RECHAZADA`.
   - Liberación inmediata del socket para prevenir agotamiento de conexiones (Resource Leak).

---

## Cómo se probó (para repetirlo)

```bash
mvn test -f backend/api-gateway/pom.xml
```

Suite automatizada en `ApiGatewayApplicationTests.java`:
- `actuatorHealthRetornaUp()`: valida salud del Gateway en `/actuator/health`.
- `rutaServiceApagadoResponde503EnMenosDeDosSegundos()`: simula destino apagado y valida respuesta 503 inmediata (< 2000 ms).
- `rutaServiceLentoCortaExactamenteEnTimeoutDosSegundos()`: levanta un servidor HTTP simulado con retardo de 4.000 ms y comprueba con `MockMvc` que el Gateway corta exactamente a los ~2.000 ms con 503 y motivo `TIMEOUT`.
