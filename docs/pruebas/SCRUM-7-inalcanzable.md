# SCRUM-7 [RN-03] · Destino inalcanzable

**Criterio de aceptación:** si no existe camino, `dijkstra()` devuelve `-1` sin escribir en los arreglos de salida y sin quedarse en un ciclo. Con un grafo partido en dos componentes, la consulta entre componentes devuelve `-1` y la función retorna en el mismo orden de tiempo que una consulta normal.

**Resultado:** ✅ 13 de 13 pruebas pasan (`make test`, 0,2 s). Salida completa: [`scrum7_salida.txt`](scrum7_salida.txt).

## Qué se prueba

| Caso | Esperado |
|---|---|
| Grafo partido en dos (0-1 y 2-3), en los dos sentidos | `-1` |
| Parada sin ningún tramo, como destino y como origen | `-1` |
| Red con paradas pero sin tramos | `-1` |
| Isla con un ciclo (0-1-2-0) y destino en otra isla | `-1` (no da vueltas sin fin) |
| Red de 40 paradas, todo abierto: los dos casos de `casos_prueba_rf03.md` y Terminal → Terminal Aérea | camino y costo exactos (10, 11 y 43 min) |
| Red de 40 paradas con el **tramo 90 cerrado** (Salida Sur ↔ Peaje Aeropuerto): Terminal → Terminal Aérea y Zona Rural → Salida Sur | `-1` |
| Misma red con el tramo 90 cerrado, dentro de la isla aislada: Peaje → Zona Rural | sí hay ruta (37 → 39, 7 min) |

En cada caso que devuelve `-1`, la prueba llena antes `camino_salida` y `costo_salida` con un valor centinela (`-999`) y comprueba que **sigan intactos** después de la llamada. También comprueba en todos los casos que nada se escriba más allá de `num_paradas` posiciones.

## "En el mismo orden de tiempo"

Red de 400 paradas partida en dos islas de 200 (1.100 tramos). Se miden 1.000 consultas de cada tipo y se reporta la mediana de 5 corridas:

| Consulta | Tiempo por consulta |
|---|---:|
| Con ruta (origen y destino en la misma isla) | 17,7 µs |
| Sin ruta (destino en la otra isla) | 28,2 µs |

Sin ruta tarda **1,6 veces** una consulta normal: el mismo orden. Tiene sentido: cuando el destino no se puede alcanzar, Dijkstra cierra todas las paradas de la isla del origen, la cola se vacía y la función termina. Con ruta, se detiene antes, apenas cierra el destino. La prueba falla si la consulta sin ruta tarda 5 veces o más.

## Por qué no puede quedarse en un ciclo

Cada parada se cierra **una sola vez** y nunca se vuelve a abrir. Cada tramo puede meter como máximo una entrada nueva en el montículo. Por eso el ciclo principal da como mucho `2 × tramos + 1` vueltas, aunque la red tenga ciclos o esté partida.

## Cómo repetirlo

```bash
cd backend/ruta-service/native
make test
```
