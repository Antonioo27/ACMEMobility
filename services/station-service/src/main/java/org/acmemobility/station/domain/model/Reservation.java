package org.acmemobility.station.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Entity di dominio: rappresenta una prenotazione (reservation) di un veicolo in una stazione.
 *
 * Invarianti "strutturali" (sempre vere):
 * - reservationId, stationId, vehicleId, userId non null/blank
 * - status e createdAt non null
 *
 * Nota:
 * - Le regole "di flusso" (quando diventa CANCELED/CONSUMED/EXPIRED, autorizzazioni, mismatch, ecc.)
 *   sono applicate in StationServiceImpl.
 */
public class Reservation {

    private final String reservationId;
    private final String stationId;
    private final String vehicleId;
    private final String userId;

    /**
     * Stato mutabile: viene aggiornato dal dominio.
     * Esempi:
     * - ACTIVE -> CANCELED quando annulli
     * - ACTIVE -> CONSUMED quando fai unlock usando quella reservation
     * - ACTIVE -> EXPIRED quando scade il TTL
     */
    private ReservationStatus status;

    private final Instant createdAt;

    /**
     * TTL opzionale:
     * - null => prenotazione senza scadenza (o scadenza gestita altrove)
     * - non-null => scade quando now > expiresAt
     */
    private final Instant expiresAt;

    public Reservation(String reservationId,
                       String stationId,
                       String vehicleId,
                       String userId,
                       ReservationStatus status,
                       Instant createdAt,
                       Instant expiresAt) {

        this.reservationId = requireId(reservationId, "reservationId");
        this.stationId = requireId(stationId, "stationId");
        this.vehicleId = requireId(vehicleId, "vehicleId");
        this.userId = requireId(userId, "userId");

        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.expiresAt = expiresAt; // può essere null: significa "no TTL"
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getStationId() {
        return stationId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getUserId() {
        return userId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        // Non permettiamo status null perché rompe la semantica del dominio.
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Scadenza "meccanica" basata sul TTL.
     * - Se expiresAt è null, non scade mai via TTL.
     * - Se now è null, per prudenza torna false (evita NPE, ma nel dominio passate sempre now).
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && now != null && now.isAfter(expiresAt);
    }

    /**
     * Terminal = stato conclusivo: non dovrebbe più tornare ACTIVE.
     * Serve nel dominio per ragionare su "cleanup" e coerenza:
     * - CANCELED/EXPIRED => non è più utilizzabile per unlock
     * - CONSUMED => già usata, blocca re-uso
     */
    public boolean isTerminal() {
        return status == ReservationStatus.CANCELED
                || status == ReservationStatus.EXPIRED
                || status == ReservationStatus.CONSUMED;
    }

    private static String requireId(String s, String field) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return s.trim();
    }

    /**
     * Identity equality: due Reservation sono "la stessa" se hanno lo stesso reservationId.
     * Questo è tipico per entity di dominio.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reservation other)) return false;
        return reservationId.equals(other.reservationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId);
    }

    @Override
    public String toString() {
        return "Reservation{reservationId='" + reservationId + "', stationId='" + stationId + "', vehicleId='"
                + vehicleId + "', userId='" + userId + "', status=" + status + ", createdAt=" + createdAt
                + ", expiresAt=" + expiresAt + "}";
    }
}
