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

  // Límites seguros de Zoom para no perderse en el vacío
  readonly MIN_ZOOM = 0.6;
  readonly MAX_ZOOM = 2.4;

  // Límites seguros de Paneo para mantener el grafo siempre visible
  readonly MIN_PAN_X = -120;
  readonly MAX_PAN_X = 350;
  readonly MIN_PAN_Y = -80;
  readonly MAX_PAN_Y = 250;

  // Estado reactivo de Zoom y Paneo (RF-02)
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

  // viewBox dinámico computado a partir del zoom, paneo y escala de la red
  readonly viewBox = computed(() => {
    const total = this.redService.totalParadas();
    const baseW = total > 100 ? 2500 : this.BASE_WIDTH;
    const baseH = total > 100 ? 1750 : this.BASE_HEIGHT;
    const w = Math.round((baseW / this.zoom()) * 10) / 10;
    const h = Math.round((baseH / this.zoom()) * 10) / 10;
    const x = this.panX();
    const y = this.panY();
    return `${x} ${y} ${w} ${h}`;
  });

  // Cálculo anti-solapamiento de etiquetas de peso con complejidad O(M) lineal (W-RQ-02 / RF-02)
  readonly etiquetasPeso = computed<EtiquetaPeso[]>(() => {
    const paradas = this.redService.paradas();
    const tramos = this.redService.tramos();

    if (paradas.length === 0 || tramos.length === 0) {
      return [];
    }

    const mapaParadas = new Map<number, Parada>();
    for (let i = 0; i < paradas.length; i++) {
      mapaParadas.set(paradas[i].id, paradas[i]);
    }

    const etiquetas: EtiquetaPeso[] = [];
    const CELL_SIZE = 24;
    const minDistSq = 16 * 16;
    const spatialGrid = new Map<string, EtiquetaPeso[]>();

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

      // Resolución de proximidad anti-solapamiento O(M) mediante Spatial Hash Grid
      const gx = Math.floor(finalX / CELL_SIZE);
      const gy = Math.floor(finalY / CELL_SIZE);
      let collision = false;

      for (let ox = -1; ox <= 1 && !collision; ox++) {
        for (let oy = -1; oy <= 1 && !collision; oy++) {
          const bucket = spatialGrid.get(`${gx + ox}_${gy + oy}`);
          if (bucket) {
            for (let b = 0; b < bucket.length; b++) {
              const exist = bucket[b];
              const diffX = finalX - exist.x;
              const diffY = finalY - exist.y;
              if (diffX * diffX + diffY * diffY < minDistSq) {
                finalX += dirX * 14 + perpX * 10;
                finalY += dirY * 14 + perpY * 10;
                collision = true;
                break;
              }
            }
          }
        }
      }

      const nuevaEtiqueta: EtiquetaPeso = {
        tramoId: t.id,
        x: Math.round(finalX * 10) / 10,
        y: Math.round(finalY * 10) / 10,
        peso: t.peso,
        abierto: t.abierto
      };

      etiquetas.push(nuevaEtiqueta);

      const newGx = Math.floor(nuevaEtiqueta.x / CELL_SIZE);
      const newGy = Math.floor(nuevaEtiqueta.y / CELL_SIZE);
      const cellKey = `${newGx}_${newGy}`;
      const bucket = spatialGrid.get(cellKey);
      if (bucket) {
        bucket.push(nuevaEtiqueta);
      } else {
        spatialGrid.set(cellKey, [nuevaEtiqueta]);
      }
    }

    return etiquetas;
  });

  // Métodos de control de Zoom (con límites seguros)
  zoomIn(): void {
    const nuevo = Math.min(this.MAX_ZOOM, Math.round((this.zoom() + 0.2) * 10) / 10);
    this.zoom.set(nuevo);
  }

  zoomOut(): void {
    const nuevo = Math.max(this.MIN_ZOOM, Math.round((this.zoom() - 0.2) * 10) / 10);
    this.zoom.set(nuevo);
  }

  resetVista(): void {
    this.zoom.set(1.0);
    this.panX.set(0);
    this.panY.set(0);
    this.paradaSeleccionada.set(null);
  }

  // Interacción de Paneo con el ratón acotada dentro de límites
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

    const targetX = Math.round(this.initialPanX - deltaX);
    const targetY = Math.round(this.initialPanY - deltaY);

    // Clamping para no perderse en el vacío
    const clampedX = Math.max(this.MIN_PAN_X, Math.min(this.MAX_PAN_X, targetX));
    const clampedY = Math.max(this.MIN_PAN_Y, Math.min(this.MAX_PAN_Y, targetY));

    this.panX.set(clampedX);
    this.panY.set(clampedY);
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
      } else if (t.destino === paradaId) {
        const orig = mapa.get(t.origen);
        if (orig) vecinos.push({ parada: orig, tramo: t });
      }
    }

    return vecinos;
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
