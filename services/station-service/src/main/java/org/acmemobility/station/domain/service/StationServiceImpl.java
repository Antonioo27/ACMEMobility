package org.acmemobility.station.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acmemobility.station.domain.error.DomainError;
import org.acmemobility.station.domain.error.DomainException;
import org.acmemobility.station.domain.model.*;
import org.acmemobility.station.persistence.lock.VehicleLockManager;
import org.acmemobility.station.persistence.store.ReservationStore;
import org.acmemobility.station.persistence.store.StationStore;
import org.acmemobility.station.persistence.store.VehicleStore;
import org.acmemobility.station.domain.service.integration.VehicleCommandDispatcher;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;


@ApplicationScoped
public class StationServiceImpl implements StationService {

    // Store (in-memory) che rappresentano lo "stato" persistente del servizio.
    private final StationStore stationStore;
    private final VehicleStore vehicleStore;
    private final ReservationStore reservationStore;

    // Lock manager per serializzare operazioni concorrenti sullo stesso veicolo.
    // Obiettivo: evitare race tra reserve/unlock/cancel/lock sullo stesso vehicleId.
    private final VehicleLockManager lockManager;

    // TTL della reservation, configurabile da MicroProfile config (default 30 minuti).
    private final long reservationTtlMinutes;

    private final VehicleCommandDispatcher commandDispatcher;

    @Inject
    public StationServiceImpl(StationStore stationStore,
                              VehicleStore vehicleStore,
                              ReservationStore reservationStore,
                              VehicleLockManager lockManager,
                              VehicleCommandDispatcher commandDispatcher, 
                              @ConfigProperty(name = "station.reservation.ttl.minutes", defaultValue = "30")
                              long reservationTtlMinutes) {
        this.stationStore = stationStore;
        this.vehicleStore = vehicleStore;
        this.reservationStore = reservationStore;
        this.lockManager = lockManager;
        this.reservationTtlMinutes = reservationTtlMinutes;
        this.commandDispatcher = commandDispatcher;
    }

    @Override
    public Reservation reserve(String stationId, String vehicleId, String userId) {
        // Verifica esistenza stazione (fail-fast).
        Station station = requireStation(stationId);

        // Controlli minimi richiesti dal dominio (non dall’API).
        if (isBlank(vehicleId) || isBlank(userId)) {
            throw DomainException.of(DomainError.RENTAL_MISMATCH, "vehicleId and userId are required");
        }

        // Serializzazione per veicolo: tutte le operazioni che mutano Vehicle/Reservation
        // devono essere atomiche rispetto allo stesso vehicleId.
        return lockManager.withVehicleLock(vehicleId, () -> {
            Vehicle v = requireVehicle(vehicleId);

            // Non puoi prenotare un veicolo già in uso.
            if (v.getState() == VehicleState.IN_USE) {
                throw DomainException.of(DomainError.VEHICLE_IN_USE);
            }

            // Vincolo: il veicolo deve essere fisicamente presso la station richiesta.
            if (!station.getStationId().equals(v.getCurrentStationId())) {
                throw DomainException.of(DomainError.VEHICLE_NOT_AT_STATION);
            }

            // Se il veicolo risulta RESERVED ma la reservation associata è scaduta/cancellata/inesistente,
            // normalizziamo lo stato a AVAILABLE prima di procedere.
            releaseStaleReservationIfAny(v, station.getStationId(), Instant.now());

            // Dopo la normalizzazione, per prenotare deve essere disponibile e docked.
            if (v.getState() != VehicleState.DOCKED_AVAILABLE) {
                // Tipicamente: era già riservato da un’altra reservation valida.
                throw DomainException.of(DomainError.VEHICLE_ALREADY_RESERVED);
            }

            // Creazione reservation.
            String reservationId = "RSV-" + UUID.randomUUID();
            Instant now = Instant.now();
            Instant expiresAt = now.plus(reservationTtlMinutes, ChronoUnit.MINUTES);

            Reservation r = new Reservation(
                    reservationId,
                    station.getStationId(),
                    vehicleId,
                    userId,
                    ReservationStatus.ACTIVE,
                    now,
                    expiresAt
            );

            // Aggiorna stato veicolo: reserved + puntatore alla reservation + owner.
            // Scelta "pulizia mentale": usiamo transizioni coerenti del modello invece di setter sparsi.
            v.reserve(reservationId, userId);

            // Persistenza: prima la reservation, poi il veicolo (ordine non critico in-memory,
            // ma concettualmente: crei la reservation e poi "agganci" il veicolo).
            reservationStore.upsert(r);
            vehicleStore.upsert(v);

            return r;
        });
    }

