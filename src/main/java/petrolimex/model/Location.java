package petrolimex.model;
import org.locationtech.jts.geom.Point;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import petrolimex.serializer.PointDeserializer;
import petrolimex.serializer.PointSerializer;

@Entity
public class Location extends PanacheEntity {

    public String name;

    // @JdbcTypeCode(SqlTypes.GEOMETRY)
    @JsonSerialize(using = PointSerializer.class)
    @JsonDeserialize(using = PointDeserializer.class)
    public Point coordinates; // PostGIS Point
}
