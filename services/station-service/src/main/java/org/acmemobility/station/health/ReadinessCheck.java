package org.acmemobility.station.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.acmemobility.station.persistence.store.StationStore;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import jakarta.inject.Inject;

@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {

    private final StationStore stationStore;

    @Inject
    public ReadinessCheck(StationStore stationStore) {
        this.stationStore = stationStore;
    }

    @Override
    public HealthCheckResponse call() {
        // readiness = “posso servire richieste?”
        // Per ora: se lo store è iniettato, siamo pronti.
        // (Quando colleghi DB/Fleet, qui fai check connessione / init data, ecc.)
        boolean ok = stationStore != null;

        return HealthCheckResponse.named("station-readiness")
                .status(ok)
                .withData("storage", "inmemory")
                .build();
    }
}
