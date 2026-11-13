# SCRUM-24 [RF-06] · Componentes conexas y simulación de tramos cerrados

**Criterio de aceptación:** al cerrar un tramo que es puente entre dos zonas, el sistema reporta que el número de componentes pasó de 1 a 2 y nombra las paradas que quedaron aisladas.

**Resultado:** ✅ con la red de 40 paradas y el tramo 90 (Salida Sur – Peaje Aeropuerto) cerrado:

```json
{
  "componentesConTodoAbierto": 1,
  "componentes": 2,
  "aisladas": [37, 38, 39],
  "aisladasNombres": ["Peaje Aeropuerto", "Terminal Aérea", "Zona Rural La Marina"],
  "mensaje": "Al cerrar el tramo 90 la red pasó de 1 a 2 componentes. Quedaron aisladas: Peaje Aeropuerto, Terminal Aérea, Zona Rural La Marina."
}
```

Coincide con el caso de contingencia de `docs/casos_prueba_rf03.md`.

## Tramos puente

Además de comparar antes y después, el endpoint lista los **tramos puente** de la red: los que, si se cierran, dejan paradas incomunicadas. Se calculan con el algoritmo de Tarjan en O(V + E).

En la red de 40 paradas **el único puente es el tramo 90**. Cualquier otro tramo se puede cerrar sin aislar paradas: solo alarga rutas. Para un planificador es el dato clave, porque dice qué obras requieren un desvío obligatorio.

## Endpoint

`GET /api/rutas/conectividad[?redId=1][&sin=90][&sin=12]`, por el gateway y con token de cualquier rol.

| Campo | Contenido |
|---|---|
| `tramosCerrados` | Tramos simulados como cerrados, con sus paradas |
| `componentesConTodoAbierto` / `componentes` | Número de grupos antes y después del cierre |
| `grupos` | Cada grupo con su tamaño, paradas y nombres, del más grande al más pequeño |
| `aisladas` / `aisladasNombres` | Paradas fuera del grupo principal |
| `puentes` | Tramos puente de la red resultante |
| `mensaje` | Resumen en palabras |

- Un tramo inexistente en `sin` responde 400.
- El cálculo de rutas ya aceptaba `?sin=`: `GET /api/rutas/calcular?desde=0&hasta=38&sin=90` responde `costoTotal: -1` con el mensaje *"No existe camino alcanzable entre las paradas indicadas."*

## Pruebas

`mvn test` en `backend/ruta-service` (58 en total; 11 nuevas):
- `ConectividadTest` (6):
  - dos triángulos unidos por un tramo: 1 componente y un puente; al cerrarlo, 2 grupos;
  - tramos repetidos y bucles no cuentan como puente;
  - parada sin tramos;
  - red de 40 paradas: único puente el 90, y al cerrarlo quedan aisladas 37, 38 y 39 con sus nombres;
  - cerrar un tramo que no es puente no aísla nada.
- `ConectividadControllerTest` (5):
  - respuestas de la API con y sin cierre;
  - tramo inexistente;
  - `calcular` con el tramo 90 cerrado.
