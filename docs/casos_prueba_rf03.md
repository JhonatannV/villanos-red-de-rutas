# Casos de Prueba de Referencia (Oráculo RF-03)

Red utilizada: `backend/grafo-service/data/grafo_40_paradas.json` (40 paradas y 90 tramos).

## Caso 1: Terminal de Transportes (0) -> Coliseo Benicio (11)
- Ruta alternativa A (`0 -> 8 -> 11`): 5 + 9 = 14 min
- Ruta alternativa B (`0 -> 1 -> 9 -> 10 -> 11`): 3 + 4 + 2 + 3 = 12 min
- **Ruta óptima esperada (Dijkstra):** `[0, 9, 10, 11]`
- **Costo total esperado:** `10` minutos (tramos 64, 5 y 6)

## Caso 2: Hospital Tomás Uribe (4) -> Barrio Farfán (22)
- Ruta alternativa (`4 -> 12 -> 20 -> 22`): 5 + 6 + 4 = 15 min
- **Ruta óptima esperada (Dijkstra):** `[4, 5, 13, 21, 22]`
- **Costo total esperado:** `11` minutos (tramos 11, 13, 14 y 15)

## Caso de Contingencia (Cierre de Puente - RF-06)
- Al cerrar el tramo `90` (`Salida Sur [36] <-> Peaje Aeropuerto [37]`), la red pasa de 1 a 2 componentes conexas, dejando aisladas las paradas `37, 38 y 39`.