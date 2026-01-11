package it.unibo.acme.fleet.gateway.bootstrap;

import it.unibo.acme.fleet.gateway.subscriber.SnapshotSubscriptionResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.logging.Logger;

/**
 * Forces initialization of "background" beans.
 *
 * CDI is allowed to be lazy: if nobody injects SnapshotSubscriptionResource,
 * it might never be created -> no NATS subscriptions -> empty cache.
 *
 * This observer runs at container startup and touches the bean once.
 */
@ApplicationScoped
public class GatewayBootstrap {

    private static final Logger LOG = Logger.getLogger(GatewayBootstrap.class.getName());

    @Inject
    jakarta.inject.Provider<SnapshotSubscriptionResource> subscriber;

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object init) {
        LOG.info("Forcing initialization of NATS snapshot subscriber...");
        subscriber.get().toString(); // triggers @PostConstruct => subscribe NATS
        LOG.info("GatewayBootstrap completed.");
    }
}
