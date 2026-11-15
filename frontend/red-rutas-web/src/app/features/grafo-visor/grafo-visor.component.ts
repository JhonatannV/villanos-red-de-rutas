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

export interface SegmentoRuta {
  origen: Parada;
  destino: Parada;
  tramoId: number;
  peso: number;
}

export interface RutaCalculada {
  origen: Parada;
  destino: Parada;
  costoTotal: number;
  paradas: Parada[];
  tramosIds: Set<number>;
  segmentos: SegmentoRuta[];
  encontrada: boolean;
  mensaje?: string;
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

  // Límites estrictos de Zoom para no perderse en el vacío
  readonly MIN_ZOOM = 0.6;
  readonly MAX_ZOOM = 2.4;

  // Límites de Paneo para mantener el grafo siempre visible
  readonly MIN_PAN_X = -120;
  readonly MAX_PAN_X = 350;
  readonly MIN_PAN_Y = -80;
  readonly MAX_PAN_Y = 250;

  // Estado reactivo de Zoom y Paneo
  readonly zoom = signal<number>(1.0);
  readonly panX = signal<number>(0);
  readonly panY = signal<number>(0);
  readonly paradaSeleccionada = signal<Parada | null>(null);

  // Selección de Origen y Destino para la ruta
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

  // Cálculo reactivo del camino más corto con Dijkstra (RF-03)
  readonly rutaOptima = computed<RutaCalculada | null>(() => {
    const orig = this.paradaOrigen();
    const dest = this.paradaDestino();

    if (!orig || !dest) return null;

    if (orig.id === dest.id) {
      return {
        origen: orig,
        destino: dest,
        costoTotal: 0,
        paradas: [orig],
        tramosIds: new Set<number>(),
        segmentos: [],
        encontrada: true
      };
    }

    return this.ejecutarDijkstra(orig, dest);
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

  // Interacción de Paneo con el ratón con acotamiento de límites
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

    // Clamping para no desplazarse al vacío
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
      } else if (t.destino === paradaId) {
        const orig = mapa.get(t.origen);
        if (orig) vecinos.push({ parada: orig, tramo: t });
      }
    }

    return vecinos;
  }

  // Verifica si un tramo forma parte de la ruta óptima calculada
  esTramoDeRuta(tramoId: number): boolean {
    const ruta = this.rutaOptima();
    return !!ruta && ruta.encontrada && ruta.tramosIds.has(tramoId);
  }

  // Verifica si una parada forma parte de la ruta óptima calculada
  esParadaDeRuta(paradaId: number): boolean {
    const ruta = this.rutaOptima();
    if (!ruta || !ruta.encontrada) return false;
    return ruta.paradas.some(p => p.id === paradaId);
  }

  esTramoConectado(tramo: Tramo): boolean {
    const sel = this.paradaSeleccionada();
    if (sel && (tramo.origen === sel.id || tramo.destino === sel.id)) {
      return true;
    }
    return this.esTramoDeRuta(tramo.id);
  }

  getCoordenadaParada(id: number): { x: number; y: number } {
    const p = this.redService.paradas().find(item => item.id === id);
    return p ? { x: p.x, y: p.y } : { x: 0, y: 0 };
  }

  /**
   * Implementación del Algoritmo de Dijkstra sobre la red (RF-03)
   * Respeta el estado de los tramos (ignora los tramos cerrados por mantenimiento).
   */
  private ejecutarDijkstra(origen: Parada, destino: Parada): RutaCalculada {
    const paradas = this.redService.paradas();
    const tramos = this.redService.tramos();

    const mapaParadas = new Map<number, Parada>();
    paradas.forEach(p => mapaParadas.set(p.id, p));

    // Grafo bidireccional (los tramos permiten tránsito en ambos sentidos)
    interface Arco {
      vecinoId: number;
      peso: number;
      tramoId: number;
    }
    const adyacencia = new Map<number, Arco[]>();
    paradas.forEach(p => adyacencia.set(p.id, []));

    for (const t of tramos) {
      if (!t.abierto) continue; // Si el tramo está cerrado, no se puede transitar (RF-06)
      adyacencia.get(t.origen)?.push({ vecinoId: t.destino, peso: t.peso, tramoId: t.id });
      adyacencia.get(t.destino)?.push({ vecinoId: t.origen, peso: t.peso, tramoId: t.id });
    }

    const dist = new Map<number, number>();
    const prevParada = new Map<number, number>();
    const prevTramo = new Map<number, number>();
    const visitado = new Set<number>();

    paradas.forEach(p => dist.set(p.id, Infinity));
    dist.set(origen.id, 0);

    for (let i = 0; i < paradas.length; i++) {
      let u: number | null = null;
      let mejorDist = Infinity;

      for (const [id, d] of dist.entries()) {
        if (!visitado.has(id) && d < mejorDist) {
          mejorDist = d;
          u = id;
        }
      }

      if (u === null || mejorDist === Infinity) break;
      if (u === destino.id) break;

      visitado.add(u);

      const vecinos = adyacencia.get(u) || [];
      for (const arco of vecinos) {
        if (!visitado.has(arco.vecinoId)) {
          const nuevaDist = mejorDist + arco.peso;
          if (nuevaDist < (dist.get(arco.vecinoId) ?? Infinity)) {
            dist.set(arco.vecinoId, nuevaDist);
            prevParada.set(arco.vecinoId, u);
            prevTramo.set(arco.vecinoId, arco.tramoId);
          }
        }
      }
    }

    const costoFinal = dist.get(destino.id) ?? Infinity;

    if (costoFinal === Infinity) {
      return {
        origen,
        destino,
        costoTotal: 0,
        paradas: [],
        tramosIds: new Set<number>(),
        segmentos: [],
        encontrada: false,
        mensaje: `No existe camino vial disponible entre "${origen.nombre}" y "${destino.nombre}" (las vías están cerradas o desconectadas).`
      };
    }

    // Reconstruir el camino desde destino hasta origen
    const caminoParadas: Parada[] = [];
    const tramosIds = new Set<number>();
    const segmentos: SegmentoRuta[] = [];
    let curr = destino.id;

    while (curr !== origen.id) {
      const pActual = mapaParadas.get(curr);
      if (pActual) caminoParadas.unshift(pActual);

      const tramoId = prevTramo.get(curr);
      const ant = prevParada.get(curr);

      if (tramoId !== undefined) tramosIds.add(tramoId);

      if (ant !== undefined && pActual && tramoId !== undefined) {
        const pAnt = mapaParadas.get(ant);
        const tramoObj = tramos.find(t => t.id === tramoId);
        if (pAnt && tramoObj) {
          segmentos.unshift({
            origen: pAnt,
            destino: pActual,
            tramoId: tramoId,
            peso: tramoObj.peso
          });
        }
        curr = ant;
      } else {
        break;
      }
    }
    caminoParadas.unshift(origen);

    return {
      origen,
      destino,
      costoTotal: costoFinal,
      paradas: caminoParadas,
      tramosIds,
      segmentos,
      encontrada: true
    };
  }
}
