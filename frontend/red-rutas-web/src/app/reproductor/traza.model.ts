/**
 * Modelo del reproductor paso a paso (RF-04 / SCRUM-31).
 *
 * El backend entrega los pasos del algoritmo y aquí se convierten en una lista de
 * "fotos" del estado completo. Avanzar o retroceder solo mueve un índice sobre esa
 * lista, así que volver un paso deja todo exactamente como estaba, sin recalcular.
 */

export type AlgoritmoTraza = 'dijkstra' | 'anchura' | 'profundidad';

/** Cómo se pinta una parada en un paso dado. */
export type EstadoParada = 'actual' | 'cerrada' | 'frontera';

export type Velocidad = 0.5 | 1 | 2;

/** Qué le pasó a una parada vecina cuando se revisó en este paso. */
export interface CambioFila {
  anterior: number | null;
  propuesta: number;
  mejora: boolean;
}

/** Una fila de la tabla: el valor tentativo de una parada en ese instante. */
export interface FilaTraza {
  parada: number;
  nombre: string;
  /** Distancia (Dijkstra), nivel (BFS) o profundidad (DFS). */
  valor: number;
  desde: number | null;
  desdeNombre: string | null;
  estado: EstadoParada;
  cambio: CambioFila | null;
}

/** Foto del estado completo del algoritmo en un paso. */
export interface EstadoPaso {
  numero: number;
  actual: number | null;
  titulo: string;
  detalle: string[];
  estados: Record<number, EstadoParada>;
  filas: FilaTraza[];
  /** Tramos del árbol de caminos (de dónde se llegó a cada parada). */
  arbol: [number, number][];
  /** Tramos que se revisaron en este paso (desde la parada actual). */
  evaluadas: [number, number][];
  /** Cola (BFS / Dijkstra) o pila (DFS) después del paso; null si no aplica. */
  pendientes: number[] | null;
}

export interface Traza {
  algoritmo: AlgoritmoTraza;
  redId: number;
  desde: number;
  hasta: number | null;
  /** Costo de la ruta (solo Dijkstra); -1 si el destino no se alcanza. */
  costoTotal: number | null;
  camino: number[];
  tramoCerrado: number | null;
  /** NATIVE o JAVA: qué implementación calculó el camino. */
  modoEfectivo: string | null;
  columnaValor: string;
  nombrePendientes: string | null;
  mensaje: string | null;
  pasos: EstadoPaso[];
}

/* ---------- Respuestas del backend (ruta-service vía api-gateway) ---------- */

export interface RelajacionApi {
  vecino: number;
  peso: number;
  anterior: number | null;
  propuesta: number;
  mejora: boolean;
}

export interface PasoDijkstraApi {
  paso: number;
  actual: number;
  distancia: number;
  desde: number;
  relajaciones: RelajacionApi[];
}

/** GET /api/rutas/calcular?desde=&hasta=&traza=true */
export interface RespuestaRuta {
  redId: number;
  desde: number;
  hasta: number;
  costoTotal: number;
  camino: number[];
  modoEfectivo: string;
  tramoCerrado: number | null;
  trazaCalculadaCon?: string;
  pasos?: PasoDijkstraApi[];
  mensaje?: string;
}

export interface PasoRecorridoApi {
  visita: number;
  parada: number;
  nombre: string;
  desde: number;
  nivel?: number;
  profundidad?: number;
  descubiertas: number[];
  cola?: number[];
  pila?: number[];
}

/** GET /api/rutas/recorrido?desde=&modo=anchura|profundidad */
export interface RespuestaRecorrido {
  redId: number;
  desde: number;
  modo: 'anchura' | 'profundidad';
  tramosCerrados: number[];
  visitadas: number;
  orden: number[];
  pasos: PasoRecorridoApi[];
  mensaje?: string;
}
