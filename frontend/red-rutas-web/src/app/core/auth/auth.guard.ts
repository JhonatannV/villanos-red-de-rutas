import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Guard funcional que protege rutas de Angular.
 * Criterio de aceptacion RA-02:
 * Al navegar sin sesion redirige a Keycloak mediante el flujo PKCE.
 * Tras autenticarse regresa a la aplicacion.
 */
export const authGuard: CanActivateFn = async (route, state) => {
  const authService = inject(AuthService);

  if (!authService.isInitialized()) {
    await authService.init();
  }

  if (authService.isAuthenticated()) {
    // Si la ruta requiere un rol especifico
    const requiredRole = route.data?.['role'] as string | undefined;
    if (requiredRole && !authService.hasRole(requiredRole)) {
      console.warn(`Acceso denegado: Se requiere el rol ${requiredRole}`);
      return false;
    }
    return true;
  }

  // Redirigir a Keycloak si no esta autenticado
  await authService.login();
  return false;
};
