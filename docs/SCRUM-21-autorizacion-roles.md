# Documentación Técnica: SCRUM-21 [RA-05]

## 1. Información General
* **Historia de Usuario:** SCRUM-21 [RA-05]
* **Título:** Autorización por rol según matriz de permisos (HTTP 403)
* **Responsable:** Jhonatan Varón Henao (DevSecOps, Ciberseguridad & Backend de Datos)
* **Equipo:** Villanos — UCEVA, 6.° Semestre
* **Rama Git:** `feature/jhonatan-scrum-21-autorizacion-roles`

---

## 2. Objetivo y Justificación Técnica

La autenticación (verificar quién es el usuario) resuelta en `SCRUM-20` no es suficiente por sí sola: el sistema requiere **Control de Acceso Basado en Roles (RBAC - Role-Based Access Control)**.

En el sistema "Red de Rutas":
1. El usuario con rol **`planificador`** tiene privilegios de escritura para diseñar y mantener la red de transporte (agregar paradas, registrar tramos y abrir/cerrar vías por obras o contingencias).
2. El usuario con rol **`consultor`** es un usuario final o agente de atención que únicamente necesita consultar la red y calcular caminos mínimos de transporte.

### Criterio de Aceptación
* **Matriz de permisos estricta:**
  * Endpoints de mutación (`POST`, `PUT`, `DELETE`, `PATCH` en `/api/redes/**`): Reservados exclusivamente para `planificador` y `admin`.
  * Endpoints de consulta (`GET` en `/api/redes/**` y `/api/rutas/**`): Accesibles para `consultor`, `planificador` y `admin`.
* **Respuesta de contingencia por permisos:** Si un usuario con rol `consultor` intenta crear una parada o cambiar el estado de un tramo, el sistema **debe rechazar la petición con `HTTP 403 Forbidden`**.

---

## 3. Matriz de Permisos (RBAC)

| Recurso / Endpoint | Método HTTP | Acción | `consultor` | `planificador` | `admin` |
|---|:---:|---|:---:|:---:|:---:|
| `/api/redes/{id}` | `GET` | Consultar red completa | **Permitido (200)** | **Permitido (200)** | **Permitido (200)** |
| `/api/redes/{id}/paradas` | `GET` | Listar paradas | **Permitido (200)** | **Permitido (200)** | **Permitido (200)** |
| `/api/redes/{id}/tramos` | `GET` | Listar tramos | **Permitido (200)** | **Permitido (200)** | **Permitido (200)** |
| `/api/rutas/calcular` | `GET` | Calcular ruta Dijkstra | **Permitido (200)** | **Permitido (200)** | **Permitido (200)** |
| `/api/redes/{id}/paradas` | `POST` | Crear nueva parada | **DENEGADO (403)** | **Permitido (201)** | **Permitido (201)** |
| `/api/redes/{id}/tramos/{tId}/estado` | `PUT` | Abrir/Cerrar tramo | **DENEGADO (403)** | **Permitido (200)** | **Permitido (200)** |

---

## 4. Implementación Técnica

### 4.1. Conversor de Roles Keycloak (`KeycloakJwtAuthenticationConverter.java`)
Keycloak expide los roles del realm dentro del claim `realm_access.roles`. Se implementó un conversor personalizado que extrae estos roles y los registra en el `SecurityContext` con el prefijo estándar `ROLE_`:
* `planificador` $\rightarrow$ `ROLE_planificador`
* `consultor` $\rightarrow$ `ROLE_consultor`
* `admin` $\rightarrow$ `ROLE_admin`

```java
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                defaultAuthoritiesConverter.convert(jwt).stream(),
                extractRealmRoles(jwt).stream()
        ).collect(Collectors.toSet());

        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"));
    }
}
```

### 4.2. Reglas de Autorización en `SecurityConfig.java`
En `api-gateway`, `grafo-service` y `ruta-service`:
```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/**").permitAll()
        // Mutaciones: SOLO planificador o admin
        .requestMatchers(HttpMethod.POST, "/api/redes/**").hasAnyRole("planificador", "admin")
        .requestMatchers(HttpMethod.PUT, "/api/redes/**").hasAnyRole("planificador", "admin")
        .requestMatchers(HttpMethod.DELETE, "/api/redes/**").hasAnyRole("planificador", "admin")
        // Consultas y calculo: consultor, planificador o admin
        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("consultor", "planificador", "admin")
        .anyRequest().authenticated()
    )
    .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter()))
    );
```

---

## 5. Evidencias de Pruebas Automatizadas

Se verificaron las suites de pruebas de integración con `MockMvc` simulando usuarios con roles específicos:

| Microservicio | Escenario de Prueba | Rol Simulado | Código HTTP Obtenido | Resultado |
|---|---|:---:|:---:|:---:|
| **grafo-service** | Crear parada (`POST /api/redes/1/paradas`) | `consultor` | **403 Forbidden** | Cumple Criterio |
| **grafo-service** | Cambiar estado de tramo (`PUT .../estado`) | `consultor` | **403 Forbidden** | Cumple Criterio |
| **grafo-service** | Crear parada (`POST /api/redes/1/paradas`) | `planificador` | **201 Created** | Cumple Criterio |
| **grafo-service** | Cambiar estado de tramo (`PUT .../estado`) | `planificador` | **200 OK** | Cumple Criterio |
| **grafo-service** | Consultar red (`GET /api/redes/1`) | `consultor` | **200 OK** | Cumple Criterio |
| **api-gateway** | Crear parada vía Gateway | `consultor` | **403 Forbidden** | Cumple Criterio |
| **api-gateway** | Calcular ruta vía Gateway | `consultor` | **200 OK** (o contingencia) | Cumple Criterio |

---

## 6. Guía de Sustentación ante el Docente

* **Pregunta 1:** *"¿Cuál es la diferencia entre un error 401 y un error 403?"*
  * **Tu respuesta como DevSecOps:** *"El código **401 Unauthorized** significa falta de autenticación: el cliente no envió token o el token expiró/es inválido (no sé quién eres). El código **403 Forbidden** significa falta de autorización: el sistema ya sabe exactamente quién eres (autenticación válida), pero tu rol no tiene los privilegios requeridos para ejecutar esa acción (sé quién eres, pero no tienes permiso)."*

* **Pregunta 2:** *"¿Cómo extrae Spring Boot los roles de Keycloak?"*
  * **Tu respuesta:** *"Keycloak almacena los roles del realm en la propiedad `realm_access.roles` dentro del payload del JWT. Implementamos `KeycloakJwtAuthenticationConverter` para transformar esos claims en objetos `GrantedAuthority` con el prefijo `ROLE_`, lo que le permite a Spring Security evaluar expresiones como `hasAnyRole('planificador', 'admin')`."*
