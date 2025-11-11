package petrolimex.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import org.locationtech.jts.geom.Point;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import petrolimex.serializer.PointSerializer;
import petrolimex.serializer.PointDeserializer;

@Entity
public class Anchor extends PanacheEntity {

    @JsonSerialize(using = PointSerializer.class)
    @JsonDeserialize(using = PointDeserializer.class)
    public Point geometry;

    @ManyToOne
    @JoinColumn(name = "ADDRESS_ID")
    public Address address;
}
