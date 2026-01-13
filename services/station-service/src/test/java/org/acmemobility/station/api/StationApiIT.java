package org.acmemobility.station.api;

import io.helidon.microprofile.testing.Socket;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acmemobility.station.api.dto.CancelReservationRequest;
import org.acmemobility.station.api.dto.CancelReservationResponse;
import org.acmemobility.station.api.dto.ErrorResponse;
import org.acmemobility.station.api.dto.LockRequest;
import org.acmemobility.station.api.dto.LockResponse;
import org.acmemobility.station.api.dto.ReserveRequest;
import org.acmemobility.station.api.dto.ReserveResponse;
import org.acmemobility.station.api.dto.UnlockRequest;
import org.acmemobility.station.api.dto.UnlockResponse;
import org.acmemobility.station.domain.model.Station;
import org.acmemobility.station.domain.model.Vehicle;
import org.acmemobility.station.persistence.store.inmemory.InMemoryReservationStore;
import org.acmemobility.station.persistence.store.inmemory.InMemoryStationStore;
import org.acmemobility.station.persistence.store.inmemory.InMemoryVehicleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test "end-to-end" sulla Station REST API.
 *
 * Obiettivo:
 * - verificare il "wiring" completo HTTP -> Resource -> Service -> Store (in-memory) -> Response DTO
 * - coprire i flussi base del ciclo di vita veicolo:
 *   (1) reserve -> (2) unlock -> (3) lock
 *   e anche unlock/lock senza reservation (noleggio immediato).
 *
 * Caratteristiche:
 * - usa HelidonTest per avviare il microservizio in test
 * - usa JAX-RS client (WebTarget) per chiamare le vere route HTTP
 * - usa store in-memory concreti e li ripulisce ad ogni test per renderlo deterministico
 */
