package it.unibo.acme.fleet.battery.resource;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import it.unibo.acme.fleet.battery.capability.BatteryCapability;
import it.unibo.acme.fleet.battery.model.CommandResponse;
import it.unibo.acme.fleet.battery.model.StartBatteryCommand;
import it.unibo.acme.fleet.battery.model.StopBatteryCommand;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class BatteryCommandResource {

    private static final Logger LOG = Logger.getLogger(BatteryCommandResource.class.getName());

    private final Connection nats;
    private final Jsonb jsonb;
    private final BatteryCapability capability;
    private final String startSubject;
    private final String stopSubject;

    @Inject
    public BatteryCommandResource(Connection nats,
                                 Jsonb jsonb,
                                 BatteryCapability capability,
                                 @ConfigProperty(name = "battery.cmd.start.subject", defaultValue = "cmd.battery.start") String startSubject,
                                 @ConfigProperty(name = "battery.cmd.stop.subject", defaultValue = "cmd.battery.stop") String stopSubject) {
        this.nats = nats;
        this.jsonb = jsonb;
        this.capability = capability;
        this.startSubject = startSubject;
        this.stopSubject = stopSubject;
    }

    @PostConstruct
    public void start() {
        Dispatcher dispatcher = nats.createDispatcher(msg -> {
            try {
                String subject = msg.getSubject();
                String json = new String(msg.getData(), StandardCharsets.UTF_8);

                CommandResponse response;
                if (startSubject.equals(subject)) {
                    StartBatteryCommand cmd = jsonb.fromJson(json, StartBatteryCommand.class);
                    if (cmd == null || cmd.vehicleId == null) {
                        response = CommandResponse.error(null, "vehicleId mancante");
                    } else {
                        capability.start(cmd.vehicleId, cmd.ts);
                        response = CommandResponse.ok(cmd.vehicleId, "battery monitoring started");
                    }
                } else if (stopSubject.equals(subject)) {
                    StopBatteryCommand cmd = jsonb.fromJson(json, StopBatteryCommand.class);
                    if (cmd == null || cmd.vehicleId == null) {
                        response = CommandResponse.error(null, "vehicleId mancante");
                    } else {
                        capability.stop(cmd.vehicleId, cmd.ts);
                        response = CommandResponse.ok(cmd.vehicleId, "battery monitoring stopped");
                    }
                } else {
                    response = CommandResponse.error(null, "subject non gestito: " + subject);
                }

                if (msg.getReplyTo() != null && !msg.getReplyTo().isBlank()) {
                    byte[] payload = jsonb.toJson(response).getBytes(StandardCharsets.UTF_8);
                    nats.publish(msg.getReplyTo(), payload);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Command handling failed", e);
                if (msg.getReplyTo() != null && !msg.getReplyTo().isBlank()) {
                    try {
                        byte[] payload = jsonb.toJson(CommandResponse.error(null, "internal error"))
                                .getBytes(StandardCharsets.UTF_8);
                        nats.publish(msg.getReplyTo(), payload);
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
            }
        });

        dispatcher.subscribe(startSubject);
        dispatcher.subscribe(stopSubject);

        LOG.info(() -> "Subscribed to command subjects: " + startSubject + " , " + stopSubject);
    }
}
