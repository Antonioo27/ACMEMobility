package org.acmemobility.station.persistence.store;

import org.acmemobility.station.domain.model.Vehicle;

import java.util.Optional;

/**
 * Persistence port (store) per i Vehicle.
 *
 * Scopo:
 * - astrarre il "come" vengono salvati/recuperati i veicoli (in-memory oggi, DB domani)
 * - offrire al dominio (StationServiceImpl) operazioni minime e mirate:
 *   - findById: leggere lo stato di un veicolo
 *   - upsert: creare/aggiornare un veicolo
 *   - countDockedAtStation: vincolo di capacità stazione (quanti veicoli occupano slot)
 *
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
     * Conta quanti veicoli occupano uno slot dock nella stazione.
     * Per definizione, occupano slot:
     * - DOCKED_AVAILABLE
     * - DOCKED_RESERVED
     *
     * Serve per la regola: station capacity non può essere superata in lock().
     */
    long countDockedAtStation(String stationId);
}
