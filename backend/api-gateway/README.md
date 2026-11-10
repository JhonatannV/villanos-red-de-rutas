# api-gateway (Spring Cloud Gateway)
**Responsable:** Jhonatan Varón Henao

## Propósito
Puerta de enlace única del sistema. 
- Enruta peticiones hacia `grafo-service` y `ruta-service`.
- Implementa tiempo límite estricto de 2 segundos hacia `ruta-service`.
- Devuelve HTTP 503 estructurado si `ruta-service` no responde (Escenario RQ-01: Disponibilidad).
- Valida tokens JWT emitidos por Keycloak como Resource Server.
