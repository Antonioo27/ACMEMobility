package it.unibo.acme.fleet.tracking.model;

public class TrackingSnapshot {
    public String vehicleId;
    public long ts;
    public boolean active;

    public Double lat; // nullable if no telemetry yet
    public Double lon; // nullable if no telemetry yet

    public double distanceMeters;

    public long startedAt;
    public long lastUpdateTs;

    public boolean stale;

    public TrackingSnapshot() {}
}
