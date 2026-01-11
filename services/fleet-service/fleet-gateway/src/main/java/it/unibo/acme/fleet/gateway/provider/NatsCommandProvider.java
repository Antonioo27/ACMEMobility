package it.unibo.acme.fleet.gateway.provider;

import io.nats.client.Connection;
import io.nats.client.Message;
import it.unibo.acme.fleet.gateway.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Provider: hides the NATS details from the business capability.
 *
 * This is the ONLY class that should know:
 * - subject names
 * - request-reply details
 * - timeouts, payload serialization
 */
@ApplicationScoped
public class NatsCommandProvider {

    private static final Logger LOG = Logger.getLogger(NatsCommandProvider.class.getName());

    private final Connection nats;
    private final Jsonb jsonb;

    private final String trackingStartSubject;
    private final String trackingStopSubject;
    private final String batteryStartSubject;
    private final String batteryStopSubject;

    private final long requestTimeoutMs;

    @Inject
    public NatsCommandProvider(Connection nats,
                              Jsonb jsonb,
                              @ConfigProperty(name = "tracking.cmd.start.subject") String trackingStartSubject,
                              @ConfigProperty(name = "tracking.cmd.stop.subject") String trackingStopSubject,
                              @ConfigProperty(name = "battery.cmd.start.subject") String batteryStartSubject,
                              @ConfigProperty(name = "battery.cmd.stop.subject") String batteryStopSubject,
                              @ConfigProperty(name = "gateway.cmd.timeoutMs", defaultValue = "2000") long requestTimeoutMs) {
        this.nats = nats;
        this.jsonb = jsonb;

        this.trackingStartSubject = trackingStartSubject;
        this.trackingStopSubject = trackingStopSubject;
        this.batteryStartSubject = batteryStartSubject;
        this.batteryStopSubject = batteryStopSubject;

        this.requestTimeoutMs = requestTimeoutMs;
    }

    public CommandResponse requestTrackingStart(StartTrackingCommand cmd) {
        return request(trackingStartSubject, cmd, cmd.vehicleId, "tracking.start");
    }

    public CommandResponse requestTrackingStop(StopTrackingCommand cmd) {
        return request(trackingStopSubject, cmd, cmd.vehicleId, "tracking.stop");
    }

    public CommandResponse requestBatteryStart(StartBatteryCommand cmd) {
        return request(batteryStartSubject, cmd, cmd.vehicleId, "battery.start");
    }

    public CommandResponse requestBatteryStop(StopBatteryCommand cmd) {
        return request(batteryStopSubject, cmd, cmd.vehicleId, "battery.stop");
    }

    private CommandResponse request(String subject, Object payload, String vehicleId, String op) {
        try {
            byte[] data = jsonb.toJson(payload).getBytes(StandardCharsets.UTF_8);
            Message msg = nats.request(subject, data, Duration.ofMillis(requestTimeoutMs));

            if (msg == null || msg.getData() == null) {
                return CommandResponse.error(vehicleId, "No response from " + op);
            }
            String json = new String(msg.getData(), StandardCharsets.UTF_8);
            return jsonb.fromJson(json, CommandResponse.class);

        } catch (Exception e) {
            LOG.warning("NATS request failed for " + op + " vehicle=" + vehicleId + " err=" + e.getMessage());
            return CommandResponse.error(vehicleId, "NATS request failed: " + e.getMessage());
        }
    }
}
