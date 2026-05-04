package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.JsonSchemaParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JoltCovenantCheckerTest {

    private JoltCovenantChecker checker;
    private AbstractTypeSystem system;
    private ObjectMapper mapper;
    private JsonSchemaParser jsonParser;

    @BeforeEach
    public void setup() {
        system = JoltTypeSystem.INSTANCE;
        checker = new JoltCovenantChecker(system);
        mapper = new ObjectMapper();
        jsonParser = new JsonSchemaParser(system);
    }

    private Type parseSchema(String jsonSchema) throws Exception {
        return jsonParser.parse(mapper.readTree(jsonSchema));
    }

    @Test
    public void testBasicShift() throws Exception {
        String inputSchemaJson = """
        {
          "type": "object",
          "properties": {
            "Rating": { "type": "number" },
            "SecondaryRatings": {
              "type": "object",
              "properties": {
                "design": { "type": "number" }
              }
            }
          }
        }
        """;
        Type inputSchema = parseSchema(inputSchemaJson);

        String joltSpec = """
        {
          "Rating": "rating",
          "SecondaryRatings": {
            "design": "SecondaryRatings.design"
          }
        }
        """;

        Type inferred = checker.infer(inputSchema, joltSpec);

        Type expectedRating = system.expression("Object<rating: Number, ...>");
        Type expectedDesign = system.expression("Object<SecondaryRatings: Object<design: Number, ...>, ...>");
        Type expected = expectedRating.intersect(expectedDesign);

        assertTrue(expected.isAssignableFrom(inferred), "Inferred type should contain mapped properties");
    }
}
