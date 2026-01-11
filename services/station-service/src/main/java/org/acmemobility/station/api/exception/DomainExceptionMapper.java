package org.acmemobility.station.api.exception;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.acmemobility.station.api.dto.ErrorResponse;
import org.acmemobility.station.domain.error.DomainError;
import org.acmemobility.station.domain.error.DomainException;

/**
 * ExceptionMapper JAX-RS per DomainException.
 *
 * Scopo:
 * - trasformare in modo CENTRALIZZATO gli errori di dominio in risposte HTTP coerenti
 * - separare "regole di business" (domain) da "protocollo HTTP" (api)
 *
 * Comportamento:
 * - legge sempre ex.getError() (obbligatorio in DomainException)
 * - mappa DomainError -> HTTP status
 * - produce un JSON standard: ErrorResponse { error = <DomainError.name()> }
 *
 * Nota:
 * - questo mapper definisce il "contratto" degli errori verso i client.
 * - i nomi degli enum (DomainError.name()) sono parte del contratto: rinominarli rompe test/client.
 */
@Provider
@Priority(Priorities.USER)
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    /**
     * Entry point chiamato da JAX-RS quando una resource (o qualsiasi layer sotto)
     * lascia propagare una DomainException.
     *
     * Qui NON facciamo logica di dominio: facciamo solo traduzione in HTTP.
     */
    @Override
    public Response toResponse(DomainException ex) {
        // Mappo la semantica di dominio (codice) in un codice HTTP coerente col contratto REST.
        Response.Status status = mapDomainErrorToStatus(ex.getError());

        // Body: sempre un "error code" stringa (il name() dell'enum), utile per i client e i test.
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(ex.getError().name()))
                .build();
    }

    /**
     * Mappatura "business error" -> "HTTP status".
     *
     * Regola pratica:
     * - NOT_FOUND: risorsa inesistente (station/vehicle/reservation)
     * - FORBIDDEN: richiesta valida ma non autorizzata (user non owner, ecc.)
     * - CONFLICT: stati non compatibili, mismatch, vincoli, conflitti di concorrenza
     *
     * Nota architetturale:
     * - questa mappatura dovrebbe vivere in UN SOLO punto.
     *   Se la duplichi anche nelle Resource (per idempotency cache), rischi divergenze.
     *   In quel caso, estraila in una classe condivisa (es. DomainErrorHttpMapper).
     */
    private Response.Status mapDomainErrorToStatus(DomainError error) {
        return switch (error) {
            case STATION_NOT_FOUND, VEHICLE_NOT_FOUND, RESERVATION_NOT_FOUND -> Response.Status.NOT_FOUND;
            case NOT_AUTHORIZED -> Response.Status.FORBIDDEN;
            default -> Response.Status.CONFLICT; // mismatch/stati/station full ecc.
        };
    }
}
