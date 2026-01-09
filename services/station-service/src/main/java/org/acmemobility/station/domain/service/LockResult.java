package org.acmemobility.station.domain.service;

import org.acmemobility.station.domain.model.Vehicle;

public class LockResult {
    private final Vehicle vehicle;
    private final String closedRentalId;

    public LockResult(Vehicle vehicle, String closedRentalId) {
        this.vehicle = vehicle;
        this.closedRentalId = closedRentalId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public String getClosedRentalId() {
        return closedRentalId;
    }
}
