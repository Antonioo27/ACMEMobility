package org.acmemobility.station.persistence.store;

import org.acmemobility.station.domain.model.Vehicle;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port (store) per i Vehicle.
 *
 * Scopo:
 * - astrarre il "come" vengono salvati/recuperati i veicoli (in-memory oggi, DB domani)
 * - offrire al dominio (StationServiceImpl) operazioni minime e mirate:
 *   - findById: leggere lo stato di un veicolo
 *   - upsert: creare/aggiornare un veicolo
 * Nota:
 * - "upsert" = insert se nuovo / update se esiste (semantica comoda per store semplici).
 */
public interface VehicleStore {

    /**
     * @param vehicleId id logico del veicolo
     * @return Optional vuoto se non esiste
     */
    Optional<Vehicle> findById(String vehicleId);

    /**
     * Inserisce o aggiorna lo stato del veicolo.
     * Nel modello attuale il Vehicle è un oggetto mutabile: lo store conserva il reference.
     */
    void upsert(Vehicle vehicle);

    /**
     * @return lista di tutti i veicoli (ordine non garantito dallo store; nell'impl in-memory conviene ordinarli)
     */
    List<Vehicle> findAll();

}
