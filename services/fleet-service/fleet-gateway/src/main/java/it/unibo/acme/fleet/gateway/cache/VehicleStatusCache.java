package it.unibo.acme.fleet.gateway.cache;

import it.unibo.acme.fleet.gateway.model.BatterySnapshot;
import it.unibo.acme.fleet.gateway.model.TrackingSnapshot;
import it.unibo.acme.fleet.gateway.model.VehicleStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache updated by NATS snapshot subscriptions.
 *
 * The Gateway answers REST GETs straight from this cache.
 * It's intentionally "dumb": no persistence, no DB, no magic.
 *
 * (When you'll containerize, each gateway instance will have its own cache,
 * so you'd typically run 1 replica or add a shared store. For your project it's fine.)
 */
public class VehicleStatusCache {

    private final ConcurrentHashMap<String, TrackingSnapshot> tracking = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BatterySnapshot> battery = new ConcurrentHashMap<>();

    public void upsertTracking(TrackingSnapshot s) {
        if (s != null && s.vehicleId != null) {
            tracking.put(s.vehicleId, s);
        }
    }

    public void upsertBattery(BatterySnapshot s) {
        if (s != null && s.vehicleId != null) {
            battery.put(s.vehicleId, s);
        }
    }

    public VehicleStatus get(String vehicleId) {
        VehicleStatus vs = new VehicleStatus();
        vs.vehicleId = vehicleId;
        vs.ts = Instant.now().toEpochMilli();
        vs.tracking = tracking.get(vehicleId);
        vs.battery = battery.get(vehicleId);

        boolean trackingActive = vs.tracking != null && vs.tracking.active;
        boolean batteryActive = vs.battery != null && vs.battery.active;
        vs.active = trackingActive || batteryActive;

        boolean trackingStale = vs.tracking == null || vs.tracking.stale;
        boolean batteryStale = vs.battery == null || vs.battery.stale;
        vs.stale = trackingStale || batteryStale;

        return vs;
    }

    public List<VehicleStatus> listAll() {
        // Union of keys so we don't lose vehicles that only have one snapshot type.
        var keys = new java.util.HashSet<String>();
        keys.addAll(tracking.keySet());
        keys.addAll(battery.keySet());

        List<VehicleStatus> out = new ArrayList<>();
        for (String k : keys) {
            out.add(get(k));
        }
        return out;
    }
}
