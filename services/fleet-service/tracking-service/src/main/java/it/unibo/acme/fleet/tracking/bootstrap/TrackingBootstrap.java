package it.unibo.acme.fleet.tracking.bootstrap;

import it.unibo.acme.fleet.tracking.resource.SnapshotEmitter;
import it.unibo.acme.fleet.tracking.resource.TelemetrySubscriptionResource;
import it.unibo.acme.fleet.tracking.resource.TrackingCommandResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.logging.Logger;

@ApplicationScoped
public class TrackingBootstrap {
    private static final Logger LOG = Logger.getLogger(TrackingBootstrap.class.getName());

    @Inject Instance<TrackingCommandResource> commands;
    @Inject Instance<TelemetrySubscriptionResource> telemetry;
    @Inject Instance<SnapshotEmitter> emitter;

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object init) {
        // forza la creazione dei bean -> scatta @PostConstruct -> subscribe NATS + scheduler
        
        LOG.info("Forcing initialization of NATS resources...");
        
        commands.get().toString();
        telemetry.get().toString();
        emitter.get().toString();

        LOG.info("TrackingBootstrap completed: NATS resources initialized.");
    }
}
