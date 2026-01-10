package it.unibo.acme.fleet.tracking.resource;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import it.unibo.acme.fleet.tracking.capability.TrackingCapability;
import it.unibo.acme.fleet.tracking.model.CommandResponse;
import it.unibo.acme.fleet.tracking.model.StartTrackingCommand;
import it.unibo.acme.fleet.tracking.model.StopTrackingCommand;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class TrackingCommandResource {

    private static final Logger LOG = Logger.getLogger(TrackingCommandResource.class.getName());

    private final Connection nats;
    private final Jsonb jsonb;
    private final TrackingCapability capability;
    private final String startSubject;
    private final String stopSubject;

    @Inject
    public TrackingCommandResource(Connection nats,
                                  Jsonb jsonb,
                                  TrackingCapability capability,
                                  @ConfigProperty(name = "tracking.cmd.start.subject", defaultValue = "cmd.tracking.start") String startSubject,
                                  @ConfigProperty(name = "tracking.cmd.stop.subject", defaultValue = "cmd.tracking.stop") String stopSubject) {
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
                    StartTrackingCommand cmd = jsonb.fromJson(json, StartTrackingCommand.class);
                    if (cmd == null || cmd.vehicleId == null) {
                        response = CommandResponse.error(null, "vehicleId mancante");
                    } else {
                        capability.startTracking(cmd.vehicleId, cmd.ts);
                        response = CommandResponse.ok(cmd.vehicleId, "tracking started");
                    }
                } else if (stopSubject.equals(subject)) {
                    StopTrackingCommand cmd = jsonb.fromJson(json, StopTrackingCommand.class);
                    if (cmd == null || cmd.vehicleId == null) {
                        response = CommandResponse.error(null, "vehicleId mancante");
                    } else {
                        capability.stopTracking(cmd.vehicleId, cmd.ts);
                        response = CommandResponse.ok(cmd.vehicleId, "tracking stopped");
                    }
                } else {
                    response = CommandResponse.error(null, "subject non gestito: " + subject);
                }

                // Request-reply: replyTo may be null (fire-and-forget)
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
