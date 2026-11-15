import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';
import { RedService } from './core/services/red.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        AuthService,
        RedService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();
  });

  it('debe crear el componente principal App', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('debe renderizar el titulo del sistema de transporte', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.app-title')?.textContent).toContain('Red de Rutas');
  });

  it('debe mostrar boton de iniciar sesion cuando el usuario no esta autenticado', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.guest-label')?.textContent).toContain('Modo Invitado');
    expect(compiled.querySelector('.btn-primary')?.textContent).toContain('Iniciar Sesión');
  });

  it('debe mostrar la informacion del usuario y roles cuando esta autenticado', async () => {
    const fixture = TestBed.createComponent(App);
    const authService = TestBed.inject(AuthService);

    authService.setMockSession(
      {
        sub: 'uuid-12345-usuario-test',
        username: 'planificador.test',
        email: 'planificador@uceva.edu.co',
        roles: ['planificador', 'consultor']
      },
      'mock-jwt-token-123'
    );

    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.user-name')?.textContent).toContain('planificador.test');
    expect(compiled.querySelector('.role-planificador')?.textContent).toContain('planificador');
    expect(compiled.querySelector('.btn-outline')?.textContent).toContain('Cerrar Sesión');
  });
});
