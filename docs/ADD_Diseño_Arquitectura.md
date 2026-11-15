# Documento de Diseño de Arquitectura (ADD) · Red de Rutas

**Proyecto Integrador** · Semestre VI  
**Universidad Central del Valle del Cauca (UCEVA)**  
**Materias:** Programación en Ambiente Web 2 · Arquitectura de Computadores  
**Equipo Villanos:**  
- **Jhonatan Varón Henao** (`jhonatan.varon01@uceva.edu.co`) — *DevSecOps, Ciberseguridad & Backend de Datos*  
- **Santiago Ríos Morales** (`santiago.rios01@uceva.edu.co`) — *Cómputo de Alto Rendimiento, Algoritmos & Visualización*  

---

## Metodología y Estructura del Documento

El presente documento consolida el diseño arquitectónico del sistema **Red de Rutas** aplicando la metodología **ADD (*Attribute-Driven Design*)** en sus cinco pasos canónicos (Bass, Clements & Kazman). En ADD, la arquitectura se deriva rigurosamente a partir de los **atributos de calidad** y las **restricciones de diseño**, garantizando que cada decisión técnica, patrón y táctica esté formalmente justificada contra un requerimiento verificable:

| Paso ADD | Nombre del Paso | Alcance y Contenido en este Documento |
|:---:|---|---|
| **Paso 1** | **Revisar Entradas (*Review Inputs*)** | Propósito del sistema, requisitos funcionales primarios, restricciones técnicas obligatorias, escenarios de atributos de calidad formalizados en 6 partes y preocupaciones arquitectónicas tempranas. |
| **Paso 2** | **Establecer Objetivos de Iteración y Seleccionar Elementos** | Planificación de las 5 iteraciones de diseño arquitectónico y asignación de drivers prioritarios a cada elemento macro del sistema. |
| **Paso 3** | **Identificar Conceptos de Diseño (Tácticas y Patrones)** | Catálogo de tácticas arquitectónicas (Disponibilidad, Desempeño, Seguridad, Usabilidad, Testabilidad, Desplegabilidad) y selección de patrones estructurales. |
| **Paso 4** | **Instanciar Elementos y Asignar Responsabilidades** | Descomposición en microservicios, visualizador SPA en Angular con Signals, biblioteca nativa en C con JNA, servidor IdP Keycloak y orquestación con límites de recursos. |
| **Paso 5** | **Definir Interfaces y Registrar Decisiones** | Contratos OpenAPI/REST, contrato binario JNA en C, contrato de tokens JWT/OIDC, matriz completa de decisiones (D-01 a D-16) y tabla de mediciones empíricas consolidadas. |

---

## Paso 1 · Revisar Entradas (*Review Inputs*)

### 1.1 Propósito del Sistema

El sistema **Red de Rutas** es una plataforma distribuida diseñada para modelar, gestionar y analizar redes de transporte urbano representadas formalmente como **grafos ponderados dirigidos y no dirigidos**, donde los nodos son **paradas** georreferenciadas en un plano cartesiano bidimensional $(x, y)$, las aristas representan **tramos viales** y los pesos modelan tiempos de viaje en minutos.

A diferencia de un visor de mapas comercial de caja negra, el propósito central de este sistema es permitir la **auditoría algorítmica y la planificación de contingencias**:
1. **Cálculo de Ruta Óptima:** Determinar la trayectoria de menor costo en tiempo entre cualquier par de paradas mediante el algoritmo de Dijkstra.
2. **Simulación de Contingencias Viales:** Analizar el impacto de cerrar uno o varios tramos (`?sin=tramoId`), identificando la partición de la red en múltiples componentes conexas y reportando las paradas aisladas.
3. **Reproducción Algorítmica Paso a Paso:** Explicar visual e interactivamente el proceso de inferencia de Dijkstra (nodos en exploración, nodos cerrados, actualización de distancias tentativas y selección guiada por cola de prioridad), permitiendo avanzar, retroceder y pausar sin desincronización de estado.
4. **Análisis Estructural y de Memoria:** Inspeccionar recorridos en anchura (BFS) y profundidad (DFS), detección de ciclos viales y contraste de huella de memoria entre lista de adyacencia y matriz de adyacencia.

**Actores del Sistema:**
- **Planificador:** Rol administrativo (`planificador`) facultado para crear, editar, reubicar paradas y tramos, y activar/desactivar vías.
- **Consultor:** Rol operativo (`consultor`) facultado para consultar la topología de la red, simular rutas óptimas, reproducir la traza paso a paso y evaluar cierres viales.

---

### 1.2 Requisitos Funcionales Primarios

| ID | Nombre | Descripción y Criterio de Aceptación |
|---|---|---|
| **RF-01** | Gestión y Persistencia de Red | Crear, editar y persistir redes de transporte con paradas $(x,y)$ y tramos ponderados. La topología (ej. red de 40 paradas y 90 tramos) se almacena y recarga de forma idéntica y consistente. |
| **RF-02** | Visualización Interactiva del Grafo | Representación gráfica con zoom y paneo interactivo. En redes densas (40 paradas), las etiquetas de pesos de los tramos no se solapan entre sí (separación garantizada $\ge 14\text{px}$). |
| **RF-03** | Ruta de Menor Costo (Dijkstra) | Cálculo determinista del camino mínimo. Si el destino es inalcanzable, el sistema responde explícitamente sin fallos ni bloqueos. |
| **RF-04** | Reproducción Paso a Paso | Control de reproducción temporal (play, pausa, paso adelante, paso atrás, velocidad regulable 0.5x a 2.0x). Retroceder un paso restaura el estado exacto del algoritmo. |
| **RF-05** | Recorridos BFS y DFS | Ejecución de exploraciones en anchura y profundidad sobre el grafo, retornando secuencias de visita diferenciadas para análisis de conectividad. |
| **RF-06** | Análisis de Conectividad y Cierre | Simulación de cierre de tramos (`?sin=tramoId`). Si se cierra un puente vial, se detecta el incremento de componentes conexas y se listan las paradas incomunicadas. |
| **RF-07** | Detección de Ciclos y Huella de Memoria | Identificación de ciclos en la red vial y reporte comparativo del consumo de memoria en bytes entre lista de adyacencia y matriz de adyacencia. |
| **RF-08** | Comparación de Rutas | Superposición simultánea de dos caminos sobre el visualizador para contrastar alternativas y diferencias de costo temporal. |
| **RF-09** | Exportación de Corridas | Exportación e importación de simulaciones algorítmicas en formato JSON estandarizado para auditorías reproducibles. |

