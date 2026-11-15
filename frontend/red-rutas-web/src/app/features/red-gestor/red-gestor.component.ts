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

  // Campos formulario Parada
  nuevaParadaNombre = signal<string>('');
  nuevaParadaX = signal<number>(4.085);
  nuevaParadaY = signal<number>(-76.195);

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

  crearParada(): void {
    const nombre = this.nuevaParadaNombre().trim();
    if (!nombre) {
      this.redService.mensajeError.set('El nombre de la parada es obligatorio.');
      return;
    }

    this.redService.crearParada({
      nombre,
      x: Number(this.nuevaParadaX()),
      y: Number(this.nuevaParadaY())
    }).subscribe({
      next: () => {
        this.nuevaParadaNombre.set('');
      }
    });
  }

  crearTramo(): void {
    const origen = Number(this.nuevoTramoOrigen());
    const destino = Number(this.nuevoTramoDestino());
    const peso = Number(this.nuevoTramoPeso());

    this.redService.crearTramo(origen, destino, peso).subscribe({
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
    this.redService.cambiarEstadoTramo(tramoId, !estadoActual).subscribe();
  }

  recargarRed(): void {
    this.redService.cargarRed(1).subscribe();
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
