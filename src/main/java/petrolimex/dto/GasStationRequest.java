package petrolimex.dto;
import java.util.List;
import org.locationtech.jts.geom.Point;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import petrolimex.serializer.PointDeserializer;
import petrolimex.serializer.PointSerializer;

public class GasStationRequest {
    public String name;                         // -> gs
    public String address_old;                  // -> address
    public String address_new;                  // -> address
    public String owner;                        // -> gs
    public String supplier;                     // -> gs

    public int station_type;                 // -> gs
    public NearestPetrolimex nearest_petrolimex;
    public EstimatedSale estimated_sale;
    public PumpsQuantity pumps_quantity;
    public Double facade_length;                // -> gs
    public List<String> dmn;                    // -> DMN
    public List<String> other_services;         // -> other service
    public int status;                          // -> gs (0 -> Ngung kinh doanh, 1 -> Tam nghi, 2 -> Dang hoat dong)
    public String image;                        // -> gs
    public String notes;                        // -> gs

    
    @JsonSerialize(using = PointSerializer.class)
    @JsonDeserialize(using = PointDeserializer.class)
    public Point coordinates;                   // -> coordinates

    public static class NearestPetrolimex { public String name; public Double distance_km; }
    public static class EstimatedSale { public Double total; public Double fuel; public Double oil; }
    public static class PumpsQuantity { public Integer fuel; public Integer oil; }
}

