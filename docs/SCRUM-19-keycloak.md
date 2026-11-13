# Documentación Técnica: SCRUM-19 [RA-01]

## 1. Información General
* **Historia de Usuario:** SCRUM-19 [RA-01]
* **Título:** Contenedor Keycloak y exportación del realm `rutas-realm.json`
* **Responsable:** Jhonatan Varón Henao (DevSecOps, Ciberseguridad & Backend de Datos)
* **Equipo:** Villanos — UCEVA, 6.° Semestre
* **Rama Git:** `feature/jhonatan-scrum-19-keycloak`

---

## 2. Objetivo y Justificación Técnica

El sistema "Red de Rutas" exige en su especificación de arquitectura y requisitos de identidad (Web 2) que ningún endpoint del backend permanezca abierto o desprotegido. Para ello, se adopta un modelo de seguridad basado en **OAuth 2.0 y OpenID Connect (OIDC)** con un Servidor de Autorización centralizado provisto por **Keycloak**.

### Criterio de Aceptación
1. **Contenedor Docker para Keycloak:** Configurado en `docker-compose.yml` en la red `red-de-rutas-net`.
2. **Realm Versionado (`keycloak/rutas-realm.json`):**
   * Nombre del realm: `rutas-realm`.
   * **Cliente Frontend (`red-rutas-frontend`):** Cliente público para Angular con flujo *Authorization Code con PKCE (S256)*.
   * **Cliente Backend (`red-rutas-backend`):** Resource Server (*bearer-only*) para validación de tokens JWKS en Spring Boot.
   * **Roles de aplicación:** `planificador` (escritura/edición de la red), `consultor` (solo lectura de rutas) y `admin`.
   * **Usuarios de prueba:** Credenciales no temporales creadas para demostración en sustentación.
3. **Carga Automática:** Al iniciar el contenedor en una máquina limpia con `docker compose up keycloak`, el realm debe importarse automáticamente sin requerir intervención manual en la interfaz administrativa.

---

## 3. Especificación del Realm (`rutas-realm.json`)

### 3.1. Clientes OAuth 2.0 / OIDC

| ID del Cliente | Tipo | Propósito | Configuración Clave |
|---|---|---|---|
| `red-rutas-frontend` | Público | Aplicación SPA en Angular 17+ | Standard Flow (Code Flow) con **PKCE (S256)**, `redirect_uri: http://localhost:4200/*`, Web Origins: `+` (CORS permitido). |
| `red-rutas-backend` | Bearer-only | Spring Boot Microservicios | Valida los tokens de acceso JWT contra el endpoint de claves públicas JWKS de Keycloak. |

### 3.2. Roles del Realm

* **`planificador`**: Permite mantenimiento de la red (crear, modificar y eliminar paradas y tramos en `grafo-service`).
* **`consultor`**: Permite consulta de la red y cálculo de caminos mínimos en `ruta-service` (solo lectura).
* **`admin`**: Rol compuesto que hereda `planificador` y `consultor` con control total administrativo.

### 3.3. Usuarios de Prueba Versionados

| Usuario | Contraseña | Roles Asignados | Propósito en Sustentación |
|---|---|---|---|
| `planificador` | `planificador123` | `planificador` | Demostrar creación y edición exitosa de tramos. |
| `consultor` | `consultor123` | `consultor` | Demostrar acceso a consulta y restricción `403 Forbidden` al intentar editar. |
| `admin` | `admin123` | `admin`, `planificador`, `consultor` | Acceso completo. |

---

## 4. Orquestación y Despliegue en Docker Compose

En el archivo `docker-compose.yml`, el servicio `keycloak` se definió de la siguiente manera:

```yaml
  keycloak:
    image: quay.io/keycloak/keycloak:24.0.5
    container_name: keycloak
    command: start-dev --import-realm
    environment:
      - KEYCLOAK_ADMIN=admin
      - KEYCLOAK_ADMIN_PASSWORD=admin
      - KC_HEALTH_ENABLED=true
      - KC_METRICS_ENABLED=true
      - KC_HTTP_ENABLED=true
    volumes:
      - ./keycloak/rutas-realm.json:/opt/keycloak/data/import/rutas-realm.json:ro
    ports:
      - "8085:8080"
    networks:
      - red-de-rutas-net
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/health/ready || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 6
      start_period: 30s
    restart: unless-stopped
```

### Mecanismo de Importación en Máquina Limpia
Keycloak (en su distribución moderna basada en Quarkus) detecta la bandera `--import-realm` y lee los archivos `.json` ubicados en el directorio `/opt/keycloak/data/import/`. Al arrancar por primera vez, si el realm `rutas-realm` no existe en la base de datos interna, lo crea e inicializa con todos los clientes, roles y usuarios definidos en el archivo versionado.

---

## 5. Instrucciones de Prueba y Verificación

### 5.1. Validación Estática del Realm
Ejecutar el script automatizado de verificación:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/test_keycloak_realm.ps1
```

### 5.2. Levantar el Contenedor de Keycloak
```bash
docker compose up -d keycloak
```

### 5.3. Verificar Endpoints OIDC
Una vez que el contenedor esté en estado `healthy`:
* **Well-Known OIDC Configuration:**
  ```bash
  curl http://localhost:8085/realms/rutas-realm/.well-known/openid-configuration
  ```
* **Certificados y claves públicas (JWKS):**
  ```bash
  curl http://localhost:8085/realms/rutas-realm/protocol/openid-connect/certs
  ```

### 5.4. Obtención de Token de Prueba vía CLI
```bash
curl -X POST "http://localhost:8085/realms/rutas-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=red-rutas-frontend" \
  -d "grant_type=password" \
  -d "username=planificador" \
  -d "password=planificador123"
```
El servidor responde con un token JWT firmado (`access_token`) con el claim `sub`, `preferred_username` y el rol `planificador`.
