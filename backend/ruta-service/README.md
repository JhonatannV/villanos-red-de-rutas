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

## Selección del algoritmo (SCRUM-15)
`SelectorAlgoritmo` decide qué implementación de Dijkstra responde las consultas:

| `app.algorithm.mode` | Comportamiento |
|---|---|
| `native` (por defecto) | Usa `libgrafo.so` (C) por JNA. Si la biblioteca no carga, o una llamada falla con `UnsatisfiedLinkError`, cambia a Java y lo registra en el log. |
| `java` | Usa `DijkstraJava` y no intenta cargar la biblioteca. |

- En el servicio, `AlgoritmoConfig` crea el selector con la propiedad de Spring `app.algorithm.mode`, que en `application.yml` toma la variable de entorno `ALGORITHM_MODE` (por defecto `native`). Un valor distinto de `native` o `java` detiene el arranque con un mensaje claro.
- En Docker: `ALGORITHM_MODE=java docker compose up -d ruta-service` cambia de implementación sin tocar archivos.
- JNA busca la biblioteca en `jna.library.path` (`libgrafo.so` en Linux, `grafo.dll` en Windows).
- Las entradas se validan antes de elegir implementación, con las reglas de `native/grafo.h`. Así una entrada inválida produce el mismo `IllegalArgumentException` en los dos modos.
- Códigos de C:
  - `-1`: sin ruta (`costoTotal = -1`, camino vacío);
  - `-4`: sin memoria; la consulta se repite en Java;
  - `-5`: `ArithmeticException`;
  - cualquier código no documentado: `IllegalStateException`.
- `GET /actuator/info` publica el modo pedido, el activo y el motivo, por ejemplo: `{"algoritmo":{"modoPedido":"native","modoEfectivo":"native","motivo":"biblioteca nativa cargada"}}`. La respuesta de `/api/rutas/calcular` incluye `modoEfectivo`.

Pruebas: `mvn test`. Las que necesitan la biblioteca real se omiten si no está compilada en `native/`:
- en Windows: `gcc -O2 -shared -o native/grafo.dll native/dijkstra.c`;
- en Linux: `make -C native libgrafo.so`.

Con la biblioteca presente, C y Java se comparan en las 1.600 parejas de la red de 40 paradas.
