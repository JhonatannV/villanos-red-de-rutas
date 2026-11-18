import { Injectable, signal } from '@angular/core';

export type PestanaPanel = 'recorrido' | 'comparar' | 'red';

/**
 * Pestaña abierta en el panel lateral de la mesa de trabajo. El visor del grafo
 * la lee para saber qué pintar: la traza del reproductor o la comparación de rutas.
 */
@Injectable({ providedIn: 'root' })
export class PanelService {
  readonly pestana = signal<PestanaPanel>('recorrido');

  abrir(p: PestanaPanel): void {
    this.pestana.set(p);
  }
}
