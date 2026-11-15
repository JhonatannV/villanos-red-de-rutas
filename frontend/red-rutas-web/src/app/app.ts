import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { RedGestorComponent } from './features/red-gestor/red-gestor.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RedGestorComponent],
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
