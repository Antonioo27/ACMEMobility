package org.acmemobility.station.domain.service.integration;

public interface VehicleCommandDispatcher {
    void sendUnlockCommand(String vehicleId, double destLat, double destLon);
    void sendLockCommand(String vehicleId);
}