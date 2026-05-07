package dev.akre.covenant.jolt;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
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

        Type inputSchema = JoltTypeSystem.INSTANCE.expression("Object<rating: Object<primary: Object<value: Number, max: Number>, secondary: Object<value: Number, max: Number>>>");

        Type inferredSchema = checker.infer(inputSchema, spec);

        // Define exact expected output schema
        // The simple jolt checker outputs object with exact properties for Rating and RatingRange inferred as Number
        ObjectNode expectedNode = mapper.createObjectNode();
        expectedNode.put("type", "object");
        ObjectNode expectedProps = expectedNode.putObject("properties");
        expectedProps.putObject("Rating").put("type", "number");
        expectedProps.putObject("RatingRange").put("type", "number");

        Type expectedOutputSchema = parser.parse(expectedNode);

        // Let's print out what we inferred so we can debug why assignable fails
        System.out.println("Expected: " + expectedOutputSchema.repr());
        System.out.println("Inferred: " + inferredSchema.repr());

        assertTrue(expectedOutputSchema.isAssignableFrom(inferredSchema),
                "The typechecker should infer Rating as a Number properly extracted from the input schema");
    }

    @Test
    public void testBucketToPrefixSoupValidation() throws Exception {
        JsonNode spec = mapper.readTree(new File("src/test/resources/examples/bucketToPrefixSoup-spec.json"));
        Type inputSchema = JoltTypeSystem.INSTANCE.expression("Object<Rating: Number, SecondaryRatings: Object<quality: Number>>");
        Type inferredSchema = checker.infer(inputSchema, spec);

        ObjectNode expectedNode = mapper.createObjectNode();
        expectedNode.put("type", "object");
        ObjectNode expectedProps = expectedNode.putObject("properties");
        expectedProps.putObject("rating-primary").put("type", "number");

        Type expectedOutputSchema = parser.parse(expectedNode);
        assertTrue(expectedOutputSchema.isAssignableFrom(inferredSchema),
                "The typechecker should properly map 'Rating' to 'rating-primary' as a Number");
    }
}
