package it.unibo.acme.fleet.tracking.model;

public class TelemetryMessage {
    public String vehicleId;
    public long ts;
    public double lat;
    public double lon;
    public Integer batteryPct; // optional (tracking doesn't care, but simulator may send it)

    public TelemetryMessage() {}
}
