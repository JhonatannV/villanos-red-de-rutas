# SCRUM-23 [RF-05] · Recorridos BFS y DFS desde una parada

**Criterio de aceptación:** sobre el mismo nodo inicial, BFS y DFS producen órdenes de visita distintos y demostrables en consola o API.

**Resultado:** ✅ desde Terminal de Transportes (parada 0) los dos recorridos visitan las 40 paradas, pero en orden distinto desde la tercera visita.

| Visita | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| **BFS** (anchura) | 0 | 1 | **8** | 9 | 2 | 10 | 11 | 16 | 17 | 18 |
| **DFS** (profundidad) | 0 | 1 | **2** | 3 | 4 | 5 | 6 | 7 | 14 | 13 |

- **BFS** avanza por niveles: primero las paradas a 1 tramo de Terminal (Estación Central, C.C. Herradura, Estadio 12 de Octubre), luego las que están a 2. El nivel máximo es 7.
- **DFS** se mete lo más hondo posible por un camino: Terminal → Estación Central → Parque Boyacá → UCEVA → Hospital → Plaza Cívica → … Solo se devuelve cuando no quedan vecinos sin visitar.

## Endpoint

`GET /api/rutas/recorrido?desde=0&modo=anchura|profundidad[&redId=1][&sin=90]`, por el gateway y con token de cualquier rol.

```json
{
  "redId": 1, "desde": 0, "modo": "anchura", "tramosCerrados": [], "visitadas": 40,
  "orden": [0, 1, 8, 9, 2, ...],
  "ordenNombres": ["Terminal de Transportes", "Estación Central", "C.C. Herradura", ...],
  "pasos": [
    { "visita": 1, "parada": 0, "nombre": "Terminal de Transportes", "desde": -1,
      "nivel": 0, "descubiertas": [1, 8, 9], "cola": [1, 8, 9] },
    ...
  ]
}
```

- Cada **paso** dice qué parada se visitó, desde cuál se llegó, su nivel (BFS) o profundidad (DFS), qué vecinos se descubrieron y cómo quedó la cola o la pila. Es lo que el reproductor del frontend necesita para animar el recorrido con el mismo control que Dijkstra (RF-04).
- Con `sin=90` el recorrido respeta el tramo cerrado y el campo `mensaje` informa cuántas paradas no se alcanzaron (37 de 40 visitadas).
- Errores con HTTP 400 y mensaje:
  - una parada inexistente (`desde=99`);
  - un modo desconocido.

  El mismo manejo se aplica ahora a `/api/rutas/calcular`, que antes respondía 500 ante una parada inexistente.

## Detalles de implementación

- `algoritmo/Recorridos`:
  - **BFS** con cola: una parada se marca al descubrirla, así entra a la cola una sola vez.
  - **DFS** iterativo con pila: los vecinos se apilan en orden inverso para visitar primero el de menor número, igual que la versión recursiva.
  - En ambos, los vecinos se recorren en orden ascendente de número de parada, de modo que el resultado es determinista y verificable a mano.
- `red/ClienteRed` y `red/Red`: la lectura de la red desde `grafo-service`, con el token reenviado, se extrajo de `RutaController` para compartirla entre Dijkstra, los recorridos y los análisis de SCRUM-24 y SCRUM-25.

## Pruebas

`mvn test` en `backend/ruta-service` (47 en total; 12 nuevas):
- `RecorridosTest` (6):
  - red de 5 paradas calculada a mano: BFS `0,1,2,3,4` con niveles `0,1,1,2,3`; DFS `0,1,3,2,4`;
  - en la red de 40 paradas ambos visitan las 40 sin repetir y en orden distinto;
  - los niveles de BFS nunca bajan;
  - con el tramo 90 cerrado se alcanzan 37 paradas, y desde la zona aislada solo `38, 37, 39`;
  - modos y paradas inválidos.
- `RecorridoControllerTest` (6):
  - respuestas de la API en los dos modos;
  - tramo cerrado;
  - 400 por parada o modo inválidos (también en `/calcular`);
  - 401 sin token.
