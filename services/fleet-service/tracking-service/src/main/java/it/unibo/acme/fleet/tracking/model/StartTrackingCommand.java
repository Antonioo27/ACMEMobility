package it.unibo.acme.fleet.tracking.model;

public class StartTrackingCommand {
    public String vehicleId;
    public long ts;
    public String stationId;

    public StartTrackingCommand() {}
}
