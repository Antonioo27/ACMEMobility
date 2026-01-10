package it.unibo.acme.fleet.simulator.sim;

import it.unibo.acme.fleet.simulator.model.Station;

import java.util.List;

public final class DefaultStations {
    private DefaultStations() {}

    /**
     * 8 stazioni "tipo" nell'area di Bologna (coordinate indicative).
     * Per il progetto servono solo coordinate plausibili.
     */
    public static List<Station> stations() {
        return List.of(
                new Station("S01", 44.4949, 11.3426),
                new Station("S02", 44.5070, 11.3510),
                new Station("S03", 44.4795, 11.3300),
                new Station("S04", 44.5005, 11.3170),
                new Station("S05", 44.5170, 11.3235),
                new Station("S06", 44.4880, 11.3650),
                new Station("S07", 44.4700, 11.3500),
                new Station("S08", 44.5100, 11.3350)
        );
    }
}
