package dev.akre.covenant.jolt;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.JsonSchemaParser;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JoltCovenantCheckerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaParser parser = new JsonSchemaParser(JoltTypeSystem.INSTANCE);
    private final JoltCovenantChecker checker = new JoltCovenantChecker();

    private void runTestForExample(String name) throws Exception {
        JsonNode input = mapper.readTree(new File("src/test/resources/examples/" + name + "-input.json"));
        JsonNode spec = mapper.readTree(new File("src/test/resources/examples/" + name + "-spec.json"));

        Type inputSchema = parser.parse(input);
        Type inferredSchema = checker.infer(inputSchema, spec);

        assertNotNull(inferredSchema, "Inferred schema for " + name + " should not be null");
    }

    @Test
    public void testInception() throws Exception {
        runTestForExample("inception");
    }

    @Test
    public void testBucketToPrefixSoup() throws Exception {
        runTestForExample("bucketToPrefixSoup");
    }

    @Test
    public void testPrefixSoupToBuckets() throws Exception {
        runTestForExample("prefixSoupToBuckets");
    }

    @Test
    public void testListKeys() throws Exception {
        runTestForExample("listKeys");
    }

    @Test
    public void testMapToList() throws Exception {
        runTestForExample("mapToList");
    }

    @Test
    public void testInputArrayToPrefix() throws Exception {
        runTestForExample("inputArrayToPrefix");
    }

    @Test
    public void testHashDefault() throws Exception {
        runTestForExample("hashDefault");
    }

    @Test
    public void testTransposeSimple() throws Exception {
        runTestForExample("transposeSimple");
    }

    @Test
    public void testTransposeComplex() throws Exception {
        runTestForExample("transposeComplex");
    }
}
