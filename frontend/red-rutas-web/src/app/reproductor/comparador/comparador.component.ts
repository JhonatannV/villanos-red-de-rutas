import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RedService } from '../../core/services/red.service';
import { Tramo } from '../../core/models/red.model';
import { ReproductorService } from '../reproductor.service';
import { claveTramo } from '../traza.builder';
import { ComparadorService, RutaComparada, textoDelta } from './comparador.service';
import { PanelService } from '../../core/services/panel.service';

interface OpcionTramo {
  tramo: Tramo;
  etiqueta: string;
}

/**
 * Comparador de 2 rutas (RF-08 / SCRUM-33). Toma la ruta que acaba de calcular
 * el reproductor, cierra un tramo y superpone la ruta resultante en el mapa.
 */
@Component({
  selector: 'app-comparador',
  standalone: true,
  templateUrl: './comparador.component.html',
  styleUrl: './comparador.component.css'
})
export class ComparadorComponent {
  readonly redService = inject(RedService);
  readonly rep = inject(ReproductorService);
  readonly comp = inject(ComparadorService);
  readonly panel = inject(PanelService);

  readonly textoDelta = textoDelta;
  readonly tramoElegido = signal<number | null>(null);

  /** Ruta original: la que el reproductor calculó con Dijkstra. */
  readonly original = computed<RutaComparada | null>(() => {
    const t = this.rep.traza();
    if (!t || t.algoritmo !== 'dijkstra' || t.hasta === null || t.costoTotal === null || t.costoTotal < 0) {
      return null;
    }
    return { desde: t.desde, hasta: t.hasta, costo: t.costoTotal, camino: t.camino };
  });

  private readonly nombres = computed(() => new Map(this.redService.paradas().map(p => [p.id, p.nombre])));

  /** Primero los tramos de la ruta (cerrarlos sí cambia algo), luego el resto. */
  readonly opciones = computed(() => {
    const o = this.original();
    const abiertos = this.redService.tramos().filter(t => t.abierto);
    if (!o) {
      return { enRuta: [] as OpcionTramo[], otros: [] as OpcionTramo[] };
    }
    const enRuta: OpcionTramo[] = [];
    for (let i = 1; i < o.camino.length; i++) {
      const k = claveTramo(o.camino[i - 1], o.camino[i]);
      const candidatos = abiertos.filter(t => claveTramo(t.origen, t.destino) === k);
      const t = candidatos.sort((a, b) => a.peso - b.peso)[0];
      if (t) {
        enRuta.push({ tramo: t, etiqueta: this.etiqueta(t) });
      }
    }
    const usados = new Set(enRuta.map(x => x.tramo.id));
    const otros = abiertos
      .filter(t => !usados.has(t.id))
      .sort((a, b) => a.id - b.id)
      .map(t => ({ tramo: t, etiqueta: this.etiqueta(t) }));
    return { enRuta, otros };
  });

  constructor() {
    // Una ruta nueva invalida la comparación anterior y propone el primer tramo de la ruta
    effect(() => {
      const o = this.original();
      untracked(() => {
        this.comp.limpiar();
        const primero = this.opciones().enRuta[0];
        this.tramoElegido.set(o && primero ? primero.tramo.id : null);
      });
    });
  }

  nombre(id: number): string {
    return this.nombres().get(id) ?? `Parada ${id}`;
  }

  private etiqueta(t: Tramo): string {
    return `Tramo ${t.id}: ${this.nombre(t.origen)} ↔ ${this.nombre(t.destino)} (${t.peso} min)`;
  }

  elegir(valor: string): void {
    this.tramoElegido.set(valor === '' ? null : Number(valor));
  }

  comparar(): void {
    const o = this.original();
    const id = this.tramoElegido();
    const tramo = this.redService.tramos().find(t => t.id === id);
    if (!o || !tramo || this.comp.cargando()) {
      return;
    }
    // El mapa muestra el estado final del reproductor mientras se compara
    this.rep.irFin();
    this.comp.comparar(o, tramo, this.redService.redActual()?.redId ?? 1)
      .subscribe({ error: () => undefined });
  }

  /** Cifra corta para el número grande: "+6 min", "−2 min" o "sin camino". */
  deltaCorto(delta: number | null): string {
    if (delta === null) {
      return 'sin camino';
    }
    return `${delta > 0 ? '+' : delta < 0 ? '−' : '±'}${Math.abs(delta)} min`;
  }

  caminoTexto(camino: number[]): string {
    return camino.map(p => this.nombre(p)).join(' → ');
  }
}
