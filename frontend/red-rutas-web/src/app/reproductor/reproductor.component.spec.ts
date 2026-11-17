import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReproductorComponent } from './reproductor.component';
import { ReproductorService } from './reproductor.service';
import { RedService } from '../core/services/red.service';
import { RUTA_GUIA, NOMBRES_GUIA, nombreGuia } from './traza.fixtures';
import { construirTrazaDijkstra } from './traza.builder';
import { crearArchivoTraza } from './traza-archivo';

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
    rep.limpiar();
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

  it('con ruta-service caído muestra el aviso, el botón Reintentar y no se queda cargando', () => {
    fixture.componentInstance.hasta.set(4);
    boton('Calcular ruta').click();
    http.expectOne(r => r.url.endsWith('/api/rutas/calcular')).flush(
      { servicio: 'ruta-service', motivo: 'TIMEOUT', mensaje: 'El microservicio ruta-service excedio el tiempo limite.' },
      { status: 503, statusText: 'Service Unavailable' }
    );
    fixture.detectChanges();

    const aviso = el.querySelector('.rep-degradado')?.textContent ?? '';
    expect(aviso).toContain('Modo degradado');
    expect(aviso).toContain('El mapa y la red siguen funcionando');
    expect(aviso).toContain('Reintento automático en 10 s');
    expect(boton('Reintentar').disabled).toBe(false);
    expect(el.querySelector('.spinner')).toBeNull();

    boton('Reintentar').click();
    http.expectOne(r => r.url.endsWith('/api/rutas/calcular')).flush(RUTA_GUIA);
    fixture.detectChanges();
    expect(el.querySelector('.rep-degradado')).toBeNull();
    expect(el.querySelector('.rep-recuperado')?.textContent).toContain('volvió a responder');
    expect(el.querySelector('.vcr-contador')?.textContent).toContain('Paso 0 de 5');
  });

  it('importar un JSON reproduce la traza paso a paso sin consultar al backend', () => {
    const archivo = crearArchivoTraza(construirTrazaDijkstra(RUTA_GUIA, nombreGuia), { paradas: 5 });
    fixture.componentInstance.cargarArchivo(JSON.stringify(archivo), 'traza.json');
    fixture.detectChanges();

    expect(el.querySelector('.rep-archivo')?.textContent).toContain('sin consultar al servidor');
    expect(el.querySelector('.vcr-contador')?.textContent).toContain('Paso 0 de 5');
    rep.irFin();
    fixture.detectChanges();
    expect(el.querySelector('.resultado')?.textContent).toContain('11 min');
    http.expectNone(() => true);
  });

  it('no importa una traza que usa paradas que la red cargada no tiene', () => {
    const red = TestBed.inject(RedService);
    red.paradas.set(red.paradas().slice(0, 3));
    fixture.detectChanges();
    const archivo = crearArchivoTraza(construirTrazaDijkstra(RUTA_GUIA, nombreGuia), { paradas: 5 });
    expect(() => fixture.componentInstance.cargarArchivo(JSON.stringify(archivo), 'traza.json'))
      .toThrow('solo tiene 3 paradas');
    expect(rep.traza()).toBeNull();
  });

  it('exportar descarga la traza actual como archivo JSON', () => {
    calcularGuia();
    const crear = vi.fn(() => 'blob:traza');
    const revocar = vi.fn();
    Object.assign(URL, { createObjectURL: crear, revokeObjectURL: revocar });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    boton('Exportar JSON').click();
    fixture.detectChanges();

    expect(crear).toHaveBeenCalledTimes(1);
    expect(click).toHaveBeenCalledTimes(1);
    expect(el.querySelector('.rep-archivo')?.textContent).toMatch(/traza-dijkstra-0-4-.*\.json \(6 pasos\)/);
    click.mockRestore();
  });

  it('las flechas del teclado avanzan y retroceden', () => {
    calcularGuia();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    expect(rep.indice()).toBe(1);
  });
});
