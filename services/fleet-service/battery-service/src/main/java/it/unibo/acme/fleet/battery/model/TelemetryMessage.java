package it.unibo.acme.fleet.battery.model;

public class TelemetryMessage {
    public String vehicleId;
    public long ts;
    public double lat;
    public double lon;
    public Integer batteryPct;

    public TelemetryMessage() {}
}
