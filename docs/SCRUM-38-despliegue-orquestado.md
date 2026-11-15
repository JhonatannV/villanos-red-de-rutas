# SCRUM-38 [DEPLOY-01 / A-RQ-03]: Despliegue Orquestado con Docker Compose y Kubernetes con Límites de Recursos

## 1. Información General del Requerimiento
* **ID Jira:** `SCRUM-38`
* **Código de Requerimiento:** `DEPLOY-01` / `A-RQ-03` (*Facilidad de Despliegue y Orquestación*)
* **Sprint:** Sprint 5 - Medición y Deploy
* **Responsable:** Jhonatan Varón Henao (`jhonatan.varon01@uceva.edu.co`) - *DevSecOps, Ciberseguridad & Backend de Datos*
* **Rama Git:** `feature/jhonatan-scrum-38-despliegue-orquestado-recursos`
* **Tecnologías:** Docker, Docker Compose v2, Kubernetes (K8s / Kustomize), Multi-stage Dockerfiles, Nginx Alpine, Spring Boot 3, Keycloak 24.

---

## 2. Descripción del Problema y Objetivos
El Requerimiento **DEPLOY-01** (y el escenario arquitectónico **A-RQ-03**) exige que el sistema completo de transporte se despliegue de forma automatizada y reproducible mediante un **único comando / manifiesto**, declarando explícitamente los límites de recursos de hardware para evitar contención, fugas de memoria o acaparamiento de CPU (*noisy neighbor problem*).

### Criterios de Aceptación:
1. **Un solo comando de inicio:** El sistema completo (Keycloak, Microservicios Spring Boot, API Gateway y Frontend Angular) debe iniciar con `docker compose up -d` o `kubectl apply -k k8s/`.
2. **Límites de CPU y Memoria obligatorios:** Declaración explícita de `deploy.resources.limits` y `reservations` para el 100 % de los contenedores del ecosistema.
3. **Frontend empaquetado en contenedor:** Dockerfile multi-stage para compilar Angular 17+ y servir los assets estáticos mediante Nginx Alpine ultraligero (< 30 MB).
4. **Orden de inicio determinista:** Control de dependencias mediante verificaciones de salud (`condition: service_healthy`) para garantizar que la base de autenticación Keycloak esté lista antes de que los microservicios intenten sincronizar sus llaves JWKS.

---

## 3. Matriz de Asignación de Recursos (CPU y Memoria)

| Servicio | Contenedor | Puerto Host | CPU Limit | Memory Limit | CPU Request | Memory Request | Propósito de Carga |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Keycloak** | `keycloak` | `8085` | **1.5 CPU** | **1024 MB** | 0.5 CPU | 512 MB | Servidor OAuth2/OIDC, JVM y criptografía RSA/S256. |
| **Grafo Service** | `grafo-service` | `8081` | **1.0 CPU** | **512 MB** | 0.25 CPU | 256 MB | Gestión y persistencia en memoria de paradas y tramos. |
| **Ruta Service** | `ruta-service` | `8082` | **1.5 CPU** | **512 MB** | 0.25 CPU | 256 MB | Motor de cómputo algorítmico (Dijkstra C/Java y Min-Heap). |
| **API Gateway** | `api-gateway` | `8080` | **1.0 CPU** | **512 MB** | 0.25 CPU | 256 MB | Enrutador perimetral, pool HTTP y validación de tokens. |
| **Frontend Web** | `frontend` | `4200` | **0.5 CPU** | **256 MB** | 0.1 CPU | 64 MB | Nginx sirviendo la SPA Angular compilada en producción. |
| **TOTAL ECOSISTEMA** | - | - | **5.5 CPU** | **2.75 GB** | **1.35 CPU** | **1.31 GB** | **Óptimo para servidores y estaciones de desarrollo.** |

---

## 4. Arquitectura de Manifiestos Implementada

### 4.1. Docker Compose (`docker-compose.yml`)
* **Healthchecks integrados:**
  * `keycloak`: Verifica `/health/ready` mediante socket bash `/dev/tcp`.
  * Microservicios Java: Verifican `/actuator/health` con `wget --spider`.
  * `frontend`: Verifica disponibilidad en puerto 80 con `wget --spider`.
* **Red interna aislada:** Red de tipo puente `red-de-rutas-net` que aísla el tráfico inter-servicios.
* **Persistencia y volumen:** Mapeo de `rutas-realm.json` en modo solo lectura (`:ro`) para asegurar consistencia e inmutabilidad.

### 4.2. Manifiestos de Kubernetes (`k8s/`)
Estructurados para despliegue declarativo con Kustomize (`kubectl apply -k k8s/`):
* `namespace.yaml`: Aislamiento en el namespace dedicado `red-de-rutas`.
* `keycloak.yaml`: Deployment con probes de readiness y Service ClusterIP.
* `grafo-service.yaml`, `ruta-service.yaml`, `api-gateway.yaml`: Deployments con liveness probes hacia `/actuator/health` y services DNS internos (`*.red-de-rutas.svc.cluster.local`).
* `frontend.yaml`: Deployment de Nginx y Service LoadBalancer exponiendo el puerto 80.
* `kustomization.yaml`: Agregador maestro para despliegue reproducible en clúster.

### 4.3. Dockerfile Multi-Stage del Frontend (`frontend/red-rutas-web/Dockerfile`)
* **Etapa 1 (Build):** Imagen `node:20-alpine` ejecuta `npm run build -- --configuration production`.
* **Etapa 2 (Runtime):** Imagen base `nginx:1.27-alpine` (< 30 MB) copia únicamente los archivos compilados en `dist/red-rutas-web/browser`.
* **Configuración Nginx (`nginx.conf`):** Soporta fallback para Single Page Application (`try_files $uri $uri/ /index.html;`) y proxy reverso hacia `/api/` en el Gateway.

---

## 5. Instrucciones de Despliegue

### Despliegue en Docker Compose:
```bash
# Iniciar todo el ecosistema con un solo comando
docker compose up -d

# Verificar estado y salud de los contenedores
docker compose ps

# Verificar consumo en vivo de CPU y Memoria con respecto a los limites
docker stats
```

### Despliegue en Kubernetes:
```bash
# Aplicar todos los manifiestos en el clúster
kubectl apply -k k8s/

# Verificar pods en el namespace
kubectl get pods -n red-de-rutas
```

### Script de Verificación Rápida:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/verificar_despliegue.ps1
```
