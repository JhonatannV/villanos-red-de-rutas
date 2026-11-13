# SCRUM-26 [TEST-02] · Registro de evidencia HTTP de seguridad

**Criterio de aceptación:** script reproducible que registre las respuestas HTTP de seguridad (401, 403 y 200) con el comando ejecutado junto a cada una, listo para el entregable 06.

**Resultado:** ✅ 14 de 14 respuestas como se esperaba, con el sistema levantado por `docker compose`. Registro completo con comandos y respuestas: [`http_evidencia_2026-11-13_0834.md`](http_evidencia_2026-11-13_0834.md).

| Caso | Dónde | Esperado | Obtenido |
|---|---|:---:|:---:|
| Sin cabecera `Authorization` | gateway, `grafo-service` y `ruta-service` por separado | 401 | 401 ×3 |
| Token con un carácter de la firma alterado | gateway, `grafo-service` y `ruta-service` por separado | 401 | 401 ×3 |
| `consultor` crea una parada (nodo) | gateway y `grafo-service` directo | 403 | 403 ×2 |
| `consultor` modifica un tramo (arista) | gateway | 403 | 403 |
| `consultor` elimina una parada (nodo) | gateway | 403 | 403 |
| `consultor` consulta la red y calcula una ruta | gateway | 200 | 200 ×2 |
| `planificador` modifica un tramo y consulta la auditoría | gateway | 200 | 200 ×2 |

## Qué demuestra

- **RA-04:** cada servicio valida el token por su cuenta. El 401 sin token y con firma alterada se comprobó contra el gateway y **directamente contra cada microservicio**, no solo a través del frontend.
- **RA-05:** la autorización está en el backend. Un token válido con el rol equivocado responde **403**, no 401 ni 200. Se probó en las dos operaciones que la matriz prohíbe al `consultor`: crear o modificar la topología, y eliminar. También se probó contra `grafo-service` directo, sin pasar por el gateway.
- La diferencia que pide el enunciado:
  - **401** significa "no se sabe quién es": no hay token o la firma no es válida;
  - **403** significa "se sabe quién es y no le corresponde".

## Cómo se obtiene

`scripts/evidencia_http.ps1`:
1. Pide tokens al endpoint de Keycloak con los usuarios de prueba del realm (`consultor` y `planificador`).
2. Arma el token alterado cambiando un carácter de la firma del token del consultor.
3. Ejecuta las 14 peticiones.
4. Escribe la tabla y cada comando con su respuesta en `docs/pruebas/http_evidencia_<fecha>.md`. Los tokens no se guardan en el registro.

Termina con código 0 solo si todas las respuestas son las esperadas.

```powershell
docker compose up -d --build
powershell -ExecutionPolicy Bypass -File scripts/evidencia_http.ps1
```

**Efectos del script:** la petición 13 deja abierto el tramo 5, que ya estaba abierto, así que no cambia la red. Queda registrada en la auditoría de `grafo-service` con el `sub` del planificador (RA-06).
