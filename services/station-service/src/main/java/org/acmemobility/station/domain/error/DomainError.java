package org.acmemobility.station.domain.error;

/**
 * Codici errore del DOMINIO (regole di business / invarianti).
 *
 * Scopo:
 * - rappresentare in modo stabile e "tecnico" le cause di fallimento previste dal dominio
 * - NON dipendere dal trasporto (HTTP): la mappatura DomainError -> HTTP status sta nell'API layer
 *   (es. DomainExceptionMapper o helper mapDomainErrorToStatus nelle Resource).
 *
 * Uso tipico:
 * - StationServiceImpl quando una regola fallisce:
 *     throw DomainException.of(DomainError.VEHICLE_NOT_AVAILABLE);
 *
 * - API layer intercetta DomainException e converte:
 *     DomainError -> Response.Status + ErrorResponse{error=<name()>}
 *
 * Nota:
 * - i nomi (enum.name()) diventano i codici che il client vede (ErrorResponse.error).
 *   Quindi sono parte del "contratto" esterno: se li rinomini, rompi i client e/o i test.
 */
public enum DomainError {

    // ----------------- Not found -----------------

    /** stationId inesistente */
    STATION_NOT_FOUND,

    /** vehicleId inesistente */
    VEHICLE_NOT_FOUND,

    /** reservationId inesistente */
    RESERVATION_NOT_FOUND,

    // ----------------- Authorization -----------------

    /**
     * Operazione richiesta da un utente non autorizzato.
     * Esempio: cancel/unlock con userId diverso da quello della reservation.
     */
    NOT_AUTHORIZED,

    // ----------------- Station constraints -----------------

    /**
     * La station non ha posti liberi per dockare (lock).
     * In StationServiceImpl.lock() viene verificato capacity vs veicoli docked.
     */
    STATION_FULL,

    // ----------------- Vehicle constraints / state -----------------

    /**
     * Il veicolo non è associato alla station indicata (es. currentStationId != stationId).
     * Tipico in reserve/unlock: deve essere fisicamente alla station.
     */
    VEHICLE_NOT_AT_STATION,

    /**
     * Il veicolo è già IN_USE (in noleggio).
     * In unlock: se è IN_USE e rentalId diverso -> VEHICLE_IN_USE_BY_OTHER_RENTAL.
     */
    VEHICLE_IN_USE,

    /**
     * Il veicolo non è IN_USE quando ci si aspetta che lo sia.
     * Tipico in lock: non puoi "rientrare" un veicolo che non è in uso.
     */
    VEHICLE_NOT_IN_USE,

    /**
     * Stato veicolo non compatibile con l'operazione richiesta.
     * Esempio: unlock senza reservation richiede DOCKED_AVAILABLE.
     * Oppure unlock con reservation richiede DOCKED_RESERVED.
     */
    VEHICLE_NOT_AVAILABLE,

    /**
     * Tentativo di reserve su un veicolo già riservato (DOCKED_RESERVED).
     */
    VEHICLE_ALREADY_RESERVED,

    /**
     * Lock idempotente: il veicolo risulta già docked, ma in un'altra station.
     * Quindi non è un "repeat" della stessa operazione.
     */
    VEHICLE_ALREADY_DOCKED_ELSEWHERE,

    /**
     * Il veicolo è già IN_USE ma sotto un rentalId diverso da quello della richiesta:
     * caso di doppio unlock concorrente con rental diversi.
     */
    VEHICLE_IN_USE_BY_OTHER_RENTAL,

    // ----------------- Reservation constraints / state -----------------

    /**
     * Incoerenza tra reservation e richiesta:
     * - reservation stationId != path stationId
     * - reservation vehicleId != path vehicleId
     * - vehicle.activeReservationId != reservationId richiesto
     * - reservation non è ACTIVE quando serve che lo sia
     */
    RESERVATION_MISMATCH,

    /**
     * La reservation è già stata consumata (CONSUMED) da un unlock precedente.
     * Non può essere riutilizzata.
     */
    RESERVATION_ALREADY_CONSUMED,

    // ----------------- Rental constraints -----------------

    /**
     * rentalId mancante o non coerente con lo stato del veicolo.
     * Esempi:
     * - unlock/lock senza rentalId
     * - lock con rentalId diverso da vehicle.activeRentalId
     */
    RENTAL_MISMATCH
}
