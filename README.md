# 🚀 Red de Rutas - Sistema Distribuido de Transporte Urbano

**Proyecto Integrador** · Semestre VI  
**Universidad Central del Valle del Cauca (UCEVA)**  
**Materias:** Programación en Ambiente Web 2 & Arquitectura de Computadores  
**Equipo:** Villanos  
**Integrantes:**
- **Jhonatan Varón Henao** (`jhonatan.varon01@uceva.edu.co`) — *DevSecOps, Ciberseguridad & Backend de Datos*
- **Santiago Ríos Morales** (`santiago.rios01@uceva.edu.co`) — *Cómputo de Alto Rendimiento, Algoritmos & Visualización*

---

## 📌 Descripción del Sistema

**Red de Rutas** es una plataforma distribuida de alta disponibilidad y cómputo de alto rendimiento para el modelado de redes de transporte urbano como **grafos ponderados**, cálculo de caminos óptimos (algoritmo de Dijkstra) y simulación interactiva de contingencias viales (cierre de vías, componentes conexas, detección de ciclos y análisis de memoria).

El sistema se destaca por cuatro pilares arquitectónicos:
1. **Cómputo Nativo en C (`libgrafo.so`):** Dijkstra implementado en C99 con cola de prioridad en montículo binario ($O(E \log V)$), invocado desde Java mediante **JNA** con línea base en Java y conmutación automática de contingencia.
2. **Alta Disponibilidad y Resiliencia Perimetral (W-RQ-01):** API Gateway reactivo con timeout estricto de $2.0\text{ s}$ y retorno inmediato HTTP 503 ante caídas de servicios de cálculo, manteniendo los mapas y paradas siempre disponibles.
3. **Frontend Reactivo con Angular 17+ Signals (W-RQ-02):** Visualizador SVG con algoritmo **Spatial Hash Grid $O(M)$** que resuelve colisiones de etiquetas de peso en $1.27\text{ ms}$ sobre redes de 400 paradas y 1.100 tramos, con animación temporal determinista paso a paso (RF-04).
4. **Seguridad Integral OIDC con Keycloak (RA-01 a RA-07):** Autenticación mediante flujo *Authorization Code con PKCE*, control de acceso granular por roles (`planificador` y `consultor`) y registro de auditoría inmutable indexado por el claim `sub`.

---

## 🏗️ Diagrama de Arquitectura

```text
                                  +-----------------------+
                                  |   Keycloak (OAuth2)   |
                                  |   Realm: rutas-realm  |
                                  +-----------+-----------+
                                              |
                          +-------------------+-------------------+
                          | (OIDC: Auth Code + PKCE / Bearer JWT) |
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
                               |  Paradas, Tramos   |                          |  Dijkstra Nativo,  |
                               |    y Auditoría     |                          |   BFS, DFS, Ciclos |
                               +--------------------+                          +---------+----------+
                                                                                         |
                                                                                         v (JNA FFI)
                                                                               +--------------------+
                                                                               |    libgrafo.so     |
                                                                               |     (C Nativo)     |
                                                                               +--------------------+
```

---

## 📋 Requisitos Previos

Para arrancar el sistema en una máquina limpia se requiere únicamente:
- **Docker Engine** 24.0+ y **Docker Compose v2** (o clúster de **Kubernetes** con `kubectl`)
- Para desarrollo y compilación local fuera de Docker (opcional):
  - Java OpenJDK 17+
  - Maven 3.9+
  - Node.js 20+ y NPM 10+
  - Compilador GCC con Make (para la biblioteca nativa en C)

---

## ⚡ Guía de Arranque Rápido (*README de Arranque*)

### 1. Despliegue con Docker Compose (Recomendado)

En la raíz del proyecto, ejecute un solo comando para compilar y levantar todo el ecosistema con límites de CPU y memoria:

```bash
docker compose up -d
```

Para verificar que los 5 contenedores estén saludables y en ejecución:

```bash
docker compose ps
```

*Para detener el ecosistema completo:*
```bash
docker compose down
```

### 2. Despliegue con Kubernetes (K8s)

Para desplegar sobre un clúster de Kubernetes mediante Kustomize:

```bash
kubectl apply -k k8s/
```

Para verificar el estado de los pods y servicios en el namespace `villanos`:

```bash
kubectl get pods,svc -n villanos
```

---

## 🔑 Credenciales de Acceso (Keycloak `rutas-realm`)

