package it.unibo.acme.fleet.gateway.model;

/**
 * Aggregated view returned by the Gateway.
 *
 * This is what ACMEMobility wants when it "polls" Fleet Management:
 * a single response that contains both tracking + battery information.
 */
public class VehicleStatus {
    public String vehicleId;

    /** When the Gateway assembled this response (epoch millis). */
    public long ts;

    /** Last snapshots observed for the vehicle (can be null if never received). */
    public TrackingSnapshot tracking;
    public BatterySnapshot battery;

    /**
     * Convenience flags:
     * - active: true if either sub-service considers the vehicle active
     * - stale: true if data is missing/old in any sub-service
     */
    public boolean active;
    public boolean stale;

    public VehicleStatus() {}
}
