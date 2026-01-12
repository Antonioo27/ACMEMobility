# Usa la stessa base immagine degli altri (Java 21)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 1. Copia le librerie (SE ESISTE LA CARTELLA target/libs)
# Se il tuo simulatore è un "Fat JAR" (tutto in uno), commenta o rimuovi questa riga
COPY target/libs /app/libs

# 2. Copia il JAR del simulatore
# Il *.jar prende qualsiasi nome abbia il file
COPY target/*.jar /app/app.jar

# Nota: Non serve EXPOSE 8080 perché il simulatore non riceve chiamate,
# ma solo invia dati verso NATS. Puoi lasciarlo o toglierlo, non cambia nulla.

# 3. Avvia
CMD ["java", "-jar", "/app/app.jar"]