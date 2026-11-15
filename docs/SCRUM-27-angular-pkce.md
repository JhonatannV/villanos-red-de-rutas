# SCRUM-27 [RA-02] Inicio de Sesión en Angular con Authorization Code y PKCE

**Sprint:** Sprint 4 (Frontend en Angular)  
**Ticket Jira:** [SCRUM-27](https://villanos.atlassian.net/browse/SCRUM-27)  
**Responsable:** Jhonatan Varón Henao (`jhonatan.varon01@uceva.edu.co`) - DevSecOps, Ciberseguridad & Backend de Datos  
**Equipo:** Villanos · Proyecto Integrador UCEVA (Semestre 6)

---

## 1. Objetivo del Incremento

Inicializar la aplicación frontend en **Angular 17+** (utilizando componentes *Standalone* y reactividad moderna con *Signals*) e integrarla de forma nativa con **Keycloak** aplicando el flujo de seguridad estándar **Authorization Code Flow con PKCE (Proof Key for Code Exchange - RFC 7636)**.

### Propiedades de seguridad garantizadas:
1. **Cliente Público:** El frontend opera como cliente público (`red-rutas-frontend`) sin ningún secreto de cliente (`client-secret`) almacenado en el navegador.
2. **Cero Contraseñas en el Frontend:** Las credenciales del usuario (usuario y contraseña) nunca son tocadas, leídas ni procesadas por el código Angular. Toda autenticación ocurre directamente en el servidor seguro de Keycloak.
3. **Criptografía PKCE con SHA-256 (`S256`):** En cada intento de login, el frontend genera un `code_verifier` aleatorio y deriva un `code_challenge` criptográfico que envía a Keycloak, protegiendo contra ataques de intercepción de códigos de autorización.

---

## 2. Criterio de Aceptación

> **Criterio de Aceptación Jira [RA-02]:**  
> *"Al navegar sin sesión redirige a Keycloak; tras autenticarse regresa a la app. No existen contraseñas en el frontend."*

---

## 3. Arquitectura Frontend y Componentes Desarrollados

### 3.1 Estructura del Proyecto (`frontend/red-rutas-web`)
- **Arquitectura:** Angular 17+ Standalone (sin `NgModule` legados), habilitando compilación modular y bundles optimizados.
- **Configuración de Entorno (`src/environments/environment.ts`):**
  - Servidor Keycloak: `http://localhost:8085`
  - Reino: `rutas-realm`
  - Client ID: `red-rutas-frontend`
  - Gateway API: `http://localhost:8080`

### 3.2 Servicio de Autenticación Reactivo (`AuthService`)
Implementado en `src/app/core/auth/auth.service.ts` utilizando Angular Signals:
- `isAuthenticated = signal<boolean>(false);`
- `currentUser = signal<UserProfile | null>(null);`
- `userRoles = signal<string[]>([]);`
- `token = signal<string | null>(null);`
- **Computed Signals:** `username()`, `userSub()`, `isPlanificador()`, `isConsultor()`, `isAdmin()`.
- **Métodos:**
  - `init()`: Inicializa `Keycloak` con `{ onLoad: 'check-sso', pkceMethod: 'S256' }`.
  - `login()`: Redirige al usuario al servidor Keycloak preservando el URI de retorno.
  - `logout()`: Ejecuta frontchannel logout en Keycloak y limpia las señales locales.
  - `getToken()`: Retorna el JWT y ejecuta refresco automático transparente si resta menos de 30 segundos de vigencia.

### 3.3 Guard Funcional de Rutas (`authGuard`)
Implementado en `src/app/core/auth/auth.guard.ts`:
- Si el usuario no tiene sesión iniciada, invoca automáticamente `authService.login()`, redirigiendo el navegador a la pantalla de login de Keycloak.
- Tras la autenticación exitosa, Keycloak redirige al navegador de vuelta a la aplicación con el parámetro `code`, el cual se intercambia silenciosamente por el JWT mediante el `code_verifier`.
- Si la ruta requiere un rol específico (e.g. `planificador`), valida que `authService.hasRole(rol)` sea verdadero antes de permitir la activación.

### 3.4 Interfaz de Usuario (`App`)
- Barra superior con información institucional y de seguridad.
- Estado de autenticación reactivo:
  - En modo invitado: Notificación de acceso público y botón de inicio de sesión PKCE.
  - En sesión activa: Nombre de usuario, identificador único `sub`, insignias de roles (`planificador`, `consultor`, `admin`) y botón de cierre de sesión.

---

## 4. Pruebas Automatizadas

Se crearon suites de pruebas unitarias con Vitest y TestBed:
- `src/app/app.spec.ts`: Creación del componente, visualización de títulos e insignias según el estado de autenticación.
- `src/app/core/auth/auth.service.spec.ts`: Validación de señales reactivas, extracción de roles de reino, identificación `sub` y métodos de consulta de permisos.
- `src/app/core/auth/auth.guard.spec.ts`: Validación del criterio de aceptación (redirección a login de Keycloak ante ausencia de sesión y control por roles).

### Resultado de Pruebas:
```text
 ✓  red-rutas-web  src/app/core/auth/auth.guard.spec.ts (3 tests)
 ✓  red-rutas-web  src/app/core/auth/auth.service.spec.ts (5 tests)
 ✓  red-rutas-web  src/app/app.spec.ts (4 tests)

 Test Files  3 passed (3)
      Tests  12 passed (12)
   Duration  3.24s
```
Total: **12 pruebas ejecutadas, 12 exitosas, 0 fallos**.

### Resultado de Compilación de Producción (`ng build`):
```text
Application bundle generation complete. [5.311 seconds]
Output location: dist/red-rutas-web
```
Compilación limpia sin advertencias ni errores.
