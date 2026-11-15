# SCRUM-35 [W-RQ-02]: Medición de Desempeño al Renderizar Red Grande (400 Paradas y 1.100 Tramos) en Angular

## 1. Información General del Requerimiento
* **ID Jira:** `SCRUM-35`
* **Código de Requerimiento:** `W-RQ-02` (Desempeño Web - Renderizado de Red Grande)
* **Sprint:** Sprint 5 - Medición y Deploy
* **Responsable:** Jhonatan Varón Henao (`jhonatan.varon01@uceva.edu.co`) - *DevSecOps, Ciberseguridad & Backend de Datos*
* **Rama Git:** `feature/jhonatan-scrum-35-desempeno-frontend-400-paradas`
* **Tecnologías:** Angular 17+, TypeScript, Signals reactivas, SVG nativo, Spatial Hash Grid, Node.js, PowerShell.

---

## 2. Descripción del Problema y Objetivos
El escenario arquitectónico **W-RQ-02** estipula el comportamiento del sistema cuando la red de transporte escala de un tamaño urbano convencional (40 paradas y 90 tramos) a una escala metropolitana masiva (**400 paradas y 1.100 tramos**):

| Métrica Requerida | Límite Máximo Aceptable | Objetivo Técnico |
| :--- | :---: | :--- |
| **Tiempo de dibujo inicial** | $< 2.000\text{ ms}$ ($< 2\text{ s}$) | El visualizador vectorial del grafo debe instanciar y proyectar la totalidad de nodos y aristas en el DOM SVG en menos de 2 segundos desde la llegada de los datos. |
| **Crecimiento de complejidad temporal** | **Lineal en tramos $O(M)$** | El cómputo y posicionamiento anti-solapamiento de etiquetas de peso no debe presentar complejidad cuadrática $O(M^2)$ al escalar de 90 a 1.100 tramos. |
| **Bloqueo del hilo principal** | $< 100\text{ ms}$ | Ninguna tarea síncrona o ciclo reactivo de Signals en el hilo principal (*Main Thread Long Tasks*) debe superar los 100 ms para evitar caídas de cuadros (*frame drops*) o congelamiento visual de la interfaz. |

---

## 3. Arquitectura y Solución Técnica

### 3.1. Generación Determinista del Dataset de Red Grande (400x1100)
Se desarrolló un generador determinista mediante un generador congruencial lineal (*LCG*) con semilla fija (`seed = 20261115`), garantizando reproducibilidad exacta en cualquier entorno:
* **400 Paradas:** Distribuidas uniformemente en una malla geográfica 2D de $20 \times 20$ con variación espacial pseudoaleatoria ($X \in [80, 2400]$, $Y \in [80, 1600]$), representando barrios y sectores reales de la región.
* **1.100 Tramos:** Conectividad garantizada mediante malla ortogonal base (conexión horizontal y vertical) complementada con arterias exprés y diagonales con pesos enteros positivos ($2 \le \text{peso} \le 12\text{ min}$).
* **Ubicación:**
  * Backend: `backend/grafo-service/data/grafo_400_paradas.json`.
  * Frontend: `frontend/red-rutas-web/src/app/core/models/red-400.data.ts`.

### 3.2. Optimización Asintótica: Spatial Hash Grid $O(M)$
En la implementación inicial, la resolución anti-solapamiento de etiquetas de peso comparaba cada tramo $i$ contra todas las etiquetas ya ubicadas ($0 \dots i-1$), incurriendo en una complejidad cuadrática:
$$\sum_{i=1}^{M} i = \frac{M(M-1)}{2} \approx 604.450 \text{ comparaciones para } M = 1.100 \implies O(M^2)$$

Para garantizar el criterio de **crecimiento lineal $O(M)$**, se sustituyó la búsqueda exhaustiva por una estructura de indexación espacial **Spatial Hash Grid**:
1. Se particiona el lienzo vectorial en celdas de tamaño $R = 24\text{px}$ (superior al radio de colisión mínima de $16\text{px}$).
2. Para cada etiqueta calculada en $(x, y)$, se mapea a su celda:
   $$\text{celda}_x = \lfloor x / 24 \rfloor, \quad \text{celda}_y = \lfloor y / 24 \rfloor$$
3. La verificación de colisiones inspecciona únicamente las $3 \times 3 = 9$ celdas adyacentes en una tabla hash indexada por cadena (`${gx}_${gy}`).
4. Debido a la restricción de distancia mínima ($16\text{px}$), cada celda alberga como máximo $O(1)$ elementos ($1 \text{ a } 2$ etiquetas).
5. **Complejidad resultante:** $M \times 9 \times O(1) = O(M)$ **estrictamente lineal**.

