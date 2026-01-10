package it.unibo.acme.fleet.battery.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class NatsConnectionProvider {

    private volatile Connection connection;

    @Produces
    @ApplicationScoped
    public Connection connection(@ConfigProperty(name = "nats.url") String natsUrl) throws IOException, InterruptedException {
        this.connection = Nats.connect(natsUrl);
        return this.connection;
    }

    @PreDestroy
    public void close() {
        Connection c = this.connection;
        if (c != null) {
            try {
                c.flush(Duration.ofSeconds(2));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException ignored) {
                // ignore
            }
            try {
                c.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }
}
