package it.unibo.acme.mobility.api;

// Import del DTO appena creato
import it.unibo.acme.mobility.api.dto.StartRentalRequest;

// Import di Spring Web
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Import di Camunda/Zeebe
import io.camunda.zeebe.client.ZeebeClient;

// Import Java Collections
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rentals")
public class RentalController {

    @Autowired
    private ZeebeClient zeebeClient;

    @PostMapping("/start")
    public ResponseEntity<String> startRental(@RequestBody StartRentalRequest request) {
        
        // 1. Preparo le variabili per il processo
        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", request.getUserId());
        variables.put("requestType", request.getRequestType()); 
        // Nota: Nel worker bancario stiamo usando "cardNumber", "cardHolder" hardcoded.
        // In produzione dovresti mapparli dalla request.
        variables.put("creditCardToken", request.getCreditCardToken());
        variables.put("amount", 10.0);

        // 2. Avvio l'istanza del processo BPMN
        var processInstanceEvent = zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId("ACME_Rental_Process") // Assicurati che l'ID nel file BPMN sia questo!
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        return ResponseEntity.ok("Processo avviato: " + processInstanceEvent.getProcessInstanceKey());
    }
}