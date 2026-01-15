package it.unibo.acme.mobility.integration.bank;

// Import delle classi generate dal WSDL (il pacchetto dipende dal tuo pom.xml)
import it.unibo.acme.mobility.integration.bank.generated.BankPort;
import it.unibo.acme.mobility.integration.bank.generated.BankPortService;
import it.unibo.acme.mobility.integration.bank.generated.CardData;
import it.unibo.acme.mobility.integration.bank.generated.PaymentDeclined_Exception;

// Import standard di Spring per l'iniezione delle dipendenze e configurazione
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Import standard JAX-WS per gestire la connessione SOAP
import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.Handler;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Client SOAP per comunicare con il servizio bancario (Jolie).
 * Gestisce la creazione della richiesta, l'autenticazione (se necessaria)
 * e la correzione delle risposte "nude" di Jolie tramite un Handler.
 */
@Service
public class BankSoapClient {

    // Legge l'URL del servizio banca da application.properties.
    // Default: http://localhost:8000/BankService se non specificato.
    @Value("${bank.service.url:http://localhost:8000/BankService}")
    private String bankServiceUrl;

    /**
     * Esegue la pre-autorizzazione di un importo sulla carta di credito.
     * * @param amount L'importo da bloccare (es. 10.0)
     * @param cardNumber Numero carta
     * @param holder Titolare
     * @param expiry Scadenza
     * @param cvv CVV
     * @return Il token della transazione (String) restituito dalla banca.
     */
    public String preAuthorize(double amount, String cardNumber, String holder, String expiry, String cvv) {
        try {
            // 1. Caricamento del WSDL dal classpath
            // È importante caricare il WSDL locale per avere la definizione del servizio
            URL wsdlUrl = getClass().getClassLoader().getResource("wsdl/BankService.wsdl");
            if (wsdlUrl == null) {
                throw new RuntimeException("ERRORE GRAVE: File WSDL non trovato in src/main/resources/wsdl/BankService.wsdl");
            }

            // 2. Creazione del Servizio e della Porta (Stub)
            // BankPortService è la classe "Factory" generata da JAX-WS
            BankPortService service = new BankPortService(wsdlUrl);
            BankPort port = service.getBankPortServicePort();

            // 3. Configurazione Dinamica dell'Endpoint (BindingProvider)
            // Questo ci permette di cambiare l'URL del server senza ricompilare il WSDL
            BindingProvider bindingProvider = (BindingProvider) port;
            bindingProvider.getRequestContext().put(
                BindingProvider.ENDPOINT_ADDRESS_PROPERTY, 
                bankServiceUrl
            );

            // =================================================================================
            // 4. INIEZIONE DELL'HANDLER (LA SOLUZIONE AL PROBLEMA NAMESPACE)
            // =================================================================================
            // Jolie risponde con XML senza namespace ({}preAuthorizeResponse), ma Java si aspetta
            // un namespace ({http://acmemobility.org...}). L'Handler corregge la risposta al volo.
            
            // Otteniamo la catena di handler attuale
            List<Handler> handlerChain = bindingProvider.getBinding().getHandlerChain();
            if (handlerChain == null) {
                handlerChain = new ArrayList<>();
            }
            
            // Aggiungiamo il nostro correttore (BankResponseHandler)
            handlerChain.add(new BankResponseHandler());
            
            // Aggiorniamo la catena sulla porta
            bindingProvider.getBinding().setHandlerChain(handlerChain);
            // =================================================================================

            // 5. Preparazione dei Dati (DTO Generato)
            // Creiamo l'oggetto CardData definito nello schema XSD
            CardData card = new CardData();
            card.setCardNumber(cardNumber);
            card.setHolderName(holder);
            card.setExpiryDate(expiry);
            card.setCvv(cvv);

            // 6. Chiamata Effettiva al Servizio Remoto
            // JAX-WS in modalità "Wrapper Style" spacchetta i parametri.
            // Invece di passare un oggetto 'PreAuthorizeRequest', passiamo direttamente (double, CardData).
            // E ci restituisce direttamente la String (il token) invece di 'PreAuthorizeResponse'.
            
            // NOTA: Se qui ti da errore di compilazione "Incompatible types", vedi commento sotto.
            String token = port.preAuthorize(amount, card);
            
            /* CASO ALTERNATIVO (se JAX-WS non spacchetta il ritorno):
               it.unibo.acme.mobility.integration.bank.generated.PreAuthorizeResponse response = port.preAuthorize(amount, card);
               String token = response.getToken();
            */

            return token;

        } catch (PaymentDeclined_Exception e) {
            // 7. Gestione Errori di Business (es. Fondi Insufficienti)
            // Questo catch cattura il Fault SOAP definito nel WSDL
            throw new RuntimeException("BANK_DECLINED: " + e.getFaultInfo().getReason());
            
        } catch (Exception e) {
            // 8. Gestione Errori Tecnici (es. Banca irraggiungibile, XML malformato, ecc.)
            throw new RuntimeException("BANK_ERROR: " + e.getMessage(), e);
        }
    }
}