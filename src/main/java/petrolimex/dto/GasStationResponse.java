package petrolimex.dto;

import java.util.List;

import org.locationtech.jts.geom.Point;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import petrolimex.serializer.PointSerializer;

public class GasStationResponse {
    public String name;
    public String address_old;
    public String address_new;
    public String owner;
    public String supplier;
    public String station_type;
    public Nearest nearest_petrolimex;
    public EstimatedSale estimated_sale;
    public PumpsQuantity pumps_quantity;
    public Double facade_length;
    public List<String> dmn;
    public List<String> other_services;
    public int status;
    public String image;
    public String notes;

    @JsonSerialize(using = PointSerializer.class)
    public Point coordinates;

    public static class Nearest { public String name; public Double distance_km; }
    public static class EstimatedSale { public Double total; public Double fuel; public Double oil; }
    public static class PumpsQuantity { public Integer fuel; public Integer oil; }
}
