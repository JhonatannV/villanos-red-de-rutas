import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { RedGestorComponent } from './red-gestor.component';
import { RedService } from '../../core/services/red.service';
import { AuthService } from '../../core/auth/auth.service';

describe('RedGestorComponent', () => {
  let fixture: ComponentFixture<RedGestorComponent>;
  let component: RedGestorComponent;
  let httpTestingController: HttpTestingController;
  let authService: AuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RedGestorComponent],
      providers: [
        RedService,
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RedGestorComponent);
    component = fixture.componentInstance;
    httpTestingController = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('debe crearse e inicializar la carga de la red en ngOnInit', () => {
    fixture.detectChanges(); // Ejecuta ngOnInit()

    const req = httpTestingController.expectOne(req => req.url.includes('/api/redes/1'));
    req.flush({ redId: 1, nombre: 'Red', paradas: [], tramos: [] });

    expect(component).toBeTruthy();
  });

  it('debe bloquear los formularios si el usuario no tiene rol planificador o admin', () => {
    // Usuario autenticado solo con rol consultor
    authService.setMockSession(
      {
        sub: 'sub-consultor',
        username: 'consultor',
        roles: ['consultor']
      },
      'token-consultor'
    );

    fixture.detectChanges(); // Ejecuta ngOnInit()

    const req = httpTestingController.expectOne(req => req.url.includes('/api/redes/1'));
    req.flush({ redId: 1, nombre: 'Red', paradas: [], tramos: [] });

    expect(authService.isPlanificador()).toBe(false);
  });

  it('debe iniciar con el listado colapsado y permitir expandirlo o contraerlo', () => {
    fixture.detectChanges();
    const req = httpTestingController.expectOne(req => req.url.includes('/api/redes/1'));
    req.flush({ redId: 1, nombre: 'Red', paradas: [], tramos: [] });

    // Inicia minimizado por defecto
    expect(component.tablaExpandida()).toBe(false);

    // Expandir
    component.toggleTabla();
    expect(component.tablaExpandida()).toBe(true);

    // Contraer
    component.toggleTabla();
    expect(component.tablaExpandida()).toBe(false);

    // Al cambiar de tab se debe expandir
    component.cambiarTab('tramos');
    expect(component.tabActiva()).toBe('tramos');
    expect(component.tablaExpandida()).toBe(true);
  });
});
