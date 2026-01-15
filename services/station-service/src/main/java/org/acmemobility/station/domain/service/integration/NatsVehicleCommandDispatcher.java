package org.acmemobility.station.domain.service.integration;

import io.nats.client.Connection;
import io.nats.client.Nats;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject; // Importante
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class NatsVehicleCommandDispatcher implements VehicleCommandDispatcher {

    private static final Logger LOG = Logger.getLogger(NatsVehicleCommandDispatcher.class.getName());

    // Lasciamo l'injection, ma prepariamoci al fallimento
    @Inject
    @ConfigProperty(name = "nats.url", defaultValue = "nats://localhost:4222")
    String natsUrl;

    private Connection natsConnection;
    private final Jsonb jsonb = JsonbBuilder.create();

    @PostConstruct
    public void init() {
        // --- FIX DI SICUREZZA ---
        // Se l'injection ha fallito (è null), leggiamo direttamente l'ENV di Kubernetes
        String targetUrl = this.natsUrl;
        
        if (targetUrl == null) {
            LOG.warning("Injection of nats.url failed. Trying System.getenv('NATS_URL')...");
            targetUrl = System.getenv("NATS_URL");
        }
        
        // Se ancora null, usiamo il default di Kubernetes
        if (targetUrl == null) {
            targetUrl = "nats://fleet-nats:4222";
            LOG.warning("NATS_URL env not found. Fallback to hardcoded: " + targetUrl);
        }

        try {
            LOG.info("Attempting NATS connection to: " + targetUrl);
            this.natsConnection = Nats.connect(targetUrl);
            LOG.info(">>> SUCCESS: Connected to NATS at " + targetUrl);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "!!! CRITICAL: Failed to connect to NATS !!!", e);
            // Non blocchiamo l'avvio, ma il sistema sarà zoppo
        }
    }

    @PreDestroy
    public void close() {
        if (natsConnection != null) {
            try { natsConnection.close(); } catch (Exception e) { /* ignore */ }
        }
    }

    @Override
    public void sendUnlockCommand(String vehicleId, double destLat, double destLon) {
        if (natsConnection == null) {
            LOG.severe("CANNOT SEND UNLOCK: Nats connection is NULL");
            return;
        }

        try {
            var command = new CommandPayload("UNLOCK", destLat, destLon);
            String json = jsonb.toJson(command);
            String subject = "commands.vehicle." + vehicleId;
            
            natsConnection.publish(subject, json.getBytes(StandardCharsets.UTF_8));
            LOG.info(">>> SENT NATS UNLOCK COMMAND for " + vehicleId + " to subject " + subject);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error sending NATS message", e);
        }
    }

    @Override
    public void sendLockCommand(String vehicleId) {
        if (natsConnection == null) return;
        
        try {
            var command = new CommandPayload("LOCK", null, null);
            String json = jsonb.toJson(command);
            String subject = "commands.vehicle." + vehicleId;
            
            natsConnection.publish(subject, json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error sending lock", e);
        }
    }
    
    public static class CommandPayload {
        public String type;
        public Double destLat;
        public Double destLon;

        public CommandPayload(String type, Double lat, Double lon) {
            this.type = type;
            this.destLat = lat;
            this.destLon = lon;
        }
    }
}