# SCRUM-25 [RF-07] · Detección de ciclos y comparativa de memoria

**Criterio de aceptación:** para la red de 40 paradas y 90 tramos, el sistema informa si tiene ciclos y cuáles, y muestra lado a lado la ocupación de memoria de la lista y de la matriz de adyacencia, con una justificación de cuál se usa.

**Resultado:** ✅

## Ciclos

La red **tiene 51 ciclos independientes**: 90 tramos − 40 paradas + 1 componente = **51** (número ciclomático).

**Cómo se encuentran:**
1. Se arma un árbol de recorrido (BFS) que conecta las 40 paradas con 39 tramos.
2. Cada uno de los 51 tramos restantes cierra exactamente un ciclo: el camino por el árbol entre sus dos paradas más el propio tramo.
3. Esos 51 **ciclos fundamentales** forman una base: cualquier otro ciclo de la red se obtiene combinándolos.

| Longitud del ciclo | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Cantidad | 12 | 13 | 8 | 5 | 7 | 2 | 2 | 1 | 1 |

El más corto que se reporta primero es un triángulo: **Estación Central → Terminal de Transportes → Estadio 12 de Octubre**, cerrado por el tramo 4.

Con el tramo 90 cerrado siguen siendo 51 (89 − 40 + 2). Era un puente (SCRUM-24), y un puente nunca forma parte de un ciclo.

## Memoria: lista contra matriz de adyacencia

Enteros de 4 bytes, como `int` en C y en Java:
- **Lista de adyacencia compacta** (la que usa `dijkstra.c`): `inicio_vecinos[V+1]` + `vecino[2E]` + `peso_vecino[2E]` = **4·(V + 1 + 4E)** bytes. Cada tramo aparece dos veces porque se recorre en los dos sentidos.
- **Matriz de adyacencia**: una casilla con el peso por cada par de paradas = **4·V²** bytes.

| Red | Paradas | Tramos | Lista | Matriz | Matriz / lista | Casillas vacías de la matriz |
|---|---:|---:|---:|---:|---:|---:|
| Actual | 40 | 90 | **1.604 B** | **6.400 B** | 4,0× | 88,8 % |
| Ciudad intermedia (W-RQ-02) | 400 | 1.100 | **19.204 B** | **640.000 B** | 33,3× | 99,3 % |

**Por qué se usa la lista:**
- La red es **dispersa**: de los 780 pares posibles de paradas, solo 90 tienen tramo (densidad 11,5 %). La matriz gasta casi toda su memoria en ceros.
- La lista crece con los tramos y la matriz con el cuadrado de las paradas. Para que la matriz ocupara menos, la red de 40 paradas necesitaría **390 tramos o más**, la mitad de todos los pares posibles. Una red vial no se acerca a eso.
- **Lo que se pierde:** con la matriz, la pregunta "¿hay tramo entre A y B?" se responde en un paso; con la lista hay que revisar los vecinos de A (máximo 9 en esta red). Dijkstra, BFS y DFS no hacen esa pregunta: recorren los vecinos de cada parada, y para eso la lista es más rápida.

## Endpoints

Ambos van por el gateway, con token de cualquier rol.
- `GET /api/rutas/ciclos[?redId=1][&sin=90][&limite=10]`: `tieneCiclos`, `numeroCiclos`, la explicación del cálculo y cada ciclo con sus paradas, nombres, longitud y el tramo que lo cierra.
- `GET /api/rutas/memoria[?redId=1][&sin=90]`: bytes de cada representación para la red actual y para la red de 400 paradas, densidad, casillas vacías, punto de cruce y fórmulas.

## Pruebas

`mvn test` en `backend/ruta-service` (69 en total; 11 nuevas):
- `AnalisisCiclosTest` (8):
  - un árbol no tiene ciclos;
  - un cuadrado tiene uno de 4 paradas;
  - dos triángulos separados dan 2 ciclos y 2 componentes;
  - cada ciclo de la red de 40 paradas es un camino cerrado por tramos que existen, sin repetir paradas;
  - 51 ciclos, con y sin el tramo 90;
  - memoria de la red de 40 y de la de 400 paradas.
- `AnalisisControllerTest` (3):
  - respuestas de `/ciclos` (completo y con límite);
  - respuestas de `/memoria`.
