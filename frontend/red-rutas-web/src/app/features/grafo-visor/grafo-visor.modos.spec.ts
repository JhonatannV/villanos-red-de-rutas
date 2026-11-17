import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { GrafoVisorComponent } from './grafo-visor.component';
import { RedService } from '../../core/services/red.service';
import { PanelService } from '../../core/services/panel.service';
import { ReproductorService } from '../../reproductor/reproductor.service';
import { ComparadorService } from '../../reproductor/comparador/comparador.service';
import { construirTrazaDijkstra } from '../../reproductor/traza.builder';
import { NOMBRES_GUIA, RUTA_GUIA, TRAMOS_GUIA, nombreGuia } from '../../reproductor/traza.fixtures';

/** Rediseño de la mesa de trabajo: qué pinta el mapa según la pestaña abierta. */
describe('GrafoVisorComponent · modos del mapa', () => {
  let fixture: ComponentFixture<GrafoVisorComponent>;
  let el: HTMLElement;
  let rep: ReproductorService;
  let panel: PanelService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GrafoVisorComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    const red = TestBed.inject(RedService);
    red.redActual.set({ redId: 1, nombre: 'Red guía', paradas: [], tramos: [] });
    red.paradas.set(NOMBRES_GUIA.map((nombre, id) => ({ id, nombre, x: 100 + id * 150, y: 200 })));
    red.tramos.set(TRAMOS_GUIA);
    rep = TestBed.inject(ReproductorService);
    panel = TestBed.inject(PanelService);
    fixture = TestBed.createComponent(GrafoVisorComponent);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => {
    rep.limpiar();
    TestBed.inject(ComparadorService).limpiar();
  });

  const leyenda = () => [...el.querySelectorAll('.legend-item')].map(e => e.textContent?.trim());

  it('muestra el nombre de la red y sus cifras en la barra', () => {
    expect(el.querySelector('.toolbar-title h2')?.textContent).toContain('Red guía');
    expect(el.querySelector('.toolbar-stats')?.textContent).toContain('5 paradas');
    expect(el.querySelector('.toolbar-stats')?.textContent).toContain('7 tramos');
  });

  it('los pesos están ocultos al empezar y el botón Pesos los muestra', () => {
    expect(el.querySelectorAll('.weight-badge-group').length).toBe(0);
    const boton = [...el.querySelectorAll('button')].find(b => b.textContent?.trim() === 'Pesos')!;
    boton.click();
    fixture.detectChanges();
    expect(el.querySelectorAll('.weight-badge-group').length).toBe(7);
    expect(boton.getAttribute('aria-pressed')).toBe('true');
  });

  it('la leyenda cambia de la red sola a la del reproductor', () => {
    expect(leyenda()).toEqual(['Tramo abierto', 'Cierre vial']);
    rep.cargarTraza(construirTrazaDijkstra(RUTA_GUIA, nombreGuia));
    fixture.detectChanges();
    expect(leyenda()).toContain('Parada actual');
    expect(leyenda()).toContain('Ruta final');
  });

  it('en el último paso la ruta se dibuja tramo a tramo desde el origen', () => {
    rep.cargarTraza(construirTrazaDijkstra(RUTA_GUIA, nombreGuia));
    rep.irFin();
    fixture.detectChanges();
    const ruta = [...el.querySelectorAll<SVGLineElement>('.tramo-traza-camino')];
    expect(ruta.length).toBe(3);
    expect(ruta.every(l => l.getAttribute('pathLength') === '1')).toBe(true);
    const retardos = ruta.map(l => l.style.animationDelay).sort();
    expect(retardos).toEqual(['0ms', '140ms', '70ms']);
  });

  it('la comparación solo se pinta con la pestaña Comparar abierta', () => {
    rep.cargarTraza(construirTrazaDijkstra(RUTA_GUIA, nombreGuia));
    TestBed.inject(ComparadorService).comparacion.set({
      original: { desde: 0, hasta: 4, costo: 11, camino: [0, 1, 3, 4] },
      alterna: { desde: 0, hasta: 4, costo: 12, camino: [0, 2, 3, 4] },
      tramoCerrado: TRAMOS_GUIA[3], delta: 1, paradasQueSeDejan: [1], paradasNuevas: [2]
    });
    fixture.detectChanges();
    expect(el.querySelectorAll('.tramo-comp-cerrado').length).toBe(0);

    panel.abrir('comparar');
    fixture.detectChanges();
    expect(el.querySelectorAll('.tramo-comp-cerrado').length).toBe(1);
    expect(el.querySelectorAll('.node-comp-alterna').length).toBe(1);
    expect(leyenda()).toContain('Tramo cerrado');

    panel.abrir('recorrido');
    fixture.detectChanges();
    expect(el.querySelectorAll('.tramo-comp-cerrado').length).toBe(0);
  });
});
