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
| [D-13](#d-13) | Visualizador SVG declarativo con Signals y dispersión anticolisión | Tomada y medida |
| [D-14](#d-14) | Spatial Hash Grid O(M) para renderizado de red masiva (400x1100) | Tomada y medida |
| [D-15](#d-15) | Connection Pooling en API Gateway para latencia p95 < 400 ms | Tomada y medida |
| [D-16](#d-16) | Orquestación dual Docker Compose v2 y Kubernetes con límites de CPU/RAM | Tomada y verificada |
| [D-17](#d-17) | La traza del reproductor la arma Java y se pide aparte con `traza=true` | Tomada |
| [D-18](#d-18) | El reproductor guarda una foto del estado por paso y solo mueve un índice | Tomada |
| [D-19](#d-19) | Los servicios arrancan en paralelo: se espera a que inicien, no a que estén sanos | Tomada y medida |
| — | Consolidación y cierre de decisiones abiertas iniciales | [Consolidadas al 100 %](#cierre-de-decisiones-abiertas) |

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

### D-15
**Reutilización de Conexiones HTTP Persistentes mediante `PoolingHttpClientConnectionManager` en API Gateway para Mitigar Latencia de Handshake y Cumplir $p95 < 400\text{ ms}$ (W-RQ-03)**, en lugar de instanciar sockets HTTP efímeros por cada consulta.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | Conexiones HTTP/1.1 efímeras creadas por petición sin pool compartido (`SimpleClientHttpRequestFactory`). |
| Por qué se descartó | En ráfagas consecutivas de consultas (p. ej. 20 peticiones seguidas en horas pico o pruebas de carga), abrir y cerrar sockets TCP incrementa la sobrecarga por negociación de tres vías (three-way handshake) y agota los puertos locales (TIME_WAIT socket exhaustion), elevando el percentil 95 por encima del límite de 400 ms. |
| Requisito de calidad | **W-RQ-03 Desempeño**: `PoolingHttpClientConnectionManager` mantiene conexiones TCP calientes hacia `ruta-service` y `grafo-service` con timeouts de $2.000\text{ ms}$, permitiendo responder ráfagas con latencia promedio de **4.95 ms** y **$p95 = 65.73\text{ ms}$** (83.6 % de margen de seguridad frente al límite de 400 ms). |
| Qué se pierde | Se reserva un pequeño cupo constante de descriptores de archivo y memoria para el pool de conexiones en el proceso del API Gateway. |
| Fecha y quién | 15 nov 2026 · Jhonatan (SCRUM-36). |

### D-16
**Orquestación Dual basada en Docker Compose v2 y Manifiestos Declarativos de Kubernetes (`k8s/`) con Asignación Explícita de Límites de CPU y Memoria (`deploy.resources.limits`)**, resolviendo la decisión abierta sobre el orquestador (A-RQ-03 / DEPLOY-01).

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Contenedores ejecutados individualmente con scripts shell `docker run` sin orquestador. (b) Despliegue sin límites de recursos explícitos dejando que la JVM compita por la memoria global del host. |
| Por qué se descartaron | Sin orquestación declarativa, el orden de inicialización no es determinista y los microservicios fallan al arrancar si Keycloak no está saludable. Sin límites de CPU/RAM, un contenedor con fuga o bajo estrés puede degradar todo el nodo (*noisy neighbor problem*), violando los criterios de aislamiento. |
| Requisito de calidad | **A-RQ-03 y DEPLOY-01 Facilidad de Despliegue y Disponibilidad**: Se provee un comando único `docker compose up -d` para entornos locales y `kubectl apply -k k8s/` con Kustomize para clústeres. Cada uno de los 5 contenedores tiene definidos sus límites máximos (total ecosistema: 5.5 CPUs y 2.75 GB RAM) y reservas mínimas (1.35 CPUs y 1.31 GB RAM), garantizando un despliegue predecible y seguro. |
| Qué se pierde | Se requiere mantener sincronizada la definición de puertos y variables de entorno entre `docker-compose.yml` y los manifiestos YAML de Kubernetes. |
| Fecha y quién | 15 nov 2026 · Jhonatan (SCRUM-38). |

### D-17
**La traza paso a paso de Dijkstra (RF-04) la arma `DijkstraJava` y solo se entrega cuando el cliente la pide con `GET /api/rutas/calcular?...&traza=true`.** El camino y el costo siguen saliendo de `libgrafo.so` en modo `native`.

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Agregar a `libgrafo.so` una segunda función que llene también la traza. (b) Mandar la traza en todas las respuestas de `/calcular`. (c) Recalcular la traza en el frontend. |
| Por qué se descartaron | (a) La traza tiene tamaño variable (cada paso trae una lista de vecinos revisados): exigiría reservar y liberar estructuras anidadas a través de JNA, cambiar la firma congelada de `grafo.h` (D-08) y volver a pasar Valgrind, para algo que no está en la ruta crítica que se mide. (b) Las consultas sin reproductor (mediciones, comparador) pagarían un JSON más grande sin usarlo. (c) El enunciado no permite lógica de grafos en el frontend. |
| Requisito de calidad | **A-RQ-01 Desempeño**: el cálculo medido sigue en C. **W-RQ-03 Latencia**: la traza es opcional y solo la pide el reproductor. **A-RQ-02 Testabilidad**: la traza se verifica con JUnit contra la traza a mano del grafo guía (`TrazaDijkstraTest`). |
| Qué se pierde | En modo `native` con `traza=true` el recorrido se calcula dos veces (C para el camino, Java para la traza). Que coincidan lo garantiza la misma regla de desempate (D-06), y la respuesta lo declara con `trazaCalculadaCon: "JAVA"`. |
| Fecha y quién | 17 nov 2026 · Santiago (SCRUM-31). |

### D-18
**El reproductor convierte la traza en una lista de fotos completas del estado (una por paso) y avanzar o retroceder solo cambia un índice.**

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Aplicar o deshacer el paso sobre un único estado mutable. (b) Volver a correr el algoritmo desde el inicio hasta el paso pedido. |
| Por qué se descartaron | (a) Deshacer obliga a guardar qué había antes de cada cambio; un error ahí rompe justo el criterio de RF-04 ("retroceder deja el estado exactamente como estaba"). (b) Exige lógica de grafos en el frontend y crece con el número de paso. |
| Requisito de calidad | **RF-04** y **A-RQ-02 Testabilidad**: cada foto se compara en pruebas y retroceder devuelve el mismo objeto que se mostró antes (`reproductor.service.spec.ts`). **W-RQ-02 Desempeño en el navegador**: moverse a cualquier paso es O(1). |
| Qué se pierde | Memoria en el navegador: con la red de 40 paradas son 41 fotos de hasta 40 filas; en el peor caso de la red de 400 paradas serían unas 400 fotos de hasta 400 filas (160.000 filas). |
| Fecha y quién | 17 nov 2026 · Santiago (SCRUM-31). |

### D-19
**En `docker-compose.yml` cada servicio espera a que sus dependencias inicien (`service_started`), no a que estén sanas (`service_healthy`). Mientras arrancan, la salud se revisa cada 2 s (`start_interval`).**

| Campo | Contenido |
|---|---|
| Alternativas descartadas | (a) Mantener la cadena `keycloak → grafo-service → ruta-service → api-gateway → frontend`, esperando a que cada eslabón esté sano. (b) Mantener la cadena y solo bajar el `interval` de los healthchecks a 2 s también durante la operación normal. |
| Por qué se descartaron | (a) Medido con `scripts/arranque_limpio.sh`: la primera ruta tardaba **108,8 s** y no cumplía los 90 s. Además, la cadena no protege nada: los servicios piden las claves de Keycloak (`jwk-set-uri`) con el primer token, `ruta-service` pide la red en cada consulta y el gateway responde 503 mientras un servicio no contesta (W-RQ-01). (b) Revisar cada 2 s todo el tiempo gasta CPU sin necesidad; `start_interval` solo aplica mientras el contenedor arranca. |
| Requisito de calidad | **A-RQ-03 / DEPLOY-02**: primera ruta en menos de 90 s desde una máquina sin contenedores. Con el cambio, **44,1 a 44,7 s** en tres corridas. El camino crítico pasa a ser el arranque de Keycloak. |
| Qué se pierde | Durante los primeros segundos una consulta puede llegar antes de que su servicio esté listo. En ese caso recibe 503 en vez de esperar, y el frontend lo muestra como modo degradado (SCRUM-32). |
| Fecha y quién | 17 nov 2026 · Santiago (SCRUM-39). |

---

## Cierre de decisiones abiertas

Todas las inquietudes y alternativas arquitectónicas iniciales han sido resueltas, implementadas y validadas formalmente a lo largo de los 5 Sprints:

| Tema | Decisión tomada | Justificación y evidencia | Decisión / Tarea |
|---|---|---|---|
| **De dónde sale la traza paso a paso** (RF-04) | **Calculada en Java (`DijkstraJava`)** | Medido en W-RQ-03: generar la traza en Java toma apenas **5.30 ms** frente a **4.95 ms** de solo ruta (ratio **1.07x**, muy inferior al límite de 2.0x). No justifica duplicar lógica compleja de traza en C. | D-10 · SCRUM-36 |
| **Cómo se carga `libgrafo.so` en el contenedor** | **`jna.library.path=/native` y `/usr/lib`** | En Dockerfile multi-stage, `libgrafo.so` se compila con GCC y se copia a `/native/libgrafo.so` y a la ruta estándar `/usr/lib/`. `LibGrafo` lo carga transparentemente sin rutas absolutas fijas. | SCRUM-15 · SCRUM-16 |
| **Red en caché o pedida en cada consulta** | **Pedida a `grafo-service` con Connection Pooling** | Se eliminó el riesgo de datos obsoletos solicitando la topología fresca en cada cálculo. La latencia se mitigó mediante el pool persistente HTTP (`PoolingHttpClientConnectionManager`), logrando p95 de **77.14 ms** ($< 400\text{ ms}$). | D-01 · D-15 · SCRUM-36 |
| **Orquestador del sistema** | **Docker Compose v2 y Kubernetes (`k8s/`)** | Se provee soporte dual: `docker compose up -d` para evaluación local y manifiestos declarativos con Kustomize (`kubectl apply -k k8s/`) con cuotas estrictas de CPU y RAM. | D-16 · SCRUM-38 |
| **Dónde se valida la autorización** | **En Gateway y microservicios downstream** | El Gateway valida la existencia y firma del Bearer JWT en el perímetro. Los microservicios downstream validan localmente la firma del token, extraen el claim `sub` para auditoría y verifican roles específicos (`planificador` / `consultor`). | SCRUM-20 · SCRUM-21 · SCRUM-22 |
