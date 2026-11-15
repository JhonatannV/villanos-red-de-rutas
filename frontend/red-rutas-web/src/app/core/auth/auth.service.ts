import { Injectable, signal, computed } from '@angular/core';
import Keycloak from 'keycloak-js';
import { environment } from '../../../environments/environment';
import { UserProfile } from './user-profile.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private keycloakInstance: Keycloak | null = null;

  // Reactividad moderna con Angular Signals
  readonly isAuthenticated = signal<boolean>(false);
  readonly currentUser = signal<UserProfile | null>(null);
  readonly userRoles = signal<string[]>([]);
  readonly token = signal<string | null>(null);
  readonly isInitialized = signal<boolean>(false);

  // Computed signals
  readonly username = computed(() => this.currentUser()?.username ?? 'Invitado');
  readonly userSub = computed(() => this.currentUser()?.sub ?? '');
  readonly isPlanificador = computed(() => this.hasRole('planificador') || this.hasRole('admin'));
  readonly isConsultor = computed(() => this.hasRole('consultor') || this.hasRole('admin'));
  readonly isAdmin = computed(() => this.hasRole('admin'));

  constructor() {
    this.keycloakInstance = new Keycloak({
      url: environment.keycloak.url,
      realm: environment.keycloak.realm,
      clientId: environment.keycloak.clientId
    });
  }

  /**
   * Inicializa Keycloak con flujo Authorization Code y PKCE (S256).
   * No requiere ni almacena ningun client-secret en el frontend.
   */
  async init(): Promise<boolean> {
    if (this.isInitialized()) {
      return this.isAuthenticated();
    }

    try {
      if (!this.keycloakInstance) {
        this.isInitialized.set(true);
        return false;
      }

      // Timeout de seguridad: si Keycloak no responde en 8s (servidor offline),
      // continuar en modo degradado/invitado sin bloquear el inicio de la app.
      const timeoutPromise = new Promise<boolean>((_, reject) =>
        setTimeout(() => reject(new Error('Keycloak no disponible (timeout)')), 8000)
      );

      const initPromise = this.keycloakInstance.init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        checkLoginIframe: false,
        silentCheckSsoRedirectUri: window.location.origin + '/assets/silent-check-sso.html'
      });

      const authenticated = await Promise.race([initPromise, timeoutPromise]);

      this.isAuthenticated.set(authenticated);
      this.isInitialized.set(true);

      if (authenticated) {
        this.procesarToken();
      }

      // Evento de refresco de token automatico
      this.keycloakInstance.onTokenExpired = () => {
        this.refreshToken();
      };

      return authenticated;
    } catch (error) {
      console.warn('No fue posible contactar a Keycloak en inicio (modo degradado/offline):', error);
      this.isInitialized.set(true);
      this.isAuthenticated.set(false);
      return false;
    }
  }

  /**
   * Redirige al usuario al servidor Keycloak para autenticarse via Authorization Code + PKCE
   */
  async login(): Promise<void> {
    if (this.keycloakInstance) {
      await this.keycloakInstance.login({
        redirectUri: window.location.origin + '/'
      });
    }
  }

  /**
   * Cierra sesion en Keycloak y limpia la sesion local
   */
  async logout(): Promise<void> {
    if (this.keycloakInstance) {
      await this.keycloakInstance.logout({
        redirectUri: window.location.origin + '/'
      });
      this.isAuthenticated.set(false);
      this.currentUser.set(null);
      this.userRoles.set([]);
      this.token.set(null);
    }
  }

  /**
   * Obtiene el token de acceso vigente, refrescandolo transparentemente si va a expirar en menos de 30s
   */
  async getToken(): Promise<string | null> {
    if (!this.keycloakInstance) {
      return this.token();
    }

    if (this.keycloakInstance.token) {
      try {
        if (this.keycloakInstance.isTokenExpired(30)) {
          const refreshed = await this.keycloakInstance.updateToken(30);
          if (refreshed) {
            this.procesarToken();
          }
        }
        return this.keycloakInstance.token;
      } catch (error) {
        console.warn('Advertencia refrescando token, usando token existente:', error);
        return this.keycloakInstance.token;
      }
    }

    return this.token();
  }

  /**
   * Refresco forzado de token
   */
  async refreshToken(): Promise<boolean> {
    if (!this.keycloakInstance) return false;
    try {
      const refreshed = await this.keycloakInstance.updateToken(70);
      if (refreshed) {
        this.procesarToken();
        return true;
      }
      return false;
    } catch {
      this.isAuthenticated.set(false);
      return false;
    }
  }

  hasRole(role: string): boolean {
    return this.userRoles().includes(role);
  }

  hasAnyRole(roles: string[]): boolean {
    const current = this.userRoles();
    return roles.some(r => current.includes(r));
  }

  private procesarToken(): void {
    if (!this.keycloakInstance) return;

    const token = this.keycloakInstance.token ?? null;
    this.token.set(token);

    const parsed = this.keycloakInstance.tokenParsed;
    if (parsed) {
      const sub = parsed.sub ?? '';
      const username = (parsed['preferred_username'] as string) || (parsed['username'] as string) || sub;
      const email = parsed['email'] as string | undefined;
      const name = (parsed['name'] as string) || username;

      // Extraer roles del realm
      const realmAccess = parsed['realm_access'] as { roles?: string[] } | undefined;
      const roles: string[] = realmAccess?.roles ?? [];

      this.userRoles.set(roles);
      this.currentUser.set({
        sub,
        username,
        email,
        name,
        roles
      });
    }
  }

  /**
   * Metodo auxiliar para tests unitarios
   */
  setMockSession(user: UserProfile, mockToken: string): void {
    this.currentUser.set(user);
    this.userRoles.set(user.roles);
    this.token.set(mockToken);
    this.isAuthenticated.set(true);
    this.isInitialized.set(true);
  }
}
