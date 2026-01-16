package it.unibo.acme.mobility.bpnm;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.camunda.zeebe.spring.client.exception.ZeebeBpmnError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class StationWorker {

    @Value("${station.service.url:http://localhost:8080}") // Default localhost per test
    private String stationServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @JobWorker(type = "station-unlock")
    public Map<String, Object> handleUnlock(final ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();

        String stationId = (String) vars.get("stationId");
        String vehicleId = (String) vars.get("vehicleId");
        String userId = (String) vars.get("userId");
        String destinationStationId = (String) vars.get("destinationStationId");
        String reservationId = (String) vars.get("reservationId");

        // Generazione Rental ID
        String rentalId = "RENT-" + UUID.randomUUID().toString().substring(0, 8);

        // Preparazione Body
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("rentalId", rentalId);
        requestBody.put("userId", userId);
        requestBody.put("destinationStationId", destinationStationId);
        if (reservationId != null) {
            requestBody.put("reservationId", reservationId);
        }

        try {
            System.out.println("[WORKER] Tentativo sblocco: " + vehicleId + " @ " + stationId);

            String url = String.format("%s/stations/%s/vehicles/%s/unlock", 
                                       stationServiceUrl, stationId, vehicleId);

            // Chiamata REST
            restTemplate.postForObject(url, requestBody, Map.class);

            System.out.println("[WORKER] Sblocco RIUSCITO. RentalID: " + rentalId);

            // Successo: restituisco le variabili
            return Map.of(
                "rentalId", rentalId,
                "vehicleStatus", "IN_USE"
            );

        } catch (HttpClientErrorException e) {
            // ERRORI 4xx (Client Error): Sono errori di business.
            // NON ha senso riprovare (il veicolo non apparirà magicamente).
            // Lanciamo un BPMN Error che viene catturato dal Boundary Event.
            
            String errorMsg = "Errore Client: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
            System.err.println("[WORKER] " + errorMsg);

            // Mappiamo tutto su UNLOCK_FAILED, oppure potresti averne diversi
            throw new ZeebeBpmnError("UNLOCK_FAILED", errorMsg);

        } catch (HttpServerErrorException | ResourceAccessException e) {
            // ERRORI 5xx (Server Error) o Network (Timeout): Sono errori tecnici.
            // Lanciamo RuntimeException così Zeebe RIPROVA (Retries) automaticamente.
            System.err.println("[WORKER] Errore Tecnico (verrà riprovato): " + e.getMessage());
            throw new RuntimeException("Errore comunicazione con Station Service", e);
            
        } catch (Exception e) {
            // Catch-all per bug imprevisti nel codice Java
            e.printStackTrace();
            throw new RuntimeException("Errore interno worker", e);
        }
    }

    @JobWorker(type = "station-lock")
    public void handleLock(final ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        
        String stationId = (String) vars.get("stationId"); 
        // In uno scenario reale la stazione di arrivo potrebbe essere diversa.
        // Per ora assumiamo che torni alla stessa stazione o che stationId nel processo venga aggiornato.
        // Se vuoi supportare stazioni diverse, dovresti passare 'endStationId' nel messaggio di Stop
        // e aggiornare le variabili. Per ora usiamo quella di partenza per semplicità.
        
        String vehicleId = (String) vars.get("vehicleId");
        String rentalId = (String) vars.get("rentalId");

        System.out.println("[STATION WORKER] Richiesta blocco veicolo: " + vehicleId + " (Rental: " + rentalId + ")");

        // Endpoint: POST /stations/{stationId}/vehicles/{vehicleId}/lock
        String url = String.format("%s/stations/%s/vehicles/%s/lock", 
                                   stationServiceUrl, stationId, vehicleId);

        Map<String, String> body = new HashMap<>();
        body.put("rentalId", rentalId);

        try {
            restTemplate.postForObject(url, body, Map.class);
            System.out.println("[STATION WORKER] Veicolo bloccato con successo.");
        } catch (Exception e) {
            System.err.println("[STATION WORKER] Errore blocco veicolo: " + e.getMessage());
            // Lanciamo eccezione per far gestire l'incidente a Camunda (o riprovare)
            throw new RuntimeException("Errore blocco veicolo", e);
        }
    }
}