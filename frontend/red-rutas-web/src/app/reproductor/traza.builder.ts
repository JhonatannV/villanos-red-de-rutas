import {
  CambioFila,
  EstadoParada,
  EstadoPaso,
  FilaTraza,
  RespuestaRecorrido,
  RespuestaRuta,
  Traza
} from './traza.model';

/** Devuelve el nombre visible de una parada a partir de su número. */
export type NombreParada = (id: number) => string;

const INFINITO = '∞';

function arbolDesde(previo: Map<number, number>): [number, number][] {
  const aristas: [number, number][] = [];
  previo.forEach((p, v) => {
    if (p >= 0) {
      aristas.push([p, v]);
    }
  });
  return aristas;
}

function ordenarFilas(filas: FilaTraza[]): FilaTraza[] {
  return filas.sort((a, b) => a.valor - b.valor || a.parada - b.parada);
}

function filasDesde(valores: Map<number, number>,
                    previo: Map<number, number>,
                    estados: Record<number, EstadoParada>,
                    cambios: Map<number, CambioFila>,
                    nombre: NombreParada): FilaTraza[] {
  const filas: FilaTraza[] = [];
  valores.forEach((valor, parada) => {
    const p = previo.get(parada);
    const desde = p !== undefined && p >= 0 ? p : null;
    filas.push({
      parada,
      nombre: nombre(parada),
      valor,
      desde,
      desdeNombre: desde === null ? null : nombre(desde),
      estado: estados[parada] ?? 'frontera',
      cambio: cambios.get(parada) ?? null
    });
  });
  return ordenarFilas(filas);
}

/**
 * Dijkstra: cada paso del backend cierra una parada y revisa sus vecinos.
 * Se agrega un paso 0 con el estado inicial (solo el origen, con distancia 0).
 */
export function construirTrazaDijkstra(resp: RespuestaRuta, nombre: NombreParada): Traza {
  const distancia = new Map<number, number>();
  const previo = new Map<number, number>();
  const cerradas = new Set<number>();
  const pasos: EstadoPaso[] = [];

  distancia.set(resp.desde, 0);
  previo.set(resp.desde, -1);

  pasos.push({
    numero: 0,
    actual: null,
    titulo: `Inicio: ${nombre(resp.desde)} arranca con distancia 0`,
    detalle: ['Todas las demás paradas empiezan con distancia infinita (∞) y la cola de prioridad solo tiene el origen.'],
    estados: { [resp.desde]: 'frontera' },
    filas: filasDesde(distancia, previo, { [resp.desde]: 'frontera' }, new Map(), nombre),
    arbol: [],
    evaluadas: [],
    pendientes: [resp.desde]
  });

  for (const paso of resp.pasos ?? []) {
    const u = paso.actual;
    distancia.set(u, paso.distancia);
    previo.set(u, paso.desde);

    const cambios = new Map<number, CambioFila>();
    const detalle: string[] = [];
    for (const r of paso.relajaciones) {
      cambios.set(r.vecino, { anterior: r.anterior, propuesta: r.propuesta, mejora: r.mejora });
      const antes = r.anterior === null ? INFINITO : String(r.anterior);
      if (r.mejora) {
        distancia.set(r.vecino, r.propuesta);
        previo.set(r.vecino, u);
        detalle.push(`${nombre(r.vecino)}: ${antes} → ${r.propuesta} (${paso.distancia} + ${r.peso}). Mejora.`);
      } else {
        detalle.push(`${nombre(r.vecino)}: por aquí costaría ${r.propuesta}, pero ya tiene ${antes}. No cambia.`);
      }
    }

    const estados: Record<number, EstadoParada> = {};
    distancia.forEach((_, p) => {
      estados[p] = cerradas.has(p) ? 'cerrada' : 'frontera';
    });
    estados[u] = 'actual';

    if (u === resp.hasta) {
      detalle.push('Es el destino: su distancia ya es definitiva y el algoritmo termina.');
    } else if (paso.relajaciones.length === 0) {
      detalle.push('No tiene vecinos abiertos sin cerrar.');
    }

    const pendientes = [...distancia.keys()]
      .filter(p => !cerradas.has(p) && p !== u)
      .sort((a, b) => distancia.get(a)! - distancia.get(b)! || a - b);

    const llegada = paso.desde >= 0 ? ` (llegó desde ${nombre(paso.desde)})` : '';
    pasos.push({
      numero: paso.paso,
      actual: u,
      titulo: `Se cierra ${nombre(u)} con distancia ${paso.distancia}${llegada}`,
      detalle,
      estados,
      filas: filasDesde(distancia, previo, estados, cambios, nombre),
      arbol: arbolDesde(previo),
      evaluadas: paso.relajaciones.map(r => [u, r.vecino] as [number, number]),
      pendientes
    });

    cerradas.add(u);
  }

  const sinCamino = resp.costoTotal < 0;
  return {
    algoritmo: 'dijkstra',
    redId: resp.redId,
    desde: resp.desde,
    hasta: resp.hasta,
    costoTotal: resp.costoTotal,
    camino: resp.camino ?? [],
    tramoCerrado: resp.tramoCerrado ?? null,
    modoEfectivo: resp.modoEfectivo ?? null,
    columnaValor: 'Distancia',
    nombrePendientes: 'Cola de prioridad',
    mensaje: sinCamino ? (resp.mensaje ?? 'No existe camino entre las paradas indicadas.') : null,
    pasos
  };
}

