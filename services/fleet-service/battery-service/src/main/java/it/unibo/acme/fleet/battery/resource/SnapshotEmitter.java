package it.unibo.acme.fleet.battery.resource;

import it.unibo.acme.fleet.battery.capability.BatteryCapability;
import it.unibo.acme.fleet.battery.provider.BatterySnapshotPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@ApplicationScoped
public class SnapshotEmitter {

    private static final Logger LOG = Logger.getLogger(SnapshotEmitter.class.getName());

    private final BatteryCapability capability;
    private final BatterySnapshotPublisher publisher;
    private final long intervalMs;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "battery-snapshot-emitter");
        t.setDaemon(true);
        return t;
    });

    @Inject
    public SnapshotEmitter(BatteryCapability capability,
                           BatterySnapshotPublisher publisher,
                           @ConfigProperty(name = "battery.snapshot.intervalMs", defaultValue = "1000") long intervalMs) {
        this.capability = capability;
        this.publisher = publisher;
        this.intervalMs = intervalMs;
    }

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            var snaps = capability.collectSnapshotsToPublish(now);
            for (var s : java.util.List.copyOf(snaps)) {
                publisher.publish(s);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        LOG.info(() -> "Snapshot emitter started, intervalMs=" + intervalMs);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }
}