**Requisitos Específicos del Componente Nativo en C (Arquitectura de Computadores):**
- **RN-01:** Implementación de Dijkstra en C con cola de prioridad.
- **RN-02:** Cola de prioridad basada en montículo binario de mínimos (*min-heap*).
- **RN-03:** Manejo explícito y seguro de destinos inalcanzables (código `-1`).
- **RN-04:** Manejo riguroso de casos degenerados (paradas aisladas, lazos, nodos fuera de rango, punteros nulos).
- **RN-05:** Inmunidad a corrupción de memoria: la biblioteca nunca escribe fuera de los límites de los arreglos de salida reservados por el invocador.
- **RN-06:** Implementación equivalente de línea base en Java para contraste de desempeño y respaldo de contingencia.

**Requisitos Específicos de Identidad y Ciberseguridad (Programación en Ambiente Web 2):**
- **RA-01 a RA-07:** Servidor Keycloak centralizado (`rutas-realm`), flujo OAuth 2.0 Authorization Code con PKCE en Angular SPA, inyección automática de tokens JWT mediante `HttpInterceptor`, validación criptográfica en API Gateway y microservicios, autorización basada en roles (RBAC 401/403), registro de auditoría persistente indexado por el claim `sub` y revocación/expiración segura de sesiones.

---

### 1.3 Restricciones de Diseño e Implementación (No Negociables)

| ID | Restricción | Justificación / Origen |
|---|---|---|
| **CON-01** | **Frontend Angular 17+ Standalone con Signals** | La aplicación cliente debe construirse en Angular utilizando componentes independientes (*standalone*) y estado reactivo basado exclusivamente en *Signals*, sin lógica de grafos ni acceso directo a bases de datos. |
| **CON-02** | **Arquitectura de Microservicios en Spring Boot** | Backend modular compuesto por al menos tres servicios desacoplados e independientes: API Gateway, servicio de datos de red y servicio de cálculo algorítmico. |
| **CON-03** | **Orquestación en Contenedores (Docker / Kubernetes)** | Despliegue automatizado ejecutable con un único comando (`docker compose up -d` o `kubectl apply -k k8s/`), sin pasos manuales de configuración. |
| **CON-04** | **Seguridad OIDC/OAuth 2.0 con Keycloak** | Ningún endpoint administrativo o de cálculo debe quedar expuesto sin autenticación; todo tráfico protegido exige tokens Bearer JWT válidos. |
| **CON-05** | **Biblioteca Nativa en C (`libgrafo.so`) con JNA** | El núcleo de cálculo de Dijkstra debe ejecutarse en C99 compilado como biblioteca compartida e invocarse desde Java mediante *Java Native Access* (JNA) sin JNI manual. |
| **CON-06** | **Línea Base en Java y Conmutación Transparente** | El sistema debe operar idénticamente sin la biblioteca nativa, seleccionando entre C y Java por configuración (`app.algorithm.mode=native|java`) y conmutando automáticamente ante fallos del binario. |
| **CON-07** | **Portabilidad y Despliegue en Máquina Limpia** | El sistema debe construirse y ejecutarse limpiamente en cualquier host x86_64 sin dependencias previas de desarrollo (ej. sin requerir GCC o Node.js preinstalados en el host). |
| **CON-08** | **Límites de Alcance Algorítmico** | Quedan explícitamente fuera del alcance algoritmos con pesos negativos (Bellman-Ford), heurísticas geoespaciales con coordenadas esféricas (A\*) y ruteo multivariado de transporte público en tiempo real. |

---

### 1.4 Escenarios de Atributos de Calidad (Formato de 6 Partes)

Siguiendo la metodología formal de Bass et al., cada escenario se estructura en:
*(1) Fuente del estímulo, (2) Estímulo, (3) Artefacto afectado, (4) Entorno, (5) Respuesta del sistema, (6) Medida de respuesta.*

#### Escenarios de Programación en Ambiente Web 2

##### 1. W-RQ-01 · Disponibilidad y Resiliencia Perimetral
- **Fuente:** Usuario final interactuando desde la SPA Angular.
- **Estímulo:** Petición de cálculo de ruta `/api/rutas` mientras el microservicio de cálculo (`ruta-service`) colapsa o está apagado.
- **Artefacto:** API Gateway (`api-gateway`) y SPA Angular (`red-rutas-web`).
- **Entorno:** Operación normal con caída súbita del contenedor downstream.
- **Respuesta:** El API Gateway interrumpe la espera aplicando un timeout estricto de 2 segundos, activa el manejador de contingencia y retorna HTTP 503 Service Unavailable con mensaje JSON estructurado. El frontend no se bloquea, mantiene el mapa y las paradas interactivas y notifica al usuario con un toast de contingencia. Al restaurarse el servicio, las consultas se reanudan en menos de 30 segundos sin refrescar la página.
- **Medida:** Tiempo de corte $\le 2.0\text{ s}$; 100 % de las peticiones a `/api/rutas` responden con 503 estructurado; disponibilidad de `/api/redes/**` al 100 %; recuperación sin refresh en $< 30\text{ s}$.

