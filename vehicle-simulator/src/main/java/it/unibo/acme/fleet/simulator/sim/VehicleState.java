package it.unibo.acme.fleet.simulator.sim;

import it.unibo.acme.fleet.simulator.model.Station;

public class VehicleState {
    public final String vehicleId;

    public double batteryPct;

    public Station from;
    public Station to;
    public long routeStartTs;
    public long routeEndTs;

    public double lastLat;
    public double lastLon;

    public VehicleState(String vehicleId) {
        this.vehicleId = vehicleId;
    }
}
