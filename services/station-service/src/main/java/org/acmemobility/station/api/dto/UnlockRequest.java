package org.acmemobility.station.api.dto;

public class UnlockRequest {
    public String rentalId;       // Obbligatorio
    public String userId;         // Obbligatorio
    public String reservationId;  // Opzionale
    
    // NUOVO CAMPO: Dove vuoi andare?
    public String destinationStationId; 
}