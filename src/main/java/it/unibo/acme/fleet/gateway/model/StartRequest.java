package it.unibo.acme.fleet.gateway.model;

/**
 * REST payload coming from ACMEMobility towards Fleet Management.
 * It maps the choreography message track_start.
 */
public class StartRequest {
    public String stationId;
    public long ts; // optional client timestamp (0 if unknown)

    public StartRequest() {}
}
