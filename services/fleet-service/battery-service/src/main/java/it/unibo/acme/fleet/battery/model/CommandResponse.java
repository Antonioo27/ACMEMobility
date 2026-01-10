package it.unibo.acme.fleet.battery.model;

public class CommandResponse {
    public String status;   // OK | ERROR
    public String message;
    public String vehicleId;

    public CommandResponse() {}

    public static CommandResponse ok(String vehicleId, String message) {
        CommandResponse r = new CommandResponse();
        r.status = "OK";
        r.vehicleId = vehicleId;
        r.message = message;
        return r;
    }

    public static CommandResponse error(String vehicleId, String message) {
        CommandResponse r = new CommandResponse();
        r.status = "ERROR";
        r.vehicleId = vehicleId;
        r.message = message;
        return r;
    }
}
