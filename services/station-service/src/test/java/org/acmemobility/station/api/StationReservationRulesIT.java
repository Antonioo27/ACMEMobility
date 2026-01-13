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
@DisplayName("Reservation API – business rules (integration tests)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StationReservationRulesIT {

    // ----------------- deterministic test data (seeded in @BeforeEach) -----------------
    private static final String STATION_S45 = "S45";
    private static final String STATION_S46 = "S46";
    private static final String VEHICLE_V123 = "V123";

    // ----------------- actors -----------------
    private static final String USER_1 = "U1";
    private static final String USER_2 = "U2";

    // ----------------- rentals (for state setup) -----------------
    private static final String RENTAL_R1 = "R1";
    private static final String RENTAL_R2 = "R2";

    @Inject
    @Socket("@default")
    private WebTarget target;

    // Rendiamo i test deterministici: pulizia + seed esplicito ad ogni test.
    @Inject
    private InMemoryStationStore stationStore;

    @Inject
    private InMemoryVehicleStore vehicleStore;

    @Inject
    private InMemoryReservationStore reservationStore;

    @BeforeEach
    void seed() {
        // Isolamento: niente stato tra test.
        stationStore.clear();
        vehicleStore.clear();
        reservationStore.clear();

        // Stazioni
        stationStore.upsert(new Station(STATION_S45));
        stationStore.upsert(new Station(STATION_S46));

        // Veicolo docked in S45
        Vehicle v123 = new Vehicle(VEHICLE_V123);
        v123.dockAt(STATION_S45);
        vehicleStore.upsert(v123);
    }

    // -------------------------------------------------------------------------
    // Reserve: validation / not-found / conflicts
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Reserve: payload incompleto (userId vuoto) -> 400 INVALID_REQUEST")
    void reserve_invalid_request_missing_userId_returns_400_invalid_request() {
        ReserveRequest req = new ReserveRequest();
        req.vehicleId = VEHICLE_V123;
        req.userId = ""; // invalid

        ErrorResponse err = reserveExpectingError(STATION_S45, req, 400);
        assertEquals("INVALID_REQUEST", err.error);
    }

    @Test
    @DisplayName("Reserve: station inesistente -> 404 STATION_NOT_FOUND")
    void reserve_station_not_found_returns_404_station_not_found() {
        ReserveRequest req = reserveRequest(USER_1, VEHICLE_V123);

        ErrorResponse err = reserveExpectingError("S404", req, 404);
        assertEquals("STATION_NOT_FOUND", err.error);
    }

    @Test
    @DisplayName("Reserve: vehicle inesistente -> 404 VEHICLE_NOT_FOUND")
    void reserve_vehicle_not_found_returns_404_vehicle_not_found() {
        ReserveRequest req = reserveRequest(USER_1, "V404");

        ErrorResponse err = reserveExpectingError(STATION_S45, req, 404);
        assertEquals("VEHICLE_NOT_FOUND", err.error);
    }

    @Test
    @DisplayName("Reserve: veicolo già riservato (active) -> 409 VEHICLE_ALREADY_RESERVED")
    void reserve_when_vehicle_already_reserved_returns_409_vehicle_already_reserved() {
        // Given: USER_1 reserves vehicle
        ReserveRequest first = reserveRequest(USER_1, VEHICLE_V123);
        ReserveResponse r1 = reserveOk(STATION_S45, first);
        assertNotNull(r1.reservationId);
        assertEquals("ACTIVE", r1.status);

        // When: USER_2 tries to reserve same vehicle
        ReserveRequest second = reserveRequest(USER_2, VEHICLE_V123);
        ErrorResponse err = reserveExpectingError(STATION_S45, second, 409);

        // Then
        assertEquals("VEHICLE_ALREADY_RESERVED", err.error);
    }

    @Test
    @DisplayName("Reserve: veicolo IN_USE -> 409 VEHICLE_IN_USE")
    void reserve_when_vehicle_in_use_returns_409_vehicle_in_use() {
        // Given: vehicle is put IN_USE via unlock (immediate rent)
        unlockOk(STATION_S45, VEHICLE_V123, USER_1, RENTAL_R1, null);

        // When: reserve is attempted
        ReserveRequest req = reserveRequest(USER_1, VEHICLE_V123);
        ErrorResponse err = reserveExpectingError(STATION_S45, req, 409);

        // Then
        assertEquals("VEHICLE_IN_USE", err.error);
    }

    @Test
    @DisplayName("Reserve: veicolo non è alla station richiesta -> 409 VEHICLE_NOT_AT_STATION")
    void reserve_when_vehicle_not_at_station_returns_409_vehicle_not_at_station() {
        // Given: move vehicle from S45 to S46 by unlocking and then locking at S46
        unlockOk(STATION_S45, VEHICLE_V123, USER_1, RENTAL_R1, null);
        lockOk(STATION_S46, VEHICLE_V123, RENTAL_R1);

        // When: reserve is attempted at the old station S45
        ReserveRequest req = reserveRequest(USER_1, VEHICLE_V123);
        ErrorResponse err = reserveExpectingError(STATION_S45, req, 409);

        // Then
        assertEquals("VEHICLE_NOT_AT_STATION", err.error);
    }

    // -------------------------------------------------------------------------
    // Cancel: validation / not-found / authorization / state
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Cancel: stationId blank (\" \") -> 400 INVALID_REQUEST")
    void cancel_invalid_request_blank_stationId_returns_400_invalid_request() {
        CancelReservationRequest req = new CancelReservationRequest();
        req.userId = USER_1;

        ErrorResponse err = cancelExpectingError(" ", "RSV-ANY", req, 400);
        assertEquals("INVALID_REQUEST", err.error);
    }

    @Test
    @DisplayName("Cancel: reservationId blank (\" \") -> 400 INVALID_REQUEST")
    void cancel_invalid_request_blank_reservationId_returns_400_invalid_request() {
        CancelReservationRequest req = new CancelReservationRequest();
        req.userId = USER_1;

        ErrorResponse err = cancelExpectingError(STATION_S45, " ", req, 400);
        assertEquals("INVALID_REQUEST", err.error);
    }

    @Test
    @DisplayName("Cancel: reservation inesistente -> 404 RESERVATION_NOT_FOUND")
    void cancel_reservation_not_found_returns_404_reservation_not_found() {
        CancelReservationRequest req = new CancelReservationRequest();
        req.userId = USER_1;

        ErrorResponse err = cancelExpectingError(STATION_S45, "RSV-NOT-EXIST", req, 404);
        assertEquals("RESERVATION_NOT_FOUND", err.error);
    }

    @Test
    @DisplayName("Cancel: stationId non corrisponde alla reservation -> 404 RESERVATION_NOT_FOUND")
    void cancel_station_mismatch_returns_404_reservation_not_found() {
        // Given: reservation created at S45
        String reservationId = createReservation(USER_1);

        // When: cancel is called with S46
        CancelReservationRequest req = new CancelReservationRequest();
        req.userId = USER_1;

        ErrorResponse err = cancelExpectingError(STATION_S46, reservationId, req, 404);
        assertEquals("RESERVATION_NOT_FOUND", err.error);
    }

    @Test
    @DisplayName("Cancel: utente diverso dal proprietario -> 403 NOT_AUTHORIZED")
    void cancel_by_different_user_returns_403_not_authorized() {
        // Given
        String reservationId = createReservation(USER_1);

        // When
        CancelReservationRequest req = new CancelReservationRequest();
        req.userId = USER_2;

        ErrorResponse err = cancelExpectingError(STATION_S45, reservationId, req, 403);

        // Then
        assertEquals("NOT_AUTHORIZED", err.error);
    }

    @Test
    @DisplayName("Cancel: reservation già CONSUMED -> 409 RESERVATION_ALREADY_CONSUMED")
    void cancel_consumed_reservation_returns_409_reservation_already_consumed() {
        // Given: create reservation for USER_1
        String reservationId = createReservation(USER_1);

        // And: consume it via unlock with reservationId
        unlockOk(STATION_S45, VEHICLE_V123, USER_1, RENTAL_R2, reservationId);

        // When: cancel is attempted
        CancelReservationRequest req = new CancelReservationRequest();
        req.userId = USER_1;

        ErrorResponse err = cancelExpectingError(STATION_S45, reservationId, req, 409);

        // Then
        assertEquals("RESERVATION_ALREADY_CONSUMED", err.error);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private String createReservation(String userId) {
        ReserveResponse res = reserveOk(STATION_S45, reserveRequest(userId, VEHICLE_V123));
        assertNotNull(res.reservationId, "Reserve must return reservationId");
        return res.reservationId;
    }

    private ReserveRequest reserveRequest(String userId, String vehicleId) {
        ReserveRequest req = new ReserveRequest();
        req.userId = userId;
        req.vehicleId = vehicleId;
        return req;
    }

    private ReserveResponse reserveOk(String stationId, ReserveRequest req) {
        try (Response r = post(
                target.path("stations").path(stationId).path("reservations"),
                req
        )) {
            assertEquals(201, r.getStatus(), "Reserve should return 201");
            return r.readEntity(ReserveResponse.class);
        }
    }

    private ErrorResponse reserveExpectingError(String stationId, ReserveRequest req, int expectedStatus) {
        try (Response r = post(
                target.path("stations").path(stationId).path("reservations"),
                req
        )) {
            assertEquals(expectedStatus, r.getStatus(), "Reserve should fail with " + expectedStatus);
            return r.readEntity(ErrorResponse.class);
        }
    }

    private ErrorResponse cancelExpectingError(String stationId,
                                               String reservationId,
                                               CancelReservationRequest req,
                                               int expectedStatus) {
        try (Response r = post(
                target.path("stations").path(stationId).path("reservations").path(reservationId).path("cancel"),
                req
        )) {
            assertEquals(expectedStatus, r.getStatus(), "Cancel should fail with " + expectedStatus);
            return r.readEntity(ErrorResponse.class);
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

    private Response post(WebTarget t, Object body) {
        Invocation.Builder b = t.request(MediaType.APPLICATION_JSON_TYPE);
        return b.post(Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
    }
}
