package petrolimex.boundary;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import petrolimex.model.Location;

import java.util.List;

@Path("/locations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LocationResource {

    @GET
    public List<Location> getAll() {
        return Location.listAll();
    }

    @POST
    @Transactional
    public Response add(Location location) {
        location.persist();
        return Response.ok(location).status(201).build();
    }
}