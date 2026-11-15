# SCRUM-36 [W-RQ-03]: Medición de Latencia de Consulta en API Gateway (p95 < 400 ms en 20 Peticiones Consecutivas)

## 1. Información General del Requerimiento
* **ID Jira:** `SCRUM-36`
* **Código de Requerimiento:** `W-RQ-03` (Desempeño Web - Latencia de Consulta en Gateway)
* **Sprint:** Sprint 5 - Medición y Deploy
* **Responsable:** Jhonatan Varón Henao (`jhonatan.varon01@uceva.edu.co`) - *DevSecOps, Ciberseguridad & Backend de Datos*
* **Rama Git:** `feature/jhonatan-scrum-36-latencia-gateway-p95`
* **Tecnologías:** Spring Cloud Gateway, Apache HttpClient 5 (`PoolingHttpClientConnectionManager`), Dijkstra Algorithm, JWT Security, Node.js, PowerShell.

---

## 2. Descripción del Problema y Objetivos
El escenario arquitectónico **W-RQ-03** evalúa el desempeño del sistema cuando un usuario solicita la ruta más corta sobre la red metropolitana masiva (**400 paradas y 1.100 tramos**), midiendo la latencia extremo a extremo percibida por el cliente desde el **API Gateway**.

| Métrica Requerida | Límite Máximo Aceptable | Objetivo Técnico |
| :--- | :---: | :--- |
| **Percentil 95 ($p95$)** | $< 400\text{ ms}$ | El 95 % de las consultas consecutivas de ruta más corta a través del Gateway deben responder en menos de 400 milisegundos en una ráfaga de 20 peticiones. |
| **Sobrecosto de Traza Paso a Paso** | **Ratio $< 2.0\times$** | Solicitar la traza paso a paso de nodos explorados (*VCR playback / RF-04*) no debe duplicar el tiempo de respuesta frente a una consulta de ruta simple. |
| **Integridad de Respuesta HTTP** | **100 % HTTP 200 OK** | El 100 % de las consultas deben resolver la ruta óptima con token Bearer válido sin degradación de servicio ni colapso del pool de conexiones. |

---

## 3. Arquitectura y Solución Técnica

### 3.1. Flujo Extremo a Extremo a través del Gateway
1. **Cliente HTTP / Frontend:** Emite petición `GET /api/rutas/calcular?desde=X&hasta=Y&redId=2[&traza=true]` con encabezado `Authorization: Bearer <JWT>`.
2. **API Gateway (Puerto 8080):**
   * Inspección y validación criptográfica de la firma del token JWT mediante `KeycloakJwtAuthenticationConverter` y `SecurityConfig`.
   * Enrutamiento reverso mediante `GatewayController` utilizando un pool reutilizable de conexiones HTTP persistentes (`PoolingHttpClientConnectionManager` con Apache HttpClient 5).
   * Timeout de corte preventivo fijado en $2.000\text{ ms}$ (contingencia W-RQ-01).
3. **Servicio de Rutas (`ruta-service`):**
   * Carga de la red metropolitana (`redId=2`) con 400 paradas y 1.100 tramos.
   * Ejecución del algoritmo Dijkstra con Cola de Prioridad (*Min-Heap*).
   * Generación de la secuencia de paradas y cálculo acumulativo de la traza de nodos explorados.
4. **Respuesta Serializada JSON:** Retorno con código `200 OK`, costo total, arreglo de camino y traza temporal.

---

## 4. Resultados de las Mediciones y Benchmarks (20 Consultas Consecutivas)

*Medición ejecutada mediante `scripts/medir_rq03_gateway.js` sobre la red de 400 paradas y 1.100 tramos:*

