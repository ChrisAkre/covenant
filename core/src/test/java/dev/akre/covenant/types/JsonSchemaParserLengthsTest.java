package dev.akre.covenant.types;

import static org.junit.jupiter.api.Assertions.*;
import dev.akre.covenant.api.Type;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class JsonSchemaParserLengthsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TestTypeSystem system = TestTypeSystem.of(JsonTypeSystem.INSTANCE);
    private final JsonSchemaParser parser = new JsonSchemaParser(system);

    @Test
    public void testStringLengths() throws Exception {
        JsonNode schema = mapper.readTree("{\"type\": \"string\", \"minLength\": 3, \"maxLength\": 5}");
        Type type = parser.parse(schema);

        system.assertThat(type).isEquivalentTo("String & minlength 3 & maxlength 5");
    }

    @Test
    public void testArrayLengths() throws Exception {
        JsonNode schema = mapper.readTree("{\"type\": \"array\", \"minItems\": 2, \"maxItems\": 4, \"items\": {\"type\": \"integer\"}}");
        Type type = parser.parse(schema);

        system.assertThat(type).isEquivalentTo("Array<Int...> & minlength 2 & maxlength 4");
    }
}
