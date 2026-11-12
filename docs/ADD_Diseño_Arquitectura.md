# Diseño de arquitectura con ADD · Red de rutas

**Equipo Villanos** · Jhonatan Varón Henao · Santiago Ríos Morales
**Materias:** Programación en Ambiente Web 2 · Arquitectura de Computadores

ADD (*Attribute-Driven Design*) deriva la arquitectura de los atributos de calidad: primero se fija qué debe cumplir el sistema, y cada decisión se justifica contra eso. El entregable son cinco pasos:

| Paso | Contenido | Estado |
|---|---|---|
| 1 | **Drivers**: propósito, requisitos funcionales, restricciones y atributos de calidad | ✅ Este documento (SCRUM-13) |
| 2 | Escenarios de calidad de seis partes | ⏳ Base en la sección 1.4; se completa con las mediciones |
| 3 | Descomposición en servicios | ⏳ |
| 4 | Interfaces y contratos (incluido qué pasa cuando el de al lado no responde) | ⏳ El contrato JNA ya está en `backend/ruta-service/native/grafo.h` |
| 5 | Bitácora de decisiones | ✅ Abierta en [`Bitacora_Decisiones.md`](Bitacora_Decisiones.md) |

La consolidación final de los cinco pasos es SCRUM-40.

---

## Paso 1 · Drivers

### 1.1 Propósito del sistema

Modelar la red de transporte de una ciudad como un **grafo ponderado**: las paradas son nodos, los tramos son aristas y su peso son minutos. Sobre ese grafo, el sistema responde tres preguntas que un planificador urbano se hace todo el tiempo:

1. **¿Cuál es el camino más barato entre dos paradas?** (Dijkstra)
2. **Si se cierra un tramo, ¿qué paradas quedan incomunicadas y cuánto se alarga la ruta?** (componentes conexas, cierre con `?sin=tramoId`)
3. **¿Cómo llegó el algoritmo a esa respuesta?** El sistema reproduce el recorrido paso a paso: qué parada cerró primero, qué distancias tentativas tenía y por qué descartó otros caminos.

Lo tercero es lo que lo diferencia de una aplicación de mapas, que entrega la ruta pero no el razonamiento. No compite con una aplicación comercial: la red la declara quien la conoce, y el resultado se puede auditar y defender.

**Usuarios:** el **planificador** mantiene la red (crea, edita y elimina paradas y tramos); el **consultor** consulta la red y calcula rutas.

### 1.2 Requisitos funcionales principales

| ID | Requisito | Criterio de aceptación (resumen) |
|---|---|---|
| RF-01 | Cargar y editar la red | Una red de 40 paradas y 90 tramos se guarda y reaparece idéntica al reabrir |
| RF-02 | Ver el grafo dibujado, con zoom y paneo | Con 40 paradas, ninguna etiqueta de peso queda encima de otra |
| RF-03 | Ruta de menor peso | Camino y costo iguales al cálculo a mano; si no hay camino, lo dice con palabras |
| RF-04 | Reproducción paso a paso | Avanzar, retroceder, pausar y velocidad; retroceder deja el estado exacto |
| RF-05 | Recorridos BFS y DFS | Desde la misma parada dan órdenes de visita distintos |
| RF-06 | Conectividad y cierre de tramos | Al cerrar un tramo puente, las componentes pasan de 1 a 2 y se nombran las paradas aisladas |
| RF-07 | Ciclos y representación usada | Memoria de lista vs. matriz de adyacencia para 40 paradas y 90 tramos |
| RF-08 | Comparar dos rutas *(opcional)* | Rutas superpuestas en colores distintos y diferencia de costo |
| RF-09 | Exportar la corrida *(opcional)* | El JSON exportado se recarga y reproduce igual, sin recalcular |

**Componente nativo** (Arquitectura de Computadores): RN-01 Dijkstra con cola de prioridad en C; RN-02 montículo binario; RN-03 destino inalcanzable; RN-04 casos degenerados; RN-05 sin escribir fuera de los arreglos de salida; RN-06 línea base en Java.

**Identidad** (Web 2): RA-01 a RA-07. Keycloak con `rutas-realm`, PKCE en Angular, token en un interceptor, cada servicio valida el JWT, autorización por rol (401/403), auditoría con el claim `sub`, y expiración y cierre de sesión.

### 1.3 Restricciones (no se negocian)

