package org.acmemobility.station.api;

import io.helidon.microprofile.testing.Socket;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acmemobility.station.api.dto.ErrorResponse;
import org.acmemobility.station.api.dto.ReserveRequest;
import org.acmemobility.station.api.dto.ReserveResponse;
import org.acmemobility.station.api.dto.UnlockRequest;
import org.acmemobility.station.api.dto.UnlockResponse;
import org.acmemobility.station.domain.model.Station;
import org.acmemobility.station.domain.model.Vehicle;
import org.acmemobility.station.persistence.store.inmemory.InMemoryReservationStore;
import org.acmemobility.station.persistence.store.inmemory.InMemoryStationStore;
import org.acmemobility.station.persistence.store.inmemory.InMemoryVehicleStore;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@HelidonTest(resetPerTest = true)
@DisplayName("Station API – Concurrency (VehicleLockManager races)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StationConcurrencyIT {

    private static final String STATION_ID = "S45";
    private static final String VEHICLE_ID = "V123";

    private static final String USER_1 = "U1";
    private static final String USER_2 = "U2";

    private static final String RENTAL_1 = "R-CONC-1";
    private static final String RENTAL_2 = "R-CONC-2";

    private ExecutorService pool;

    @Inject
    @Socket("@default")
    private WebTarget target;

    @Inject
    private InMemoryStationStore stationStore;

    @Inject
    private InMemoryVehicleStore vehicleStore;

    @Inject
    private InMemoryReservationStore reservationStore;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(2);

        // Reset deterministico: evita leakage di dati tra test.
        stationStore.clear();
        vehicleStore.clear();
        reservationStore.clear();

        // Seed: stazione capiente e veicolo docked e disponibile.
        stationStore.upsert(new Station(STATION_ID));

        Vehicle v = new Vehicle(VEHICLE_ID);
        v.dockAt(STATION_ID);
        vehicleStore.upsert(v);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(15)
    @DisplayName("2 reserve concorrenti sullo stesso vehicle -> uno 201, uno 409 VEHICLE_ALREADY_RESERVED")
    void concurrent_reserve_same_vehicle_one_wins_other_conflicts() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<HttpCallResult> c1 = () -> {
            barrierAwait(barrier);

            ReserveRequest req = new ReserveRequest();
            req.userId = USER_1;
            req.vehicleId = VEHICLE_ID;

            return reserveCall(STATION_ID, req);
        };

        Callable<HttpCallResult> c2 = () -> {
            barrierAwait(barrier);

            ReserveRequest req = new ReserveRequest();
            req.userId = USER_2;
            req.vehicleId = VEHICLE_ID;

            return reserveCall(STATION_ID, req);
        };

        List<HttpCallResult> results = run2(c1, c2);

        long created = results.stream().filter(r -> r.status == 201).count();
        long conflicts = results.stream().filter(r -> r.status == 409).count();

        assertEquals(1, created, "Expected exactly one 201 CREATED, got: " + results);
        assertEquals(1, conflicts, "Expected exactly one 409 CONFLICT, got: " + results);

        HttpCallResult ok = results.stream().filter(r -> r.status == 201).findFirst().orElseThrow();
        assertNotNull(ok.reserve, "201 response must deserialize ReserveResponse");
        assertNotNull(ok.reserve.reservationId, "reservationId must not be null");
        assertEquals("ACTIVE", ok.reserve.status);
        assertEquals(STATION_ID, ok.reserve.stationId);
        assertEquals(VEHICLE_ID, ok.reserve.vehicleId);

        HttpCallResult ko = results.stream().filter(r -> r.status == 409).findFirst().orElseThrow();
        assertNotNull(ko.error, "409 response must deserialize ErrorResponse");
        assertEquals("VEHICLE_ALREADY_RESERVED", ko.error.error);
    }

    @Test
    @Timeout(15)
    @DisplayName("2 unlock concorrenti (rental diversi) sullo stesso vehicle -> uno 200, uno 409 VEHICLE_IN_USE_BY_OTHER_RENTAL")
    void concurrent_unlock_same_vehicle_different_rentals_one_wins_other_conflicts() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<HttpCallResult> c1 = () -> {
            barrierAwait(barrier);

            UnlockRequest req = new UnlockRequest();
            req.userId = USER_1;
            req.rentalId = RENTAL_1;
            req.reservationId = null; // immediate rent

            return unlockCall(STATION_ID, VEHICLE_ID, req);
        };

        Callable<HttpCallResult> c2 = () -> {
            barrierAwait(barrier);

            UnlockRequest req = new UnlockRequest();
            req.userId = USER_1;
            req.rentalId = RENTAL_2;
            req.reservationId = null;

            return unlockCall(STATION_ID, VEHICLE_ID, req);
        };

        List<HttpCallResult> results = run2(c1, c2);

        long ok = results.stream().filter(r -> r.status == 200).count();
        long conflicts = results.stream().filter(r -> r.status == 409).count();

        assertEquals(1, ok, "Expected exactly one 200 OK, got: " + results);
        assertEquals(1, conflicts, "Expected exactly one 409 CONFLICT, got: " + results);

        HttpCallResult winner = results.stream().filter(r -> r.status == 200).findFirst().orElseThrow();
        assertNotNull(winner.unlock, "200 response must deserialize UnlockResponse");
        assertEquals(VEHICLE_ID, winner.unlock.vehicleId);
        assertEquals(STATION_ID, winner.unlock.stationId);
        assertEquals("IN_USE", winner.unlock.vehicleState);
        assertNotNull(winner.unlock.activeRentalId);

        HttpCallResult loser = results.stream().filter(r -> r.status == 409).findFirst().orElseThrow();
        assertNotNull(loser.error, "409 response must deserialize ErrorResponse");
        assertEquals("VEHICLE_IN_USE_BY_OTHER_RENTAL", loser.error.error);
    }

    // ----------------- concurrency helpers -----------------

    private static void barrierAwait(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("Barrier await failed", e);
        }
    }

    private List<HttpCallResult> run2(Callable<HttpCallResult> c1, Callable<HttpCallResult> c2) throws Exception {
        Future<HttpCallResult> f1 = pool.submit(c1);
        Future<HttpCallResult> f2 = pool.submit(c2);

        HttpCallResult r1 = f1.get(10, TimeUnit.SECONDS);
        HttpCallResult r2 = f2.get(10, TimeUnit.SECONDS);

        return List.of(r1, r2);
    }

    // ----------------- HTTP calls (typed result) -----------------

    private HttpCallResult reserveCall(String stationId, ReserveRequest req) {
        WebTarget t = target.path("stations").path(stationId).path("reservations");

        try (Response r = post(t, req)) {
            int status = r.getStatus();

            if (status == 201) {
                return HttpCallResult.created(r.readEntity(ReserveResponse.class));
            }

            if (status == 400 || status == 403 || status == 404 || status == 409) {
                return HttpCallResult.error(status, r.readEntity(ErrorResponse.class));
            }

            r.bufferEntity();
            fail("Unexpected status for reserve: " + status + " body=" + r.readEntity(String.class));
            return null;
        }
    }

    private HttpCallResult unlockCall(String stationId, String vehicleId, UnlockRequest req) {
        WebTarget t = target.path("stations").path(stationId).path("vehicles").path(vehicleId).path("unlock");

        try (Response r = post(t, req)) {
            int status = r.getStatus();

            if (status == 200) {
                return HttpCallResult.ok(r.readEntity(UnlockResponse.class));
            }

            if (status == 400 || status == 403 || status == 404 || status == 409) {
                return HttpCallResult.error(status, r.readEntity(ErrorResponse.class));
            }

            r.bufferEntity();
            fail("Unexpected status for unlock: " + status + " body=" + r.readEntity(String.class));
            return null;
        }
    }

    private Response post(WebTarget t, Object body) {
        Invocation.Builder b = t.request(MediaType.APPLICATION_JSON_TYPE);
        return b.post(Entity.entity(body, MediaType.APPLICATION_JSON_TYPE));
    }

    // ----------------- result wrapper -----------------

    private static final class HttpCallResult {
        final int status;
        final ReserveResponse reserve; // valorizzato solo per 201
        final UnlockResponse unlock;   // valorizzato solo per 200
        final ErrorResponse error;     // valorizzato per 4xx

        private HttpCallResult(int status, ReserveResponse reserve, UnlockResponse unlock, ErrorResponse error) {
            this.status = status;
            this.reserve = reserve;
            this.unlock = unlock;
            this.error = error;
        }

        static HttpCallResult created(ReserveResponse res) {
            return new HttpCallResult(201, Objects.requireNonNull(res), null, null);
        }

        static HttpCallResult ok(UnlockResponse res) {
            return new HttpCallResult(200, null, Objects.requireNonNull(res), null);
        }

        static HttpCallResult error(int status, ErrorResponse err) {
            return new HttpCallResult(status, null, null, Objects.requireNonNull(err));
        }

        @Override
        public String toString() {
            return "HttpCallResult{status=" + status +
                    ", reserve=" + (reserve != null ? reserve.status + ":" + reserve.reservationId : "null") +
                    ", unlock=" + (unlock != null ? unlock.vehicleState + ":" + unlock.activeRentalId : "null") +
                    ", error=" + (error != null ? error.error : "null") +
                    '}';
        }
    }
}
