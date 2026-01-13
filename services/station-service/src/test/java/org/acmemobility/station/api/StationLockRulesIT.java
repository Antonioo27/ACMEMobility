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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@HelidonTest // niente resetPerTest=true: eviti problemi con GlobalConfig
@DisplayName("Station API – Lock rules (integration tests)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StationLockRulesIT {

    // ----------------- deterministic test data (seeded in @BeforeEach) -----------------
    private static final String STATION_S45 = "S45";
    private static final String STATION_S46 = "S46";
    private static final String VEHICLE_V123 = "V123";

    // ----------------- actors -----------------
    private static final String USER_1 = "U1";

    // ----------------- rentals -----------------
    private static final String RENTAL_R1 = "R1";
    private static final String RENTAL_R2 = "R2";

    @Inject
    @Socket("@default")
    private WebTarget target;

    // Seed esplicito: eviti dipendenze da "demo seed" e rendi i test deterministic.
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

        stationStore.upsert(new Station(STATION_S45));
        stationStore.upsert(new Station(STATION_S46));

        // Veicolo docked in S45 (stato iniziale standard per i casi lock/unlock/reserve).
        Vehicle v123 = new Vehicle(VEHICLE_V123);
        v123.dockAt(STATION_S45);
        vehicleStore.upsert(v123);
    }

    // -------------------------------------------------------------------------
    // A) API validation (400)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Lock: rentalId blank -> 400 INVALID_REQUEST")
    void lock_invalid_request_blank_rentalId_returns_400_invalid_request() {
        LockRequest req = new LockRequest();
        req.rentalId = " ";

        ErrorResponse err = lockExpectingError(STATION_S45, VEHICLE_V123, req, 400);
        assertEquals("INVALID_REQUEST", err.error);
    }

    // -------------------------------------------------------------------------
    // B) Not found (404)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Lock: station inesistente -> 404 STATION_NOT_FOUND")
    void lock_station_not_found_returns_404_station_not_found() {
        LockRequest req = new LockRequest();
        req.rentalId = RENTAL_R1;

        ErrorResponse err = lockExpectingError("S404", VEHICLE_V123, req, 404);
        assertEquals("STATION_NOT_FOUND", err.error);
    }

    @Test
    @DisplayName("Lock: vehicle inesistente -> 404 VEHICLE_NOT_FOUND")
    void lock_vehicle_not_found_returns_404_vehicle_not_found() {
        LockRequest req = new LockRequest();
        req.rentalId = RENTAL_R1;

        ErrorResponse err = lockExpectingError(STATION_S45, "V404", req, 404);
        assertEquals("VEHICLE_NOT_FOUND", err.error);
    }

    // -------------------------------------------------------------------------
    // C) Domain conflicts (409)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Lock: vehicle DOCKED_AVAILABLE ma in station diversa -> 409 VEHICLE_ALREADY_DOCKED_ELSEWHERE")
    void lock_when_vehicle_already_docked_elsewhere_returns_409_vehicle_already_docked_elsewhere() {
        // Given: move vehicle from S45 to S46 (unlock + lock at S46)
        unlockOk(STATION_S45, VEHICLE_V123, USER_1, RENTAL_R1, null);
        lockOk(STATION_S46, VEHICLE_V123, RENTAL_R1);

        // When: try to lock at S45 while already docked at S46
        LockRequest req = new LockRequest();
        req.rentalId = RENTAL_R1;

        ErrorResponse err = lockExpectingError(STATION_S45, VEHICLE_V123, req, 409);
        assertEquals("VEHICLE_ALREADY_DOCKED_ELSEWHERE", err.error);
    }

    @Test
    @DisplayName("Lock: vehicle DOCKED_RESERVED -> 409 VEHICLE_NOT_IN_USE")
    void lock_when_vehicle_is_docked_reserved_returns_409_vehicle_not_in_use() {
        // Given: vehicle reserved (state DOCKED_RESERVED)
        createReservationAt(STATION_S45, USER_1);

        // When: lock is called (but vehicle is not IN_USE)
        LockRequest req = new LockRequest();
        req.rentalId = RENTAL_R1;

        ErrorResponse err = lockExpectingError(STATION_S45, VEHICLE_V123, req, 409);
        assertEquals("VEHICLE_NOT_IN_USE", err.error);
    }

    @Test
    @DisplayName("Lock: rentalId diverso da activeRentalId -> 409 RENTAL_MISMATCH")
    void lock_when_rental_mismatch_returns_409_rental_mismatch() {
        // Given: vehicle IN_USE with rental R1
        unlockOk(STATION_S45, VEHICLE_V123, USER_1, RENTAL_R1, null);

        // When: lock with different rental R2
        LockRequest req = new LockRequest();
        req.rentalId = RENTAL_R2;

        ErrorResponse err = lockExpectingError(STATION_S45, VEHICLE_V123, req, 409);
        assertEquals("RENTAL_MISMATCH", err.error);
    }

    // -------------------------------------------------------------------------
    // NOTE on "vehicle non è IN_USE -> 409 VEHICLE_NOT_IN_USE"
    // -------------------------------------------------------------------------
    // Nel dominio attuale i soli stati pratici sono DOCKED_AVAILABLE / DOCKED_RESERVED / IN_USE.
    // - DOCKED_RESERVED è già coperto sopra (-> VEHICLE_NOT_IN_USE).
    // - DOCKED_AVAILABLE nella stessa station è volutamente idempotente (-> 200).
    // Quindi non c’è un secondo caso “naturale” via API che generi VEHICLE_NOT_IN_USE
    // diverso da DOCKED_RESERVED, senza introdurre altri stati o corruzione dello store.

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private String createReservationAt(String stationId, String userId) {
        ReserveRequest req = new ReserveRequest();
        req.userId = userId;
        req.vehicleId = VEHICLE_V123;

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

    private ErrorResponse lockExpectingError(String stationId,
                                             String vehicleId,
                                             LockRequest req,
                                             int expectedStatus) {
        try (Response r = post(
                target.path("stations").path(stationId).path("vehicles").path(vehicleId).path("lock"),
                req
        )) {
            assertEquals(expectedStatus, r.getStatus(), "Lock should fail with " + expectedStatus);
            return r.readEntity(ErrorResponse.class);
        }
    }

    private Response post(WebTarget t, Object body) {
        Invocation.Builder b = t.request(MediaType.APPLICATION_JSON_TYPE);
        return b.post(Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
    }
}
