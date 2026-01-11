package it.unibo.acme.fleet.gateway.model;

/**
 * NATS command sent to battery-service.
 */
public class StopBatteryCommand {
    public String vehicleId;
    public long ts;
    public String stationId;

    public StopBatteryCommand() {}
}
