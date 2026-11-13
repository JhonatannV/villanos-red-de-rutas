# =========================================================================
# Etapa 1: Empaquetado del JAR de Spring Boot con Maven
# =========================================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn clean package -DskipTests

# =========================================================================
# Etapa 2: Imagen de produccion ultraligera (< 250 MB)
# =========================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/grafo-service-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
