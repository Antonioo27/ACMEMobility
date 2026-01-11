# syntax=docker/dockerfile:1

# 1) Build stage
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app

# Copia solo pom per sfruttare cache dipendenze
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

# Copia sorgenti e build
COPY src ./src
RUN mvn -B -ntp -DskipTests package

# 2) Runtime stage (più leggero del full JDK)
FROM eclipse-temurin:21-jre
WORKDIR /app

# (consigliato) utente non-root
RUN useradd -r -u 10001 -g root appuser

# Copia jar + libs (Helidon spesso produce libs/ con dipendenze)
COPY --from=build /app/target/station-service.jar ./station-service.jar
COPY --from=build /app/target/libs ./libs

# Helidon deve ascoltare su 0.0.0.0 in container
ENV SERVER_HOST=0.0.0.0
ENV SERVER_PORT=8080

EXPOSE 8080
USER 10001

CMD ["java", "-jar", "station-service.jar"]