/**
 * BFS y DFS (RF-05) con el mismo reproductor: el valor de la tabla es el nivel
 * (BFS) o la profundidad (DFS), y lo pendiente es la cola o la pila.
 */
export function construirTrazaRecorrido(resp: RespuestaRecorrido, nombre: NombreParada): Traza {
  const anchura = resp.modo === 'anchura';
  const etiqueta = anchura ? 'nivel' : 'profundidad';
  const estructura = anchura ? 'cola' : 'pila';

  const valor = new Map<number, number>();
  const previo = new Map<number, number>();
  const visitadas = new Set<number>();
  const pasos: EstadoPaso[] = [];

  valor.set(resp.desde, 0);
  previo.set(resp.desde, -1);
  pasos.push({
    numero: 0,
    actual: null,
    titulo: `Inicio: ${nombre(resp.desde)} entra a la ${estructura}`,
    detalle: [anchura
      ? 'BFS visita primero todas las paradas a un tramo, luego las que están a dos, y así sucesivamente.'
      : 'DFS sigue un camino lo más lejos posible y retrocede cuando no quedan vecinos sin visitar.'],
    estados: { [resp.desde]: 'frontera' },
    filas: filasDesde(valor, previo, { [resp.desde]: 'frontera' }, new Map(), nombre),
    arbol: [],
    evaluadas: [],
    pendientes: [resp.desde]
  });

  for (const p of resp.pasos) {
    const u = p.parada;
    const nivel = (anchura ? p.nivel : p.profundidad) ?? 0;
    valor.set(u, nivel);
    previo.set(u, p.desde);

    const cambios = new Map<number, CambioFila>();
    for (const v of p.descubiertas) {
      const anterior = valor.has(v) ? valor.get(v)! : null;
      valor.set(v, nivel + 1);
      previo.set(v, u);
      cambios.set(v, { anterior, propuesta: nivel + 1, mejora: true });
    }

    const pendientes = [...new Set(p.cola ?? p.pila ?? [])];
    const estados: Record<number, EstadoParada> = {};
    for (const q of pendientes) {
      estados[q] = 'frontera';
    }
    visitadas.forEach(q => (estados[q] = 'cerrada'));
    estados[u] = 'actual';

    // En la tabla: lo visitado, la parada actual y lo que espera en la cola o pila
    const visibles = new Map<number, number>();
    valor.forEach((val, q) => {
      if (estados[q] !== undefined) {
        visibles.set(q, val);
      }
    });

    const detalle: string[] = [];
    detalle.push(p.descubiertas.length > 0
      ? `${anchura ? 'Descubre' : 'Apila'}: ${p.descubiertas.map(nombre).join(', ')}.`
      : 'No tiene vecinos nuevos sin visitar.');
    if (!anchura && pendientes.length > 0) {
      detalle.push(`La siguiente en salir es ${nombre(pendientes[pendientes.length - 1])} (tope de la pila).`);
    }

    const llegada = p.desde >= 0 ? ` desde ${nombre(p.desde)}` : '';
    pasos.push({
      numero: p.visita,
      actual: u,
      titulo: `Visita ${p.visita}: ${nombre(u)}${llegada} (${etiqueta} ${nivel})`,
      detalle,
      estados,
      filas: filasDesde(visibles, previo, estados, cambios, nombre),
      arbol: arbolDesde(new Map([...previo].filter(([q]) => visitadas.has(q) || q === u))),
      evaluadas: p.descubiertas.map(v => [u, v] as [number, number]),
      pendientes
    });

    visitadas.add(u);
  }

  return {
    algoritmo: resp.modo,
    redId: resp.redId,
    desde: resp.desde,
    hasta: null,
    costoTotal: null,
    camino: [],
    tramoCerrado: resp.tramosCerrados?.length ? resp.tramosCerrados[0] : null,
    modoEfectivo: null,
    columnaValor: anchura ? 'Nivel' : 'Profundidad',
    nombrePendientes: anchura ? 'Cola' : 'Pila (tope al final)',
    mensaje: resp.mensaje ?? null,
    pasos
  };
}

/** Clave sin dirección para buscar un tramo por sus dos extremos. */
export function claveTramo(a: number, b: number): string {
  return a < b ? `${a}-${b}` : `${b}-${a}`;
}
