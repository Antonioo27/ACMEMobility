package org.acmemobility.station.api.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.acmemobility.station.api.dto.CancelReservationResponse;
import org.acmemobility.station.api.dto.ErrorResponse;
import org.acmemobility.station.api.dto.LockResponse;
import org.acmemobility.station.api.dto.ReserveResponse;
import org.acmemobility.station.api.dto.UnlockResponse;
import org.acmemobility.station.domain.model.Reservation;
import org.acmemobility.station.domain.model.Vehicle;
import org.acmemobility.station.domain.service.LockResult;
import org.acmemobility.station.domain.service.UnlockResult;

/**
 * Mapper "API layer": converte oggetti di dominio (Reservation, Vehicle, risultati del service)
 * in DTO di risposta della REST API.
 *
 * Scopo:
 * - tenere la REST resource pulita: la resource gestisce HTTP (path, status),
 *   il mapper costruisce i DTO.
 * - evitare che DTO "inquinino" il dominio e viceversa.
 *
 * Nota: qui NON ci sono regole di dominio (quelle stanno in StationServiceImpl).
 * Qui c'è solo traduzione dati + piccole scelte di "source of truth" (es. stationId dal path).
 */
@ApplicationScoped
public class StationApiMapper {

    /**
     * Converte una Reservation di dominio in ReserveResponse (DTO).
     *
     * Campi chiave:
     * - status è serializzato come stringa tramite enum.name()
     * - expiresAt può essere null (il contratto lo consente)
     */
    public ReserveResponse toReserveResponse(Reservation r) {
        if (r == null) {
            throw new IllegalArgumentException("Reservation is null");
        }

        ReserveResponse res = new ReserveResponse();
        res.reservationId = r.getReservationId();
        res.stationId = r.getStationId();
        res.vehicleId = r.getVehicleId();
        res.status = (r.getStatus() != null) ? r.getStatus().name() : null;
        res.expiresAt = r.getExpiresAt(); // può essere null: ok
        return res;
    }

    /**
     * Converte una Reservation di dominio in CancelReservationResponse (DTO).
     *
     * Qui non rimandiamo stationId/vehicleId perché la risposta di cancel nel vostro contratto
     * è minimale: id + stato finale.
     */
    public CancelReservationResponse toCancelResponse(Reservation r) {
        if (r == null) {
            throw new IllegalArgumentException("Reservation is null");
        }

        CancelReservationResponse res = new CancelReservationResponse();
        res.reservationId = r.getReservationId();
        res.status = (r.getStatus() != null) ? r.getStatus().name() : null;
        return res;
    }

    /**
     * Converte il risultato di unlock in UnlockResponse (DTO).
     *
     * Nota importante: stationId nella response.
     * - dopo unlock, nel dominio il veicolo ha currentStationId = null (perché è in uso, non è docked)
     * - ma l'API deve rispondere "da quale station è stato sbloccato", e quel dato è nel path.
     *
     * Quindi: stationIdFromPath è la source-of-truth per la response.
     */
    public UnlockResponse toUnlockResponse(String stationIdFromPath, UnlockResult result) {
        if (result == null || result.getVehicle() == null) {
            throw new IllegalArgumentException("UnlockResult/Vehicle is null");
        }

        Vehicle v = result.getVehicle();

        UnlockResponse res = new UnlockResponse();
        res.vehicleId = v.getVehicleId();

        // Per unlock, lo stationId "giusto" per la risposta è quello del path:
        // il veicolo sta uscendo dalla station e nel dominio viene settato currentStationId = null.
        res.stationId = stationIdFromPath;

        res.vehicleState = (v.getState() != null) ? v.getState().name() : null;
        res.activeRentalId = v.getActiveRentalId();

        // Se unlock ha consumato una reservation, la riportiamo per tracciare il legame.
        res.consumedReservationId = result.getConsumedReservationId();

        return res;
    }

    /**
     * Converte il risultato di lock in LockResponse (DTO).
     *
     * Nota: stationId nella response.
     * - dopo lock, nel dominio il veicolo viene dockato e currentStationId diventa stationId
     * - qui usiamo comunque stationIdFromPath come source-of-truth (coerente con l'API e più robusto)
     *
     * Se vuoi essere "paranoico" in futuro:
     * - potresti verificare che stationIdFromPath == v.getCurrentStationId() e,
     *   se differiscono, loggare un warning (ma non è necessario per ora).
     */
    public LockResponse toLockResponse(String stationIdFromPath, LockResult result) {
        if (result == null || result.getVehicle() == null) {
            throw new IllegalArgumentException("LockResult/Vehicle is null");
        }

        Vehicle v = result.getVehicle();

        LockResponse res = new LockResponse();
        res.vehicleId = v.getVehicleId();

        // Per lock, lo stationId deve essere quello dove lo stai dockando:
        // uso il path come source of truth per l'API.
        res.stationId = stationIdFromPath;

        res.vehicleState = (v.getState() != null) ? v.getState().name() : null;

        // Nel vostro dominio LockResult porta l'id del rental che è stato chiuso (o comunque confermato).
        res.closedRentalId = result.getClosedRentalId();

        return res;
    }

    /**
     * Factory semplice per ErrorResponse.
     * Utile se in futuro vuoi centralizzare la "normalizzazione" dei codici di errore.
     */
    public ErrorResponse error(String code) {
        return new ErrorResponse(code);
    }
}
