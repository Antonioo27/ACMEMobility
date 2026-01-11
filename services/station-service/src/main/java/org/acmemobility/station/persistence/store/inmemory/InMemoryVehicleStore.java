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
    private final boolean seedDemo;

    @Inject
    public InMemoryVehicleStore(
            @ConfigProperty(name = "station.seed.demo", defaultValue = "false") boolean seedDemo
    ) {
        this.seedDemo = seedDemo;
        if (seedDemo) {
            seedDemoData();
        }
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

    @Override
    public long countDockedAtStation(String stationId) {
        String sid = normalize(stationId);
        if (sid == null) return 0;

        // Conta solo i veicoli che:
        // - hanno currentStationId = stationId
        // - e sono in stati che occupano uno slot dock (AVAILABLE o RESERVED)
        return vehicles.values().stream()
                .filter(v -> sid.equals(v.getCurrentStationId()))
                .filter(v -> v.getState() == VehicleState.DOCKED_AVAILABLE
                        || v.getState() == VehicleState.DOCKED_RESERVED)
                .count();
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
        if (!seedDemo) return;
        seedDemoData();
    }

    // ----------------- internal -----------------

    private void seedDemoData() {
        // Dati "demo" coerenti con i vostri test d'integrazione (es. S45, V123).
        // NOTA: upsert fa normalize+validazioni, quindi riusiamo upsert.
        Vehicle v123 = new Vehicle("V123");
        v123.dockAt("S45");
        upsert(v123);

        Vehicle v124 = new Vehicle("V124");
        v124.dockAt("S45");
        upsert(v124);

        Vehicle v200 = new Vehicle("V200");
        v200.dockAt("S46");
        upsert(v200);
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
