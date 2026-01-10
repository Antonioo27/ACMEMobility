package it.unibo.acme.fleet.battery.model;

public class BatterySnapshot {
    public String vehicleId;
    public long ts;

    public boolean active;

    public Integer batteryPct;   // nullable if never received
    public boolean lowBattery;

    public long startedAt;
    public long lastUpdateTs;
    public boolean stale;

    public BatterySnapshot() {}
}
