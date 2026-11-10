# ruta-service (Spring Boot + JNA + C Nativo)
**Responsable:** Santiago Ríos Morales

## Propósito
Microservicio dueño de los algoritmos de cálculo.
- No guarda la red: la solicita a `grafo-service` y la procesa en memoria.
- Invoca la biblioteca nativa en C `libgrafo.so` mediante interfaz JNA `LibGrafo`.
- Cuenta con respaldo y línea base en Java puro conmutada por configuración (`app.algorithm.mode=native|java`).
- Implementa endpoints de:
  - Ruta de menor peso (Dijkstra) con traza de nodos cerrados paso a paso.
  - Recorridos BFS y DFS.
  - Componentes conexas con simulación de cierre de tramos (`?sin=tramoId`).
  - Detección de ciclos y comparativa de memoria (lista vs. matriz de adyacencia).
