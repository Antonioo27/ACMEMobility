package it.unibo.acme.fleet.tracking.capability;

import it.unibo.acme.fleet.tracking.model.Position;
import it.unibo.acme.fleet.tracking.model.TelemetryMessage;
import it.unibo.acme.fleet.tracking.model.TrackingSnapshot;
import it.unibo.acme.fleet.tracking.util.Geo;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.helidon.service.registry.Service.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TrackingCapability {

    private final ConcurrentHashMap<String, VehicleTrackingState> stateByVehicle = new ConcurrentHashMap<>();

    private long snapshotIntervalMs;
    private double publishDistanceThresholdM;
    private boolean publishStopped;


    public TrackingCapability() {
        // CDI proxy
    }

    @Inject
    public TrackingCapability(
            @ConfigProperty(name = "tracking.snapshot.intervalMs", defaultValue = "1000") long snapshotIntervalMs,
            @ConfigProperty(name = "tracking.snapshot.publishDistanceThresholdM", defaultValue = "10") double publishDistanceThresholdM,
            @ConfigProperty(name = "tracking.snapshot.publishStopped", defaultValue = "true") boolean publishStopped
    ) {
        this.snapshotIntervalMs = snapshotIntervalMs;
        this.publishDistanceThresholdM = publishDistanceThresholdM;
        this.publishStopped = publishStopped;
    }

    public void startTracking(String vehicleId, long ts) {
        VehicleTrackingState st = stateByVehicle.computeIfAbsent(vehicleId, v -> new VehicleTrackingState());
        synchronized (st) {
            st.active = true;
            st.startedAt = ts > 0 ? ts : System.currentTimeMillis();
            st.distanceMeters = 0.0;
            st.lastPos = null;
            st.lastUpdateTs = 0;
            st.pendingFinalPublish = false;
            st.lastPublishedTs = 0;
            st.lastPublishedPos = null;
        }
    }

    public void stopTracking(String vehicleId, long ts) {
        VehicleTrackingState st = stateByVehicle.computeIfAbsent(vehicleId, v -> new VehicleTrackingState());
        synchronized (st) {
            st.active = false;
            // Keep last known state; optionally publish a final snapshot once
            st.pendingFinalPublish = publishStopped;
            if (ts > 0) {
                st.lastUpdateTs = Math.max(st.lastUpdateTs, ts);
            }
        }
    }

    public void onTelemetry(TelemetryMessage telemetry) {
        if (telemetry == null || telemetry.vehicleId == null) {
            return;
        }
        VehicleTrackingState st = stateByVehicle.computeIfAbsent(telemetry.vehicleId, v -> new VehicleTrackingState());
        synchronized (st) {
            // Always store last seen, but only accumulate distance if active
            Position newPos = new Position(telemetry.lat, telemetry.lon);
            long ts = telemetry.ts > 0 ? telemetry.ts : System.currentTimeMillis();

            if (st.active && st.lastPos != null) {
                st.distanceMeters += Geo.distanceMeters(st.lastPos.lat, st.lastPos.lon, newPos.lat, newPos.lon);
            }
            st.lastPos = newPos;
            st.lastUpdateTs = ts;
        }
    }

    /**
     * Returns snapshots that should be published now, applying:
     * - minimum publish interval (per vehicle)
     * - distance threshold (per vehicle)
     * - optional one-shot publish after stop (pendingFinalPublish)
     */
    public List<TrackingSnapshot> collectSnapshotsToPublish(long nowTs) {
        List<TrackingSnapshot> out = new ArrayList<>();
        for (var entry : stateByVehicle.entrySet()) {
            String vehicleId = entry.getKey();
            VehicleTrackingState st = entry.getValue();
            TrackingSnapshot snap = null;

            synchronized (st) {
                boolean shouldPublish = false;

                // Publish while active, on interval or if moved enough
                if (st.active) {
                    if (st.lastPublishedTs == 0 || nowTs - st.lastPublishedTs >= snapshotIntervalMs) {
                        shouldPublish = true;
                    } else if (st.lastPos != null && st.lastPublishedPos != null) {
                        double moved = Geo.distanceMeters(
                                st.lastPublishedPos.lat, st.lastPublishedPos.lon,
                                st.lastPos.lat, st.lastPos.lon
                        );
                        if (moved >= publishDistanceThresholdM) {
                            shouldPublish = true;
                        }
                    } else if (st.lastPos != null && st.lastPublishedPos == null) {
                        // First position ever
                        shouldPublish = true;
                    }
                } else if (st.pendingFinalPublish) {
                    // One-shot publish when stopping
                    shouldPublish = true;
                }

                if (!shouldPublish) {
                    continue;
                }

                snap = new TrackingSnapshot();
                snap.vehicleId = vehicleId;
                snap.ts = nowTs;
                snap.active = st.active;
                snap.distanceMeters = st.distanceMeters;
                snap.startedAt = st.startedAt;
                snap.lastUpdateTs = st.lastUpdateTs;
                snap.stale = st.lastUpdateTs > 0 && (nowTs - st.lastUpdateTs) > (3L * snapshotIntervalMs);

                if (st.lastPos != null) {
                    snap.lat = st.lastPos.lat;
                    snap.lon = st.lastPos.lon;
                }

                // Mark as published
                st.lastPublishedTs = nowTs;
                st.lastPublishedPos = st.lastPos != null ? new Position(st.lastPos.lat, st.lastPos.lon) : null;
                st.pendingFinalPublish = false;
            }

            if (snap != null) {
                out.add(snap);
            }
        }
        return out;
    }

    private static final class VehicleTrackingState {
        boolean active;
        long startedAt;
        Position lastPos;
        long lastUpdateTs;
        double distanceMeters;

        long lastPublishedTs;
        Position lastPublishedPos;

        boolean pendingFinalPublish;
    }
}
