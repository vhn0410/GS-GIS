package petrolimex.boundary;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import petrolimex.dto.GasStationRequest;
import petrolimex.dto.GasStationResponse;
import petrolimex.model.GasStation;
import petrolimex.service.GasStationService;

@Path("/gasstations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GasStationResource {

    @Inject
    GasStationService service;

    @POST
    @Transactional
    public Response add(GasStationRequest req) {
        GasStation gs = service.create(req);
        return Response.status(Response.Status.CREATED).entity(gs).build();
    }

 

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGasStation(@PathParam("id") Long id) {
        GasStationResponse res = service.getById(id);
        if (res == null)
            return Response.status(Status.NOT_FOUND).build();
        return Response.ok(res).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, GasStationRequest req) {
        GasStation gs = service.update(id, req);
        return Response.ok(gs).build();

    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        service.delete(id);
        return Response.noContent().build();
    }
    
}

