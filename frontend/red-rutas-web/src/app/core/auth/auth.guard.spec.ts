import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard (Criterio de Aceptacion RA-02)', () => {
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService]
    });
    authService = TestBed.inject(AuthService);
  });

  it('debe permitir la navegacion si el usuario ya esta autenticado', async () => {
    authService.setMockSession(
      {
        sub: 'uuid-1',
        username: 'usuario.autenticado',
        roles: ['consultor']
      },
      'token-valido'
    );

    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;

    const result = await TestBed.runInInjectionContext(() => authGuard(route, state));
    expect(result).toBe(true);
  });

  it('debe redirigir al login de Keycloak si el usuario no tiene sesion iniciada', async () => {
    authService.isAuthenticated.set(false);
    authService.isInitialized.set(true);

    let loginLlamado = false;
    authService.login = async () => {
      loginLlamado = true;
    };

    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;

    const result = await TestBed.runInInjectionContext(() => authGuard(route, state));
    expect(result).toBe(false);
    expect(loginLlamado).toBe(true);
  });

  it('debe denegar acceso si la ruta exige un rol especifico que el usuario no tiene', async () => {
    authService.setMockSession(
      {
        sub: 'uuid-2',
        username: 'consultor.solo.lectura',
        roles: ['consultor']
      },
      'token-consultor'
    );

    const route = {
      data: { role: 'planificador' }
    } as unknown as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;

    const result = await TestBed.runInInjectionContext(() => authGuard(route, state));
    expect(result).toBe(false);
  });
});
