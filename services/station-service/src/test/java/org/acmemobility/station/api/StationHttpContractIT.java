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

@HelidonTest(resetPerTest = true)
@DisplayName("Station API – HTTP wiring contract (routing + GenericExceptionMapper)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StationHttpContractIT {

    private static final String STATION_S45 = "S45";
    private static final String STATION_S46 = "S46";
    private static final String VEHICLE_V123 = "V123";

    @Inject
    @Socket("@default")
    private WebTarget target;

    // Seed esplicito: rende i test indipendenti da eventuali "demo seed" via config.
    @Inject
    private InMemoryStationStore stationStore;

    @Inject
    private InMemoryVehicleStore vehicleStore;

    @Inject
    private InMemoryReservationStore reservationStore;

    @BeforeEach
    void seed() {
        // Questi test non dipendono davvero dallo stato di dominio,
        // ma mantenere lo stesso pattern degli altri IT evita sorprese future.
        stationStore.clear();
        vehicleStore.clear();
        reservationStore.clear();

        stationStore.upsert(new Station(STATION_S45));
        stationStore.upsert(new Station(STATION_S46));

        Vehicle v = new Vehicle(VEHICLE_V123);
        v.dockAt(STATION_S45);
        vehicleStore.upsert(v);
    }

    @Test
    @DisplayName("GET su endpoint non esistente -> 404 HTTP_404 (JSON)")
    void get_on_unknown_endpoint_returns_404_http_404_json() {
        try (Response r = request("definitely-not-a-route").get()) {
            assertEquals(404, r.getStatus());
            assertJson(r);

            ErrorResponse err = r.readEntity(ErrorResponse.class);
            assertEquals("HTTP_404", err.error);
        }
    }

    @Test
    @DisplayName("Metodo non supportato: GET su /stations/{stationId}/reservations (solo POST) -> 405 HTTP_405")
    void method_not_allowed_on_existing_resource_returns_405_http_405() {
        // La route ESISTE, ma non c'è un metodo GET: deve scattare 405.
        try (Response r = request("stations", STATION_S45, "reservations").get()) {
            assertEquals(405, r.getStatus());
            assertJson(r);

            ErrorResponse err = r.readEntity(ErrorResponse.class);
            assertEquals("HTTP_405", err.error);
        }
    }

    @Test
    @DisplayName("Path sbagliato: POST su /stations/{stationId}/reservation (manca 's') -> 404 HTTP_404")
    void post_on_wrong_path_returns_404_http_404() {
        // Usiamo un body JSON minimo: evitare post(null) previene NPE lato client.
        try (Response r = request("stations", STATION_S45, "reservation")
                .post(Entity.entity("{}", MediaType.APPLICATION_JSON_TYPE))) {

            assertEquals(404, r.getStatus());
            assertJson(r);

            ErrorResponse err = r.readEntity(ErrorResponse.class);
            assertEquals("HTTP_404", err.error);
        }
    }

    @Test
    @DisplayName("Path con segmento mancante: /stations//reservations -> 404 HTTP_404")
    void missing_path_segment_returns_404_http_404() {
        // Qui costruiamo un path "raw" con double slash.
        // A seconda del server/container, potrebbe essere normalizzato a /stations/reservations,
        // ma in entrambi i casi l'endpoint non esiste => 404 con body JSON via GenericExceptionMapper.
        try (Response r = requestRaw("stations//reservations").get()) {
            assertEquals(404, r.getStatus());
            assertJson(r);

            ErrorResponse err = r.readEntity(ErrorResponse.class);
            assertEquals("HTTP_404", err.error);
        }
    }

    // ----------------- helpers -----------------

    private Invocation.Builder request(String... pathParts) {
        WebTarget t = target;
        for (String p : pathParts) {
            t = t.path(p);
        }
        return t.request(MediaType.APPLICATION_JSON_TYPE);
    }

    /**
     * Variante "raw": permette di passare una stringa con slash interni
     * (utile per testare path anomali come double slash).
     */
    private Invocation.Builder requestRaw(String rawRelativePath) {
        return target.path(rawRelativePath).request(MediaType.APPLICATION_JSON_TYPE);
    }

    private static void assertJson(Response r) {
        String ct = r.getHeaderString("Content-Type");
        assertNotNull(ct, "Content-Type must be present");
        assertTrue(ct.toLowerCase().contains("application/json"),
                "Expected JSON Content-Type, got: " + ct);
    }
}
