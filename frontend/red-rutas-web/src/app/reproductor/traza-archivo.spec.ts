import { crearArchivoTraza, FORMATO_TRAZA, leerArchivoTraza, mayorParada, nombreArchivoTraza } from './traza-archivo';
import { construirTrazaDijkstra, construirTrazaRecorrido } from './traza.builder';
import { BFS_PEQUENO, RUTA_GUIA, nombreGuia } from './traza.fixtures';

describe('Archivo de traza JSON (RF-09 · SCRUM-34)', () => {
  const traza = construirTrazaDijkstra(RUTA_GUIA, nombreGuia);
  const fecha = new Date('2026-11-17T15:04:00Z');

  it('exportar y volver a leer da exactamente la misma traza', () => {
    const texto = JSON.stringify(crearArchivoTraza(traza, { nombre: 'Guía', paradas: 5 }, fecha));
    const leido = leerArchivoTraza(texto);
    expect(leido.formato).toBe(FORMATO_TRAZA);
    expect(leido.red).toEqual({ redId: 1, nombre: 'Guía', paradas: 5 });
    expect(leido.exportado).toBe('2026-11-17T15:04:00.000Z');
    expect(leido.traza).toEqual(traza);
  });

  it('también sirve para BFS y DFS', () => {
    const bfs = construirTrazaRecorrido(BFS_PEQUENO, nombreGuia);
    const leido = leerArchivoTraza(JSON.stringify(crearArchivoTraza(bfs, { paradas: 4 })));
    expect(leido.traza).toEqual(bfs);
    expect(nombreArchivoTraza(bfs, fecha)).toBe('traza-anchura-0-20261117-1504.json');
  });

  it('el nombre del archivo dice algoritmo, origen, destino y fecha', () => {
    expect(nombreArchivoTraza(traza, fecha)).toBe('traza-dijkstra-0-4-20261117-1504.json');
  });

  it('rechaza archivos que no son una traza válida', () => {
    const valido = crearArchivoTraza(traza, { paradas: 5 });
    expect(() => leerArchivoTraza('no es json')).toThrow('JSON válido');
    expect(() => leerArchivoTraza('{"hola":1}')).toThrow('no es una traza');
    expect(() => leerArchivoTraza(JSON.stringify({ ...valido, version: 9 }))).toThrow('Versión');
    expect(() => leerArchivoTraza(JSON.stringify({ ...valido, traza: { ...traza, pasos: [] } }))).toThrow('no tiene pasos');
    expect(() => leerArchivoTraza(JSON.stringify({ ...valido, traza: { ...traza, algoritmo: 'astar' } }))).toThrow('algoritmo');
    const pasoRoto = { ...valido, traza: { ...traza, pasos: [traza.pasos[0], { numero: 1 }] } };
    expect(() => leerArchivoTraza(JSON.stringify(pasoRoto))).toThrow('paso 1');
  });

  it('calcula la mayor parada usada para comprobar que cabe en la red', () => {
    expect(mayorParada(traza)).toBe(4);
  });
});
