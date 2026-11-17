import { Component, HostListener, OnDestroy, computed, effect, inject, signal, untracked } from '@angular/core';
import { RedService } from '../core/services/red.service';
import { ReproductorService } from './reproductor.service';
import { AlgoritmoTraza, Velocidad } from './traza.model';
import { crearArchivoTraza, leerArchivoTraza, mayorParada, nombreArchivoTraza } from './traza-archivo';

/**
 * Reproductor paso a paso tipo VCR (RF-04 / SCRUM-31): pide la traza a ruta-service
 * y deja avanzar, retroceder, pausar y cambiar la velocidad. El mapa se pinta
 * desde ReproductorService en el visor del grafo.
 */
@Component({
  selector: 'app-reproductor',
  standalone: true,
  templateUrl: './reproductor.component.html',
  styleUrl: './reproductor.component.css'
})
export class ReproductorComponent implements OnDestroy {
  readonly redService = inject(RedService);
  readonly rep = inject(ReproductorService);

  readonly algoritmos: { id: AlgoritmoTraza; etiqueta: string }[] = [
    { id: 'dijkstra', etiqueta: 'Dijkstra' },
    { id: 'anchura', etiqueta: 'BFS' },
    { id: 'profundidad', etiqueta: 'DFS' }
  ];
  readonly velocidades: Velocidad[] = [0.5, 1, 2];

  readonly algoritmo = signal<AlgoritmoTraza>('dijkstra');
  readonly desde = signal(0);
  readonly hasta = signal(1);

  readonly paradasOrdenadas = computed(() =>
    [...this.redService.paradas()].sort((a, b) => a.id - b.id));

  readonly nombres = computed(() => {
    const m = new Map<number, string>();
    for (const p of this.redService.paradas()) {
      m.set(p.id, p.nombre);
    }
    return m;
  });

  /** Resultado de la última exportación o importación (SCRUM-34). */
  readonly avisoArchivo = signal<{ tipo: 'ok' | 'error'; texto: string } | null>(null);

  readonly mismaParada = computed(() => this.algoritmo() === 'dijkstra' && this.desde() === this.hasta());

  readonly caminoNombres = computed(() => (this.rep.traza()?.camino ?? []).map(id => this.nombre(id)));

  private readonly redId = computed(() => this.redService.redActual()?.redId ?? 1);
  private destinoElegido = false;

  constructor() {
    // Si cambia la red, la traza anterior ya no corresponde al mapa
    effect(() => {
      this.redId();
      untracked(() => this.rep.limpiar());
    });

    // Destino por defecto: la última parada de la red recién cargada
    effect(() => {
      const total = this.redService.totalParadas();
      untracked(() => {
        if (total > 1 && (!this.destinoElegido || this.hasta() >= total)) {
          this.hasta.set(total - 1);
        }
        if (this.desde() >= total) {
          this.desde.set(0);
        }
      });
    });
  }

  ngOnDestroy(): void {
    this.rep.pausar();
  }

  nombre(id: number | null): string {
    if (id === null || id < 0) {
      return '—';
    }
    return this.nombres().get(id) ?? `Parada ${id}`;
  }

  elegirAlgoritmo(a: AlgoritmoTraza): void {
    this.algoritmo.set(a);
  }

  cambiarDesde(valor: string): void {
    this.desde.set(Number(valor));
  }

  cambiarHasta(valor: string): void {
    this.destinoElegido = true;
    this.hasta.set(Number(valor));
  }

  calcular(): void {
    if (this.mismaParada() || this.rep.cargando()) {
      return;
    }
    this.rep.consultar({
      algoritmo: this.algoritmo(),
      desde: this.desde(),
      hasta: this.algoritmo() === 'dijkstra' ? this.hasta() : null,
      redId: this.redId()
    }, id => this.nombre(id)).subscribe({ error: () => { /* el mensaje queda en rep.error() */ } });
  }

  /* ---------- Exportar e importar la traza en JSON (RF-09 / SCRUM-34) ---------- */

  exportar(): void {
    const traza = this.rep.traza();
    if (!traza) {
      return;
    }
    const archivo = crearArchivoTraza(traza, {
      nombre: this.redService.redActual()?.nombre,
      paradas: this.redService.totalParadas()
    });
    const blob = new Blob([JSON.stringify(archivo, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombreArchivoTraza(traza);
    enlace.click();
    setTimeout(() => URL.revokeObjectURL(url), 0);
    this.avisoArchivo.set({ tipo: 'ok', texto: `Traza exportada como ${enlace.download} (${traza.pasos.length} pasos).` });
  }

  async importar(evento: Event): Promise<void> {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    input.value = '';
    if (!archivo) {
      return;
    }
    try {
      this.cargarArchivo(await archivo.text(), archivo.name);
    } catch (e) {
      this.avisoArchivo.set({ tipo: 'error', texto: (e as Error).message });
    }
  }

  /** Valida el JSON y lo carga en el reproductor sin consultar al backend. */
  cargarArchivo(texto: string, nombreArchivo: string): void {
    const leido = leerArchivoTraza(texto);
    const traza = leido.traza;
    const total = this.redService.totalParadas();
    if (mayorParada(traza) >= total) {
      throw new Error(`La traza usa la parada ${mayorParada(traza)}, pero la red cargada solo tiene ${total} paradas.`);
    }
    this.rep.cargarTraza(traza);
    this.algoritmo.set(traza.algoritmo);
    this.desde.set(traza.desde);
    if (traza.hasta !== null) {
      this.hasta.set(traza.hasta);
    }
    const otraRed = traza.redId !== this.redId() ? ` Ojo: se exportó desde la red ${traza.redId}.` : '';
    this.avisoArchivo.set({
      tipo: 'ok',
      texto: `Traza importada de ${nombreArchivo}: ${traza.pasos.length} pasos, sin consultar al servidor.${otraRed}`
    });
  }

  moverA(valor: string): void {
    this.rep.pausar();
    this.rep.irA(Number(valor));
  }

  /** Atajos: espacio reproduce o pausa; flechas avanzan o retroceden. */
  @HostListener('document:keydown', ['$event'])
  onTecla(evento: KeyboardEvent): void {
    if (!this.rep.traza() || evento.altKey || evento.ctrlKey || evento.metaKey) {
      return;
    }
    const destino = evento.target as HTMLElement | null;
    const etiqueta = destino?.tagName?.toLowerCase();
    if (etiqueta === 'input' || etiqueta === 'select' || etiqueta === 'textarea' || etiqueta === 'button'
        || destino?.isContentEditable) {
      return;
    }
    if (evento.key === 'ArrowRight') {
      this.rep.siguiente();
    } else if (evento.key === 'ArrowLeft') {
      this.rep.anterior();
    } else if (evento.key === ' ') {
      this.rep.alternar();
    } else {
      return;
    }
    evento.preventDefault();
  }
}
