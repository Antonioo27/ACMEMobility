package it.unibo.acme.fleet.simulator.stream;

import io.nats.client.Connection;
import it.unibo.acme.fleet.simulator.SimulatorConfig;
import it.unibo.acme.fleet.simulator.model.Station;
import it.unibo.acme.fleet.simulator.model.TelemetryMessage;
import it.unibo.acme.fleet.simulator.sim.DefaultStations;
import it.unibo.acme.fleet.simulator.sim.VehicleState;
import it.unibo.acme.fleet.simulator.util.Geo;

import jakarta.json.bind.Jsonb;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TelemetryStreamer {
    private static final Logger LOG = Logger.getLogger(TelemetryStreamer.class.getName());

    private final SimulatorConfig cfg;
    private final Connection nats;
    private final Jsonb jsonb;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "vehicle-telemetry-streamer");
        t.setDaemon(true);
        return t;
    });

    private final List<Station> stations;
    private final List<VehicleState> vehicles;
    private final Random rnd;

    public TelemetryStreamer(SimulatorConfig cfg, Connection nats, Jsonb jsonb) {
        this.cfg = cfg;
        this.nats = nats;
        this.jsonb = jsonb;
        this.rnd = new Random(cfg.randomSeed);

        this.stations = DefaultStations.stations();
        this.vehicles = new ArrayList<>(cfg.numVehicles);

        initVehicles();
    }

    private void initVehicles() {
        long now = System.currentTimeMillis();
        for (int i = 1; i <= cfg.numVehicles; i++) {
            String id = String.format("V%03d", i);
            VehicleState v = new VehicleState(id);

            v.batteryPct = 50 + rnd.nextInt(51); // 50..100

            Station a = stations.get(rnd.nextInt(stations.size()));
            Station b = pickDifferentStation(a);
            setNewRoute(v, a, b, now);

            v.lastLat = a.lat;
            v.lastLon = a.lon;

            vehicles.add(v);
        }
        LOG.info(() -> "Initialized vehicles=" + vehicles.size() + ", stations=" + stations.size());
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 0, cfg.tickMs, TimeUnit.MILLISECONDS);
        LOG.info(() -> "Telemetry streamer started: subjectPrefix=" + cfg.telemetrySubjectPrefix + ", tickMs=" + cfg.tickMs);
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (VehicleState v : vehicles) {
            if (now >= v.routeEndTs) {
                Station newFrom = v.to;
                Station newTo = pickDifferentStation(newFrom);
                setNewRoute(v, newFrom, newTo, now);
            }

            double t = (double) (now - v.routeStartTs) / (double) (v.routeEndTs - v.routeStartTs);
            if (t < 0) t = 0;
            if (t > 1) t = 1;

            double lat = Geo.lerp(v.from.lat, v.to.lat, t);
            double lon = Geo.lerp(v.from.lon, v.to.lon, t);

            double stepMeters = Geo.distanceMeters(v.lastLat, v.lastLon, lat, lon);
            drainBattery(v, stepMeters);

            v.lastLat = lat;
            v.lastLon = lon;

            publishTelemetry(v, now, lat, lon);
        }
    }

    private void publishTelemetry(VehicleState v, long ts, double lat, double lon) {
        TelemetryMessage msg = new TelemetryMessage();
        msg.vehicleId = v.vehicleId;
        msg.ts = ts;
        msg.lat = lat;
        msg.lon = lon;
        msg.batteryPct = (int) Math.round(v.batteryPct);

        String subject = cfg.telemetrySubjectPrefix + "." + v.vehicleId;
        byte[] payload = jsonb.toJson(msg).getBytes(StandardCharsets.UTF_8);
        nats.publish(subject, payload);
    }

    private void drainBattery(VehicleState v, double stepMeters) {
        if (cfg.batteryDrainPctPerKm <= 0) {
            return;
        }
        double km = stepMeters / 1000.0;
        double drain = cfg.batteryDrainPctPerKm * km;

        v.batteryPct -= drain;
        if (v.batteryPct <= 0) {
            // "ricarica" istantanea: semplifica la demo
            v.batteryPct = 100;
        }
        if (v.batteryPct > 100) v.batteryPct = 100;
    }

    private Station pickDifferentStation(Station current) {
        Station s;
        do {
            s = stations.get(rnd.nextInt(stations.size()));
        } while (s.id.equals(current.id));
        return s;
    }

    private void setNewRoute(VehicleState v, Station from, Station to, long now) {
        v.from = from;
        v.to = to;
        v.routeStartTs = now;

        double distMeters = Geo.distanceMeters(from.lat, from.lon, to.lat, to.lon);
        long durationMs = (long) Math.max(2000, (distMeters / cfg.speedMps) * 1000.0);
        v.routeEndTs = now + durationMs;
    }
}
