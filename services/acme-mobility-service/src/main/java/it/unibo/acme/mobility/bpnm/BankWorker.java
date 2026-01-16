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

    // NUOVO WORKER PER IL RILASCIO
    @JobWorker(type = "bank-release")
    public void handleReleaseDeposit(final ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        
        // Recuperiamo il token bancario salvato nel processo durante la fase di Start
        String token = (String) vars.get("bankToken");
        
        if (token == null) {
            System.err.println("BankWorker: Impossibile rilasciare cauzione, token mancante!");
            return; 
        }

        System.out.println("BankWorker: Richiesto rilascio cauzione per token " + token);
        
        // Chiamata al client
        bankClient.releaseDeposit(token);
        
        // Non serve restituire variabili, il lavoro è fatto.
    }

    @JobWorker(type = "bank-charge")
    public Map<String, Object> handleFinalPayment(final ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        String token = (String) vars.get("bankToken");
        
        // Recuperiamo i dati passati da FleetWorker.stopTracking
        Double finalKm = (Double) vars.getOrDefault("finalKm", 0.0);
        Double finalBattery = (Double) vars.getOrDefault("finalBattery", 100.0);
        
        System.out.println("[BANK WORKER] Calcolo costo finale...");

        // --- Logica di Business per il prezzo (Semplificata) ---
        double pricePerKm = 1.50; // Euro al Km
        double amount = finalKm * pricePerKm;
        
        // Penale batteria < 15%
        if (finalBattery < 15.0) {
            System.out.println("[BANK WORKER] ⚠️ Applicazione penale 10% (Batteria scarica)");
            amount = amount * 1.10; 
        }

        // Minimo di addebito 1 euro se hai guidato poco o nulla
        if (amount < 1.0) amount = 1.0;

        // Arrotondamento a 2 decimali
        amount = Math.round(amount * 100.0) / 100.0;

        System.out.println(String.format("[BANK WORKER] Totale da addebitare: € %.2f (Km: %.2f)", amount, finalKm));

        try {
            // Chiamata SOAP reale
            // Nota: Se hai generato il metodo processFinalPayment nel client fallo qui.
            // Altrimenti, per testare il flusso usiamo un log o captureDeposit se il WSDL non è pronto.
            
            bankClient.processFinalPayment(token, amount); 
            System.out.println("[BANK WORKER] Chiamata SOAP verso Banca simulata con successo.");

            // Rilasciamo eventuali residui (concettualmente la banca fa tutto in una transazione "capture", 
            // ma se il token era una pre-auth, ora finalizziamo).
            
            return Map.of(
                "paymentStatus", "COMPLETED", 
                "finalCost", amount
            );

        } catch (Exception e) {
             System.err.println("[BANK WORKER] Errore critico nel pagamento: " + e.getMessage());
             // Qui potresti lanciare un errore BPMN specifico per gestire il fallimento manuale
             throw new RuntimeException("Errore pagamento finale", e);
        }
    }
}