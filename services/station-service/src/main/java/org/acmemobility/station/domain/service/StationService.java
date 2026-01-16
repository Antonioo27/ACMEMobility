package org.acmemobility.station.domain.service;

import org.acmemobility.station.domain.model.Reservation;
import org.acmemobility.station.domain.model.Station;
import org.acmemobility.station.domain.model.Vehicle;

import java.util.List;

public interface StationService {
    Reservation reserve(String stationId, String vehicleId, String userId);

    Reservation cancelReservation(String stationId, String reservationId, String userId);

    UnlockResult unlock(String stationId, String vehicleId, String rentalId, String reservationId, String userId, String destinationStationId);

    LockResult lock(String stationId, String vehicleId, String rentalId);

    // --- QUERY / READ endpoints support ---
    List<Station> listStations();

    List<Vehicle> listVehicles();

    List<Vehicle> listVehiclesAtStation(String stationId);
}
