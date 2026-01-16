package it.unibo.acme.mobility.bpnm;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class NotificationWorker {

    @JobWorker(type = "notify-user")
    public void sendNotification(final ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();

        String userId = (String) vars.get("userId");
        // Possiamo recuperare variabili specifiche per personalizzare il messaggio
        String vehicleId = (String) vars.get("vehicleId");
        
        // In uno scenario reale, qui chiameresti un servizio di notifiche (Firebase, Email, SMS)
        // Per ora simuliamo con un log ben visibile.
        
        System.out.println("=================================================");
        System.out.println(" [NOTIFICATION SERVICE] -> To: " + userId);
        System.out.println(" Messaggio: Gentile cliente, ci dispiace ma non è");
        System.out.println(" stato possibile sbloccare il veicolo " + vehicleId + ".");
        System.out.println(" L'importo pre-autorizzato verrà rilasciato a breve.");
        System.out.println("=================================================");
        
        // Non ritorniamo nulla, il task finisce e il token va all'End Event.
    }
}