    @Override
    public Reservation cancelReservation(String stationId, String reservationId, String userId) {
        // Verifica esistenza stazione (fail-fast).
        requireStation(stationId);

        // userId obbligatorio: serve per autorizzazione/coerenza (solo owner può cancellare).
        if (isBlank(userId)) {
            throw DomainException.of(DomainError.RENTAL_MISMATCH, "userId is required");
        }

        // Prima lettura per validare appartenenza stationId -> reservation.
        Reservation initial = requireReservation(reservationId);
        if (!stationId.equals(initial.getStationId())) {
            // Scelta: nascondere l’esistenza della reservation se non appartiene alla station richiesta.
            throw DomainException.of(DomainError.RESERVATION_NOT_FOUND);
        }

        // Serializziamo sul veicolo della reservation, perché cancel compete con unlock (consumo reservation).
        return lockManager.withVehicleLock(initial.getVehicleId(), () -> {
            // Re-read: entro lock rileggo per avere lo stato più aggiornato.
            Reservation r = requireReservation(reservationId);
            if (!stationId.equals(r.getStationId())) {
                throw DomainException.of(DomainError.RESERVATION_NOT_FOUND);
            }

            // Autorizzazione: solo owner può cancellare.
            if (!userId.equals(r.getUserId())) {
                throw DomainException.of(DomainError.NOT_AUTHORIZED);
            }

            Instant now = Instant.now();

            // Se era ACTIVE ma è scaduta, la marchiamo EXPIRED.
            // Questo mantiene il modello coerente e rende l'operazione deterministica.
            if (r.getStatus() == ReservationStatus.ACTIVE && r.isExpired(now)) {
                r.setStatus(ReservationStatus.EXPIRED);
            }

            // Se già CONSUMED, significa che qualcuno l’ha usata per unlock:
            // non può più essere cancellata.
            if (r.getStatus() == ReservationStatus.CONSUMED) {
                throw DomainException.of(DomainError.RESERVATION_ALREADY_CONSUMED);
            }

            // Se è ACTIVE -> CANCELED. Se è già EXPIRED/CANCELED: lasciamo com’è (operazione ripetuta = stesso esito).
            if (r.getStatus() == ReservationStatus.ACTIVE) {
                r.setStatus(ReservationStatus.CANCELED);
            }

            reservationStore.upsert(r);

            // Cleanup sul veicolo: se è ancora riservato da questa reservation, lo liberiamo.
            Vehicle v = requireVehicle(r.getVehicleId());
            if (v.getState() == VehicleState.DOCKED_RESERVED
                    && reservationId.equals(v.getActiveReservationId())) {

                // - clearReservation() azzera i campi legati alla prenotazione
                // - dockAt(...) ristabilisce uno stato DOCKED_AVAILABLE coerente
                v.clearReservation();

                // In condizioni normali v.getCurrentStationId() è già stationId.
                // Se per qualche motivo fosse nullo/vuoto, usiamo lo stationId della request come fallback.
                String dockStation = firstNonBlank(v.getCurrentStationId(), stationId);
                v.dockAt(dockStation);

                vehicleStore.upsert(v);
            }

            return r;
        });
    }

