# Keycloak - Identity & Access Management (IAM)
**Responsable:** Jhonatan Varón Henao

## Propósito
Servidor de identidad dedicado basado en OAuth 2.0 y OpenID Connect (OIDC).
- Realm exportado: `rutas-realm.json`.
- Clientes configurados:
  - `red-rutas-frontend`: Cliente público para Angular (PKCE activo).
  - `red-rutas-backend`: Resource Server para validación de tokens JWKS.
- Roles de aplicación:
  - `planificador`: Mantenimiento y edición del grafo.
  - `consultor`: Consulta de rutas y grafo (solo lectura).
- Usuarios de prueba versionados para demostración en la sustentación.
