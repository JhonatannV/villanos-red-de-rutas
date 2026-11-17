import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { RedGestorComponent } from './features/red-gestor/red-gestor.component';
import { GrafoVisorComponent } from './features/grafo-visor/grafo-visor.component';
import { ReproductorComponent } from './reproductor/reproductor.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RedGestorComponent, GrafoVisorComponent, ReproductorComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  readonly authService = inject(AuthService);

  login(): void {
    this.authService.login();
  }

  logout(): void {
    this.authService.logout();
  }
}