    @Override
    public UnlockResult unlock(String stationId,
                               String vehicleId,
                               String rentalId,
                               String reservationId,                               
                               String userId,
                               String destinationStationId) {

        // Verifica stazione (fail-fast).
        requireStation(stationId);

        //  Validazione Destinazione
        Station destStation = stationStore.findById(destinationStationId)
                .orElseThrow(() -> DomainException.of(DomainError.STATION_NOT_FOUND, "Destination station not found"));

        // rentalId è obbligatorio: serve a legare l’operazione al noleggio specifico.
        if (isBlank(rentalId)) {
            throw DomainException.of(DomainError.RENTAL_MISMATCH, "rentalId is required");
        }

        // userId obbligatorio: l'API lo richiede e serve per autorizzazione/coerenza.
        if (isBlank(userId)) {
            throw DomainException.of(DomainError.RENTAL_MISMATCH, "userId is required");
        }

        // Serializzazione per veicolo: unlock compete con reserve/cancel/lock.
        return lockManager.withVehicleLock(vehicleId, () -> {
            Vehicle v = requireVehicle(vehicleId);

            // Gestione chiamate ripetute / retry:
            // - se il veicolo è già IN_USE con lo stesso rentalId, consideriamo l’operazione già "effettuata"
            // - se è IN_USE ma con rentalId diverso, è conflitto (altro noleggio)
            if (v.getState() == VehicleState.IN_USE) {
                if (rentalId.equals(v.getActiveRentalId())) {
                    return new UnlockResult(v, null);
                }
                throw DomainException.of(DomainError.VEHICLE_IN_USE_BY_OTHER_RENTAL);
            }

            // Coerenza fisica: per unlock deve essere docked nella station indicata.
            if (!stationId.equals(v.getCurrentStationId())) {
                throw DomainException.of(DomainError.VEHICLE_NOT_AT_STATION);
            }

            String consumedReservationId = null;

            // Caso A: unlock con reservation (booking flow).
            if (!isBlank(reservationId)) {
                Reservation r = requireReservation(reservationId);

                // La reservation deve "matchare" station e vehicle.
                if (!stationId.equals(r.getStationId()) || !vehicleId.equals(r.getVehicleId())) {
                    throw DomainException.of(DomainError.RESERVATION_MISMATCH);
                }

                Instant now = Instant.now();

                // Se ACTIVE ma scaduta -> la marchiamo EXPIRED (persistiamo).
                if (r.getStatus() == ReservationStatus.ACTIVE && r.isExpired(now)) {
                    r.setStatus(ReservationStatus.EXPIRED);
                    reservationStore.upsert(r);
                }

                // Per poter unlockare, la reservation deve essere ACTIVE.
                // Se è CONSUMED: qualcuno l’ha già usata (non riutilizzabile).
                // Altrimenti (CANCELED/EXPIRED): mismatch.
                if (r.getStatus() != ReservationStatus.ACTIVE) {
                    if (r.getStatus() == ReservationStatus.CONSUMED) {
                        throw DomainException.of(DomainError.RESERVATION_ALREADY_CONSUMED);
                    }
                    throw DomainException.of(DomainError.RESERVATION_MISMATCH);
                }

                // Autorizzazione: deve essere l'owner della reservation.
                if (!userId.equals(r.getUserId())) {
                    throw DomainException.of(DomainError.NOT_AUTHORIZED);
                }

                // Stato veicolo: deve risultare RESERVED (non già disponibile/in uso).
                if (v.getState() != VehicleState.DOCKED_RESERVED) {
                    throw DomainException.of(DomainError.VEHICLE_NOT_AVAILABLE);
                }

                // La reservation attiva sul veicolo deve essere proprio questa.
                if (!reservationId.equals(v.getActiveReservationId())) {
                    throw DomainException.of(DomainError.RESERVATION_MISMATCH);
                }

                // Consumo reservation: da ACTIVE -> CONSUMED.
                // Da questo momento non è più cancellabile e non può essere riutilizzata.
                r.setStatus(ReservationStatus.CONSUMED);
                reservationStore.upsert(r);
                consumedReservationId = reservationId;

                // Pulizia attributi reservation sul veicolo: il veicolo passa a IN_USE.
                // Scelta "pulizia mentale": clearReservation() invece di settare singoli campi.
                v.clearReservation();

            } else {
                // Caso B: unlock senza reservation (immediate rent).
                // Se è rimasto DOCKED_RESERVED per una reservation scaduta/cancellata, lo liberiamo.
                releaseStaleReservationIfAny(v, stationId, Instant.now());

                // Dopo normalizzazione deve essere disponibile.
                if (v.getState() != VehicleState.DOCKED_AVAILABLE) {
                    throw DomainException.of(DomainError.VEHICLE_NOT_AVAILABLE);
                }
            }

            // Transizione a IN_USE: il veicolo "lascia" la station.
            // Scelta "pulizia mentale": startRental(...) fa in un colpo
            // state=IN_USE, currentStationId=null, activeRentalId=rentalId.
            v.startRental(rentalId);
            vehicleStore.upsert(v);

            // 2. COMUNICAZIONE AL SIMULATORE
            // Inviamo il comando fisico al simulatore: "Sbloccati e vai alle coordinate X,Y"
            commandDispatcher.sendUnlockCommand(
                    vehicleId, 
                    destStation.getLat(), 
                    destStation.getLon()
            );

            return new UnlockResult(v, consumedReservationId);
        });
    }

