package it.unibo.acme.fleet.simulator.model;

public class Station {
    public String id;
    public double lat;
    public double lon;

    public Station() {}

    public Station(String id, double lat, double lon) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
    }
}
