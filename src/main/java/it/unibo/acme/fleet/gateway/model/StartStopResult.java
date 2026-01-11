package it.unibo.acme.fleet.gateway.model;

/**
 * Response of /start and /stop endpoints.
 * We keep per-service results so debugging is painless.
 */
public class StartStopResult {
    public String vehicleId;
    public long ts;

    public CommandResponse tracking;
    public CommandResponse battery;

    public String status; // OK | ERROR (derived)
    public String message;

    public StartStopResult() {}
}
