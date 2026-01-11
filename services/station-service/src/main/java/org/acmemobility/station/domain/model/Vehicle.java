package org.acmemobility.station.domain.model;

import java.util.Objects;

/**
 * Entity di dominio: rappresenta un veicolo e il suo stato operativo.
 *
 * Il veicolo è un "aggregato leggero" che tiene insieme:
 * - dove si trova (currentStationId) quando è docked
 * - se è riservato (activeReservationId + reservationOwnerUserId)
 * - se è in uso (activeRentalId)
 *
 * Importante:
 * - Questa classe NON applica tutte le regole di business (quelle stanno in StationServiceImpl).
 * - Qui ci sono solo:
 *   1) invarianti strutturali di base
 *   2) metodi di comodo per transizioni coerenti (dockAt/reserve/startRental...)
 *
 * Nel vostro codice attuale, StationServiceImpl usa sia questi metodi che i setter diretti.
 * Va bene, ma è meglio scegliere uno stile (solo metodi “coerenti” o solo setter) per ridurre ambiguità.
 */
public class Vehicle {

    private final String vehicleId;

    /**
     * Stato principale del veicolo (macchina a stati semplice).
     * In teoria:
     * - DOCKED_AVAILABLE: in stazione e libero
     * - DOCKED_RESERVED : in stazione ma riservato
     * - IN_USE          : fuori dalla stazione, in noleggio
     */
    private VehicleState state;

    /**
     * Valorizzato quando il veicolo è docked (AVAILABLE o RESERVED).
     * Tipicamente null quando IN_USE.
     */
    private String currentStationId;

    /**
     * Valorizzato quando DOCKED_RESERVED.
     * Non è "la reservation intera": è solo l'ID per collegarsi a ReservationStore.
     */
    private String activeReservationId;
    private String reservationOwnerUserId;

    /**
     * Valorizzato quando IN_USE.
     * Serve per idempotenza di dominio:
     * - se arriva unlock con stesso rentalId e veicolo già IN_USE, rispondi OK (StationServiceImpl).
     */
    private String activeRentalId;

    public Vehicle(String vehicleId) {
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("vehicleId must not be null/blank");
        }
        this.vehicleId = vehicleId.trim();

        // Stato iniziale: per default lo consideriamo docked disponibile.
        // Nota: currentStationId parte null finché non lo docki esplicitamente (dockAt).
        this.state = VehicleState.DOCKED_AVAILABLE;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public VehicleState getState() {
        return state;
    }

    public String getCurrentStationId() {
        return currentStationId;
    }

    public String getActiveReservationId() {
        return activeReservationId;
    }

    public String getReservationOwnerUserId() {
        return reservationOwnerUserId;
    }

    public String getActiveRentalId() {
        return activeRentalId;
    }

    /* ---------------------------
       Transizioni "coerenti" (metodi di comodo)
       --------------------------- */

    /**
     * Dock "pulito" in una stazione:
     * - stato => DOCKED_AVAILABLE
     * - currentStationId => stationId
     * - activeRental associated => null (fine noleggio o comunque non in uso)
     *
     * Nota: NON tocchiamo reservation fields:
     * - perché la reservation è concetto separato (ReservationStore),
     * - e la pulizia/cancellazione dipende da regole di business (StationServiceImpl).
     */
    public void dockAt(String stationId) {
        if (stationId == null || stationId.isBlank()) {
            throw new IllegalArgumentException("stationId must not be null/blank");
        }
        this.state = VehicleState.DOCKED_AVAILABLE;
        this.currentStationId = stationId.trim();
        this.activeRentalId = null;
        // NON tocchiamo activeReservationId/reservationOwnerUserId qui.
    }

    /**
     * Marca il veicolo come riservato:
     * - stato => DOCKED_RESERVED
     * - set dei puntatori alla reservation e al suo owner
     *
     * Nota: non settiamo currentStationId qui: si assume che sia già docked in una station.
     * Se vuoi più rigidità, potresti richiedere currentStationId non-null.
     */
    public void reserve(String reservationId, String userId) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("reservationId must not be null/blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be null/blank");
        }
        this.state = VehicleState.DOCKED_RESERVED;
        this.activeReservationId = reservationId.trim();
        this.reservationOwnerUserId = userId.trim();
    }

    /**
     * Pulisce i campi di reservation.
     * Non cambia lo stato: perché non sempre "clearReservation" implica "available".
     * Nel vostro dominio attuale, quando la reservation è stale (EXPIRED/CANCELED),
     * allora:
     * - clearReservation()
     * - e poi state => DOCKED_AVAILABLE
     */
    public void clearReservation() {
        this.activeReservationId = null;
        this.reservationOwnerUserId = null;
    }

    /**
     * Inizio noleggio:
     * - stato => IN_USE
     * - activeRentalId => rentalId
     * - currentStationId => null (coerenza: se è in uso non sta docked)
     *
     * Nota: NON puliamo la reservation qui.
     * Nel vostro StationServiceImpl.unlock, se si usa reservationId, la pulizia viene fatta lì.
     */
    public void startRental(String rentalId) {
        if (rentalId == null || rentalId.isBlank()) {
            throw new IllegalArgumentException("rentalId must not be null/blank");
        }
        this.state = VehicleState.IN_USE;
        this.activeRentalId = rentalId.trim();
        this.currentStationId = null;
    }

    /**
     * Comodità: termina il rental e docka.
     * Implementazione: reset activeRentalId e poi delega a dockAt().
     */
    public void endRentalAndDock(String stationId) {
        this.activeRentalId = null;
        dockAt(stationId);
    }

    /* ---------------------------
       Setter “semplici”
       --------------------------- */

    /**
     * Setter diretto dello stato.
     * Attenzione: aggira le "transizioni coerenti" (dockAt/reserve/startRental).
     * Nel vostro StationServiceImpl li usate già; va bene, ma scegliete uno stile.
     */
    public void setState(VehicleState state) {
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    public void setCurrentStationId(String currentStationId) {
        this.currentStationId = (currentStationId == null) ? null : currentStationId.trim();
    }

    public void setActiveReservationId(String activeReservationId) {
        this.activeReservationId = (activeReservationId == null) ? null : activeReservationId.trim();
    }

    public void setReservationOwnerUserId(String reservationOwnerUserId) {
        this.reservationOwnerUserId = (reservationOwnerUserId == null) ? null : reservationOwnerUserId.trim();
    }

    public void setActiveRentalId(String activeRentalId) {
        this.activeRentalId = (activeRentalId == null) ? null : activeRentalId.trim();
    }
}
