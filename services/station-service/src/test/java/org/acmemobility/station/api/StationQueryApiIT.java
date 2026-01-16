package org.acmemobility.station.api;

import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.acmemobility.station.api.dto.StationDto;
import org.acmemobility.station.api.dto.VehicleDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests (HTTP) per gli endpoint GET di query:
 * - GET /stations
 * - GET /vehicles
 * - GET /stations/{stationId}/vehicles
 * - GET /stations/total
 *
 * Scopo:
 * - verificare che gli endpoint siano esposti correttamente
 * - verificare che la risposta sia consistente con il seed in-memory
 * - verificare che gli endpoint "derivati" (vehiclesAtStation, total) siano coerenti con quelli base
 *
 * Nota:
 * - Questi test assumono che gli store in-memory seedino:
 *   - stazioni: S01..S05
 *   - veicoli : V001..V010
 */
@HelidonTest
class StationQueryApiIT {

    @Inject
    private WebTarget target;

    /**
     * Verifica che GET /stations risponda 200 e includa almeno le stazioni seedate (S01..S05).
     *
     * Non si controlla l'ordine (non garantito), ma la presenza degli ID.
     */
    @Test
    void getStations_returnsSeededStations() {
        try (Response res = target.path("stations").request().get()) {
            assertEquals(200, res.getStatus());

            List<StationDto> stations = res.readEntity(new GenericType<List<StationDto>>() {});
            assertNotNull(stations);
            assertFalse(stations.isEmpty());

            Set<String> ids = stations.stream()
                    .map(s -> s.stationId)
                    .collect(Collectors.toSet());

            // Seed atteso (S01..S05)
            for (int i = 1; i <= 5; i++) {
                String expected = String.format("S%02d", i);
                assertTrue(ids.contains(expected), "Missing station: " + expected);
            }
        }
    }

    /**
     * Verifica che GET /vehicles risponda 200 e includa almeno i veicoli seedati (V001..V010).
     *
     * Non si controlla l'ordine (non garantito), ma la presenza degli ID.
     */
    @Test
    void getVehicles_returnsSeededVehicles() {
        try (Response res = target.path("vehicles").request().get()) {
            assertEquals(200, res.getStatus());

            List<VehicleDto> vehicles = res.readEntity(new GenericType<List<VehicleDto>>() {});
            assertNotNull(vehicles);
            assertFalse(vehicles.isEmpty());

            Set<String> ids = vehicles.stream()
                    .map(v -> v.vehicleId)
                    .collect(Collectors.toSet());

            // Seed atteso (V001..V010)
            for (int i = 1; i <= 10; i++) {
                String expected = String.format("V%03d", i);
                assertTrue(ids.contains(expected), "Missing vehicle: " + expected);
            }
        }
    }

    /**
     * Verifica di coerenza tra:
     * - GET /vehicles (tutti i veicoli, con currentStationId valorizzato se docked)
     * - GET /stations/S01/vehicles (solo i veicoli docked a S01)
     *
     * Strategia:
     * 1) Leggo tutti i veicoli da /vehicles
     * 2) Calcolo l'insieme atteso dei vehicleId docked a S01 usando currentStationId
     * 3) Chiamo /stations/S01/vehicles e confronto gli insiemi
     *
     * Vantaggio:
     * - Il test resta robusto anche se altri test in futuro cambiano lo stato dei veicoli:
     *   la "verità" è calcolata al momento della chiamata.
     */
    @Test
    void getVehiclesAtStation_matchesFilterOfAllVehicles() {
        // 1) prendo tutti i veicoli
        List<VehicleDto> allVehicles;
        try (Response res = target.path("vehicles").request().get()) {
            assertEquals(200, res.getStatus());
            allVehicles = res.readEntity(new GenericType<List<VehicleDto>>() {});
        }

        // 2) filtro quelli "attualmente" docked a S01 (null se IN_USE)
        Set<String> expectedAtS01 = allVehicles.stream()
                .filter(v -> "S01".equals(v.currentStationId))
                .map(v -> v.vehicleId)
                .collect(Collectors.toSet());

        // 3) chiamo endpoint dedicato
        Set<String> actualAtS01;
        try (Response res = target.path("stations").path("S01").path("vehicles").request().get()) {
            assertEquals(200, res.getStatus());

            List<VehicleDto> atS01 = res.readEntity(new GenericType<List<VehicleDto>>() {});
            actualAtS01 = atS01.stream()
                    .map(v -> v.vehicleId)
                    .collect(Collectors.toSet());

            // Sanity: tutti quelli ritornati devono dichiarare currentStationId=S01
            assertTrue(atS01.stream().allMatch(v -> "S01".equals(v.currentStationId)));
        }

        assertEquals(expectedAtS01, actualAtS01);
    }

    /**
     * Verifica di coerenza dell'endpoint aggregato:
     * - GET /stations/total deve essere equivalente a fare:
     *   - GET /stations
     *   - GET /vehicles
     *
     * Confronto fatto per insiemi di ID (ordine non rilevante).
     */
    @Test
    void getTotal_matchesIndividualEndpoints() {
        List<StationDto> stations;
        try (Response res = target.path("stations").request().get()) {
            assertEquals(200, res.getStatus());
            stations = res.readEntity(new GenericType<List<StationDto>>() {});
        }

        List<VehicleDto> vehicles;
        try (Response res = target.path("vehicles").request().get()) {
            assertEquals(200, res.getStatus());
            vehicles = res.readEntity(new GenericType<List<VehicleDto>>() {});
        }

        StationsQueryResource.TotalResponse total;
        try (Response res = target.path("stations").path("total").request().get()) {
            assertEquals(200, res.getStatus());
            total = res.readEntity(StationsQueryResource.TotalResponse.class);
        }

        assertNotNull(total);
        assertNotNull(total.stations);
        assertNotNull(total.vehicles);

        Set<String> stationIds = stations.stream().map(s -> s.stationId).collect(Collectors.toSet());
        Set<String> totalStationIds = total.stations.stream().map(s -> s.stationId).collect(Collectors.toSet());
        assertEquals(stationIds, totalStationIds);

        Set<String> vehicleIds = vehicles.stream().map(v -> v.vehicleId).collect(Collectors.toSet());
        Set<String> totalVehicleIds = total.vehicles.stream().map(v -> v.vehicleId).collect(Collectors.toSet());
        assertEquals(vehicleIds, totalVehicleIds);
    }
}