    @Override
    public LockResult lock(String stationId, String vehicleId, String rentalId) {
        // Verifica esistenza station (coerenza e validazione dominio).
        requireStation(stationId);

        if (isBlank(rentalId)) {
            throw DomainException.of(DomainError.RENTAL_MISMATCH, "rentalId is required");
        }

        // Serializzazione per veicolo: evita race tra lock e altre operazioni.
        return lockManager.withVehicleLock(vehicleId, () -> {
            Vehicle v = requireVehicle(vehicleId);

            // Gestione chiamate ripetute / retry:
            // se è già docked disponibile nella stessa station, consideriamo lock ripetuta -> OK.
            if (v.getState() == VehicleState.DOCKED_AVAILABLE) {
                if (stationId.equals(v.getCurrentStationId())) {
                    return new LockResult(v, rentalId);
                }
                // Se è docked altrove, non è una retry "legittima": è un conflitto.
                throw DomainException.of(DomainError.VEHICLE_ALREADY_DOCKED_ELSEWHERE);
            }

            // Se è RESERVED, non è in uso: non puoi fare lock di un veicolo prenotato.
            if (v.getState() == VehicleState.DOCKED_RESERVED) {
                throw DomainException.of(DomainError.VEHICLE_NOT_IN_USE);
            }

            // Per lock "vero" deve essere IN_USE.
            if (v.getState() != VehicleState.IN_USE) {
                throw DomainException.of(DomainError.VEHICLE_NOT_IN_USE);
            }

            // Deve matchare il rental attivo, altrimenti un client sta chiudendo un noleggio diverso.
            if (v.getActiveRentalId() == null || !rentalId.equals(v.getActiveRentalId())) {
                throw DomainException.of(DomainError.RENTAL_MISMATCH);
            }

            // Transizione a docked disponibile nella station + pulizia rental.
            // Scelta "pulizia mentale": endRentalAndDock(...) evita setter sparsi.
            v.endRentalAndDock(stationId);
            vehicleStore.upsert(v);

            // 3. COMUNICAZIONE AL SIMULATORE
             // Inviamo il comando fisico: "Bloccati qui"
            commandDispatcher.sendLockCommand(vehicleId);

            return new LockResult(v, rentalId);
        });
    }

