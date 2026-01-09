package org.acmemobility.station.domain.service;

import org.acmemobility.station.domain.model.Vehicle;

public class UnlockResult {
    private final Vehicle vehicle;
    private final String consumedReservationId; // può essere null se unlock senza reservation

    public UnlockResult(Vehicle vehicle, String consumedReservationId) {
        this.vehicle = vehicle;
        this.consumedReservationId = consumedReservationId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public String getConsumedReservationId() {
        return consumedReservationId;
    }
}
