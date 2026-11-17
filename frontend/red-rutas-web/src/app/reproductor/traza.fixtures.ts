import { Tramo } from '../core/models/red.model';
import { RespuestaRecorrido, RespuestaRuta } from './traza.model';

/** Nombres del grafo guía de 5 paradas (A=0 ... E=4). */
export const NOMBRES_GUIA = ['A', 'B', 'C', 'D', 'E'];
export const nombreGuia = (id: number): string => NOMBRES_GUIA[id] ?? `P${id}`;

/**
 * Respuesta real de /api/rutas/calcular?desde=0&hasta=4&traza=true sobre el grafo
 * guía (la misma que verifica TrazaDijkstraTest en ruta-service).
 */
export const RUTA_GUIA: RespuestaRuta = {
  redId: 1,
  desde: 0,
  hasta: 4,
  costoTotal: 11,
  camino: [0, 1, 3, 4],
  modoEfectivo: 'NATIVE',
  tramoCerrado: null,
  trazaCalculadaCon: 'JAVA',
  pasos: [
    { paso: 1, actual: 0, distancia: 0, desde: -1, relajaciones: [
      { vecino: 1, peso: 4, anterior: null, propuesta: 4, mejora: true },
      { vecino: 2, peso: 2, anterior: null, propuesta: 2, mejora: true }
    ] },
    { paso: 2, actual: 2, distancia: 2, desde: 0, relajaciones: [
      { vecino: 1, peso: 3, anterior: 4, propuesta: 5, mejora: false },
      { vecino: 3, peso: 8, anterior: null, propuesta: 10, mejora: true },
      { vecino: 4, peso: 15, anterior: null, propuesta: 17, mejora: true }
    ] },
    { paso: 3, actual: 1, distancia: 4, desde: 0, relajaciones: [
      { vecino: 3, peso: 5, anterior: 10, propuesta: 9, mejora: true }
    ] },
    { paso: 4, actual: 3, distancia: 9, desde: 1, relajaciones: [
      { vecino: 4, peso: 2, anterior: 17, propuesta: 11, mejora: true }
    ] },
    { paso: 5, actual: 4, distancia: 11, desde: 3, relajaciones: [] }
  ]
};

/** Red 0-1, 0-2, 1-3: BFS desde 0. */
export const BFS_PEQUENO: RespuestaRecorrido = {
  redId: 1,
  desde: 0,
  modo: 'anchura',
  tramosCerrados: [],
  visitadas: 4,
  orden: [0, 1, 2, 3],
  pasos: [
    { visita: 1, parada: 0, nombre: 'A', desde: -1, nivel: 0, descubiertas: [1, 2], cola: [1, 2] },
    { visita: 2, parada: 1, nombre: 'B', desde: 0, nivel: 1, descubiertas: [3], cola: [2, 3] },
    { visita: 3, parada: 2, nombre: 'C', desde: 0, nivel: 1, descubiertas: [], cola: [3] },
    { visita: 4, parada: 3, nombre: 'D', desde: 1, nivel: 2, descubiertas: [], cola: [] }
  ]
};

/** Misma red, DFS desde 0 (vecinos en orden ascendente, tope de la pila al final). */
export const DFS_PEQUENO: RespuestaRecorrido = {
  redId: 1,
  desde: 0,
  modo: 'profundidad',
  tramosCerrados: [],
  visitadas: 4,
  orden: [0, 1, 3, 2],
  pasos: [
    { visita: 1, parada: 0, nombre: 'A', desde: -1, profundidad: 0, descubiertas: [1, 2], pila: [2, 1] },
    { visita: 2, parada: 1, nombre: 'B', desde: 0, profundidad: 1, descubiertas: [3], pila: [2, 3] },
    { visita: 3, parada: 3, nombre: 'D', desde: 1, profundidad: 2, descubiertas: [], pila: [2] },
    { visita: 4, parada: 2, nombre: 'C', desde: 0, profundidad: 1, descubiertas: [], pila: [] }
  ]
};

/** Tramos del grafo guía con sus ids de grafo-service (1..7). */
export const TRAMOS_GUIA: Tramo[] = [
  { id: 1, origen: 0, destino: 1, peso: 4, abierto: true },
  { id: 2, origen: 0, destino: 2, peso: 2, abierto: true },
  { id: 3, origen: 1, destino: 2, peso: 3, abierto: true },
  { id: 4, origen: 1, destino: 3, peso: 5, abierto: true },
  { id: 5, origen: 2, destino: 3, peso: 8, abierto: true },
  { id: 6, origen: 2, destino: 4, peso: 15, abierto: true },
  { id: 7, origen: 3, destino: 4, peso: 2, abierto: true }
];

/** /calcular?desde=0&hasta=4&sin=4 (se cierra B-D): A -> C -> D -> E con costo 12. */
export const RUTA_GUIA_SIN_BD: RespuestaRuta = {
  redId: 1, desde: 0, hasta: 4, costoTotal: 12, camino: [0, 2, 3, 4], modoEfectivo: 'NATIVE', tramoCerrado: 4
};
