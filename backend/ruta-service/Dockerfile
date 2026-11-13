# =========================================================================
# Etapa 1: Compilacion de biblioteca nativa en C con GCC (-O2)
# =========================================================================
FROM alpine:3.19 AS c-builder

RUN apk add --no-cache build-base

WORKDIR /src/native
COPY native/grafo.h native/dijkstra.c ./
RUN gcc -Wall -Wextra -O2 -std=c11 -shared -fPIC -o libgrafo.so dijkstra.c

# =========================================================================
# Etapa 2: Empaquetado del JAR de Spring Boot con Maven
# =========================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS jar-builder

WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn clean package -DskipTests

# =========================================================================
# Etapa 3: Imagen de produccion ultraligera (< 250 MB)
# Criterio de aceptacion: < 250 MB, sin GCC ni codigo fuente C
# =========================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copiar biblioteca compartida compilada a la ruta de bibliotecas del sistema
COPY --from=c-builder /src/native/libgrafo.so /usr/lib/libgrafo.so

# Copiar el JAR compilado de Spring Boot
COPY --from=jar-builder /app/target/ruta-service-*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-Djna.library.path=/usr/lib", "-jar", "app.jar"]
