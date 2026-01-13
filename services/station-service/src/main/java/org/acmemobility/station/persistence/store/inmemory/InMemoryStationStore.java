package org.acmemobility.station.persistence.store.inmemory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acmemobility.station.domain.model.Station;
import org.acmemobility.station.persistence.store.StationStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementazione in-memory di StationStore.
 *
 * Scopo:
 * - supportare sviluppo/test senza DB
 * - essere thread-safe a livello di accesso alla mappa (ConcurrentHashMap)
 *
 * Nota:
 * - Le Station sono oggetti piccoli e "quasi immutabili" (id), quindi qui non servono
 *   lock dedicati come per i veicoli.
 */
@ApplicationScoped
public class InMemoryStationStore implements StationStore {

    /**
     * stationId -> Station
     */
    private final ConcurrentHashMap<String, Station> stations = new ConcurrentHashMap<>();

    /**
     * Flag configurabile per caricare un set demo di stazioni all'avvio.
     * Serve per far girare il servizio e alcuni test senza dover seedare manualmente.
     */
    private final boolean seedDemo;

    @Inject
    public InMemoryStationStore(
            @ConfigProperty(name = "station.seed.demo", defaultValue = "false") boolean seedDemo
    ) {
        this.seedDemo = seedDemo;
        if (seedDemo) {
            seedDemoData();
        }
    }

    @Override
    public Optional<Station> findById(String stationId) {
        String id = normalize(stationId);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(stations.get(id));
    }

    @Override
    public void upsert(Station station) {
        if (station == null) {
            throw new IllegalArgumentException("station must not be null");
        }

        String id = normalize(station.getStationId());
        if (id == null) {
            throw new IllegalArgumentException("stationId must not be null/blank");
        }

        // Upsert: in-memory significa semplicemente "sovrascrivi" l'entry con quell'id.
        // Se esisteva già, viene aggiornata; se non esisteva, viene inserita.
        stations.put(id, station);
    }

    // ----------------- utilities (test/debug) -----------------

    public void clear() {
        stations.clear();
    }

    public int size() {
        return stations.size();
    }

    /**
     * Re-seed esplicito (utile nei test dopo clear()).
     * Se station.seed.demo=false, non fa nulla: evita seed non voluti.
     */
    public void reseedDemo() {
        if (!seedDemo) return;
        seedDemoData();
    }

    // ----------------- internal -----------------

    private void seedDemoData() {
        // Stazioni demo "standard" usate spesso nei test/esperimenti.
        upsert(new Station("S45"));
        upsert(new Station("S46"));
        upsert(new Station("S47"));
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
