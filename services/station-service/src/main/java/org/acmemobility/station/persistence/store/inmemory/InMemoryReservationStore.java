package org.acmemobility.station.persistence.store.inmemory;

import jakarta.enterprise.context.ApplicationScoped;
import org.acmemobility.station.domain.model.Reservation;
import org.acmemobility.station.persistence.store.ReservationStore;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementazione in-memory di ReservationStore.
 *
 * Scopo:
 * - supportare sviluppo/test senza DB
 * - accesso thread-safe alla struttura dati (ConcurrentHashMap)
 *
 * Nota importante:
 * - Reservation è un oggetto mutabile nel vostro modello (status, expiresAt...).
 * - Le race condizioni di business (es. unlock vs cancel sulla stessa reservation/vehicle)
 *   vengono prevenute nel dominio tramite VehicleLockManager (lock per vehicleId).
 */
@ApplicationScoped
public class InMemoryReservationStore implements ReservationStore {

    /**
     * reservationId -> Reservation
     */
    private final ConcurrentHashMap<String, Reservation> reservations = new ConcurrentHashMap<>();

    @Override
    public Optional<Reservation> findById(String reservationId) {
        String id = normalize(reservationId);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(reservations.get(id));
    }

    @Override
    public void upsert(Reservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("reservation must not be null");
        }

        String id = normalize(reservation.getReservationId());
        if (id == null) {
            throw new IllegalArgumentException("reservationId must not be null/blank");
        }

        // Upsert semplice: sovrascrive l'entry.
        // Nel dominio spesso mutate Reservation e poi chiamate upsert(r) per "persistenza".
        reservations.put(id, reservation);
    }

    // ---- utilities (comode in test/debug) ----

    public void clear() {
        reservations.clear();
    }

    public int size() {
        return reservations.size();
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
