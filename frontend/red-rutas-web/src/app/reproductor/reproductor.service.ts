import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, finalize, map, tap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { claveTramo, construirTrazaDijkstra, construirTrazaRecorrido, NombreParada } from './traza.builder';
import {
  AlgoritmoTraza,
  EstadoParada,
  RespuestaRecorrido,
  RespuestaRuta,
  Traza,
  Velocidad
} from './traza.model';

export interface ConsultaTraza {
  algoritmo: AlgoritmoTraza;
  desde: number;
  /** Solo para Dijkstra. */
  hasta: number | null;
  redId: number;
}

/** Tiempo entre pasos a velocidad 1x. */
export const MS_POR_PASO = 1000;

/**
 * Estado compartido del reproductor (RF-04). El componente del reproductor lo
 * controla y el visor del grafo lo lee para pintar cada paso sobre el mapa.
 */
@Injectable({ providedIn: 'root' })
export class ReproductorService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiGatewayUrl}/api/rutas`;
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  readonly traza = signal<Traza | null>(null);
  readonly indice = signal(0);
  readonly reproduciendo = signal(false);
  readonly velocidad = signal<Velocidad>(1);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  readonly totalPasos = computed(() => this.traza()?.pasos.length ?? 0);
  readonly pasoActual = computed(() => this.traza()?.pasos[this.indice()] ?? null);
  readonly enInicio = computed(() => this.indice() === 0);
  readonly enFin = computed(() => this.totalPasos() === 0 || this.indice() >= this.totalPasos() - 1);

  /** La ruta final solo se dibuja al llegar al último paso. */
  readonly caminoVisible = computed(() => (this.enFin() ? this.traza()?.camino ?? [] : []));

  private readonly valores = computed(() => {
    const m = new Map<number, number>();
    for (const f of this.pasoActual()?.filas ?? []) {
      m.set(f.parada, f.valor);
    }
    return m;
  });

  private readonly tramosArbol = computed(() =>
    new Set((this.pasoActual()?.arbol ?? []).map(([a, b]) => claveTramo(a, b))));

  private readonly tramosEvaluados = computed(() =>
    new Set((this.pasoActual()?.evaluadas ?? []).map(([a, b]) => claveTramo(a, b))));

  private readonly tramosCamino = computed(() => {
    const c = this.caminoVisible();
    const s = new Set<string>();
    for (let i = 1; i < c.length; i++) {
      s.add(claveTramo(c[i - 1], c[i]));
    }
    return s;
  });

  private readonly paradasCamino = computed(() => new Set(this.caminoVisible()));

  /* ---------- Consulta al backend ---------- */

  consultar(consulta: ConsultaTraza, nombre: NombreParada): Observable<Traza> {
    this.pausar();
    this.cargando.set(true);
    this.error.set(null);

    const peticion: Observable<Traza> = consulta.algoritmo === 'dijkstra'
      ? this.http.get<RespuestaRuta>(`${this.baseUrl}/calcular`, {
          params: new HttpParams()
            .set('desde', consulta.desde)
            .set('hasta', consulta.hasta ?? consulta.desde)
            .set('redId', consulta.redId)
            .set('traza', true)
        }).pipe(map(r => construirTrazaDijkstra(r, nombre)))
      : this.http.get<RespuestaRecorrido>(`${this.baseUrl}/recorrido`, {
          params: new HttpParams()
            .set('desde', consulta.desde)
            .set('modo', consulta.algoritmo)
            .set('redId', consulta.redId)
        }).pipe(map(r => construirTrazaRecorrido(r, nombre)));

    return peticion.pipe(
      tap(t => this.cargarTraza(t)),
      catchError((err: HttpErrorResponse) => {
        this.error.set(this.mensajeDeError(err));
        return throwError(() => err);
      }),
      finalize(() => this.cargando.set(false))
    );
  }

  /** Reemplaza la traza actual y vuelve al paso 0. */
  cargarTraza(traza: Traza): void {
    this.pausar();
    this.traza.set(traza);
    this.indice.set(0);
  }

  limpiar(): void {
    this.pausar();
    this.traza.set(null);
    this.indice.set(0);
    this.error.set(null);
  }

  /* ---------- Controles tipo VCR ---------- */

  irA(i: number): void {
    const total = this.totalPasos();
    if (total === 0) {
      return;
    }
    this.indice.set(Math.max(0, Math.min(total - 1, Math.trunc(i))));
  }

  siguiente(): void {
    this.pausar();
    this.irA(this.indice() + 1);
  }

  anterior(): void {
    this.pausar();
    this.irA(this.indice() - 1);
  }

  irInicio(): void {
    this.pausar();
    this.irA(0);
  }

  irFin(): void {
    this.pausar();
    this.irA(this.totalPasos() - 1);
  }

  reproducir(): void {
    if (this.totalPasos() < 2) {
      return;
    }
    if (this.enFin()) {
      this.indice.set(0);
    }
    this.reproduciendo.set(true);
    this.programar();
  }

  pausar(): void {
    this.reproduciendo.set(false);
    this.cancelarTemporizador();
  }

  alternar(): void {
    if (this.reproduciendo()) {
      this.pausar();
    } else {
      this.reproducir();
    }
  }

  cambiarVelocidad(v: Velocidad): void {
    this.velocidad.set(v);
    if (this.reproduciendo()) {
      this.programar();
    }
  }

  /* ---------- Lectura para el visor del grafo ---------- */

  estadoDe(parada: number): EstadoParada | null {
    return this.pasoActual()?.estados[parada] ?? null;
  }

  valorDe(parada: number): number | null {
    return this.valores().get(parada) ?? null;
  }

  enCamino(parada: number): boolean {
    return this.paradasCamino().has(parada);
  }

  claseTramo(origen: number, destino: number): 'camino' | 'evaluado' | 'arbol' | null {
    if (!this.pasoActual()) {
      return null;
    }
    const k = claveTramo(origen, destino);
    if (this.tramosCamino().has(k)) {
      return 'camino';
    }
    if (this.tramosEvaluados().has(k)) {
      return 'evaluado';
    }
    if (this.tramosArbol().has(k)) {
      return 'arbol';
    }
    return null;
  }

  /* ---------- Internos ---------- */

  private programar(): void {
    this.cancelarTemporizador();
    this.temporizador = setTimeout(() => {
      this.temporizador = null;
      if (!this.reproduciendo()) {
        return;
      }
      this.irA(this.indice() + 1);
      if (this.enFin()) {
        this.pausar();
      } else {
        this.programar();
      }
    }, MS_POR_PASO / this.velocidad());
  }

  private cancelarTemporizador(): void {
    if (this.temporizador !== null) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }

  private mensajeDeError(err: HttpErrorResponse): string {
    if (err.status === 401) {
      return 'Para calcular rutas hay que iniciar sesión (rol consultor, planificador o admin).';
    }
    if (err.status === 403) {
      return 'Tu rol no tiene permiso para calcular rutas.';
    }
    if (err.status === 503) {
      return err.error?.mensaje ?? 'El servicio de rutas no está disponible en este momento.';
    }
    if (err.status === 0) {
      return `No se pudo contactar el API Gateway en ${environment.apiGatewayUrl}.`;
    }
    return err.error?.mensaje ?? err.error?.error ?? `Error ${err.status} al consultar la traza.`;
  }
}
