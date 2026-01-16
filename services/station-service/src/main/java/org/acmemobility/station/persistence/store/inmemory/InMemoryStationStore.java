package org.acmemobility.station.persistence.store.inmemory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acmemobility.station.domain.model.Station;
import org.acmemobility.station.persistence.store.StationStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

    @Inject
    public InMemoryStationStore() {
        // NIENTE IF, NIENTE CONFIG. CARICA E BASTA.
        System.out.println("!!! FORCING DATA SEED IN MEMORY STORE !!!");
        seedDemoData();
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

    @Override
    public List<Station> findAll() {
        // Ritorno deterministico: utile per test/contract (ordine stabile)
        return stations.values().stream()
                .sorted(Comparator.comparing(Station::getStationId))
                .collect(Collectors.toList());
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
        seedDemoData();
    }

    // ----------------- internal -----------------

    private void seedDemoData() {
        // Coordinate Bologna (allineate col simulatore)
        upsert(new Station("S01", 44.4949, 11.3426)); // Piazza Maggiore
        upsert(new Station("S02", 44.5070, 11.3510)); // Stazione Centrale
        upsert(new Station("S03", 44.4795, 11.3300)); // Stadio
        upsert(new Station("S04", 44.5005, 11.3170)); // Ospedale Maggiore
        upsert(new Station("S05", 44.5170, 11.3235)); // Fiera
        
        System.out.println("DEBUG: InMemoryStationStore seeded with stations S01-S05");
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
