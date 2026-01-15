package it.unibo.acme.fleet.simulator.sim;

public class VehicleState {
    public final String vehicleId;

    // Stato fisico
    public double batteryPct;
    public double curLat;
    public double curLon;

    // Stato logico di navigazione
    public boolean isLocked; // True = bloccato, False = sbloccato (consuma batteria)
    
    // Destinazione corrente (null se fermo/arrivato)
    public Double targetLat; 
    public Double targetLon;

    public VehicleState(String vehicleId, double startLat, double startLon) {
        this.vehicleId = vehicleId;
        this.curLat = startLat;
        this.curLon = startLon;
        // All'avvio sono tutti bloccati e fermi
        this.isLocked = true;
        this.targetLat = null;
        this.targetLon = null;
        this.batteryPct = 100.0;
    }
}