package org.acmemobility.station.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class LivenessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        // liveness = “il processo è vivo e non in deadlock/crash”
        return HealthCheckResponse.named("station-liveness")
                .up()
                .build();
    }
}
