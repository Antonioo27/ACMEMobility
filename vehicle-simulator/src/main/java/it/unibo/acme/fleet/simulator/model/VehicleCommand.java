package it.unibo.acme.fleet.simulator.model;

public class VehicleCommand {
    public enum Type {
        UNLOCK, // Richiede destinazione
        LOCK    // Ferma il veicolo
    }

    public Type type;
    
    // Usati solo se type == UNLOCK
    public Double destLat;
    public Double destLon;

    public VehicleCommand() {}
}