package it.unibo.acme.fleet.tracking.provider;

import io.nats.client.Connection;
import it.unibo.acme.fleet.tracking.model.TrackingSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class TrackingSnapshotPublisher {

    private final Connection nats;
    private final Jsonb jsonb;
    private final String subjectPrefix;

    @Inject
    public TrackingSnapshotPublisher(Connection nats,
                                    Jsonb jsonb,
                                    @ConfigProperty(name = "tracking.snapshot.subjectPrefix", defaultValue = "event.tracking.snapshot")
                                    String subjectPrefix) {
        this.nats = nats;
        this.jsonb = jsonb;
        this.subjectPrefix = subjectPrefix;
    }

    public void publish(TrackingSnapshot snapshot) {
        if (snapshot == null || snapshot.vehicleId == null) {
            return;
        }
        String subject = subjectPrefix + "." + snapshot.vehicleId;
        byte[] payload = jsonb.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
        nats.publish(subject, payload);
    }
}
