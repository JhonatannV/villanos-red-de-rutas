import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, finalize, map, tap, throwError, timeout } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Tramo } from '../../core/models/red.model';
import { claveTramo } from '../traza.builder';
import { RespuestaRuta } from '../traza.model';
import { MS_LIMITE_CONSULTA } from '../reproductor.service';

export interface RutaComparada {
  desde: number;
  hasta: number;
  /** -1 si no hay camino. */
  costo: number;
  camino: number[];
}

export interface Comparacion {
  original: RutaComparada;
  alterna: RutaComparada;
  tramoCerrado: Tramo;
  /** alterna - original; null si con el cierre ya no hay camino. */
  delta: number | null;
  /** Paradas de la ruta original que la alterna ya no usa. */
  paradasQueSeDejan: number[];
  /** Paradas nuevas que aparecen en la ruta alterna. */
  paradasNuevas: number[];
}

export type ClaseComparacion = 'cerrado' | 'ambas' | 'original' | 'alterna';

/** Texto del cambio de costo, p. ej. "Aumentó en +9 minutos". */
export function textoDelta(delta: number | null): string {
  if (delta === null) {
    return 'Con ese tramo cerrado no queda ningún camino';
  }
  if (delta === 0) {
    return 'El costo no cambia (hay un camino alterno igual de corto)';
  }
  const min = Math.abs(delta) === 1 ? 'minuto' : 'minutos';
  return delta > 0 ? `Aumentó en +${delta} ${min}` : `Disminuyó en ${delta} ${min}`;
}

/** Tramos de un camino como claves sin dirección "a-b". */
function tramosDe(camino: number[]): Set<string> {
  const s = new Set<string>();
  for (let i = 1; i < camino.length; i++) {
    s.add(claveTramo(camino[i - 1], camino[i]));
  }
  return s;
}

export function compararRutas(original: RutaComparada, alterna: RutaComparada, tramoCerrado: Tramo): Comparacion {
  const enAlterna = new Set(alterna.camino);
  const enOriginal = new Set(original.camino);
  return {
    original,
    alterna,
    tramoCerrado,
    delta: alterna.costo < 0 ? null : alterna.costo - original.costo,
    paradasQueSeDejan: original.camino.filter(p => !enAlterna.has(p)),
    paradasNuevas: alterna.camino.filter(p => !enOriginal.has(p))
  };
}

/**
 * Comparador de 2 rutas (RF-08 / SCRUM-33): la ruta original contra la que
 * resulta al cerrar un tramo, superpuestas en el mapa con dos colores.
 */
@Injectable({ providedIn: 'root' })
export class ComparadorService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiGatewayUrl}/api/rutas`;

  readonly comparacion = signal<Comparacion | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  readonly activo = computed(() => this.comparacion() !== null);

  private readonly tramosOriginal = computed(() => tramosDe(this.comparacion()?.original.camino ?? []));
  private readonly tramosAlterna = computed(() => tramosDe(this.comparacion()?.alterna.camino ?? []));

  /** Pide la ruta con el tramo cerrado (`sin=`) y la compara con la original. */
  comparar(original: RutaComparada, tramo: Tramo, redId: number): Observable<Comparacion> {
    this.cargando.set(true);
    this.error.set(null);
    const params = new HttpParams()
      .set('desde', original.desde)
      .set('hasta', original.hasta)
      .set('redId', redId)
      .set('sin', tramo.id);
    return this.http.get<RespuestaRuta>(`${this.baseUrl}/calcular`, { params }).pipe(
      timeout(MS_LIMITE_CONSULTA),
      map(r => compararRutas(original, {
        desde: r.desde, hasta: r.hasta, costo: r.costoTotal, camino: r.camino ?? []
      }, tramo)),
      tap(c => this.comparacion.set(c)),
      catchError((err: HttpErrorResponse) => {
        this.error.set(err?.status === 503 || err?.status === 0 || !(err instanceof HttpErrorResponse)
          ? 'El cálculo de rutas no está disponible; no se pudo comparar.'
          : err.error?.mensaje ?? err.error?.error ?? `Error ${err.status} al comparar rutas.`);
        return throwError(() => err);
      }),
      finalize(() => this.cargando.set(false))
    );
  }

  limpiar(): void {
    this.comparacion.set(null);
    this.error.set(null);
  }

  /** Cómo pintar un tramo del mapa mientras la comparación está activa. */
  claseTramo(tramo: Tramo): ClaseComparacion | null {
    const c = this.comparacion();
    if (!c) {
      return null;
    }
    if (tramo.id === c.tramoCerrado.id) {
      return 'cerrado';
    }
    const k = claveTramo(tramo.origen, tramo.destino);
    const o = this.tramosOriginal().has(k);
    const a = this.tramosAlterna().has(k);
    if (o && a) {
      return 'ambas';
    }
    return o ? 'original' : a ? 'alterna' : null;
  }
}
