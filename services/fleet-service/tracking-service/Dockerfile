# Usa un'immagine JRE leggera (Temurin è ottima)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copia le librerie (Helidon separa le lib dal jar principale per caching efficiente)
COPY target/libs /app/libs

# Copia il JAR del servizio (cambia il nome per ogni servizio!)
# Esempio per il gateway: COPY target/fleet-gateway.jar /app/app.jar
COPY target/*.jar /app/app.jar

# Espone la porta (solo documentativo, K8s usa lo YAML)
EXPOSE 8080

# Comando di avvio
CMD ["java", "-jar", "/app/app.jar"]