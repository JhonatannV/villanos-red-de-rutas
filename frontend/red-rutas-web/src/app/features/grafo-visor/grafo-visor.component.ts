import { Component, inject, signal, computed, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RedService } from '../../core/services/red.service';
import { Parada, Tramo } from '../../core/models/red.model';

export interface EtiquetaPeso {
  tramoId: number;
  x: number;
  y: number;
  peso: number;
  abierto: boolean;
  origen: number;
  destino: number;
}

@Component({
  selector: 'app-grafo-visor',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './grafo-visor.component.html',
  styleUrl: './grafo-visor.component.css'
})
export class GrafoVisorComponent {
  readonly redService = inject(RedService);

  // Dimensiones base del lienzo SVG
  readonly BASE_WIDTH = 1200;
  readonly BASE_HEIGHT = 800;

  // Estado reactivo de Zoom y Paneo
  readonly zoom = signal<number>(1.0);
  readonly panX = signal<number>(0);
  readonly panY = signal<number>(0);
  readonly paradaSeleccionada = signal<Parada | null>(null);

  // Estado de arrastre manual (mouse drag)
  private isDragging = false;
  private startMouseX = 0;
  private startMouseY = 0;
  private initialPanX = 0;
  private initialPanY = 0;

  // viewBox dinámico computado a partir del zoom y paneo
  readonly viewBox = computed(() => {
    const w = Math.round((this.BASE_WIDTH / this.zoom()) * 10) / 10;
    const h = Math.round((this.BASE_HEIGHT / this.zoom()) * 10) / 10;
    const x = this.panX();
    const y = this.panY();
    return `${x} ${y} ${w} ${h}`;
  });

  // Cálculo anti-solapamiento de etiquetas de peso (Criterio de Aceptación RF-02)
  readonly etiquetasPeso = computed<EtiquetaPeso[]>(() => {
    const paradas = this.redService.paradas();
    const tramos = this.redService.tramos();

    if (paradas.length === 0 || tramos.length === 0) {
      return [];
    }

    const mapaParadas = new Map<number, Parada>();
    paradas.forEach(p => mapaParadas.set(p.id, p));

    const etiquetas: EtiquetaPeso[] = [];

    for (let i = 0; i < tramos.length; i++) {
      const t = tramos[i];
      const p1 = mapaParadas.get(t.origen);
      const p2 = mapaParadas.get(t.destino);

      if (!p1 || !p2) continue;

      const dx = p2.x - p1.x;
      const dy = p2.y - p1.y;
      const dist = Math.sqrt(dx * dx + dy * dy);

      // Fracción a lo largo de la arista (alternando entre 0.38 y 0.62)
      const fraction = (t.id % 2 === 0) ? 0.42 : 0.58;
      const baseX = p1.x + dx * fraction;
      const baseY = p1.y + dy * fraction;

      // Vector perpendicular normalizado
      const perpX = dist > 0 ? -dy / dist : 0;
      const perpY = dist > 0 ? dx / dist : 0;

      // Desplazamiento lateral alternado
      const offset = (t.id % 3 === 0) ? 10 : (t.id % 3 === 1 ? -10 : 8);

      let posX = baseX + perpX * offset;
      let posY = baseY + perpY * offset;

      // Detección y corrección iterativa de colisiones con etiquetas anteriores
      let colision = true;
      let intentos = 0;
      while (colision && intentos < 8) {
        colision = false;
        intentos++;
        for (const previa of etiquetas) {
          const dX = posX - previa.x;
          const dY = posY - previa.y;
          const d = Math.sqrt(dX * dX + dY * dY);

          if (d < 16) {
            colision = true;
            const dirX = dist > 0 ? dx / dist : 1;
            const dirY = dist > 0 ? dy / dist : 0;
            posX += dirX * 14 - perpX * 10;
            posY += dirY * 14 - perpY * 10;
            break;
          }
        }
      }

      etiquetas.push({
        tramoId: t.id,
        x: Math.round(posX * 10) / 10,
        y: Math.round(posY * 10) / 10,
        peso: t.peso,
        abierto: t.abierto,
        origen: t.origen,
        destino: t.destino
      });
    }

    return etiquetas;
  });

  // Métodos de Zoom
  zoomIn(): void {
    const actual = this.zoom();
    if (actual < 2.5) {
      this.zoom.set(Math.round((actual + 0.2) * 10) / 10);
    }
  }

  zoomOut(): void {
    const actual = this.zoom();
    if (actual > 0.6) {
      this.zoom.set(Math.round((actual - 0.2) * 10) / 10);
    }
  }

  resetVista(): void {
    this.zoom.set(1.0);
    this.panX.set(0);
    this.panY.set(0);
    this.paradaSeleccionada.set(null);
  }

  // Interacción de Paneo con el ratón
  onMouseDown(event: MouseEvent): void {
    if (event.button !== 0) return; // Solo clic izquierdo
    this.isDragging = true;
    this.startMouseX = event.clientX;
    this.startMouseY = event.clientY;
    this.initialPanX = this.panX();
    this.initialPanY = this.panY();
  }

  onMouseMove(event: MouseEvent): void {
    if (!this.isDragging) return;

    const deltaX = (event.clientX - this.startMouseX) / this.zoom();
    const deltaY = (event.clientY - this.startMouseY) / this.zoom();

    this.panX.set(Math.round(this.initialPanX - deltaX));
    this.panY.set(Math.round(this.initialPanY - deltaY));
  }

  @HostListener('window:mouseup')
  onMouseUp(): void {
    this.isDragging = false;
  }

  // Zoom con rueda del ratón
  onWheel(event: WheelEvent): void {
    event.preventDefault();
    if (event.deltaY < 0) {
      this.zoomIn();
    } else {
      this.zoomOut();
    }
  }

  seleccionarParada(parada: Parada): void {
    if (this.paradaSeleccionada()?.id === parada.id) {
      this.paradaSeleccionada.set(null);
    } else {
      this.paradaSeleccionada.set(parada);
    }
  }

  esTramoConectado(tramo: Tramo): boolean {
    const sel = this.paradaSeleccionada();
    if (!sel) return false;
    return tramo.origen === sel.id || tramo.destino === sel.id;
  }

  getCoordenadaParada(id: number): { x: number; y: number } {
    const p = this.redService.paradas().find(item => item.id === id);
    return p ? { x: p.x, y: p.y } : { x: 0, y: 0 };
  }
}
