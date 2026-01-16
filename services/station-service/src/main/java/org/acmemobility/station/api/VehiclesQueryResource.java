package org.acmemobility.station.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acmemobility.station.api.mapper.StationApiMapper;
import org.acmemobility.station.domain.service.StationService;

import java.util.List;
import java.util.stream.Collectors;

@Path("/vehicles")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class VehiclesQueryResource {

    private final StationService stationService;
    private final StationApiMapper mapper;

    @Inject
    public VehiclesQueryResource(StationService stationService, StationApiMapper mapper) {
        this.stationService = stationService;
        this.mapper = mapper;
    }

    /**
     * GET /vehicles
     * Ritorna la lista di tutti i veicoli (ovunque siano).
     */
    @GET
    public Response listVehicles() {
        List<?> body = stationService.listVehicles().stream()
                .map(mapper::toVehicleDto)
                .collect(Collectors.toList());

        return Response.ok(body).build();
    }
}
