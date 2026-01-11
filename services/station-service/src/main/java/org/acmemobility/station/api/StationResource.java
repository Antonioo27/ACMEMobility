package org.acmemobility.station.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acmemobility.station.api.dto.ErrorResponse;
import org.acmemobility.station.api.dto.LockRequest;
import org.acmemobility.station.api.dto.UnlockRequest;
import org.acmemobility.station.api.mapper.StationApiMapper;
import org.acmemobility.station.domain.service.LockResult;
import org.acmemobility.station.domain.service.StationService;
import org.acmemobility.station.domain.service.UnlockResult;

/**
 * REST resource principale per operazioni "operative" su un veicolo in una stazione:
 * - /unlock : sblocco (inizio/continuazione noleggio) con controlli di dominio
 * - /lock   : blocco (fine noleggio / chiusura veicolo) con controlli di dominio
 *
 * Questa classe fa da "API layer":
 * - valida input (controlli base)
 * - delega la logica vera a StationService (dominio)
 * - trasforma i risultati di dominio in DTO di risposta tramite StationApiMapper
 * - NON implementa regole di business (quelle stanno in StationServiceImpl)
 *
 * Nota:
 * - Non gestiamo idempotenza qui. Se in futuro vorrai aggiungerla, è un "layer"
 *   che può stare sopra queste stesse chiamate, senza cambiare le regole di dominio.
 */
@Path("/stations/{stationId}/vehicles/{vehicleId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class StationResource {

    private final StationService stationService;
    private final StationApiMapper mapper;

    @Inject
    public StationResource(StationService stationService, StationApiMapper mapper) {
        this.stationService = stationService;
        this.mapper = mapper;
    }

    /**
     * Endpoint: POST /stations/{stationId}/vehicles/{vehicleId}/unlock
     *
     * Input:
     * - stationId, vehicleId da path
     * - UnlockRequest body (rentalId, reservationId opzionale, userId)
     *
     * Output:
     * - 200 OK con UnlockResponse se ok
     * - 400 INVALID_REQUEST se input incompleto
     * - errori di dominio (DomainException) mappati da DomainExceptionMapper
     */
    @POST
    @Path("/unlock")
    public Response unlock(@PathParam("stationId") String stationId,
                           @PathParam("vehicleId") String vehicleId,
                           UnlockRequest request) {

        // Validazione minima: evita NullPointer e request "vuote".
        // Le regole di business (stato veicolo, reservation, autorizzazioni, ecc.) stanno nel dominio.
        if (isBlank(stationId) || isBlank(vehicleId) || request == null
                || isBlank(request.rentalId) || isBlank(request.userId)) {
            return badRequest();
        }

        // Logica reale: delega al dominio.
        // StationServiceImpl applica regole e aggiorna gli store.
        UnlockResult result = stationService.unlock(
                stationId,
                vehicleId,
                request.rentalId,
                request.reservationId,
                request.userId
        );

        // Traduzione dominio -> DTO risposta.
        Object body = mapper.toUnlockResponse(stationId, result);

        return Response.ok()
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    /**
     * Endpoint: POST /stations/{stationId}/vehicles/{vehicleId}/lock
     *
     * Input:
     * - stationId, vehicleId da path
     * - LockRequest body (rentalId)
     *
     * Output:
     * - 200 OK con LockResponse se ok
     * - 400 INVALID_REQUEST se input incompleto
     * - errori di dominio (DomainException) mappati da DomainExceptionMapper
     */
    @POST
    @Path("/lock")
    public Response lock(@PathParam("stationId") String stationId,
                         @PathParam("vehicleId") String vehicleId,
                         LockRequest request) {

        // Validazione minima: evita NullPointer e request "vuote".
        if (isBlank(stationId) || isBlank(vehicleId) || request == null || isBlank(request.rentalId)) {
            return badRequest();
        }

        LockResult result = stationService.lock(stationId, vehicleId, request.rentalId);

        Object body = mapper.toLockResponse(stationId, result);

        return Response.ok()
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    /**
     * Errore di validazione input (API layer).
     * Non va confuso con un errore di dominio.
     */
    private static Response badRequest() {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("INVALID_REQUEST"))
                .build();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
