package it.unibo.acme.fleet.gateway.resource;

import it.unibo.acme.fleet.gateway.capability.FleetGatewayCapability;
import it.unibo.acme.fleet.gateway.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST entrypoint of Fleet Management (Gateway).
 *
 * This is the only part exposed "outside" (ACMEMobility).
 * Everything else is internal implementation detail.
 */
@Path("/fleet")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FleetGatewayResource {

    private final FleetGatewayCapability capability;

    @Inject
    public FleetGatewayResource(FleetGatewayCapability capability) {
        this.capability = capability;
    }

    /**
     * ACMEMobility notifies Fleet Mgmt that a rental has started.
     * In your choreography: track_start: a -> f
     */
    @POST
    @Path("/vehicles/{vehicleId}/start")
    public Response start(@PathParam("vehicleId") String vehicleId, StartRequest req) {
        StartStopResult r = capability.start(vehicleId, req);
        return "OK".equalsIgnoreCase(r.status)
                ? Response.ok(r).build()
                : Response.status(Response.Status.BAD_GATEWAY).entity(r).build();
    }

    /**
     * ACMEMobility notifies Fleet Mgmt that a rental ended (vehicle locked).
     * In your choreography: track_stop: a -> f
     */
    @POST
    @Path("/vehicles/{vehicleId}/stop")
    public Response stop(@PathParam("vehicleId") String vehicleId, StopRequest req) {
        StartStopResult r = capability.stop(vehicleId, req);
        return "OK".equalsIgnoreCase(r.status)
                ? Response.ok(r).build()
                : Response.status(Response.Status.BAD_GATEWAY).entity(r).build();
    }

    /**
     * ACMEMobility periodically polls for the current status.
     * In your choreography: (poll: a -> f ; status: f -> a)*
     *
     * The gateway answers from an in-memory cache kept updated by snapshot events.
     */
    @GET
    @Path("/vehicles/{vehicleId}/status")
    public VehicleStatus status(@PathParam("vehicleId") String vehicleId) {
        return capability.getStatus(vehicleId);
    }

    @GET
    @Path("/vehicles")
    public List<VehicleStatus> list() {
        return capability.listAll();
    }
}
