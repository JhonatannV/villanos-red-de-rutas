import { AlgoritmoTraza, Traza } from './traza.model';

/**
 * Archivo de traza (RF-09 / SCRUM-34): la corrida completa en JSON, para
 * guardarla y volver a reproducirla después sin consultar al backend.
 */
export const FORMATO_TRAZA = 'villanos-red-de-rutas/traza';
export const VERSION_TRAZA = 1;

export interface ArchivoTraza {
  formato: typeof FORMATO_TRAZA;
  version: number;
  exportado: string;
  red: { redId: number; nombre: string | null; paradas: number };
  traza: Traza;
}

const ALGORITMOS: AlgoritmoTraza[] = ['dijkstra', 'anchura', 'profundidad'];

export function crearArchivoTraza(traza: Traza, red: { nombre?: string | null; paradas: number },
                                  ahora = new Date()): ArchivoTraza {
  return {
    formato: FORMATO_TRAZA,
    version: VERSION_TRAZA,
    exportado: ahora.toISOString(),
    red: { redId: traza.redId, nombre: red.nombre ?? null, paradas: red.paradas },
    traza
  };
}

export function nombreArchivoTraza(traza: Traza, ahora = new Date()): string {
  const fecha = ahora.toISOString().slice(0, 16).replace(/[-:]/g, '').replace('T', '-');
  const tramo = traza.hasta === null ? `${traza.desde}` : `${traza.desde}-${traza.hasta}`;
  return `traza-${traza.algoritmo}-${tramo}-${fecha}.json`;
}

function esEntero(v: unknown): v is number {
  return typeof v === 'number' && Number.isInteger(v);
}

/**
 * Lee y valida un archivo exportado. Lanza Error con un mensaje para el usuario
 * si el archivo no es una traza válida.
 */
export function leerArchivoTraza(texto: string): ArchivoTraza {
  let datos: unknown;
  try {
    datos = JSON.parse(texto);
  } catch {
    throw new Error('El archivo no es un JSON válido.');
  }
  const a = datos as Partial<ArchivoTraza>;
  if (!a || typeof a !== 'object' || a.formato !== FORMATO_TRAZA) {
    throw new Error('El archivo no es una traza exportada por Red de Rutas.');
  }
  if (a.version !== VERSION_TRAZA) {
    throw new Error(`Versión de traza no soportada: ${String(a.version)} (se espera ${VERSION_TRAZA}).`);
  }
  const t = a.traza as Partial<Traza> | undefined;
  if (!t || !ALGORITMOS.includes(t.algoritmo as AlgoritmoTraza) || !esEntero(t.desde) || !esEntero(t.redId)) {
    throw new Error('La traza no indica un algoritmo, una parada de inicio y una red válidos.');
  }
  if (!Array.isArray(t.pasos) || t.pasos.length === 0) {
    throw new Error('La traza no tiene pasos.');
  }
  t.pasos.forEach((p, i) => {
    if (!p || typeof p !== 'object' || !Array.isArray(p.filas) || typeof p.estados !== 'object'
        || !Array.isArray(p.arbol) || !Array.isArray(p.evaluadas) || typeof p.titulo !== 'string') {
      throw new Error(`El paso ${i} de la traza está incompleto.`);
    }
  });
  if (!Array.isArray(t.camino)) {
    throw new Error('La traza no tiene el campo camino.');
  }
  return a as ArchivoTraza;
}

/** Mayor número de parada que usa la traza (para comprobar que cabe en la red cargada). */
export function mayorParada(traza: Traza): number {
  let mayor = Math.max(traza.desde, traza.hasta ?? -1, ...traza.camino);
  for (const p of traza.pasos) {
    for (const f of p.filas) {
      mayor = Math.max(mayor, f.parada);
    }
  }
  return mayor;
}
