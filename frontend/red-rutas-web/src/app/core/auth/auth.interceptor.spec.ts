import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor (Criterio de Aceptacion RA-03)', () => {
  let httpClient: HttpClient;
  let httpTestingController: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpTestingController = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('debe crearse e interceptar peticiones HTTP', () => {
    expect(httpClient).toBeTruthy();
  });

  it('debe adjuntar automaticamente el encabezado Authorization: Bearer <token> a peticiones salientes a la API', async () => {
    const tokenPrueba = 'eyJhbGciOiJSUzI1NiJ9.tokenDePruebaJwt.firma';

    authService.setMockSession(
      {
        sub: 'sub-planificador-1',
        username: 'planificador1',
        roles: ['planificador']
      },
      tokenPrueba
    );

    // Mock de getToken para retornar el token actual
    authService.getToken = async () => tokenPrueba;

    httpClient.get('/api/redes/1').subscribe(response => {
      expect(response).toBeTruthy();
    });

    // Esperar microtask para switchMap
    await new Promise(resolve => setTimeout(resolve, 10));

    const req = httpTestingController.expectOne('/api/redes/1');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.has('Authorization')).toBe(true);
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${tokenPrueba}`);

    req.flush({ redId: 1, nombre: 'Red Troncal' });
  });

  it('debe refrescar transparentemente el token antes del envio si esta proximo a expirar', async () => {
    const tokenRefrescado = 'eyJhbGciOiJSUzI1NiJ9.tokenRefrescadoNuevo.firma';

    authService.setMockSession(
      {
        sub: 'sub-consultor-1',
        username: 'consultor1',
        roles: ['consultor']
      },
      'token-viejo'
    );

    let refreshEjecutado = false;
    authService.getToken = async () => {
      refreshEjecutado = true;
      authService.token.set(tokenRefrescado);
      return tokenRefrescado;
    };

    httpClient.post('/api/redes/1/paradas', { id: 10, nombre: 'Terminal' }).subscribe();

    await new Promise(resolve => setTimeout(resolve, 10));

    const req = httpTestingController.expectOne('/api/redes/1/paradas');
    expect(refreshEjecutado).toBe(true);
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${tokenRefrescado}`);

    req.flush({ id: 10, nombre: 'Terminal' });
  });

  it('no debe inyectar el token en assets estaticos o recursos locales', async () => {
    httpClient.get('/assets/silent-check-sso.html').subscribe();

    const req = httpTestingController.expectOne('/assets/silent-check-sso.html');
    expect(req.request.headers.has('Authorization')).toBe(false);

    req.flush('<html></html>');
  });

  it('debe reintentar la peticion tras refrescar el token si el servidor responde 401', async () => {
    const tokenInicial = 'token-expirado';
    const tokenRenovado = 'token-renovado-exitoso';

    authService.setMockSession(
      {
        sub: 'sub-admin',
        username: 'admin',
        roles: ['admin']
      },
      tokenInicial
    );

    authService.getToken = async () => tokenInicial;

    let refreshTokenLlamado = false;
    authService.refreshToken = async () => {
      refreshTokenLlamado = true;
      authService.token.set(tokenRenovado);
      return true;
    };

    httpClient.get('/api/redes/1/tramos').subscribe(response => {
      expect(response).toBeTruthy();
    });

    await new Promise(resolve => setTimeout(resolve, 10));

    // Primera peticion responde 401
    const primerReq = httpTestingController.expectOne('/api/redes/1/tramos');
    expect(primerReq.request.headers.get('Authorization')).toBe(`Bearer ${tokenInicial}`);
    primerReq.flush('Token expirado', { status: 401, statusText: 'Unauthorized' });

    await new Promise(resolve => setTimeout(resolve, 10));

    // Segunda peticion reintentada con token renovado
    expect(refreshTokenLlamado).toBe(true);
    const reintentoReq = httpTestingController.expectOne('/api/redes/1/tramos');
    expect(reintentoReq.request.headers.get('Authorization')).toBe(`Bearer ${tokenRenovado}`);
    reintentoReq.flush([{ id: 1, origen: 0, destino: 1, peso: 5 }]);
  });
});