El realm `rutas-realm` viene preconfigurado con dos usuarios para pruebas y evaluación de roles:

| Usuario | Contraseña | Rol Asignado | Permisos y Capacidades |
|---|---|---|---|
| **`planificador`** | `planificador123` | `planificador` | Control total: Crear, editar y eliminar paradas y tramos viales; consultar auditoría (`sub`); simular rutas y contingencias. |
| **`consultor`** | `consultor123` | `consultor` | Modo consulta: Visualizar el mapa, calcular caminos mínimos, reproducir paso a paso y evaluar cierres viales. |

---

## 🌐 Mapa de Puertos y Servicios

| Servicio | Puerto Host | URL de Acceso | Descripción |
|---|:---:|---|---|
| **Frontend Web** | `4200` | `http://localhost:4200` | SPA en Angular 17 servida por Nginx Alpine. |
| **API Gateway** | `8080` | `http://localhost:8080` | Puerta de entrada única a los microservicios backend. |
| **Grafo Service** | `8081` | `http://localhost:8081` | API interna de persistencia de paradas, tramos y auditoría. |
| **Ruta Service** | `8082` | `http://localhost:8082` | API interna de cálculo algorítmico y puente JNA C. |
| **Keycloak IdP** | `8085` | `http://localhost:8085` | Servidor de autenticación y consola de administración. |

---

## 🧪 Reproducción de Pruebas y Mediciones de Calidad

### 1. Verificación de Despliegue y Límites de Recursos (A-RQ-03 / DEPLOY-01)
Ejecuta la validación automatizada de límites de CPU, memoria y puertos de Docker Compose y Kubernetes:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/verificar_despliegue.ps1
```

### 2. Pruebas Unitarias y Sanidad de Memoria en C (A-RQ-02)
Compila y ejecuta la suite de 38 pruebas unitarias en C99 y el análisis de fugas con Valgrind:
```bash
cd backend/ruta-service/native
make clean && make test
make valgrind
```
*Resultado esperado: 38 pruebas pasando en < 0.30s; 0 fugas de memoria y 0 errores en Valgrind.*

### 3. Pruebas Unitarias del Frontend en Angular
Ejecuta la suite completa de pruebas unitarias sobre los componentes standalone y señales de Angular:
```bash
cd frontend/red-rutas-web
npm test -- --watch=false
```
*Resultado esperado: 36 pruebas unitarias exitosas.*

### 4. Benchmark de Desempeño Frontend en Red 400x1100 (W-RQ-02)
Genera la red determinista de 400 paradas y 1.100 tramos y evalúa el tiempo de renderizado con Spatial Hash Grid:
```bash
node scripts/generar_red_400.js
```
*Resultado esperado: Dibujo inicial en 702 ms (< 2.000 ms), algoritmo de proximidad en 1.27 ms ($O(M)$), hilo principal < 37 ms.*

### 5. Benchmark de Latencia en API Gateway (W-RQ-03)
Ejecuta 20 consultas consecutivas sobre la red de 400 paradas y calcula el percentil 95:
```bash
node scripts/medir_rq03_gateway.js
```
*Resultado esperado: $p95 = 77.14\text{ ms}$ ($< 400\text{ ms}$), ratio de traza 1.07x ($< 2.0\text{x}$).*

---

## 📚 Documentación Técnica Detallada

- 📑 **[Documento de Diseño de Arquitectura ADD (5 Pasos)](docs/ADD_Diseño_Arquitectura.md):** Especificación completa de drivers, escenarios en 6 partes, tácticas, diagramas de componentes, contratos de interfaces y tabla consolidada de verificación empírica.
- 📖 **[Bitácora de Decisiones de Arquitectura](docs/Bitacora_Decisiones.md):** Registro formal de las decisiones D-01 a D-16 con alternativas evaluadas, justificaciones por atributo de calidad y trade-offs aceptados.
- 📊 **[Mediciones y Evidencias Experimentales](docs/mediciones/):** Reportes en Markdown y CSV con los datos crudos de las pruebas de carga, latencia y disponibilidad.

---

## 📋 Gestión del Proyecto (Scrum en Jira)
El proyecto fue planificado y ejecutado a lo largo de 5 Sprints en Jira:  
🔗 **[villanos.atlassian.net](https://villanos.atlassian.net)** (Proyecto `SCRUM`, historias asignadas a Jhonatan y Santiago completadas).
