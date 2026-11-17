import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MS_POR_PASO, ReproductorService } from './reproductor.service';
import { construirTrazaDijkstra } from './traza.builder';
import { BFS_PEQUENO, RUTA_GUIA, nombreGuia } from './traza.fixtures';

describe('ReproductorService (RF-04 · controles VCR)', () => {
  let rep: ReproductorService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    rep = TestBed.inject(ReproductorService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    rep.pausar();
    http.verify();
    vi.useRealTimers();
  });

  const cargarGuia = () => rep.cargarTraza(construirTrazaDijkstra(RUTA_GUIA, nombreGuia));

  it('pide la traza de Dijkstra a /api/rutas/calcular con traza=true', () => {
    let pasos = 0;
    rep.consultar({ algoritmo: 'dijkstra', desde: 0, hasta: 4, redId: 1 }, nombreGuia)
      .subscribe(t => (pasos = t.pasos.length));

    const req = http.expectOne(r => r.url.endsWith('/api/rutas/calcular'));
    expect(req.request.params.get('desde')).toBe('0');
    expect(req.request.params.get('hasta')).toBe('4');
    expect(req.request.params.get('traza')).toBe('true');
    expect(rep.cargando()).toBe(true);
    req.flush(RUTA_GUIA);

    expect(pasos).toBe(6);
    expect(rep.cargando()).toBe(false);
    expect(rep.indice()).toBe(0);
    expect(rep.traza()?.algoritmo).toBe('dijkstra');
  });

  it('pide BFS y DFS a /api/rutas/recorrido con el modo', () => {
    rep.consultar({ algoritmo: 'anchura', desde: 0, hasta: null, redId: 1 }, nombreGuia).subscribe();
    const req = http.expectOne(r => r.url.endsWith('/api/rutas/recorrido'));
    expect(req.request.params.get('modo')).toBe('anchura');
    req.flush(BFS_PEQUENO);
    expect(rep.traza()?.columnaValor).toBe('Nivel');
  });

  it('retroceder un paso restaura exactamente el estado anterior, sin reiniciar', () => {
    cargarGuia();
    rep.siguiente();
    rep.siguiente();
    const antes = rep.pasoActual();
    const filasAntes = structuredClone(antes!.filas);

    rep.siguiente();
    expect(rep.indice()).toBe(3);
    rep.anterior();

    expect(rep.indice()).toBe(2);
    expect(rep.pasoActual()).toBe(antes);
    expect(rep.pasoActual()!.filas).toEqual(filasAntes);
    expect(rep.estadoDe(2)).toBe('actual');
    expect(rep.valorDe(3)).toBe(10);
  });

  it('no se sale de los límites de la traza', () => {
    cargarGuia();
    rep.anterior();
    expect(rep.indice()).toBe(0);
    rep.irFin();
    rep.siguiente();
    expect(rep.indice()).toBe(5);
    rep.irA(99);
    expect(rep.indice()).toBe(5);
    rep.irA(-3);
    expect(rep.indice()).toBe(0);
  });

  it('play avanza un paso por segundo a 1x y se detiene al final', () => {
    vi.useFakeTimers();
    cargarGuia();
    rep.reproducir();
    expect(rep.reproduciendo()).toBe(true);

    vi.advanceTimersByTime(MS_POR_PASO);
    expect(rep.indice()).toBe(1);
    vi.advanceTimersByTime(MS_POR_PASO * 10);
    expect(rep.indice()).toBe(5);
    expect(rep.reproduciendo()).toBe(false);
  });

  it('a 2x avanza el doble de rápido y a 0.5x la mitad', () => {
    vi.useFakeTimers();
    cargarGuia();
    rep.cambiarVelocidad(2);
    rep.reproducir();
    vi.advanceTimersByTime(MS_POR_PASO);
    expect(rep.indice()).toBe(2);

    rep.cambiarVelocidad(0.5);
    vi.advanceTimersByTime(MS_POR_PASO);
    expect(rep.indice()).toBe(2);
    vi.advanceTimersByTime(MS_POR_PASO);
    expect(rep.indice()).toBe(3);
  });

  it('pausa congela el paso y un paso manual también pausa', () => {
    vi.useFakeTimers();
    cargarGuia();
    rep.reproducir();
    vi.advanceTimersByTime(MS_POR_PASO);
    rep.pausar();
    vi.advanceTimersByTime(MS_POR_PASO * 5);
    expect(rep.indice()).toBe(1);

    rep.reproducir();
    rep.siguiente();
    expect(rep.reproduciendo()).toBe(false);
    expect(rep.indice()).toBe(2);
  });

  it('al reproducir desde el final vuelve a empezar', () => {
    cargarGuia();
    rep.irFin();
    rep.reproducir();
    expect(rep.indice()).toBe(0);
    expect(rep.reproduciendo()).toBe(true);
  });

  it('la ruta final solo se pinta en el último paso', () => {
    cargarGuia();
    expect(rep.claseTramo(3, 4)).toBeNull();
    rep.irA(4);
    expect(rep.claseTramo(3, 4)).toBe('evaluado');
    expect(rep.enCamino(1)).toBe(false);
    rep.irFin();
    expect(rep.claseTramo(4, 3)).toBe('camino');
    expect(rep.enCamino(1)).toBe(true);
    expect(rep.claseTramo(0, 2)).toBe('arbol');
  });

  it('traduce un 503 del gateway en un mensaje claro', () => {
    rep.consultar({ algoritmo: 'dijkstra', desde: 0, hasta: 4, redId: 1 }, nombreGuia)
      .subscribe({ error: () => undefined });
    http.expectOne(r => r.url.endsWith('/api/rutas/calcular')).flush(
      { mensaje: 'El microservicio ruta-service no esta disponible.' },
      { status: 503, statusText: 'Service Unavailable' }
    );
    expect(rep.error()).toContain('ruta-service');
    expect(rep.cargando()).toBe(false);
    expect(rep.traza()).toBeNull();
  });

  it('un 401 pide iniciar sesión', () => {
    rep.consultar({ algoritmo: 'dijkstra', desde: 0, hasta: 4, redId: 1 }, nombreGuia)
      .subscribe({ error: () => undefined });
    http.expectOne(() => true).flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(rep.error()).toContain('iniciar sesión');
  });
});
