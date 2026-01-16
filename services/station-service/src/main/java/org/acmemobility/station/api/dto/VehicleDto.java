package org.acmemobility.station.api.dto;

public class VehicleDto {
    public String vehicleId;
    public String vehicleState;

    // null se IN_USE (coerente col tuo dominio: currentStationId viene messo a null in startRental)
    public String currentStationId;

    // valorizzato quando DOCKED_RESERVED
    public String activeReservationId;

    // valorizzato quando IN_USE
    public String activeRentalId;
}
