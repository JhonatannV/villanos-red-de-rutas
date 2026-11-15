# Bitácora de decisiones · Red de rutas

**Equipo Villanos** · Paso 5 de ADD ([`ADD_Diseño_Arquitectura.md`](ADD_Diseño_Arquitectura.md))

Cada entrada dice qué se decidió, qué alternativas se descartaron y por qué, contra qué requisito de calidad se justificó, qué se pierde y cuándo y quién la tomó. *Una decisión sin alternativa evaluada es una decisión que no se tomó.*

Los requisitos de calidad usan los prefijos del documento ADD:
- `W-RQ-0x` para los de Web 2;
- `A-RQ-0x` para los de Arquitectura de Computadores.

| # | Decisión | Estado |
|---|---|---|
| [D-01](#d-01) | El cálculo va en un servicio aparte de los datos | Tomada |
| [D-02](#d-02) | La red cruza a C como tres arreglos paralelos de enteros | Tomada |
| [D-03](#d-03) | Java reserva la salida; C libera solo su memoria de trabajo | Tomada |
| [D-04](#d-04) | C reporta errores con códigos de retorno negativos | Tomada |
| [D-05](#d-05) | Cola de prioridad con montículo binario y eliminación perezosa | Tomada y medida |
| [D-06](#d-06) | A igual costo gana el camino con menos paradas | Tomada |
| [D-07](#d-07) | Respaldo en Java escogido por configuración | Tomada |
| [D-08](#d-08) | El tamaño del arreglo de salida no viaja en la firma | Tomada |
| [D-09](#d-09) | Las entradas se validan antes de elegir implementación | Tomada |
| [D-10](#d-10) | BFS, DFS, conectividad y ciclos se implementan en Java | Tomada |
| [D-11](#d-11) | Los endpoints de algoritmos cuelgan de `/api/rutas` | Tomada |
| [D-12](#d-12) | La red se representa como lista de adyacencia, no como matriz | Tomada y medida |
| — | Abiertas: origen de la traza, carga del `.so`, caché de la red, Swarm o Kubernetes | [Ver al final](#decisiones-abiertas) |

---

### D-01
**El cálculo de rutas va en un servicio aparte de los datos de la red** (`ruta-service` separado de `grafo-service`).

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Todo en `grafo-service`, dos servicios en total. (b) Calcular Dijkstra en el navegador. |
| Por qué se descartaron | Con (a), al caerse el cálculo se cae también el grafo y la pantalla queda en blanco. Con (b) no hay nada que se pueda caer, así que el escenario deja de tener sentido; además, el enunciado prohíbe lógica de grafos en el frontend. |
| Requisito de calidad | **W-RQ-01 Disponibilidad**: apagar el cálculo deja los datos intactos y la pantalla sigue mostrando el grafo. |
| Qué se pierde | Un salto de red más por consulta (`ruta-service` le pide la red a `grafo-service`), que cuenta en contra de W-RQ-03. |
| Fecha y quién | 10 nov 2026 · Equipo (arquitectura inicial del repositorio, Jhonatan). |

### D-02
**La red cruza de Java a C como tres arreglos paralelos de enteros** (`origen[]`, `destino[]`, `peso[]`), no como una lista de estructuras.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Una lista de estructuras `Arista { origen, destino, peso }` mapeada con `com.sun.jna.Structure`. (b) Pasar la red como texto JSON y analizarla en C. |
| Por qué se descartaron | Con (a), JNA tiene que traducir objeto por objeto en cada llamada y el orden y alineación de los campos se vuelven parte del contrato: un error ahí no se ve hasta ejecutar. Con (b), C tendría que analizar JSON, lo que agrega código y tiempo y le quita sentido a bajar el cálculo a C. Un `int[]` de Java, en cambio, se mapea directamente a un `int*` de C. |
| Requisito de calidad | **A-RQ-01 Desempeño**: cruzar la frontera Java↔C cuesta, y una traducción por arista se come la ganancia. |
| Qué se pierde | Firma larga, de 9 parámetros. Los pesos son enteros (minutos), no decimales. Java debe convertir la red a tres arreglos antes de cada llamada. |
| Fecha y quién | 11 nov 2026 · Santiago, sobre el contrato `grafo.h` y `LibGrafo.java` que dejó Jhonatan. Probado de punta a punta con `PruebaContratoJnaTest` (costo 11, camino `[0, 1, 3, 4]`). |

### D-03
**Java reserva los arreglos de salida y C solo los llena.** C pide su propia memoria de trabajo y la libera toda antes de retornar.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) C reserva el camino y expone una función `liberar()` que Java debe llamar en un `try/finally`. (b) C sin ningún `malloc`, usando arreglos en la pila. |
| Por qué se descartaron | Con (a), si Java olvida llamar `liberar()` hay una fuga en el servidor, y ese error no se ve en las pruebas. Con (b), el tamaño de la red no se conoce al compilar: arreglos grandes en la pila pueden desbordarla con redes de miles de paradas. |
| Requisito de calidad | **A-RQ-02 Testabilidad** y seguridad de memoria: `make valgrind` da 111.505 `malloc` y 111.505 `free`, 0 errores (SCRUM-9). |
| Qué se pierde | Java debe saber de antemano cuánto reservar (`num_paradas` para el camino, 1 para el costo), aunque el camino use menos. Cada llamada paga sus propios `malloc` y `free`. |
| Fecha y quién | 11 nov 2026 · Santiago. |

### D-04
**C reporta errores con códigos de retorno negativos** documentados en `grafo.h`.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Usar `errno`. (b) Devolver una estructura de resultado con un campo de error. (c) Abortar el programa ante una entrada inválida. |
| Por qué se descartaron | (a) `errno` es global por hilo y JNA no lo lee de forma natural. (b) Obliga a mapear otra estructura en JNA, con los mismos problemas de D-02. (c) Una entrada mala tumbaría `ruta-service` completo. |
| Requisito de calidad | **A-RQ-02 Testabilidad** (cada código tiene su prueba en `make test`) y **W-RQ-01 Disponibilidad** (un error de entrada no tumba el servicio). |
| Qué se pierde | Java tiene que traducir cada código a una excepción o a una respuesta HTTP. Un código nuevo no documentado sería un fallo del contrato. |
| Códigos | `-1` sin ruta · `-2` parada fuera de rango · `-3` argumento inválido · `-4` sin memoria · `-5` costo desbordado. |
| Fecha y quién | 11 nov 2026 · Santiago. |

### D-05
**La cola de prioridad de Dijkstra es un montículo binario con eliminación perezosa.** Cuando una parada mejora, se mete una entrada nueva y la vieja se ignora al salir.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Búsqueda lineal de la parada más barata, O(V²). (b) Montículo indexado con *decrease-key*. (c) Montículo de Fibonacci. |
| Por qué se descartaron | (a) Crece como el cuadrado de las paradas. Medido en SCRUM-6: con 4.000 paradas es 16 veces más lenta. (b) Necesita un índice extra de posiciones que hay que mantener en cada intercambio: más código y más puntos de error para una ganancia pequeña con estos tamaños. (c) Su ventaja teórica no compensa su complejidad ni sus constantes en redes de cientos de paradas. |
| Requisito de calidad | **A-RQ-01 Desempeño.** Medido: de 50 a 400 paradas los tramos crecen ×8 y el tiempo ×9,9, lo que predice O(E log V). |
| Qué se pierde | El montículo puede tener entradas repetidas, hasta 2 × tramos + 1, lo que usa algo más de memoria. |
| Fecha y quién | 11 nov 2026 · Santiago. Evidencia en `docs/mediciones/SCRUM-6-monticulo.md`. |

### D-06
**A igual costo gana el camino con menos paradas,** tanto en C como en Java.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Devolver el primer camino que encuentre cada implementación. (b) Desempatar por el número de parada más bajo. |
| Por qué se descartaron | (a) C y Java podrían devolver caminos distintos con el mismo costo, y la comparación de RN-06 fallaría sin que ninguno esté mal. (b) Es arbitrario y no significa nada para el usuario. Menos paradas sí: menos transbordos. |
| Requisito de calidad | **A-RQ-02 Testabilidad**: la suite compara el camino completo, no solo el costo, porque ahí aparecen los errores de reconstrucción. |
| Qué se pierde | Una comparación extra por cada actualización (el contador de saltos). |
| Fecha y quién | 11 nov 2026 · Equipo. `DijkstraJava` (Jhonatan) y `dijkstra.c` (Santiago) usan el mismo criterio, con el campo `saltos`. |

### D-07
**`ruta-service` escoge entre C y Java por configuración** (`app.algorithm.mode=native|java`). Si el `.so` no carga, cambia solo a Java.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Solo C. (b) Solo Java, con C como experimento aparte. |
| Por qué se descartaron | Con (a), si la biblioteca falla al cargar en el contenedor, el cálculo de rutas deja de existir. Con (b) no hay forma de medir A-RQ-01 dentro del sistema real. |
| Requisito de calidad | **W-RQ-01 Disponibilidad** (la aplicación sigue en pie sin el nativo) y **A-RQ-01 Desempeño** (la versión Java es la línea base). |
| Qué se pierde | Dos implementaciones que mantener iguales. Lo mitigan los casos de referencia comunes y D-06. |
| Fecha y quién | 11 nov 2026 · Jhonatan (`PuenteRutas.java`). 12 nov 2026 · Santiago: `SelectorAlgoritmo` con `app.algorithm.mode` y cambio a Java también si una llamada nativa falla (SCRUM-15). |

### D-08
**El tamaño del arreglo de salida no viaja en la firma de `dijkstra()`.**

| Campo | Contenido |
|---|---|
| Alternativas descartadas | Agregar un parámetro `tam_camino` para que C devuelva error si el arreglo es corto, como sugiere RN-05 del documento de Arquitectura. |
| Por qué se descartó | La firma ya estaba congelada y probada de punta a punta con Java. Cambiarla obliga a tocar `grafo.h`, `LibGrafo.java`, `PuenteRutas.java` y las pruebas de los dos lados. Además, el riesgo ya está cubierto: el contrato obliga a reservar `num_paradas` posiciones, y un camino nunca puede tener más paradas que la red porque cada parada se cierra una sola vez. |
| Requisito de calidad | **A-RQ-02 Testabilidad** y estabilidad del contrato. La prueba de "arreglos del tamaño exacto" con Valgrind (SCRUM-9) confirma que C nunca escribe una posición de más. |
| Qué se pierde | Si alguien llama a la biblioteca sin respetar el contrato, C no lo puede detectar. |
| Fecha y quién | 12 nov 2026 · Santiago. Por confirmar con Jhonatan. |

### D-09
**Las entradas se validan una sola vez, antes de elegir entre C y Java,** con las reglas de `grafo.h`.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Dejar que cada implementación valide a su manera y traducir sus respuestas. (b) Validar solo en el modo `native`. |
| Por qué se descartaron | `dijkstra.c` distingue parada fuera de rango (`-2`) y argumento inválido (`-3`), mientras que `DijkstraJava` devuelve `-1` en ambos casos. Con (a) o (b), la misma consulta inválida respondería "sin ruta" en un modo y "parada inexistente" en el otro, y el resultado dependería de la configuración. |
| Requisito de calidad | **A-RQ-02 Testabilidad**: las pruebas exigen el mismo resultado en los dos modos. **W-RQ-01 Disponibilidad**: cambiar de C a Java no cambia lo que ve el usuario. |
| Qué se pierde | Las entradas se recorren dos veces en modo `native` (una en Java y otra en C). Con 1.100 tramos es despreciable frente al cálculo. |
| Fecha y quién | 12 nov 2026 · Santiago (SCRUM-15). |

### D-10
**BFS, DFS, componentes conexas y detección de ciclos se implementan en Java dentro de `ruta-service`.** Solo Dijkstra va en C.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Implementarlos también en `libgrafo.so` con las firmas que ya declara `grafo.h` (`recorrido`, `componentes_conexas`). (b) Calcularlos en el frontend. |
| Por qué se descartaron | (a) El documento de Arquitectura solo exige Dijkstra en C; los demás son opcionales. Estos algoritmos son O(V + E) y se llaman una vez por consulta: cruzar la frontera JNA no tiene ganancia que medir, y cada función nativa más amplía el contrato, las pruebas y la revisión con Valgrind. (b) El enunciado prohíbe lógica de grafos en el frontend. |
| Requisito de calidad | **A-RQ-01 Desempeño** (el código nativo se reserva para el cálculo que sí se mide) y **A-RQ-02 Testabilidad** (un solo lenguaje para los algoritmos auxiliares, con sus pruebas JUnit). |
| Qué se pierde | Las declaraciones `recorrido` y `componentes_conexas` de `grafo.h` quedan sin implementación nativa. |
| Fecha y quién | 13 nov 2026 · Santiago (SCRUM-23). |

### D-11
**Los endpoints de los algoritmos cuelgan de `/api/rutas`** (`/api/rutas/recorrido`, `/api/rutas/conectividad`, `/api/rutas/ciclos`, `/api/rutas/memoria`), y no de `/api/redes/{id}/...`.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) `/api/redes/{id}/recorrido` y `/api/redes/{id}/conectividad`, como en la planeación inicial. (b) Una regla especial en el gateway para desviar esas rutas a `ruta-service`. |
| Por qué se descartaron | (a) El gateway envía todo `/api/redes/**` a `grafo-service`, que no tiene los algoritmos: esas rutas responderían 404. (b) Funciona, pero mezcla en un mismo prefijo dos servicios con dueños y disponibilidad distintos, y obliga a mantener una excepción en el gateway. |
| Requisito de calidad | **W-RQ-01 Disponibilidad**: el prefijo decide qué servicio responde. Si `ruta-service` cae, todo `/api/rutas/**` responde 503 y `/api/redes/**` sigue sirviendo el mapa. |
| Qué se pierde | El identificador de la red viaja como parámetro (`redId`, por defecto 1) en vez de ir en la ruta. |
| Fecha y quién | 13 nov 2026 · Santiago (SCRUM-23). |

### D-12
La red se representa como lista de adyacencia compacta**, tanto en `dijkstra.c` como en los algoritmos de `ruta-service`. No se usa matriz de adyacencia.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | Matriz de adyacencia V × V con el peso de cada tramo. |
| Por qué se descartó | La red es dispersa: 90 tramos de 780 pares posibles (11,5 %). La matriz ocupa 4·V² bytes y la lista 4·(V + 1 + 4E): **6.400 B contra 1.604 B** con 40 paradas, y **640.000 B contra 19.204 B** con 400 paradas y 1.100 tramos. La matriz solo ocuparía menos con 390 tramos o más en la red de 40 paradas. Además, Dijkstra, BFS y DFS recorren los vecinos de cada parada: con la lista eso cuesta su grado, y con la matriz cuesta V por parada, lo que lleva Dijkstra hacia O(V²). |
| Requisito de calidad | **A-RQ-01 y W-RQ-03 Desempeño**: tiempo por consulta en la red de 400 paradas (medido en SCRUM-6: 30 µs con lista y montículo). |
| Qué se pierde | Saber si existe un tramo entre dos paradas pasa de un paso (matriz) a revisar los vecinos de una de ellas (máximo 9 en la red actual). |
| Fecha y quién | 13 nov 2026 · Santiago (SCRUM-25). Cálculo en `GET /api/rutas/memoria`. |

### D-13
**Visualizador del Grafo en SVG Declarativo con Señales de Angular y Desplazamiento Vectorial Anticolisión** (`GrafoVisorComponent`), en lugar de HTML5 Canvas imperativo o bibliotecas externas pesadas (D3.js / Cytoscape).

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) HTML5 `<canvas>` con loop `requestAnimationFrame` imperativo. (b) Bibliotecas de grafos pesadas externas como Cytoscape.js o Vis.js. |
| Por qué se descartaron | (a) Canvas rasteriza y pierde nitidez al hacer zoom; no se integra de forma reactiva con Angular Signals y requiere redibujar manualmente todo el contexto gráfico en cada evento de ratón. (b) Cytoscape y Vis.js agregan dependencias externas voluminosas (>500KB) que complican la gestión de bundles y desentonan con la arquitectura moderna de Signals de Angular 17+. |
| Requisito de calidad | **RF-02 y A-RQ-02 Usabilidad**: SVG ofrece escalabilidad vectorial infinita, nitidez matemática en cualquier nivel de zoom, y el algoritmo de dispersión perpendicular/vectorial garantiza separación $\ge 14\text{px}$ entre todas las etiquetas de peso en redes densas. |
| Qué se pierde | Con grafos de más de 10.000 nodos el DOM SVG puede tener mayor costo en memoria que un canvas rasterizado, pero la red de transporte del proyecto opera en el orden de decenas a centenas de nodos, donde SVG es significativamente más eficiente y declarativo. |
| Fecha y quién | 14 nov 2026 · Jhonatan (SCRUM-30). |

### D-14
**Optimización de Cómputo Espacial mediante Spatial Hash Grid con Complejidad Lineal $O(M)$ para Renderizado de Red Masiva (400 Paradas y 1.100 Tramos) en Angular SVG**, en lugar de búsqueda cuadrática exhaustiva $O(M^2)$.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | Búsqueda exhaustiva por pares $\sum_{i=1}^{M} i = \frac{M(M-1)}{2} \approx 604.450$ comparaciones ($O(M^2)$) para resolución de proximidad de etiquetas. |
| Por qué se descartó | El requisito **W-RQ-02** exige taxativamente **crecimiento lineal en tramos** y **ninguna tarea del hilo principal superior a 100 ms**. Al escalar de 90 a 1.100 tramos, un algoritmo $O(M^2)$ escalaría su tiempo $\approx 150\times$, provocando caídas de cuadros (*jank*) y degradación perceptible en la experiencia de usuario. |
| Requisito de calidad | **W-RQ-02 Desempeño**: La indexación espacial por celdas fijas de $24\text{px}$ acota la búsqueda a las 9 celdas adyacentes ($O(1)$ por tramo), reduciendo el cómputo de 1.100 tramos a **1.27 ms** (escala lineal $10.96\times$ idéntica al incremento de tramos $12.22\times$), dibujo inicial en **702 ms** ($< 2.000\text{ ms}$) y microtareas en hilo principal en **36.5 ms** ($< 100\text{ ms}$). |
| Qué se pierde | Se requiere una estructura de datos `Map<string, EtiquetaPeso[]>` adicional en memoria durante el cálculo de la señal computada, la cual se recolecta inmediatamente al finalizar la evaluación reactiva. |
| Fecha y quién | 15 nov 2026 · Jhonatan (SCRUM-35). |

---

## Decisiones abiertas

Se registran aquí para no perderlas. Cada una pasa a una entrada D-xx cuando se tome.

| Tema | Opciones | Requisito que la decide | Tarea |
|---|---|---|---|
| **De dónde sale la traza paso a paso** (RF-04) | (a) La calcula Java aunque el camino lo dé C. (b) Una segunda función en C que llene también la traza. | W-RQ-03: pedir la traza no puede duplicar el tiempo. A-RQ-01. | SCRUM-14/15 |
| **Cómo se carga `libgrafo.so` en el contenedor** | `jna.library.path` (ya usado en pruebas, SCRUM-15), `LD_LIBRARY_PATH` o `Native.load` con ruta absoluta | A-RQ-03 Facilidad de despliegue | SCRUM-16 |
| **Red en caché o pedida en cada consulta** | Pedirla a `grafo-service` siempre, o guardarla en `ruta-service` e invalidarla cuando cambie | W-RQ-03 Desempeño contra correctitud | SCRUM-14 |
| **Orquestador** | Docker Swarm o Kubernetes | A-RQ-03 | SCRUM-38 |
| **Dónde se valida la autorización** | Solo en el gateway, o en cada servicio | Seguridad | SCRUM-20/21 |
