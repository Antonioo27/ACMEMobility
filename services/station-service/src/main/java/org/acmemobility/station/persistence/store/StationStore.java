package org.acmemobility.station.persistence.store;

import org.acmemobility.station.domain.model.Station;

import java.util.Optional;

/**
 * Persistence port (store) per le Station.
 *
 * Scopo:
 * - astrarre il layer di persistenza per le stazioni (oggi in-memory, domani DB)
 * - offrire al dominio operazioni minime:
 *   - findById: recupera la stazione per applicare vincoli di coerenza (es. esistenza stazione)
 *   - upsert: inserisce o aggiorna una stazione
 *
 * Nota:
 * - le regole di business "vive" vengono applicate nel dominio (StationServiceImpl), non nello store.
 */
public interface StationStore {

    /**
     * @param stationId id logico della stazione
     * @return Optional vuoto se la stazione non esiste
     */
    Optional<Station> findById(String stationId);

    /**
     * Insert or update.
     * "Upsert" evita di distinguere tra create e update nello store in-memory.
     */
    void upsert(Station station);
}
