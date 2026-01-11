package org.acmemobility.station.api.exception;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.acmemobility.station.api.dto.ErrorResponse;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Catch-all ExceptionMapper: intercetta qualsiasi Exception NON già gestita da mapper più specifici.
 *
 * Scopo:
 * - evitare che eccezioni non previste producano HTML/stacktrace o risposte non JSON
 * - garantire che il contratto d'errore REST sia sempre rispettato:
 *     JSON -> ErrorResponse{ error = ... }
 * - loggare in modo affidabile gli errori "veri" (bug / 5xx) per debugging
 *
 * Priorità:
 * - USER + 10: è volutamente "dopo" i mapper più specifici (DomainExceptionMapper, ApiExceptionMapper, ecc.)
 *   così non li oscura.
 */
@Provider
@Priority(Priorities.USER + 10)
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class.getName());

    /**
     * Entry point chiamato da JAX-RS quando una Exception generica sfugge ai mapper più specifici.
     *
     * Due casi:
     * 1) WebApplicationException:
     *    - eccezione "JAX-RS native" che spesso contiene già un HTTP status (e magari una response)
     *    - noi la convertiamo comunque in JSON uniforme
     * 2) Qualsiasi altra Exception:
     *    - considerata "bug/errore tecnico" -> 500 INTERNAL_ERROR
     */
    @Override
    public Response toResponse(Exception ex) {

        // Caso speciale: JAX-RS (es. NotFoundException, NotAllowedException, ecc.)
        // Queste eccezioni portano già un status HTTP.
        if (ex instanceof WebApplicationException wae) {
            Response r = wae.getResponse();
            int status = (r != null) ? r.getStatus() : 500;

            // Logghiamo solo i 5xx: i 4xx sono spesso input errati / flusso normale.
            if (status >= 500) {
                LOG.log(Level.SEVERE, "WebApplicationException " + status, ex);
            }

            // Uniformiamo il body: non rimandiamo l'eventuale body originale dell'eccezione,
            // perché vogliamo una risposta sempre nel formato ErrorResponse.
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse("HTTP_" + status))
                    .build();
        }

        // Qualsiasi altra eccezione: non prevista -> errore interno (bug, NPE, concorrenza non gestita, ecc.)
        LOG.log(Level.SEVERE, "Unhandled exception", ex);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("INTERNAL_ERROR"))
                .build();
    }
}
