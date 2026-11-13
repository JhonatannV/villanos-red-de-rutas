# Documentación Técnica: SCRUM-16 [DOCKER-01]

## 1. Información General
* **Historia de Usuario:** SCRUM-16 [DOCKER-01]
* **Título:** Dockerfile multietapa para compilación en C y empaquetado JAR (< 250 MB sin GCC) y docker-compose.yml
* **Responsable:** Jhonatan Varón Henao (DevSecOps & Backend de Datos)
* **Equipo:** Villanos — UCEVA, 6.° Semestre
* **Rama Git:** `feature/jhonatan-scrum-16-docker`

---

## 2. Objetivo y Justificación Técnica

El microservicio `ruta-service` combina código de alto rendimiento escrito en lenguaje C (`dijkstra.c` con optimización `-O2`) y una API REST desarrollada en Spring Boot 3 con Java 21 comunicada mediante JNA (Java Native Access).

En entornos de producción contenerizados, incluir herramientas de compilación (`gcc`, `make`, paquetes de desarrollo `build-base`) dentro de la imagen final acarrea serios problemas:
1. **Riesgo de Seguridad (Ciberseguridad/DevSecOps):** La presencia de un compilador en una imagen expuesta facilita a un atacante que logre ejecución remota de código compilar exploits nativos dentro del contenedor.
2. **Superficie de Ataque y Vulnerabilidades (CVEs):** Herramientas innecesarias aumentan el número de paquetes vulnerables detectados en escaneos de seguridad.
3. **Sobrecarga de Almacenamiento y Red:** Imágenes pesadas (> 800 MB) ralentizan el despliegue continuo (CI/CD) y consumen ancho de banda innecesario.

### Criterios de Aceptación
1. **Dockerfile Multietapa (Multi-Stage Build):**
   * **Etapa 1 (`c-builder`):** Compila `dijkstra.c` con `gcc -Wall -Wextra -O2 -std=c11 -shared -fPIC -o libgrafo.so` sobre imagen base Alpine.
   * **Etapa 2 (`jar-builder`):** Compila y empaqueta la aplicación Spring Boot (`mvn clean package -DskipTests`) utilizando JDK 21.
   * **Etapa 3 (Runtime de producción):** Imagen basada en `eclipse-temurin:21-jre-alpine`, copiando únicamente `libgrafo.so` a `/usr/lib/` y el archivo `app.jar`.
2. **Tamaño de Imagen:** Menor a 250 MB (resultado obtenido: ~180 MB).
3. **Higienización de Entorno:** Sin `gcc`, sin cabeceras (`.h`), sin código fuente C (`.c`), y sin herramientas de construcción en la imagen final.
4. **Orquestación con Docker Compose:** Archivo `docker-compose.yml` en la raíz que orquesta los tres microservicios (`api-gateway`: 8080, `grafo-service`: 8081, `ruta-service`: 8082) en la red bridge `red-de-rutas-net` con healthchecks en `/actuator/health`.

---

## 3. Arquitectura Multietapa (Multi-Stage) de ruta-service

```
+-------------------------------------------------------------+
| Etapa 1: c-builder (alpine:3.19 + build-base)               |
| Compila native/dijkstra.c -> /src/native/libgrafo.so        |
+-------------------------------------------------------------+
                               | (Solo libgrafo.so)
                               v
+-------------------------------------------------------------+
| Etapa 3: Runtime Final (eclipse-temurin:21-jre-alpine)      |
| Copia: /usr/lib/libgrafo.so                                 |
| Copia: /app/app.jar (de Etapa 2)                            |
| Resultado: < 250 MB | SIN GCC | SIN FUENTES .c/.h           |
+-------------------------------------------------------------+
                               ^
                               | (Solo app.jar)
+-------------------------------------------------------------+
| Etapa 2: jar-builder (maven:3.9-eclipse-temurin-21-alpine)   |
| Empaqueta Spring Boot JAR (mvn clean package)               |
+-------------------------------------------------------------+
```

---

## 4. Dockerfiles de Microservicios

### 4.1. backend/ruta-service/Dockerfile
```dockerfile
# Etapa 1: Compilacion C
FROM alpine:3.19 AS c-builder
RUN apk add --no-cache build-base
WORKDIR /src/native
COPY native/grafo.h native/dijkstra.c ./
RUN gcc -Wall -Wextra -O2 -std=c11 -shared -fPIC -o libgrafo.so dijkstra.c

# Etapa 2: Empaquetado Spring Boot
FROM maven:3.9-eclipse-temurin-21-alpine AS jar-builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 3: Runtime de Produccion (< 250 MB)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=c-builder /src/native/libgrafo.so /usr/lib/libgrafo.so
COPY --from=jar-builder /app/target/ruta-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Djna.library.path=/usr/lib", "-jar", "app.jar"]
```

### 4.2. backend/grafo-service/Dockerfile
```dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/grafo-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
```

### 4.3. backend/api-gateway/Dockerfile
```dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/api-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
```

---

## 5. Orquestación con docker-compose.yml

El archivo `docker-compose.yml` en la raíz del proyecto define:
* **Red interna:** `red-de-rutas-net` (driver: bridge), garantizando resolución de nombres por DNS de Docker (`http://grafo-service:8081`, `http://ruta-service:8082`).
* **Dependencias y Salud (`depends_on` con `service_healthy`):** El `api-gateway` espera a que `grafo-service` y `ruta-service` pasen sus pruebas de salud en `/actuator/health` antes de recibir peticiones de clientes.
* **Políticas de reinicio:** `restart: unless-stopped`.

---

## 6. Verificación y Resultados de Cumplimiento

| Criterio | Requisito | Resultado Obtenido | Estado |
| :--- | :--- | :--- | :--- |
| **Tamaño de imagen ruta-service** | < 250 MB | ~180 MB | Cumple |
| **Ausencia de GCC** | `which gcc` devuelve 1 / no encontrado | Verificado (GCC ausente en runtime) | Cumple |
| **Ausencia de código fuente C** | Sin archivos `.c` o `.h` en `/app` | Verificado (solo `libgrafo.so` en `/usr/lib/`) | Cumple |
| **Biblioteca nativa C** | Compilación `-O2` compartida (`.so`) | `/usr/lib/libgrafo.so` presente y enlazada con musl | Cumple |
| **Resolución DNS interna** | Comunicación entre servicios | Gateway -> Grafo/Ruta por nombres de contenedor | Cumple |
| **Healthchecks** | Monitoreo en `/actuator/health` | HTTP 200 OK en los 3 servicios | Cumple |

---

## 7. Instrucciones de Ejecución

### 7.1. Levantar el ecosistema completo
```bash
docker compose up --build -d
```

### 7.2. Verificar estado de los contenedores
```bash
docker compose ps
```

### 7.3. Probar los servicios a través del API Gateway
```bash
# Consultar estaciones/grafo
curl http://localhost:8080/api/grafo/paradas

# Calcular ruta mas corta con Dijkstra
curl "http://localhost:8080/api/ruta/calcular?origen=1&destino=10"
```

### 7.4. Detener el ecosistema
```bash
docker compose down
```
