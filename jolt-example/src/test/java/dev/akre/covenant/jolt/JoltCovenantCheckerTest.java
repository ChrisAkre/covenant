package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.AbstractTypeSystem;
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

    @BeforeEach
    public void setup() {
        checker = new JoltCovenantChecker();
        system = JoltTypeSystem.INSTANCE;
        mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build();
    }

    private String readResource(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/test/resources/" + name)));
    }

    private Type inferTypeFromData(JsonNode node) {
        if (node.isNumber()) return system.expression("Number");
        if (node.isTextual()) {
            return system.expression("String").intersect(system.expression("'" + node.asText() + "'"));
        }
        if (node.isBoolean()) return system.expression("Bool");
        if (node.isNull()) return system.expression("Null");
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder("Object<");
            boolean first = true;
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append(": ").append(inferTypeFromData(entry.getValue()).repr());
                first = false;
            }
            sb.append(">");
            return system.expression(sb.toString());
        }
        if (node.isArray()) {
             if (node.isEmpty()) return system.expression("Array");
             Type unionType = system.bottom();
             for (JsonNode child : node) {
                 unionType = unionType.union(inferTypeFromData(child));
             }
             return system.expression("Array<" + unionType.repr() + ">");
        }
        return system.top();
    }

    @Test
    public void testInception() throws Exception {
        String inputJson = readResource("inception-input.json");
        String specJson = readResource("inception-spec.json");

        JsonNode inputData = mapper.readTree(inputJson);
        Type inputType = inferTypeFromData(inputData);

        Type outputType = checker.infer(inputType, specJson);
        assertNotNull(outputType);

        System.out.println("Output Type Inception: " + outputType.repr());
        // Spec defaults SecondaryRatings.Range to 5
        assertTrue(system.expression("Object<SecondaryRatings: Object, Rating: Number, Range: Number, ...>").isAssignableFrom(outputType), "Range 5 should be assigned");
    }

    @Test
    public void testBucketToPrefixSoup() throws Exception {
        String inputJson = readResource("bucketToPrefixSoup-input.json");
        String specJson = readResource("bucketToPrefixSoup-spec.json");

        JsonNode inputData = mapper.readTree(inputJson);
        Type inputType = inferTypeFromData(inputData);

        Type outputType = checker.infer(inputType, specJson);

        assertNotNull(outputType);
        System.out.println("Output Type BucketToPrefixSoup: " + outputType.repr());
        // The output should have keys starting with "rating-"
        assertTrue(system.expression("Object<rating-primary: Number, rating-SecondaryRatings: Number, ...>").isAssignableFrom(outputType), "Output should map prefix fields");
    }
}
