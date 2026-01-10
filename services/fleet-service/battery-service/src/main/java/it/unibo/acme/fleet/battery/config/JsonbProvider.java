package it.unibo.acme.fleet.battery.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

@ApplicationScoped
public class JsonbProvider {
    @Produces
    @ApplicationScoped
    public Jsonb jsonb() {
        return JsonbBuilder.create();
    }
}
