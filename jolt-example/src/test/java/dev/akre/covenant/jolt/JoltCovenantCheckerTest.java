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
    public void testInception() throws Exception {
        String inputSchemaJson = """
        {
          "type": "object",
          "properties": {
            "rating": {
              "type": "object",
              "properties": {
                "primary": {
                  "type": "object",
                  "properties": {
                    "value": { "type": "number" },
                    "max": { "type": "number" }
                  }
                }
              },
              "additionalProperties": {
                "type": "object",
                "properties": {
                  "value": { "type": "number" },
                  "max": { "type": "number" }
                }
              }
            }
          }
        }
        """;
        Type inputSchema = parseSchema(inputSchemaJson);

        String joltSpec = """
        [
          {
            "operation": "shift",
            "spec": {
              "rating": {
                "primary": {
                  "value": "Rating",
                  "max": "RatingRange"
                },
                "*": {
                  "max":   "SecondaryRatings.&1.Range",
                  "value": "SecondaryRatings.&1.Value",
                  "$": "SecondaryRatings.&1.Id"
                }
              }
            }
          },
          {
            "operation": "default",
            "spec": {
              "Range": 5,
              "SecondaryRatings": {
                "*": {
                  "Range": 5
                }
              }
            }
          }
        ]
        """;

        Type inferred = checker.infer(inputSchema, joltSpec);

        Type expectedRating = system.expression("Object<Rating: Number, RatingRange: ?Number, ...>");
        Type expectedSecondary = system.expression("Object<SecondaryRatings: Object<matches \"^.*$\": Object<Range: Number, Value: ?Number, Id: String, ...>, ...>, ...>");
        Type expectedDefaultRange = system.expression("Object<Range: Number, ...>");
        Type expected = expectedRating.intersect(expectedSecondary).intersect(expectedDefaultRange);

        assertTrue(expected.isAssignableFrom(inferred), "Inferred type should match inception output");
    }
}
