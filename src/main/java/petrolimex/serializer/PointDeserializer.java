package petrolimex.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode; // ✅ bắt buộc
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.io.IOException;

public class PointDeserializer extends JsonDeserializer<Point> {

    private final GeometryFactory gf = new GeometryFactory();

    @Override
    public Point deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);     // JsonNode
        JsonNode coords = node.get("coordinates");    // array node
        double x = coords.get(0).asDouble();
        double y = coords.get(1).asDouble();
        return gf.createPoint(new Coordinate(x, y));
    }
}
