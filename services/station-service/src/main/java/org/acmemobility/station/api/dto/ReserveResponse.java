package org.acmemobility.station.api.dto;

import java.time.Instant;

public class ReserveResponse {
    public String reservationId;
    public String stationId;
    public String vehicleId;
    public String status;
    public Instant expiresAt;
}
