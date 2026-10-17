# Imagen multi-stage de bank-customer-service.
# Etapa 1: compila con Maven y JDK 17. Etapa 2: solo el jar sobre un JRE 17 liviano.

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# Primero solo el pom para cachear las dependencias entre builds. El repositorio
# Maven es un cache de BuildKit compartido por todas las imagenes (id bank-m2):
# cada dependencia se descarga una sola vez.
COPY pom.xml ./
RUN --mount=type=cache,id=bank-m2,target=/root/.m2 mvn -B -q dependency:go-offline
COPY checkstyle.xml lombok.config ./
COPY src ./src
RUN --mount=type=cache,id=bank-m2,target=/root/.m2 mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S bank && adduser -S bank -G bank
COPY --from=build /workspace/target/bank-customer-service-1.0.0-SNAPSHOT.jar app.jar
USER bank
EXPOSE 8081
# Sin credenciales en la imagen: toda la configuracion llega por variables de
# entorno (CONFIG_SERVER_URL, EUREKA_URL, MONGO_URI) y desde el Config Server.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
