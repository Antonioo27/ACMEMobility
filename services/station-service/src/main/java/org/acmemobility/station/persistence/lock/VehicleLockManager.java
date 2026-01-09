package org.acmemobility.station.persistence.lock;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Lock manager per serializzare operazioni concorrenti sullo STESSO veicolo.
 *
 * Scopo:
 * - garantire mutua esclusione a grana fine (per vehicleId) evitando race tra:
 *   reserve / cancelReservation / unlock / lock
 *
 * Perché serve:
 * - Lo store è in-memory e non hai transazioni DB.
 * - Anche con DB, spesso serve comunque una forma di serializzazione per evitare interleaving
 *   che portano a stati incoerenti (es. due unlock simultanei).
 *
 * Design:
 * - 1 ReentrantLock per vehicleId, tenuto in una ConcurrentHashMap.
 * - computeIfAbsent crea il lock una sola volta (thread-safe).
 * - best-effort cleanup per evitare crescita infinita della mappa.
 */
@ApplicationScoped
public class VehicleLockManager {

    /**
     * Mappa (vehicleId -> lock).
     * ConcurrentHashMap permette accesso concorrente mentre garantisce correttezza.
     */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Esegue un'azione "protetta" dal lock associato a vehicleId.
     *
     * Proprietà:
     * - se due thread chiamano withVehicleLock sullo stesso vehicleId,
     *   il secondo aspetta il primo (serializzazione).
     * - su vehicleId diversi, possono procedere in parallelo (scalabilità).
     *
     * @param vehicleId identificativo del veicolo (obbligatorio, non blank)
     * @param action logica da eseguire in sezione critica
     * @return valore ritornato da action
     */
    public <T> T withVehicleLock(String vehicleId, Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");

        // Normalizzazione: evita che " v1 " e "v1" diventino due lock diversi.
        String id = normalize(vehicleId);

        // Ottieni o crea il lock associato al veicolo.
        // computeIfAbsent è atomico: se due thread arrivano insieme, uno solo crea il lock.
        ReentrantLock lock = locks.computeIfAbsent(id, k -> new ReentrantLock());

        // Entra in sezione critica (bloccante).
        lock.lock();
        try {
            // Esecuzione dell'azione dentro il lock.
            return action.get();
        } finally {
            // Garantiamo unlock anche se action lancia eccezioni.
            try {
                lock.unlock();
            } finally {
                // Best-effort cleanup:
                // - se non è locked e non c'è nessuno in coda, proviamo a rimuoverlo dalla map
                // - remove(id, lock) rimuove SOLO se la value attuale è proprio quell'istanza,
                //   evitando di cancellare un lock "nuovo" messo da un altro thread per qualche motivo.
                //
                // Obiettivo: non far crescere la mappa all'infinito dopo tanti veicoli "toccati una volta".
                if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                    locks.remove(id, lock);
                }
            }
        }
    }

    /**
     * Validazione e normalizzazione dell'id del veicolo.
     * Se è null/blank è un bug chiamare il lock manager (il dominio deve avere vehicleId valido).
     */
    private static String normalize(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("vehicleId must not be null/blank");
        }
        return s.trim();
    }
}
