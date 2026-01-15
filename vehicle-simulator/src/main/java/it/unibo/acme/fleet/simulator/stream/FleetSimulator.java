package it.unibo.acme.fleet.simulator.stream;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import it.unibo.acme.fleet.simulator.SimulatorConfig;
import it.unibo.acme.fleet.simulator.model.Station;
import it.unibo.acme.fleet.simulator.model.TelemetryMessage;
import it.unibo.acme.fleet.simulator.model.VehicleCommand;
import it.unibo.acme.fleet.simulator.sim.DefaultStations;
import it.unibo.acme.fleet.simulator.sim.VehicleState;
import it.unibo.acme.fleet.simulator.util.Geo;

import jakarta.json.bind.Jsonb;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FleetSimulator {
    private static final Logger LOG = Logger.getLogger(FleetSimulator.class.getName());

    private final SimulatorConfig cfg;
    private final Connection nats;
    private final Jsonb jsonb;

    // Mappa ID Veicolo -> Stato. 
    // Usiamo una mappa per accesso rapido quando arriva un comando.
    private final Map<String, VehicleState> vehicles = new HashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sim-loop");
        t.setDaemon(true);
        return t;
    });

    public FleetSimulator(SimulatorConfig cfg, Connection nats, Jsonb jsonb) {
        this.cfg = cfg;
        this.nats = nats;
        this.jsonb = jsonb;
        initVehicles();
    }

    private void initVehicles() {
        List<Station> stations = DefaultStations.stations();
        // Rimuoviamo la dipendenza dal Random per la posizione iniziale
        Random rnd = new Random(cfg.randomSeed); 

        for (int i = 1; i <= cfg.numVehicles; i++) {
            String id = String.format("V%03d", i);
            
            // LOGICA DETERMINISTICA (Stessa del Station Service)
            // Assumiamo che stations.get(0) sia S01, get(1) sia S02...
            // Usiamo il modulo per ciclare sulle stazioni disponibili
            int stationIndex = (i - 1) % stations.size();
            Station startStation = stations.get(stationIndex);
            
            VehicleState v = new VehicleState(id, startStation.lat, startStation.lon);
            
            // La batteria può rimanere random, non influisce sulla logica di business dell'unlock
            v.batteryPct = 50 + rnd.nextInt(51); 
            
            vehicles.put(id, v);
        }
        LOG.info("Initialized " + vehicles.size() + " vehicles at deterministic locations.");
    }

    public void start() {
        // 1. Sottoscrizione ai Comandi (Async)
        // Topic pattern: commands.vehicle.* (es. commands.vehicle.V001)
        Dispatcher d = nats.createDispatcher(this::handleCommand);
        d.subscribe("commands.vehicle.*");
        
        LOG.info("Listening for commands on: commands.vehicle.*");

        // 2. Avvio Loop di Simulazione
        scheduler.scheduleAtFixedRate(this::tick, 0, cfg.tickMs, TimeUnit.MILLISECONDS);
        LOG.info("Simulation loop started, tickMs=" + cfg.tickMs);
    }

    /**
     * Gestisce i messaggi NATS in arrivo dal Servizio Stazioni
     */
    private void handleCommand(Message msg) {
        String subject = msg.getSubject(); // es. "commands.vehicle.V001"
        String vehicleId = subject.substring(subject.lastIndexOf(".") + 1);

        String payload = new String(msg.getData(), StandardCharsets.UTF_8);
        
        // Sincronizziamo l'accesso perché questo metodo gira su thread NATS, 
        // mentre il tick gira sul thread dello scheduler.
        synchronized (vehicles) {
            VehicleState v = vehicles.get(vehicleId);
            if (v == null) {
                LOG.warning("Received command for unknown vehicle: " + vehicleId);
                return;
            }

            try {
                VehicleCommand cmd = jsonb.fromJson(payload, VehicleCommand.class);
                applyCommand(v, cmd);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error parsing command for " + vehicleId, e);
            }
        }
    }

    private void applyCommand(VehicleState v, VehicleCommand cmd) {
        if (cmd.type == VehicleCommand.Type.UNLOCK) {
            if (cmd.destLat != null && cmd.destLon != null) {
                v.isLocked = false;
                v.targetLat = cmd.destLat;
                v.targetLon = cmd.destLon;
                LOG.info("Vehicle " + v.vehicleId + " UNLOCKED -> Target: " + cmd.destLat + ", " + cmd.destLon);
            } else {
                LOG.warning("UNLOCK command without destination for " + v.vehicleId);
            }
        } else if (cmd.type == VehicleCommand.Type.LOCK) {
            v.isLocked = true;
            // Opzionale: se viene bloccato, smette di navigare anche se non è arrivato?
            // Per specifica: "lock alla riconsegna", quindi assumiamo sia fermo.
            v.targetLat = null;
            v.targetLon = null;
            LOG.info("Vehicle " + v.vehicleId + " LOCKED.");
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        
        // synchronized per evitare modifiche concorrenti (race condition con handleCommand)
        synchronized (vehicles) {
            for (VehicleState v : vehicles.values()) {
                updateVehiclePhysics(v);
                publishTelemetry(v, now);
            }
        }
    }

    private void updateVehiclePhysics(VehicleState v) {
        // Se è bloccato, non si muove e non consuma (o consumo trascurabile)
        if (v.isLocked) return;

        // Se è sbloccato ma non ha target, consuma solo batteria (Idle)
        if (v.targetLat == null || v.targetLon == null) {
            drainBattery(v, 0); // 0 km percorsi, ma logica drain potrebbe avere base cost
            return;
        }

        // Calcolo movimento lineare verso il target
        double distMeters = Geo.distanceMeters(v.curLat, v.curLon, v.targetLat, v.targetLon);
        
        // Quanta strada faccio in questo tick?
        // tickMs è in millisecondi -> secondi
        double stepMeters = cfg.speedMps * (cfg.tickMs / 1000.0);

        if (distMeters <= stepMeters) {
            // Arrivato!
            v.curLat = v.targetLat;
            v.curLon = v.targetLon;
            v.targetLat = null; // Stop navigazione
            v.targetLon = null;
            drainBattery(v, distMeters); // Consumo per l'ultimo pezzetto
            LOG.info("Vehicle " + v.vehicleId + " ARRIVED at destination.");
        } else {
            // Mi muovo verso il target
            // Interpolazione lineare basata sulla distanza
            double ratio = stepMeters / distMeters;
            
            v.curLat = Geo.lerp(v.curLat, v.targetLat, ratio);
            v.curLon = Geo.lerp(v.curLon, v.targetLon, ratio);
            
            drainBattery(v, stepMeters);
        }
    }

    private void drainBattery(VehicleState v, double metersTraveled) {
        if (cfg.batteryDrainPctPerKm <= 0) return;
        
        // Consumo da movimento
        double km = metersTraveled / 1000.0;
        double drain = cfg.batteryDrainPctPerKm * km;
        
        // Aggiungi un piccolo consumo base se è sbloccato (es. luci accese)? 
        // Per ora teniamo semplice: consumo solo se si muove.
        
        v.batteryPct -= drain;
        if (v.batteryPct < 0) v.batteryPct = 0;
    }

    private void publishTelemetry(VehicleState v, long ts) {
        TelemetryMessage msg = new TelemetryMessage();
        msg.vehicleId = v.vehicleId;
        msg.ts = ts;
        msg.lat = v.curLat;
        msg.lon = v.curLon;
        msg.batteryPct = (int) Math.round(v.batteryPct);

        // Subject: telemetry.vehicle.V001
        String subject = cfg.telemetrySubjectPrefix + "." + v.vehicleId;
        byte[] payload = jsonb.toJson(msg).getBytes(StandardCharsets.UTF_8);
        
        // Publish è thread-safe in NATS client
        nats.publish(subject, payload);
    }
}