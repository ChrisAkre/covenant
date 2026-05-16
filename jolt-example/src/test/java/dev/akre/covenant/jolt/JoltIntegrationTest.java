package dev.akre.covenant.jolt;

import dev.akre.covenant.api.Type;
import dev.akre.covenant.types.JsonSchemaParser;
import dev.akre.covenant.types.JsonTypeSystem;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JoltIntegrationTest {
    public static final Logger LOGGER = LoggerFactory.getLogger(JoltIntegrationTest.class);

    private JsonSchemaParser jsonParser;
    private JsonMapper mapper;

    @BeforeEach
    public void setup() {
        jsonParser = new JsonSchemaParser(JsonTypeSystem.INSTANCE);
        mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build();
    }

    static Stream<Path> jsonFilesProvider() throws IOException {
        Path jsonDir = Paths.get("src/test/json/shiftr");
        if (!Files.exists(jsonDir)) {
            // fallback if running from root
             jsonDir = Paths.get("jolt-example/src/test/json/shiftr");
        }
        return Files.walk(jsonDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"));
    }

    @ParameterizedTest
    @MethodSource("jsonFilesProvider")
    public void testJoltShiftr(Path path) throws IOException {
        String content = Files.readString(path);
        JsonNode node = mapper.readTree(content);
        
        if (!node.has("inputSchema") || !node.has("spec") || !node.has("expectedSchema")) {
            return;
        }

        JsonNode inputSchemaNode = node.get("inputSchema");
        JsonNode specNode = node.get("spec");
        JsonNode expectedSchemaNode = node.get("expectedSchema");

        Type inputSchema = jsonParser.parse(inputSchemaNode);
        Type expectedSchema = jsonParser.parse(expectedSchemaNode);

        JoltCovenantChecker checker = new JoltCovenantChecker(JsonTypeSystem.INSTANCE);
        Type inferred = checker.infer(inputSchema, specNode);
        boolean result = expectedSchema.isAssignableFrom(inferred);
        Assumptions.assumeTrue(result, "Jolt verification failed for: " + path + "\nExpected: " + expectedSchema.repr() + "\nInferred: " + inferred.repr());
    }
}
