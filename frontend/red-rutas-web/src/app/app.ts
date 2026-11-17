import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { PanelService, PestanaPanel } from './core/services/panel.service';
import { RedGestorComponent } from './features/red-gestor/red-gestor.component';
import { GrafoVisorComponent } from './features/grafo-visor/grafo-visor.component';
import { ReproductorComponent } from './reproductor/reproductor.component';
import { ComparadorComponent } from './reproductor/comparador/comparador.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RedGestorComponent, GrafoVisorComponent, ReproductorComponent, ComparadorComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  readonly authService = inject(AuthService);
  readonly panel = inject(PanelService);

  readonly pestanas: { id: PestanaPanel; texto: string }[] = [
    { id: 'recorrido', texto: 'Recorrido' },
    { id: 'comparar', texto: 'Comparar' },
    { id: 'red', texto: 'Red' }
  ];

  login(): void {
    this.authService.login();
  }

  logout(): void {
    this.authService.logout();
  }

  /** Flechas izquierda/derecha entre pestañas, como pide el patrón de tabs accesibles. */
  moverPestana(evento: KeyboardEvent, indice: number): void {
    if (evento.key !== 'ArrowRight' && evento.key !== 'ArrowLeft') {
      return;
    }
    evento.preventDefault();
    evento.stopPropagation();
    const n = this.pestanas.length;
    const siguiente = (indice + (evento.key === 'ArrowRight' ? 1 : n - 1)) % n;
    this.panel.abrir(this.pestanas[siguiente].id);
    (document.getElementById(`pestana-${this.pestanas[siguiente].id}`) as HTMLElement | null)?.focus();
  }
}
