import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, Observable, throwError } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';

/**
 * Interceptor HTTP Funcional para Angular 17+ (SCRUM-28 [RA-03]).
 * Adjunta automaticamente el encabezado 'Authorization: Bearer <token>' a todas
 * las peticiones salientes hacia la API.
 * 
 * Criterio de aceptacion:
 * - Todas las peticiones HTTP llevan el token.
 * - Si el token vence o esta por vencer (< 30s), se refresca transparentemente antes del envio.
 */
export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
): Observable<HttpEvent<unknown>> => {
  const authService = inject(AuthService);

  // Excluir assets estaticos o peticiones internas de configuracion si no requieren token
  if (req.url.includes('/assets/') || req.url.endsWith('.json') && !req.url.includes('/api/')) {
    return next(req);
  }

  // Obtener el token de forma asincrona para asegurar que si expiro o esta cerca de vencer,
  // Keycloak ejecute un updateToken() transparente antes de clonar la peticion.
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
          // Si el backend responde 401 Unauthorized a pesar del token (ej. expiracion en vuelo),
          // intentamos un refresco y reintento unico
          if (error.status === 401 && authService.isAuthenticated()) {
            return from(authService.refreshToken()).pipe(
              switchMap(refrescado => {
                if (refrescado) {
                  const nuevoToken = authService.token();
                  if (nuevoToken) {
                    const reintentoPeticion = req.clone({
                      setHeaders: {
                        Authorization: `Bearer ${nuevoToken}`
                      }
                    });
                    return next(reintentoPeticion);
                  }
                }
                return throwError(() => error);
              }),
              catchError(() => throwError(() => error))
            );
          }
          return throwError(() => error);
        })
      );
    })
  );
};
