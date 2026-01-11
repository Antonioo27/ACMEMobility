package it.unibo.acme.fleet.gateway.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

@ApplicationScoped
public class JsonbProvider {
    
    @Produces
    @ApplicationScoped
    public Jsonb jsonb() {
        // Crea e restituisce l'istanza di JSON-B che useranno tutte le classi
        return JsonbBuilder.create();
    }
}