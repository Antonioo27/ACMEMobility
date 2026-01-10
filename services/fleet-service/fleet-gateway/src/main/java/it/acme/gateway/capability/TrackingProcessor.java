package it.acme.tracking.capability;

import it.acme.tracking.model.VehicleTelemetry;
import it.acme.tracking.provider.InternalBusProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

@ApplicationScoped
public class TrackingProcessor {

    @Inject
    private InternalBusProvider busProvider;

    private final Jsonb jsonb = JsonbBuilder.create();

    public void processIncomingTelemetry(String json) {
        try {
            VehicleTelemetry t = jsonb.fromJson(json, VehicleTelemetry.class);
            
            // LOGICA DI BUSINESS: 
            // Ad esempio, scartiamo coordinate (0,0) o aggiorniamo timestamp
            if (t.getLat() != 0 && t.getLon() != 0) {
                
                // Passiamo al Provider per inviarlo al Gateway
                busProvider.publishCheckedUpdate(t);
            }
        } catch (Exception e) {
            System.err.println("Dati non validi: " + e.getMessage());
        }
    }
}