package it.unibo.acme.fleet.battery.capability;

import it.unibo.acme.fleet.battery.model.BatterySnapshot;
import it.unibo.acme.fleet.battery.model.TelemetryMessage;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class BatteryCapability {

    private final ConcurrentHashMap<String, VehicleBatteryState> stateByVehicle = new ConcurrentHashMap<>();

    private final long snapshotIntervalMs;
    private final int publishDeltaPct;
    private final int lowThresholdPct;
    private final boolean publishStopped;

    public BatteryCapability(
            @ConfigProperty(name = "battery.snapshot.intervalMs", defaultValue = "1000") long snapshotIntervalMs,
            @ConfigProperty(name = "battery.snapshot.publishDeltaPct", defaultValue = "1") int publishDeltaPct,
            @ConfigProperty(name = "battery.low.thresholdPct", defaultValue = "15") int lowThresholdPct,
            @ConfigProperty(name = "battery.snapshot.publishStopped", defaultValue = "true") boolean publishStopped
    ) {
        this.snapshotIntervalMs = snapshotIntervalMs;
        this.publishDeltaPct = Math.max(0, publishDeltaPct);
        this.lowThresholdPct = lowThresholdPct;
        this.publishStopped = publishStopped;
    }

    public void start(String vehicleId, long ts) {
        VehicleBatteryState st = stateByVehicle.computeIfAbsent(vehicleId, v -> new VehicleBatteryState());
        synchronized (st) {
            st.active = true;
            st.startedAt = ts > 0 ? ts : System.currentTimeMillis();
            st.pendingFinalPublish = false;
            st.lastPublishedTs = 0;
            st.lastPublishedPct = null;
            st.changedSinceLastPublish = true; // force first publish (if pct already known)
        }
    }

    public void stop(String vehicleId, long ts) {
        VehicleBatteryState st = stateByVehicle.computeIfAbsent(vehicleId, v -> new VehicleBatteryState());
        synchronized (st) {
            st.active = false;
            st.pendingFinalPublish = publishStopped;
            if (ts > 0) {
                st.lastUpdateTs = Math.max(st.lastUpdateTs, ts);
            }
            st.changedSinceLastPublish = true; // allow final publish
        }
    }

    public void onTelemetry(TelemetryMessage telemetry) {
        if (telemetry == null || telemetry.vehicleId == null) {
            return;
        }
        VehicleBatteryState st = stateByVehicle.computeIfAbsent(telemetry.vehicleId, v -> new VehicleBatteryState());
        synchronized (st) {
            if (!st.active) {
                // business rule: ignore telemetry when rental not active
                return;
            }

            long ts = telemetry.ts > 0 ? telemetry.ts : System.currentTimeMillis();
            if (telemetry.batteryPct != null) {
                st.batteryPct = telemetry.batteryPct;
                st.lowBattery = telemetry.batteryPct < lowThresholdPct;
                st.lastUpdateTs = ts;
                st.changedSinceLastPublish = true;
            } else {
                // telemetry without battery does not update state
                st.lastUpdateTs = Math.max(st.lastUpdateTs, ts);
            }
        }
    }

    public List<BatterySnapshot> collectSnapshotsToPublish(long nowTs) {
        List<BatterySnapshot> out = new ArrayList<>();
        for (var entry : stateByVehicle.entrySet()) {
            String vehicleId = entry.getKey();
            VehicleBatteryState st = entry.getValue();
            BatterySnapshot snap;

            synchronized (st) {
                boolean shouldPublish = false;

                if (st.active) {
                    if (st.batteryPct != null) {
                        if (st.lastPublishedTs == 0 || nowTs - st.lastPublishedTs >= snapshotIntervalMs) {
                            shouldPublish = true;
                        } else if (st.changedSinceLastPublish && st.lastPublishedPct != null) {
                            int delta = Math.abs(st.batteryPct - st.lastPublishedPct);
                            if (delta >= publishDeltaPct) {
                                shouldPublish = true;
                            }
                        } else if (st.changedSinceLastPublish && st.lastPublishedPct == null) {
                            shouldPublish = true;
                        }
                    }
                } else if (st.pendingFinalPublish && st.batteryPct != null) {
                    // publish one final snapshot when stopping
                    shouldPublish = true;
                }

                if (!shouldPublish) {
                    continue;
                }

                snap = new BatterySnapshot();
                snap.vehicleId = vehicleId;
                snap.ts = nowTs;
                snap.active = st.active;
                snap.batteryPct = st.batteryPct;
                snap.lowBattery = st.lowBattery;
                snap.startedAt = st.startedAt;
                snap.lastUpdateTs = st.lastUpdateTs;
                snap.stale = st.lastUpdateTs > 0 && (nowTs - st.lastUpdateTs) > (3L * snapshotIntervalMs);

                st.lastPublishedTs = nowTs;
                st.lastPublishedPct = st.batteryPct;
                st.changedSinceLastPublish = false;
                st.pendingFinalPublish = false;
            }

            out.add(snap);
        }
        return out;
    }

    private static final class VehicleBatteryState {
        boolean active;
        long startedAt;

        Integer batteryPct;
        boolean lowBattery;
        long lastUpdateTs;

        long lastPublishedTs;
        Integer lastPublishedPct;
        boolean changedSinceLastPublish;

        boolean pendingFinalPublish;
    }
}
