package org.acmemobility.station.domain.model;

import java.util.Objects;

public class Station {
    private final String stationId;
    private final double lat;
    private final double lon;

    public Station(String stationId, double lat, double lon) {
        if (stationId == null || stationId.isBlank()) {
            throw new IllegalArgumentException("stationId must not be blank");
        }
        this.stationId = stationId.trim();
        this.lat = lat;
        this.lon = lon;
    }

    public String getStationId() { return stationId; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }

    // equals, hashCode, toString rimangono simili (basati su ID)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Station other)) return false;
        return stationId.equals(other.stationId);
    }

    @Override
    public int hashCode() { return Objects.hash(stationId); }
}