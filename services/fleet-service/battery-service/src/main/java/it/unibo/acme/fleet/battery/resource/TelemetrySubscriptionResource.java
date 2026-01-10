package it.unibo.acme.fleet.battery.resource;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import it.unibo.acme.fleet.battery.capability.BatteryCapability;
import it.unibo.acme.fleet.battery.model.TelemetryMessage;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class TelemetrySubscriptionResource {

    private static final Logger LOG = Logger.getLogger(TelemetrySubscriptionResource.class.getName());

    private final Connection nats;
    private final Jsonb jsonb;
    private final BatteryCapability capability;
    private final String telemetrySubject;

    @Inject
    public TelemetrySubscriptionResource(Connection nats,
                                         Jsonb jsonb,
                                         BatteryCapability capability,
                                         @ConfigProperty(name = "battery.telemetry.subject", defaultValue = "telemetry.vehicle.*")
                                         String telemetrySubject) {
        this.nats = nats;
        this.jsonb = jsonb;
        this.capability = capability;
        this.telemetrySubject = telemetrySubject;
    }

    @PostConstruct
    public void start() {
        Dispatcher dispatcher = nats.createDispatcher(msg -> {
            try {
                String json = new String(msg.getData(), StandardCharsets.UTF_8);
                TelemetryMessage telemetry = jsonb.fromJson(json, TelemetryMessage.class);
                capability.onTelemetry(telemetry);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Telemetry parse/handle failed on subject " + msg.getSubject(), e);
            }
        });
        dispatcher.subscribe(telemetrySubject);
        LOG.info(() -> "Subscribed to telemetry subject: " + telemetrySubject);
    }
}
