package it.unibo.acme.fleet.battery.provider;

import io.nats.client.Connection;
import it.unibo.acme.fleet.battery.model.BatterySnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class BatterySnapshotPublisher {

    private final Connection nats;
    private final Jsonb jsonb;
    private final String subjectPrefix;

    @Inject
    public BatterySnapshotPublisher(Connection nats,
                                   Jsonb jsonb,
                                   @ConfigProperty(name = "battery.snapshot.subjectPrefix", defaultValue = "event.battery.snapshot")
                                   String subjectPrefix) {
        this.nats = nats;
        this.jsonb = jsonb;
        this.subjectPrefix = subjectPrefix;
    }

    public void publish(BatterySnapshot snapshot) {
        if (snapshot == null || snapshot.vehicleId == null) {
            return;
        }
        String subject = subjectPrefix + "." + snapshot.vehicleId;
        byte[] payload = jsonb.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
        nats.publish(subject, payload);
    }
}
