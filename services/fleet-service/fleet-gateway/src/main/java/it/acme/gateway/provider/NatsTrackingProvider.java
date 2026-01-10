package it.acme.tracking.provider;

import io.nats.client.*;
import it.acme.tracking.model.VehicleTelemetry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Questo provider agisce come un'antenna.
 * Riceve dati da NATS e li tiene in RAM.
 * NON applica logica di business.
 */
@ApplicationScoped
public class NatsTrackingProvider {
    private static final Logger LOGGER = Logger.getLogger(NatsTrackingProvider.class.getName());

    @Inject
    @ConfigProperty(name = "nats.url", defaultValue = "nats://localhost:4222")
    String natsUrl;

    // Store interno: Mappa ID -> Ultima Telemetria
    private final Map<String, VehicleTelemetry> vehicleStore = new ConcurrentHashMap<>();
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Connection connection;
    private Dispatcher dispatcher;

    @PostConstruct
    void start() {
        // RETRY LOGIC (Come nel codice del Prof)
        for (int attempt = 1; attempt <= 15; attempt++) {
            try {
                connection = Nats.connect(natsUrl);
                this.running.set(true);
                
                // Creiamo il consumatore "fire-and-forget"
                dispatcher = connection.createDispatcher(msg -> {
                    onMessageReceived(new String(msg.getData()));
                });
                
                dispatcher.subscribe("telemetry.vehicle.*");
                LOGGER.info("Tracking Provider connesso a NATS.");
                return;
            } catch (Exception e) {
                LOGGER.warning("NATS connection failed (attempt " + attempt + ")");
                try { Thread.sleep(2000); } catch (InterruptedException ex) {}
            }
        }
        throw new RuntimeException("Impossibile connettersi a NATS.");
    }

    private void onMessageReceived(String json) {
        if (!running.get()) return;
        try {
            Jsonb jsonb = JsonbBuilder.create();
            VehicleTelemetry t = jsonb.fromJson(json, VehicleTelemetry.class);
            
            // Logica MINIMA di infrastruttura: salvo il dato così com'è.
            // Se il JSON è valido, lo consideriamo buono.
            if (t.getVehicleId() != null) {
                vehicleStore.put(t.getVehicleId(), t);
            }
        } catch (Exception e) {
            LOGGER.severe("Errore deserializzazione JSON: " + e.getMessage());
        }
    }

    // Metodo esposto alla Capability per leggere i dati
    public VehicleTelemetry getLatestTelemetry(String vehicleId) {
        return vehicleStore.get(vehicleId);
    }
    
    public Map<String, VehicleTelemetry> getAll() {
        return vehicleStore;
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (connection != null) try { connection.close(); } catch (Exception e) {}
    }
}