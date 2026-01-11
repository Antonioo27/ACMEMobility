package it.unibo.acme.fleet.gateway.model;

/**
 * NATS command sent to tracking-service.
 */
public class StopTrackingCommand {
    public String vehicleId;
    public long ts;
    public String stationId;

    public StopTrackingCommand() {}
}
