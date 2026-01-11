package it.unibo.acme.fleet.gateway.model;

/**
 * Copy of the snapshot published by battery-service on:
 *   event.battery.snapshot.<vehicleId>
 */
public class BatterySnapshot {
    public String vehicleId;
    public long ts;

    public boolean active;

    public Integer batteryPct;   // null if we never got telemetry for this vehicle
    public boolean lowBattery;

    public long startedAt;
    public long lastUpdateTs;
    public boolean stale;

    public BatterySnapshot() {}
}
