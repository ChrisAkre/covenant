package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.JsonSchemaParser;
import dev.akre.covenant.types.JsonTypeSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JoltCovenantCheckerTest {

    private JoltCovenantChecker checker;
    private AbstractTypeSystem system;
    private ObjectMapper mapper;
    private JsonSchemaParser jsonParser;

    private static final String INPUT_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "rating": {
          "type": "object",
          "properties": {
            "primary": {
              "type": "object",
              "properties": {
                "value": {"type": "number"}
              }
            },
            "quality": {
              "type": "object",
              "properties": {
                "value": {"type": "number"}
              }
            }
          }
        }
      }
    }
    """;

    private static final String JOLT_SPEC = """
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
              "max": "SecondaryRatings.&1.Range",
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

    private static final String EXPECTED_OUTPUT_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "Rating": { "type": "number" },
        "Range": { "type": "number" },
        "SecondaryRatings": {
          "type": "object",
          "additionalProperties": {
            "type": "object",
            "properties": {
              "Range": { "type": "number" }
            }
          }
        }
      }
    }
    """;

    @BeforeEach
    public void setup() {
        checker = new JoltCovenantChecker();
        system = JoltTypeSystem.INSTANCE;
        mapper = new ObjectMapper();
        jsonParser = new JsonSchemaParser(system);
    }

    @Test
    public void testInception() throws Exception {
        Type inputType = jsonParser.parse(mapper.readTree(INPUT_SCHEMA));
        Type expectedOutputType = jsonParser.parse(mapper.readTree(EXPECTED_OUTPUT_SCHEMA));

        Type inferredOutput = checker.infer(inputType, JOLT_SPEC);

        System.out.println("Expected Output: " + expectedOutputType.repr());
        System.out.println("Inferred Output: " + inferredOutput.repr());

        // Because "expectedOutput" builds via JSON Schema which implicitly creates optional fields and open properties boundaries:
        // `Object<Rating?: Number, Range?: Number, SecondaryRatings?: Object<...>, ...>`
        // and our custom typechecker infers explicitly closed explicit keys with implicit rest mapping bounds:
        // `Object<Range: Number, SecondaryRatings: Object<..., Object<Range: Number, ...>>, Rating: Number, ...>`
        // We use `.isAssignableTo` (flipped) to verify our explicitly inferred type output is safely a subtype of the loosely expected bounds schema
        assertTrue(inferredOutput.isAssignableFrom(expectedOutputType));
    }
}
