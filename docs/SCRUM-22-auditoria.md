# SCRUM-22 [RA-06] Auditoría de Cambios con Identificador de Usuario (Claim `sub`)

**Sprint:** Sprint 3 (Seguridad, Autenticación y Autorización con Keycloak)  
**Ticket Jira:** [SCRUM-22](https://villanos.atlassian.net/browse/SCRUM-22)  
**Responsable:** Jhonatan Varón Henao (`jhonatan.varon01@uceva.edu.co`) - DevSecOps, Ciberseguridad & Backend de Datos  
**Equipo:** Villanos · Proyecto Integrador UCEVA (Semestre 6)

---

## 1. Objetivo del Incremento

Implementar el registro persistente de auditoría en base de datos para todas las operaciones de mutación de la red de transporte (creación de paradas, apertura o cierre de tramos y modificaciones generales), capturando:
1. El identificador único, inmutable y estable del usuario (claim `sub` del estándar OpenID Connect / JWT emitido por Keycloak).
2. El nombre de usuario legible (`preferred_username`) para facilitar la trazabilidad administrativa.
3. La marca temporal exacta del evento (`fecha` en formato UTC / `Instant`).
4. La operación ejecutada (`operacion`) y su detalle específico (`detalle`).
5. La red afectada (`redId`).

---

## 2. Criterio de Aceptación

> **Criterio de Aceptación Jira [RA-06]:**  
> *"Tras operar con dos usuarios, la base de datos registra dos identificadores estables distintos provenientes del token."*

---

## 3. Arquitectura y Persistencia de Auditoría

Para garantizar persistencia relacional SQL sin requerir infraestructura externa compleja que comprometa la portabilidad del contenedor, se integró **Spring Data JPA** con **H2 Database**:

- **Dependencias Maven agregadas a `grafo-service`:**
  - `spring-boot-starter-data-jpa`: abstracción ORM con Hibernate 6.5.
  - `com.h2database:h2`: motor relacional embebido con almacenamiento persistente en `./data/grafo_audit_db`.

### 3.1 Modelo de Entidad (`RegistroAuditoria`)

Tabla relacional `auditoria_cambios`:

| Campo | Tipo SQL | Descripción |
|---|---|---|
| `id` | `BIGINT AUTO_INCREMENT PRIMARY KEY` | Llave primaria secuencial |
| `usuario_sub` | `VARCHAR(255) NOT NULL` | Claim `sub` del JWT (UUID de Keycloak) |
| `usuario_username` | `VARCHAR(255)` | Claim `preferred_username` del JWT |
| `fecha` | `TIMESTAMP WITH TIME ZONE NOT NULL` | Marca temporal UTC del cambio |
| `operacion` | `VARCHAR(255) NOT NULL` | Identificador de la acción (`CREAR_PARADA`, `CAMBIAR_ESTADO_TRAMO`) |
| `detalle` | `VARCHAR(1000)` | Descripción del recurso modificado |
| `red_id` | `INTEGER` | Identificador de la red evaluada |

### 3.2 Extracción Segura del Claim `sub`

En `GrafoController`, se inyecta el contexto de seguridad `Authentication`:

```java
private String extraerSub(Authentication authentication) {
    if (authentication == null) {
        return "anonimo";
    }
    if (authentication instanceof JwtAuthenticationToken jwtToken) {
        String sub = jwtToken.getToken().getSubject();
        if (sub != null && !sub.isBlank()) {
            return sub;
        }
    }
    if (authentication.getPrincipal() instanceof Jwt jwt) {
        String sub = jwt.getSubject();
        if (sub != null && !sub.isBlank()) {
            return sub;
        }
    }
    return authentication.getName();
}
```

El claim `sub` representa el identificador primario del sujeto en el estándar RFC 7519 y OpenID Connect Core 1.0. A diferencia de un nombre de usuario que puede renombrarse, el `sub` es un UUID inmutable asignado por Keycloak a la identidad del usuario.

---

## 4. Endpoints Expuestos

1. **`POST /api/redes/{id}/paradas`**:
   - Requiere rol: `planificador` o `admin`.
   - Efecto: Registra la parada y genera un registro de auditoría con la operación `CREAR_PARADA` y el `sub` del usuario autenticado.
2. **`PUT /api/redes/{id}/tramos/{tramoId}/estado?abierto={true|false}`**:
   - Requiere rol: `planificador` o `admin`.
   - Efecto: Modifica el tramo y genera un registro de auditoría con la operación `CAMBIAR_ESTADO_TRAMO` y el `sub` del usuario.
3. **`GET /api/redes/{id}/auditoria`**:
   - Requiere rol: `consultor`, `planificador` o `admin`.
   - Efecto: Retorna la lista cronológica descendente de auditoría para la red especificada.
4. **`GET /api/redes/auditoria`**:
   - Requiere rol: `consultor`, `planificador` o `admin`.
   - Efecto: Retorna el historial global de cambios.

---

## 5. Pruebas Automatizadas y Validación del Criterio

Se incorporaron tres pruebas unitarias e integrales en `GrafoServiceApplicationTests`:

1. **`operacionCrearParadaGuardaAuditoriaConClaimSubYFecha`**:
   - Envía un JWT con claim `sub = "keycloak-sub-uuid-planificador-1234"` y `preferred_username = "planificador.operaciones"`.
   - Realiza `POST /api/redes/1/paradas`.
   - Comprueba que en la tabla `auditoria_cambios` se insertó exactamente un registro con dicho `sub`, username, fecha válida y detalle descriptivo.

2. **`dosUsuariosDistintosRegistranIdentificadoresSubEstablesDiferentes`** (*Prueba directa del Criterio de Aceptación*):
   - Usuario A opera con token con `sub = "11111111-aaaa-bbbb-cccc-111111111111"`.
   - Usuario B opera con token con `sub = "22222222-dddd-eeee-ffff-222222222222"`.
   - Valida que la base de datos almacena dos registros con identificadores `sub` distintos (`assertNotEquals`), persistentes y no colisionables.

3. **`consultarAuditoriaPorEndpointRetornaRegistrosGuardados`**:
   - Comprueba que el endpoint REST `GET /api/redes/1/auditoria` expone los registros guardados en formato JSON estructurado.

### Resultado de Ejecución de Pruebas:
```text
[INFO] Running com.villanos.grafo.GrafoServiceApplicationTests
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 57.04 s
[INFO] BUILD SUCCESS
```
Total: **14 pruebas ejecutadas, 0 fallos, 0 errores**.
