import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComparadorService, compararRutas, textoDelta } from './comparador.service';
import { RUTA_GUIA_SIN_BD, TRAMOS_GUIA } from '../traza.fixtures';

describe('Comparador de 2 rutas (RF-08 · SCRUM-33)', () => {
  const original = { desde: 0, hasta: 4, costo: 11, camino: [0, 1, 3, 4] };
  const bd = TRAMOS_GUIA[3];

  it('textoDelta dice cuánto aumentó o disminuyó', () => {
    expect(textoDelta(9)).toBe('Aumentó en +9 minutos');
    expect(textoDelta(1)).toBe('Aumentó en +1 minuto');
    expect(textoDelta(-2)).toBe('Disminuyó en -2 minutos');
    expect(textoDelta(0)).toContain('no cambia');
    expect(textoDelta(null)).toContain('no queda ningún camino');
  });

  it('compararRutas calcula el delta y qué paradas cambian', () => {
    const c = compararRutas(original, { desde: 0, hasta: 4, costo: 12, camino: [0, 2, 3, 4] }, bd);
    expect(c.delta).toBe(1);
    expect(c.paradasQueSeDejan).toEqual([1]);
    expect(c.paradasNuevas).toEqual([2]);
  });

  it('sin camino alterno el delta es null', () => {
    const c = compararRutas(original, { desde: 0, hasta: 4, costo: -1, camino: [] }, bd);
    expect(c.delta).toBeNull();
  });

  describe('servicio', () => {
    let comp: ComparadorService;
    let http: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
      comp = TestBed.inject(ComparadorService);
      http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('pide la ruta con sin=<tramo> y pinta cada tramo según la ruta a la que pertenece', () => {
      comp.comparar(original, bd, 1).subscribe();
      const req = http.expectOne(r => r.url.endsWith('/api/rutas/calcular'));
      expect(req.request.params.get('sin')).toBe('4');
      expect(req.request.params.get('desde')).toBe('0');
      expect(req.request.params.get('hasta')).toBe('4');
      req.flush(RUTA_GUIA_SIN_BD);

      expect(comp.activo()).toBe(true);
      expect(comp.comparacion()?.delta).toBe(1);
      const clase = (id: number) => comp.claseTramo(TRAMOS_GUIA[id - 1]);
      expect(clase(4)).toBe('cerrado');   // B-D, el que se cerró
      expect(clase(1)).toBe('original');  // A-B solo en la original
      expect(clase(2)).toBe('alterna');   // A-C solo en la alterna
      expect(clase(5)).toBe('alterna');   // C-D
      expect(clase(7)).toBe('ambas');     // D-E en las dos
      expect(clase(6)).toBeNull();        // C-E en ninguna

      comp.limpiar();
      expect(comp.claseTramo(TRAMOS_GUIA[0])).toBeNull();
    });

    it('si ruta-service no responde informa que no se pudo comparar', () => {
      comp.comparar(original, bd, 1).subscribe({ error: () => undefined });
      http.expectOne(() => true).flush({}, { status: 503, statusText: 'Service Unavailable' });
      expect(comp.error()).toContain('no se pudo comparar');
      expect(comp.activo()).toBe(false);
      expect(comp.cargando()).toBe(false);
    });
  });
});
