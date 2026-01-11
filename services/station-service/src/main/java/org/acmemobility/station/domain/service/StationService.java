package org.acmemobility.station.domain.service;

import org.acmemobility.station.domain.model.Reservation;

public interface StationService {
    Reservation reserve(String stationId, String vehicleId, String userId);

    Reservation cancelReservation(String stationId, String reservationId, String userId);

    UnlockResult unlock(String stationId, String vehicleId, String rentalId, String reservationId, String userId);

    LockResult lock(String stationId, String vehicleId, String rentalId);
}