| # | Origen $\to$ Destino | Latencia Ruta Base | Latencia con Traza | Ratio Traza/Base | Costo Total (min) | Longitud Camino | Veredicto ($< 400\text{ ms}$) |
| :-: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **01** | $0 \to 399$ (Diagonal extrema) | 65.73 ms | 6.27 ms | 0.10x | 120 | 38 nodos | **CUMPLE** |
| **02** | $10 \to 385$ | 4.76 ms | 3.17 ms | 0.67x | 69 | 24 nodos | **CUMPLE** |
| **03** | $25 \to 370$ | 3.05 ms | 3.41 ms | 1.12x | 75 | 26 nodos | **CUMPLE** |
| **04** | $40 \to 355$ | 2.59 ms | 3.01 ms | 1.16x | 88 | 29 nodos | **CUMPLE** |
| **05** | $65 \to 340$ | 4.13 ms | 3.82 ms | 0.92x | 68 | 23 nodos | **CUMPLE** |
| **06** | $80 \to 325$ | 1.55 ms | 1.56 ms | 1.01x | 54 | 19 nodos | **CUMPLE** |
| **07** | $100 \to 310$ | 1.43 ms | 1.36 ms | 0.95x | 60 | 21 nodos | **CUMPLE** |
| **08** | $120 \to 295$ | 1.14 ms | 1.29 ms | 1.13x | 72 | 25 nodos | **CUMPLE** |
| **09** | $145 \to 280$ | 1.10 ms | 1.80 ms | 1.64x | 41 | 15 nodos | **CUMPLE** |
| **10** | $160 \to 265$ | 1.95 ms | 1.30 ms | 0.67x | 34 | 12 nodos | **CUMPLE** |
| **11** | $180 \to 250$ | 1.44 ms | 1.92 ms | 1.33x | 48 | 17 nodos | **CUMPLE** |
| **12** | $200 \to 235$ | 1.22 ms | 1.48 ms | 1.21x | 61 | 21 nodos | **CUMPLE** |
| **13** | $220 \to 220$ (Idéntico) | 0.95 ms | 1.06 ms | 1.12x | 0 | 1 nodo | **CUMPLE** |
| **14** | $240 \to 205$ | 0.94 ms | 0.89 ms | 0.94x | 22 | 8 nodos | **CUMPLE** |
| **15** | $260 \to 190$ | 1.10 ms | 1.08 ms | 0.99x | 47 | 16 nodos | **CUMPLE** |
| **16** | $280 \to 175$ | 1.87 ms | 1.19 ms | 0.64x | 69 | 24 nodos | **CUMPLE** |
| **17** | $300 \to 160$ | 0.96 ms | 0.90 ms | 0.94x | 31 | 11 nodos | **CUMPLE** |
| **18** | $320 \to 145$ | 0.89 ms | 0.99 ms | 1.12x | 53 | 18 nodos | **CUMPLE** |
| **19** | $350 \to 100$ | 1.13 ms | 1.36 ms | 1.20x | 84 | 28 nodos | **CUMPLE** |
| **20** | $380 \to 20$ (Retorno opuesto) | 1.02 ms | 1.06 ms | 1.05x | 89 | 30 nodos | **CUMPLE** |

---

### 4.1. Resumen Estadístico y Análisis de Percentiles

| Métrica Estadística | Valor Obtenido | Umbral Exigido (W-RQ-03) | Margen de Seguridad |
| :--- | :---: | :---: | :---: |
| **Latencia Mínima** | **0.89 ms** | - | - |
| **Latencia Mediana ($p50$)** | **1.33 ms** | - | - |
| **Latencia Promedio** | **4.95 ms** | - | - |
| **Latencia Máxima** | **65.73 ms** (Arranque en frío de conexión) | $< 400.0\text{ ms}$ | **83.6 % por debajo** |
| **Percentil 95 ($p95$)** | **65.73 ms** | **$< 400.0\text{ ms}$** | **83.6 % por debajo** |
| **Ratio Promedio Traza / Base** | **1.00x** | **$< 2.0\times$** | **Cumple estrictamente** |

#### Conclusión Técnica:
1. **El percentil 95 se sitúa en 65.73 ms**, superando holgadamente el criterio de aceptación que exige un tiempo inferior a **400 ms** (margen de holgura superior al 83 %).
2. En estado de régimen permanente (tras el arranque en frío de la petición #1), **la latencia promedio ronda los 1.75 ms**, demostrando la altísima eficiencia de la arquitectura de microservicios y el cálculo con min-heap.
3. El sobrecosto computacional de calcular y empaquetar la traza paso a paso es insignificante ($\text{ratio} \approx 1.00\times$), cumpliendo la condición de que **pedir la traza no duplica el tiempo**.

---

## 5. Entregables Asociados
* `scripts/medir_rq03_gateway.js`: Script de benchmark automatizado con 20 consultas consecutivas.
* `scripts/medir_rq03_gateway.ps1`: Script ejecutable en PowerShell.
* `docs/mediciones/w_rq03_latencia_gateway.csv`: Registro tabular de las 20 mediciones.
* `docs/SCRUM-36-latencia-gateway.md`: Este informe técnico de arquitectura.
* `docs/mediciones/SCRUM-36-latencia-gateway.md`: Resumen ejecutivo de evidencias para sustentación.
