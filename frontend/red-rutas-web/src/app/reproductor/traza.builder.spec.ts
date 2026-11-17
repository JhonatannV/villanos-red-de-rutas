import { claveTramo, construirTrazaDijkstra, construirTrazaRecorrido } from './traza.builder';
import { BFS_PEQUENO, DFS_PEQUENO, RUTA_GUIA, nombreGuia } from './traza.fixtures';
import { RespuestaRuta } from './traza.model';

describe('construirTrazaDijkstra (RF-04 · SCRUM-31)', () => {
  const traza = construirTrazaDijkstra(RUTA_GUIA, nombreGuia);
  const valores = (i: number) => Object.fromEntries(traza.pasos[i].filas.map(f => [f.nombre, f.valor]));

  it('agrega un paso 0 de estado inicial antes de los 5 pasos del backend', () => {
    expect(traza.pasos.length).toBe(6);
    expect(traza.pasos[0].actual).toBeNull();
    expect(traza.pasos[0].estados).toEqual({ 0: 'frontera' });
    expect(valores(0)).toEqual({ A: 0 });
  });

  it('en cada paso muestra el nodo actual y la distancia tentativa de sus vecinos', () => {
    const p2 = traza.pasos[2]; // se cierra C
    expect(p2.actual).toBe(2);
    expect(p2.estados[0]).toBe('cerrada');
    expect(p2.estados[2]).toBe('actual');
    expect(p2.estados[1]).toBe('frontera');
    expect(valores(2)).toEqual({ A: 0, C: 2, B: 4, D: 10, E: 17 });

    const filaB = p2.filas.find(f => f.parada === 1)!;
    expect(filaB.cambio).toEqual({ anterior: 4, propuesta: 5, mejora: false });
    const filaD = p2.filas.find(f => f.parada === 3)!;
    expect(filaD.cambio).toEqual({ anterior: null, propuesta: 10, mejora: true });
    expect(filaD.desdeNombre).toBe('C');
    expect(p2.evaluadas).toEqual([[2, 1], [2, 3], [2, 4]]);
  });

  it('cada paso es una foto independiente: los pasos siguientes no alteran los anteriores', () => {
    // Si el paso 1 compartiera estado con los siguientes, B o D cambiarían aquí
    expect(valores(1)).toEqual({ A: 0, C: 2, B: 4 });
    expect(traza.pasos[1].estados).toEqual({ 0: 'actual', 1: 'frontera', 2: 'frontera' });
    expect(valores(3)).toEqual({ A: 0, C: 2, B: 4, D: 9, E: 17 });
    expect(valores(4)).toEqual({ A: 0, C: 2, B: 4, D: 9, E: 11 });
  });

  it('las filas van ordenadas por distancia', () => {
    const dist = traza.pasos[4].filas.map(f => f.valor);
    expect(dist).toEqual([...dist].sort((a, b) => a - b));
  });

  it('el último paso cierra el destino y deja el árbol de caminos completo', () => {
    const ultimo = traza.pasos[5];
    expect(ultimo.actual).toBe(4);
    expect(ultimo.detalle.at(-1)).toContain('destino');
    const arbol = new Set(ultimo.arbol.map(([a, b]) => claveTramo(a, b)));
    for (const k of ['0-1', '0-2', '1-3', '3-4']) {
      expect(arbol.has(k)).toBe(true);
    }
    expect(traza.camino).toEqual([0, 1, 3, 4]);
    expect(traza.costoTotal).toBe(11);
    expect(traza.mensaje).toBeNull();
  });

  it('si el destino no se alcanza conserva lo explorado y explica por qué', () => {
    const sinRuta: RespuestaRuta = {
      ...RUTA_GUIA,
      costoTotal: -1,
      camino: [],
      mensaje: 'No existe camino alcanzable entre las paradas indicadas.',
      pasos: RUTA_GUIA.pasos!.slice(0, 2)
    };
    const t = construirTrazaDijkstra(sinRuta, nombreGuia);
    expect(t.pasos.length).toBe(3);
    expect(t.mensaje).toContain('No existe camino');
  });
});

describe('construirTrazaRecorrido (BFS y DFS con el mismo reproductor)', () => {
  it('BFS: el valor es el nivel y lo pendiente es la cola', () => {
    const t = construirTrazaRecorrido(BFS_PEQUENO, nombreGuia);
    expect(t.columnaValor).toBe('Nivel');
    expect(t.pasos.length).toBe(5);
    const p2 = t.pasos[2]; // visita B
    expect(p2.actual).toBe(1);
    expect(p2.estados).toEqual({ 0: 'cerrada', 1: 'actual', 2: 'frontera', 3: 'frontera' });
    expect(p2.pendientes).toEqual([2, 3]);
    expect(p2.filas.find(f => f.parada === 3)!.valor).toBe(2);
  });

  it('DFS: el valor es la profundidad y lo pendiente es la pila', () => {
    const t = construirTrazaRecorrido(DFS_PEQUENO, nombreGuia);
    expect(t.columnaValor).toBe('Profundidad');
    expect(t.nombrePendientes).toContain('Pila');
    expect(t.pasos[3].actual).toBe(3);
    expect(t.pasos[3].detalle.join(' ')).toContain('tope de la pila');
    expect(t.pasos.at(-1)!.estados).toEqual({ 0: 'cerrada', 1: 'cerrada', 3: 'cerrada', 2: 'actual' });
  });

  it('BFS y DFS dan órdenes distintos sobre la misma red', () => {
    const bfs = construirTrazaRecorrido(BFS_PEQUENO, nombreGuia).pasos.slice(1).map(p => p.actual);
    const dfs = construirTrazaRecorrido(DFS_PEQUENO, nombreGuia).pasos.slice(1).map(p => p.actual);
    expect(bfs).toEqual([0, 1, 2, 3]);
    expect(dfs).toEqual([0, 1, 3, 2]);
  });
});
