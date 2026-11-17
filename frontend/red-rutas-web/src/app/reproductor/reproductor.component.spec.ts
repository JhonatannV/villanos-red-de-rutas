import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReproductorComponent } from './reproductor.component';
import { ReproductorService } from './reproductor.service';
import { RedService } from '../core/services/red.service';
import { RUTA_GUIA, NOMBRES_GUIA } from './traza.fixtures';

describe('ReproductorComponent (RF-04 · SCRUM-31)', () => {
  let fixture: ComponentFixture<ReproductorComponent>;
  let el: HTMLElement;
  let rep: ReproductorService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReproductorComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    const red = TestBed.inject(RedService);
    red.redActual.set({ redId: 1, nombre: 'Guía', paradas: [], tramos: [] });
    red.paradas.set(NOMBRES_GUIA.map((nombre, id) => ({ id, nombre, x: id * 100, y: 100 })));

    rep = TestBed.inject(ReproductorService);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReproductorComponent);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => {
    rep.pausar();
    http.verify();
  });

  const boton = (texto: string) =>
    [...el.querySelectorAll<HTMLButtonElement>('button')].find(b => b.textContent?.includes(texto))!;

  const calcularGuia = () => {
    fixture.componentInstance.hasta.set(4);
    boton('Calcular ruta').click();
    http.expectOne(r => r.url.endsWith('/api/rutas/calcular')).flush(RUTA_GUIA);
    fixture.detectChanges();
  };

  it('antes de consultar muestra la ayuda y ofrece las paradas de la red', () => {
    expect(el.querySelector('.rep-vacio')).toBeTruthy();
    expect(el.querySelectorAll('select option').length).toBe(10); // origen y destino
  });

  it('al calcular muestra los controles, el paso 0 y la tabla de distancias', () => {
    calcularGuia();
    expect(el.querySelector('.vcr-contador')?.textContent).toContain('Paso 0 de 5');
    expect(el.querySelectorAll('.rep-tabla tbody tr').length).toBe(1);
    expect(boton('Atrás').disabled).toBe(true);
  });

  it('siguiente y atrás mueven la tabla y el contador', () => {
    calcularGuia();
    boton('Siguiente').click();
    boton('Siguiente').click();
    fixture.detectChanges();
    expect(el.querySelector('.vcr-contador')?.textContent).toContain('Paso 2 de 5');
    expect(el.querySelector('.paso-titulo h4')?.textContent).toContain('Se cierra C');
    expect(el.querySelectorAll('.rep-tabla tbody tr').length).toBe(5);
    expect(el.querySelector('.fila-actual')?.textContent).toContain('C');

    boton('Atrás').click();
    fixture.detectChanges();
    expect(el.querySelector('.paso-titulo h4')?.textContent).toContain('Se cierra A');
    expect(el.querySelectorAll('.rep-tabla tbody tr').length).toBe(3);
  });

  it('en el último paso muestra el costo y el camino', () => {
    calcularGuia();
    rep.irFin();
    fixture.detectChanges();
    const resultado = el.querySelector('.resultado')?.textContent ?? '';
    expect(resultado).toContain('11 min');
    expect(resultado).toContain('A → B → D → E');
  });

  it('no deja calcular con el mismo origen y destino', () => {
    fixture.componentInstance.hasta.set(0);
    fixture.detectChanges();
    expect(boton('Calcular ruta').disabled).toBe(true);
    expect(el.querySelector('.rep-aviso')).toBeTruthy();
  });

  it('las flechas del teclado avanzan y retroceden', () => {
    calcularGuia();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    expect(rep.indice()).toBe(1);
  });
});
