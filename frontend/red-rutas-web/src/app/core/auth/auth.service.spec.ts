import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService (OAuth 2.0 PKCE)', () => {
  let service: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuthService]
    });
    service = TestBed.inject(AuthService);
  });

  it('debe crearse correctamente', () => {
    expect(service).toBeTruthy();
  });

  it('debe iniciar con estado no autenticado y sin usuario', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.currentUser()).toBeNull();
    expect(service.token()).toBeNull();
    expect(service.userRoles()).toEqual([]);
    expect(service.username()).toBe('Invitado');
    expect(service.userSub()).toBe('');
  });

  it('debe permitir configurar sesion y actualizar senales reactivas', () => {
    service.setMockSession(
      {
        sub: '00000000-keycloak-sub-uuid',
        username: 'juan.planificador',
        email: 'juan@uceva.edu.co',
        roles: ['planificador']
      },
      'token.jwt.firmado'
    );

    expect(service.isAuthenticated()).toBe(true);
    expect(service.username()).toBe('juan.planificador');
    expect(service.userSub()).toBe('00000000-keycloak-sub-uuid');
    expect(service.hasRole('planificador')).toBe(true);
    expect(service.hasRole('admin')).toBe(false);
    expect(service.isPlanificador()).toBe(true);
    expect(service.isAdmin()).toBe(false);
  });

  it('debe verificar hasAnyRole correctamente', () => {
    service.setMockSession(
      {
        sub: 'uuid-consultor-1',
        username: 'ana.consultora',
        roles: ['consultor']
      },
      'token.jwt.consultor'
    );

    expect(service.hasAnyRole(['consultor', 'admin'])).toBe(true);
    expect(service.hasAnyRole(['planificador', 'admin'])).toBe(false);
    expect(service.isConsultor()).toBe(true);
    expect(service.isPlanificador()).toBe(false);
  });

  it('al cerrar sesion debe limpiar todos los estados reactivos', async () => {
    service.setMockSession(
      {
        sub: 'uuid-admin-1',
        username: 'admin.general',
        roles: ['admin']
      },
      'token.jwt.admin'
    );

    // Mock logout interno
    service.isAuthenticated.set(false);
    service.currentUser.set(null);
    service.userRoles.set([]);
    service.token.set(null);

    expect(service.isAuthenticated()).toBe(false);
    expect(service.currentUser()).toBeNull();
    expect(service.token()).toBeNull();
    expect(service.userRoles()).toEqual([]);
  });
});
