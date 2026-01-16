package org.acmemobility.station.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acmemobility.station.api.dto.StationDto;
import org.acmemobility.station.api.dto.VehicleDto;
import org.acmemobility.station.api.mapper.StationApiMapper;
import org.acmemobility.station.domain.service.StationService;

import java.util.List;
import java.util.stream.Collectors;

@Path("/stations")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class StationsQueryResource {

    private final StationService stationService;
    private final StationApiMapper mapper;

    @Inject
    public StationsQueryResource(StationService stationService, StationApiMapper mapper) {
        this.stationService = stationService;
        this.mapper = mapper;
    }

    /**
     * GET /stations
     * Ritorna la lista di tutte le stazioni.
     */
    @GET
    public Response listStations() {
        List<StationDto> body = stationService.listStations().stream()
                .map(mapper::toStationDto)
                .collect(Collectors.toList());

        return Response.ok(body).build();
    }

    /**
     * GET /stations/{stationId}/vehicles
     * Ritorna i veicoli docked in quella stazione (opzionale ma utile).
     */
    @GET
    @Path("/{stationId}/vehicles")
    public Response listVehiclesAtStation(@PathParam("stationId") String stationId) {
        List<VehicleDto> body = stationService.listVehiclesAtStation(stationId).stream()
                .map(mapper::toVehicleDto)
                .collect(Collectors.toList());

        return Response.ok(body).build();
    }

    /**
     * GET /stations/total
     * Ritorna in un colpo solo: stations + vehicles.
     * Utile per mobile app / UI (1 round-trip).
     */
    @GET
    @Path("/total")
    public Response getTotal() {
        List<StationDto> stations = stationService.listStations().stream()
                .map(mapper::toStationDto)
                .collect(Collectors.toList());

        List<VehicleDto> vehicles = stationService.listVehicles().stream()
                .map(mapper::toVehicleDto)
                .collect(Collectors.toList());

        TotalResponse body = new TotalResponse(stations, vehicles);
        return Response.ok(body).build();
    }

    public static class TotalResponse {
        public List<StationDto> stations;
        public List<VehicleDto> vehicles;

        public TotalResponse() {}

        public TotalResponse(List<StationDto> stations, List<VehicleDto> vehicles) {
            this.stations = stations;
            this.vehicles = vehicles;
        }
    }

}
