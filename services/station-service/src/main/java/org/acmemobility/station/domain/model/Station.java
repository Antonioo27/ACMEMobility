package org.acmemobility.station.domain.model;

import java.util.Objects;

public class Station {
    private final String stationId;
    private final int capacity;

    public Station(String stationId, int capacity) {
        if (stationId == null || stationId.isBlank()) {
            throw new IllegalArgumentException("stationId must not be blank");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.stationId = stationId.trim();
        this.capacity = capacity;
    }

    public String getStationId() {
        return stationId;
    }

    public int getCapacity() {
        return capacity;
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
        return "Station{stationId='" + stationId + "', capacity=" + capacity + "}";
    }
}
