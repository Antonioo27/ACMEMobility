package it.unibo.acme.fleet.gateway.model;

/**
 * NATS command sent to battery-service.
 */
public class StartBatteryCommand {
    public String vehicleId;
    public long ts;
    public String stationId;

    public StartBatteryCommand() {}
}
