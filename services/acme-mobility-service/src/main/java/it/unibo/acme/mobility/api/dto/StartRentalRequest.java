package it.unibo.acme.mobility.api.dto;

public class StartRentalRequest {
    private String userId;
    private String requestType; // "SCAN" o "BOOK"
    private String creditCardToken; // O i dati carta grezzi
    // Aggiungi altri campi se servono (es. dati carta completi)

    // Getter e Setter sono obbligatori per Jackson (JSON)
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getCreditCardToken() { return creditCardToken; }
    public void setCreditCardToken(String creditCardToken) { this.creditCardToken = creditCardToken; }
}