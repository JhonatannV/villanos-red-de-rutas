import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComparadorComponent } from './comparador.component';
import { ComparadorService } from './comparador.service';
import { PanelService } from '../../core/services/panel.service';
import { ReproductorService } from '../reproductor.service';
import { RedService } from '../../core/services/red.service';
import { construirTrazaDijkstra, construirTrazaRecorrido } from '../traza.builder';
import { BFS_PEQUENO, NOMBRES_GUIA, RUTA_GUIA, RUTA_GUIA_SIN_BD, TRAMOS_GUIA, nombreGuia } from '../traza.fixtures';

describe('ComparadorComponent (RF-08 · SCRUM-33)', () => {
  let fixture: ComponentFixture<ComparadorComponent>;
  let el: HTMLElement;
  let rep: ReproductorService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ComparadorComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    const red = TestBed.inject(RedService);
    red.redActual.set({ redId: 1, nombre: 'Guía', paradas: [], tramos: [] });
    red.paradas.set(NOMBRES_GUIA.map((nombre, id) => ({ id, nombre, x: id * 100, y: 100 })));
    red.tramos.set(TRAMOS_GUIA);
    rep = TestBed.inject(ReproductorService);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ComparadorComponent);
    el = fixture.nativeElement;
  });

  afterEach(() => {
    rep.limpiar();
    TestBed.inject(ComparadorService).limpiar();
    http.verify();
  });

  it('sin una ruta de Dijkstra explica qué hacer y lleva a Recorrido', () => {
    fixture.detectChanges();
    expect(el.querySelector('.comp')).toBeNull();
    expect(el.querySelector('.comp-vacio')?.textContent).toContain('Primero calcula una ruta con Dijkstra');
    rep.cargarTraza(construirTrazaRecorrido(BFS_PEQUENO, nombreGuia));
    fixture.detectChanges();
    expect(el.querySelector('.comp')).toBeNull();

    const panel = TestBed.inject(PanelService);
    panel.abrir('comparar');
    [...el.querySelectorAll('button')].find(b => b.textContent?.includes('Ir a Recorrido'))!.click();
    expect(panel.pestana()).toBe('recorrido');
  });

  it('ofrece primero los tramos de la ruta y propone el primero', () => {
    rep.cargarTraza(construirTrazaDijkstra(RUTA_GUIA, nombreGuia));
    fixture.detectChanges();
    const grupos = el.querySelectorAll('optgroup');
    expect(grupos[0].getAttribute('label')).toBe('Tramos de la ruta');
    const enRuta = [...grupos[0].querySelectorAll('option')].map(o => o.textContent?.trim());
    expect(enRuta).toEqual(['Tramo 1: A ↔ B (4 min)', 'Tramo 4: B ↔ D (5 min)', 'Tramo 7: D ↔ E (2 min)']);
    expect(fixture.componentInstance.tramoElegido()).toBe(1);
  });

  it('al comparar muestra el delta de costo y las dos rutas', () => {
    rep.cargarTraza(construirTrazaDijkstra(RUTA_GUIA, nombreGuia));
    fixture.detectChanges();
    fixture.componentInstance.elegir('4');
    [...el.querySelectorAll('button')].find(b => b.textContent?.includes('Comparar'))!.click();
    http.expectOne(r => r.params.get('sin') === '4').flush(RUTA_GUIA_SIN_BD);
    fixture.detectChanges();

    expect(el.querySelector('.comp-delta')?.textContent).toContain('Aumentó en +1 minuto');
    expect(el.querySelector('.comp-delta-num')?.textContent).toContain('+1 min');
    const rutas = el.querySelector('.comp-rutas')?.textContent ?? '';
    expect(rutas).toContain('Ruta original · 11 min');
    expect(rutas).toContain('A → B → D → E');
    expect(rutas).toContain('Con el tramo 4 cerrado · 12 min');
    expect(rutas).toContain('A → C → D → E');
    expect(el.querySelector('.comp-cambios')?.textContent).toContain('Deja de pasar por B');
    expect(rep.enFin()).toBe(true);
  });

  it('una ruta nueva borra la comparación anterior', () => {
    rep.cargarTraza(construirTrazaDijkstra(RUTA_GUIA, nombreGuia));
    fixture.detectChanges();
    fixture.componentInstance.comparar();
    http.expectOne(() => true).flush(RUTA_GUIA_SIN_BD);
    expect(TestBed.inject(ComparadorService).activo()).toBe(true);

    rep.cargarTraza(construirTrazaDijkstra({ ...RUTA_GUIA }, nombreGuia));
    fixture.detectChanges();
    expect(TestBed.inject(ComparadorService).activo()).toBe(false);
  });
});
