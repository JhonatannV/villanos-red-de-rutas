# 🚀 Red de Rutas - Proyecto Integrador
**Universidad Central del Valle del Cauca (UCEVA) · Semestre VI**  
**Materias:** Programación en Ambiente Web 2 & Arquitectura de Computadores  
**Equipo:** Villanos  
**Integrantes:**
- **Jhonatan Varón Henao** (`jhonatan.varon01@uceva.edu.co`) - *DevSecOps, Ciberseguridad & Backend de Datos*
- **Santiago Ríos Morales** (`santiago.rios01@uceva.edu.co`) - *Cómputo de Alto Rendimiento, Algoritmos & Visualización*

---

## 📌 Descripción del Sistema
Sistema distribuido de alta disponibilidad para el modelado de redes de transporte urbano como grafos, cálculo de caminos óptimos (Dijkstra) y simulación de contingencias viales (cierre de tramos, análisis de componentes conexas y ciclos).

El sistema se caracteriza por dos pilares arquitectónicos:
1. **Reproducción paso a paso:** El frontend anima de forma pedagógica el recorrido del algoritmo (nodos visitados, nodos cerrados y tabla dinámica de distancias tentativas).
2. **Cómputo nativo y resiliencia:** El cálculo crítico de rutas está implementado en **C nativo (`libgrafo.so`)** invocado desde Spring Boot con **JNA**, con respaldo transparente en Java puro y tolerancia a fallos en el API Gateway ante caídas del servicio de rutas (**RQ-01 Disponibilidad**).

---

## 🏗️ Arquitectura del Sistema

```text
                                 +---------------------+
                                 |   Keycloak (OAuth2)  |
                                 |  Realm: rutas-realm  |
                                 +----------+----------+
                                            |
                         +------------------+------------------+
                         | (Authorization Code + PKCE / JWT)   |
                         v                                     v
                  +---------------+                     +-------------+
                  |    Frontend   |                     | API Gateway |
                  |  (Angular 17) +-------------------->+ (Spring GW) |
                  +---------------+                     +------+------+
                                                               |
                                        +----------------------+----------------------+
                                        |                                             |
                                        v                                             v
                             +--------------------+                        +--------------------+
                             |   grafo-service    |                        |    ruta-service    |
                             |   (Spring Boot)    |                        |   (Spring Boot)    |
                             |  CRUD de Paradas   |                        |  Cálculo de Rutas  |
                             |     y Tramos       |                        +---------+----------+
                             +--------------------+                                  |
                                                                                     v (JNA)
                                                                           +--------------------+
                                                                           |    libgrafo.so     |
                                                                           |     (C Nativo)     |
                                                                           +--------------------+
```

---

## 📁 Estructura del Repositorio

* `backend/`
  * `api-gateway/`: Puerta de entrada única con timeout estricto de 2s y fallback 503 ante fallos.
  * `grafo-service/`: Persistencia de paradas ($x, y$), tramos y registro de auditoría con claim `sub`.
  * `ruta-service/`: Orquestación de algoritmos, puente JNA y conmutación native/Java.
    * `native/`: Código fuente en C (`dijkstra.c`, `grafo.h`, Makefile).
* `frontend/`
  * `red-rutas-web/`: Aplicación SPA en Angular con Standalone Components, Signals y cliente PKCE.
* `keycloak/`: Archivo de importación `rutas-realm.json` con roles `planificador` y `consultor`.
* `docs/`: Documento metodológico ADD (5 pasos) y Bitácora de Decisiones de Arquitectura.

---

## 📋 Gestión del Proyecto (Scrum en Jira)
El proyecto está gestionado en el tablero oficial de Jira:
🔗 **[villanos.atlassian.net](https://villanos.atlassian.net)** (Proyecto `SCRUM`, 36 historias en 5 Sprints).
