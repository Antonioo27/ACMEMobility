package it.unibo.acme.fleet.gateway;

/**
 * Bootstrap entrypoint.
 *
 * We keep it tiny on purpose: Helidon MP + CDI does the rest.
 */
public class Main {
    public static void main(String[] args) {
        io.helidon.microprofile.cdi.Main.main(args);
    }
}
