package org.acmemobility.station.persistence.store.inmemory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acmemobility.station.domain.model.Vehicle;
import org.acmemobility.station.domain.model.VehicleState;
import org.acmemobility.station.persistence.store.VehicleStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementazione in-memory di VehicleStore.
 *
 * Scopo:
 * - supportare sviluppo/test senza DB
 * - essere thread-safe a livello di struttura dati (ConcurrentHashMap)
 *
 * Nota importante:
 * - La mappa è thread-safe, ma il Vehicle è un oggetto mutabile.
 * - La serializzazione corretta delle operazioni concorrenti sullo stesso veicolo
 *   avviene nel dominio tramite VehicleLockManager (lock per vehicleId).
 */
@ApplicationScoped
public class InMemoryVehicleStore implements VehicleStore {

    /**
     * Storage in-memory: vehicleId -> Vehicle.
     * ConcurrentHashMap protegge la struttura (put/get/compute), non la mutabilità interna di Vehicle.
     */
    private final ConcurrentHashMap<String, Vehicle> vehicles = new ConcurrentHashMap<>();

    /**
     * Flag configurabile per "seed" di dati demo.
     * Utile per far girare il servizio senza dover sempre creare stazioni/veicoli a mano.
     */

    @Inject
    public InMemoryVehicleStore() {
        // NIENTE IF, NIENTE CONFIG. CARICA E BASTA.
        System.out.println("!!! FORCING DATA SEED IN MEMORY STORE !!!");
        seedDemoData();
    }

    @Override
    public Optional<Vehicle> findById(String vehicleId) {
        String id = normalize(vehicleId);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(vehicles.get(id));
    }

    @Override
    public void upsert(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle must not be null");

        String id = normalize(vehicle.getVehicleId());
        if (id == null) {
            throw new IllegalArgumentException("vehicleId must not be null/blank");
        }

        // Upsert semplice: sostituisce il valore precedente con quello attuale.
        // Nel vostro dominio spesso mutate lo stesso oggetto e poi chiamate upsert:
        // qui significa "assicurati che lo store veda lo stato aggiornato".
        vehicles.put(id, vehicle);
    }

    // ----------------- utilities (test/debug) -----------------

    public void clear() {
        vehicles.clear();
    }

    public int size() {
        return vehicles.size();
    }

    /**
     * Re-seed esplicito (utile nei test dopo clear()).
     * Se station.seed.demo=false, non fa nulla: evita sorprese in ambienti non-demo.
     */
    public void reseedDemo() {
        seedDemoData();
    }

    // ----------------- internal -----------------

    private void seedDemoData() {
        // Creiamo 10 veicoli come nel simulatore (V001 ... V010)
        for (int i = 1; i <= 10; i++) {
            String vehicleId = String.format("V%03d", i); 
            Vehicle v = new Vehicle(vehicleId);

            // Distribuzione deterministica (Round Robin):
            // i=1 -> S01, i=2 -> S02 ... i=5 -> S05, i=6 -> S01 ...
            int stationIndex = ((i - 1) % 5) + 1;
            String stationId = String.format("S%02d", stationIndex);

            v.dockAt(stationId);
            
            // Salviamo nello store
            upsert(v);
        }
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
