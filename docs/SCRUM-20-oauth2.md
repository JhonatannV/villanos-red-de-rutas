# Documentación Técnica: SCRUM-20 [RA-04]

## 1. Información General
* **Historia de Usuario:** SCRUM-20 [RA-04]
* **Título:** Validación OAuth2 / Resource Server en cada microservicio
* **Responsable:** Jhonatan Varón Henao (DevSecOps, Ciberseguridad & Backend de Datos)
* **Equipo:** Villanos — UCEVA, 6.° Semestre
* **Rama Git:** `feature/jhonatan-scrum-20-oauth2`

---

## 2. Objetivo y Justificación Técnica

En una arquitectura de microservicios segura con cero confianza (*Zero Trust Architecture*), la seguridad no puede depender exclusivamente de un cortafuegos perimetral. Aunque el **API Gateway** valida y filtra el tráfico de entrada, cada microservicio interno (`grafo-service` y `ruta-service`) debe actuar de manera autónoma como un **Resource Server** OAuth 2.0.

Esto garantiza que:
1. Si un atacante o servicio comprometido dentro de la red interna intenta realizar peticiones directas a `grafo-service` (puerto 8081) o `ruta-service` (puerto 8082), la petición será rechazada inmediatamente si no presenta un token JWT válido.
2. Cada servicio valida criptográficamente la firma del JWT consultando las llaves públicas del servidor de autorización en Keycloak (`/protocol/openid-connect/certs`).

### Criterio de Aceptación
* **Petición sin token:** Responde de inmediato con `HTTP 401 Unauthorized`.
* **Petición con token alterado en su firma o mal formado:** Responde con `HTTP 401 Unauthorized`.
* **Petición con token válido emitido por Keycloak:** Pasa la autenticación y accede al recurso (`HTTP 200 OK`).
* **Endpoints de salud (`/actuator/health`):** Permanecen públicos para permitir los healthchecks de Docker y Kubernetes sin requerir credenciales.

---

## 3. Implementación en los Microservicios

### 3.1. Dependencias Incorporadas (`pom.xml`)
Se integró en los tres microservicios (`api-gateway`, `grafo-service` y `ruta-service`):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 3.2. Configuración de Seguridad (`SecurityConfig.java`)
Cada microservicio implementa una cadena de filtros de Spring Security:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
```

### 3.3. Configuración de JWKS (`application.yml`)
Cada microservicio apunta al endpoint de llaves públicas del realm `rutas-realm`:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWKS_URI:http://localhost:8085/realms/rutas-realm/protocol/openid-connect/certs}
```
En el entorno orquestado con `docker-compose.yml`, los servicios resuelven la variable interna `KEYCLOAK_JWKS_URI=http://keycloak:8080/realms/rutas-realm/protocol/openid-connect/certs`.

### 3.4. Propagación de Identidad en el Gateway
En `GatewayController.java`, el API Gateway extrae automáticamente todos los encabezados HTTP recibidos (incluyendo `Authorization: Bearer <token>`) y los inyecta en las peticiones derivadas hacia `grafo-service` y `ruta-service`, garantizando la propagación segura del contexto de seguridad de extremo a extremo.

---

## 4. Evidencias de Pruebas Automatizadas

Cada microservicio cuenta con pruebas automáticas verificadas mediante `MockMvc` y `SecurityMockMvcRequestPostProcessors.jwt()`:

| Microservicio | Prueba Ejecutada | Resultado | Estado |
|---|---|:---:|:---:|
| **api-gateway** | `peticionSinTokenRetorna401Unauthorized` | HTTP 401 | Aprobado |
| **api-gateway** | `peticionConTokenAlteradoRetorna401Unauthorized` | HTTP 401 | Aprobado |
| **api-gateway** | `actuatorHealthRetornaUpSinAutenticacion` | HTTP 200 (UP) | Aprobado |
| **grafo-service** | `peticionSinTokenRetorna401Unauthorized` | HTTP 401 | Aprobado |
| **grafo-service** | `peticionConTokenAlteradoRetorna401Unauthorized` | HTTP 401 | Aprobado |
| **grafo-service** | `peticionConTokenValidoRetorna200Ok` | HTTP 200 | Aprobado |
| **ruta-service** | `peticionSinTokenRetorna401Unauthorized` | HTTP 401 | Aprobado |
| **ruta-service** | `peticionConTokenAlteradoRetorna401Unauthorized` | HTTP 401 | Aprobado |
| **ruta-service** | `endpointCalcularRutaDevuelveCasoManualEsperadoConTokenValido` | HTTP 200 | Aprobado |

---

## 5. Instrucciones de Prueba Manual y Sustentación

### 5.1. Levantar la infraestructura
```bash
docker compose up -d
```

### 5.2. Prueba 1: Petición rechazada sin token (HTTP 401)
```bash
curl -i http://localhost:8080/api/rutas/calcular?desde=0&hasta=11&redId=1
```
*Respuesta esperada:* `HTTP/1.1 401 Unauthorized` con cabecera `WWW-Authenticate: Bearer`.

### 5.3. Prueba 2: Petición rechazada con firma alterada (HTTP 401)
```bash
curl -i -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.tokenFalso.firmaAlterada" \
  http://localhost:8080/api/rutas/calcular?desde=0&hasta=11&redId=1
```
*Respuesta esperada:* `HTTP/1.1 401 Unauthorized` con cabecera `error="invalid_token"`.

### 5.4. Prueba 3: Petición exitosa con token legítimo de Keycloak (HTTP 200)
```bash
# Obtener token de Keycloak
TOKEN=$(curl -s -X POST "http://localhost:8085/realms/rutas-realm/protocol/openid-connect/token" \
  -d "client_id=red-rutas-frontend" \
  -d "grant_type=password" \
  -d "username=consultor" \
  -d "password=consultor123" | jq -r .access_token)

# Consumir el API a traves del Gateway con el token
curl -i -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/rutas/calcular?desde=0&hasta=11&redId=1"
```
*Respuesta esperada:* `HTTP/1.1 200 OK` con el JSON de la ruta calculada.
