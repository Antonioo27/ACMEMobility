package it.unibo.acme.fleet.gateway.subscriber;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import it.unibo.acme.fleet.gateway.capability.FleetGatewayCapability;
import it.unibo.acme.fleet.gateway.model.BatterySnapshot;
import it.unibo.acme.fleet.gateway.model.TrackingSnapshot;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * NATS inbound "resource"/subscriber.
 *
 * It listens to snapshot events produced by internal microservices:
 * - event.tracking.snapshot.<vehicleId>
 * - event.battery.snapshot.<vehicleId>
 *
 * and pushes them into the Gateway capability (which updates the cache).
 */
@ApplicationScoped
public class SnapshotSubscriptionResource {

    private static final Logger LOG = Logger.getLogger(SnapshotSubscriptionResource.class.getName());

    private final Connection nats;
    private final Jsonb jsonb;
    private final FleetGatewayCapability capability;

    private final String trackingSnapshotWildcard;
    private final String batterySnapshotWildcard;

    private Dispatcher trackingDispatcher;
    private Dispatcher batteryDispatcher;

    @Inject
    public SnapshotSubscriptionResource(Connection nats,
                                        Jsonb jsonb,
                                        FleetGatewayCapability capability,
                                        @ConfigProperty(name = "tracking.snapshot.subjectWildcard") String trackingSnapshotWildcard,
                                        @ConfigProperty(name = "battery.snapshot.subjectWildcard") String batterySnapshotWildcard) {
        this.nats = nats;
        this.jsonb = jsonb;
        this.capability = capability;
        this.trackingSnapshotWildcard = trackingSnapshotWildcard;
        this.batterySnapshotWildcard = batterySnapshotWildcard;
    }

    @PostConstruct
    void start() {
        // Tracking snapshots
        trackingDispatcher = nats.createDispatcher(msg -> {
            try {
                String json = new String(msg.getData(), StandardCharsets.UTF_8);
                TrackingSnapshot s = jsonb.fromJson(json, TrackingSnapshot.class);
                capability.onTrackingSnapshot(s);
            } catch (Exception e) {
                LOG.warning("Bad tracking snapshot payload on " + msg.getSubject() + " err=" + e.getMessage());
            }
        });
        trackingDispatcher.subscribe(trackingSnapshotWildcard);

        // Battery snapshots
        batteryDispatcher = nats.createDispatcher(msg -> {
            try {
                String json = new String(msg.getData(), StandardCharsets.UTF_8);
                BatterySnapshot s = jsonb.fromJson(json, BatterySnapshot.class);
                capability.onBatterySnapshot(s);
            } catch (Exception e) {
                LOG.warning("Bad battery snapshot payload on " + msg.getSubject() + " err=" + e.getMessage());
            }
        });
        batteryDispatcher.subscribe(batterySnapshotWildcard);

        LOG.info("Subscribed to snapshots: tracking=" + trackingSnapshotWildcard + ", battery=" + batterySnapshotWildcard);
    }

    @PreDestroy
    void stop() {
        try {
            if (trackingDispatcher != null) trackingDispatcher.unsubscribe(trackingSnapshotWildcard);
        } catch (Exception ignored) {}
        try {
            if (batteryDispatcher != null) batteryDispatcher.unsubscribe(batterySnapshotWildcard);
        } catch (Exception ignored) {}
    }
}
