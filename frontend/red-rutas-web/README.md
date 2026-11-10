# red-rutas-web (Angular 17+)
**Responsables:** 
- Jhonatan Varón Henao (Seguridad PKCE, Interceptor HTTP y Lienzo SVG/Canvas con paneo/zoom)
- Santiago Ríos Morales (Reproductor animado VCR paso a paso y tabla dinámica)

## Propósito
Frontend reactivo construido con Standalone Components y Signals.
- Autenticación con Keycloak mediante Authorization Code Flow con PKCE (sin client secret).
- Interceptor HTTP que inyecta automáticamente el token Bearer en cada petición.
- Lienzo visual del grafo con soporte de paneo y zoom.
- Controles de reproducción paso a paso (Play, Pausa, Siguiente, Anterior, Velocidad).
- Modo degradado: si `ruta-service` no responde, la UI sigue mostrando el mapa y avisa el fallo sin pantallas en blanco (RQ-01).
