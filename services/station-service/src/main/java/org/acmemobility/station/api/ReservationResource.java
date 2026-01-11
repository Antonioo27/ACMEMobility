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
import org.acmemobility.station.api.dto.CancelReservationRequest;
import org.acmemobility.station.api.dto.ErrorResponse;
import org.acmemobility.station.api.dto.ReserveRequest;
import org.acmemobility.station.api.mapper.StationApiMapper;
import org.acmemobility.station.domain.model.Reservation;
import org.acmemobility.station.domain.service.StationService;

/**
 * REST resource per operazioni sulle prenotazioni (reservation) di una stazione:
 *
 * - POST /stations/{stationId}/reservations
 *     crea una prenotazione per (vehicleId, userId)
 *
 * - POST /stations/{stationId}/reservations/{reservationId}/cancel
 *     annulla una prenotazione esistente (userId opzionale)
 *
 * Ruolo (API layer):
 * - valida input minimo (null/blank)
 * - delega al dominio (StationService) la logica vera e propria
 * - mappa risultato dominio -> DTO response via StationApiMapper
 *
 * Nota:
 * - Non gestiamo idempotenza qui. Se in futuro vorrai aggiungerla, è un "layer"
 *   che può stare sopra queste stesse chiamate, senza cambiare le regole di dominio.
 */
@Path("/stations/{stationId}/reservations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ReservationResource {

    private final StationService stationService;
    private final StationApiMapper mapper;

    @Inject
    public ReservationResource(StationService stationService, StationApiMapper mapper) {
        this.stationService = stationService;
        this.mapper = mapper;
    }

    /**
     * Endpoint: POST /stations/{stationId}/reservations
     *
     * Body: ReserveRequest { vehicleId, userId }
     *
     * Risposta:
     * - 201 Created + body (ReserveResponse) se prenotazione creata
     * - 400 INVALID_REQUEST se input incompleto
     * - errori di dominio (DomainException) gestiti da DomainExceptionMapper
     */
    @POST
    public Response reserve(@PathParam("stationId") String stationId,
                            ReserveRequest request) {

        // Validazione base: evita NPE e richieste palesemente invalide.
        if (request == null || isBlank(stationId) || isBlank(request.vehicleId) || isBlank(request.userId)) {
            return badRequest();
        }

        // Delego al dominio: qui vive la regola (vehicle prenotabile? già prenotato? ecc.).
        Reservation r = stationService.reserve(stationId, request.vehicleId, request.userId);

        // Mappo il modello di dominio in DTO di risposta.
        Object body = mapper.toReserveResponse(r);

        return Response.status(Response.Status.CREATED)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    /**
     * Endpoint: POST /stations/{stationId}/reservations/{reservationId}/cancel
     *
     * Body: CancelReservationRequest { userId } (opzionale, può essere null)
     *
     * Risposta:
     * - 200 OK + body (CancelReservationResponse) se annullamento ok
     * - 400 INVALID_REQUEST se stationId/reservationId mancanti
     * - errori di dominio (DomainException) gestiti da DomainExceptionMapper
     *
     * Nota: qui userId è opzionale; il dominio deciderà se serve e come validarlo.
     */
    @POST
    @Path("/{reservationId}/cancel")
    public Response cancel(@PathParam("stationId") String stationId,
                           @PathParam("reservationId") String reservationId,
                           CancelReservationRequest request) {

        // Il body può essere null; per indirizzare la risorsa servono stationId + reservationId.
        if (isBlank(stationId) || isBlank(reservationId)) {
            return badRequest();
        }

        // userId è opzionale: se request è null, userId resta null.
        String userId = (request == null) ? null : request.userId;

        // Dominio: verifica esistenza reservation, stato annullabile, eventuale autorizzazione.
        Reservation r = stationService.cancelReservation(stationId, reservationId, userId);

        Object body = mapper.toCancelResponse(r);

        return Response.ok()
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    /**
     * Input non valido (API layer).
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
