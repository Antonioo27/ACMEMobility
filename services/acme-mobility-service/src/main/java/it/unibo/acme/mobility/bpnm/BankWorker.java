package it.unibo.acme.mobility.bpnm;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.camunda.zeebe.spring.client.exception.ZeebeBpmnError;
import it.unibo.acme.mobility.integration.bank.BankSoapClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class BankWorker {

    @Autowired
    private BankSoapClient bankClient;

    @JobWorker(type = "bank-auth") 
    public Map<String, Object> handleBankAuth(final ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        
        // Usa valori sicuri se mancano nelle variabili
        String cardNum = (String) vars.getOrDefault("cardNumber", "1234-1234-1234-1234");
        String holder = (String) vars.getOrDefault("cardHolder", "Utente Test");
        
        try {
            System.out.println("BankWorker: Invio richiesta SOAP per " + cardNum);
            
            // Esegui la chiamata
            String token = bankClient.preAuthorize(10.0, cardNum, holder, "12/25", "123");
            
            System.out.println("BankWorker: Risposta ricevuta. Token = " + token);

            if (token == null) {
                // Se il token è null, significa che il namespace è ancora sbagliato
                // Lancia un errore chiaro invece di NPE
                throw new RuntimeException("ERRORE GRAVE: Il token della banca è NULL! (Probabile mismatch namespace)");
            }

            // Usa HashMap o Collections.singletonMap che sono più sicuri, 
            // ma se il token è null abbiamo già lanciato eccezione sopra.
            return Map.of("bankToken", token);
            
        } catch (RuntimeException e) {
            // Gestione errori business (es. fondi insufficienti)
            if (e.getMessage() != null && e.getMessage().contains("BANK_DECLINED")) {
                throw new ZeebeBpmnError("BANK_AUTH_FAILED", "Fondi insufficienti");
            }
            // Log dell'errore tecnico
            e.printStackTrace();
            throw e; 
        }
    }
}