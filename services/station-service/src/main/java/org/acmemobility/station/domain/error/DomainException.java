package org.acmemobility.station.domain.error;

import java.util.Objects;

/**
 * Eccezione di DOMINIO: rappresenta un fallimento "business" e trasporta SEMPRE un DomainError.
 *
 * Scopo:
 * - separare gli errori di dominio (regole violate, risorsa non trovata, mismatch, ecc.)
 *   dagli errori tecnici (NullPointer, bug, IO, ecc.)
 * - permettere all'API layer (ExceptionMapper) di trasformare in modo consistente:
 *   DomainError  -> HTTP status + ErrorResponse JSON
 *
 * In pratica:
 * - lo strato domain/service (StationServiceImpl) lancia DomainException.of(DomainError.X)
 * - lo strato api/exception (DomainExceptionMapper) cattura DomainException e la mappa su Response
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Codice errore "canonico" del dominio.
     * È obbligatorio: chi riceve la DomainException può sempre decidere come mapparla in HTTP.
     */
    private final DomainError error;

    /**
     * Costruttore privato: costringe a usare i factory methods.
     *
     * Vantaggi:
     * - call-site più leggibili: DomainException.of(DomainError.VEHICLE_NOT_FOUND)
     * - messaggio e causa gestiti in modo uniforme
     */
    private DomainException(DomainError error, String message, Throwable cause) {
        // Se message è nullo/vuoto, usiamo come fallback il name() dell'errore.
        // Questo assicura che l'eccezione abbia sempre un messaggio utile per log/debug.
        super(messageOrDefault(error, message), cause);

        // L'errore NON può essere null: altrimenti l'API layer non può mappare correttamente.
        this.error = Objects.requireNonNull(error, "error must not be null");
    }

    public DomainError getError() {
        return error;
    }

    // ----------------- Factory methods -----------------

    /**
     * Caso standard: errore di dominio senza dettagli extra.
     * Il messaggio di RuntimeException sarà error.name().
     */
    public static DomainException of(DomainError error) {
        return new DomainException(error, null, null);
    }

    /**
     * Caso: vuoi un messaggio più informativo (log/debug) oltre al codice errore.
     * L'API comunque userà error (non il testo) per decidere lo status e il body.
     */
    public static DomainException of(DomainError error, String message) {
        return new DomainException(error, message, null);
    }

    /**
     * Caso: vuoi arricchire con message e causa (es. conversioni, check più profondi, ecc.).
     * Nota: per errori tecnici puri spesso è meglio NON usare DomainException,
     * ma qui resta disponibile.
     */
    public static DomainException of(DomainError error, String message, Throwable cause) {
        return new DomainException(error, message, cause);
    }

    /**
     * Caso: vuoi solo agganciare la causa mantenendo il codice errore.
     */
    public static DomainException of(DomainError error, Throwable cause) {
        return new DomainException(error, null, cause);
    }

    /**
     * Normalizza il messaggio dell'eccezione:
     * - se l'utente passa un messaggio non vuoto, lo usiamo
     * - altrimenti fallback su error.name()
     */
    private static String messageOrDefault(DomainError error, String message) {
        if (message != null && !message.isBlank()) return message;

        // fallback: se non passi message, il messaggio diventa il codice errore.
        // requireNonNull qui evita NPE "strane" e fa fallire subito in modo chiaro se error è null.
        return Objects.requireNonNull(error, "error must not be null").name();
    }
}
