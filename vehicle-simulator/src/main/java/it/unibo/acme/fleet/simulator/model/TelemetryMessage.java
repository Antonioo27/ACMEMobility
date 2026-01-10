package it.unibo.acme.fleet.simulator.model;

public class TelemetryMessage {
    public String vehicleId;
    public long ts;
    public double lat;
    public double lon;
    public int batteryPct;

    public TelemetryMessage() {}
}
