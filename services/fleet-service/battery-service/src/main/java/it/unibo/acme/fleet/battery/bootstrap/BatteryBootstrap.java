package it.unibo.acme.fleet.battery.bootstrap;

import it.unibo.acme.fleet.battery.resource.BatteryCommandResource;
import it.unibo.acme.fleet.battery.resource.TelemetrySubscriptionResource;
import it.unibo.acme.fleet.battery.resource.SnapshotEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.logging.Logger;

@ApplicationScoped
public class BatteryBootstrap {
    private static final Logger LOG = Logger.getLogger(BatteryBootstrap.class.getName());

    @Inject Instance<BatteryCommandResource> commands;
    @Inject Instance<TelemetrySubscriptionResource> telemetry;
    @Inject Instance<SnapshotEmitter> emitter;

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object init) {
        
        LOG.info("Forcing initialization of NATS resources...");
        
        commands.get().toString();
        telemetry.get().toString();
        emitter.get().toString();

        LOG.info("BatteryBootstrap completed: NATS resources initialized.");
    }
}
