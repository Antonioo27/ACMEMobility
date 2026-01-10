package it.unibo.acme.fleet.simulator;

import io.nats.client.Connection;
import io.nats.client.Nats;
import it.unibo.acme.fleet.simulator.stream.TelemetryStreamer;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import java.util.logging.Logger;

public class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
        SimulatorConfig cfg = SimulatorConfig.fromEnv();
        LOG.info(() -> "vehicle-simulator starting with config: " + cfg);

        try (Connection nats = Nats.connect(cfg.natsUrl)) {
            Jsonb jsonb = JsonbBuilder.create();

            TelemetryStreamer streamer = new TelemetryStreamer(cfg, nats, jsonb);
            streamer.start();

            Thread.currentThread().join();
        }
    }
}
