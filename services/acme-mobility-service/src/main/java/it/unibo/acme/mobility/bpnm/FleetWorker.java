package it.unibo.acme.mobility.bpnm;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class FleetWorker {

    @Value("${fleet.service.url:http://localhost:8100}")
    private String fleetServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @JobWorker(type = "fleet-start-tracking")
    public void startTracking(final ActivatedJob job) {
        // ... (Codice uguale a prima) ...
        Map<String, Object> vars = job.getVariablesAsMap();
        String vehicleId = (String) vars.get("vehicleId");
        String stationId = (String) vars.get("stationId");

        System.out.println("[FLEET WORKER] Start tracking request per veicolo: " + vehicleId);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("stationId", stationId);
        requestBody.put("ts", System.currentTimeMillis());

        try {
            String url = String.format("%s/fleet/vehicles/%s/start", fleetServiceUrl, vehicleId);
            restTemplate.postForEntity(url, requestBody, String.class);
            System.out.println("[FLEET WORKER] Tracking avviato con successo su Fleet Service.");
        } catch (Exception e) {
            System.err.println("[FLEET WORKER] Errore start tracking: " + e.getMessage());
            throw new RuntimeException("Errore comunicazione Fleet", e);
        }
    }

    @JobWorker(type = "fleet-update-status")
    public Map<String, Object> updateStatus(final ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        String vehicleId = (String) vars.get("vehicleId");
        String rentalId = (String) vars.get("rentalId");

        System.out.println("\n-----------------------------------------------------------");
        System.out.println("[ACME LOOP] ⏳ POLLING ATTIVO (ogni 10s) - Rental: " + rentalId);

        try {
            String url = String.format("%s/fleet/vehicles/%s/status", fleetServiceUrl, vehicleId);
            Map response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                Map batteryInfo = (Map) response.get("battery");
                Map trackingInfo = (Map) response.get("tracking");
                
                // --- FIX QUI: Usiamo un metodo helper per la conversione sicura ---
                Double bat = getSafeDouble(batteryInfo, "batteryPct");
                Double dist = getSafeDouble(trackingInfo, "distanceMeters");
                // ----------------------------------------------------------------

                System.out.println(String.format("[ACME LOOP] ✅ Dati Ricevuti -> Batt: %s%%, Dist: %s m", bat, dist));
                System.out.println("-----------------------------------------------------------\n");

                Map<String, Object> outputVars = new HashMap<>();
                if (bat != null) outputVars.put("currentBatteryLevel", bat);
                if (dist != null) outputVars.put("kmTraveled", dist);
                
                return outputVars;
            }

        } catch (Exception e) {
            // Stampiamo lo stack trace per capire meglio se succede altro
            System.err.println("[ACME LOOP] ❌ Errore polling: " + e.getMessage());
            e.printStackTrace(); 
        }
        
        return null; 
    }

    @JobWorker(type = "fleet-stop-tracking")
    public Map<String, Object> stopTracking(final ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        String vehicleId = (String) vars.get("vehicleId");
        String stationId = (String) vars.get("stationId"); // Stazione dove abbiamo parcheggiato

        System.out.println("[FLEET WORKER] Richiesto STOP tracking per veicolo: " + vehicleId);

        // Prepariamo body per Fleet Gateway
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("stationId", stationId);
        requestBody.put("ts", System.currentTimeMillis());

        try {
            // POST http://localhost:8100/fleet/vehicles/{id}/stop
            String url = String.format("%s/fleet/vehicles/%s/stop", fleetServiceUrl, vehicleId);
            
            // Il gateway risponde con StartStopResult che contiene lo stato finale
            Map response = restTemplate.postForObject(url, requestBody, Map.class);
            
            System.out.println("[FLEET WORKER] Tracking fermato. Analisi dati finali...");

            // Estrarre i dati finali dalla risposta del Gateway
            // Struttura attesa: { "tracking": { "message": "...", "distanceMeters": 1500.0, ... }, "battery": ... }
            // Nota: Il tuo Fleet Gateway attuale nel metodo 'stop' restituisce 'StartStopResult' 
            // che contiene 'CommandResponse' per tracking e battery. 
            // Le CommandResponse attuali sono semplici (status, message).
            // PERO', hai detto "chiediamo per l'ultima volta lo stato". 
            // Se il Gateway 'stop' non ritorna i KM, dobbiamo fare una chiamata extra 'status' PRIMA o DOPO.
            
            // Approccio Migliore: Facciamo una chiamata esplicita allo /status SUBITO PRIMA o DOPO lo stop
            // per essere sicuri di avere i dati. Facciamola qui dentro.
            
            String statusUrl = String.format("%s/fleet/vehicles/%s/status", fleetServiceUrl, vehicleId);
            Map statusResponse = restTemplate.getForObject(statusUrl, Map.class);
            
            double finalKm = 0.0;
            double finalBattery = 0.0;

            if (statusResponse != null) {
                Map trackingInfo = (Map) statusResponse.get("tracking");
                Map batteryInfo = (Map) statusResponse.get("battery");
                
                if (trackingInfo != null) {
                    finalKm = getSafeDouble(trackingInfo, "distanceMeters") / 1000.0; // Convertiamo in KM
                }
                if (batteryInfo != null) {
                    finalBattery = getSafeDouble(batteryInfo, "batteryPct");
                }
            }

            System.out.println(String.format("[FLEET WORKER] Dati Finali -> Km: %.2f, Batt: %.0f%%", finalKm, finalBattery));

            // Restituiamo le variabili che serviranno al BankWorker
            return Map.of(
                "finalKm", finalKm,
                "finalBattery", finalBattery
            );

        } catch (Exception e) {
            System.err.println("[FLEET WORKER] Errore stop tracking: " + e.getMessage());
            throw new RuntimeException("Errore fleet stop", e);
        }
    }

    // Helper per convertire Integer/Long/Double/BigDecimal in Double senza ClassCastException
    private Double getSafeDouble(Map map, String key) {
        if (map == null || !map.containsKey(key)) return null;
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return null;
    }
}