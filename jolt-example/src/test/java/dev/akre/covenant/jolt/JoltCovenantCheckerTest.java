package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
import dev.akre.covenant.types.JsonSchemaParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.json.JsonReadFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JoltCovenantCheckerTest {

    private JoltCovenantChecker checker;
    private AbstractTypeSystem system;
    private ObjectMapper mapper;
    private JsonSchemaParser schemaParser;

    private static final String INCEPTION_INPUT_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "rating": {
          "type": "object",
          "properties": {
            "primary": {
              "type": "object",
              "properties": {
                "value": { "type": "number" }
              }
            },
            "quality": {
              "type": "object",
              "properties": {
                "value": { "type": "number" }
              }
            }
          }
        }
      }
    }
    """;

    private static final String BUCKET_TO_PREFIX_SOUP_INPUT_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "Rating": { "type": "number" },
        "SecondaryRatings": {
          "type": "object",
          "additionalProperties": { "type": "number" }
        }
      }
    }
    """;

    private static final String INCEPTION_EXPECTED_OUTPUT_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "SecondaryRatings": { "type": "object" },
        "Rating": { "type": "number" },
        "Range": { "type": "number" }
      },
      "required": ["SecondaryRatings", "Rating", "Range"],
      "additionalProperties": true
    }
    """;

    private static final String BUCKET_TO_PREFIX_SOUP_EXPECTED_OUTPUT_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "rating-primary": { "type": "number" },
        "rating-SecondaryRatings": { "type": "number" }
      },
      "required": ["rating-primary", "rating-SecondaryRatings"],
      "additionalProperties": true
    }
    """;

    @BeforeEach
    public void setup() {
        checker = new JoltCovenantChecker();
        system = JoltTypeSystem.INSTANCE;
        mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build();
        schemaParser = new JsonSchemaParser(system);
    }

    private String readResource(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/test/resources/" + name)));
    }

    @Test
    public void testInception() throws Exception {
        String specJson = readResource("inception-spec.json");

        Type inputType = schemaParser.parse(mapper.readTree(INCEPTION_INPUT_SCHEMA));
        Type expectedOutputType = schemaParser.parse(mapper.readTree(INCEPTION_EXPECTED_OUTPUT_SCHEMA));

        Type outputType = checker.infer(inputType, specJson);
        assertNotNull(outputType);

        assertTrue(expectedOutputType.isAssignableFrom(outputType), "Output should conform to expected schema");
    }

    @Test
    public void testBucketToPrefixSoup() throws Exception {
        String specJson = readResource("bucketToPrefixSoup-spec.json");

        Type inputType = schemaParser.parse(mapper.readTree(BUCKET_TO_PREFIX_SOUP_INPUT_SCHEMA));
        Type expectedOutputType = schemaParser.parse(mapper.readTree(BUCKET_TO_PREFIX_SOUP_EXPECTED_OUTPUT_SCHEMA));

        Type outputType = checker.infer(inputType, specJson);

        assertNotNull(outputType);
        assertTrue(expectedOutputType.isAssignableFrom(outputType), "Output should conform to prefix schema");
    }
}
