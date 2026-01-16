package it.unibo.acme.mobility.api;

import it.unibo.acme.mobility.api.dto.StartRentalRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate; // Usato per le chiamate HTTP

import io.camunda.zeebe.client.ZeebeClient;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rentals")
public class RentalController {

    @Autowired
    private ZeebeClient zeebeClient;

    // Leggiamo l'URL del fleet service dal file properties
    @Value("${fleet.service.url:http://localhost:8100}") 
    private String fleetServiceUrl;

    // Istanziamo un RestTemplate per fare le chiamate verso Fleet
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * AVVIO NOLEGGIO
     * Crea l'istanza del processo su Camunda.
     */
    @PostMapping("/start")
    public ResponseEntity<String> startRental(@RequestBody StartRentalRequest request) {
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", request.getUserId());
        variables.put("requestType", request.getRequestType()); 
        variables.put("creditCardToken", request.getCreditCardToken());
        variables.put("vehicleId", request.getVehicleId());
        variables.put("stationId", request.getStationId());
        
        // --- CORREZIONE QUI SOTTO ---
        // Il campo destinationStationId era stato dimenticato!
        // Se l'utente non lo passa, possiamo mettere un default o passarlo null (ma lo Station Service fallirà se è null)
        String dest = request.getDestinationStationId();
        if (dest == null || dest.isBlank()) {
             // Opzionale: gestire un default o lanciare errore se la logica lo richiede
             // dest = "S01"; 
        }
        variables.put("destinationStationId", dest);
        // -----------------------------
        
        variables.put("amount", 10.0); 

        var processInstanceEvent = zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId("ACME_Rental_Process") 
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        return ResponseEntity.ok("Processo avviato: " + processInstanceEvent.getProcessInstanceKey());
    }
    /**
     * STOP NOLEGGIO
     * Invece di eseguire logica java, invia un MESSAGGIO a Camunda.
     * Questo sblocca l'Event Gateway nel BPMN e fa uscire dal loop di tracking.
     */
    @PostMapping("/{rentalId}/stop")
    public ResponseEntity<String> stopRental(@PathVariable String rentalId) {
        
        System.out.println("[ACME API] Richiesta STOP per rentalId: " + rentalId);

        // Pubblica il messaggio verso il motore BPMN
        zeebeClient.newPublishMessageCommand()
                .messageName("Message_StopRental") // Deve coincidere col nome nel BPMN
                .correlationKey(rentalId)          // Fondamentale: deve matchare la variabile 'rentalId' del processo
                .send()
                .join();

        return ResponseEntity.ok("Richiesta di stop inviata al motore di processo.");
    }

    /**
     * STATUS VEICOLO (Smart Proxy)
     * Il client chiama questo endpoint per sapere dov'è il veicolo.
     * ACME non guarda su Camunda, ma interroga direttamente il Fleet Gateway.
     * Pattern CQRS: Camunda gestisce lo stato "legale", Fleet i dati "real-time".
     */
    @GetMapping("/{vehicleId}/status")
    public ResponseEntity<Object> getVehicleStatus(@PathVariable String vehicleId) {
        
        // Costruiamo l'URL verso il Fleet Gateway (porta 8100)
        String url = fleetServiceUrl + "/fleet/vehicles/" + vehicleId + "/status";
        
        try {
            // Chiamata GET diretta
            Object fleetResponse = restTemplate.getForObject(url, Object.class);
            return ResponseEntity.ok(fleetResponse);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Errore contatto Fleet Service: " + e.getMessage());
        }
    }
}