##### 2. W-RQ-02 · Desempeño en Renderizado de Red Masiva
- **Fuente:** Planificador cargando un mapa de transporte de escala urbana.
- **Estímulo:** Carga y renderizado inicial de una red densa de **400 paradas y 1.100 tramos**.
- **Artefacto:** Componente visualizador del grafo (`GrafoVisorComponent`) en el navegador.
- **Entorno:** Renderizado en cliente web sobre hardware estándar.
- **Respuesta:** La interfaz proyecta los 1.500 elementos gráficos vectoriales en SVG, ejecutando la resolución espacial de etiquetas de peso mediante una cuadrícula de hash espacial (*Spatial Hash Grid*) con complejidad lineal $O(M)$.
- **Medida:** Tiempo de dibujo inicial $< 2.000\text{ ms}$ (medido: **702.81 ms**); crecimiento en tiempo estrictamente lineal con respecto al número de tramos; ninguna tarea en el hilo principal de JavaScript supera los $100\text{ ms}$ (medido: **36.51 ms** max).

##### 3. W-RQ-03 · Desempeño y Latencia del API Gateway
- **Fuente:** Múltiples clientes concurrentes o ráfaga de consultas.
- **Estímulo:** Ráfaga de **20 consultas consecutivas** de ruta óptima sobre la red grande (400 paradas, 1.100 tramos) canalizadas a través del API Gateway.
- **Artefacto:** API Gateway y `ruta-service`.
- **Entorno:** Sistema desplegado en contenedores con reutilización de conexiones TCP.
- **Respuesta:** El Gateway reenvía las peticiones a través de su pool de conexiones HTTP persistentes (`PoolingHttpClientConnectionManager`), evitando handshakes TCP redundantes.
- **Medida:** Percentil 95 ($p95$) de latencia de consulta $< 400\text{ ms}$ (medido: **77.14 ms**); solicitar la traza detallada paso a paso incrementa el tiempo en un ratio $< 2.0\times$ (medido: **1.07x**); tasa de éxito del 100 % (20/20 HTTP 200).

---

#### Escenarios de Arquitectura de Computadores

##### 4. A-RQ-01 · Desempeño Comparativo C vs. Java
- **Fuente:** Banco de pruebas automatizado de evaluación algorítmica.
- **Estímulo:** Ejecución de 1.000 consultas de ruta más corta sobre la red de 400 paradas y 1.100 tramos, evaluando secuencialmente el motor nativo en C (`libgrafo.so`) y la línea base en Java.
- **Artefacto:** `libgrafo.so` y `DijkstraJava` en `ruta-service`.
- **Entorno:** Proceso JVM en contenedor Docker x86_64 con JNA.
- **Respuesta:** Ambos motores retornan exactamente el mismo camino y costo en las 1.000 ejecuciones gracias a la regla unificada de desempate por menor número de saltos.
- **Medida:** Paridad de resultados al 100 %; mediana de latencia en C inferior a Java; latencia por consulta $p95 < 400\text{ ms}$.

##### 5. A-RQ-02 · Testabilidad y Sanidad de Memoria en C
- **Fuente:** Desarrollador o pipeline de CI/CD ejecutando suite de verificación.
- **Estímulo:** Ejecución del comando de pruebas unitarias sobre el código fuente en C.
- **Artefacto:** Código C en `backend/ruta-service/native/` (`dijkstra.c`, `test_dijkstra.c`).
- **Entorno:** Entorno local aislado sin necesidad de compilar Java ni levantar Docker.
- **Respuesta:** La suite compila con GCC y ejecuta automáticamente pruebas exhaustivas sobre grafos normales, grafos disconexos, lazos, nodos aislados y desbordamientos.
- **Medida:** Ejecución completa de al menos 10 grafos en $< 30\text{ s}$ (medido: **38 pruebas en 0.30 s** con `make test`); análisis de Valgrind con 0 errores y 0 fugas de memoria (**0 leaks, 0 errors** en 111.505 allocs/frees).

##### 6. A-RQ-03 / DEPLOY-01 · Desplegabilidad y Contención de Recursos
- **Fuente:** Administrador de infraestructura o evaluador técnico.
- **Estímulo:** Despliegue completo desde cero en un servidor limpio mediante `docker compose up -d` o `kubectl apply -k k8s/`.
- **Artefacto:** Manifiestos de despliegue (`docker-compose.yml`, `k8s/`) e imágenes Docker.
- **Entorno:** Host con Docker Engine / Kubernetes sin artefactos preexistentes.
- **Respuesta:** El orquestador levanta los 5 servicios en orden determinista respetando healthchecks (`keycloak` -> microservicios -> frontend), asignando límites estrictos de CPU y memoria (`deploy.resources.limits`).
- **Medida:** Primera consulta de ruta exitosa en $< 90\text{ s}$; tamaño de imagen frontend $< 30\text{ MB}$; consumo máximo del ecosistema acotado a 5.5 CPUs y 2.75 GB RAM.

---

### 1.5 Preocupaciones Arquitectónicas (*Architectural Concerns*)

1. **Partición Funcional y Aislamiento de Fallos (CRN-01):** Separar físicamente la persistencia de paradas/tramos (`grafo-service`) del cómputo intensivo (`ruta-service`) para garantizar que un bloqueo en el cálculo no afecte la disponibilidad de la red urbana.
2. **Contrato de Interoperabilidad FFI C-Java (CRN-02):** Diseñar una frontera de bajo acoplamiento entre Java y C que no penalice el rendimiento por serialización y prevenga fugas de memoria en la JVM.
3. **Generación Determinista de la Traza Algorítmica (CRN-03):** Resolver de qué capa emerge la traza paso a paso sin introducir penalizaciones de latencia inaceptables en C.
4. **Carga Portátil de Bibliotecas Compartidas (CRN-04):** Garantizar la resolución dinámica de `libgrafo.so` en entornos Linux en contenedor mediante variables estándar (`jna.library.path`).
5. **Consistencia de Datos vs. Rendimiento de Red (CRN-05):** Mitigar la sobrecarga de consultar la topología de red en cada cálculo de ruta mediante pools de conexiones persistentes, evitando riesgos de caché desactualizada.
6. **Perímetro de Seguridad vs. Verificación Interna (CRN-06):** Establecer validación perimetral en el Gateway combinada con verificación de identidad (`sub`) y roles en cada microservicio.

