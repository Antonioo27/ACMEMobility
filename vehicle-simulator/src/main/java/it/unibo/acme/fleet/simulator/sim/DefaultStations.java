package it.unibo.acme.fleet.simulator.sim;

import it.unibo.acme.fleet.simulator.model.Station;
import java.util.List;

public final class DefaultStations {
    private DefaultStations() {}

    /**
     * Stazioni allineate con il Station Service (Bologna).
     */
    public static List<Station> stations() {
        return List.of(
            // Piazza Maggiore
            new Station("S01", 44.4949, 11.3426),
            // Stazione Centrale
            new Station("S02", 44.5070, 11.3510),
            // Stadio
            new Station("S03", 44.4795, 11.3300),
            // Ospedale Maggiore
            new Station("S04", 44.5005, 11.3170),
            // Fiera
            new Station("S05", 44.5170, 11.3235)
        );
    }
}