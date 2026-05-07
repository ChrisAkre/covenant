package dev.akre.covenant.jolt;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.JsonSchemaParser;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JoltCovenantCheckerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaParser parser = new JsonSchemaParser(JoltTypeSystem.INSTANCE);
    private final JoltCovenantChecker checker = new JoltCovenantChecker();

    @Test
    public void testInceptionSchemaValidation() throws Exception {
        JsonNode spec = mapper.readTree(new File("src/test/resources/examples/inception-spec.json"));

        JsonNode inputSchemaNode = mapper.readTree(new File("src/test/resources/examples/inception-input-schema.json"));
        Type inputSchema = parser.parse(inputSchemaNode);

        Type inferredSchema = checker.infer(inputSchema, spec);

        JsonNode expectedNode = mapper.readTree(new File("src/test/resources/examples/inception-expected-output-schema.json"));
        Type expectedOutputSchema = parser.parse(expectedNode);

        assertTrue(expectedOutputSchema.isAssignableFrom(inferredSchema),
                "The typechecker should infer Rating as a Number properly extracted from the input schema");
    }

    @Test
    public void testBucketToPrefixSoupValidation() throws Exception {
        JsonNode spec = mapper.readTree(new File("src/test/resources/examples/bucketToPrefixSoup-spec.json"));

        JsonNode inputSchemaNode = mapper.readTree(new File("src/test/resources/examples/bucketToPrefixSoup-input-schema.json"));
        Type inputSchema = parser.parse(inputSchemaNode);

        Type inferredSchema = checker.infer(inputSchema, spec);

        JsonNode expectedNode = mapper.readTree(new File("src/test/resources/examples/bucketToPrefixSoup-expected-output-schema.json"));
        Type expectedOutputSchema = parser.parse(expectedNode);

        assertTrue(expectedOutputSchema.isAssignableFrom(inferredSchema),
                "The typechecker should properly map 'Rating' to 'rating-primary' as a Number");
    }
}
