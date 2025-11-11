package petrolimex.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Point;

import java.io.IOException;

public class PointSerializer extends JsonSerializer<Point> {
    @Override
    public void serialize(Point value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeStartObject();
            gen.writeStringField("type", "Point");
            gen.writeArrayFieldStart("coordinates");
            gen.writeNumber(value.getX());
            gen.writeNumber(value.getY());
            gen.writeEndArray();
            gen.writeEndObject();
        }
    }
}