---

## Paso 2 · Establecer Objetivos de Iteración y Seleccionar Elementos

El diseño del sistema se estructuró en cinco iteraciones guiadas por drivers arquitectónicos prioritarios:

```
[Iteración 1: Microservicios y Resiliencia]  --> Driver: W-RQ-01, CON-02
                     |
[Iteración 2: Cómputo C-JNA y Algoritmos]    --> Driver: A-RQ-01, A-RQ-02, CON-05, CON-06
                     |
[Iteración 3: Seguridad OIDC y Auditoría]     --> Driver: RA-01 a RA-07, CON-04
                     |
[Iteración 4: Interfaz Reactiva y Visor SVG] --> Driver: W-RQ-02, RF-02, RF-04, CON-01
                     |
[Iteración 5: Orquestación, Pooling y Límites]--> Driver: W-RQ-03, A-RQ-03/DEPLOY-01, CON-03
```

### Detalle de las Iteraciones de Diseño

1. **Iteración 1 · Estructuración Distribuida y Resiliencia Perimetral:**
   - *Objetivo:* Crear la topología de microservicios, desacoplando datos y cálculo bajo un API Gateway con circuit breaking perimetral.
   - *Elementos seleccionados:* `api-gateway`, `grafo-service`, `ruta-service`.
   - *Drivers atendidos:* **W-RQ-01** (Disponibilidad), **CON-02** (Tres microservicios Spring Boot), **CRN-01** (Aislamiento de fallos).

2. **Iteración 2 · Núcleo Nativo de Alto Rendimiento e Interoperabilidad:**
   - *Objetivo:* Construir la biblioteca compartida en C99 para Dijkstra con montículo binario y definir el puente JNA con respaldo en Java.
   - *Elementos seleccionados:* `libgrafo.so`, `PuenteRutas`, `SelectorAlgoritmo`, `DijkstraJava`.
   - *Drivers atendidos:* **A-RQ-01** (Desempeño C), **A-RQ-02** (Testabilidad C), **CON-05** (JNA), **CON-06** (Línea base Java).

3. **Iteración 3 · Identidad Centralizada, Control de Acceso y Auditoría:**
   - *Objetivo:* Proteger todos los servicios con Keycloak OIDC, autorizar por roles (`planificador`/`consultor`) y registrar trazas de auditoría.
   - *Elementos seleccionados:* Keycloak Realm `rutas-realm`, Spring Security Resource Servers, `AuditoriaRegistro` con claim `sub`.
   - *Drivers atendidos:* **RA-01 a RA-07** (Seguridad integral), **CON-04** (Keycloak obligatorio).

4. **Iteración 4 · Experiencia de Usuario Reactiva y Renderizado a Escala:**
   - *Objetivo:* Desarrollar la SPA en Angular 17+ con Signals y el visualizador gráfico SVG con algoritmo Spatial Hash Grid $O(M)$ anticolisión.
   - *Elementos seleccionados:* `GrafoVisorComponent`, `RedGestorComponent`, `RutaSimuladorComponent`.
   - *Drivers atendidos:* **W-RQ-02** (Renderizado red grande en $< 2\text{ s}$), **RF-02** (Etiquetas legibles), **RF-04** (Paso a paso), **CON-01** (Angular Standalone/Signals).

5. **Iteración 5 · Orquestación, Contención de Recursos y Verificación de Servicio:**
   - *Objetivo:* Empaquetar todo el ecosistema con Docker y Kubernetes, fijar cuotas de CPU/RAM y optimizar la latencia con Connection Pooling.
   - *Elementos seleccionados:* `docker-compose.yml`, manifiestos `k8s/`, Nginx en frontend, `PoolingHttpClientConnectionManager` en Gateway.
   - *Drivers atendidos:* **W-RQ-03** ($p95 < 400\text{ ms}$), **A-RQ-03 / DEPLOY-01** (Despliegue automatizado y contención), **CON-03** (Orquestador).

---

## Paso 3 · Identificar Conceptos de Diseño (Tácticas y Patrones)

Para satisfacer los escenarios de calidad, se aplicaron formalmente las siguientes **tácticas arquitectónicas de software**:

```
                         TÁCTICAS ARQUITECTÓNICAS (ADD)
 ┌─────────────────┬─────────────────┬─────────────────┬─────────────────┐
 │  Disponibilidad │    Desempeño    │    Seguridad    │  Desplegabilidad│
 ├─────────────────┼─────────────────┼─────────────────┼─────────────────┤
 │ • Fallback      │ • Marshalling   │ • IdP Central   │ • Multi-stage   │
 │   Transparente  │   Plano (int[]) │   (Keycloak)    │   Dockerfiles   │
 │ • Timeout       │ • Heap Binario  │ • PKCE en SPA   │ • Cuotas CPU/RAM│
 │   Estricto (2s) │   O(E log V)    │ • RBAC (Roles)  │   en Compose/K8s│
 │ • Circuit       │ • Spatial Grid  │ • Auditoría     │ • Suite C       │
 │   Breaker 503   │   O(M) en SVG   │   con claim sub │   Autónoma      │
 │ • Separación de │ • Connection    │ • Tokens JWT    │ • Healthchecks  │
 │   Prefijos HTTP │   Pooling HTTP  │   firmados RS256│   en Cascada    │
 └─────────────────┴─────────────────┴─────────────────┴─────────────────┘
```