| Restricción | De dónde sale |
|---|---|
| Frontend en **Angular** con componentes *standalone* y *signals*; no contiene lógica de grafos ni habla con la base de datos | Enunciado general |
| Backend en **Spring Boot**, mínimo **tres microservicios** con responsabilidades separadas | Enunciado general |
| **Kubernetes o Docker Swarm**: todo se levanta con un manifiesto y un comando, sin arrancar servicios a mano | Enunciado general |
| Identidad con **Keycloak**, OAuth 2.0 y OIDC; ningún endpoint abierto | Enunciado general |
| **Biblioteca nativa en C** obligatoria: Dijkstra en `libgrafo.so`, invocada desde Java con **JNA** | Enunciado general y documento de Arquitectura |
| La aplicación debe funcionar **sin la biblioteca nativa**: versión en Java como línea base y respaldo, escogida por configuración | Documento Red de rutas |
| Debe arrancar en una **máquina distinta** a la de desarrollo | Documento Red de rutas |
| Fuera del alcance: mapas geográficos reales, horarios, GPS, transbordos, A\* y Bellman-Ford | Documentos Red de rutas y Núcleo |

### 1.4 Atributos de calidad

Los nombres son los de Len Bass, *Software Architecture in Practice*. Cada documento del profesor numera sus requisitos desde RQ-01, así que aquí van con prefijo para no confundirlos.

**Programación en Ambiente Web 2**

| ID | Atributo | Escenario en una frase | Medida |
|---|---|---|---|
| **W-RQ-01** · asignado | **Disponibilidad** | `ruta-service` se cae mientras alguien consulta la red | Contenido útil en < 3 s; el gateway corta a 2 s con 503; el 100 % de las peticiones a `/rutas` responde con error explicado; vuelve a funcionar en < 30 s sin recargar |
| W-RQ-02 | Desempeño | La red crece de 40 a 400 paradas y 1.100 tramos | Dibujo inicial < 2 s; crecimiento lineal en tramos; ninguna tarea del hilo principal > 100 ms |
| W-RQ-03 | Desempeño | Un usuario pide la ruta más corta en la red grande | p95 < 400 ms en 20 peticiones medidas desde el gateway; pedir la traza no duplica el tiempo |

**Arquitectura de Computadores**

| ID | Atributo | Escenario en una frase | Medida |
|---|---|---|---|
| **A-RQ-01** · asignado | **Desempeño** | Mil rutas en una red de 400 paradas y 1.100 tramos, primero en C y luego en Java | Mismo camino y costo en las mil; mediana de 5 corridas y relación C/Java; p95 por consulta < 400 ms |
| A-RQ-02 | Testabilidad | Alguien cambia el algoritmo y quiere saber si sigue bien | Un comando compila y corre la suite en < 30 s, sin el sistema desplegado, con al menos 10 grafos |
| A-RQ-03 | Facilidad de despliegue | Se despliega desde cero en una máquina limpia | Imagen < 250 MB sin `gcc`; primera ruta correcta en < 90 s; tamaño y arranque medidos antes y después de la biblioteca |

**Avance a la fecha (12 nov 2026):**

- **A-RQ-02 ya se cumple:**
  - `make test` corre 38 pruebas en 0,3 s, sin Java ni Docker, e incluye el grafo partido en dos y los casos degenerados;
  - `make valgrind` da 0 errores y 0 fugas.
  - Ver `docs/pruebas/`.
- **El montículo de RN-02 está medido.** Ver `docs/mediciones/SCRUM-6-monticulo.md`.
- **Pendiente:** A-RQ-01, la medición C vs. Java (SCRUM-37).

### 1.5 Preocupaciones arquitectónicas

Son decisiones que aparecen temprano y deben quedar escritas antes de que se vuelvan problemas:

- **Dónde se parte el sistema.** Los datos de la red y el cálculo van en servicios distintos, para que W-RQ-01 se pueda cumplir.
- **El contrato entre Java y C.** Firma, quién reserva y quién libera la memoria, cómo se reporta un error y los casos de referencia. Se congela en la primera semana.
- **De dónde sale la traza paso a paso** que necesita RF-04. Hoy la produce la versión en Java; la de C devuelve solo camino y costo.
- **Cómo se carga el `.so` dentro del contenedor:** `jna.library.path`, `LD_LIBRARY_PATH` o ruta absoluta. Es la causa más común de que funcione en el portátil y no en el contenedor.
- **Si `ruta-service` pide la red a `grafo-service` en cada consulta o la guarda en caché.** Lo primero es siempre correcto; lo segundo favorece W-RQ-03.
- **Lista o matriz de adyacencia** dentro de `ruta-service` (RF-07).
- **Dónde se valida la autorización:** solo en el gateway o en cada servicio.

Las que ya se tomaron están en la [bitácora](Bitacora_Decisiones.md). Las demás aparecen allí como abiertas.
