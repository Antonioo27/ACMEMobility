package it.unibo.acme.fleet.gateway.model;

/**
 * Copy of the snapshot published by tracking-service on:
 *   event.tracking.snapshot.<vehicleId>
 *
 * Note: fields are public + no-args ctor on purpose (JSON-B friendly).
 */
public class TrackingSnapshot {
    public String vehicleId;
    public long ts;
    public boolean active;

    public Double lat; // null if we never got telemetry for this vehicle
    public Double lon;

    public double distanceMeters;

    public long startedAt;
    public long lastUpdateTs;

    public boolean stale; // true if data is old / missing

    public TrackingSnapshot() {}
}