    /**
     * Normalizzazione dello stato veicolo in caso di "reservation stale".
     *
     * Se il veicolo è DOCKED_RESERVED ma la reservation associata è:
     * - non trovata
     * - EXPIRED
     * - CANCELED
     * - ACTIVE ma scaduta (=> la marchiamo EXPIRED)
     *
     * allora liberiamo il veicolo a DOCKED_AVAILABLE.
     *
     * Questo evita che un veicolo resti bloccato in RESERVED per sempre quando nessuno
     * esegue un job di cleanup periodico.
     */
    private void releaseStaleReservationIfAny(Vehicle v, String fallbackStationId, Instant now) {
        if (v.getState() != VehicleState.DOCKED_RESERVED) return;

        String rid = v.getActiveReservationId();
        if (isBlank(rid)) {
            // Stato incoerente: RESERVED senza reservationId.
            // Scelta: riparazione automatica -> torna AVAILABLE.
            makeVehicleDockedAvailable(v, fallbackStationId);
            return;
        }

        Optional<Reservation> opt = reservationStore.findById(rid);
        if (opt.isEmpty()) {
            // Incoerenza: veicolo punta a reservation inesistente.
            // Scelta: riparazione automatica -> torna AVAILABLE.
            makeVehicleDockedAvailable(v, fallbackStationId);
            return;
        }

        Reservation r = opt.get();

        // Se ACTIVE ma scaduta -> EXPIRED (persistiamo).
        if (r.getStatus() == ReservationStatus.ACTIVE && r.isExpired(now)) {
            r.setStatus(ReservationStatus.EXPIRED);
            reservationStore.upsert(r);
        }

        // Se non è più valida (cancellata o scaduta), liberiamo il veicolo.
        if (r.getStatus() == ReservationStatus.CANCELED || r.getStatus() == ReservationStatus.EXPIRED) {
            makeVehicleDockedAvailable(v, fallbackStationId);
        }
    }

    /**
     * Ripristina un Vehicle in uno stato DOCKED_AVAILABLE coerente:
     * - azzera la parte di reservation (clearReservation)
     * - garantisce "docked at station" con dockAt(...)
     *
     * Nota: preferiamo questo helper ai setter sparsi perché riduce il rischio di lasciare campi incoerenti.
     */
    private void makeVehicleDockedAvailable(Vehicle v, String fallbackStationId) {
        v.clearReservation();

        // Normalmente currentStationId esiste (docked). Se manca, usiamo il fallback del contesto chiamante.
        String dockStation = firstNonBlank(v.getCurrentStationId(), fallbackStationId);
        v.dockAt(dockStation);

        vehicleStore.upsert(v);
    }

    @Override
    public List<Station> listStations() {
        return stationStore.findAll();
    }

    @Override
    public List<Vehicle> listVehicles() {
        return vehicleStore.findAll();
    }

    @Override
    public List<Vehicle> listVehiclesAtStation(String stationId) {
        requireStation(stationId); // se non esiste -> DomainException(STATION_NOT_FOUND)

        return vehicleStore.findAll().stream()
                .filter(v -> stationId.equals(v.getCurrentStationId()))
                .collect(Collectors.toList());
    }


    // ---------- require* helpers: traduzione "not found" in DomainException ----------

    private Station requireStation(String stationId) {
        return stationStore.findById(stationId)
                .orElseThrow(() -> DomainException.of(DomainError.STATION_NOT_FOUND));
    }

    private Vehicle requireVehicle(String vehicleId) {
        return vehicleStore.findById(vehicleId)
                .orElseThrow(() -> DomainException.of(DomainError.VEHICLE_NOT_FOUND));
    }

    private Reservation requireReservation(String reservationId) {
        return reservationStore.findById(reservationId)
                .orElseThrow(() -> DomainException.of(DomainError.RESERVATION_NOT_FOUND));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        if (!isBlank(a)) return a.trim();
        if (!isBlank(b)) return b.trim();
        // Qui preferiamo fallire forte: serve una stationId per poter "dockare" in modo coerente.
        throw new IllegalStateException("Cannot determine stationId to dock the vehicle");
    }
}
