# SCRUM-28 [RA-03] Interceptor HTTP en Angular para Bearer Token

**Sprint:** Sprint 4 (Frontend en Angular)  
**Ticket Jira:** [SCRUM-28](https://villanos.atlassian.net/browse/SCRUM-28)  
**Responsable:** Jhonatan Varón Henao (`jhonatan.varon01@uceva.edu.co`) - DevSecOps, Ciberseguridad & Backend de Datos  
**Equipo:** Villanos · Proyecto Integrador UCEVA (Semestre 6)

---

## 1. Objetivo del Incremento

Implementar un interceptor HTTP funcional (`HttpInterceptorFn`) en Angular 17+ que capture automáticamente todas las peticiones salientes hacia la API REST (a través del API Gateway y los microservicios) e inyecte el encabezado estándar:
$$\text{Authorization: Bearer } \langle\text{token\_jwt}\rangle$$

### Requisitos y comportamiento técnico:
1. **Inyección Universal y Automática:** Ningún servicio consumidor de datos en Angular debe preocuparse por concatenar o leer manualmente el token de autorización.
2. **Refresco Transparente Preventivo:** Antes de enviar la petición, se evalúa la vigencia del token (`authService.getToken()`). Si restan menos de 30 segundos para expirar, Keycloak ejecuta `updateToken(30)` de forma asíncrona y transparente para no enviar un token vencido al backend.
3. **Mecanismo de Reintento Reactivo ante 401:** Si por condiciones de red o expiración en tránsito el servidor responde `401 Unauthorized`, el interceptor captura el error, invoca `refreshToken()` y reintenta la petición una sola vez con el nuevo token sin interrumpir al usuario.
4. **Exclusión de Recursos Estáticos:** Las llamadas a activos estáticos locales (como `silent-check-sso.html` o archivos de configuración local) no llevan encabezados de autorización.

---

## 2. Criterio de Aceptación

> **Criterio de Aceptación Jira [RA-03]:**  
> *"Todas las peticiones HTTP llevan el token. Si el token vence, se refresca transparentemente."*

---

## 3. Implementación Técnica

### 3.1 Interceptor Funcional (`authInterceptor`)
Ubicado en `src/app/core/auth/auth.interceptor.ts`:

```typescript
export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
): Observable<HttpEvent<unknown>> => {
  const authService = inject(AuthService);

  if (req.url.includes('/assets/') || req.url.endsWith('.json') && !req.url.includes('/api/')) {
    return next(req);
  }

  return from(authService.getToken()).pipe(
    switchMap(token => {
      let peticionConToken = req;

      if (token) {
        peticionConToken = req.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`
          }
        });
      }

      return next(peticionConToken).pipe(
        catchError((error: HttpErrorResponse) => {
          if (error.status === 401 && authService.isAuthenticated()) {
            return from(authService.refreshToken()).pipe(
              switchMap(refrescado => {
                if (refrescado) {
                  const nuevoToken = authService.token();
                  if (nuevoToken) {
                    return next(req.clone({
                      setHeaders: { Authorization: `Bearer ${nuevoToken}` }
                    }));
                  }
                }
                return throwError(() => error);
              })
            );
          }
          return throwError(() => error);
        })
      );
    })
  );
};
```

### 3.2 Registro Global en `app.config.ts`
Se configuró en la inicialización de la aplicación Angular mediante:
```typescript
provideHttpClient(withInterceptors([authInterceptor]))
```

---

## 4. Pruebas Automatizadas y Validación

Se desarrolló la suite completa de pruebas unitarias en `src/app/core/auth/auth.interceptor.spec.ts` usando `provideHttpClientTesting()` y `HttpTestingController`:

1. **`debe adjuntar automaticamente el encabezado Authorization: Bearer <token> a peticiones salientes a la API`**:
   - Envía `GET /api/redes/1`.
   - Verifica que el encabezado `Authorization` está presente y contiene exactamente el formato `Bearer <token>`.
2. **`debe refrescar transparentemente el token antes del envio si esta proximo a expirar`**:
   - Simula un token próximo a expirar.
   - Valida que `authService.getToken()` actualiza el token y la petición se despacha con el token nuevo.
3. **`no debe inyectar el token en assets estaticos o recursos locales`**:
   - Petición a `/assets/silent-check-sso.html` se envía limpia sin encabezado `Authorization`.
4. **`debe reintentar la peticion tras refrescar el token si el servidor responde 401`**:
   - Simula una respuesta `401 Unauthorized` en el primer intento.
   - El interceptor ejecuta el refresco automático y reenvía exitosamente la solicitud con el nuevo token renovado.

### Resultado de Ejecución de Pruebas:
```text
 ✓  red-rutas-web  src/app/core/auth/auth.guard.spec.ts (3 tests)
 ✓  red-rutas-web  src/app/core/auth/auth.service.spec.ts (5 tests)
 ✓  red-rutas-web  src/app/core/auth/auth.interceptor.spec.ts (5 tests)
 ✓  red-rutas-web  src/app/app.spec.ts (4 tests)

 Test Files  4 passed (4)
      Tests  17 passed (17)
   Duration  3.73s
```
Total: **17 pruebas ejecutadas, 17 exitosas, 0 fallos**.
Compilación de producción verificada con `ng build`.
