package org.acmemobility.station.persistence.store;

import org.acmemobility.station.domain.model.Reservation;

import java.util.Optional;

/**
 * Persistence port (store) per le Reservation.
 *
 * Scopo:
 * - astrarre dove e come vengono salvate le prenotazioni (in-memory oggi, DB domani)
 * - fornire al dominio operazioni minime:
 *   - findById: leggere stato e vincoli di una prenotazione
 *   - upsert: creare/aggiornare una prenotazione
 *
 * Nota:
 * - la logica (ACTIVE/EXPIRED/CANCELED/CONSUMED, TTL, mismatch) sta nel dominio (StationServiceImpl),
 *   lo store è solo lettura/scrittura.
 */
public interface ReservationStore {

    /**
     * @param reservationId id logico prenotazione
     * @return Optional vuoto se non esiste
     */
    Optional<Reservation> findById(String reservationId);

    /**
     * Insert or update.
     * Semantica comoda per store semplici: inserisce se non esiste, aggiorna se esiste.
     */
    void upsert(Reservation reservation);
}