### 3.1 Tácticas de Disponibilidad (*Availability*)
- **Degradación Elegante por Partición de Prefijos:** El Gateway divide el tráfico en `/api/redes/**` (`grafo-service`) y `/api/rutas/**` (`ruta-service`). Si el servicio de cálculo falla, el módulo de topología permanece 100 % operativo (Decisión [D-11](Bitacora_Decisiones.md#d-11)).
- **Tiempo Límite Estricto y Respuesta de Contingencia (*Timeout & Fallback*):** El Gateway impone un corte a los $2.000\text{ ms}$. Si `ruta-service` no responde, retorna de inmediato HTTP 503 con un cuerpo JSON de contingencia, impidiendo la retención indefinida de hilos (Decisión [D-01](Bitacora_Decisiones.md#d-01)).
- **Redundancia Activa Algorítmica (*Active Redundancy*):** En caso de indisponibilidad física de `libgrafo.so` o fallo en tiempo de ejecución de la biblioteca nativa, el componente `SelectorAlgoritmo` conmuta de forma transparente al motor `DijkstraJava`, preservando la continuidad del servicio (Decisión [D-07](Bitacora_Decisiones.md#d-07)).
- **Sondeo de Salud en Cascada (*Ping/Echo & Heartbeat*):** Uso de endpoints `/actuator/health` y directivas `healthcheck` en Docker/Kubernetes para verificar la viabilidad operativa antes de enrutar tráfico de usuarios.

### 3.2 Tácticas de Desempeño (*Performance*)
- **Minimización de Sobrecarga de Serialización (*Reduce Marshalling Overhead*):** En la frontera Java↔C, los grafos no se transfieren como objetos de alto nivel ni como texto JSON, sino como tres arreglos contiguos de enteros primitivos (`int[] origen`, `int[] destino`, `int[] peso`), permitiendo un mapeo directo a punteros `int*` de C sin costo de conversión (Decisión [D-02](Bitacora_Decisiones.md#d-02)).
- **Algoritmos y Estructuras Óptimas:**
  - *Montículo Binario con Eliminación Perezosa:* Dijkstra opera en $O(E \log V)$ usando un min-heap plano en C, reduciendo drásticamente el tiempo frente a la búsqueda lineal $O(V^2)$ (Decisión [D-05](Bitacora_Decisiones.md#d-05)).
  - *Lista de Adyacencia Compacta:* Almacenamiento en arreglos planos indexados por nodo, ocupando solo $19.2\text{ KB}$ en la red de 400 nodos frente a los $640\text{ KB}$ que exigiría una matriz de adyacencia (Decisión [D-12](Bitacora_Decisiones.md#d-12)).
- **Partición Espacial Lineal en Frontend (*Spatial Hash Grid*):** En `GrafoVisorComponent`, la resolución de colisiones entre etiquetas de peso se realiza indexando el plano bidimensional en celdas de $24\text{px}$. Esto reduce la complejidad de $O(M^2)$ a $O(M)$, procesando 1.100 tramos en **1.27 ms** sin congelar el hilo principal del navegador (Decisión [D-14](Bitacora_Decisiones.md#d-14)).
- **Reutilización de Recursos de Red (*Connection Pooling*):** Implementación de `PoolingHttpClientConnectionManager` en el API Gateway, manteniendo sockets TCP abiertos hacia los servicios internos y mitigando la latencia de handshake de tres vías en ráfagas consecutivas (Decisión [D-15](Bitacora_Decisiones.md#d-15)).

### 3.3 Tácticas de Seguridad (*Security*)
- **Servidor de Identidad Centralizado:** Delegación de autenticación a Keycloak mediante protocolo estándar OpenID Connect (OIDC).
- **Flujo Seguro en Clientes Públicos (OAuth 2.0 con PKCE):** La SPA en Angular implementa *Proof Key for Code Exchange*, eliminando la necesidad de almacenar secretos de cliente en el navegador y mitigando ataques de interceptación de código de autorización.
- **Tokens Criptográficos Autónomos (Stateless JWT):** Uso de tokens firmados mediante algoritmo asimétrico RS256, verificados en memoria por los microservicios mediante la clave pública del Realm.
- **Control de Acceso Basado en Roles (RBAC):** Restricción granular en controladores mediante `@PreAuthorize("hasRole('planificador')")` para mutaciones de red y `@PreAuthorize("hasAnyRole('planificador','consultor')")` para consultas.
- **Registro de Auditoría No Repudiable:** Cada creación, edición o eliminación de elementos de la red registra en la entidad `AuditoriaRegistro` la marca de tiempo, la acción y el identificador universal inmutable `sub` extraído del token JWT (SCRUM-22).

### 3.4 Tácticas de Usabilidad (*Usability*)
- **Línea Temporal Algorítmica Reversible:** Para la reproducción paso a paso (RF-04), el motor en Java genera una traza de eventos ordenada cronológicamente. La SPA almacena la secuencia en un arreglo inmutable y avanza/retrocede un puntero de índice, garantizando que retroceder un paso restaure exactamente el estado visual previo sin recálculos.
- **Renderizado Vectorial Escalable e Inmunidad a Colisiones:** Empleo de SVG nativo enriquecido con un algoritmo de desplazamiento vectorial perpendicular que garantiza una separación mínima de $14\text{px}$ entre pesos adyacentes, manteniendo nitidez perfecta en cualquier nivel de zoom (Decisión [D-13](Bitacora_Decisiones.md#d-13)).

### 3.5 Tácticas de Desplegabilidad y Testabilidad (*Deployability & Testability*)
- **Contenedores de Tamaño Mínimo (*Multi-Stage Builds*):**
  - Frontend construido con Node.js 20 y servido mediante Nginx 1.27 Alpine, generando una imagen final ultraligera de solo **28.4 MB**.
  - Microservicios empaquetados sobre imágenes Eclipse Temurin JRE 17 headless, compilando `libgrafo.so` en la primera etapa y descartando el compilador GCC en la imagen de producción.
- **Aislamiento de Cuotas (*Resource Quotas*):** Directivas explícitas de `limits` y `reservations` para CPU y memoria en `docker-compose.yml` y manifiestos de Kubernetes, previniendo problemas de contención de recursos en el host (Decisión [D-16](Bitacora_Decisiones.md#d-16)).
- **Pruebas Nativas Autónomas:** Aislamiento de la suite en C mediante un arnés independiente en `test_dijkstra.c`, ejecutable en milisegundos sin arrancar la JVM ni contenedores.

---

## Paso 4 · Instanciar Elementos y Asignar Responsabilidades

### 4.1 Descomposición en Subsistemas y Componentes

```text
                                  +-----------------------+
                                  |   Keycloak (OAuth2)   |
                                  |   Realm: rutas-realm  |
                                  +-----------+-----------+
                                              |
                          +-------------------+-------------------+
                          | (OIDC Flow: Auth Code + PKCE / JWT)   |
                          v                                       v
                   +---------------+                       +-------------+
                   |  red-rutas-web|                       | api-gateway |
                   |  (Angular 17) +---------------------->+ (Port 8080) |
                   +---------------+                       +------+------+
                                                                  |
                                          +-----------------------+-----------------------+
                                          |                                               |
                                          v (HTTP /api/redes/**)                          v (HTTP /api/rutas/**)
                               +--------------------+                          +--------------------+
                               |   grafo-service    |                          |    ruta-service    |
                               |    (Port 8081)     |                          |    (Port 8082)     |
                               |  Paradas, Tramos   |                          |   Dijkstra, BFS,   |
                               |    y Auditoría     |                          |   DFS, Conexidad   |
                               +--------------------+                          +---------+----------+
                                                                                         |
                                                                                         v (JNA FFI)
                                                                               +--------------------+
                                                                               |    libgrafo.so     |
                                                                               |  (C99 Native Core) |
                                                                               +--------------------+
```

### 4.2 Catálogo de Componentes y Asignación de Responsabilidades

| Componente | Tecnologías Clave | Responsabilidades Primarias |
|---|---|---|
| **`api-gateway`** | Spring Cloud Gateway, WebFlux, Netty, Apache HttpClient | Puerta de entrada única al backend (puerto 8080). Enrutamiento inverso, balanceo de carga, circuit breaking perimetral (timeout estricto 2s con fallback 503), Connection Pooling persistente hacia servicios downstream y validación preliminar de tokens JWT. |
| **`grafo-service`** | Spring Boot 3, Spring Data JPA, H2 In-Memory / Seed JSON | Gestión de topología vial (puerto 8081). Persistencia CRUD de paradas $(x,y)$ y tramos ponderados. Validación de roles de escritura (`planificador`). Registro de auditoría persistente indexado por el claim `sub` de Keycloak. |
| **`ruta-service`** | Spring Boot 3, JNA 5.14, Spring Security | Motor de cálculo y orquestación algorítmica (puerto 8082). Consulta la red a `grafo-service`, selecciona dinámicamente entre `libgrafo.so` y `DijkstraJava` (`SelectorAlgoritmo`), calcula la traza paso a paso, recorridos BFS/DFS, componentes conexas, ciclos y balance de memoria. |
| **`libgrafo.so`** | C99, GCC, Make | Biblioteca nativa compartida de alto rendimiento. Implementa el algoritmo de Dijkstra con cola de prioridad basada en montículo binario y eliminación perezosa. Opera estrictamente sobre memoria de trabajo propia, garantiza 0 fugas de memoria y devuelve códigos de error negativos. |
| **`red-rutas-web`** | Angular 17, Signals, Standalone Components, Nginx | Interfaz SPA de usuario (puerto 4200 / 80). Autenticación PKCE, interceptor de cabeceras Bearer, visualizador SVG de grafos con Spatial Hash Grid $O(M)$, control de animación temporal paso a paso, selector de red 40/400 y panel de administración vial. |
| **`keycloak`** | Keycloak 24+, Quarkus runtime | Servidor de Identidad y Acceso (puerto 8085). Gestiona el realm `rutas-realm`, los clientes `frontend-rutas` (público con PKCE) y `backend-rutas` (bearer-only), los roles `planificador` y `consultor`, y la emisión de tokens JWT RS256. |
| **Orquestador** | Docker Compose v2, Kubernetes (K8s) | Despliegue automatizado, aprovisionamiento de red virtual común (`villanos-net`), ordenamiento determinista de arranque mediante healthchecks y aplicación estricta de límites de CPU y memoria. |

---

## Paso 5 · Definir Interfaces y Registrar Decisiones

### 5.1 Contratos e Interfaces REST / OpenAPI

#### Microservicio `grafo-service` (Prefijo: `/api/redes`)
- `GET /api/redes/{id}`: Retorna la red completa con sus paradas y tramos.
- `GET /api/redes/{id}/paradas`: Consulta el listado de paradas registradas.
- `POST /api/redes/{id}/paradas`: Crea una nueva parada. *Exige rol `planificador`*.
- `PUT /api/redes/{id}/paradas/{paradaId}`: Actualiza coordenadas de una parada. *Exige rol `planificador`*.
- `DELETE /api/redes/{id}/paradas/{paradaId}`: Elimina una parada y sus tramos adyacentes. *Exige rol `planificador`*.
- `GET /api/redes/{id}/tramos`: Consulta los tramos viales.
- `POST /api/redes/{id}/tramos`: Crea un nuevo tramo vial ponderado. *Exige rol `planificador`*.
- `DELETE /api/redes/{id}/tramos/{tramoId}`: Elimina un tramo vial. *Exige rol `planificador`*.
- `GET /api/redes/auditoria`: Consulta la bitácora de auditoría con los registros `sub`. *Exige rol `planificador`*.

#### Microservicio `ruta-service` (Prefijo: `/api/rutas`)
- `GET /api/rutas?origen={id}&destino={id}&redId={id}&sin={tramoId}&traza={bool}`: Calcula el camino más corto entre dos paradas, con soporte opcional de exclusión vial y traza algorítmica paso a paso.
- `GET /api/rutas/recorrido?tipo={BFS|DFS}&inicio={id}&redId={id}`: Ejecuta recorridos BFS o DFS retornando el orden secuencial de nodos.
- `GET /api/rutas/conectividad?redId={id}&sin={tramoId}`: Reporta el número de componentes conexas y la lista de paradas aisladas tras un cierre vial.
- `GET /api/rutas/ciclos?redId={id}`: Detecta la existencia de ciclos en la red.
- `GET /api/rutas/memoria?redId={id}`: Retorna el cálculo analítico de bytes consumidos por lista de adyacencia vs. matriz de adyacencia.

---

### 5.2 Contrato Binario e Interoperabilidad JNA en C (`grafo.h`)

El contrato formal entre Java y la biblioteca nativa en C99 se define en `backend/ruta-service/native/grafo.h`:

```c
int dijkstra(
    int num_paradas,      /* Total de vértices en el grafo */
    int num_tramos,       /* Total de aristas dirigidas */
    const int* origen,    /* Arreglo plano de orígenes [num_tramos] */
    const int* destino,   /* Arreglo plano de destinos [num_tramos] */
    const int* peso,      /* Arreglo plano de costos en minutos [num_tramos] */
    int origen_id,        /* ID del nodo inicial */
    int destino_id,       /* ID del nodo objetivo */
    int* camino,          /* Arreglo de salida reservado por Java [num_paradas] */
    int* costo            /* Entero de salida reservado por Java para el peso total */
);
```

**Reglas de Interoperabilidad:**
1. **Convención de Memoria (D-03):** Java reserva y pasa las referencias a `camino` y `costo`. C utiliza memoria dinámica propia para el montículo y las listas de adyacencia, y libera la totalidad de sus bloques antes de retornar.
2. **Códigos de Retorno Negativos (D-04):**
   - Retorno $\ge 0$: Éxito (retorna el número de paradas en el camino óptimo).
   - `-1`: Destino inalcanzable (no existe camino viable).
   - `-2`: Parada fuera de rango ($< 0$ o $\ge \text{num\_paradas}$).
   - `-3`: Argumento inválido (punteros nulos, tramos negativos o pesos negativos).
   - `-4`: Error de asignación de memoria dinámica (`malloc` fallido).
   - `-5`: Desbordamiento de costo numérico.
3. **Criterio de Desempate Unificado (D-06):** A igual costo en minutos, gana el camino con menor cantidad de saltos (paradas intermedias), implementado idénticamente en C y Java.

---

### 5.3 Contrato de Seguridad y Formato de Tokens JWT

Los microservicios validan en cada petición las aserciones criptográficas del token Bearer emitido por Keycloak:

```json
{
  "iss": "http://localhost:8085/realms/rutas-realm",
  "sub": "4a7f9c21-8842-4f67-8912-abcdef123456",
  "preferred_username": "jhonatan.admin",
  "realm_access": {
    "roles": ["planificador", "default-roles-rutas-realm"]
  },
  "scope": "openid profile email",
  "exp": 1731675600
}
```

- **Validación Perimetral:** El Gateway verifica estructura y expiración.
- **Validación Downstream:** Cada microservicio verifica la firma RS256 mediante el endpoint público de claves `jwks_uri`.
- **Trazabilidad:** Mutaciones en `grafo-service` persisten el identificador inmutable `sub`.

---

### 5.4 Matriz Consolidada de Decisiones de Arquitectura (Bitácora D-01 a D-16)

Todas las decisiones registradas en [`Bitacora_Decisiones.md`](Bitacora_Decisiones.md) responden directamente a un escenario de calidad o restricción:

| ID | Resumen de la Decisión | Alternativas Descartadas | Requisito Justificado | Trade-off / Mitigación |
|---|---|---|---|---|
| **[D-01](Bitacora_Decisiones.md#d-01)** | Cálculo en servicio aparte (`ruta-service` separado de `grafo-service`). | Monolito o cálculo en navegador. | **W-RQ-01** (Disponibilidad) | Agrega un salto HTTP; mitigado con Connection Pooling (D-15). |
| **[D-02](Bitacora_Decisiones.md#d-02)** | Datos cruzan a C como tres arreglos planos de enteros (`int[]`). | Lista de objetos `Structure` o payload JSON. | **A-RQ-01** (Desempeño C) | Firma con 9 parámetros; traducción a tipos planos en Java. |
| **[D-03](Bitacora_Decisiones.md#d-03)** | Java reserva memoria de salida; C libera toda su memoria de trabajo. | C reserva con función `liberar()` externa; o solo memoria estática en pila. | **A-RQ-02** (Testabilidad) | Java reserva `num_paradas` posiciones completas por seguridad. |
| **[D-04](Bitacora_Decisiones.md#d-04)** | C reporta errores con códigos enteros negativos en el retorno. | Uso de variable global `errno` o abortar con `exit()`. | **A-RQ-02** y **W-RQ-01** | Java mapea cada código negativo a excepciones tipadas. |
| **[D-05](Bitacora_Decisiones.md#d-05)** | Cola de prioridad con montículo binario y eliminación perezosa. | Búsqueda lineal $O(V^2)$ o montículo de Fibonacci. | **A-RQ-01** (Desempeño) | Entradas repetidas temporales en el heap ($2E+1$ max). |
| **[D-06](Bitacora_Decisiones.md#d-06)** | A igual costo gana el camino con menor cantidad de paradas. | Desempate arbitrario o por índice de parada. | **A-RQ-02** (Testabilidad) | Requiere mantener y comparar un contador de saltos. |
| **[D-07](Bitacora_Decisiones.md#d-07)** | Conmutación dinámica C / Java por configuración y tolerancia a fallos. | Compilar exclusivamente en C o prescindir de C. | **W-RQ-01** y **CON-06** | Mantenimiento simultáneo de implementaciones C y Java. |
| **[D-08](Bitacora_Decisiones.md#d-08)** | Arreglo de salida de tamaño fijo en firma sin parámetro de capacidad. | Modificar la firma congelada en C y Java. | **A-RQ-02** (Estabilidad) | La invariante de que un camino no repite nodos garantiza seguridad. |
| **[D-09](Bitacora_Decisiones.md#d-09)** | Validación de parámetros de entrada previa a la selección de motor. | Validaciones dispares en cada motor. | **A-RQ-02** y **W-RQ-01** | Doble recorrido de validación en modo nativo (despreciable). |
| **[D-10](Bitacora_Decisiones.md#d-10)** | Algoritmos complementarios (BFS, DFS, componentes, ciclos) en Java. | Escribir todo en C nativo. | **A-RQ-01** y **A-RQ-02** | Se reserva C únicamente para el cálculo intensivo de Dijkstra. |
| **[D-11](Bitacora_Decisiones.md#d-11)** | Endpoints de algoritmos agrupados bajo prefijo `/api/rutas/**`. | Agrupar bajo `/api/redes/**`. | **W-RQ-01** (Disponibilidad) | El identificador de red viaja como query param (`redId`). |
| **[D-12](Bitacora_Decisiones.md#d-12)** | Representación del grafo mediante lista de adyacencia compacta. | Matriz de adyacencia $V \times V$. | **A-RQ-01** y **W-RQ-03** | Consulta de existencia de tramo pasa de $O(1)$ a $O(\text{grado})$. |
| **[D-13](Bitacora_Decisiones.md#d-13)** | Visualizador gráfico vectorial SVG con Signals y dispersión vectorial. | HTML5 Canvas imperativo o bibliotecas externas (D3/Cytoscape). | **RF-02** y **CON-01** | Mayor consumo DOM que canvas en $>10.000$ nodos (fuera de alcance). |
| **[D-14](Bitacora_Decisiones.md#d-14)** | Algoritmo Spatial Hash Grid $O(M)$ para renderizado de red masiva. | Búsqueda exhaustiva por pares $O(M^2)$. | **W-RQ-02** (Desempeño) | Estructura `Map` auxiliar en memoria durante cómputo reactivo. |
| **[D-15](Bitacora_Decisiones.md#d-15)** | Connection Pooling persistente en API Gateway para peticiones HTTP. | Conexiones efímeras por petición. | **W-RQ-03** (Desempeño) | Reserva pequeña de descriptores de socket en memoria del Gateway. |
| **[D-16](Bitacora_Decisiones.md#d-16)** | Orquestación dual Docker Compose v2 y Kubernetes con cuotas de CPU/RAM. | Contenedores sueltos sin límites de recursos. | **A-RQ-03** y **DEPLOY-01** | Sincronización de variables y puertos entre Compose y K8s. |

---

### 5.5 Tabla Consolidada de Verificación Empírica de Atributos de Calidad

| Escenario | Métrica Requerida por la Especificación | Resultado Medido Experimentalmente | Estado de Cumplimiento | Evidencia Documental |
|---|---|---|:---:|---|
| **W-RQ-01** | Corte Gateway a $2.0\text{ s}$ con HTTP 503 ante caída de `ruta-service`; recuperación en $< 30\text{ s}$. | Corte en **2.01 s** con 503 estructurado; `/api/redes` al 100 %; recuperación en **12.4 s** sin refrescar. | ✅ **CUMPLE AL 100 %** | `docs/mediciones/SCRUM-17-disponibilidad.md` |
| **W-RQ-02** | Dibujo de red 400x1100 en $< 2.000\text{ ms}$; crecimiento lineal; tareas de hilo principal $< 100\text{ ms}$. | Dibujo en **702.81 ms**; Spatial Hash Grid en **1.27 ms** ($10.96\times$ vs $12.22\times$ aristas); hilo principal en **36.51 ms**. | ✅ **CUMPLE AL 100 %** | `docs/mediciones/SCRUM-35-desempeno-frontend.md` |
| **W-RQ-03** | Latencia en red 400x1100 $p95 < 400\text{ ms}$ en 20 consultas; traza paso a paso con ratio $< 2.0\times$. | Latencia **$p95 = 77.14\text{ ms}$**; ratio traza paso a paso en **1.07x**; tasa de éxito 20/20 HTTP 200. | ✅ **CUMPLE AL 100 %** | `docs/mediciones/SCRUM-36-latencia-gateway.md` |
| **A-RQ-01** | 1.000 rutas en C vs Java con paridad de caminos; montículo binario $O(E \log V)$; $p95 < 400\text{ ms}$. | Montículo binario verificado con $30\ \mu\text{s}$ por consulta; paridad algorítmica garantizada por D-06. | ✅ **CUMPLE AL 100 %** | `docs/mediciones/SCRUM-6-monticulo.md` |
| **A-RQ-02** | Suite C desacoplada con $\ge 10$ grafos en $< 30\text{ s}$; memoria con 0 fugas en Valgrind. | **38 pruebas unitarias en 0.30 s** con `make test`; **0 leaks y 0 errors** en Valgrind (111.505 allocs). | ✅ **CUMPLE AL 100 %** | `docs/pruebas/SCRUM-9-valgrind.md` |
| **A-RQ-03** | Despliegue con un comando en máquina limpia; primera ruta en $< 90\text{ s}$; imagen $< 250\text{ MB}$. | Despliegue `docker compose up -d` en **32.8 s**; imagen frontend **28.4 MB**; cuotas de 5.5 CPU / 2.75 GB RAM. | ✅ **CUMPLE AL 100 %** | `docs/SCRUM-38-despliegue-orquestado.md` |

---

## Conclusión

El diseño arquitectónico de **Red de Rutas** demuestra que la aplicación formal del método ADD permite desacoplar limpiamente el cómputo de alto rendimiento en C, la gestión de estado reactivo en Angular con Signals, la seguridad centralizada OIDC y la resiliencia en microservicios Spring Boot, logrando un sistema altamente disponible, predecible y verificable frente a cada requerimiento de la especificación académica.
