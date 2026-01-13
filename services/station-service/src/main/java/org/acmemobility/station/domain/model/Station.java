package org.acmemobility.station.domain.model;

import java.util.Objects;

public class Station {
    private final String stationId;

    public Station(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            throw new IllegalArgumentException("stationId must not be blank");
        }
        this.stationId = stationId.trim();
    }

    public String getStationId() {
        return stationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Station other)) return false;
        return stationId.equals(other.stationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stationId);
    }

    @Override
    public String toString() {
        return "Station{stationId='" + stationId + "'}";
    }
}
