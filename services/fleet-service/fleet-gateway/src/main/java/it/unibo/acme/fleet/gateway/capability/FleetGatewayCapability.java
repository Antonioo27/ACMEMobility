package it.unibo.acme.fleet.gateway.capability;

import it.unibo.acme.fleet.gateway.cache.VehicleStatusCache;
import it.unibo.acme.fleet.gateway.model.*;
import it.unibo.acme.fleet.gateway.provider.NatsCommandProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;

/**
 * Business logic of the Fleet Gateway.
 *
 * Responsibilities:
 * - orchestrate START/STOP by talking to internal services (provider)
 * - keep/update the aggregated cache (called by NATS subscriber)
 * - serve data to REST resources
 *
 * It does NOT:
 * - deal with NATS protocol details (provider does that)
 * - expose endpoints (resource does that)
 */
@ApplicationScoped
public class FleetGatewayCapability {

    private final VehicleStatusCache cache = new VehicleStatusCache();

    private final NatsCommandProvider commands;

    @Inject
    public FleetGatewayCapability(NatsCommandProvider commands) {
        this.commands = commands;
    }

    // ---- cache update entrypoints (called by SnapshotSubscriptionResource) ----

    public void onTrackingSnapshot(TrackingSnapshot s) {
        cache.upsertTracking(s);
    }

    public void onBatterySnapshot(BatterySnapshot s) {
        cache.upsertBattery(s);
    }

    // ---- REST-facing operations (called by FleetGatewayResource) ----

    public StartStopResult start(String vehicleId, StartRequest req) {
        long ts = (req != null && req.ts > 0) ? req.ts : Instant.now().toEpochMilli();
        String stationId = (req != null) ? req.stationId : null;

        StartTrackingCommand tCmd = new StartTrackingCommand();
        tCmd.vehicleId = vehicleId;
        tCmd.ts = ts;
        tCmd.stationId = stationId;

        StartBatteryCommand bCmd = new StartBatteryCommand();
        bCmd.vehicleId = vehicleId;
        bCmd.ts = ts;
        bCmd.stationId = stationId;

        CommandResponse tRes = commands.requestTrackingStart(tCmd);
        CommandResponse bRes = commands.requestBatteryStart(bCmd);

        return merge(vehicleId, tRes, bRes, "START");
    }

    public StartStopResult stop(String vehicleId, StopRequest req) {
        long ts = (req != null && req.ts > 0) ? req.ts : Instant.now().toEpochMilli();
        String stationId = (req != null) ? req.stationId : null;

        StopTrackingCommand tCmd = new StopTrackingCommand();
        tCmd.vehicleId = vehicleId;
        tCmd.ts = ts;
        tCmd.stationId = stationId;

        StopBatteryCommand bCmd = new StopBatteryCommand();
        bCmd.vehicleId = vehicleId;
        bCmd.ts = ts;
        bCmd.stationId = stationId;

        CommandResponse tRes = commands.requestTrackingStop(tCmd);
        CommandResponse bRes = commands.requestBatteryStop(bCmd);

        return merge(vehicleId, tRes, bRes, "STOP");
    }

    public VehicleStatus getStatus(String vehicleId) {
        return cache.get(vehicleId);
    }

    public List<VehicleStatus> listAll() {
        return cache.listAll();
    }

    // ---- helpers ----

    private StartStopResult merge(String vehicleId, CommandResponse tRes, CommandResponse bRes, String op) {
        StartStopResult out = new StartStopResult();
        out.vehicleId = vehicleId;
        out.ts = Instant.now().toEpochMilli();
        out.tracking = tRes;
        out.battery = bRes;

        boolean ok = "OK".equalsIgnoreCase(tRes.status) && "OK".equalsIgnoreCase(bRes.status);
        out.status = ok ? "OK" : "ERROR";
        out.message = ok
                ? op + " completed"
                : op + " partially failed (check per-service responses)";
        return out;
    }
}
