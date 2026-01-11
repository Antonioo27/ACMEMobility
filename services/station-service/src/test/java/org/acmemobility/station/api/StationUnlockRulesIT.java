package org.acmemobility.station.api;

import io.helidon.microprofile.testing.Socket;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acmemobility.station.api.dto.*;
import org.acmemobility.station.domain.model.Station;
import org.acmemobility.station.domain.model.Vehicle;
import org.acmemobility.station.persistence.store.inmemory.InMemoryReservationStore;
import org.acmemobility.station.persistence.store.inmemory.InMemoryStationStore;
import org.acmemobility.station.persistence.store.inmemory.InMemoryVehicleStore;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@HelidonTest(resetPerTest = true)
@DisplayName("Station API – Unlock rules (integration tests)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StationUnlockRulesIT {

    // ----------------- deterministic test data (seeded in @BeforeEach) -----------------
    private static final String STATION_S45 = "S45";
    private static final String STATION_S46 = "S46";

    private static final String VEHICLE_V123 = "V123";
    private static final String VEHICLE_V124 = "V124"; // serve per testare mismatch (vehicle mismatch) senza 404

    // ----------------- actors -----------------
    private static final String USER_1 = "U1";
    private static final String USER_2 = "U2";

    // ----------------- rentals -----------------
    private static final String RENTAL_R1 = "R1";
    private static final String RENTAL_R2 = "R2";

    @Inject
    @Socket("@default")
    private WebTarget target;

    // Invece di dipendere da "seed demo" via config, rendiamo i test deterministici:
    // pulizia + seed esplicito per ogni test.
    @Inject
    private InMemoryStationStore stationStore;

    @Inject
    private InMemoryVehicleStore vehicleStore;

    @Inject
    private InMemoryReservationStore reservationStore;

    @BeforeEach
    void seed() {
        // Isolamento: niente stato condiviso tra test.
        stationStore.clear();
        vehicleStore.clear();
        reservationStore.clear();

        // Stazioni
        stationStore.upsert(new Station(STATION_S45, 10));
        stationStore.upsert(new Station(STATION_S46, 10));

        // Veicoli (entrambi docked in S45)
        Vehicle v123 = new Vehicle(VEHICLE_V123);
        v123.dockAt(STATION_S45);
        vehicleStore.upsert(v123);

        Vehicle v124 = new Vehicle(VEHICLE_V124);
        v124.dockAt(STATION_S45);
        vehicleStore.upsert(v124);
    }

    // -------------------------------------------------------------------------
    // A) API validation (400)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Unlock: rentalId blank -> 400 INVALID_REQUEST")
    void unlock_invalid_request_blank_rentalId_returns_400_invalid_request() {
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = " "; // invalid
        req.reservationId = null;

        ErrorResponse err = unlockExpectingError(STATION_S45, VEHICLE_V123, req, 400);
        assertEquals("INVALID_REQUEST", err.error);
    }

    @Test
    @DisplayName("Unlock: stationId blank -> 400 INVALID_REQUEST")
    void unlock_invalid_request_blank_stationId_returns_400_invalid_request() {
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R1;

        ErrorResponse err = unlockExpectingError(" ", VEHICLE_V123, req, 400);
        assertEquals("INVALID_REQUEST", err.error);
    }

    @Test
    @DisplayName("Unlock: vehicleId blank -> 400 INVALID_REQUEST")
    void unlock_invalid_request_blank_vehicleId_returns_400_invalid_request() {
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R1;

        ErrorResponse err = unlockExpectingError(STATION_S45, " ", req, 400);
        assertEquals("INVALID_REQUEST", err.error);
    }

    // -------------------------------------------------------------------------
    // B) Not found (404)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Unlock: vehicle inesistente -> 404 VEHICLE_NOT_FOUND")
    void unlock_vehicle_not_found_returns_404_vehicle_not_found() {
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R1;

        ErrorResponse err = unlockExpectingError(STATION_S45, "V404", req, 404);
        assertEquals("VEHICLE_NOT_FOUND", err.error);
    }

    @Test
    @DisplayName("Unlock: reservationId inesistente -> 404 RESERVATION_NOT_FOUND")
    void unlock_reservation_not_found_returns_404_reservation_not_found() {
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R1;
        req.reservationId = "RSV-NOT-EXIST";

        ErrorResponse err = unlockExpectingError(STATION_S45, VEHICLE_V123, req, 404);
        assertEquals("RESERVATION_NOT_FOUND", err.error);
    }

    // -------------------------------------------------------------------------
    // C) Domain conflicts / authorization
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Unlock: vehicle non è alla station -> 409 VEHICLE_NOT_AT_STATION")
    void unlock_when_vehicle_not_at_station_returns_409_vehicle_not_at_station() {
        // Given: move vehicle from S45 to S46 (unlock immediate + lock at S46)
        unlockOk(STATION_S45, VEHICLE_V123, USER_1, RENTAL_R1, null);
        lockOk(STATION_S46, VEHICLE_V123, RENTAL_R1);

        // When: try to unlock from wrong station S45
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R2;

        ErrorResponse err = unlockExpectingError(STATION_S45, VEHICLE_V123, req, 409);
        assertEquals("VEHICLE_NOT_AT_STATION", err.error);
    }

    @Test
    @DisplayName("Unlock: vehicle già IN_USE con rental diverso -> 409 VEHICLE_IN_USE_BY_OTHER_RENTAL")
    void unlock_when_vehicle_in_use_by_other_rental_returns_409_vehicle_in_use_by_other_rental() {
        // Given: vehicle is in use with R1
        unlockOk(STATION_S45, VEHICLE_V123, USER_1, RENTAL_R1, null);

        // When: unlock again with different rental R2
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R2;

        ErrorResponse err = unlockExpectingError(STATION_S45, VEHICLE_V123, req, 409);
        assertEquals("VEHICLE_IN_USE_BY_OTHER_RENTAL", err.error);
    }

    @Test
    @DisplayName("Unlock: reservation mismatch (vehicle mismatch) -> 409 RESERVATION_MISMATCH")
    void unlock_with_reservation_vehicle_mismatch_returns_409_reservation_mismatch() {
        // Given: reservation for V123
        String reservationId = createReservationAt(STATION_S45, VEHICLE_V123, USER_1);

        // When: call unlock for V124 using reservationId of V123
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R1;
        req.reservationId = reservationId;

        ErrorResponse err = unlockExpectingError(STATION_S45, VEHICLE_V124, req, 409);
        assertEquals("RESERVATION_MISMATCH", err.error);
    }

    @Test
    @DisplayName("Unlock: reservation mismatch (station mismatch) -> 409 RESERVATION_MISMATCH")
    void unlock_with_reservation_station_mismatch_returns_409_reservation_mismatch() {
        // IMPORTANT:
        // Con la logica attuale del servizio, prima viene verificato:
        //   stationId == vehicle.currentStationId
        // poi viene validata la reservation (stationId/vehicleId match).
        //
        // Se vuoi testare "RESERVATION_MISMATCH (station mismatch)" in modo puro,
        // devi rendere il veicolo coerente col path (S46) ma lasciare la reservation su S45.
        // Questo è uno scenario di "incoerenza" che può capitare se dati/store vengono alterati esternamente.
        String reservationId = createReservationAt(STATION_S45, VEHICLE_V123, USER_1);

        // Spostiamo solo il veicolo a S46 (senza toccare la reservation): serve a superare il check VEHICLE_NOT_AT_STATION
        Vehicle v = vehicleStore.findById(VEHICLE_V123).orElseThrow();
        v.setCurrentStationId(STATION_S46);
        vehicleStore.upsert(v);

        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R1;
        req.reservationId = reservationId;

        ErrorResponse err = unlockExpectingError(STATION_S46, VEHICLE_V123, req, 409);
        assertEquals("RESERVATION_MISMATCH", err.error);
    }

    @Test
    @DisplayName("Unlock: reservation CANCELED -> 409 RESERVATION_MISMATCH")
    void unlock_with_canceled_reservation_returns_409_reservation_mismatch() {
        // Given: create reservation
        String reservationId = createReservationAt(STATION_S45, VEHICLE_V123, USER_1);

        // And: cancel it
        cancelOk(STATION_S45, reservationId, USER_1);

        // When: unlock with canceled reservation
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R1;
        req.reservationId = reservationId;

        ErrorResponse err = unlockExpectingError(STATION_S45, VEHICLE_V123, req, 409);
        assertEquals("RESERVATION_MISMATCH", err.error);
    }

    @Test
    @DisplayName("Unlock: reservation CONSUMED -> 409 RESERVATION_ALREADY_CONSUMED")
    void unlock_with_consumed_reservation_returns_409_reservation_already_consumed() {
        // Given: create reservation
        String reservationId = createReservationAt(STATION_S45, VEHICLE_V123, USER_1);

        // And: consume it via unlock
        unlockOk(STATION_S45, VEHICLE_V123, USER_1, RENTAL_R1, reservationId);

        // And: bring back to make vehicle available again (otherwise we'd hit IN_USE rule first)
        lockOk(STATION_S45, VEHICLE_V123, RENTAL_R1);

        // When: try to unlock again with the same (now CONSUMED) reservation
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R2;
        req.reservationId = reservationId;

        ErrorResponse err = unlockExpectingError(STATION_S45, VEHICLE_V123, req, 409);
        assertEquals("RESERVATION_ALREADY_CONSUMED", err.error);
    }

    @Test
    @DisplayName("Unlock: userId diverso dal reservation.userId -> 403 NOT_AUTHORIZED")
    void unlock_with_reservation_by_different_user_returns_403_not_authorized() {
        // Given: reservation owned by USER_1
        String reservationId = createReservationAt(STATION_S45, VEHICLE_V123, USER_1);

        // When: USER_2 attempts to unlock using that reservation
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_2;
        req.rentalId = RENTAL_R1;
        req.reservationId = reservationId;

        ErrorResponse err = unlockExpectingError(STATION_S45, VEHICLE_V123, req, 403);
        assertEquals("NOT_AUTHORIZED", err.error);
    }

    @Test
    @DisplayName("Unlock: con reservation ma vehicle non DOCKED_RESERVED -> 409 VEHICLE_NOT_AVAILABLE")
    void unlock_with_reservation_when_vehicle_not_docked_reserved_returns_409_vehicle_not_available() {
        // Given: create reservation (reservation ACTIVE, vehicle DOCKED_RESERVED)
        String reservationId = createReservationAt(STATION_S45, VEHICLE_V123, USER_1);

        // Per colpire la regola "vehicle not DOCKED_RESERVED" con reservation ACTIVE,
        // dobbiamo simulare una incoerenza: veicolo riportato a DOCKED_AVAILABLE senza cancellare la reservation.
        // Questo è utile per assicurare che il servizio blocchi l'unlock con reservation
        // quando lo stato veicolo non è quello atteso.
        Vehicle v = vehicleStore.findById(VEHICLE_V123).orElseThrow();
        v.dockAt(STATION_S45);         // torna DOCKED_AVAILABLE e ripristina currentStationId
        v.clearReservation();          // rimuove puntatori reservation dal veicolo
        vehicleStore.upsert(v);

        // When: unlock with reservation ACTIVE ma vehicle non reserved
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R1;
        req.reservationId = reservationId;

        ErrorResponse err = unlockExpectingError(STATION_S45, VEHICLE_V123, req, 409);
        assertEquals("VEHICLE_NOT_AVAILABLE", err.error);
    }

    @Test
    @DisplayName("Unlock: senza reservation ma vehicle non DOCKED_AVAILABLE -> 409 VEHICLE_NOT_AVAILABLE")
    void unlock_without_reservation_when_vehicle_not_available_returns_409_vehicle_not_available() {
        // Given: vehicle reserved by USER_1 (so state becomes DOCKED_RESERVED)
        createReservationAt(STATION_S45, VEHICLE_V123, USER_1);

        // When: unlock immediate (no reservationId)
        UnlockRequest req = new UnlockRequest();
        req.userId = USER_1;
        req.rentalId = RENTAL_R1;
        req.reservationId = null;

        ErrorResponse err = unlockExpectingError(STATION_S45, VEHICLE_V123, req, 409);
        assertEquals("VEHICLE_NOT_AVAILABLE", err.error);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private String createReservationAt(String stationId, String vehicleId, String userId) {
        ReserveRequest req = new ReserveRequest();
        req.userId = userId;
        req.vehicleId = vehicleId;

        try (Response r = post(
                target.path("stations").path(stationId).path("reservations"),
                req
        )) {
            assertEquals(201, r.getStatus(), "Reserve should return 201");
            ReserveResponse res = r.readEntity(ReserveResponse.class);
            assertNotNull(res.reservationId, "Reserve must return reservationId");
            return res.reservationId;
        }
    }

    private CancelReservationResponse cancelOk(String stationId, String reservationId, String userId) {
        CancelReservationRequest req = new CancelReservationRequest();
        req.userId = userId;

        try (Response r = post(
                target.path("stations").path(stationId).path("reservations").path(reservationId).path("cancel"),
                req
        )) {
            assertEquals(200, r.getStatus(), "Cancel should return 200");
            return r.readEntity(CancelReservationResponse.class);
        }
    }

    private UnlockResponse unlockOk(String stationId,
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
            assertEquals(200, r.getStatus(), "Unlock should return 200");
            return r.readEntity(UnlockResponse.class);
        }
    }

    private LockResponse lockOk(String stationId,
                                String vehicleId,
                                String rentalId) {
        LockRequest req = new LockRequest();
        req.rentalId = rentalId;

        try (Response r = post(
                target.path("stations").path(stationId).path("vehicles").path(vehicleId).path("lock"),
                req
        )) {
            assertEquals(200, r.getStatus(), "Lock should return 200");
            return r.readEntity(LockResponse.class);
        }
    }

    private ErrorResponse unlockExpectingError(String stationId,
                                               String vehicleId,
                                               UnlockRequest req,
                                               int expectedStatus) {
        try (Response r = post(
                target.path("stations").path(stationId).path("vehicles").path(vehicleId).path("unlock"),
                req
        )) {
            assertEquals(expectedStatus, r.getStatus(), "Unlock should fail with " + expectedStatus);
            return r.readEntity(ErrorResponse.class);
        }
    }

    private Response post(WebTarget t, Object body) {
        Invocation.Builder b = t.request(MediaType.APPLICATION_JSON_TYPE);
        return b.post(Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
    }
}
