package it.unibo.acme.fleet.gateway.model;

/**
 * REST payload coming from ACMEMobility towards Fleet Management.
 * It maps the choreography message track_stop.
 */
public class StopRequest {
    public String stationId;
    public long ts;

    public StopRequest() {}
}
