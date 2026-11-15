import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, catchError, of, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Parada, Tramo, RedGrafo } from '../models/red.model';
import { RED_DEFAULT_40 } from '../models/red-default.data';
import { RED_GRANDE_400 } from '../models/red-400.data';

@Injectable({
  providedIn: 'root'
})
export class RedService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiGatewayUrl}/api/redes`;
  private readonly STORAGE_KEY = 'red_rutas_cache_';

  // Reactividad con Signals
  readonly redActual = signal<RedGrafo | null>(null);
  readonly paradas = signal<Parada[]>([]);
  readonly tramos = signal<Tramo[]>([]);
  readonly cargando = signal<boolean>(false);
  readonly mensajeExito = signal<string | null>(null);
  readonly mensajeError = signal<string | null>(null);

  // Computed signals
  readonly totalParadas = computed(() => this.paradas().length);
  readonly totalTramos = computed(() => this.tramos().length);
  readonly tramosAbiertos = computed(() => this.tramos().filter(t => t.abierto).length);
  readonly tramosCerrados = computed(() => this.tramos().filter(t => !t.abierto).length);

  /**
   * Carga la red de transporte desde grafo-service (RF-01).
   * Almacena una copia de persistencia local para asegurar consistencia identica tras recargas.
   */
  cargarRed(redId: number = 1): Observable<RedGrafo> {
    this.cargando.set(true);
    this.mensajeError.set(null);

    return this.http.get<RedGrafo>(`${this.baseUrl}/${redId}`).pipe(
      tap(red => {
        this.aplicarRed(red);
        this.guardarEnAlmacenamientoLocal(red);
        this.cargando.set(false);
      }),
      catchError(err => {
        console.warn('Error al cargar la red desde el Gateway/grafo-service, verificando respaldo local:', err);
        const respaldo = this.obtenerDeAlmacenamientoLocal(redId);
        if (respaldo) {
          this.aplicarRed(respaldo);
          this.mensajeExito.set(`Red cargada desde persistencia local segura (${respaldo.paradas.length} paradas, ${respaldo.tramos.length} tramos).`);
        } else {
          const fallback = redId === 2 ? RED_GRANDE_400 : RED_DEFAULT_40;
          this.aplicarRed(fallback);
          this.guardarEnAlmacenamientoLocal(fallback);
          this.mensajeExito.set(`Red cargada con dataset predeterminado (${fallback.paradas.length} paradas, ${fallback.tramos.length} tramos).`);
        }
        this.cargando.set(false);
        return of(this.redActual()!);
      })
    );
  }

  /**
   * Carga la red grande de 400 paradas y 1.100 tramos para mediciones de desempeño (W-RQ-02)
   */
  cargarRedGrande(): Observable<RedGrafo> {
    return this.cargarRed(2);
  }

  /**
   * Carga la red urbana estándar de 40 paradas y 90 tramos
   */
  cargarRedUrbana(): Observable<RedGrafo> {
    return this.cargarRed(1);
  }

  /**
   * Crea una nueva parada con coordenadas x, y en la red
   */
  crearParada(parada: { nombre: string; x: number; y: number }, redId: number = 1): Observable<Parada> {
    this.cargando.set(true);
    this.mensajeError.set(null);

    const payload: Parada = {
      id: 0,
      nombre: parada.nombre.trim(),
      x: parada.x,
      y: parada.y
    };

    return this.http.post<Parada>(`${this.baseUrl}/${redId}/paradas`, payload).pipe(
      tap(creada => {
        const actualizadas = [...this.paradas(), creada];
        this.paradas.set(actualizadas);
        this.actualizarRedActualParadas(actualizadas);
        this.mensajeExito.set(`Parada "${creada.nombre}" (ID: ${creada.id}) creada y persistida con éxito.`);
        this.cargando.set(false);
      }),
      catchError(err => {
        this.cargando.set(false);
        const mensaje = err?.status === 403 
          ? 'Error 403 Forbidden: Su rol actual no tiene permisos para crear paradas (requiere rol planificador o admin).'
          : 'Error al persistir la parada en el servidor.';
        this.mensajeError.set(mensaje);
        return throwError(() => err);
      })
    );
  }

  /**
   * Crea un nuevo tramo con peso estrictamente positivo (> 0)
   */
  crearTramo(origen: number, destino: number, peso: number, redId: number = 1): Observable<Tramo> {
    if (peso <= 0) {
      const errorMsg = 'El peso del tramo debe ser estrictamente positivo (> 0 minutos).';
      this.mensajeError.set(errorMsg);
      return throwError(() => new Error(errorMsg));
    }

    if (origen === destino) {
      const errorMsg = 'El tramo no puede conectar una parada consigo misma (origen y destino deben ser distintos).';
      this.mensajeError.set(errorMsg);
      return throwError(() => new Error(errorMsg));
    }

    this.cargando.set(true);
    this.mensajeError.set(null);

    const payload: Tramo = {
      id: 0,
      origen,
      destino,
      peso,
      abierto: true
    };

    return this.http.post<Tramo>(`${this.baseUrl}/${redId}/tramos`, payload).pipe(
      tap(creado => {
        const actualizados = [...this.tramos(), creado];
        this.tramos.set(actualizados);
        this.actualizarRedActualTramos(actualizados);
        this.mensajeExito.set(`Tramo ${creado.origen} -> ${creado.destino} (peso: ${creado.peso} min) persistido.`);
        this.cargando.set(false);
      }),
      catchError(err => {
        this.cargando.set(false);
        const mensaje = err?.status === 403 
          ? 'Error 403 Forbidden: Su rol actual no tiene permisos para crear tramos (requiere rol planificador o admin).'
          : 'Error al persistir el tramo en el servidor.';
        this.mensajeError.set(mensaje);
        return throwError(() => err);
      })
    );
  }

  /**
   * Cambia el estado de un tramo (abierto / cerrado) para simular cierres viales
   */
  cambiarEstadoTramo(tramoId: number, abierto: boolean, redId: number = 1): Observable<Tramo> {
    this.cargando.set(true);
    this.mensajeError.set(null);

    return this.http.put<Tramo>(`${this.baseUrl}/${redId}/tramos/${tramoId}/estado?abierto=${abierto}`, {}).pipe(
      tap(modificado => {
        const actualizados = this.tramos().map(t => t.id === tramoId ? { ...t, abierto: modificado.abierto } : t);
        this.tramos.set(actualizados);
        this.actualizarRedActualTramos(actualizados);
        this.mensajeExito.set(`Tramo ID ${tramoId} ${abierto ? 'abierto' : 'cerrado'} exitosamente.`);
        this.cargando.set(false);
      }),
      catchError(err => {
        this.cargando.set(false);
        const mensaje = err?.status === 403 
          ? 'Error 403 Forbidden: No tiene permisos para modificar tramos.'
          : 'Error al actualizar el estado del tramo.';
        this.mensajeError.set(mensaje);
        return throwError(() => err);
      })
    );
  }

  limpiarMensajes(): void {
    this.mensajeExito.set(null);
    this.mensajeError.set(null);
  }

  private aplicarRed(red: RedGrafo): void {
    this.redActual.set(red);
    this.paradas.set(red.paradas || []);
    this.tramos.set(red.tramos || []);
  }

  private actualizarRedActualParadas(paradas: Parada[]): void {
    const actual = this.redActual();
    if (actual) {
      const nueva = { ...actual, paradas };
      this.redActual.set(nueva);
      this.guardarEnAlmacenamientoLocal(nueva);
    }
  }

  private actualizarRedActualTramos(tramos: Tramo[]): void {
    const actual = this.redActual();
    if (actual) {
      const nueva = { ...actual, tramos };
      this.redActual.set(nueva);
      this.guardarEnAlmacenamientoLocal(nueva);
    }
  }

  private guardarEnAlmacenamientoLocal(red: RedGrafo): void {
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem(`${this.STORAGE_KEY}${red.redId}`, JSON.stringify(red));
      }
    } catch {
      // Ignorar si el almacenamiento local no esta disponible
    }
  }

  private obtenerDeAlmacenamientoLocal(redId: number): RedGrafo | null {
    try {
      if (typeof localStorage !== 'undefined') {
        const raw = localStorage.getItem(`${this.STORAGE_KEY}${redId}`);
        if (raw) {
          return JSON.parse(raw) as RedGrafo;
        }
      }
    } catch {
      return null;
    }
    return null;
  }
}
