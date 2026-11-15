import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RedService } from '../../core/services/red.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-red-gestor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './red-gestor.component.html',
  styleUrl: './red-gestor.component.css'
})
export class RedGestorComponent implements OnInit {
  readonly redService = inject(RedService);
  readonly authService = inject(AuthService);

  // Campos formulario Parada (Espacio de coordenadas SVG: 1200x800)
  nuevaParadaNombre = signal<string>('');
  nuevaParadaX = signal<number>(600);
  nuevaParadaY = signal<number>(400);

  // Campos formulario Tramo
  nuevoTramoOrigen = signal<number>(0);
  nuevoTramoDestino = signal<number>(1);
  nuevoTramoPeso = signal<number>(10);

  // Filtros y estado colapsable (Accordion)
  filtroParada = signal<string>('');
  tabActiva = signal<'paradas' | 'tramos'>('paradas');
  tablaExpandida = signal<boolean>(false);

  toggleTabla(): void {
    this.tablaExpandida.update(v => !v);
  }

  cambiarTab(tab: 'paradas' | 'tramos'): void {
    this.tabActiva.set(tab);
    this.tablaExpandida.set(true); // Al hacer clic en una pestaña, expandir automáticamente
  }

  ngOnInit(): void {
    this.redService.cargarRed(1).subscribe({
      next: red => {
        if (red.paradas.length > 1) {
          this.nuevoTramoOrigen.set(red.paradas[0].id);
          this.nuevoTramoDestino.set(red.paradas[1].id);
        }
      },
      error: () => {}
    });
  }

  alinearACuadracula(): void {
    const x = Math.round(Number(this.nuevaParadaX()) / 50) * 50;
    const y = Math.round(Number(this.nuevaParadaY()) / 50) * 50;
    this.nuevaParadaX.set(Math.max(50, Math.min(1150, x)));
    this.nuevaParadaY.set(Math.max(50, Math.min(750, y)));
  }

  crearParada(): void {
    const nombre = this.nuevaParadaNombre().trim();
    if (!nombre) {
      this.redService.mensajeError.set('El nombre de la parada es obligatorio.');
      return;
    }

    const x = Number(this.nuevaParadaX());
    const y = Number(this.nuevaParadaY());

    if (isNaN(x) || isNaN(y)) {
      this.redService.mensajeError.set('Las coordenadas X e Y deben ser valores numéricos válidos.');
      return;
    }

    // Validación de límites: la cuadrícula del visualizador mide 1200 x 800 px
    const minX = 50, maxX = 1150;
    const minY = 50, maxY = 750;

    if (x < minX || x > maxX || y < minY || y > maxY) {
      this.redService.mensajeError.set(
        `Coordenadas fuera de la cuadrícula: La parada debe ubicarse dentro del área visible del grafo (X entre ${minX} y ${maxX}, Y entre ${minY} y ${maxY} px).`
      );
      return;
    }

    // Redondear a coordenada entera para nitidez en el SVG
    const xFinal = Math.round(x);
    const yFinal = Math.round(y);

    this.redService.crearParada({
      nombre,
      x: xFinal,
      y: yFinal
    }, this.redSeleccionada()).subscribe({
      next: () => {
        this.nuevaParadaNombre.set('');
        // Desplazar sugerencia a la siguiente posición en la cuadrícula
        const sigX = xFinal + 50 <= maxX ? xFinal + 50 : 100;
        const sigY = xFinal + 50 <= maxX ? yFinal : Math.min(maxY, yFinal + 50);
        this.nuevaParadaX.set(sigX);
        this.nuevaParadaY.set(sigY);
      }
    });
  }

  crearTramo(): void {
    const origen = Number(this.nuevoTramoOrigen());
    const destino = Number(this.nuevoTramoDestino());
    const peso = Number(this.nuevoTramoPeso());

    this.redService.crearTramo(origen, destino, peso, this.redSeleccionada()).subscribe({
      next: () => {
        this.nuevoTramoPeso.set(10);
      }
    });
  }

  toggleEstadoTramo(tramoId: number, estadoActual: boolean): void {
    if (!this.authService.isPlanificador()) {
      this.redService.mensajeError.set('Error 403: Se requiere rol planificador o admin para cambiar el estado de tramos.');
      return;
    }
    this.redService.cambiarEstadoTramo(tramoId, !estadoActual, this.redSeleccionada()).subscribe();
  }

  readonly redSeleccionada = signal<number>(1);

  cargarRedSeleccionada(redId: number): void {
    this.redSeleccionada.set(redId);
    this.redService.cargarRed(redId).subscribe();
  }

  recargarRed(): void {
    this.redService.cargarRed(this.redSeleccionada()).subscribe();
  }

  getParadaNombre(id: number): string {
    const p = this.redService.paradas().find(item => item.id === id);
    return p ? `${p.nombre} (ID: ${p.id})` : `Parada ${id}`;
  }

  paradasFiltradas() {
    const filtro = this.filtroParada().toLowerCase().trim();
    if (!filtro) return this.redService.paradas();
    return this.redService.paradas().filter(p => 
      p.nombre.toLowerCase().includes(filtro) || p.id.toString() === filtro
    );
  }
}
