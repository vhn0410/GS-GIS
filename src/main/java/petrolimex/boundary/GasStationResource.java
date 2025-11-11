package petrolimex.boundary;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import petrolimex.dto.GasStationRequest;
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
    public List<GasStation> list() {
        return GasStation.listAll();
    }
}

