# SCRUM-14 [MS-01] · Creación de Microservicios Spring Boot: Gateway, Grafo y Ruta

**Responsables:** Jhonatan Varón Henao (Gateway y Grafo-Service) & Santiago Ríos Morales (Ruta-Service)  
**Materias:** Programación en Ambiente Web 2 & Arquitectura de Computadores · UCEVA · Semestre VI  

---

## 📌 Criterio de Aceptación
> *"Configurar la arquitectura de backend con 3 servicios independientes: `api-gateway` (puerta de enlace), `grafo-service` (CRUD de paradas y tramos) y `ruta-service` (cálculo algorítmico). Los tres servicios compilan, exponen health checks y arrancan en contenedores independientes."*

**Resultado:** ✅ **34 de 34 pruebas pasan** en la compilación integrada de Maven (`backend-parent`, tiempo total: 28,9 s).

---

## 🏗️ Arquitectura de Microservicios Implementada

```text
                               +-----------------------------+
                               |   Cliente Web / Consumidor  |
                               +--------------+--------------+
                                              | HTTP (8080)
                                              v
                               +-----------------------------+
                               |         api-gateway         |
                               |    Puerto 8080 / Actuator   |
                               |  Timeouts + Fallback (RQ-01)|
                               +-------+-------------+-------+
                                       |             |
                 /api/redes/** (8081)  |             |  /api/rutas/** (8082)
                                       v             v
                     +-------------------+         +-------------------+
                     |   grafo-service   |         |   ruta-service    |
                     |    Puerto 8081    |         |    Puerto 8082    |
                     |  CRUD de Paradas  |         | Algoritmo Dijkstra|
                     |     y Tramos      |<--------| (JNA C / Java)    |
                     +-------------------+ Consulta+-------------------+
                                            de Red
```

---

## 📋 Matriz de Servicios y Endpoints

| Microservicio | Puerto | Responsable | Endpoint de Salud | Endpoints de Negocio | Propósito Arquitectónico |
|---|:---:|:---:|:---:|---|---|
| **`api-gateway`** | `8080` | Jhonatan Varón | `/actuator/health` | `/api/redes/**`<br>`/api/rutas/**` | Puerta de entrada única. Aplica tiempo límite (timeout 2s a rutas) y retorna HTTP 503 explicativo si un backend no responde. |
| **`grafo-service`** | `8081` | Jhonatan Varón | `/actuator/health` | `GET /api/redes/{id}`<br>`GET /api/redes/{id}/paradas`<br>`GET /api/redes/{id}/tramos`<br>`POST /api/redes/{id}/paradas`<br>`PUT /api/redes/{id}/tramos/{tId}/estado` | Dueño de los datos de la red. Carga `grafo_40_paradas.json`, expone topología y permite abrir/cerrar vías para simular contingencias. |
| **`ruta-service`** | `8082` | Santiago Ríos / Jhonatan Varón | `/actuator/health` | `GET /api/rutas/calcular?desde={}&hasta={}&sin={}` | Motor de cálculo. Consume la red desde `grafo-service` (con fallback local) y ejecuta Dijkstra mediante `SelectorAlgoritmo` (C nativo con respaldo Java). |

---

## 🔍 Detalle Técnico por Componente

### 1. `backend/api-gateway` (Spring Boot 3.3.5)
- **Control de Tiempo Límite (Timeout):** Configurado con `SimpleClientHttpRequestFactory` con límites explícitos:
  - `grafo-service`: 5.000 ms.
  - `ruta-service`: 2.000 ms (cumpliendo el requisito estricto de **RQ-01 Disponibilidad**).
- **Tolerancia a Fallos:** Atrapa `ResourceAccessException` y devuelve respuesta estructurada en JSON con código HTTP 503:
  ```json
  {
    "timestamp": "2026-11-12T19:05:04.740Z",
    "status": 503,
    "error": "Service Unavailable",
    "servicio": "ruta-service",
    "mensaje": "El microservicio ruta-service no responde o excedio el tiempo limite de espera.",
    "detalle": "I/O error on GET request for \"http://localhost:8082/...\""
  }
  ```

### 2. `backend/grafo-service` (Spring Boot 3.3.5)
- **Persistencia en Memoria y Carga de Dataset:** Al iniciar (`@PostConstruct`), localiza y deserializa automáticamente el archivo `data/grafo_40_paradas.json` con Jackson.
- **Simulación de Vías Cerradas (`RF-06`):** Endpoint `PUT /api/redes/{id}/tramos/{tramoId}/estado?abierto=false` permite marcar un tramo como inhabilitado para que las consultas de ruta eviten esa vía.

### 3. `backend/ruta-service` (Spring Boot 3.3.5)
- **Orquestación del Cálculo:** Expone `GET /api/rutas/calcular` que recibe origen, destino y tramo opcional cerrado (`?sin=tramoId`).
- **Selector Nativo/Java:** Invoca `SelectorAlgoritmo` para ejecutar la biblioteca `libgrafo.so` en C (JNA) o conmutar de forma transparente a `DijkstraJava` si la biblioteca nativa no está presente.

---

## 🧪 Pruebas Automatizadas Integradas

Ejecución del build agregador `backend/pom.xml`:

| Módulo | Pruebas Ejecutadas | Fallos | Errores | Estado |
|---|:---:|:---:|:---:|:---:|
| `api-gateway` | 3 | 0 | 0 | ✅ PASÓ |
| `grafo-service` | 3 | 0 | 0 | ✅ PASÓ |
| `ruta-service` | 28 | 0 | 0 | ✅ PASÓ |
| **Total Reactor** | **34** | **0** | **0** | ✅ **BUILD SUCCESS** |

---

## 🚀 Cómo Ejecutar y Probar

Para compilar y correr toda la suite de pruebas del backend en un solo comando:

```bash
cd backend
mvn test
```

Para arrancar un servicio individualmente (ejemplo: `grafo-service`):

```bash
cd backend/grafo-service
mvn spring-boot:run
```
Verificar salud en el navegador o terminal:
```bash
curl http://localhost:8081/actuator/health
# Respuesta esperada: {"status":"UP"}
```
