package it.unibo.acme.mobility.api.dto;

public class StartRentalRequest {
    private String userId;
    private String requestType; // "SCAN" o "BOOK"
    private String creditCardToken; // O i dati carta grezzi
    private String vehicleId;
    private String stationId;
    private String destinationStationId;

    public String getDestinationStationId() {
        return destinationStationId;
    }
    public void setDestinationStationId(String destinationStationId) {
        this.destinationStationId = destinationStationId;
    }

    // Getter e Setter sono obbligatori per Jackson (JSON)
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getCreditCardToken() { return creditCardToken; }
    public void setCreditCardToken(String creditCardToken) { this.creditCardToken = creditCardToken; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }
}