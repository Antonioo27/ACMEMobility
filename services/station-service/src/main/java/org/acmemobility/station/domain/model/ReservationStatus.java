package org.acmemobility.station.domain.model;

/**
 * Stati possibili di una Reservation.
 *
 * ACTIVE   : prenotazione valida e (in teoria) utilizzabile
 * CANCELED : annullata (terminal)
 * CONSUMED : consumata da un unlock (terminal)
 * EXPIRED  : scaduta per TTL (terminal)
 *
 * Nota:
 * - La transizione di stato è gestita nel dominio (StationServiceImpl).
 */
public enum ReservationStatus {
    ACTIVE,
    CANCELED,
    CONSUMED,
    EXPIRED
}