### 3.3. ViewBox Reactivo Auto-Ajustable y Selector de Red
* `viewBox`: Adapta dinámicamente las dimensiones del canvas base. Si la red contiene más de 100 paradas, el encuadre se expande automáticamente a $2500 \times 1750$ para permitir una navegación holgada.
* **Selector en UI:** Se incorporó en `RedGestorComponent` un conmutador rápido para alternar en vivo entre la **Red Urbana (40 paradas)** y la **Red Grande W-RQ-02 (400 paradas)**, facilitando la auditoría interactiva del profesor y del equipo.

---

## 4. Resultados de las Mediciones y Benchmarks

### 4.1. Escalabilidad Temporal según Número de Tramos (Validación $O(M)$)
Ejecutado con `node scripts/medir_rq02_frontend.js` (25 iteraciones estabilizadas por configuración):

| Configuración | Paradas ($N$) | Tramos ($M$) | Tiempo Promedio | Tiempo Máximo | Complejidad Observada | Criterio $< 100\text{ ms}$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Red Urbana Base** | 40 | 90 | **0.116 ms** | 0.652 ms | $O(M)$ | **CUMPLE** |
| **Intermedia 1** | 100 | 250 | **0.199 ms** | 0.494 ms | $O(M)$ | **CUMPLE** |
| **Intermedia 2** | 200 | 500 | **0.384 ms** | 0.957 ms | $O(M)$ | **CUMPLE** |
| **Intermedia 3** | 300 | 800 | **0.749 ms** | 1.237 ms | $O(M)$ | **CUMPLE** |
| **Red Grande W-RQ-02** | 400 | 1.100 | **1.271 ms** | 1.934 ms | $O(M)$ | **CUMPLE** |

#### Análisis de Proporcionalidad:
* **Factor de incremento de tramos:** $\frac{1.100}{90} = 12.22\times$.
* **Factor de incremento de tiempo:** $\frac{1.271\text{ ms}}{0.116\text{ ms}} = 10.96\times$.
* **Relación $\frac{\Delta \text{Tiempo}}{\Delta \text{Tramos}}$:** $\frac{10.96}{12.22} = 0.897 \approx 1.0$ (Crecimiento estrictamente proporcional y lineal).  
  *(En una solución $O(M^2)$ sin Spatial Grid, el incremento teórico habría sido de $12.22^2 \approx 149.3\times$, elevando el tiempo a $> 17\text{ ms}$)*.

### 4.2. Medición en Angular Testbed (Vitest / Jasmine)
Ejecutado mediante `npm test -- --watch=false` en `GrafoVisorComponent`:

| Prueba Automatizada | Valor Medido | Límite W-RQ-02 | Margen de Seguridad | Estado |
| :--- | :---: | :---: | :---: | :---: |
| **Dibujo inicial (DOM SVG)** | **702.81 ms** | $< 2.000\text{ ms}$ | **64.8 % por debajo** | **SUPERADO** |
| **Crecimiento de tramos** | **0.059 ms $\to$ 0.304 ms** | Lineal | Escala $5.1\times < 35\times$ | **SUPERADO** |
| **Tareas síncronas hilo principal** | **36.51 ms** | $< 100\text{ ms}$ | **63.5 % por debajo** | **SUPERADO** |

---

## 5. Archivos Creados y Modificados
1. `scripts/generar_red_400.js`: Generador de datos reproducibles de 400 paradas y 1.100 tramos.
2. `backend/grafo-service/data/grafo_400_paradas.json`: Dataset persistente de la red grande.
3. `backend/grafo-service/src/main/java/com/villanos/grafo/service/GrafoService.java`: Soporte de carga de red 1 y red 2.
4. `frontend/red-rutas-web/src/app/core/models/red-400.data.ts`: Constante `RED_GRANDE_400` en TypeScript.
5. `frontend/red-rutas-web/src/app/core/services/red.service.ts`: Métodos `cargarRedGrande()` y fallback automático.
6. `frontend/red-rutas-web/src/app/features/grafo-visor/grafo-visor.component.ts`: Algoritmo Spatial Hash Grid $O(M)$ y `viewBox` adaptativo.
7. `frontend/red-rutas-web/src/app/features/grafo-visor/grafo-visor.component.spec.ts`: Suite de pruebas unitarias y benchmarks W-RQ-02.
8. `frontend/red-rutas-web/src/app/features/red-gestor/red-gestor.component.ts`: Selector reactivo de red activa.
9. `frontend/red-rutas-web/src/app/features/red-gestor/red-gestor.component.html`: Botones para alternar Red 40 / Red 400.
10. `frontend/red-rutas-web/src/app/features/red-gestor/red-gestor.component.css`: Estilos visuales del selector de red.
11. `scripts/medir_rq02_frontend.js`: Benchmark automatizado de complejidad temporal.
12. `scripts/medir_rq02_frontend.ps1`: Script PowerShell de ejecución rápida.
13. `docs/mediciones/w_rq02_desempeno_frontend.csv`: Registro CSV de mediciones.
14. `docs/SCRUM-35-desempeno-frontend.md`: Esta documentación técnica.
15. `docs/mediciones/SCRUM-35-desempeno-frontend.md`: Resumen ejecutivo de evidencias.
