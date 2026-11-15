import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RedService } from '../../core/services/red.service';
import { Parada, Tramo } from '../../core/models/red.model';

export interface EtiquetaPeso {
  tramoId: number;
  x: number;
  y: number;
  peso: number;
  abierto: boolean;
}

export interface VecinoConexion {
  parada: Parada;
  tramo: Tramo;
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

  // Estados interactivos para origen / destino de rutas
  readonly paradaOrigen = signal<Parada | null>(null);
  readonly paradaDestino = signal<Parada | null>(null);

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

      // Fracción a lo largo de la arista (alternando entre 0.42 y 0.58)
      const fraction = (t.id % 2 === 0) ? 0.42 : 0.58;
      const baseX = p1.x + dx * fraction;
      const baseY = p1.y + dy * fraction;

      let perpX = 0;
      let perpY = 0;
      let dirX = 0;
      let dirY = 0;

      if (dist > 0.0001) {
        dirX = dx / dist;
        dirY = dy / dist;
        // Vector perpendicular normalizado (-dy, dx)
        perpX = -dy / dist;
        perpY = dx / dist;
      }

      // Desplazamiento inicial perpendicular
      const offsetPerp = (t.id % 2 === 0) ? 10 : -10;
      let finalX = baseX + perpX * offsetPerp;
      let finalY = baseY + perpY * offsetPerp;

      // Desplazamiento adicional para tramos con extremos adyacentes o paralelos
      const offsetAlong = ((t.id * 7) % 15) - 7;
      finalX += dirX * offsetAlong;
      finalY += dirY * offsetAlong;

      // Resolución iterativa de proximidad si colisiona con alguna ya calculada
      for (let j = 0; j < etiquetas.length; j++) {
        const exist = etiquetas[j];
        const diffX = finalX - exist.x;
        const diffY = finalY - exist.y;
        const distSq = diffX * diffX + diffY * diffY;
        const minDist = 16;

        if (distSq < minDist * minDist) {
          // Desplazar a lo largo de la arista y perpendicularmente para evitar el solapamiento
          finalX += dirX * 14 + perpX * 10;
          finalY += dirY * 14 + perpY * 10;
        }
      }

      etiquetas.push({
        tramoId: t.id,
        x: Math.round(finalX * 10) / 10,
        y: Math.round(finalY * 10) / 10,
        peso: t.peso,
        abierto: t.abierto
      });
    }

    return etiquetas;
  });

  // Métodos de control de Zoom
  zoomIn(): void {
    const nuevo = Math.min(4.0, Math.round((this.zoom() + 0.2) * 10) / 10);
    this.zoom.set(nuevo);
  }

  zoomOut(): void {
    const nuevo = Math.max(0.3, Math.round((this.zoom() - 0.2) * 10) / 10);
    this.zoom.set(nuevo);
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

  marcarComoOrigen(parada: Parada): void {
    this.paradaOrigen.set(parada);
    if (this.paradaDestino()?.id === parada.id) {
      this.paradaDestino.set(null);
    }
  }

  marcarComoDestino(parada: Parada): void {
    this.paradaDestino.set(parada);
    if (this.paradaOrigen()?.id === parada.id) {
      this.paradaOrigen.set(null);
    }
  }

  limpiarSeleccionRuta(): void {
    this.paradaOrigen.set(null);
    this.paradaDestino.set(null);
  }

  getVecinos(paradaId: number): VecinoConexion[] {
    const tramos = this.redService.tramos();
    const paradas = this.redService.paradas();
    const mapa = new Map<number, Parada>();
    paradas.forEach(p => mapa.set(p.id, p));

    const vecinos: VecinoConexion[] = [];

    for (const t of tramos) {
      if (t.origen === paradaId) {
        const dest = mapa.get(t.destino);
        if (dest) vecinos.push({ parada: dest, tramo: t });
      }
    }

    return vecinos;
  }

  esTramoConectado(tramo: Tramo): boolean {
    const sel = this.paradaSeleccionada();
    const orig = this.paradaOrigen();
    const dest = this.paradaDestino();

    if (sel && (tramo.origen === sel.id || tramo.destino === sel.id)) {
      return true;
    }
    if (orig && dest && ((tramo.origen === orig.id && tramo.destino === dest.id) || (tramo.origen === dest.id && tramo.destino === orig.id))) {
      return true;
    }
    return false;
  }

  getCoordenadaParada(id: number): { x: number; y: number } {
    const p = this.redService.paradas().find(item => item.id === id);
    return p ? { x: p.x, y: p.y } : { x: 0, y: 0 };
  }
}