@HelidonTest(resetPerTest = true)
@DisplayName("Station API – Vehicle lifecycle (integration tests)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StationApiIT {

    // ----------------- fixed demo data -----------------
    // Valori costanti per rendere i test leggibili e ripetibili.
    private static final String STATION_ID = "S45";
    private static final String VEHICLE_ID = "V123";

    // ----------------- test actors -----------------
    private static final String USER_1 = "U1";

    // ID noleggio usati nei test (simulano un identificatore che arriva dal "rental flow").
    private static final String RENTAL_1 = "R1";
    private static final String RENTAL_2 = "R2";

    /**
     * JAX-RS client verso il server Helidon avviato in test.
     * Socket("@default") = endpoint base del server embedded.
     */
    @Inject
    @Socket("@default")
    private WebTarget target;

    /**
     * Store in-memory reali: li iniettiamo per poter fare clear() in @BeforeEach
     * e avere test deterministici (niente leakage tra test).
     */
    @Inject
    private InMemoryStationStore stationStore;

    @Inject
    private InMemoryVehicleStore vehicleStore;

    @Inject
    private InMemoryReservationStore reservationStore;

    /**
     * Seed deterministico:
     * - pulizia store
     * - creazione di 1 station
     * - creazione di 1 veicolo docked e disponibile in quella station
     *
     * In questo modo ogni test parte dalla stessa "fotografia" del mondo.
     */
    @BeforeEach
    void seed() {
        // Reset state deterministically.
        stationStore.clear();
        vehicleStore.clear();
        reservationStore.clear();

        // Station + one vehicle docked & available.
        stationStore.upsert(new Station(STATION_ID));

        Vehicle v = new Vehicle(VEHICLE_ID);
        v.dockAt(STATION_ID); // DOCKED_AVAILABLE + currentStationId
        vehicleStore.upsert(v);
    }

    // -------------------------------------------------------------------------
    // Test: Reserve
    // -------------------------------------------------------------------------

    /**
     * Verifica che la POST /stations/{stationId}/reservations:
     * - crei una reservation
     * - ritorni 201
     * - ritorni un DTO coerente (stationId, vehicleId, status ACTIVE)
     */
    @Test
    @DisplayName("Reserve: crea una reservation ACTIVE (201) e il veicolo risulta riservato")
    void reserve_creates_active_reservation() {
        ReserveRequest req = reserveRequest(USER_1, VEHICLE_ID);

        ReserveResponse res = reserve(STATION_ID, req);

        assertNotNull(res.reservationId, "Reserve must return reservationId");
        assertEquals(STATION_ID, res.stationId);
        assertEquals(VEHICLE_ID, res.vehicleId);
        assertEquals("ACTIVE", res.status);
    }

    // -------------------------------------------------------------------------
    // Test: Cancel
    // -------------------------------------------------------------------------

    /**
     * Verifica che la POST /stations/{stationId}/reservations/{reservationId}/cancel:
     * - su reservation ACTIVE ritorni 200
     * - lo stato finale sia coerente con le regole:
     *   tipicamente CANCELED, ma può essere EXPIRED se durante la richiesta risulta scaduta.
     */
    @Test
    @DisplayName("Cancel: su reservation ACTIVE -> 200 e stato finale CANCELED o EXPIRED")
    void cancel_active_reservation_returns_200_and_final_status() {
        String reservationId = createReservation(USER_1);

        CancelReservationResponse canceled = cancel(STATION_ID, reservationId, USER_1);

        assertEquals(reservationId, canceled.reservationId);
        assertTrue(
                "CANCELED".equals(canceled.status) || "EXPIRED".equals(canceled.status),
                "Expected CANCELED or EXPIRED, got: " + canceled.status
        );
    }

    // -------------------------------------------------------------------------
    // Test: Happy path con reservation (reserve -> unlock -> lock)
    // -------------------------------------------------------------------------

    /**
     * Flusso "booking":
     * - reserve (crea reservation)
     * - unlock con reservationId (consuma la reservation)
     * - lock (chiude noleggio e dock)
     *
     * Questo test copre il percorso principale "pulito".
     */
    @Test
    @DisplayName("Happy path (reservation): reserve -> unlock -> lock")
    void happy_path_given_reservation_when_unlock_then_lock() {
        String reservationId = createReservation(USER_1);

        UnlockResponse unlock = unlock(STATION_ID, VEHICLE_ID, USER_1, RENTAL_1, reservationId);

        assertEquals(VEHICLE_ID, unlock.vehicleId);
        assertEquals(STATION_ID, unlock.stationId);
        assertEquals("IN_USE", unlock.vehicleState);
        assertEquals(RENTAL_1, unlock.activeRentalId);

        // Nel vostro dominio: se unlock avviene con reservation, quella reservation viene consumata.
        assertEquals(reservationId, unlock.consumedReservationId);

        LockResponse lock = lock(STATION_ID, VEHICLE_ID, RENTAL_1);

        assertEquals(VEHICLE_ID, lock.vehicleId);
        assertEquals(STATION_ID, lock.stationId);
        assertEquals("DOCKED_AVAILABLE", lock.vehicleState);
        assertEquals(RENTAL_1, lock.closedRentalId);
    }

    // -------------------------------------------------------------------------
    // Test: Happy path senza reservation (unlock immediato)
    // -------------------------------------------------------------------------

    /**
     * Flusso "immediate rent":
     * - unlock senza reservationId
     * - lock
     *
     * Qui verifichi che:
     * - unlock metta il veicolo IN_USE e associ un activeRentalId
     * - lock riporti il veicolo DOCKED_AVAILABLE e ritorni closedRentalId
     */
    @Test
    @DisplayName("Happy path (no reservation): unlock immediato (reservationId=null) -> lock")
    void happy_path_given_no_reservation_when_unlock_then_lock() {
        UnlockResponse unlock = unlock(STATION_ID, VEHICLE_ID, USER_1, RENTAL_2, null);

        assertEquals(VEHICLE_ID, unlock.vehicleId);
        assertEquals(STATION_ID, unlock.stationId);
        assertEquals("IN_USE", unlock.vehicleState);
        assertEquals(RENTAL_2, unlock.activeRentalId);

        // Nessuna reservation usata/consumata.
        assertNull(unlock.consumedReservationId);

        LockResponse lock = lock(STATION_ID, VEHICLE_ID, RENTAL_2);

        assertEquals("DOCKED_AVAILABLE", lock.vehicleState);
        assertEquals(RENTAL_2, lock.closedRentalId);
    }

    // -------------------------------------------------------------------------
    // Scenario helpers (utility per ridurre boilerplate nei test)
    // -------------------------------------------------------------------------

    /**
     * Crea una reservation standard su STATION_ID e VEHICLE_ID per l'utente indicato.
     * Ritorna reservationId, utile per i test.
     */
    private String createReservation(String userId) {
        ReserveResponse res = reserve(STATION_ID, reserveRequest(userId, VEHICLE_ID));
        assertNotNull(res.reservationId, "Reserve must return reservationId");
        return res.reservationId;
    }

    /**
     * Builder minimale del payload per reserve.
     * Mantenerlo qui rende i test più leggibili.
     */
    private ReserveRequest reserveRequest(String userId, String vehicleId) {
        ReserveRequest req = new ReserveRequest();
        req.userId = userId;
        req.vehicleId = vehicleId;
        return req;
    }

    /**
     * Esegue la chiamata HTTP a:
     * POST /stations/{stationId}/reservations
     *
     * Se non riceve 201, fallisce mostrando anche il body della risposta (utile per debug).
     */
    private ReserveResponse reserve(String stationId, ReserveRequest req) {
        try (Response r = post(
                target.path("stations").path(stationId).path("reservations"),
                req
        )) {
            if (r.getStatus() != 201) {
                r.bufferEntity();
                String body = r.readEntity(String.class);
                fail("Reserve should return 201 but got " + r.getStatus() + " body=" + body);
            }
            return r.readEntity(ReserveResponse.class);
        }
    }

    /**
     * Helper opzionale (non usato nei test "happy path"):
     * utile se in futuro vuoi aggiungere test negativi su reserve.
     */
    @SuppressWarnings("unused")
    private ErrorResponse reserveExpectingError(String stationId, ReserveRequest req, int expectedStatus) {
        try (Response r = post(
                target.path("stations").path(stationId).path("reservations"),
                req
        )) {
            assertEquals(expectedStatus, r.getStatus(), "Reserve should fail with " + expectedStatus);
            return r.readEntity(ErrorResponse.class);
        }
    }

    /**
     * Esegue la chiamata HTTP a:
     * POST /stations/{stationId}/reservations/{reservationId}/cancel
     *
     * Se non riceve 200, fallisce mostrando anche il body (utile per capire error mapper / codice dominio).
     */
    private CancelReservationResponse cancel(String stationId, String reservationId, String userId) {
        CancelReservationRequest req = new CancelReservationRequest();
        req.userId = userId;

        try (Response r = post(
                target.path("stations").path(stationId).path("reservations").path(reservationId).path("cancel"),
                req
        )) {
            if (r.getStatus() != 200) {
                r.bufferEntity();
                String body = r.readEntity(String.class);
                fail("Cancel should return 200 but got " + r.getStatus() + " body=" + body);
            }
            return r.readEntity(CancelReservationResponse.class);
        }
    }

    /**
     * Helper opzionale (non usato ora): test negativi su cancel.
     */
    @SuppressWarnings("unused")
    private ErrorResponse cancelExpectingError(String stationId,
                                               String reservationId,
                                               String userId,
                                               int expectedStatus) {
        CancelReservationRequest req = new CancelReservationRequest();
        req.userId = userId;

        try (Response r = post(
                target.path("stations").path(stationId).path("reservations").path(reservationId).path("cancel"),
                req
        )) {
            assertEquals(expectedStatus, r.getStatus(), "Cancel should fail with " + expectedStatus);
            return r.readEntity(ErrorResponse.class);
        }
    }

    /**
     * Esegue la chiamata HTTP a:
     * POST /stations/{stationId}/vehicles/{vehicleId}/unlock
     *
     * Supporta sia:
     * - unlock con reservation (reservationId non null)
     * - unlock immediato (reservationId null)
     */
    private UnlockResponse unlock(String stationId,
                                  String vehicleId,
                                  String userId,
                                  String rentalId,
                                  String reservationId) {
        UnlockRequest req = new UnlockRequest();
        req.userId = userId;
        req.rentalId = rentalId;
        req.reservationId = reservationId;

        try (Response r = post(
                target.path("stations").path(stationId).path("vehicles").path(vehicleId).path("unlock"),
                req
        )) {
            if (r.getStatus() != 200) {
                r.bufferEntity();
                String body = r.readEntity(String.class);
                fail("Unlock should return 200 but got " + r.getStatus() + " body=" + body);
            }
            return r.readEntity(UnlockResponse.class);
        }
    }

    /**
     * Helper opzionale (non usato ora): test negativi su unlock.
     */
    @SuppressWarnings("unused")
    private ErrorResponse unlockExpectingError(String stationId,
                                               String vehicleId,
                                               String userId,
                                               String rentalId,
                                               String reservationId,
                                               int expectedStatus) {
        UnlockRequest req = new UnlockRequest();
        req.userId = userId;
        req.rentalId = rentalId;
        req.reservationId = reservationId;

        try (Response r = post(
                target.path("stations").path(stationId).path("vehicles").path(vehicleId).path("unlock"),
                req
        )) {
            assertEquals(expectedStatus, r.getStatus(), "Unlock should fail with " + expectedStatus);
            return r.readEntity(ErrorResponse.class);
        }
    }

    /**
     * Esegue la chiamata HTTP a:
     * POST /stations/{stationId}/vehicles/{vehicleId}/lock
     *
     * Se non riceve 200, fallisce con body per debug.
     */
    private LockResponse lock(String stationId,
                              String vehicleId,
                              String rentalId) {
        LockRequest req = new LockRequest();
        req.rentalId = rentalId;

        try (Response r = post(
                target.path("stations").path(stationId).path("vehicles").path(vehicleId).path("lock"),
                req
        )) {
            if (r.getStatus() != 200) {
                r.bufferEntity();
                String body = r.readEntity(String.class);
                fail("Lock should return 200 but got " + r.getStatus() + " body=" + body);
            }
            return r.readEntity(LockResponse.class);
        }
    }

    /**
     * Helper opzionale (non usato ora): test negativi su lock.
     */
    @SuppressWarnings("unused")
    private ErrorResponse lockExpectingError(String stationId,
                                             String vehicleId,
                                             String rentalId,
                                             int expectedStatus) {
        LockRequest req = new LockRequest();
        req.rentalId = rentalId;

        try (Response r = post(
                target.path("stations").path(stationId).path("vehicles").path(vehicleId).path("lock"),
                req
        )) {
            assertEquals(expectedStatus, r.getStatus(), "Lock should fail with " + expectedStatus);
            return r.readEntity(ErrorResponse.class);
        }
    }

    /**
     * Helper comune per POST JSON.
     * Centralizzare questo evita ripetizioni e riduce errori (content-type ecc).
     */
    private Response post(WebTarget t, Object body) {
        Invocation.Builder b = t.request(MediaType.APPLICATION_JSON_TYPE);
        return b.post(Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
    }
}
